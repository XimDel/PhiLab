package com.example.philab.core.detection

import android.content.Context
import com.example.philab.ui.lab.experiment.camera.ModelOption

class DetectorManager(
    private val context: Context
) {
    private var _detector: TfliteObjectDetector = createDetector(
        model = ModelOption("efficientdet_lite0.tflite", "EfficientDet Lite0 (320×320)"),
        maxPerFrame = 4
    )

    val detector: TfliteObjectDetector
        get() = _detector

    val detectorInfo: String
        get() = "Detector:\nModel: ${_detector.modelFileName}\nRes: ${_detector.inputWidth}x${_detector.inputHeight}"

    fun updateDetector(
        model: ModelOption,
        maxPerFrame: Int
    ) {
        _detector = createDetector(model, maxPerFrame)
    }

    private fun createDetector(
        model: ModelOption,
        maxPerFrame: Int
    ): TfliteObjectDetector {
        return TfliteObjectDetector(
            context = context,
            modelFileName = model.file,
            scoreThreshold = 0.3f,
            maxResults = maxPerFrame,
            numThreads = 4,
            useGpu = true
        )
    }
}