package com.example.philab.core.detection

import android.content.Context
import com.example.philab.ui.lab.experiment.camera.ModelOption

/**
 * Gestiona el ciclo de vida del detector de objetos TFLite activo.
 *
 * Mantiene una instancia única de [TfliteObjectDetector] y permite
 * reemplazarla en tiempo de ejecución cuando el usuario selecciona
 * un modelo diferente o cambia el límite de detecciones por fotograma.
 *
 * @param context Contexto de la aplicación, usado para acceder a los assets del modelo.
 */
class DetectorManager(
    private val context: Context
) {
    private var _detector: TfliteObjectDetector = createDetector(
        model = ModelOption("efficientdet_lite0.tflite", "EfficientDet Lite0 (320×320)"),
        maxPerFrame = 4
    )

    /**
     * Instancia activa del detector de objetos.
     */
    val detector: TfliteObjectDetector
        get() = _detector

    /**
     * Cadena de diagnóstico con el nombre del modelo y la resolución de entrada.
     * Útil para mostrar información del detector en la UI de depuración.
     */
    val detectorInfo: String
        get() = "Detector:\nModel: ${_detector.modelFileName}\nRes: ${_detector.inputWidth}x${_detector.inputHeight}"

    /**
     * Reemplaza el detector activo por una nueva instancia configurada con el modelo
     * y el límite de detecciones indicados.
     *
     * @param model     Opción de modelo que incluye el nombre del archivo `.tflite` y su etiqueta.
     * @param maxPerFrame Número máximo de detecciones que el detector devolverá por fotograma.
     */
    fun updateDetector(
        model: ModelOption,
        maxPerFrame: Int
    ) {
        _detector = createDetector(model, maxPerFrame)
    }

    /**
     * Crea y devuelve un nuevo [TfliteObjectDetector] con los parámetros indicados.
     *
     * El umbral de puntuación se fija en `0.3`, el número de hilos en `4`
     * y la aceleración GPU se habilita por defecto.
     *
     * @param model       Opción de modelo seleccionada.
     * @param maxPerFrame Límite de resultados devueltos por el detector.
     * @return Nueva instancia de [TfliteObjectDetector] lista para usar.
     */
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