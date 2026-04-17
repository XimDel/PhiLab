package com.example.philab.ui.lab.experiment.camera

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.philab.core.camera.UiDetection
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.data.local.database.PhiLabDatabase
import com.example.philab.data.repository.SessionRepository
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.experiment.SessionRecorder
import com.example.philab.core.measurement.MeasurementResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Nivel de sensibilidad del detector de objetos.
 *
 * Determina el umbral mínimo de confianza que debe superar una detección para
 * ser aceptada por el [FrameAnalyzer].
 *
 * @property label  Etiqueta legible para mostrar en la UI.
 * @property enter  Umbral de confianza en el rango `[0, 1]`.
 */
enum class Sensitivity(val label: String, val enter: Float) {
    BAJA("Bajo", 0.35f),
    MEDIA("Medio", 0.45f),
    ALTA("Alto", 0.60f)
}

/**
 * Modelo de un archivo TFLite disponible para detección de objetos.
 *
 * @property file  Nombre del archivo `.tflite` en la carpeta `assets`.
 * @property label Etiqueta descriptiva para mostrar en el selector de modelos.
 */
data class ModelOption(val file: String, val label: String)

/**
 * Representa el objeto actualmente seleccionado por el usuario en la vista de cámara.
 *
 * @property label   Etiqueta de clase asignada por el detector.
 * @property centerX Coordenada X del centroide en el espacio de imagen (píxeles).
 * @property centerY Coordenada Y del centroide en el espacio de imagen (píxeles).
 */
data class SelectedObject(
    val label: String,
    val centerX: Float,
    val centerY: Float
)

/**
 * ViewModel principal de la pantalla de cámara y experimento.
 *
 * Gestiona el ciclo de vida de la cámara, la grabación de sesiones, el estado del
 * detector TFLite, la calibración ArUco, el tracking del objeto seleccionado y la
 * persistencia de resultados en la base de datos local.
 *
 * Expone todo su estado como propiedades Compose observables (`mutableStateOf`) para
 * que la UI se recomponga automáticamente ante cualquier cambio.
 *
 * @param application Instancia de [Application] requerida por [AndroidViewModel] para
 *                    acceder al contexto sin filtrar un [Activity].
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository by lazy {
        val db = PhiLabDatabase.getInstance(application)
        SessionRepository(db.sessionDao())
    }

    /**
     * Lista de modelos TFLite disponibles para seleccionar en la configuración.
     */
    val models = listOf(
        ModelOption("ssd_mobilenet_v1.tflite",     "SSD MobileNet v1 (300×300)"),
        ModelOption("efficientdet_lite0.tflite",   "EfficientDet Lite0 (320×320)"),
        ModelOption("efficientdet_lite1.tflite",   "EfficientDet Lite1 (384×384)"),
        ModelOption("efficientdet_lite2.tflite",   "EfficientDet Lite2 (448×448)"),
        ModelOption("efficientdet_lite4.tflite",   "EfficientDet Lite4 (640×640)")
    )

    /** Indica si el stream de cámara está activo. */
    var isCameraActive by mutableStateOf(false)
        private set

    /** Indica si hay una grabación de sesión en curso. */
    var isRunning by mutableStateOf(false)
        private set

    /** Modelo TFLite actualmente seleccionado. */
    var selectedModel by mutableStateOf(models[0])
        private set

    /** Nivel de sensibilidad del detector activo. */
    var sensitivity by mutableStateOf(Sensitivity.MEDIA)
        private set

    /** Número máximo de detecciones totales por frame. */
    var maxPerFrame by mutableStateOf(1)
        private set

    /** Número máximo de detecciones por clase por frame. */
    var maxPerClass by mutableStateOf(1)
        private set

    /** Controla la visibilidad del panel de configuración. */
    var showConfig by mutableStateOf(false)
        private set

    /** Tasa de frames por segundo medida en la ventana deslizante del analizador. */
    var fps by mutableStateOf(0.0)
        private set

    /** Contador acumulado de frames procesados desde que se activó la cámara. */
    var totalFrames by mutableStateOf(0L)
        private set

    /** Tiempo transcurrido en milisegundos desde el inicio de la grabación activa. */
    var elapsedMs by mutableStateOf(0L)
        private set

    /** Lista de detecciones del último frame procesado, lista para renderizar. */
    var detections by mutableStateOf(emptyList<UiDetection>())
        private set

    /** Texto de estado del detector para mostrar en la UI. */
    var detectorStatus by mutableStateOf("Listo")
        private set

    /** Tamaño en píxeles del componente de preview de cámara en pantalla. */
    var previewSize by mutableStateOf(IntSize(0, 0))
        private set

    /** Tamaño real del marcador ArUco en centímetros, configurable por el usuario. */
    var markerSizeCm by mutableStateOf(5f)
        private set

    /** Estado actual de la calibración espacial mediante ArUco. */
    var calibrationState by mutableStateOf<CalibrationState>(CalibrationState.Idle)
        private set

    /** Resultado de la última medición física del objeto trackeado, o `null`. */
    var measurementResult by mutableStateOf<MeasurementResult?>(null)
        private set

    /** Objeto actualmente seleccionado por el usuario mediante tap, o `null`. */
    var selectedObject by mutableStateOf<SelectedObject?>(null)
        private set

    /** Detección actualmente trackeada por el pipeline de Optical Flow + Kalman, o `null`. */
    var trackedDetection by mutableStateOf<UiDetection?>(null)
        private set

    /** Grabador de sesión que acumula los puntos de trayectoria durante la grabación. */
    val sessionRecorder = SessionRecorder()

    /** Resultados del experimento tras detener la grabación, o `null` si no hay sesión finalizada. */
    var experimentResults by mutableStateOf<ExperimentResults?>(null)
        private set

    /** Número de puntos grabados en la sesión activa, actualizado en tiempo real. */
    var livePointCount by mutableStateOf(0)
        private set

    /** Cadena de depuración con la fuente de tracking activa en el último frame. */
    var trackingDebugInfo by mutableStateOf("Tracking: Idle")
        private set

    /** Indica si hay una operación de guardado en curso en la base de datos. */
    var isSaving by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    /**
     * Persiste los resultados de la sesión finalizada en la base de datos local.
     *
     * Actualiza [experimentResults] en memoria con la etiqueta editada por el usuario
     * antes de llamar al repositorio. Establece [isSaving] durante la operación y
     * lo libera en el bloque `finally` para garantizar que la UI siempre se restaure.
     *
     * @param experimentName Nombre del experimento introducido por el usuario.
     * @param editedLabel    Etiqueta del objeto seguido, potencialmente editada por el usuario.
     * @param onDone         Callback invocado al completar el guardado, independientemente
     *                       de si hubo error.
     */
    fun saveSession(
        experimentName: String,
        editedLabel: String,
        onDone: () -> Unit
    ) {
        val results = experimentResults ?: return
        experimentResults = results.copy(selectedLabel = editedLabel)
        isSaving = true
        viewModelScope.launch {
            try {
                repository.saveSession(
                    results = experimentResults!!,
                    experimentName = experimentName,
                    editedLabel = editedLabel
                )
            } finally {
                isSaving = false
                onDone()
            }
        }
    }

    /**
     * Actualiza el texto de depuración de tracking emitido por [FrameAnalyzer].
     *
     * @param value Cadena con la fuente de tracking activa, p. ej. `"Tracking: OPTICAL_FLOW"`.
     */
    fun updateTrackingDebugInfo(value: String) { trackingDebugInfo = value }

    /**
     * Procesa un tap del usuario sobre el preview de cámara y selecciona la detección
     * más cercana al punto pulsado.
     *
     * Transforma las coordenadas de pantalla al espacio de imagen aplicando el mismo
     * escalado `fit-center` que usa [DetectionOverlay]. Si ninguna detección contiene
     * el punto pulsado, limpia la selección actual.
     *
     * @param touchOffset Coordenadas del tap en píxeles del componente de preview.
     */
    fun onUserTap(touchOffset: Offset) {
        val currentDetections = detections
        if (currentDetections.isEmpty()) return
        val viewW = previewSize.width.toFloat()
        val viewH = previewSize.height.toFloat()
        if (viewW == 0f || viewH == 0f) return
        val srcW = currentDetections.first().sourceWidth.toFloat()
        val srcH = currentDetections.first().sourceHeight.toFloat()
        val scale = maxOf(viewW / srcW, viewH / srcH)
        val dx = (viewW - srcW * scale) / 2f
        val dy = (viewH - srcH * scale) / 2f
        val tapX = (touchOffset.x - dx) / scale
        val tapY = (touchOffset.y - dy) / scale
        val tapped = currentDetections
            .filter { det -> tapX in det.left..det.right && tapY in det.top..det.bottom }
            .minByOrNull { det ->
                val cx = (det.left + det.right) / 2f
                val cy = (det.top + det.bottom) / 2f
                hypot((cx - tapX).toDouble(), (cy - tapY).toDouble())
            }
        if (tapped == null) { clearSelectedObject(); return }
        selectedObject = SelectedObject(
            label   = tapped.label,
            centerX = (tapped.left + tapped.right) / 2f,
            centerY = (tapped.top + tapped.bottom) / 2f
        )
        trackedDetection = tapped
    }

    /**
     * Limpia los resultados del experimento finalizado y resetea el contador de puntos
     * y la selección de objeto.
     */
    fun clearExperimentResults() {
        experimentResults = null
        livePointCount = 0
        clearSelectedObject()
    }

    /**
     * Elimina el objeto seleccionado y la detección trackeada actualmente.
     */
    fun clearSelectedObject() {
        selectedObject = null
        trackedDetection = null
    }

    /**
     * Actualiza la detección trackeada y sincroniza el contador de puntos en vivo
     * si hay una grabación activa.
     *
     * @param detection Detección actualmente trackeada, o `null` si se perdió el objeto.
     */
    fun updateTrackedDetection(detection: UiDetection?) {
        trackedDetection = detection
        if (sessionRecorder.isActive) livePointCount = sessionRecorder.pointCount
    }

    /** Cambia el modelo TFLite activo. @param model Nuevo modelo seleccionado. */
    fun selectModel(model: ModelOption) { selectedModel = model }

    /** Actualiza el nivel de sensibilidad del detector. @param value Nuevo nivel. */
    fun updateSensitivity(value: Sensitivity) { sensitivity = value }

    /**
     * Actualiza el límite de detecciones por frame, clampado al rango `[1, 6]`.
     * @param value Nuevo valor solicitado.
     */
    fun updateMaxPerFrame(value: Int) { maxPerFrame = value.coerceIn(1, 6) }

    /**
     * Actualiza el límite de detecciones por clase, clampado al rango `[1, 10]`.
     * @param value Nuevo valor solicitado.
     */
    fun updateMaxPerClass(value: Int) { maxPerClass = value.coerceIn(1, 10) }

    /** Alterna la visibilidad del panel de configuración. */
    fun toggleConfig() { showConfig = !showConfig }

    /** Recibe y almacena la tasa de FPS medida. @param value FPS actuales. */
    fun updateFps(value: Double) { fps = value }

    /** Recibe y almacena el contador de frames. @param value Total de frames procesados. */
    fun updateTotalFrames(value: Long) { totalFrames = value }

    /** Recibe y almacena la lista de detecciones del frame actual. @param value Lista de detecciones. */
    fun updateDetections(value: List<UiDetection>) { detections = value }

    /** Actualiza el texto de estado del detector. @param value Nuevo texto. */
    fun updateDetectorStatus(value: String) { detectorStatus = value }

    /** Actualiza el tamaño en píxeles del componente de preview. @param value Tamaño actual. */
    fun updatePreviewSize(value: IntSize) { previewSize = value }

    /**
     * Actualiza el tamaño del marcador ArUco, forzando un mínimo de 0.1 cm.
     * @param value Nuevo tamaño en centímetros.
     */
    fun updateMarkerSizeCm(value: Float) { markerSizeCm = value.coerceAtLeast(0.1f) }

    /** Actualiza el resultado de medición del objeto trackeado. @param value Resultado, o `null`. */
    fun updateMeasurementResult(value: MeasurementResult?) { measurementResult = value }

    /** Indica al ViewModel que el modelo está cargándose; limpia las detecciones actuales. */
    fun onDetectorLoading() { detectorStatus = "Cargando modelo"; detections = emptyList() }

    /** Indica al ViewModel que el modelo terminó de cargarse. */
    fun onDetectorLoaded() { detectorStatus = "Modelo cargado" }

    /**
     * Actualiza el estado de calibración ArUco.
     *
     * Ignora cualquier estado distinto de [CalibrationState.Idle] si la cámara no
     * está activa, para evitar que un frame tardío sobrescriba el estado de reposo.
     *
     * @param value Nuevo estado de calibración emitido por el [FrameAnalyzer].
     */
    fun updateCalibrationState(value: CalibrationState) {
        if (!isCameraActive && value !is CalibrationState.Idle) return
        calibrationState = value
    }

    /**
     * Alterna el estado de la cámara: la inicia si está inactiva y la detiene si está activa.
     */
    fun toggleCamera() { if (isCameraActive) stopCamera() else startCamera() }

    private fun startCamera() {
        isCameraActive = true
        detectorStatus = "Buscando..."
        calibrationState = CalibrationState.Searching
    }

    /**
     * Detiene la cámara, cancela la grabación si está activa y resetea todo el estado
     * relacionado con la sesión de captura.
     */
    fun stopCamera() {
        if (isRunning) stop()
        calibrationState = CalibrationState.Idle
        detections = emptyList()
        measurementResult = null
        clearSelectedObject()
        detectorStatus = "Listo"
        isCameraActive = false
    }

    /**
     * Alterna el estado de grabación: inicia si está detenida y detiene si está activa.
     */
    fun toggleRunning() { if (isRunning) stop() else start() }

    private fun start() {
        fps = 0.0; totalFrames = 0L; elapsedMs = 0L
        detections = emptyList()
        detectorStatus = "Grabando"
        experimentResults = null
        livePointCount = 0
        val cal = calibrationState as? CalibrationState.Calibrated
        sessionRecorder.start(
            label   = selectedObject?.label ?: "Object",
            cmPerPx = cal?.cmPerPx ?: 1f,
            unit    = if (cal != null) "cm" else "px"
        )
        isRunning = true
        startTimer()
    }

    private fun stop() {
        isRunning = false
        detectorStatus = "Detenido"
        experimentResults = sessionRecorder.stop()
        timerJob?.cancel()
    }

    /**
     * Inicia un job de corutina que actualiza [elapsedMs] cada 16 ms (~60 fps) mientras
     * [isRunning] sea `true`.
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (isRunning) {
                elapsedMs = System.currentTimeMillis() - start
                delay(16)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}