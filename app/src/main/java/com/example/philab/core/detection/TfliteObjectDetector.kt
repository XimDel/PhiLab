package com.example.philab.core.detection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class TfliteObjectDetector(
    private val context: Context,
    val modelFileName: String,
    private val scoreThreshold: Float = 0.3f,
    private val maxResults: Int = 6,
    private val numThreads: Int = 4,
    private val useGpu: Boolean = true
) {
    val inputWidth: Int
    val inputHeight: Int

    private val detector: ObjectDetector

    init {
        val (h, w) = readModelInputSize(context, modelFileName)
        inputHeight = h
        inputWidth = w

        detector = tryCreateDetector()
    }

    // GPU
    private fun tryCreateDetector(): ObjectDetector {
        if (useGpu) {
            try {
                val gpuOptions = BaseOptions.builder()
                    .setNumThreads(numThreads)
                    .useGpu()
                    .build()

                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(gpuOptions)
                    .setScoreThreshold(scoreThreshold)
                    .setMaxResults(maxResults)
                    .build()

                val det = ObjectDetector.createFromFileAndOptions(context, modelFileName, options)
                Log.d("TfliteDetector", "GPU delegate activo: $modelFileName")
                return det
            } catch (e: Exception) {
                Log.w("TfliteDetector", "GPU falló (${e.message}), usando CPU")
            } catch (e: Error) {
                Log.w("TfliteDetector", "GPU error nativo (${e.message}), usando CPU")
            }
        }

        // Fallback CPU
        val cpuOptions = BaseOptions.builder()
            .setNumThreads(numThreads)
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(cpuOptions)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(maxResults)
            .build()

        Log.d("TfliteDetector", "CPU ($numThreads hilos): $modelFileName")
        return ObjectDetector.createFromFileAndOptions(context, modelFileName, options)
    }

    fun detect(image: TensorImage): List<Detection> = detector.detect(image)

    private fun readModelInputSize(context: Context, fileName: String): Pair<Int, Int> {
        val modelBuffer = FileUtil.loadMappedFile(context, fileName)
        val interpreter = Interpreter(modelBuffer)
        return try {
            val shape = interpreter.getInputTensor(0).shape()
            val h = shape[1]
            val w = shape[2]
            h to w
        } finally {
            interpreter.close()
        }
    }
}