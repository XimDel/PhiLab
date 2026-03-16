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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.philab.core.calibration.CalibrationState
import com.example.philab.core.detection.DetectorManager
import com.example.philab.ui.camera.CameraController

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
                cameraController.bindCamera(
                    isCameraActive = { viewModel.isCameraActive },
                    isRecording = { viewModel.isRunning },
                    detectorProvider = { detectorManager.detector },
                    onFps = viewModel::updateFps,
                    onTotalFrames = viewModel::updateTotalFrames,
                    onDetections = viewModel::updateDetections,
                    onDetectorStatus = viewModel::updateDetectorStatus,
                    onCalibration = viewModel::updateCalibrationState,
                    onMeasurement = viewModel::updateMeasurementResult,
                    markerSizeCmProvider = { viewModel.markerSizeCm },
                    enterThresholdProvider = { viewModel.sensitivity.enter },
                    maxPerClassProvider = { viewModel.maxPerClass },
                    maxPerFrameProvider = { viewModel.maxPerFrame },
                    selectedCenterProvider = {
                        viewModel.selectedObject?.let { it.centerX to it.centerY }
                    },
                    onTrackedDetection = viewModel::updateTrackedDetection,
                    sessionRecorder = viewModel.sessionRecorder
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
            isCameraActive = viewModel.isCameraActive
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
                onSave = { onNavigateToResults() },
                onRestart = { viewModel.clearExperimentResults() }
            )
        }
    }
}

@Composable
private fun BoxScope.CameraStatsOverlay(
    fps: Double,
    elapsedMs: Long,
    totalFrames: Long,
    detectorStatus: String,
    livePointCount: Int,
    isRunning: Boolean,
    isCameraActive: Boolean
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
        }
    }
}

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
    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Config modal
            if (showConfig) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    colors = CardDefaults.cardColors(containerColor = Color(0x8C000000))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Modelo", color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            InfoTooltip("A mayor tamaño, mayor precisión y menos FPS")
                        }
                        Spacer(Modifier.height(6.dp))
                        ModelDropdown(models, selectedModel, enabled = !isCameraActive, onSelect = onSelectModel)
                        Spacer(Modifier.height(10.dp))
                        Text("Umbral de Confianza", color = Color.White)
                        SensitivityRow(value = sensitivity, onChange = onSensitivityChange)
                        Spacer(Modifier.height(10.dp))
                        Text("Máx. objetos por frame: $maxPerFrame", color = Color.White)
                        Slider(
                            value = maxPerFrame.toFloat(),
                            onValueChange = { onMaxPerFrameChange(it.toInt().coerceIn(1, 6)) },
                            valueRange = 1f..6f, steps = 4
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Máx. por clase: $maxPerClass", color = Color.White)
                        Slider(
                            value = maxPerClass.toFloat(),
                            onValueChange = { onMaxPerClassChange(it.toInt().coerceIn(1, 10)) },
                            valueRange = 1f..10f, steps = 8
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Tamaño marcador ArUco: ${"%.1f".format(markerSizeCm)} cm", color = Color.White)
                        Slider(
                            value = markerSizeCm,
                            onValueChange = onMarkerSizeCmChange,
                            valueRange = 1f..20f, steps = 37
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Chips de estado
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón volver
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x88000000), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Botón configuración
                IconButton(
                    onClick = onToggleConfig,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF289BAD), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configuración",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Calibrar | Detectar
                Button(
                    onClick = onToggleCamera,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCameraActive) Color(0xFF555577) else Color(0xFF2B77CB),
                        disabledContainerColor = Color(0xFF797676)
                    )
                ) {
                    Text(
                        text = if (isCameraActive) "Detener cámara" else "Calibrar | Detectar",
                        fontSize = 12.sp
                    )
                }

                // Iniciar / Detener grabación
                Button(
                    onClick = onStartStop,
                    enabled = isCameraActive && selectedObject != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color.Red else Color(0xFF1D9E75),
                        disabledContainerColor = Color(0xFF969191)
                    )
                ) {
                    Text(
                        text = if (isRunning) "Detener" else "Iniciar",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

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
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                disabledTextColor = Color.Gray, focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White, cursorColor = Color.White
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

@Composable
fun InfoTooltip(text: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = !open }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.HelpOutline, contentDescription = "Info",
                tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(text) }, onClick = { open = false })
        }
    }
}

@Composable
private fun SensitivityRow(value: Sensitivity, onChange: (Sensitivity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sensitivity.values().forEach { opt ->
            FilterChip(selected = (opt == value), onClick = { onChange(opt) }, label = { Text(opt.label) })
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean,
    onClick: (() -> Unit)? = null
) {
    val bg = if (active) Color(0xFF1D9E75) else Color(0xFF8B0000)
    val textColor = Color.White
    if (onClick != null) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = bg),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(label, fontSize = 11.sp, color = textColor)
        }
    } else {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.height(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(label, fontSize = 11.sp, color = textColor)
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    val milliseconds = (ms % 1000) / 10
    return "%02d:%02d:%02d".format(minutes, seconds, milliseconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Config abierta - cámara inactiva")
@Composable
private fun CameraOverlayConfigOpenPreview() {
    MaterialTheme {
        CameraOverlay(
            isCameraActive = false,
            isRunning = false,
            models = listOf(
                ModelOption("ssd_mobilenet_v1.tflite", "SSD MobileNet v1 (300×300)"),
                ModelOption("efficientdet_lite0.tflite", "EfficientDet Lite0 (320×320)"),
                ModelOption("efficientdet_lite4.tflite", "EfficientDet Lite4 (640×640)")
            ),
            selectedModel = ModelOption("efficientdet_lite0.tflite", "EfficientDet Lite0 (320×320)"),
            onSelectModel = {},
            showConfig = true,
            onToggleConfig = {},
            sensitivity = Sensitivity.ALTA,
            onSensitivityChange = {},
            maxPerFrame = 3,
            onMaxPerFrameChange = {},
            maxPerClass = 5,
            onMaxPerClassChange = {},
            markerSizeCm = 7.5f,
            onMarkerSizeCmChange = {},
            onBack = {},
            onToggleCamera = {},
            onStartStop = {},
            selectedObject = null,
            onClearSelection = {},
            calibrationState = CalibrationState.Idle
        )
    }
}