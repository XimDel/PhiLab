package com.example.philab.core.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.philab.core.calibration.ArucoScaleDetector
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.core.detection.TfliteObjectDetector
import com.example.philab.core.measurement.MeasurementManager
import com.example.philab.core.measurement.MeasurementResult
import com.example.philab.domain.experiment.SessionRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Analizador de frames de cámara que implementa un pipeline de tracking en tiempo real.
 *
 * Combina detección asíncrona con TFLite, filtrado Kalman sobre el bounding box,
 * calibración de escala mediante marcadores ArUco y grabación de trayectorias
 * en [SessionRecorder].
 *
 * Pipeline ejecutado por cada frame:
 * 1. Conversión de [ImageProxy] a [Bitmap] con rotación del sensor.
 * 2. Aplicación de detecciones pendientes del hilo TFLite para alimentar el Kalman.
 * 3. Gestión del cambio de selección de objeto por parte del usuario.
 * 4. Actualización del tracker: si hay detección nueva → Kalman correct;
 *    si no → Kalman predict con decaimiento de velocidad; si Kalman agotado → Lost.
 * 5. Lanzamiento asíncrono de inferencia TFLite cada [detectionEveryNFrames] frames,
 *    con detección forzada inmediata al seleccionar un objeto sin tracking activo.
 * 6. Detección de marcadores ArUco cada [arucoEveryNFrames] frames (aislada:
 *    un fallo en ArUco no afecta al tracking ni a la detección). Acumula lecturas
 *    de cmPerPx y congela el valor cuando es estable para evitar fluctuaciones
 *    durante la grabación.
 * 7. Cálculo de medición física si hay calibración disponible.
 * 8. Grabación del punto de posición con suavizado EMA, filtro de saltos
 *    y timestamp del sensor de cámara.
 * 9. Emisión de resultados a la UI evitando duplicados.
 *
 * @property detectorProvider Proveedor del detector TFLite activo.
 * @property isCameraActive Indica si la cámara está activa y debe procesarse el frame.
 * @property isRecording Indica si hay una sesión de grabación en curso.
 * @property onFps Callback que recibe la tasa de frames por segundo en ventana deslizante.
 * @property onTotalFrames Callback que recibe el contador acumulado de frames procesados.
 * @property onDetections Callback con la lista de detecciones UI a renderizar en pantalla.
 * @property onStatus Callback con el texto de estado para mostrar al usuario.
 * @property onTrackingDebug Callback con información de depuración del tracker.
 * @property onCalibration Callback con el estado de calibración ArUco actualizado.
 * @property onMeasurement Callback con el resultado de medición física, o null.
 * @property markerSizeCmProvider Proveedor del tamaño real del marcador ArUco en centímetros.
 * @property enterThresholdProvider Proveedor del umbral mínimo de confianza para detecciones.
 * @property maxPerClassProvider Proveedor del máximo de detecciones permitidas por clase.
 * @property maxPerFrameProvider Proveedor del máximo de detecciones totales por frame.
 * @property arucoEveryNFrames Frecuencia de detección ArUco en número de frames.
 * @property detectionEveryNFrames Frecuencia de inferencia TFLite en número de frames.
 * @property selectedCenterProvider Proveedor del centro del objeto seleccionado, o null.
 * @property onTrackedDetection Callback con la detección trackeada, o null si se perdió.
 * @property sessionRecorder Grabador de sesión donde se almacenan los puntos de posición.
 * @property targetLabelProvider Proveedor de la clase objetivo para filtrar detecciones, o null.
 */
class FrameAnalyzer(
    private val detectorProvider: () -> TfliteObjectDetector,
    private val isCameraActive: () -> Boolean,
    private val isRecording: () -> Boolean,
    private val onFps: (Double) -> Unit,
    private val onTotalFrames: (Long) -> Unit,
    private val onDetections: (List<UiDetection>) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onTrackingDebug: (String) -> Unit,
    private val onCalibration: (CalibrationState) -> Unit,
    private val onMeasurement: (MeasurementResult?) -> Unit,
    private val markerSizeCmProvider: () -> Float,
    private val enterThresholdProvider: () -> Float,
    private val maxPerClassProvider: () -> Int,
    private val maxPerFrameProvider: () -> Int,
    private val arucoEveryNFrames: Int = 7,
    private val detectionEveryNFrames: Int = 3,
    private val selectedCenterProvider: () -> Pair<Float, Float>?,
    private val onTrackedDetection: (UiDetection?) -> Unit,
    private val sessionRecorder: SessionRecorder,
    private val targetLabelProvider: () -> String?,
) : ImageAnalysis.Analyzer {

    /** Marca de tiempo en nanosegundos del inicio de la ventana de FPS actual. */
    private var windowStartNs = 0L

    /** Número de frames procesados dentro de la ventana de FPS actual. */
    private var framesInWindow = 0

    /** Contador acumulado total de frames procesados desde la creación del analizador. */
    private var totalFrames = 0L

    /** Duración de la ventana de cálculo de FPS en nanosegundos (250 ms). */
    private val windowNs = 250_000_000L

    /**
     * Procesador de imagen TFLite que aplica recorte centrado y resize bilineal.
     * Se recrea solo cuando cambian las dimensiones de entrada.
     */
    private var processor: ImageProcessor? = null

    /** Lado del último recorte cuadrado aplicado al procesador. */
    private var lastCrop = -1

    /** Ancho de entrada del último modelo configurado en el procesador. */
    private var lastW = -1

    /** Alto de entrada del último modelo configurado en el procesador. */
    private var lastH = -1

    /** Bitmap reutilizable para la conversión de [ImageProxy] a RGBA. */
    private var rgbaBitmap: Bitmap? = null

    /** Bitmap reutilizable para almacenar el resultado de la rotación del frame. */
    private var rotatedBitmap: Bitmap? = null

    /** Canvas reutilizable asociado a [rotatedBitmap] para dibujar la rotación. */
    private var rotatedCanvas: Canvas? = null

    /** Matriz de rotación reutilizable, recalculada solo cuando cambia el ángulo. */
    private val rotateMatrix = Matrix()

    /** Imagen TFLite reutilizable para evitar allocaciones por frame. */
    private val reusableTensorImage = TensorImage()

    /** Detector de marcadores ArUco, inicializado de forma perezosa. */
    private val arucoDetector by lazy { ArucoScaleDetector() }

    /** Contador de frames para controlar la frecuencia de detección ArUco. */
    private var arucoFrameCounter = 0

    /** Último estado de calibración ArUco emitido, antes de aplicar el congelamiento. */
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching

    /**
     * Calibración congelada una vez que ArUco produce [STABLE_READINGS_TO_FREEZE]
     * lecturas consecutivas con varianza menor a [MAX_CMPPX_VARIANCE].
     * Se usa para medición y grabación, evitando que fluctuaciones del marcador
     * contaminen la trayectoria. La UI sigue recibiendo [lastCalibrationState]
     * para reflejar el estado real de detección del ArUco.
     */
    private var frozenCalibration: CalibrationState.Calibrated? = null

    /** Historial circular de las últimas lecturas de cmPerPx para evaluar estabilidad. */
    private val recentCmPerPx = mutableListOf<Float>()

    /** Número de lecturas consecutivas requeridas para congelar la calibración. */
    private val STABLE_READINGS_TO_FREEZE = 5

    /** Varianza máxima permitida entre lecturas para considerar la calibración estable. */
    private val MAX_CMPPX_VARIANCE = 0.0005f

    /** Filtro de Kalman para suavizar y predecir la posición del bounding box. */
    private val bboxKalman by lazy { BboxKalman() }

    /** Scope de coroutines para ejecutar la inferencia TFLite fuera del hilo de análisis. */
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Flag atómico que indica si hay una inferencia TFLite en curso. */
    @Volatile private var detectionRunning = false

    /** Contador de frames para controlar la frecuencia de inferencia TFLite. */
    private var frameCounter = 0

    /**
     * Resultado de detección TFLite pendiente de aplicar en el hilo de análisis.
     *
     * @property detection La detección más cercana al centro de referencia.
     * @property allDetections Lista completa de detecciones del mismo frame de inferencia.
     */
    private data class PendingDetection(
        val detection: UiDetection,
        val allDetections: List<UiDetection>
    )

    /** Detección pendiente escrita por el hilo de inferencia y leída por el hilo de análisis. */
    @Volatile private var pendingDetection: PendingDetection? = null

    /** Lista completa de detecciones del último frame de inferencia procesado. */
    @Volatile private var latestAllDetections: List<UiDetection> = emptyList()

    /** Último centro de selección conocido del usuario, para detectar cambios de selección. */
    private var lastKnownCenter: Pair<Float, Float>? = null

    /** Centro del objeto actualmente trackeado en coordenadas de píxel del frame. */
    private var lastTrackedCenter: Pair<Float, Float>? = null

    /** Última detección del detector usada para alimentar el Kalman (label y score de referencia). */
    private var lastDetectorBbox: UiDetection? = null

    /** Borde izquierdo suavizado del bounding box por EMA. */
    private var smoothL = 0f

    /** Borde superior suavizado del bounding box por EMA. */
    private var smoothT = 0f

    /** Borde derecho suavizado del bounding box por EMA. */
    private var smoothR = 0f

    /** Borde inferior suavizado del bounding box por EMA. */
    private var smoothB = 0f

    /** Indica si el suavizado EMA del bounding box ha sido inicializado con el primer frame. */
    private var smoothInit = false

    /** Factor EMA para frames con medición del detector (alto = sigue rápido). */
    private val alphaMeasured = 0.65f

    /** Factor EMA para frames con solo predicción Kalman (bajo = más inercia). */
    private val alphaPredicted = 0.40f

    /** Factor EMA activo en el frame actual, alternado entre [alphaMeasured] y [alphaPredicted]. */
    private var currentAlpha = alphaMeasured

    /** Factor EMA para suavizar el centroide antes de grabar en [SessionRecorder]. */
    private val emaAlpha = 0.25f

    /** Última coordenada X suavizada del centroide para grabación. */
    private var emaX: Float? = null

    /** Última coordenada Y suavizada del centroide para grabación. */
    private var emaY: Float? = null

    /** Desplazamiento máximo en píxeles entre frames consecutivos antes de descartar como salto. */
    private val maxJumpPx = 120f

    /** Timestamp del sensor de cámara del primer punto grabado en la sesión actual. */
    private var recordingStartTimestampNs = 0L

    /** Última lista de detecciones emitida a la UI, para deduplicación. */
    private var lastEmitDetections: List<UiDetection> = emptyList()

    /** Último resultado de medición emitido, para deduplicación. */
    private var lastMeasurement: MeasurementResult? = null

    /** Último texto de estado emitido, para deduplicación. */
    private var lastStatus: String = ""

    /**
     * Fuente de tracking activa en el frame actual.
     *
     * - [NONE]: no hay selección activa.
     * - [DETECTOR]: el frame tiene una medición nueva del detector TFLite.
     * - [KALMAN_PREDICT]: sin medición nueva, el Kalman extrapola la posición.
     * - [LOST]: el tracking se perdió (Kalman agotado y sin detecciones).
     */
    private enum class TrackingSource { NONE, DETECTOR, KALMAN_PREDICT, LOST }

    /** Fuente de tracking usada en el último frame procesado. */
    private var lastTrackingSource = TrackingSource.NONE

    /** Último texto de depuración emitido, para deduplicación. */
    private var lastTrackingDebug = ""

    /** Calculador de dimensiones físicas del objeto a partir del bounding box y la calibración. */
    private val measurementManager = MeasurementManager()

    /** Últimos grados de rotación aplicados al bitmap, para evitar recalcular la matriz. */
    private var lastRotationDegrees = -1

    /**
     * Devuelve `true` si la detección coincide con la clase objetivo activa,
     * o si no hay filtro configurado ([targetLabelProvider] devuelve null).
     */
    private fun UiDetection.matchesTarget(): Boolean {
        val target = targetLabelProvider() ?: return true
        return label.equals(target, ignoreCase = true)
    }

    /**
     * Punto de entrada del pipeline de análisis. Invocado por CameraX para cada frame capturado.
     *
     * Ejecuta el pipeline completo: conversión de imagen, tracking, detección asíncrona,
     * calibración ArUco, medición y emisión de resultados. Garantiza el cierre del [ImageProxy]
     * en el bloque `finally` independientemente de errores.
     *
     * @param image Frame capturado por CameraX en formato RGBA.
     */
    override fun analyze(image: ImageProxy) {
        val frameTimestampNs = image.imageInfo.timestamp

        try {
            if (!isCameraActive()) { handleCameraInactive(); return }

            tickFps()
            frameCounter++

            try {
                val detector = detectorProvider()
                val bitmap = getBitmap(image)
                val srcW = bitmap.width
                val srcH = bitmap.height

                val pending = pendingDetection
                if (pending != null) {
                    pendingDetection = null
                    latestAllDetections = pending.allDetections
                }

                val selectedCenter = selectedCenterProvider()
                if (selectedCenter != lastKnownCenter) {
                    handleSelectionChange(selectedCenter, srcW, srcH)
                }

                var trackingSource = TrackingSource.NONE

                val trackedThisFrame: UiDetection? = if (selectedCenter == null) {
                    lastTrackedCenter = null
                    lastDetectorBbox = null
                    null
                } else {
                    val newDetection = if (pending != null && pending.detection.matchesTarget()) {
                        pending.detection
                    } else {
                        closestDetectionTo(lastTrackedCenter ?: selectedCenter)
                    }

                    if (newDetection != null) {
                        trackingSource = TrackingSource.DETECTOR
                        currentAlpha = alphaMeasured
                        lastDetectorBbox = newDetection

                        val measured = newDetection.toAndroidRect(srcW, srcH)
                        val filtered = try {
                            bboxKalman.update(measured)
                        } catch (_: Throwable) {
                            measured
                        }

                        lastTrackedCenter = Pair(
                            (filtered.left + filtered.right) / 2f,
                            (filtered.top + filtered.bottom) / 2f
                        )
                        smoothBbox(filtered, srcW, srcH, newDetection)

                    } else if (try { bboxKalman.isInitialized() } catch (_: Throwable) { false }) {
                        val predicted = try { bboxKalman.predict() } catch (_: Throwable) { null }
                        if (predicted != null) {
                            trackingSource = TrackingSource.KALMAN_PREDICT
                            currentAlpha = alphaPredicted
                            lastTrackedCenter = Pair(
                                (predicted.left + predicted.right) / 2f,
                                (predicted.top + predicted.bottom) / 2f
                            )
                            smoothBbox(predicted, srcW, srcH, lastDetectorBbox)
                        } else {
                            trackingSource = TrackingSource.LOST
                            smoothInit = false
                            lastTrackedCenter = null
                            lastDetectorBbox = null
                            null
                        }
                    } else {
                        trackingSource = TrackingSource.LOST
                        null
                    }
                }

                lastTrackingSource = trackingSource

                val forceDetection = selectedCenter != null
                        && !(try { bboxKalman.isInitialized() } catch (_: Throwable) { false })
                if ((frameCounter % detectionEveryNFrames == 0 || forceDetection) && !detectionRunning) {
                    val refCenter = lastTrackedCenter ?: selectedCenter
                    launchDetection(bitmap, srcW, srcH, detector, refCenter)
                }

                arucoFrameCounter++
                if (arucoFrameCounter % arucoEveryNFrames == 0) {
                    handleArucoDetection(bitmap)
                }

                val calibrated = (frozenCalibration ?: lastCalibrationState)
                        as? CalibrationState.Calibrated
                val measurement = if (calibrated != null && trackedThisFrame != null)
                    measurementManager.measureObject(trackedThisFrame, calibrated.cmPerPx)
                else null

                if (isRecording() && sessionRecorder.isActive && trackedThisFrame != null) {
                    recordPoint(trackedThisFrame, calibrated, frameTimestampNs)
                }

                val uiDetections = buildUiDetections(trackedThisFrame, latestAllDetections)
                onTrackedDetection(trackedThisFrame?.copy(isSelected = true))
                val status = if (isRecording()) "Grabando"
                else "Obj. detectados: ${latestAllDetections.size}"
                emitIfChanged(uiDetections, measurement, lastCalibrationState, status)
                updateTrackingDebug()

            } catch (e: Exception) {
                try { bboxKalman.reset() } catch (_: Throwable) {}
                lastTrackedCenter = null
                lastDetectorBbox = null
                smoothInit = false
                emitIfChanged(
                    emptyList(), null, CalibrationState.Searching,
                    "ERR: ${e.javaClass.simpleName}"
                )
                onTrackedDetection(null)
                lastTrackingSource = TrackingSource.LOST
                updateTrackingDebug()
            }
        } finally {
            image.close()
        }
    }

    /**
     * Gestiona el cambio de selección del usuario (tap para seleccionar/deseleccionar).
     *
     * Al deseleccionar, resetea el Kalman, el suavizado y la EMA de grabación.
     * Al seleccionar por primera vez, busca la detección más cercana al punto de tap
     * e inicializa el Kalman con su bounding box como primera medición.
     *
     * @param selectedCenter Nuevo centro de selección, o null si se deseleccionó.
     * @param srcW Ancho del frame actual en píxeles.
     * @param srcH Alto del frame actual en píxeles.
     */
    private fun handleSelectionChange(
        selectedCenter: Pair<Float, Float>?,
        srcW: Int,
        srcH: Int
    ) {
        if (selectedCenter == null) {
            try { bboxKalman.reset() } catch (_: Throwable) {}
            smoothInit = false
            emaX = null; emaY = null
            lastTrackedCenter = null
            lastDetectorBbox = null
        } else if (lastKnownCenter == null) {
            try { bboxKalman.reset() } catch (_: Throwable) {}
            smoothInit = false
            lastTrackedCenter = null
            lastDetectorBbox = null

            val seed = closestDetectionTo(selectedCenter)
            if (seed != null) {
                lastDetectorBbox = seed
                try {
                    val seedRect = seed.toAndroidRect(srcW, srcH)
                    bboxKalman.update(seedRect)
                    lastTrackedCenter = Pair(
                        (seedRect.left + seedRect.right) / 2f,
                        (seedRect.top + seedRect.bottom) / 2f
                    )
                } catch (_: Throwable) {
                    lastDetectorBbox = null
                }
            }
        }
        lastKnownCenter = selectedCenter
    }

    /**
     * Ejecuta la detección de ArUco y gestiona el congelamiento del factor de escala.
     *
     * Acumula las últimas [STABLE_READINGS_TO_FREEZE] lecturas de cmPerPx. Cuando
     * su varianza es menor a [MAX_CMPPX_VARIANCE], congela el valor promedio en
     * [frozenCalibration]. Si el marcador se pierde, limpia el historial de estabilidad.
     *
     * Aislada con `try-catch(Throwable)`: un fallo en ArUco no afecta al tracking.
     *
     * @param bitmap Bitmap del frame actual para la detección de marcadores.
     */
    private fun handleArucoDetection(bitmap: Bitmap) {
        try {
            lastCalibrationState = arucoDetector.detectScale(
                bitmap = bitmap,
                markerSizeCm = markerSizeCmProvider()
            )

            val newState = lastCalibrationState
            if (newState is CalibrationState.Calibrated) {
                recentCmPerPx.add(newState.cmPerPx)
                if (recentCmPerPx.size > STABLE_READINGS_TO_FREEZE) {
                    recentCmPerPx.removeAt(0)
                }
                if (recentCmPerPx.size == STABLE_READINGS_TO_FREEZE
                    && frozenCalibration == null) {
                    val mean = recentCmPerPx.average().toFloat()
                    val variance = recentCmPerPx
                        .map { (it - mean) * (it - mean) }
                        .average().toFloat()
                    if (variance < MAX_CMPPX_VARIANCE) {
                        frozenCalibration = newState.copy(cmPerPx = mean)
                    }
                }
            } else {
                recentCmPerPx.clear()
            }
        } catch (e: Throwable) {
            if (lastCalibrationState !is CalibrationState.Calibrated) {
                lastCalibrationState =
                    CalibrationState.Error("ArUco: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Lanza la inferencia TFLite de forma asíncrona en [detectionScope].
     *
     * Copia el bitmap para evitar conflictos con el hilo de análisis, recorta y escala
     * la imagen al tamaño de entrada del modelo, filtra por umbral de confianza y límites
     * por clase/frame, y selecciona la detección más cercana a [refCenter] como candidata
     * principal. El resultado se almacena en [pendingDetection] para ser aplicado en el
     * siguiente frame del hilo de análisis.
     *
     * @param bitmap Bitmap del frame actual en coordenadas de cámara.
     * @param srcW Ancho del bitmap fuente en píxeles.
     * @param srcH Alto del bitmap fuente en píxeles.
     * @param detector Instancia del detector TFLite a utilizar.
     * @param refCenter Centro de referencia para elegir la detección más cercana al objeto trackeado.
     */
    private fun launchDetection(
        bitmap: Bitmap,
        srcW: Int,
        srcH: Int,
        detector: TfliteObjectDetector,
        refCenter: Pair<Float, Float>?
    ) {
        val bmpCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        val cropSize = min(srcW, srcH)
        val xOffset = ((srcW - cropSize) / 2f).coerceAtLeast(0f)
        val yOffset = ((srcH - cropSize) / 2f).coerceAtLeast(0f)
        val scaleX = cropSize.toFloat() / detector.inputWidth
        val scaleY = cropSize.toFloat() / detector.inputHeight
        val enterThr = enterThresholdProvider()
        val maxPClass = maxPerClassProvider().coerceIn(1, 10)
        val maxPFrame = maxPerFrameProvider().coerceIn(1, 6)

        detectionScope.launch {
            detectionRunning = true
            try {
                ensureProcessor(cropSize, detector.inputWidth, detector.inputHeight)
                reusableTensorImage.load(bmpCopy)
                val input = processor!!.process(reusableTensorImage)
                val results = detector.detect(input)
                bmpCopy.recycle()

                val raw = results.mapNotNull { det ->
                    val best = det.categories.maxByOrNull { it.score } ?: return@mapNotNull null
                    if (best.score < enterThr) return@mapNotNull null
                    val box = det.boundingBox
                    UiDetection(
                        label = best.label ?: "Object",
                        score = best.score,
                        left   = (box.left   * scaleX + xOffset).coerceIn(0f, srcW.toFloat()),
                        top    = (box.top    * scaleY + yOffset).coerceIn(0f, srcH.toFloat()),
                        right  = (box.right  * scaleX + xOffset).coerceIn(0f, srcW.toFloat()),
                        bottom = (box.bottom * scaleY + yOffset).coerceIn(0f, srcH.toFloat()),
                        sourceWidth = srcW, sourceHeight = srcH
                    )
                }
                    .groupBy { it.label }
                    .flatMap { (_, list) -> list.sortedByDescending { it.score }.take(maxPClass) }
                    .sortedByDescending { it.score }
                    .take(maxPFrame)
                    .filter { it.matchesTarget() }

                val best = if (refCenter != null) {
                    val (refX, refY) = refCenter
                    raw.minByOrNull { d ->
                        val cx = (d.left + d.right) / 2f
                        val cy = (d.top + d.bottom) / 2f
                        (cx - refX) * (cx - refX) + (cy - refY) * (cy - refY)
                    }
                } else null

                val allWithSelection = raw.map { it.copy(isSelected = it == best) }
                if (best != null) {
                    pendingDetection = PendingDetection(best, allWithSelection)
                } else {
                    latestAllDetections = allWithSelection
                }
            } catch (_: Exception) {
            } finally {
                detectionRunning = false
            }
        }
    }

    /**
     * Encuentra la detección más cercana a un punto de referencia,
     * filtrada por la clase objetivo configurada en [targetLabelProvider].
     *
     * @param center Punto de referencia en coordenadas de píxel del frame.
     * @return La detección más cercana, o null si no hay detecciones válidas.
     */
    private fun closestDetectionTo(center: Pair<Float, Float>): UiDetection? {
        val (refX, refY) = center
        return latestAllDetections
            .filter { it.matchesTarget() }
            .minByOrNull { d ->
                val cx = (d.left + d.right) / 2f
                val cy = (d.top + d.bottom) / 2f
                val dx = cx - refX; val dy = cy - refY
                dx * dx + dy * dy
            }
    }

    /**
     * Registra un punto de posición en el [sessionRecorder] con suavizado EMA
     * y detección de saltos.
     *
     * Aplica un filtro EMA con alpha = [emaAlpha] sobre el centroide del objeto.
     * Si el desplazamiento respecto al punto anterior supera [maxJumpPx] píxeles,
     * el punto se descarta para evitar artefactos en la trayectoria.
     * Usa el timestamp del sensor de cámara para garantizar precisión temporal.
     *
     * @param tracked Detección trackeada del frame actual.
     * @param calibrated Estado de calibración activo, o null si no hay calibración.
     * @param frameTimestampNs Timestamp del sensor de cámara en nanosegundos.
     */
    private fun recordPoint(
        tracked: UiDetection,
        calibrated: CalibrationState.Calibrated?,
        frameTimestampNs: Long
    ) {
        val rawCx = (tracked.left + tracked.right) / 2f
        val rawCy = (tracked.top + tracked.bottom) / 2f
        val prevX = emaX; val prevY = emaY

        val isJump = prevX != null && prevY != null &&
                (abs(rawCx - prevX) > maxJumpPx || abs(rawCy - prevY) > maxJumpPx)

        if (isJump) return

        val cx = prevX?.let { it + emaAlpha * (rawCx - it) } ?: rawCx
        val cy = prevY?.let { it + emaAlpha * (rawCy - it) } ?: rawCy
        emaX = cx; emaY = cy

        if (recordingStartTimestampNs == 0L) {
            recordingStartTimestampNs = frameTimestampNs
        }

        if (calibrated != null) {
            sessionRecorder.updateMetadata(cmPerPx = calibrated.cmPerPx, unit = "cm")
            sessionRecorder.addPoint(
                xCm = cx * calibrated.cmPerPx,
                yCm = cy * calibrated.cmPerPx,
                frameTimestampNs = frameTimestampNs
            )
        } else {
            sessionRecorder.addPoint(
                xCm = cx, yCm = cy,
                frameTimestampNs = frameTimestampNs
            )
        }
    }

    /**
     * Aplica suavizado EMA sobre los bordes del bounding box para reducir el jitter visual.
     *
     * Usa [currentAlpha] como factor de mezcla: [alphaMeasured] (0.65) cuando hay medición
     * del detector para seguir rápidamente, [alphaPredicted] (0.40) cuando solo hay predicción
     * Kalman para dar más inercia.
     *
     * @param box Bounding box de entrada en coordenadas de píxel.
     * @param srcW Ancho del frame para acotar los bordes.
     * @param srcH Alto del frame para acotar los bordes.
     * @param ref Detección de referencia de la que se toman la etiqueta y la puntuación.
     * @return [UiDetection] con el bounding box suavizado y [UiDetection.isSelected] = true.
     */
    private fun smoothBbox(
        box: android.graphics.Rect,
        srcW: Int, srcH: Int,
        ref: UiDetection?
    ): UiDetection {
        val fL = box.left.toFloat(); val fT = box.top.toFloat()
        val fR = box.right.toFloat(); val fB = box.bottom.toFloat()

        if (!smoothInit) {
            smoothL = fL; smoothT = fT; smoothR = fR; smoothB = fB
            smoothInit = true
        } else {
            val a = currentAlpha
            smoothL += a * (fL - smoothL); smoothT += a * (fT - smoothT)
            smoothR += a * (fR - smoothR); smoothB += a * (fB - smoothB)
        }

        return UiDetection(
            label = ref?.label ?: "Object", score = ref?.score ?: 1f,
            left   = smoothL.coerceIn(0f, srcW.toFloat()),
            top    = smoothT.coerceIn(0f, srcH.toFloat()),
            right  = smoothR.coerceIn(0f, srcW.toFloat()),
            bottom = smoothB.coerceIn(0f, srcH.toFloat()),
            sourceWidth = srcW, sourceHeight = srcH, isSelected = true
        )
    }

    /**
     * Construye la lista de [UiDetection] a renderizar, marcando como seleccionada
     * cualquier detección con IoU > 0.3 respecto al objeto trackeado.
     *
     * Si [tracked] no solapa con ninguna detección existente, se inserta al inicio.
     *
     * @param tracked Detección actualmente trackeada, o null si no hay tracking activo.
     * @param all Lista completa de detecciones del último frame de inferencia TFLite.
     * @return Lista con el campo [UiDetection.isSelected] actualizado.
     */
    private fun buildUiDetections(
        tracked: UiDetection?,
        all: List<UiDetection>
    ): List<UiDetection> {
        val marked = all.map { d ->
            d.copy(isSelected = tracked != null && iou(d, tracked) > 0.3f)
        }
        return if (tracked != null && marked.none { it.isSelected })
            listOf(tracked.copy(isSelected = true)) + marked
        else marked
    }

    /**
     * Calcula la Intersección sobre Unión (IoU) entre dos detecciones.
     *
     * @param a Primera detección.
     * @param b Segunda detección.
     * @return Valor de IoU en el rango [0.0, 1.0].
     */
    private fun iou(a: UiDetection, b: UiDetection): Float {
        val iL = max(a.left, b.left); val iT = max(a.top, b.top)
        val iR = min(a.right, b.right); val iB = min(a.bottom, b.bottom)
        val iW = (iR - iL).coerceAtLeast(0f); val iH = (iB - iT).coerceAtLeast(0f)
        val inter = iW * iH
        val union = (a.right - a.left) * (a.bottom - a.top) +
                (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Convierte esta [UiDetection] a un [android.graphics.Rect] acotado al tamaño del frame.
     *
     * @param w Ancho del frame en píxeles.
     * @param h Alto del frame en píxeles.
     * @return [android.graphics.Rect] con coordenadas enteras dentro del frame.
     */
    private fun UiDetection.toAndroidRect(w: Int, h: Int): android.graphics.Rect {
        val l = left.toInt().coerceIn(0, w - 1)
        val t = top.toInt().coerceIn(0, h - 1)
        val r = right.toInt().coerceIn(l + 1, w)
        val b = bottom.toInt().coerceIn(t + 1, h)
        return android.graphics.Rect(l, t, r, b)
    }

    /**
     * Resetea todos los componentes del pipeline cuando la cámara se desactiva.
     *
     * Limpia Kalman, suavizado, EMA, calibración congelada, detecciones pendientes
     * y emite valores neutros a la UI.
     */
    private fun handleCameraInactive() {
        arucoDetector.reset()
        lastCalibrationState = CalibrationState.Idle
        frozenCalibration = null
        recentCmPerPx.clear()
        try { bboxKalman.reset() } catch (_: Throwable) {}
        smoothInit = false
        emaX = null; emaY = null
        lastKnownCenter = null; lastTrackedCenter = null
        lastDetectorBbox = null
        latestAllDetections = emptyList()
        pendingDetection = null
        recordingStartTimestampNs = 0L
        lastTrackingSource = TrackingSource.NONE; lastTrackingDebug = ""
        onTrackingDebug("Tracking: Idle")
        emitIfChanged(emptyList(), null, CalibrationState.Idle, "Modelo de detección listo")
        onTrackedDetection(null)
    }

    /**
     * Actualiza el contador de FPS usando una ventana deslizante de [windowNs] nanosegundos.
     *
     * Incrementa [totalFrames] en cada llamada y emite la tasa calculada al completar la ventana.
     */
    private fun tickFps() {
        val now = System.nanoTime()
        if (windowStartNs == 0L) windowStartNs = now
        framesInWindow++; totalFrames++
        onTotalFrames(totalFrames)
        val elapsed = now - windowStartNs
        if (elapsed >= windowNs) {
            onFps(framesInWindow * 1e9 / elapsed.toDouble())
            windowStartNs = now; framesInWindow = 0
        }
    }

    /**
     * Emite resultados a la UI solo cuando han cambiado respecto a la última emisión.
     *
     * Compara detecciones por posición e [UiDetection.isSelected], medición por referencia,
     * calibración por tipo de estado, y status por valor de cadena.
     *
     * @param detections Lista de detecciones a emitir.
     * @param measurement Resultado de medición actual, o null.
     * @param calibration Estado de calibración ArUco actual.
     * @param status Texto de estado para la UI.
     */
    private fun emitIfChanged(
        detections: List<UiDetection>,
        measurement: MeasurementResult?,
        calibration: CalibrationState,
        status: String
    ) {
        val changed = detections.size != lastEmitDetections.size ||
                detections.zip(lastEmitDetections).any { (a, b) ->
                    a.left != b.left || a.top != b.top ||
                            a.right != b.right || a.bottom != b.bottom ||
                            a.isSelected != b.isSelected
                }
        if (changed) { lastEmitDetections = detections; onDetections(detections) }
        if (measurement != lastMeasurement) {
            lastMeasurement = measurement; onMeasurement(measurement)
        }
        val shouldEmitCal = calibration is CalibrationState.Calibrated ||
                calibration::class != lastCalibrationState::class
        if (shouldEmitCal) onCalibration(calibration)
        if (status != lastStatus) { lastStatus = status; onStatus(status) }
    }

    /**
     * Emite el estado de depuración del tracker solo si ha cambiado desde la última emisión.
     * Incluye la fuente de tracking y los frames perdidos del Kalman si está activo.
     */
    private fun updateTrackingDebug() {
        val kalmanInfo = try {
            if (bboxKalman.isInitialized()) " (lost=${bboxKalman.consecutiveFramesLost})" else ""
        } catch (_: Throwable) { "" }
        val debug = "Tracking: $lastTrackingSource$kalmanInfo"
        if (debug != lastTrackingDebug) { lastTrackingDebug = debug; onTrackingDebug(debug) }
    }

    /**
     * Obtiene el [Bitmap] del frame actual aplicando la rotación indicada por los metadatos del sensor.
     *
     * @param image Frame capturado por CameraX.
     * @return Bitmap en orientación correcta reutilizando buffers internos.
     */
    private fun getBitmap(image: ImageProxy): Bitmap =
        rotateBitmapIfNeededReusable(
            imageProxyToReusableBitmap(image),
            image.imageInfo.rotationDegrees
        )

    /**
     * Crea o reutiliza el [ImageProcessor] de TFLite si el tamaño de entrada ha cambiado.
     *
     * Aplica un recorte centrado al tamaño [cropSize] seguido de un resize bilineal
     * al tamaño de entrada del modelo ([modelW] × [modelH]).
     *
     * @param cropSize Lado del recorte cuadrado central en píxeles.
     * @param modelW Ancho de entrada del modelo TFLite.
     * @param modelH Alto de entrada del modelo TFLite.
     */
    private fun ensureProcessor(cropSize: Int, modelW: Int, modelH: Int) {
        if (processor == null || cropSize != lastCrop || modelW != lastW || modelH != lastH) {
            processor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(ResizeOp(modelH, modelW, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            lastCrop = cropSize; lastW = modelW; lastH = modelH
        }
    }

    /**
     * Convierte un [ImageProxy] en formato RGBA_8888 a un [Bitmap] reutilizable.
     *
     * Reasigna el bitmap interno solo si las dimensiones del frame han cambiado.
     *
     * @param image Frame capturado por CameraX con un único plano RGBA.
     * @return Bitmap con los píxeles del frame actual.
     */
    private fun imageProxyToReusableBitmap(image: ImageProxy): Bitmap {
        val w = image.width; val h = image.height
        val bmp = if (rgbaBitmap?.width != w || rgbaBitmap?.height != h)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { rgbaBitmap = it }
        else rgbaBitmap!!
        image.planes[0].buffer.rewind()
        bmp.copyPixelsFromBuffer(image.planes[0].buffer)
        return bmp
    }

    /**
     * Rota el [Bitmap] fuente según [rotationDegrees] reutilizando un canvas y bitmap internos.
     *
     * Si la rotación es 0° devuelve el bitmap sin copiar. La matriz de rotación se recalcula
     * solo cuando cambia el ángulo. Los bitmaps destino se reasignan únicamente si las
     * dimensiones resultantes cambian.
     *
     * @param source Bitmap a rotar.
     * @param rotationDegrees Grados de rotación del sensor de cámara (0, 90, 180, 270).
     * @return Bitmap rotado al ángulo correcto.
     */
    private fun rotateBitmapIfNeededReusable(source: Bitmap, rotationDegrees: Int): Bitmap {
        val norm = ((rotationDegrees % 360) + 360) % 360
        if (norm == 0) return source
        val targetW = if (norm == 90 || norm == 270) source.height else source.width
        val targetH = if (norm == 90 || norm == 270) source.width else source.height
        val target = if (rotatedBitmap?.width != targetW || rotatedBitmap?.height != targetH)
            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                rotatedBitmap = it; rotatedCanvas = Canvas(it); lastRotationDegrees = -1
            }
        else rotatedBitmap!!
        val canvas = rotatedCanvas ?: Canvas(target).also { rotatedCanvas = it }
        canvas.setBitmap(target); target.eraseColor(0)
        if (norm != lastRotationDegrees) {
            rotateMatrix.reset()
            when (norm) {
                90  -> { rotateMatrix.postRotate(90f);  rotateMatrix.postTranslate(targetW.toFloat(), 0f) }
                180 -> { rotateMatrix.postRotate(180f); rotateMatrix.postTranslate(targetW.toFloat(), targetH.toFloat()) }
                270 -> { rotateMatrix.postRotate(270f); rotateMatrix.postTranslate(0f, targetH.toFloat()) }
            }
            lastRotationDegrees = norm
        }
        canvas.drawBitmap(source, rotateMatrix, null)
        return target
    }
}