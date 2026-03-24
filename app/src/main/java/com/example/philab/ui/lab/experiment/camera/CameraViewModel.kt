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

enum class Sensitivity(val label: String, val enter: Float) {
    BAJA("Bajo", 0.35f),
    MEDIA("Medio", 0.45f),
    ALTA("Alto", 0.60f)
}

data class ModelOption(val file: String, val label: String)

data class SelectedObject(
    val label: String,
    val centerX: Float,
    val centerY: Float
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository by lazy {
        val db = PhiLabDatabase.getInstance(application)
        SessionRepository(db.sessionDao())
    }

    val models = listOf(
        ModelOption("ssd_mobilenet_v1.tflite", "SSD MobileNet v1 (300×300)"),
        ModelOption("efficientdet_lite0.tflite", "EfficientDet Lite0 (320×320)"),
        ModelOption("efficientdet_lite1.tflite", "EfficientDet Lite1 (384×384)"),
        ModelOption("efficientdet_lite2.tflite", "EfficientDet Lite2 (448×448)"),
        ModelOption("efficientdet_lite4.tflite", "EfficientDet Lite4 (640×640)")
    )

    var isCameraActive by mutableStateOf(false)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var selectedModel by mutableStateOf(models[0])
        private set
    var sensitivity by mutableStateOf(Sensitivity.MEDIA)
        private set
    var maxPerFrame by mutableStateOf(1)
        private set
    var maxPerClass by mutableStateOf(1)
        private set
    var showConfig by mutableStateOf(false)
        private set
    var fps by mutableStateOf(0.0)
        private set
    var totalFrames by mutableStateOf(0L)
        private set
    var elapsedMs by mutableStateOf(0L)
        private set
    var detections by mutableStateOf(emptyList<UiDetection>())
        private set
    var detectorStatus by mutableStateOf("Listo")
        private set
    var previewSize by mutableStateOf(IntSize(0, 0))
        private set
    var markerSizeCm by mutableStateOf(5f)
        private set
    var calibrationState by mutableStateOf<CalibrationState>(CalibrationState.Idle)
        private set
    var measurementResult by mutableStateOf<MeasurementResult?>(null)
        private set
    var selectedObject by mutableStateOf<SelectedObject?>(null)
        private set
    var trackedDetection by mutableStateOf<UiDetection?>(null)
        private set
    val sessionRecorder = SessionRecorder()
    var experimentResults by mutableStateOf<ExperimentResults?>(null)
        private set
    var livePointCount by mutableStateOf(0)
        private set
    var trackingDebugInfo by mutableStateOf("Tracking: Idle")
        private set
    var isSaving by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    // ── Guardar en Room ───────────────────────────────────────────────────────

    /**
     * Persiste la sesión con el nombre de experimento y el label del objeto
     * elegidos por el usuario en SessionSummaryDialog.
     * Actualiza experimentResults en memoria y ejecuta onDone al terminar.
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

    // ── Resto del ViewModel sin cambios ───────────────────────────────────────

    fun updateTrackingDebugInfo(value: String) { trackingDebugInfo = value }

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
            label = tapped.label,
            centerX = (tapped.left + tapped.right) / 2f,
            centerY = (tapped.top + tapped.bottom) / 2f
        )
        trackedDetection = tapped
    }

    fun clearExperimentResults() {
        experimentResults = null
        livePointCount = 0
        clearSelectedObject()
    }

    fun clearSelectedObject() {
        selectedObject = null
        trackedDetection = null
    }

    fun updateTrackedDetection(detection: UiDetection?) {
        trackedDetection = detection
        if (sessionRecorder.isActive) livePointCount = sessionRecorder.pointCount
    }

    fun selectModel(model: ModelOption) { selectedModel = model }
    fun updateSensitivity(value: Sensitivity) { sensitivity = value }
    fun updateMaxPerFrame(value: Int) { maxPerFrame = value.coerceIn(1, 6) }
    fun updateMaxPerClass(value: Int) { maxPerClass = value.coerceIn(1, 10) }
    fun toggleConfig() { showConfig = !showConfig }
    fun updateFps(value: Double) { fps = value }
    fun updateTotalFrames(value: Long) { totalFrames = value }
    fun updateDetections(value: List<UiDetection>) { detections = value }
    fun updateDetectorStatus(value: String) { detectorStatus = value }
    fun updatePreviewSize(value: IntSize) { previewSize = value }
    fun updateMarkerSizeCm(value: Float) { markerSizeCm = value.coerceAtLeast(0.1f) }
    fun updateMeasurementResult(value: MeasurementResult?) { measurementResult = value }
    fun onDetectorLoading() { detectorStatus = "Cargando modelo"; detections = emptyList() }
    fun onDetectorLoaded() { detectorStatus = "Modelo cargado" }

    fun updateCalibrationState(value: CalibrationState) {
        if (!isCameraActive && value !is CalibrationState.Idle) return
        calibrationState = value
    }

    fun toggleCamera() { if (isCameraActive) stopCamera() else startCamera() }

    private fun startCamera() {
        isCameraActive = true
        detectorStatus = "Buscando..."
        calibrationState = CalibrationState.Searching
    }

    private fun stopCamera() {
        if (isRunning) stop()
        calibrationState = CalibrationState.Idle
        detections = emptyList()
        measurementResult = null
        clearSelectedObject()
        detectorStatus = "Listo"
        isCameraActive = false
    }

    fun toggleRunning() { if (isRunning) stop() else start() }

    private fun start() {
        fps = 0.0; totalFrames = 0L; elapsedMs = 0L
        detections = emptyList()
        detectorStatus = "Grabando"
        experimentResults = null
        livePointCount = 0
        val cal = calibrationState as? CalibrationState.Calibrated
        sessionRecorder.start(
            label = selectedObject?.label ?: "Object",
            cmPerPx = cal?.cmPerPx ?: 1f,
            unit = if (cal != null) "cm" else "px"
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