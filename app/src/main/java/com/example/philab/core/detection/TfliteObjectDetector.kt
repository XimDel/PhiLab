package com.example.philab.core.detection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Detector de objetos basado en TensorFlow Lite.
 *
 * Carga el modelo indicado por [modelFileName] desde los assets de la aplicación,
 * intenta inicializarlo con delegado GPU y recurre a CPU si la GPU no está disponible
 * o produce un error nativo. El tamaño de entrada del modelo se lee directamente
 * del tensor de entrada para evitar hardcodear dimensiones.
 *
 * @property context Contexto de la aplicación, usado para acceder a los assets.
 * @property modelFileName Nombre del archivo `.tflite` dentro de la carpeta `assets`.
 * @property scoreThreshold Umbral mínimo de confianza para incluir una detección en los resultados.
 * @property maxResults Número máximo de detecciones a devolver por fotograma.
 * @property numThreads Número de hilos de CPU a usar cuando el delegado GPU no está disponible.
 * @property useGpu Si es `true`, intenta usar el delegado GPU antes de recurrir a CPU.
 */
class TfliteObjectDetector(
    private val context: Context,
    val modelFileName: String,
    private val scoreThreshold: Float = 0.3f,
    private val maxResults: Int = 6,
    private val numThreads: Int = 4,
    private val useGpu: Boolean = true
) {
    /** Ancho de entrada esperado por el modelo en píxeles. */
    val inputWidth: Int

    /** Alto de entrada esperado por el modelo en píxeles. */
    val inputHeight: Int

    private val detector: ObjectDetector

    init {
        val (h, w) = readModelInputSize(context, modelFileName)
        inputHeight = h
        inputWidth = w
        detector = tryCreateDetector()
    }

    /**
     * Crea el [ObjectDetector] intentando primero con delegado GPU y recurriendo a CPU si falla.
     *
     * @return Instancia de [ObjectDetector] configurada y lista para realizar detecciones.
     */
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

    /**
     * Ejecuta la detección de objetos sobre una [TensorImage] preprocesada.
     *
     * @param image Imagen de entrada ya redimensionada y convertida al formato esperado por el modelo.
     * @return Lista de [Detection] con las detecciones que superan el [scoreThreshold].
     */
    fun detect(image: TensorImage): List<Detection> = detector.detect(image)

    /**
     * Lee las dimensiones de entrada del modelo TFLite inspeccionando su primer tensor.
     *
     * Abre un [Interpreter] temporal para acceder al shape del tensor y lo cierra inmediatamente.
     *
     * @param context Contexto usado para acceder al archivo desde los assets.
     * @param fileName Nombre del archivo `.tflite` en la carpeta `assets`.
     * @return Par `(alto, ancho)` de la entrada esperada por el modelo.
     */
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