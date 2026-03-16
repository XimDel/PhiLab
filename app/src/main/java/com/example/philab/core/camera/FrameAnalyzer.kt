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
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    private val selectedCenterProvider: () -> Pair<Float, Float>?,
    private val onTrackedDetection: (UiDetection?) -> Unit,
    private val sessionRecorder: SessionRecorder
) : ImageAnalysis.Analyzer {

    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var totalFrames = 0L
    private val windowNs = 250_000_000L

    private var processor: ImageProcessor? = null
    private var lastCrop = -1; private var lastW = -1; private var lastH = -1
    private var rgbaBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private var rotatedCanvas: Canvas? = null
    private val rotateMatrix = Matrix()
    private val reusableTensorImage = TensorImage()

    private val arucoDetector by lazy { ArucoScaleDetector() }
    private val measurementManager = MeasurementManager()

    private var arucoFrameCounter = 0
    private var lastCalibrationState: CalibrationState = CalibrationState.Searching

    // ── IoU tracking ──────────────────────────────────────────────────────────
    private var lastTrackedBox: UiDetection? = null
    private val iouThreshold = 0.25f
    private var lastKnownCenter: Pair<Float, Float>? = null

    // ── Predicción de velocidad ───────────────────────────────────────────────
    private var velX = 0f
    private var velY = 0f
    private val velAlpha = 0.4f
    private var framesMissed = 0
    private val maxFramesMissed = 8

    // ── EMA sobre el centroide grabado ────────────────────────────────────────
    private val emaAlpha = 0.35f
    private var emaX: Float? = null
    private var emaY: Float? = null

    // ── Protección contra saltos grandes ─────────────────────────────────────
    private val maxJumpPx = 120f

    private var lastDetections: List<UiDetection> = emptyList()
    private var lastMeasurement: MeasurementResult? = null
    private var lastStatus: String = ""

    private data class Track(
        var active: Boolean = false,
        var confirmCount: Int = 0,
        var missCount: Int = 0,
        var last: UiDetection? = null
    )
    private val tracks = HashMap<String, Track>()

    private fun ensureProcessor(cropSize: Int, modelW: Int, modelH: Int) {
        if (processor == null || cropSize != lastCrop || modelW != lastW || modelH != lastH) {
            processor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(ResizeOp(modelH, modelW, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            lastCrop = cropSize; lastW = modelW; lastH = modelH
        }
    }

    private fun UiDetection.shifted(dx: Float, dy: Float): UiDetection = copy(
        left   = (left   + dx).coerceIn(0f, sourceWidth.toFloat()),
        top    = (top    + dy).coerceIn(0f, sourceHeight.toFloat()),
        right  = (right  + dx).coerceIn(0f, sourceWidth.toFloat()),
        bottom = (bottom + dy).coerceIn(0f, sourceHeight.toFloat())
    )

    private fun resetVelocityState() {
        velX = 0f; velY = 0f
        framesMissed = 0
        emaX = null; emaY = null
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (!isCameraActive()) {
                arucoDetector.reset()
                lastCalibrationState = CalibrationState.Idle
                lastTrackedBox = null
                lastKnownCenter = null
                tracks.clear()
                resetVelocityState()
                emitIfChanged(emptyList(), null, CalibrationState.Idle, "Listo")
                onTrackedDetection(null)
                return
            }

            val now = System.nanoTime()
            if (windowStartNs == 0L) windowStartNs = now
            framesInWindow++; totalFrames++
            onTotalFrames(totalFrames)
            val elapsedNs = now - windowStartNs
            if (elapsedNs >= windowNs) {
                onFps(framesInWindow * 1e9 / elapsedNs.toDouble())
                windowStartNs = now; framesInWindow = 0
            }

            try {
                val detector = detectorProvider()
                val bitmap0 = imageProxyToReusableBitmap(image)
                val bitmap = rotateBitmapIfNeededReusable(bitmap0, image.imageInfo.rotationDegrees)

                val srcW = bitmap.width; val srcH = bitmap.height
                val cropSize = min(srcW, srcH)
                val xOffset = ((srcW - cropSize) / 2f).coerceAtLeast(0f)
                val yOffset = ((srcH - cropSize) / 2f).coerceAtLeast(0f)

                ensureProcessor(cropSize, detector.inputWidth, detector.inputHeight)
                reusableTensorImage.load(bitmap)
                val input = processor!!.process(reusableTensorImage)
                val results = detector.detect(input)

                val scaleX = cropSize.toFloat() / detector.inputWidth.toFloat()
                val scaleY = cropSize.toFloat() / detector.inputHeight.toFloat()

                val raw = results.mapNotNull { det ->
                    val best = det.categories.maxByOrNull { it.score } ?: return@mapNotNull null
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

                val maxPerClass = maxPerClassProvider().coerceIn(1, 10)
                val maxPerFrame = maxPerFrameProvider().coerceIn(1, 6)

                val perLabelTopN = raw.groupBy { it.label }
                    .flatMap { (_, list) -> list.sortedByDescending { it.score }.take(maxPerClass) }
                    .sortedByDescending { it.score }.take(maxPerFrame)

                val candidatesByKey = perLabelTopN.groupBy { it.label }
                    .flatMap { (label, list) ->
                        list.sortedByDescending { it.score }.take(maxPerClass)
                            .mapIndexed { idx, det -> "${label}#$idx" to det }
                    }.toMap()

                val enterThreshold = enterThresholdProvider()
                val exitThreshold = (enterThreshold - 0.15f).coerceAtLeast(0.1f)

                for ((key, track) in tracks) {
                    if (candidatesByKey[key] == null) {
                        if (track.active) track.missCount++
                        track.confirmCount = 0
                    }
                }
                for ((key, cand) in candidatesByKey) {
                    val track = tracks.getOrPut(key) { Track() }
                    if (!track.active) {
                        if (cand.score >= enterThreshold) {
                            track.confirmCount++
                            if (track.confirmCount >= confirmFrames) {
                                track.active = true; track.missCount = 0
                            }
                        } else track.confirmCount = 0
                    } else {
                        if (cand.score < exitThreshold) {
                            track.missCount++
                        } else {
                            track.missCount = 0; track.last = cand
                        }
                    }
                    if (track.active && track.missCount < dropFrames) track.last = cand
                }
                tracks.entries.removeAll { (_, t) -> t.active && t.missCount >= dropFrames }

                val stable = tracks.values.mapNotNull { it.last }
                    .sortedByDescending { it.score }.take(maxPerFrame)

                val selectedCenter = selectedCenterProvider()

                if (selectedCenter != null && selectedCenter != lastKnownCenter) {
                    lastTrackedBox = null
                    lastKnownCenter = selectedCenter
                    resetVelocityState()
                } else if (selectedCenter == null) {
                    lastKnownCenter = null
                    resetVelocityState()
                }

                // ── IoU + predicción de velocidad ─────────────────────────────
                val trackedThisFrame: UiDetection? = when {
                    selectedCenter == null -> {
                        lastTrackedBox = null
                        null
                    }

                    lastTrackedBox == null -> {
                        val (tapX, tapY) = selectedCenter
                        stable.minByOrNull { det ->
                            val cx = (det.left + det.right) / 2f
                            val cy = (det.top + det.bottom) / 2f
                            (cx - tapX) * (cx - tapX) + (cy - tapY) * (cy - tapY)
                        }?.also { found ->
                            lastTrackedBox = found
                            framesMissed = 0
                        }
                    }

                    else -> {
                        val prev = lastTrackedBox!!

                        val predicted = if (framesMissed > 0 && (velX != 0f || velY != 0f)) {
                            prev.shifted(velX * framesMissed, velY * framesMissed)
                        } else prev

                        val best = stable.maxByOrNull { iou(it, predicted) }
                        val bestIou = if (best != null) iou(best, predicted) else 0f

                        if (best != null && bestIou >= iouThreshold) {
                            val prevCx = (prev.left + prev.right) / 2f
                            val prevCy = (prev.top + prev.bottom) / 2f
                            val newCx  = (best.left + best.right) / 2f
                            val newCy  = (best.top + best.bottom) / 2f

                            velX = velX + velAlpha * ((newCx - prevCx) - velX)
                            velY = velY + velAlpha * ((newCy - prevCy) - velY)

                            framesMissed = 0
                            lastTrackedBox = best
                            best
                        } else {
                            framesMissed++

                            if (framesMissed <= maxFramesMissed && (velX != 0f || velY != 0f)) {
                                predicted
                            } else {
                                if (framesMissed > maxFramesMissed) {
                                    lastTrackedBox = null
                                    resetVelocityState()
                                }
                                null
                            }
                        }
                    }
                }

                val stableWithSelection = stable.map { det ->
                    val isThisTheTracked = trackedThisFrame != null &&
                            det.left == trackedThisFrame.left &&
                            det.top == trackedThisFrame.top
                    det.copy(isSelected = isThisTheTracked)
                }

                onTrackedDetection(trackedThisFrame?.copy(isSelected = true))

                // ── ArUco throttling ──────────────────────────────────────────
                arucoFrameCounter++
                if (arucoFrameCounter % arucoEveryNFrames == 0) {
                    try {
                        lastCalibrationState = arucoDetector.detectScale(
                            bitmap = bitmap, markerSizeCm = markerSizeCmProvider()
                        )
                    } catch (e: Throwable) {
                        lastCalibrationState = CalibrationState.Error("OpenCV: ${e.javaClass.simpleName}")
                    }
                }

                val targetForMeasurement = trackedThisFrame ?: stable.firstOrNull()
                val calibrated = lastCalibrationState as? CalibrationState.Calibrated

                val measurement = if (calibrated != null && targetForMeasurement != null) {
                    measurementManager.measureObject(
                        detection = targetForMeasurement,
                        cmPerPx = calibrated.cmPerPx
                    )
                } else null

                if (isRecording() && sessionRecorder.isActive &&
                    trackedThisFrame != null && framesMissed == 0) {

                    val rawCx = (trackedThisFrame.left + trackedThisFrame.right) / 2f
                    val rawCy = (trackedThisFrame.top + trackedThisFrame.bottom) / 2f

                    val prevX = emaX
                    val prevY = emaY
                    val isJump = prevX != null && prevY != null &&
                            (abs(rawCx - prevX) > maxJumpPx || abs(rawCy - prevY) > maxJumpPx)

                    if (!isJump) {
                        val smoothCx = prevX?.let { it + emaAlpha * (rawCx - it) } ?: rawCx
                        val smoothCy = prevY?.let { it + emaAlpha * (rawCy - it) } ?: rawCy
                        emaX = smoothCx
                        emaY = smoothCy

                        if (calibrated != null) {
                            sessionRecorder.updateMetadata(cmPerPx = calibrated.cmPerPx, unit = "cm")
                            sessionRecorder.addPoint(
                                xCm = smoothCx * calibrated.cmPerPx,
                                yCm = smoothCy * calibrated.cmPerPx
                            )
                        } else {
                            sessionRecorder.addPoint(xCm = smoothCx, yCm = smoothCy)
                        }
                    }
                }

                emitIfChanged(
                    stableWithSelection, measurement, lastCalibrationState,
                    "OK (Obj. detectados: ${stable.size})"
                )

            } catch (e: Exception) {
                lastTrackedBox = null
                resetVelocityState()
                emitIfChanged(emptyList(), null, CalibrationState.Searching,
                    "ERR: ${e.javaClass.simpleName}")
                onTrackedDetection(null)
            }
        } finally {
            image.close()
        }
    }

    private fun iou(a: UiDetection, b: UiDetection): Float {
        val interLeft   = max(a.left,   b.left)
        val interTop    = max(a.top,    b.top)
        val interRight  = min(a.right,  b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = (interRight  - interLeft).coerceAtLeast(0f)
        val interH = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interW * interH
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = aArea + bArea - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    private fun emitIfChanged(
        detections: List<UiDetection>,
        measurement: MeasurementResult?,
        calibration: CalibrationState,
        status: String
    ) {
        val detectionsChanged = detections.size != lastDetections.size ||
                detections.zip(lastDetections).any { (a, b) ->
                    a.left != b.left || a.top != b.top ||
                            a.right != b.right || a.bottom != b.bottom ||
                            a.isSelected != b.isSelected
                }
        if (detectionsChanged) { lastDetections = detections; onDetections(detections) }
        if (measurement != lastMeasurement) { lastMeasurement = measurement; onMeasurement(measurement) }
        val shouldEmitCalibration = calibration is CalibrationState.Calibrated ||
                calibration::class != lastCalibrationState::class
        if (shouldEmitCalibration) onCalibration(calibration)
        if (status != lastStatus) { lastStatus = status; onStatus(status) }
    }

    private var lastRotationDegrees = -1

    private fun imageProxyToReusableBitmap(image: ImageProxy): Bitmap {
        val width = image.width; val height = image.height
        val bitmap = if (rgbaBitmap == null ||
            rgbaBitmap!!.width != width || rgbaBitmap!!.height != height) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rgbaBitmap = it }
        } else rgbaBitmap!!
        image.planes[0].buffer.rewind()
        bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
        return bitmap
    }

    private fun rotateBitmapIfNeededReusable(source: Bitmap, rotationDegrees: Int): Bitmap {
        val norm = ((rotationDegrees % 360) + 360) % 360
        if (norm == 0) return source
        val targetW = if (norm == 90 || norm == 270) source.height else source.width
        val targetH = if (norm == 90 || norm == 270) source.width else source.height
        val target = if (rotatedBitmap == null ||
            rotatedBitmap!!.width != targetW || rotatedBitmap!!.height != targetH) {
            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                rotatedBitmap = it; rotatedCanvas = Canvas(it); lastRotationDegrees = -1
            }
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