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

/**
 * Gestiona el ciclo de vida de los casos de uso de CameraX: preview y análisis de frames.
 *
 * Crea y vincula al [LifecycleOwner] un [CameraXPreview] conectado al [PreviewView]
 * y un [ImageAnalysis] con estrategia `STRATEGY_KEEP_ONLY_LATEST` en formato
 * `RGBA_8888`. El análisis se delega a un [FrameAnalyzer] ejecutado en un hilo
 * dedicado de un solo executor.
 *
 * @param context        Contexto de la aplicación para obtener el [ProcessCameraProvider].
 * @param lifecycleOwner Ciclo de vida al que se vincula la cámara; normalmente un Fragment o Activity.
 * @param previewView    Vista de CameraX donde se renderiza el stream de la cámara trasera.
 */
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

    /**
     * Inicializa y vincula los casos de uso de CameraX al ciclo de vida indicado.
     *
     * Si la cámara ya está vinculada ([isBound] es `true`), la llamada no tiene efecto.
     * Todos los parámetros dinámicos se pasan como lambdas para que el [FrameAnalyzer]
     * pueda leerlos en tiempo de ejecución sin necesidad de recrearlo.
     *
     * @param isCameraActive          Indica si la cámara debe procesar frames activamente.
     * @param isRecording             Indica si hay una sesión de grabación en curso.
     * @param detectorProvider        Proveedor del [TfliteObjectDetector] activo.
     * @param onFps                   Callback con la tasa de frames medida.
     * @param onTotalFrames           Callback con el contador acumulado de frames.
     * @param onDetections            Callback con la lista de [UiDetection] para la UI.
     * @param onDetectorStatus        Callback con el texto de estado del detector.
     * @param onCalibration           Callback con el [CalibrationState] actualizado.
     * @param onMeasurement           Callback con el [MeasurementResult] del objeto trackeado, o `null`.
     * @param markerSizeCmProvider    Proveedor del tamaño real del marcador ArUco en centímetros.
     * @param enterThresholdProvider  Proveedor del umbral mínimo de confianza para aceptar detecciones.
     * @param maxPerClassProvider     Proveedor del límite de detecciones por clase.
     * @param maxPerFrameProvider     Proveedor del límite total de detecciones por frame.
     * @param selectedCenterProvider  Proveedor del centroide del objeto seleccionado, o `null`.
     * @param onTrackedDetection      Callback con la [UiDetection] actualmente trackeada, o `null`.
     * @param sessionRecorder         Grabador de sesión donde se persisten los puntos de trayectoria.
     * @param onTrackingDebug         Callback con la fuente de tracking activa, para depuración.
     */
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
        sessionRecorder: SessionRecorder,
        onTrackingDebug: (String) -> Unit,
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
                            onTrackingDebug = onTrackingDebug,
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

    /**
     * Actualiza la rotación objetivo de los casos de uso de preview y análisis.
     *
     * Debe llamarse cuando cambia la orientación del dispositivo para que CameraX
     * informe la rotación correcta en los metadatos de cada frame.
     *
     * @param rotation Constante de rotación de [android.view.Surface], por ejemplo
     *                 `Surface.ROTATION_0` o `Surface.ROTATION_90`.
     */
    fun updateRotation(rotation: Int) {
        previewUseCase?.targetRotation = rotation
        analysisUseCase?.targetRotation = rotation
    }

    /**
     * Libera todos los recursos de la cámara: limpia el analizador, desvincula los
     * casos de uso del [ProcessCameraProvider] y apaga el [analysisExecutor].
     *
     * Tras esta llamada, [isBound] queda en `false` y es necesario invocar
     * [bindCamera] de nuevo para retomar el análisis.
     */
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