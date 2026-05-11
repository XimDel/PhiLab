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
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
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
 * Combina detección con TFLite (asíncrona), seguimiento por Optical Flow con OpenCV
 * y filtrado Kalman sobre el bounding box. Además integra calibración de escala
 * mediante marcadores ArUco y grabación de trayectorias en [SessionRecorder].
 *
 * Pipeline ejecutado por cada frame:
 * 1. Conversión de [ImageProxy] a [Bitmap] y a Mat en escala de grises.
 * 2. Aplicación de detecciones pendientes del hilo TFLite para reiniciar el tracker.
 * 3. Gestión del cambio de selección de objeto por parte del usuario.
 * 4. Actualización del tracker: Optical Flow → Kalman → detector como fallback.
 * 5. Lanzamiento asíncrono de inferencia TFLite cada [detectionEveryNFrames] frames.
 * 6. Detección de marcadores ArUco cada [arucoEveryNFrames] frames.
 * 7. Cálculo de medición física si hay calibración disponible.
 * 8. Grabación del punto de posición con timestamp del sensor de cámara.
 * 9. Emisión de resultados a la UI evitando duplicados.
 *
 * @property detectorProvider Proveedor del detector TFLite activo.
 * @property isCameraActive Indica si la cámara está activa y debe procesarse el frame.
 * @property isRecording Indica si hay una sesión de grabación en curso.
 * @property onFps Callback que recibe la tasa de frames por segundo calculada en ventana deslizante.
 * @property onTotalFrames Callback que recibe el contador acumulado de frames procesados.
 * @property onDetections Callback con la lista de detecciones UI a renderizar en pantalla.
 * @property onStatus Callback con el texto de estado para mostrar al usuario.
 * @property onTrackingDebug Callback con información de depuración del estado del tracker.
 * @property onCalibration Callback con el estado de calibración ArUco actualizado.
 * @property onMeasurement Callback con el resultado de medición física del objeto trackeado, o null.
 * @property markerSizeCmProvider Proveedor del tamaño real del marcador ArUco en centímetros.
 * @property enterThresholdProvider Proveedor del umbral mínimo de confianza para aceptar detecciones.
 * @property maxPerClassProvider Proveedor del máximo de detecciones permitidas por clase.
 * @property maxPerFrameProvider Proveedor del máximo de detecciones totales por frame.
 * @property arucoEveryNFrames Frecuencia de detección ArUco expresada en número de frames.
 * @property detectionEveryNFrames Frecuencia de inferencia TFLite expresada en número de frames.
 * @property selectedCenterProvider Proveedor del centro del objeto seleccionado por el usuario, o null si no hay selección.
 * @property onTrackedDetection Callback con la detección actualmente trackeada, o null si se perdió el tracking.
 * @property sessionRecorder Grabador de sesión donde se almacenan los puntos de posición.
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
    private val detectionEveryNFrames: Int = 8,
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
    private val rgbMat by lazy { Mat() }
    private val grayMat by lazy { Mat() }
    private val arucoDetector by lazy { ArucoScaleDetector() }
    private val measurementManager = MeasurementManager()
    private var arucoFrameCounter = 0
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching
    private val opticalFlowTracker by lazy { OpticalFlowTracker() }
    private val bboxKalman by lazy { BboxKalman() }
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var detectionRunning = false
    private var frameCounter = 0

    /**
     * Resultado de detección TFLite pendiente de aplicar en el hilo principal del analizador.
     *
     * @property detection La detección más cercana al centro de referencia, usada para reiniciar el tracker.
     * @property allDetections Lista completa de detecciones del mismo frame de inferencia.
     */
    private data class PendingDetection(
        val detection: UiDetection,
        val allDetections: List<UiDetection>
    )

    @Volatile private var pendingDetection: PendingDetection? = null
    @Volatile private var latestAllDetections: List<UiDetection> = emptyList()
    private var lastKnownCenter: Pair<Float, Float>? = null
    private var lastTrackedCenter: Pair<Float, Float>? = null
    private var smoothL = 0f
    private var smoothT = 0f
    private var smoothR = 0f
    private var smoothB = 0f
    private var smoothInit = false
    private val alphaOF     = 0.85f
    private val alphaKalman = 0.55f
    private var currentAlpha = alphaOF
    private val emaAlpha = 0.15f
    private var emaX: Float? = null
    private var emaY: Float? = null
    private val maxJumpPx = 150f
    private var recordingStartTimestampNs = 0L
    private var lastEmitDetections: List<UiDetection> = emptyList()
    private var lastMeasurement: MeasurementResult? = null
    private var lastStatus: String = ""
    private enum class TrackingSource { NONE, OPTICAL_FLOW, KALMAN, FALLBACK_DETECTOR, LOST }

    /**
     * Fuente activa de tracking en el frame actual, usada para depuración.
     */
    private var lastTrackingSource = TrackingSource.NONE
    private var lastTrackingDebug = ""

    /**
     * Devuelve true si la detección coincide con la clase objetivo activa,
     * o si no hay filtro configurado (targetLabelProvider devuelve null).
     *
     * Se usa en los tres puntos críticos donde el tracker puede "saltar" a otro objeto:
     * 1. Al reiniciar el tracker con pendingDetection.
     * 2. En closestDetectionToTracked() para el fallback.
     * 3. En launchDetection() para filtrar el raw antes de emitir.
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
     * @param image Frame capturado por CameraX, expresado como [ImageProxy] en formato RGBA.
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

                Utils.bitmapToMat(bitmap, rgbMat)
                Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                val pending = pendingDetection
                if (pending != null) {
                    pendingDetection = null
                    latestAllDetections = pending.allDetections

                    if (!opticalFlowTracker.isActive() && !grayMat.empty()
                        && pending.detection.matchesTarget()) {
                        val cvBox = pending.detection.toCvRect(grayMat.cols(), grayMat.rows())
                        opticalFlowTracker.init(grayMat, cvBox)
                        bboxKalman.reset()
                    }
                }

                val selectedCenter = selectedCenterProvider()
                if (selectedCenter != lastKnownCenter) {
                    if (selectedCenter == null) {
                        opticalFlowTracker.reset()
                        bboxKalman.reset()
                        smoothInit = false
                        emaX = null; emaY = null
                        lastTrackedCenter = null
                    } else if (lastKnownCenter == null) {
                        opticalFlowTracker.reset()
                        bboxKalman.reset()
                        lastTrackedCenter = null
                        val (tapX, tapY) = selectedCenter
                        val seed = latestAllDetections.minByOrNull { d ->
                            val cx = (d.left + d.right) / 2f
                            val cy = (d.top + d.bottom) / 2f
                            (cx - tapX) * (cx - tapX) + (cy - tapY) * (cy - tapY)
                        }
                        if (seed != null && !grayMat.empty()) {
                            opticalFlowTracker.init(
                                grayMat,
                                seed.toCvRect(grayMat.cols(), grayMat.rows())
                            )
                        }
                    }
                    lastKnownCenter = selectedCenter
                }

                var trackingSource = TrackingSource.NONE

                val trackedThisFrame: UiDetection? = if (selectedCenter == null) {
                    lastTrackedCenter = null
                    null
                } else {
                    fun closestDetectionToTracked(): UiDetection? {
                        val refX = lastTrackedCenter?.first ?: selectedCenter.first
                        val refY = lastTrackedCenter?.second ?: selectedCenter.second
                        return latestAllDetections
                            .filter { it.matchesTarget() }
                            .minByOrNull { d ->
                                val cx = (d.left + d.right) / 2f
                                val cy = (d.top + d.bottom) / 2f
                                val dx = cx - refX; val dy = cy - refY
                                dx * dx + dy * dy
                            }
                    }

                    val ofResult = if (opticalFlowTracker.isActive()) {
                        opticalFlowTracker.update(grayMat)
                    } else {
                        OpticalFlowTracker.TrackResult.Lost
                    }

                    when (ofResult) {
                        is OpticalFlowTracker.TrackResult.Found -> {
                            trackingSource = TrackingSource.OPTICAL_FLOW
                            currentAlpha = alphaOF

                            val filteredBox = bboxKalman.update(ofResult.bbox.toAndroidRect())
                            lastTrackedCenter = Pair(
                                (filteredBox.left + filteredBox.right) / 2f,
                                (filteredBox.top + filteredBox.bottom) / 2f
                            )
                            smoothBbox(
                                filteredBox, srcW, srcH,
                                latestAllDetections.firstOrNull { it.isSelected }
                                    ?: closestDetectionToTracked()
                                    ?: latestAllDetections.firstOrNull()
                            )
                        }

                        is OpticalFlowTracker.TrackResult.JustInitialized -> {
                            trackingSource = TrackingSource.OPTICAL_FLOW
                            currentAlpha = alphaOF
                            val ref = closestDetectionToTracked() ?: latestAllDetections.firstOrNull()
                            if (ref != null) {
                                val box = ref.toAndroidRect(srcW, srcH)
                                lastTrackedCenter = Pair(
                                    (box.left + box.right) / 2f,
                                    (box.top + box.bottom) / 2f
                                )
                                smoothBbox(box, srcW, srcH, ref)
                            } else {
                                trackingSource = TrackingSource.LOST
                                smoothInit = false; lastTrackedCenter = null; null
                            }
                        }

                        is OpticalFlowTracker.TrackResult.Lost -> {
                            val predicted = bboxKalman.predict()
                            if (predicted != null) {
                                trackingSource = TrackingSource.KALMAN
                                currentAlpha = alphaKalman
                                lastTrackedCenter = Pair(
                                    (predicted.left + predicted.right) / 2f,
                                    (predicted.top + predicted.bottom) / 2f
                                )
                                smoothBbox(
                                    predicted, srcW, srcH,
                                    closestDetectionToTracked() ?: latestAllDetections.firstOrNull()
                                )
                            } else {
                                val ofGraceActive = opticalFlowTracker.framesAfterInit <
                                        opticalFlowTracker.GRACE_FRAMES

                                val fallback = closestDetectionToTracked()

                                if (fallback != null && !ofGraceActive) {
                                    trackingSource = TrackingSource.FALLBACK_DETECTOR
                                    currentAlpha = alphaKalman

                                    if (!grayMat.empty()) {
                                        try {
                                            opticalFlowTracker.init(
                                                grayMat,
                                                fallback.toCvRect(grayMat.cols(), grayMat.rows())
                                            )
                                        } catch (_: Throwable) {}
                                    }

                                    lastTrackedCenter = Pair(
                                        (fallback.left + fallback.right) / 2f,
                                        (fallback.top + fallback.bottom) / 2f
                                    )
                                    smoothBbox(fallback.toAndroidRect(srcW, srcH), srcW, srcH, fallback)

                                } else if (fallback != null && ofGraceActive) {
                                    trackingSource = TrackingSource.KALMAN
                                    currentAlpha = alphaKalman
                                    lastTrackedCenter = Pair(
                                        (fallback.left + fallback.right) / 2f,
                                        (fallback.top + fallback.bottom) / 2f
                                    )
                                    smoothBbox(fallback.toAndroidRect(srcW, srcH), srcW, srcH, fallback)

                                } else {
                                    trackingSource = TrackingSource.LOST
                                    smoothInit = false; lastTrackedCenter = null; null
                                }
                            }
                        }
                    }
                }

                lastTrackingSource = trackingSource

                if (frameCounter % detectionEveryNFrames == 0 && !detectionRunning) {
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
                        lastCalibrationState =
                            CalibrationState.Error("ArUco: ${e.javaClass.simpleName}")
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
                opticalFlowTracker.reset()
                bboxKalman.reset()
                lastTrackedCenter = null
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
     * Lanza la inferencia TFLite en el [detectionScope] de forma asíncrona.
     *
     * Copia el bitmap para evitar conflictos con el hilo de análisis, recorta y escala
     * la imagen al tamaño de entrada del modelo, filtra por umbral de confianza y límites
     * por clase/frame, y selecciona la detección más cercana a [refCenter] como candidata
     * principal para reiniciar el tracker.
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
     * Resetea todos los componentes del pipeline al estado inicial cuando la cámara se desactiva.
     *
     * Limpia el estado del tracker, Kalman, EMA, detecciones pendientes y emite valores
     * neutros a la UI para reflejar el estado inactivo.
     */
    private fun handleCameraInactive() {
        arucoDetector.reset()
        lastCalibrationState = CalibrationState.Idle
        opticalFlowTracker.reset()
        bboxKalman.reset()
        smoothInit = false
        emaX = null; emaY = null
        lastKnownCenter = null; lastTrackedCenter = null
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
     * Registra un punto de posición en el [SessionRecorder] con suavizado EMA y detección de saltos.
     *
     * Aplica un filtro EMA con alpha = [emaAlpha] (~6 frames de constante de tiempo a 25 fps)
     * sobre el centroide del objeto. Si el desplazamiento respecto al punto anterior supera
     * [maxJumpPx] píxeles, el punto se descarta para evitar artefactos en la trayectoria.
     * Usa el timestamp del sensor de cámara ([frameTimestampNs]) para garantizar precisión
     * temporal con jitter inferior a 1 ms.
     *
     * @param tracked Detección trackeada del frame actual.
     * @param calibrated Estado de calibración ArUco activo, o null si no hay calibración.
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

        if (!isJump) {
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
                    xCm = cx,
                    yCm = cy,
                    frameTimestampNs = frameTimestampNs
                )
            }
        }
    }

    /**
     * Construye la lista de [UiDetection] a renderizar, marcando como seleccionada
     * cualquier detección con IoU > 0.3 respecto al objeto trackeado.
     *
     * Si [tracked] no solapa con ninguna detección existente, se inserta al inicio de la lista.
     *
     * @param tracked Detección actualmente trackeada, o null si no hay tracking activo.
     * @param all Lista completa de detecciones del último frame de inferencia TFLite.
     * @return Lista de detecciones con el campo [UiDetection.isSelected] actualizado.
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
     * Aplica suavizado EMA sobre los bordes del bounding box para reducir el jitter visual.
     *
     * Usa [currentAlpha] como factor de mezcla: valores altos (p.ej. [alphaOF] = 0.85)
     * siguen rápidamente al tracker; valores bajos (p.ej. [alphaKalman] = 0.55) dan
     * más inercia cuando el tracking es menos fiable.
     *
     * @param box Bounding box de entrada en coordenadas de píxel.
     * @param srcW Ancho del frame fuente para acotar los bordes.
     * @param srcH Alto del frame fuente para acotar los bordes.
     * @param ref Detección de referencia de la que se toman la etiqueta y la puntuación.
     * @return [UiDetection] con el bounding box suavizado y [UiDetection.isSelected] = true.
     */
    private fun smoothBbox(
        box: android.graphics.Rect,
        srcW: Int,
        srcH: Int,
        ref: UiDetection?
    ): UiDetection {
        val fL = box.left.toFloat(); val fT = box.top.toFloat()
        val fR = box.right.toFloat(); val fB = box.bottom.toFloat()
        if (!smoothInit) {
            smoothL = fL; smoothT = fT; smoothR = fR; smoothB = fB; smoothInit = true
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
     * Convierte esta [UiDetection] a un [Rect] de OpenCV normalizado al tamaño de la Mat.
     *
     * @param w Ancho de la Mat destino en píxeles.
     * @param h Alto de la Mat destino en píxeles.
     * @return [Rect] con origen y dimensiones acotadas al rango válido de la Mat.
     */
    private fun UiDetection.toCvRect(w: Int, h: Int): Rect {
        val x1 = left.toInt().coerceIn(0, w - 1)
        val y1 = top.toInt().coerceIn(0, h - 1)
        val x2 = right.toInt().coerceIn(x1 + 1, w)
        val y2 = bottom.toInt().coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
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
     * Convierte este [Rect] de OpenCV a un [android.graphics.Rect].
     */
    private fun Rect.toAndroidRect(): android.graphics.Rect =
        android.graphics.Rect(x, y, x + width, y + height)

    /**
     * Calcula la Intersección sobre Unión (IoU) entre dos detecciones.
     *
     * @param a Primera detección.
     * @param b Segunda detección.
     * @return Valor de IoU en el rango [0.0, 1.0]. Retorna 0 si la unión es nula.
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
     * Emite resultados a la UI solo cuando han cambiado respecto a la última emisión.
     *
     * Compara detecciones por posición e [UiDetection.isSelected], medición por referencia,
     * calibración por tipo de estado, y status por valor de cadena.
     *
     * @param detections Lista de detecciones a emitir.
     * @param measurement Resultado de medición actual, o null.
     * @param calibration Estado de calibración ArUco actual.
     * @param status Texto de estado para mostrar en la UI.
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
     */
    private fun updateTrackingDebug() {
        val debug = "Tracking: $lastTrackingSource"
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
     * El procesador aplica un recorte centrado al tamaño [cropSize] seguido de un resize
     * bilineal al tamaño de entrada del modelo ([modelW] × [modelH]).
     *
     * @param cropSize Lado del recorte cuadrado central en píxeles.
     * @param modelW Ancho de entrada del modelo TFLite en píxeles.
     * @param modelH Alto de entrada del modelo TFLite en píxeles.
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

    private var lastRotationDegrees = -1

    /**
     * Convierte un [ImageProxy] en formato RGBA_8888 a un [Bitmap] reutilizable.
     *
     * Reasigna el bitmap interno solo si las dimensiones del frame han cambiado,
     * minimizando las allocaciones en el heap por frame.
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
     * @param rotationDegrees Grados de rotación indicados por el sensor de cámara (0, 90, 180, 270).
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