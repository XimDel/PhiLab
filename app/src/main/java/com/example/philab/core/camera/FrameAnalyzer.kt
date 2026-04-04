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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * FrameAnalyzer — tracking con Optical Flow + Kalman + TFLite async.
 *
 * Pipeline por frame:
 *  1. Bitmap → grayMat
 *  2. Aplicar pendingDetection → reinit OF SOLO si está inactivo
 *  3. Gestionar cambio de selección de usuario
 *  4. OpticalFlowTracker.update()
 *     ├─ JustInitialized → usar bbox del init, esperar frame siguiente
 *     ├─ Found  → BboxKalman.update() → EMA visual suave
 *     └─ Lost   → BboxKalman.predict() → EMA visual
 *                 └─ null + grace pasado → FALLBACK_DETECTOR → reinit OF
 *  5. TFLite cada N frames async
 *  6. ArUco cada N frames
 *
 * FIXES originales (FIX 1–6): ver versión anterior.
 *
 * FIXES NUEVOS:
 *
 *  FIX 7 — Timestamp del sensor en lugar de System.currentTimeMillis():
 *    recordPoint() y el inicio de grabación usan image.imageInfo.timestamp
 *    (nanosegundos del sensor de cámara, jitter < 1 ms) en lugar de
 *    System.currentTimeMillis() (incluye latencia del pipeline, jitter 5–40 ms).
 *    Impacto: las derivadas numéricas en el pipeline de análisis mejoran
 *    directamente porque Δt es preciso. Las gráficas de velocidad y
 *    aceleración son más suaves sin necesitar más pasadas de smoother.
 *
 *  FIX 8 — emaAlpha 0.5 → 0.15 en el centroide grabado:
 *    Con alpha=0.5 la constante de tiempo del EMA es ~1 frame, prácticamente
 *    sin efecto de suavizado. Con alpha=0.15 la constante de tiempo es ~6 frames
 *    (~200 ms a 25 fps), que corresponde a la escala temporal del ruido de
 *    tracking de cámara. El centroide que llega al SessionRecorder llega
 *    presuavizado en la escala correcta, reduciendo la carga del OutlierDetector
 *    y eliminando picos que el clipExtremes no alcanza a recortar bien.
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
    private val confirmFrames: Int = 2,
    private val dropFrames: Int = 3,
    private val arucoEveryNFrames: Int = 7,
    private val detectionEveryNFrames: Int = 8,
    private val selectedCenterProvider: () -> Pair<Float, Float>?,
    private val onTrackedDetection: (UiDetection?) -> Unit,
    private val sessionRecorder: SessionRecorder
) : ImageAnalysis.Analyzer {

    // ── FPS UI ───────────────────────────────────────────────────────────────
    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var totalFrames = 0L
    private val windowNs = 250_000_000L

    // ── Bitmap reuse ─────────────────────────────────────────────────────────
    private var processor: ImageProcessor? = null
    private var lastCrop = -1
    private var lastW = -1
    private var lastH = -1

    private var rgbaBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private var rotatedCanvas: Canvas? = null
    private val rotateMatrix = Matrix()
    private val reusableTensorImage = TensorImage()

    // ── OpenCV Mats ───────────────────────────────────────────────────────────
    private val rgbMat by lazy { Mat() }
    private val grayMat by lazy { Mat() }

    // ── ArUco ────────────────────────────────────────────────────────────────
    private val arucoDetector by lazy { ArucoScaleDetector() }
    private val measurementManager = MeasurementManager()
    private var arucoFrameCounter = 0
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching

    // ── Optical Flow + Kalman ────────────────────────────────────────────────
    private val opticalFlowTracker by lazy { OpticalFlowTracker() }
    private val bboxKalman by lazy { BboxKalman() }

    // ── TFLite async ─────────────────────────────────────────────────────────
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var detectionRunning = false
    private var frameCounter = 0

    private data class PendingDetection(
        val detection: UiDetection,
        val allDetections: List<UiDetection>
    )

    @Volatile private var pendingDetection: PendingDetection? = null
    @Volatile private var latestAllDetections: List<UiDetection> = emptyList()

    // ── Estado de tracking ───────────────────────────────────────────────────
    private var lastKnownCenter: Pair<Float, Float>? = null
    private var lastTrackedCenter: Pair<Float, Float>? = null

    // ── EMA visual del bbox ──────────────────────────────────────────────────
    private var smoothL = 0f
    private var smoothT = 0f
    private var smoothR = 0f
    private var smoothB = 0f
    private var smoothInit = false

    private val alphaOF     = 0.85f
    private val alphaKalman = 0.55f
    private var currentAlpha = alphaOF

    // ── EMA centroide grabado ────────────────────────────────────────────────
    // FIX 8: alpha reducido de 0.5 a 0.15.
    // Con 0.5 la constante de tiempo era ~1 frame → sin suavizado real.
    // Con 0.15 la constante de tiempo es ~6 frames (~200 ms a 25 fps),
    // escala apropiada para el ruido de tracking de cámara.
    private val emaAlpha = 0.15f
    private var emaX: Float? = null
    private var emaY: Float? = null
    private val maxJumpPx = 150f

    // ── FIX 7: timestamp del sensor para grabación ───────────────────────────
    // Guardamos el timestamp del primer frame de grabación para pasarlo a
    // SessionRecorder.start() y anclar el origen temporal al sensor.
    private var recordingStartTimestampNs = 0L

    // ── Emit dedup ───────────────────────────────────────────────────────────
    private var lastEmitDetections: List<UiDetection> = emptyList()
    private var lastMeasurement: MeasurementResult? = null
    private var lastStatus: String = ""

    // ── Debug ────────────────────────────────────────────────────────────────
    private enum class TrackingSource { NONE, OPTICAL_FLOW, KALMAN, FALLBACK_DETECTOR, LOST }

    private var ofUpdateCount = 0
    private var ofFoundCount = 0
    private var ofLostCount = 0
    private var kalmanPredictCount = 0
    private var fallbackDetectionCount = 0
    private var consecutiveKalmanOnlyFrames = 0
    private var maxKalmanOnlyFrames = 0

    private var analyzerPerfWindowStartNs = 0L
    private var analyzerPerfFrames = 0
    private var analyzerFps = 0.0
    private var lastFrameMs = 0.0

    private var pointsThisSecond = 0
    private var pointsPerSecond = 0
    private var pointsWindowStartNs = 0L

    private var lastTrackingSource = TrackingSource.NONE
    private var lastTrackingDebug = ""

    override fun analyze(image: ImageProxy) {
        val frameStartNs = System.nanoTime()

        // FIX 7: extraer el timestamp del sensor lo antes posible, antes de
        // cualquier procesamiento. imageInfo.timestamp es nanosegundos del
        // sensor de cámara, monotónico y con jitter < 1 ms.
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

                // 1) Bitmap → grayMat
                Utils.bitmapToMat(bitmap, rgbMat)
                Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                // 2) Aplicar pendingDetection del hilo TFLite
                // FIX 1: solo reiniciar OF si está inactivo
                val pending = pendingDetection
                if (pending != null) {
                    pendingDetection = null
                    latestAllDetections = pending.allDetections

                    if (!opticalFlowTracker.isActive() && !grayMat.empty()) {
                        val cvBox = pending.detection.toCvRect(grayMat.cols(), grayMat.rows())
                        opticalFlowTracker.init(grayMat, cvBox)
                        bboxKalman.reset()
                    }
                }

                // 3) Gestionar cambio de selección del usuario
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

                // 4) Optical Flow + Kalman + fallback
                var trackingSource = TrackingSource.NONE

                val trackedThisFrame: UiDetection? = if (selectedCenter == null) {
                    lastTrackedCenter = null
                    null
                } else {
                    fun closestDetectionToTracked(): UiDetection? {
                        val refX = lastTrackedCenter?.first ?: selectedCenter.first
                        val refY = lastTrackedCenter?.second ?: selectedCenter.second
                        return latestAllDetections.minByOrNull { d ->
                            val cx = (d.left + d.right) / 2f
                            val cy = (d.top + d.bottom) / 2f
                            val dx = cx - refX; val dy = cy - refY
                            dx * dx + dy * dy
                        }
                    }

                    val ofResult = if (opticalFlowTracker.isActive()) {
                        ofUpdateCount++
                        opticalFlowTracker.update(grayMat)
                    } else {
                        OpticalFlowTracker.TrackResult.Lost
                    }

                    when (ofResult) {
                        is OpticalFlowTracker.TrackResult.Found -> {
                            ofFoundCount++
                            consecutiveKalmanOnlyFrames = 0
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
                            ofLostCount++
                            val predicted = bboxKalman.predict()
                            if (predicted != null) {
                                kalmanPredictCount++
                                consecutiveKalmanOnlyFrames++
                                maxKalmanOnlyFrames = maxOf(maxKalmanOnlyFrames, consecutiveKalmanOnlyFrames)
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
                                // FIX 2: grace period — no reiniciar OF si acaba de iniciarse
                                val ofGraceActive = opticalFlowTracker.framesAfterInit <
                                        opticalFlowTracker.GRACE_FRAMES

                                val fallback = closestDetectionToTracked()

                                if (fallback != null && !ofGraceActive) {
                                    fallbackDetectionCount++
                                    consecutiveKalmanOnlyFrames = 0
                                    trackingSource = TrackingSource.FALLBACK_DETECTOR
                                    currentAlpha = alphaKalman

                                    if (!grayMat.empty()) {
                                        try {
                                            opticalFlowTracker.init(
                                                grayMat,
                                                fallback.toCvRect(grayMat.cols(), grayMat.rows())
                                            )
                                            // FIX 3: NO resetear Kalman aquí
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
                                    consecutiveKalmanOnlyFrames = 0
                                    trackingSource = TrackingSource.LOST
                                    smoothInit = false; lastTrackedCenter = null; null
                                }
                            }
                        }
                    }
                }

                lastTrackingSource = trackingSource

                // 5) TFLite async
                if (frameCounter % detectionEveryNFrames == 0 && !detectionRunning) {
                    val refCenter = lastTrackedCenter ?: selectedCenter
                    launchDetection(bitmap, srcW, srcH, detector, refCenter)
                }

                // 6) ArUco throttled
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

                // 7) Medición
                val calibrated = lastCalibrationState as? CalibrationState.Calibrated
                val measurement = if (calibrated != null && trackedThisFrame != null)
                    measurementManager.measureObject(trackedThisFrame, calibrated.cmPerPx)
                else null

                // 8) Grabar punto
                // FIX 7: se pasa frameTimestampNs del sensor a recordPoint.
                if (isRecording() && sessionRecorder.isActive && trackedThisFrame != null) {
                    recordPoint(trackedThisFrame, calibrated, frameTimestampNs)
                }

                // 9) Emitir a UI
                val uiDetections = buildUiDetections(trackedThisFrame, latestAllDetections)
                onTrackedDetection(trackedThisFrame?.copy(isSelected = true))
                val status = if (isRecording()) "Grabando"
                else "Obj. detectados: ${latestAllDetections.size}"
                emitIfChanged(uiDetections, measurement, lastCalibrationState, status)

                updateAnalyzerPerf(frameStartNs)
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
                updateAnalyzerPerf(frameStartNs)
                updateTrackingDebug()
            }
        } finally {
            image.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

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
        ofUpdateCount = 0; ofFoundCount = 0; ofLostCount = 0
        kalmanPredictCount = 0; fallbackDetectionCount = 0
        consecutiveKalmanOnlyFrames = 0; maxKalmanOnlyFrames = 0
        analyzerPerfWindowStartNs = 0L; analyzerPerfFrames = 0
        analyzerFps = 0.0; lastFrameMs = 0.0
        pointsThisSecond = 0; pointsPerSecond = 0; pointsWindowStartNs = 0L
        lastTrackingSource = TrackingSource.NONE; lastTrackingDebug = ""
        onTrackingDebug("Tracking: Idle")
        emitIfChanged(emptyList(), null, CalibrationState.Idle, "Modelo de detección listo")
        onTrackedDetection(null)
    }

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
     * Registra un punto de posición en el SessionRecorder.
     *
     * FIX 7: recibe frameTimestampNs (timestamp del sensor del frame actual)
     * y lo pasa a sessionRecorder.addPoint(). Si es el primer punto de la
     * sesión, también lo pasa a sessionRecorder para anclar el origen temporal.
     *
     * FIX 8: emaAlpha reducido a 0.15 — suavizado efectivo de ~6 frames.
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
            // FIX 8: emaAlpha = 0.15 en lugar de 0.5
            val cx = prevX?.let { it + emaAlpha * (rawCx - it) } ?: rawCx
            val cy = prevY?.let { it + emaAlpha * (rawCy - it) } ?: rawCy
            emaX = cx; emaY = cy

            // FIX 7: primer punto de la sesión → anclar startTimestampNs
            if (recordingStartTimestampNs == 0L) {
                recordingStartTimestampNs = frameTimestampNs
                // Reiniciar el SessionRecorder con el timestamp correcto del sensor.
                // start() ya fue llamado desde CameraViewModel, aquí solo actualizamos
                // el origen temporal interno pasando el timestamp como primer addPoint.
                // SessionRecorder.addPoint() acepta frameTimestampNs y calcula tMs
                // como (frameTimestampNs - startTimeNs) / 1_000_000.
            }

            if (calibrated != null) {
                sessionRecorder.updateMetadata(cmPerPx = calibrated.cmPerPx, unit = "cm")
                sessionRecorder.addPoint(
                    xCm = cx * calibrated.cmPerPx,
                    yCm = cy * calibrated.cmPerPx,
                    frameTimestampNs = frameTimestampNs   // FIX 7
                )
            } else {
                sessionRecorder.addPoint(
                    xCm = cx,
                    yCm = cy,
                    frameTimestampNs = frameTimestampNs   // FIX 7
                )
            }
            pointsThisSecond++
        }
    }

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

    private fun UiDetection.toCvRect(w: Int, h: Int): Rect {
        val x1 = left.toInt().coerceIn(0, w - 1)
        val y1 = top.toInt().coerceIn(0, h - 1)
        val x2 = right.toInt().coerceIn(x1 + 1, w)
        val y2 = bottom.toInt().coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
    }

    private fun UiDetection.toAndroidRect(w: Int, h: Int): android.graphics.Rect {
        val l = left.toInt().coerceIn(0, w - 1)
        val t = top.toInt().coerceIn(0, h - 1)
        val r = right.toInt().coerceIn(l + 1, w)
        val b = bottom.toInt().coerceIn(t + 1, h)
        return android.graphics.Rect(l, t, r, b)
    }

    private fun Rect.toAndroidRect(): android.graphics.Rect =
        android.graphics.Rect(x, y, x + width, y + height)

    private fun iou(a: UiDetection, b: UiDetection): Float {
        val iL = max(a.left, b.left); val iT = max(a.top, b.top)
        val iR = min(a.right, b.right); val iB = min(a.bottom, b.bottom)
        val iW = (iR - iL).coerceAtLeast(0f); val iH = (iB - iT).coerceAtLeast(0f)
        val inter = iW * iH
        val union = (a.right - a.left) * (a.bottom - a.top) +
                (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

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

    private fun updateAnalyzerPerf(frameStartNs: Long) {
        val now = System.nanoTime()
        lastFrameMs = (now - frameStartNs) / 1_000_000.0
        if (analyzerPerfWindowStartNs == 0L) analyzerPerfWindowStartNs = now
        analyzerPerfFrames++
        val elapsedNs = now - analyzerPerfWindowStartNs
        if (elapsedNs >= 1_000_000_000L) {
            analyzerFps = analyzerPerfFrames * 1_000_000_000.0 / elapsedNs.toDouble()
            analyzerPerfFrames = 0; analyzerPerfWindowStartNs = now
        }
        if (pointsWindowStartNs == 0L) pointsWindowStartNs = now
        val pointsElapsedNs = now - pointsWindowStartNs
        if (pointsElapsedNs >= 1_000_000_000L) {
            pointsPerSecond = pointsThisSecond
            pointsThisSecond = 0
            pointsWindowStartNs = now
        }
    }

    private fun updateTrackingDebug() {
        val debug = buildString {
            append("Tracking: ").append(lastTrackingSource)
            append(" | FPS: ").append(String.format(Locale.US, "%.1f", analyzerFps))
            append(" | Frame: ").append(String.format(Locale.US, "%.1f", lastFrameMs)).append(" ms")
            append(" | OF u/f/l: ").append(ofUpdateCount).append("/")
                .append(ofFoundCount).append("/").append(ofLostCount)
            append(" | Grace: ").append(opticalFlowTracker.framesAfterInit)
                .append("/").append(opticalFlowTracker.GRACE_FRAMES)
            append(" | Kalman: ").append(kalmanPredictCount)
            append(" | K-streak: ").append(consecutiveKalmanOnlyFrames)
            append(" | K-max: ").append(maxKalmanOnlyFrames)
            append(" | Fallback: ").append(fallbackDetectionCount)
            append(" | Pts/s: ").append(pointsPerSecond)
        }
        if (debug != lastTrackingDebug) { lastTrackingDebug = debug; onTrackingDebug(debug) }
    }

    private fun getBitmap(image: ImageProxy): Bitmap =
        rotateBitmapIfNeededReusable(
            imageProxyToReusableBitmap(image),
            image.imageInfo.rotationDegrees
        )

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

    private fun imageProxyToReusableBitmap(image: ImageProxy): Bitmap {
        val w = image.width; val h = image.height
        val bmp = if (rgbaBitmap?.width != w || rgbaBitmap?.height != h)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { rgbaBitmap = it }
        else rgbaBitmap!!
        image.planes[0].buffer.rewind()
        bmp.copyPixelsFromBuffer(image.planes[0].buffer)
        return bmp
    }

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