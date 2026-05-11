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
 *    un fallo en ArUco no afecta al tracking ni a la detección).
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

    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var totalFrames = 0L
    private val windowNs = 250_000_000L

    private var processor: ImageProcessor? = null
    private var lastCrop = -1
    private var lastW = -1
    private var lastH = -1
    private var rgbaBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private var rotatedCanvas: Canvas? = null
    private val rotateMatrix = Matrix()
    private val reusableTensorImage = TensorImage()

    private val arucoDetector by lazy { ArucoScaleDetector() }
    private var arucoFrameCounter = 0
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching

    private val bboxKalman by lazy { BboxKalman() }

    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var detectionRunning = false
    private var frameCounter = 0

    /**
     * Par compuesto por la mejor detección seleccionada y la lista completa de detecciones
     * producida en la última ejecución asíncrona del detector TFLite.
     */
    private data class PendingDetection(
        val detection: UiDetection,
        val allDetections: List<UiDetection>
    )

    @Volatile private var pendingDetection: PendingDetection? = null
    @Volatile private var latestAllDetections: List<UiDetection> = emptyList()

    private var lastKnownCenter: Pair<Float, Float>? = null
    private var lastTrackedCenter: Pair<Float, Float>? = null
    private var lastDetectorBbox: UiDetection? = null

    private var smoothL = 0f
    private var smoothT = 0f
    private var smoothR = 0f
    private var smoothB = 0f
    private var smoothInit = false

    /**
     * Factor de suavizado EMA aplicado cuando el bounding box proviene del detector.
     */
    private val alphaMeasured = 0.65f

    /**
     * Factor de suavizado EMA aplicado cuando el bounding box proviene de la predicción de Kalman.
     */
    private val alphaPredicted = 0.40f
    private var currentAlpha = alphaMeasured

    private val emaAlpha = 0.25f
    private var emaX: Float? = null
    private var emaY: Float? = null

    /**
     * Umbral máximo de salto en píxeles permitido entre puntos consecutivos de trayectoria.
     * Los saltos superiores a este valor se descartan para evitar artefactos en la grabación.
     */
    private val maxJumpPx = 120f

    private var recordingStartTimestampNs = 0L

    private var lastEmitDetections: List<UiDetection> = emptyList()
    private var lastMeasurement: MeasurementResult? = null
    private var lastStatus: String = ""

    /**
     * Origen de la última actualización del tracking, utilizado para depuración.
     */
    private enum class TrackingSource { NONE, DETECTOR, KALMAN_PREDICT, LOST }
    private var lastTrackingSource = TrackingSource.NONE
    private var lastTrackingDebug = ""

    private val measurementManager = MeasurementManager()
    private var lastRotationDegrees = -1

    /**
     * Comprueba si la etiqueta de esta detección coincide con el objetivo configurado.
     *
     * Si [targetLabelProvider] devuelve `null`, cualquier etiqueta se considera válida.
     */
    private fun UiDetection.matchesTarget(): Boolean {
        val target = targetLabelProvider() ?: return true
        return label.equals(target, ignoreCase = true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  PIPELINE PRINCIPAL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Punto de entrada del analizador. Invocado por CameraX para cada fotograma.
     *
     * Ejecuta el pipeline completo de tracking y garantiza que [ImageProxy.close] se
     * llama en el bloque `finally` independientemente del resultado.
     *
     * @param image Fotograma proporcionado por CameraX.
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
                    try {
                        lastCalibrationState = arucoDetector.detectScale(
                            bitmap = bitmap,
                            markerSizeCm = markerSizeCmProvider()
                        )
                    } catch (e: Throwable) {
                        if (lastCalibrationState !is CalibrationState.Calibrated) {
                            lastCalibrationState =
                                CalibrationState.Error("ArUco: ${e.javaClass.simpleName}")
                        }
                    }
                }

                val calibrated = lastCalibrationState as? CalibrationState.Calibrated
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

    // ═══════════════════════════════════════════════════════════════
    //  DETECTION (async TFLite)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lanza la inferencia TFLite de forma asíncrona en [detectionScope].
     *
     * Copia el bitmap de entrada para evitar condiciones de carrera, aplica el
     * preprocesado definido en [ensureProcessor] y filtra los resultados por umbral
     * de confianza ([enterThresholdProvider]), clase ([maxPerClassProvider]) y total
     * de detecciones por frame ([maxPerFrameProvider]). La mejor detección según
     * proximidad a [refCenter] se almacena en [pendingDetection] para ser consumida
     * en el siguiente ciclo del pipeline.
     *
     * El flag [detectionRunning] impide lanzamientos paralelos.
     *
     * @param bitmap Bitmap del frame actual en coordenadas de cámara.
     * @param srcW Ancho del bitmap en píxeles.
     * @param srcH Alto del bitmap en píxeles.
     * @param detector Instancia del detector TFLite a utilizar.
     * @param refCenter Centro de referencia para seleccionar la mejor detección,
     *        o `null` para no aplicar criterio de proximidad.
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

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Devuelve la detección de [latestAllDetections] más cercana a [center] que
     * coincida con el objetivo configurado, o `null` si no hay candidatos.
     *
     * La proximidad se mide por distancia euclídea al cuadrado entre los centros.
     *
     * @param center Punto de referencia en coordenadas de imagen.
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
     * Agrega el punto de trayectoria de [tracked] a [sessionRecorder].
     *
     * Aplica un filtro EMA de factor [emaAlpha] sobre las coordenadas del centro
     * para reducir el ruido de alta frecuencia. Los saltos superiores a [maxJumpPx]
     * respecto al punto anterior se descartan sin registrar.
     *
     * Si hay calibración disponible, el punto se almacena en centímetros usando
     * el factor `cmPerPx` de [calibrated]; de lo contrario se almacena en píxeles.
     *
     * @param tracked Detección rastreada en el fotograma actual.
     * @param calibrated Estado de calibración activo, o `null` si no hay calibración.
     * @param frameTimestampNs Marca de tiempo del fotograma en nanosegundos.
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
     * Aplica suavizado EMA al bounding box y devuelve una [UiDetection] con las
     * coordenadas suavizadas.
     *
     * En el primer llamado inicializa el estado EMA directamente con los valores de [box].
     * En llamados sucesivos interpola con el factor [currentAlpha]. Los valores resultantes
     * se recortan al rango `[0, srcW]` × `[0, srcH]`.
     *
     * @param box Rectángulo a suavizar, proveniente del detector o del filtro de Kalman.
     * @param srcW Ancho de la imagen fuente en píxeles.
     * @param srcH Alto de la imagen fuente en píxeles.
     * @param ref Detección de referencia para copiar la etiqueta y la confianza, o `null`.
     * @return [UiDetection] con coordenadas suavizadas y `isSelected = true`.
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
     * Construye la lista de detecciones para la UI marcando cuál está rastreada.
     *
     * Marca como seleccionada cualquier detección de [all] cuyo IoU con [tracked]
     * supere 0.3. Si ninguna supera ese umbral pero [tracked] no es nula, la antepone
     * a la lista con `isSelected = true`.
     *
     * @param tracked Detección actualmente rastreada, o `null`.
     * @param all Lista completa de detecciones del último ciclo del detector.
     * @return Lista combinada con las banderas `isSelected` actualizadas.
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
     * Calcula la Intersección sobre la Unión (IoU) entre dos [UiDetection].
     *
     * @return Valor en el rango `[0, 1]`. Devuelve 0 si la unión es nula o negativa.
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
     * Convierte esta [UiDetection] a un [android.graphics.Rect] recortado a los
     * límites de la imagen `[0, w] × [0, h]`.
     *
     * Garantiza que el rectángulo sea no degenerado: `right >= left + 1` y
     * `bottom >= top + 1`.
     *
     * @param w Ancho máximo de la imagen en píxeles.
     * @param h Alto máximo de la imagen en píxeles.
     */
    private fun UiDetection.toAndroidRect(w: Int, h: Int): android.graphics.Rect {
        val l = left.toInt().coerceIn(0, w - 1)
        val t = top.toInt().coerceIn(0, h - 1)
        val r = right.toInt().coerceIn(l + 1, w)
        val b = bottom.toInt().coerceIn(t + 1, h)
        return android.graphics.Rect(l, t, r, b)
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAMERA LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Restablece todo el estado interno cuando la cámara pasa a inactiva.
     *
     * Resetea el detector ArUco, el filtro de Kalman, el estado de suavizado,
     * los centros de tracking y emite valores vacíos/inactivos a todos los callbacks.
     */
    private fun handleCameraInactive() {
        arucoDetector.reset()
        lastCalibrationState = CalibrationState.Idle
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

    // ═══════════════════════════════════════════════════════════════
    //  FPS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Actualiza el contador de FPS usando una ventana temporal de [windowNs] nanosegundos.
     *
     * Al completarse cada ventana invoca [onFps] con la frecuencia medida y reinicia
     * los contadores de la ventana. También incrementa [totalFrames] e invoca [onTotalFrames].
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

    // ═══════════════════════════════════════════════════════════════
    //  EMISSION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Emite actualizaciones a los callbacks de UI únicamente cuando los valores han cambiado.
     *
     * Para las detecciones compara tamaño y coordenadas individuales. El estado de
     * calibración se emite siempre que sea [CalibrationState.Calibrated] o cuando cambie
     * de tipo. El estado y la medición se emiten solo si difieren del valor anterior.
     *
     * @param detections Lista de detecciones a emitir.
     * @param measurement Resultado de medición física, o `null`.
     * @param calibration Estado de calibración actual.
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
     * Actualiza el mensaje de depuración del tracking e invoca [onTrackingDebug]
     * solo si el contenido ha cambiado respecto a la emisión anterior.
     */
    private fun updateTrackingDebug() {
        val kalmanInfo = try {
            if (bboxKalman.isInitialized()) " (lost=${bboxKalman.consecutiveFramesLost})" else ""
        } catch (_: Throwable) { "" }
        val debug = "Tracking: $lastTrackingSource$kalmanInfo"
        if (debug != lastTrackingDebug) { lastTrackingDebug = debug; onTrackingDebug(debug) }
    }

    // ═══════════════════════════════════════════════════════════════
    //  IMAGE CONVERSION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Obtiene el bitmap listo para procesar a partir de un [ImageProxy],
     * aplicando la rotación indicada por los metadatos de la imagen.
     *
     * @param image Frame de CameraX.
     * @return Bitmap rotado en coordenadas de pantalla.
     */
    private fun getBitmap(image: ImageProxy): Bitmap =
        rotateBitmapIfNeededReusable(
            imageProxyToReusableBitmap(image),
            image.imageInfo.rotationDegrees
        )

    /**
     * Crea o reutiliza el [ImageProcessor] de preprocesado TFLite.
     *
     * El processor se reconstruye solo cuando cambian el tamaño de crop o las
     * dimensiones del modelo, evitando allocations innecesarias por fotograma.
     *
     * @param cropSize Tamaño del lado del recorte cuadrado central en píxeles.
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
     * Copia los píxeles del [ImageProxy] a un [Bitmap] ARGB_8888 reutilizable.
     *
     * Si el bitmap en caché tiene las mismas dimensiones se reutiliza directamente;
     * de lo contrario se crea uno nuevo y se almacena en [rgbaBitmap].
     *
     * @param image Frame de CameraX cuyo plano 0 contiene los datos RGBA.
     * @return Bitmap con los píxeles del frame.
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
     * Rota el bitmap fuente el número de grados indicado reutilizando un bitmap y
     * un canvas de destino para evitar allocations por fotograma.
     *
     * Si la rotación normalizada es 0 devuelve el [source] sin modificarlo.
     * La matriz de rotación solo se recalcula cuando cambia el ángulo, almacenándola
     * en [rotateMatrix] y el último ángulo en [lastRotationDegrees].
     *
     * @param source Bitmap de origen a rotar.
     * @param rotationDegrees Ángulo de rotación en grados (puede ser cualquier entero).
     * @return Bitmap rotado, o [source] si no se requiere rotación.
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