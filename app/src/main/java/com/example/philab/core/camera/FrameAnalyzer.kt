package com.example.philab.core.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.philab.core.calibration.ArucoScaleDetector
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.core.detection.TfliteObjectDetector
import com.example.philab.domain.experiment.SessionRecorder
import com.example.philab.core.measurement.MeasurementManager
import com.example.philab.core.measurement.MeasurementResult
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
 * FrameAnalyzer — tracking con Optical Flow + Kalman + TFLite async.
 *
 * Pipeline por frame:
 *  1. Bitmap → grayMat  (analyzer thread)
 *  2. OpticalFlowTracker.update()  → tracking fluido cada frame
 *  3. BboxKalman.update/predict()  → suavizado + predicción oclusión
 *  4. TFLite cada N frames async   → re-detección, no bloquea
 *  5. ArUco cada 7 frames          → calibración escala
 *
 * Anti-SIGSEGV:
 *  - Todos los Mats OpenCV son lazy y solo usados en el analyzer thread.
 *  - TFLite nunca toca Mats — trabaja solo con Bitmap copiado.
 *  - El bbox de TFLite se valida contra el tamaño actual de grayMat
 *    antes de pasarlo a OpticalFlowTracker.init().
 *  - prevGray/grayMat size se verifican antes de calcOpticalFlowPyrLK.
 */
class FrameAnalyzer(
    private val detectorProvider: () -> TfliteObjectDetector,
    private val isCameraActive: () -> Boolean,
    private val isRecording: () -> Boolean,
    private val onFps: (Double) -> Unit,
    private val onTotalFrames: (Long) -> Unit,
    private val onDetections: (List<UiDetection>) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onCalibration: (CalibrationState) -> Unit,
    private val onMeasurement: (MeasurementResult?) -> Unit,
    private val markerSizeCmProvider: () -> Float,
    private val enterThresholdProvider: () -> Float,
    private val maxPerClassProvider: () -> Int,
    private val maxPerFrameProvider: () -> Int,
    private val confirmFrames: Int = 2,
    private val dropFrames: Int = 3,
    private val arucoEveryNFrames: Int = 7,
    private val detectionEveryNFrames: Int = 15,
    private val selectedCenterProvider: () -> Pair<Float, Float>?,
    private val onTrackedDetection: (UiDetection?) -> Unit,
    private val sessionRecorder: SessionRecorder
) : ImageAnalysis.Analyzer {

    // ── FPS ──────────────────────────────────────────────────────────────────
    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var totalFrames = 0L
    private val windowNs = 250_000_000L

    // ── Bitmap reuse ─────────────────────────────────────────────────────────
    private var processor: ImageProcessor? = null
    private var lastCrop = -1; private var lastW = -1; private var lastH = -1
    private var rgbaBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private var rotatedCanvas: Canvas? = null
    private val rotateMatrix = Matrix()
    private val reusableTensorImage = TensorImage()

    // ── OpenCV Mats — lazy, solo en analyzer thread ───────────────────────────
    private val rgbMat  by lazy { Mat() }
    private val grayMat by lazy { Mat() }

    // ── ArUco ────────────────────────────────────────────────────────────────
    private val arucoDetector     by lazy { ArucoScaleDetector() }
    private val measurementManager = MeasurementManager()
    private var arucoFrameCounter  = 0
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching

    // ── Optical Flow + Kalman — lazy, solo en analyzer thread ────────────────
    private val opticalFlowTracker by lazy { OpticalFlowTracker() }
    private val bboxKalman         by lazy { BboxKalman() }

    // ── TFLite async ─────────────────────────────────────────────────────────
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var detectionRunning = false
    private var frameCounter = 0

    // Resultado pendiente de TFLite — escrito en detectionScope,
    // consumido en el analyzer thread al inicio del siguiente frame.
    // Incluye srcW/srcH del frame en que fue generado para validar bounds.
    private data class PendingDetection(
        val detection: UiDetection,
        val allDetections: List<UiDetection>,
        val srcW: Int,
        val srcH: Int
    )
    @Volatile private var pendingDetection: PendingDetection? = null
    @Volatile private var latestAllDetections: List<UiDetection> = emptyList()

    // ── Estado de tracking ────────────────────────────────────────────────────
    private var lastKnownCenter: Pair<Float, Float>? = null

    // ── EMA visual del bbox ───────────────────────────────────────────────────
    private var smoothL = 0f; private var smoothT = 0f
    private var smoothR = 0f; private var smoothB = 0f
    private var smoothInit = false
    private val drawAlpha = 0.4f

    // ── EMA centroide grabado ─────────────────────────────────────────────────
    private val emaAlpha = 0.35f
    private var emaX: Float? = null
    private var emaY: Float? = null
    private val maxJumpPx = 120f

    // ── Emit dedup ────────────────────────────────────────────────────────────
    private var lastEmitDetections: List<UiDetection> = emptyList()
    private var lastMeasurement: MeasurementResult? = null
    private var lastStatus: String = ""

    // ─────────────────────────────────────────────────────────────────────────
    override fun analyze(image: ImageProxy) {
        try {
            if (!isCameraActive()) {
                handleCameraInactive()
                return
            }

            tickFps()
            frameCounter++

            try {
                val detector = detectorProvider()
                val bitmap   = getBitmap(image)
                val srcW     = bitmap.width
                val srcH     = bitmap.height

                // ── 1. Bitmap → grayMat (analyzer thread, OpenCV seguro) ──
                Utils.bitmapToMat(bitmap, rgbMat)
                Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                // ── 2. Aplicar resultado pendiente de TFLite ──────────────
                // CRÍTICO: validar que el bbox es compatible con grayMat actual
                val pending = pendingDetection
                if (pending != null) {
                    pendingDetection = null
                    latestAllDetections = pending.allDetections
                    // Solo inicializar OF si las dimensiones coinciden
                    if (pending.srcW == srcW && pending.srcH == srcH &&
                        !grayMat.empty() && grayMat.cols() == srcW && grayMat.rows() == srcH) {
                        val cvBox = pending.detection.toCvRect(srcW, srcH)
                        opticalFlowTracker.init(grayMat, cvBox)
                        bboxKalman.reset()
                    }
                }

                // ── 3. Gestionar cambio de selección ──────────────────────
                val selectedCenter = selectedCenterProvider()
                if (selectedCenter != lastKnownCenter) {
                    if (selectedCenter == null) {
                        opticalFlowTracker.reset()
                        bboxKalman.reset()
                        smoothInit = false
                        emaX = null; emaY = null
                    } else if (lastKnownCenter == null) {
                        // Nueva selección — forzar detección inmediata
                        // (el OF se inicializará cuando llegue el primer resultado TFLite)
                        opticalFlowTracker.reset()
                        bboxKalman.reset()
                        // Si ya hay detecciones, inicializar desde la más cercana ahora
                        val (tapX, tapY) = selectedCenter
                        val seed = latestAllDetections.minByOrNull { d ->
                            val cx = (d.left + d.right) / 2f
                            val cy = (d.top + d.bottom) / 2f
                            (cx - tapX) * (cx - tapX) + (cy - tapY) * (cy - tapY)
                        }
                        if (seed != null && !grayMat.empty() &&
                            grayMat.cols() == srcW && grayMat.rows() == srcH) {
                            val cvBox = seed.toCvRect(srcW, srcH)
                            opticalFlowTracker.init(grayMat, cvBox)
                        }
                    }
                    lastKnownCenter = selectedCenter
                }

                // ── 4. Optical Flow cada frame ────────────────────────────
                val trackedThisFrame: UiDetection? = if (selectedCenter == null) {
                    null
                } else {
                    val (selX, selY) = selectedCenter

                    fun closestDetectionToSelection(): UiDetection? {
                        return latestAllDetections.minByOrNull { d ->
                            val cx = (d.left + d.right) / 2f
                            val cy = (d.top + d.bottom) / 2f
                            val dx = cx - selX
                            val dy = cy - selY
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
                            val filteredBox = bboxKalman.update(ofResult.bbox.toAndroidRect())
                            smoothBbox(
                                filteredBox,
                                srcW,
                                srcH,
                                latestAllDetections.firstOrNull { it.isSelected }
                                    ?: closestDetectionToSelection()
                                    ?: latestAllDetections.firstOrNull()
                            )
                        }

                        is OpticalFlowTracker.TrackResult.Lost -> {
                            val predicted = bboxKalman.predict()
                            if (predicted != null) {
                                smoothBbox(
                                    predicted,
                                    srcW,
                                    srcH,
                                    closestDetectionToSelection() ?: latestAllDetections.firstOrNull()
                                )
                            } else {
                                // Fallback directo a TFLite si OF/Kalman no entregan caja
                                val fallback = closestDetectionToSelection()
                                if (fallback != null) {
                                    if (!opticalFlowTracker.isActive() && !grayMat.empty()) {
                                        try {
                                            opticalFlowTracker.init(grayMat, fallback.toCvRect(srcW, srcH))
                                            bboxKalman.reset()
                                        } catch (_: Throwable) {
                                        }
                                    }
                                    smoothBbox(
                                        fallback.toAndroidRect(),
                                        srcW,
                                        srcH,
                                        fallback
                                    )
                                } else {
                                    smoothInit = false
                                    null
                                }
                            }
                        }
                    }
                }

                // ── 5. TFLite async cada N frames ─────────────────────────
                if (frameCounter % detectionEveryNFrames == 0 && !detectionRunning) {
                    launchDetection(bitmap, srcW, srcH, detector, selectedCenter)
                }

                // ── 6. ArUco throttled ────────────────────────────────────
                arucoFrameCounter++
                if (arucoFrameCounter % arucoEveryNFrames == 0) {
                    try {
                        lastCalibrationState = arucoDetector.detectScale(
                            bitmap = bitmap, markerSizeCm = markerSizeCmProvider()
                        )
                    } catch (e: Throwable) {
                        lastCalibrationState =
                            CalibrationState.Error("ArUco: ${e.javaClass.simpleName}")
                    }
                }

                // ── 7. Medición ───────────────────────────────────────────
                val calibrated  = lastCalibrationState as? CalibrationState.Calibrated
                val measurement = if (calibrated != null && trackedThisFrame != null) {
                    measurementManager.measureObject(trackedThisFrame, calibrated.cmPerPx)
                } else null

                // ── 8. Grabar punto ───────────────────────────────────────
                if (isRecording() && sessionRecorder.isActive && trackedThisFrame != null) {
                    recordPoint(trackedThisFrame, calibrated)
                }

                // ── 9. Emitir a UI ────────────────────────────────────────
                val uiDetections = buildUiDetections(trackedThisFrame, latestAllDetections)
                onTrackedDetection(trackedThisFrame?.copy(isSelected = true))
                val status = if (isRecording()) "Grabando"
                else "Obj. detectados: ${latestAllDetections.size}"
                emitIfChanged(uiDetections, measurement, lastCalibrationState, status)

            } catch (e: Exception) {
                opticalFlowTracker.reset()
                bboxKalman.reset()
                emitIfChanged(emptyList(), null, CalibrationState.Searching,
                    "ERR: ${e.javaClass.simpleName}")
                onTrackedDetection(null)
            }
        } finally {
            image.close()
        }
    }

    // ── TFLite launch ─────────────────────────────────────────────────────────

    private fun launchDetection(
        bitmap: Bitmap,
        srcW: Int,
        srcH: Int,
        detector: TfliteObjectDetector,
        selectedCenter: Pair<Float, Float>?
    ) {
        // Copiar bitmap ANTES de lanzar la coroutine — el original puede reciclarse
        val bmpCopy   = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        val cropSize  = min(srcW, srcH)
        val xOffset   = ((srcW - cropSize) / 2f).coerceAtLeast(0f)
        val yOffset   = ((srcH - cropSize) / 2f).coerceAtLeast(0f)
        val scaleX    = cropSize.toFloat() / detector.inputWidth
        val scaleY    = cropSize.toFloat() / detector.inputHeight
        val enterThr  = enterThresholdProvider()
        val maxPClass = maxPerClassProvider().coerceIn(1, 10)
        val maxPFrame = maxPerFrameProvider().coerceIn(1, 6)

        detectionScope.launch {
            detectionRunning = true
            try {
                ensureProcessor(cropSize, detector.inputWidth, detector.inputHeight)
                val ti = TensorImage()
                ti.load(bmpCopy)
                val input   = processor!!.process(ti)
                val results = detector.detect(input)
                bmpCopy.recycle()

                val raw = results.mapNotNull { det ->
                    val best = det.categories.maxByOrNull { it.score } ?: return@mapNotNull null
                    if (best.score < enterThr) return@mapNotNull null
                    val box = det.boundingBox
                    UiDetection(
                        label  = best.label ?: "Object",
                        score  = best.score,
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

                val best = if (selectedCenter != null) {
                    val (tapX, tapY) = selectedCenter
                    raw.minByOrNull { d ->
                        val cx = (d.left + d.right) / 2f
                        val cy = (d.top + d.bottom) / 2f
                        (cx - tapX) * (cx - tapX) + (cy - tapY) * (cy - tapY)
                    }
                } else null

                val allWithSelection = raw.map { it.copy(isSelected = it == best) }

                if (best != null) {
                    // Pasar srcW/srcH junto al resultado para validar en el analyzer thread
                    pendingDetection = PendingDetection(best, allWithSelection, srcW, srcH)
                } else {
                    // Sin selección: solo actualizar la lista de detecciones
                    latestAllDetections = allWithSelection
                }
            } catch (e: Exception) {
                // silencioso
            } finally {
                detectionRunning = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun handleCameraInactive() {
        arucoDetector.reset()
        lastCalibrationState = CalibrationState.Idle
        opticalFlowTracker.reset()
        bboxKalman.reset()
        smoothInit = false
        emaX = null; emaY = null
        lastKnownCenter = null
        latestAllDetections = emptyList()
        pendingDetection = null
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

    private fun recordPoint(tracked: UiDetection, calibrated: CalibrationState.Calibrated?) {
        val rawCx = (tracked.left + tracked.right) / 2f
        val rawCy = (tracked.top + tracked.bottom) / 2f
        val prevX = emaX; val prevY = emaY
        val isJump = prevX != null && prevY != null &&
                (abs(rawCx - prevX) > maxJumpPx || abs(rawCy - prevY) > maxJumpPx)
        if (!isJump) {
            val cx = prevX?.let { it + emaAlpha * (rawCx - it) } ?: rawCx
            val cy = prevY?.let { it + emaAlpha * (rawCy - it) } ?: rawCy
            emaX = cx; emaY = cy
            if (calibrated != null) {
                sessionRecorder.updateMetadata(cmPerPx = calibrated.cmPerPx, unit = "cm")
                sessionRecorder.addPoint(xCm = cx * calibrated.cmPerPx, yCm = cy * calibrated.cmPerPx)
            } else {
                sessionRecorder.addPoint(xCm = cx, yCm = cy)
            }
        }
    }

    private fun buildUiDetections(
        tracked: UiDetection?,
        all: List<UiDetection>
    ): List<UiDetection> {
        val marked = all.map { d ->
            val isThis = tracked != null && iou(d, tracked) > 0.3f
            d.copy(isSelected = isThis)
        }
        // Si hay tracking activo pero el objeto no está en la lista (entre detecciones TFLite),
        // agregar el bbox trackeado para que siempre se dibuje
        return if (tracked != null && marked.none { it.isSelected }) {
            listOf(tracked.copy(isSelected = true)) + marked
        } else marked
    }

    private fun smoothBbox(
        box: android.graphics.Rect,
        srcW: Int, srcH: Int,
        ref: UiDetection?
    ): UiDetection {
        val fL = box.left.toFloat()
        val fT = box.top.toFloat()
        val fR = box.right.toFloat()
        val fB = box.bottom.toFloat()
        if (!smoothInit) {
            smoothL = fL; smoothT = fT; smoothR = fR; smoothB = fB
            smoothInit = true
        } else {
            smoothL += drawAlpha * (fL - smoothL)
            smoothT += drawAlpha * (fT - smoothT)
            smoothR += drawAlpha * (fR - smoothR)
            smoothB += drawAlpha * (fB - smoothB)
        }
        return UiDetection(
            label  = ref?.label ?: "Object",
            score  = ref?.score ?: 1f,
            left   = smoothL.coerceIn(0f, srcW.toFloat()),
            top    = smoothT.coerceIn(0f, srcH.toFloat()),
            right  = smoothR.coerceIn(0f, srcW.toFloat()),
            bottom = smoothB.coerceIn(0f, srcH.toFloat()),
            sourceWidth = srcW, sourceHeight = srcH,
            isSelected = true
        )
    }

    /** Convierte UiDetection a opencv Rect, asegurando que está dentro de srcW×srcH */
    private fun UiDetection.toCvRect(w: Int, h: Int): Rect {
        val x1 = left.toInt().coerceIn(0, w - 1)
        val y1 = top.toInt().coerceIn(0, h - 1)
        val x2 = right.toInt().coerceIn(x1 + 1, w)
        val y2 = bottom.toInt().coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
    }

    private fun UiDetection.toAndroidRect(): android.graphics.Rect {
        return android.graphics.Rect(
            left.toInt(),
            top.toInt(),
            right.toInt(),
            bottom.toInt()
        )
    }

    /** Convierte opencv Rect a android.graphics.Rect para BboxKalman */
    private fun Rect.toAndroidRect(): android.graphics.Rect =
        android.graphics.Rect(x, y, x + width, y + height)

    private fun iou(a: UiDetection, b: UiDetection): Float {
        val iL = max(a.left, b.left);   val iT = max(a.top, b.top)
        val iR = min(a.right, b.right); val iB = min(a.bottom, b.bottom)
        val iW = (iR - iL).coerceAtLeast(0f)
        val iH = (iB - iT).coerceAtLeast(0f)
        val inter = iW * iH
        val union = (a.right-a.left)*(a.bottom-a.top) +
                (b.right-b.left)*(b.bottom-b.top) - inter
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
        if (measurement != lastMeasurement) { lastMeasurement = measurement; onMeasurement(measurement) }
        val shouldEmitCal = calibration is CalibrationState.Calibrated ||
                calibration::class != lastCalibrationState::class
        if (shouldEmitCal) onCalibration(calibration)
        if (status != lastStatus) { lastStatus = status; onStatus(status) }
    }

    // ── Bitmap utils ──────────────────────────────────────────────────────────

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
        val bmp = if (rgbaBitmap?.width != w || rgbaBitmap?.height != h) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { rgbaBitmap = it }
        } else rgbaBitmap!!
        image.planes[0].buffer.rewind()
        bmp.copyPixelsFromBuffer(image.planes[0].buffer)
        return bmp
    }

    private fun rotateBitmapIfNeededReusable(source: Bitmap, rotationDegrees: Int): Bitmap {
        val norm = ((rotationDegrees % 360) + 360) % 360
        if (norm == 0) return source
        val targetW = if (norm == 90 || norm == 270) source.height else source.width
        val targetH = if (norm == 90 || norm == 270) source.width else source.height
        val target = if (rotatedBitmap?.width != targetW || rotatedBitmap?.height != targetH) {
            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                .also { rotatedBitmap = it; rotatedCanvas = Canvas(it); lastRotationDegrees = -1 }
        } else rotatedBitmap!!
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