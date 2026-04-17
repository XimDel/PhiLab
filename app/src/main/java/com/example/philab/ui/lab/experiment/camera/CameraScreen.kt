package com.example.philab.ui.lab.experiment.camera

import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.core.detection.DetectorManager
import com.example.philab.ui.camera.CameraController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign

private val AppGreenPrimary    = Color(0xFF1D9E75)
private val AppGreenDark       = Color(0xFF2C4A3E)
private val AppGreenLight      = Color(0xFFB8DDD1)
private val AppGreenExtraLight = Color(0xFFE1F5EE)
private val AppSurface         = Color(0xFFF5F9F7)
private val AppTextPrimary     = Color(0xFF2C4A3E)
private val AppTextSecondary   = Color(0xFF4A7A68)
private val AppTextDisabled    = Color(0xFF8AADA4)

/**
 * Pantalla principal de captura de experimentos con la cámara.
 *
 * Gestiona el ciclo de vida de la cámara, el detector de objetos y el tracker
 * mediante [CameraController] y [DetectorManager]. En cada `ON_RESUME` del ciclo
 * de vida vuelve a vincular la cámara y reinicia el estado del experimento.
 *
 * Superpone en capas sobre el visor de cámara:
 * - [DetectionOverlay]: bounding boxes de los objetos detectados.
 * - [MeasurementOverlay]: estado de calibración ArUco y contorno del marcador.
 * - [CameraStatsOverlay]: métricas en tiempo real (FPS, frames, puntos grabados).
 * - [CameraOverlay]: controles de usuario y panel de configuración.
 *
 * Cuando el experimento finaliza, muestra [SessionSummaryDialog] para que el
 * usuario nombre y guarde la sesión antes de navegar a los resultados.
 *
 * @param onBack Callback invocado al pulsar el botón de retroceso.
 * @param onNavigateToResults Callback invocado tras guardar la sesión correctamente.
 * @param viewModel ViewModel que gestiona el estado de la pantalla.
 */
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onNavigateToResults: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val detectorManager = remember(context) { DetectorManager(context) }

    val cameraController = remember(context, lifecycleOwner, previewView) {
        CameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView
        )
    }

    DisposableEffect(cameraController) {
        onDispose { cameraController.release() }
    }

    LaunchedEffect(viewModel.selectedModel.file, viewModel.maxPerFrame) {
        viewModel.onDetectorLoading()
        detectorManager.updateDetector(model = viewModel.selectedModel, maxPerFrame = viewModel.maxPerFrame)
        viewModel.onDetectorLoaded()
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.clearExperimentResults()
                viewModel.stopCamera()
                cameraController.bindCamera(
                    isCameraActive        = { viewModel.isCameraActive },
                    isRecording           = { viewModel.isRunning },
                    detectorProvider      = { detectorManager.detector },
                    onFps                 = viewModel::updateFps,
                    onTotalFrames         = viewModel::updateTotalFrames,
                    onDetections          = viewModel::updateDetections,
                    onDetectorStatus      = viewModel::updateDetectorStatus,
                    onCalibration         = viewModel::updateCalibrationState,
                    onMeasurement         = viewModel::updateMeasurementResult,
                    markerSizeCmProvider  = { viewModel.markerSizeCm },
                    enterThresholdProvider = { viewModel.sensitivity.enter },
                    maxPerClassProvider   = { viewModel.maxPerClass },
                    maxPerFrameProvider   = { viewModel.maxPerFrame },
                    selectedCenterProvider = { viewModel.selectedObject?.let { it.centerX to it.centerY } },
                    onTrackedDetection    = viewModel::updateTrackedDetection,
                    sessionRecorder       = viewModel.sessionRecorder,
                    onTrackingDebug       = viewModel::updateTrackingDebugInfo
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { previewView },
            update = { view ->
                cameraController.updateRotation(view.display?.rotation ?: Surface.ROTATION_0)
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewModel.updatePreviewSize(it) }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (viewModel.detections.isNotEmpty()) {
                            viewModel.onUserTap(offset)
                        }
                    }
                }
        ) {
            DetectionOverlay(
                detections = if (viewModel.isCameraActive) viewModel.detections else emptyList(),
                viewSize = viewModel.previewSize,
                measurementResult = viewModel.measurementResult,
                selectedLabel = viewModel.selectedObject?.label
            )
        }

        MeasurementOverlay(
            calibrationState = viewModel.calibrationState,
            viewSize = viewModel.previewSize,
            modifier = Modifier.fillMaxSize()
        )

        CameraStatsOverlay(
            fps = viewModel.fps,
            elapsedMs = viewModel.elapsedMs,
            totalFrames = viewModel.totalFrames,
            detectorStatus = viewModel.detectorStatus,
            livePointCount = viewModel.livePointCount,
            isRunning = viewModel.isRunning,
            isCameraActive = viewModel.isCameraActive,
            detectorInfo = detectorManager.detectorInfo,
            trackingDebugInfo = viewModel.trackingDebugInfo
        )

        CameraOverlay(
            isCameraActive = viewModel.isCameraActive,
            isRunning = viewModel.isRunning,
            models = viewModel.models,
            selectedModel = viewModel.selectedModel,
            onSelectModel = viewModel::selectModel,
            showConfig = viewModel.showConfig,
            onToggleConfig = viewModel::toggleConfig,
            sensitivity = viewModel.sensitivity,
            onSensitivityChange = viewModel::updateSensitivity,
            maxPerFrame = viewModel.maxPerFrame,
            onMaxPerFrameChange = viewModel::updateMaxPerFrame,
            maxPerClass = viewModel.maxPerClass,
            onMaxPerClassChange = viewModel::updateMaxPerClass,
            markerSizeCm = viewModel.markerSizeCm,
            onMarkerSizeCmChange = viewModel::updateMarkerSizeCm,
            onBack = onBack,
            onToggleCamera = viewModel::toggleCamera,
            onStartStop = viewModel::toggleRunning,
            selectedObject = viewModel.selectedObject,
            onClearSelection = viewModel::clearSelectedObject,
            calibrationState = viewModel.calibrationState
        )

        viewModel.experimentResults?.let { results ->
            SessionSummaryDialog(
                results = results,
                onSave = { experimentName, editedLabel ->
                    viewModel.saveSession(experimentName, editedLabel) {
                        onNavigateToResults()
                    }
                },
                onRestart = { viewModel.clearExperimentResults() }
            )
        }

        if (viewModel.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/**
 * Overlay de métricas en tiempo real anclado en la esquina superior derecha.
 *
 * Muestra el nombre del modelo activo, el estado del detector con color semántico,
 * FPS y contador de frames cuando la cámara está activa, y tiempo transcurrido
 * con contador de puntos grabados y datos de debug del tracker durante la grabación.
 *
 * @param fps Fotogramas por segundo del pipeline de análisis.
 * @param elapsedMs Tiempo transcurrido de la sesión de grabación en milisegundos.
 * @param totalFrames Número total de frames procesados desde que se activó la cámara.
 * @param detectorStatus Texto de estado del detector, usado para determinar el color.
 * @param livePointCount Número de puntos registrados en la sesión activa.
 * @param isRunning `true` si hay una sesión de grabación en curso.
 * @param isCameraActive `true` si la cámara está activa y procesando frames.
 * @param detectorInfo Cadena descriptiva del modelo TFLite cargado.
 * @param trackingDebugInfo Información de depuración del tracker óptico.
 */
@Composable
private fun BoxScope.CameraStatsOverlay(
    fps: Double,
    elapsedMs: Long,
    totalFrames: Long,
    detectorStatus: String,
    livePointCount: Int,
    isRunning: Boolean,
    isCameraActive: Boolean,
    detectorInfo: String,
    trackingDebugInfo: String
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 30.dp, end = 14.dp)
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(text = detectorInfo, color = Color.White, fontSize = 12.sp)
        val statusColor = when {
            detectorStatus.startsWith("Grabando")            -> Color(0xFF26D9A0)
            detectorStatus.startsWith("Obj.")                -> Color(0xFF4FC3F7)
            detectorStatus.startsWith("Buscando")            -> Color(0xFFFFB74D)
            detectorStatus.startsWith("Detenido")            -> Color(0xFFFD5C59)
            detectorStatus.startsWith("Cargando")            -> Color(0xFFCE93D8)
            detectorStatus.startsWith("Modelo de detección") -> Color(0xFF8CE18E)
            detectorStatus.startsWith("Modelo cargado")      -> Color(0xFFEDFF75)
            else                                             -> Color.White
        }
        Text(detectorStatus, color = statusColor, fontSize = 13.sp)

        if (isCameraActive) {
            Text("FPS: ${fps.toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Frames: $totalFrames", color = Color.White, fontSize = 13.sp)
        }

        if (isRunning) {
            Text("Tiempo: ${formatElapsed(elapsedMs)}", color = Color.White, fontSize = 13.sp)
            if (livePointCount > 0) {
                Text(
                    text = "Puntos: $livePointCount",
                    color = Color.Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = trackingDebugInfo,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Overlay de controles de usuario adaptativo según la orientación del dispositivo.
 *
 * En portrait muestra los controles en la parte inferior de la pantalla.
 * En landscape muestra el panel de configuración a la izquierda con scroll
 * y los botones de acción a la derecha.
 *
 * @param isCameraActive `true` si la cámara está activa.
 * @param isRunning `true` si hay una sesión de grabación en curso.
 * @param models Lista de modelos TFLite disponibles para seleccionar.
 * @param selectedModel Modelo actualmente seleccionado.
 * @param onSelectModel Callback invocado al seleccionar un nuevo modelo.
 * @param showConfig `true` si el panel de configuración está visible.
 * @param onToggleConfig Callback para mostrar u ocultar el panel de configuración.
 * @param sensitivity Nivel de sensibilidad de detección actual.
 * @param onSensitivityChange Callback invocado al cambiar la sensibilidad.
 * @param maxPerFrame Número máximo de detecciones por frame.
 * @param onMaxPerFrameChange Callback invocado al cambiar el límite por frame.
 * @param maxPerClass Número máximo de detecciones por clase.
 * @param onMaxPerClassChange Callback invocado al cambiar el límite por clase.
 * @param markerSizeCm Tamaño del marcador ArUco en centímetros.
 * @param onMarkerSizeCmChange Callback invocado al cambiar el tamaño del marcador.
 * @param onBack Callback invocado al pulsar el botón de retroceso.
 * @param onToggleCamera Callback para activar o desactivar la cámara.
 * @param onStartStop Callback para iniciar o detener la grabación.
 * @param selectedObject Objeto seleccionado actualmente para rastrear, o `null`.
 * @param onClearSelection Callback para deseleccionar el objeto actual.
 * @param calibrationState Estado actual de la calibración ArUco.
 */
@Composable
private fun CameraOverlay(
    isCameraActive: Boolean,
    isRunning: Boolean,
    models: List<ModelOption>,
    selectedModel: ModelOption,
    onSelectModel: (ModelOption) -> Unit,
    showConfig: Boolean,
    onToggleConfig: () -> Unit,
    sensitivity: Sensitivity,
    onSensitivityChange: (Sensitivity) -> Unit,
    maxPerFrame: Int,
    onMaxPerFrameChange: (Int) -> Unit,
    maxPerClass: Int,
    onMaxPerClassChange: (Int) -> Unit,
    markerSizeCm: Float,
    onMarkerSizeCmChange: (Float) -> Unit,
    onBack: () -> Unit,
    onToggleCamera: () -> Unit,
    onStartStop: () -> Unit,
    selectedObject: SelectedObject?,
    onClearSelection: () -> Unit,
    calibrationState: CalibrationState
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (showConfig) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.92f)
                            .align(Alignment.Bottom),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppGreenLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            ConfigPanelContent(
                                models = models,
                                selectedModel = selectedModel,
                                isCameraActive = isCameraActive,
                                onSelectModel = onSelectModel,
                                sensitivity = sensitivity,
                                onSensitivityChange = onSensitivityChange,
                                maxPerFrame = maxPerFrame,
                                onMaxPerFrameChange = onMaxPerFrameChange,
                                maxPerClass = maxPerClass,
                                onMaxPerClassChange = onMaxPerClassChange,
                                markerSizeCm = markerSizeCm,
                                onMarkerSizeCmChange = onMarkerSizeCmChange
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCameraActive) {
                        val arucoOk = calibrationState is CalibrationState.Calibrated
                        StatusChip(
                            label = if (arucoOk) "✓ ArUco" else "✗ ArUco",
                            active = arucoOk
                        )
                        val objOk = selectedObject != null
                        StatusChip(
                            label = if (objOk) "★ ${selectedObject!!.label} ×" else "Toca objeto",
                            active = objOk,
                            onClick = if (objOk) onClearSelection else null
                        )
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color(0x88000000),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleConfig,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color(0xFF289BAD),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Configuración",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = onToggleCamera,
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraActive) Color(0xFF555577) else Color(0xFF2B77CB),
                            disabledContainerColor = Color(0xFF797676)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isCameraActive) "Detener" else "Calibrar |\nDetectar",
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = onStartStop,
                        enabled = isCameraActive && selectedObject != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color.Red else AppGreenPrimary,
                            disabledContainerColor = Color(0xFF969191)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isRunning) "Detener" else "Iniciar",
                            fontSize = 11.sp
                        )
                    }
                }
            }

        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 38.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showConfig) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.92f),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppGreenLight)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Spacer(Modifier.height(8.dp))
                            ConfigPanelContent(
                                models = models,
                                selectedModel = selectedModel,
                                isCameraActive = isCameraActive,
                                onSelectModel = onSelectModel,
                                sensitivity = sensitivity,
                                onSensitivityChange = onSensitivityChange,
                                maxPerFrame = maxPerFrame,
                                onMaxPerFrameChange = onMaxPerFrameChange,
                                maxPerClass = maxPerClass,
                                onMaxPerClassChange = onMaxPerClassChange,
                                markerSizeCm = markerSizeCm,
                                onMarkerSizeCmChange = onMarkerSizeCmChange
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (isCameraActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        val arucoOk = calibrationState is CalibrationState.Calibrated
                        StatusChip(
                            label = if (arucoOk) "✓ ArUco" else "✗ ArUco",
                            active = arucoOk
                        )
                        val objOk = selectedObject != null
                        StatusChip(
                            label = if (objOk) "★ ${selectedObject!!.label} ×" else "Toca un objeto",
                            active = objOk,
                            onClick = if (objOk) onClearSelection else null
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color(0x88000000),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleConfig,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color(0xFF289BAD),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Configuración",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Button(
                        onClick = onToggleCamera,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraActive) Color(0xFF555577) else Color(0xFF2B77CB),
                            disabledContainerColor = Color(0xFF797676)
                        )
                    ) {
                        Text(
                            text = if (isCameraActive) "Detener cámara" else "Calibrar | Detectar",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = onStartStop,
                        enabled = isCameraActive && selectedObject != null,
                        modifier = Modifier.weight(0.7f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color.Red else AppGreenPrimary,
                            disabledContainerColor = Color(0xFF969191)
                        )
                    ) {
                        Text(
                            text = if (isRunning) "Detener" else "Iniciar",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Contenido interno del panel de configuración, compartido entre orientaciones.
 *
 * Incluye el selector de modelo TFLite, el selector de umbral de confianza,
 * los sliders de límite de detecciones por frame y por clase, y el slider
 * del tamaño físico del marcador ArUco.
 *
 * @param models Lista de modelos disponibles.
 * @param selectedModel Modelo actualmente seleccionado.
 * @param isCameraActive `true` si la cámara está activa; deshabilita el selector de modelo.
 * @param onSelectModel Callback invocado al seleccionar un nuevo modelo.
 * @param sensitivity Nivel de sensibilidad de detección actual.
 * @param onSensitivityChange Callback invocado al cambiar la sensibilidad.
 * @param maxPerFrame Número máximo de detecciones por frame.
 * @param onMaxPerFrameChange Callback invocado al cambiar el límite por frame.
 * @param maxPerClass Número máximo de detecciones por clase.
 * @param onMaxPerClassChange Callback invocado al cambiar el límite por clase.
 * @param markerSizeCm Tamaño del marcador ArUco en centímetros.
 * @param onMarkerSizeCmChange Callback invocado al cambiar el tamaño del marcador.
 */
@Composable
private fun ConfigPanelContent(
    models: List<ModelOption>,
    selectedModel: ModelOption,
    isCameraActive: Boolean,
    onSelectModel: (ModelOption) -> Unit,
    sensitivity: Sensitivity,
    onSensitivityChange: (Sensitivity) -> Unit,
    maxPerFrame: Int,
    onMaxPerFrameChange: (Int) -> Unit,
    maxPerClass: Int,
    onMaxPerClassChange: (Int) -> Unit,
    markerSizeCm: Float,
    onMarkerSizeCmChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Modelo", color = AppTextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        InfoTooltip("A mayor tamaño, mayor precisión y menos FPS")
    }
    Spacer(Modifier.height(6.dp))
    ModelDropdown(
        models = models,
        selectedModel = selectedModel,
        enabled = !isCameraActive,
        onSelect = onSelectModel
    )
    Spacer(Modifier.height(10.dp))
    Text("Umbral de Confianza", color = AppTextPrimary, fontWeight = FontWeight.Medium)
    SensitivityRow(value = sensitivity, onChange = onSensitivityChange)
    Spacer(Modifier.height(10.dp))
    Text("Máx. objetos por frame: $maxPerFrame", color = AppTextPrimary)
    Slider(
        value = maxPerFrame.toFloat(),
        onValueChange = { onMaxPerFrameChange(it.toInt().coerceIn(1, 6)) },
        valueRange = 1f..6f,
        steps = 4,
        colors = SliderDefaults.colors(
            thumbColor = AppGreenPrimary,
            activeTrackColor = AppGreenPrimary,
            inactiveTrackColor = AppGreenLight,
            activeTickColor = Color.White,
            inactiveTickColor = AppGreenPrimary,
        )
    )
    Spacer(Modifier.height(10.dp))
    Text("Máx. por clase: $maxPerClass", color = AppTextPrimary)
    Slider(
        value = maxPerClass.toFloat(),
        onValueChange = { onMaxPerClassChange(it.toInt().coerceIn(1, 10)) },
        valueRange = 1f..10f,
        steps = 8,
        colors = SliderDefaults.colors(
            thumbColor = AppGreenPrimary,
            activeTrackColor = AppGreenPrimary,
            inactiveTrackColor = AppGreenLight,
            activeTickColor = Color.White,
            inactiveTickColor = AppGreenPrimary,
        )
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Tamaño marcador ArUco: ${"%.1f".format(markerSizeCm)} cm",
        color = AppTextPrimary
    )
    Slider(
        value = markerSizeCm,
        onValueChange = onMarkerSizeCmChange,
        valueRange = 1f..20f,
        steps = 37,
        colors = SliderDefaults.colors(
            thumbColor = AppGreenPrimary,
            activeTrackColor = AppGreenPrimary,
            inactiveTrackColor = AppGreenLight,
            activeTickColor = Color.White,
            inactiveTickColor = AppGreenPrimary,
        )
    )
    Spacer(Modifier.height(6.dp))
}

/**
 * Dropdown para seleccionar el modelo TFLite activo.
 *
 * Se deshabilita mientras la cámara está activa para evitar cambiar el
 * modelo durante una sesión de detección en curso.
 *
 * @param models Lista de modelos disponibles.
 * @param selectedModel Modelo actualmente seleccionado.
 * @param enabled `false` si el selector debe estar deshabilitado.
 * @param onSelect Callback invocado al seleccionar un modelo de la lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<ModelOption>, selectedModel: ModelOption,
    enabled: Boolean, onSelect: (ModelOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = selectedModel.label, onValueChange = {}, readOnly = true, enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor      = AppTextPrimary,
                unfocusedTextColor    = AppTextPrimary,
                disabledTextColor     = AppTextDisabled,
                focusedBorderColor    = AppGreenPrimary,
                unfocusedBorderColor  = AppGreenLight,
                disabledBorderColor   = AppGreenExtraLight,
                cursorColor           = AppGreenPrimary,
                focusedTrailingIconColor   = AppGreenPrimary,
                unfocusedTrailingIconColor = AppTextSecondary,
                disabledTrailingIconColor  = AppTextDisabled
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(text = { Text(model.label) }, onClick = { expanded = false; onSelect(model) })
            }
        }
    }
}

/**
 * Icono de información con tooltip emergente al pulsarlo.
 *
 * Muestra un [DropdownMenu] con [text] al tocar el icono de ayuda.
 *
 * @param text Texto informativo a mostrar en el tooltip.
 */
@Composable
fun InfoTooltip(text: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = !open }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.HelpOutline, contentDescription = "Info",
                tint = AppTextSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(text) }, onClick = { open = false })
        }
    }
}

/**
 * Fila de chips para seleccionar el nivel de sensibilidad de detección.
 *
 * Muestra un [FilterChip] por cada valor de [Sensitivity].
 *
 * @param value Nivel de sensibilidad actualmente seleccionado.
 * @param onChange Callback invocado al seleccionar un nivel diferente.
 */
@Composable
private fun SensitivityRow(value: Sensitivity, onChange: (Sensitivity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sensitivity.values().forEach { opt ->
            FilterChip(
                selected = (opt == value),
                onClick = { onChange(opt) },
                label = { Text(opt.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppGreenPrimary,
                    selectedLabelColor     = Color.White,
                    containerColor         = AppGreenExtraLight,
                    labelColor             = AppTextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = (opt == value),
                    selectedBorderColor   = AppGreenPrimary,
                    selectedBorderWidth   = 0.dp,
                    borderColor           = AppGreenLight,
                    borderWidth           = 1.dp
                )
            )
        }
    }
}

/**
 * Chip de estado con fondo verde cuando está activo y rojo cuando no lo está.
 *
 * Si se proporciona [onClick], se renderiza como un [Button] interactivo.
 * En caso contrario se renderiza como una [Surface] no interactiva.
 *
 * @param label Texto a mostrar dentro del chip.
 * @param active `true` para fondo verde, `false` para fondo rojo.
 * @param onClick Callback opcional. Si es `null` el chip no es interactivo.
 */
@Composable
private fun StatusChip(label: String, active: Boolean, onClick: (() -> Unit)? = null) {
    val bg = if (active) AppGreenPrimary else Color(0xFF8B0000)
    if (onClick != null) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = bg),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) { Text(label, fontSize = 11.sp, color = Color.White) }
    } else {
        Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.height(32.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(label, fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

/**
 * Formatea una duración en milisegundos al formato `MM:SS:cs`.
 *
 * @param ms Duración en milisegundos.
 * @return Cadena con el formato `mm:ss:cc` donde `cc` son centésimas de segundo.
 */
private fun formatElapsed(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    val milliseconds = (ms % 1000) / 10
    return "%02d:%02d:%02d".format(minutes, seconds, milliseconds)
}