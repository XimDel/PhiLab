package com.example.philab.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.core.camera.FrameAnalyzer
import com.example.philab.core.camera.UiDetection
import com.example.philab.core.detection.TfliteObjectDetector
import com.example.philab.domain.experiment.SessionRecorder
import com.example.philab.core.measurement.MeasurementResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var previewUseCase: androidx.camera.core.Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isBound = false

    fun bindCamera(
        isCameraActive: () -> Boolean,
        isRecording: () -> Boolean,
        detectorProvider: () -> TfliteObjectDetector,
        onFps: (Double) -> Unit,
        onTotalFrames: (Long) -> Unit,
        onDetections: (List<UiDetection>) -> Unit,
        onDetectorStatus: (String) -> Unit,
        onCalibration: (CalibrationState) -> Unit,
        onMeasurement: (MeasurementResult?) -> Unit,
        markerSizeCmProvider: () -> Float,
        enterThresholdProvider: () -> Float,
        maxPerClassProvider: () -> Int,
        maxPerFrameProvider: () -> Int,
        selectedCenterProvider: () -> Pair<Float, Float>?,
        onTrackedDetection: (UiDetection?) -> Unit,
        sessionRecorder: SessionRecorder
    ) {
        if (isBound) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            previewUseCase = CameraXPreview.Builder().build().also { preview ->
                preview.setSurfaceProvider(previewView.surfaceProvider)
            }

            analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(
                        analysisExecutor,
                        FrameAnalyzer(
                            detectorProvider = detectorProvider,
                            isCameraActive = isCameraActive,
                            isRecording = isRecording,
                            onFps = onFps,
                            onTotalFrames = onTotalFrames,
                            onDetections = onDetections,
                            onStatus = onDetectorStatus,
                            onCalibration = onCalibration,
                            onMeasurement = onMeasurement,
                            markerSizeCmProvider = markerSizeCmProvider,
                            enterThresholdProvider = enterThresholdProvider,
                            maxPerClassProvider = maxPerClassProvider,
                            maxPerFrameProvider = maxPerFrameProvider,
                            selectedCenterProvider = selectedCenterProvider,
                            onTrackedDetection = onTrackedDetection,
                            sessionRecorder = sessionRecorder
                        )
                    )
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase,
                    analysisUseCase
                )
                isBound = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateRotation(rotation: Int) {
        previewUseCase?.targetRotation = rotation
        analysisUseCase?.targetRotation = rotation
    }

    fun release() {
        try {
            analysisUseCase?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (!analysisExecutor.isShutdown) analysisExecutor.shutdown()
            isBound = false
        }
    }
}