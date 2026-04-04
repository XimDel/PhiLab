package com.example.philab.ui.lab.arucogenerator

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.domain.aruco.ArucoGenerator
import com.example.philab.ui.theme.AppDrawables
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArucoGeneratorScreen(
    onBack: () -> Unit,
    onNavigateToDrawGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val markerIdOptions = remember { (0..49).map { it.toString() } }
    val markerSizeOptions = remember { (3..20).map { it.toString() } }

    var markerId by remember { mutableStateOf(0) }
    var markerSize by remember { mutableStateOf(10) }
    var markerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(markerId) {
        val bmp = withContext(Dispatchers.Default) {
            ArucoGenerator.generateBitmap(markerId, pixelSize = 600)
        }
        markerBitmap = bmp
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            Image(
                painter = painterResource(id = AppDrawables.SUB_BACKGROUND),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 14.dp, end = 14.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(150.dp))

                Text(
                    text = "GENERADOR DE\nArUco",
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Poppins,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(5.dp))

                // ── Botón guía de dibujo
                TextButton(
                    onClick = onNavigateToDrawGuide,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.HelpOutline,
                        contentDescription = null,
                        tint = Color(0xFF48835E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "¿No puedes imprimir?\nAprende a dibujar tu ArUco",
                        fontFamily = Poppins,
                        fontSize = 12.sp,
                        color = Color(0xFF48835E)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ArUco live preview
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = markerBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "ArUco ID $markerId",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color(0xFF48835E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = Color.White.copy(alpha = 0.55f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MarkerDropdownRow(
                            label = "Marker ID:",
                            selectedValue = markerId.toString(),
                            options = markerIdOptions,
                            onValueSelected = { markerId = it.toIntOrNull() ?: 0 },
                            infoText = "Identificador único del marcador dentro del diccionario (4x4_50)."
                        )

                        MarkerDropdownRow(
                            label = "Marker size:",
                            selectedValue = markerSize.toString(),
                            options = markerSizeOptions,
                            onValueSelected = { markerSize = it.toIntOrNull() ?: 10 },
                            infoText = "Tamaño físico de impresión en centímetros.\n\n3 cm → detección hasta ~30 cm.\n10 cm → detección hasta ~1.5 m.\n20 cm → detección hasta ~3 m.\n\nEl PDF se guardará a escala real."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { showSaveDialog = true },
                    enabled = !isSaving && markerBitmap != null,
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFCFFFD9),
                        contentColor = Color(0xFF48835E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .height(56.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF48835E),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "GUARDAR",
                            fontSize = 18.sp,
                            color = Color(0xFF48835E),
                            fontWeight = FontWeight.Bold,
                            fontFamily = Poppins
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) { data ->
                Snackbar(snackbarData = data)
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Guardar marcador",
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Se guardará un PDF de ${markerSize}×${markerSize} cm con el marcador ID $markerId en tu carpeta de Descargas. ¿Continuar?",
                    fontFamily = Poppins,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        isSaving = true
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                ArucoGenerator.exportPdf(
                                    context = context,
                                    markerId = markerId,
                                    sizeInCm = markerSize
                                ) { success, fileName ->
                                    coroutineScope.launch {
                                        isSaving = false
                                        val msg = if (success)
                                            "Guardado en Descargas: $fileName"
                                        else
                                            "Error al guardar el archivo."
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Guardar PDF", fontFamily = Poppins, color = Color(0xFF48835E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", fontFamily = Poppins)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerDropdownRow(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueSelected: (String) -> Unit,
    infoText: String
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(135.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontFamily = Poppins,
                color = Color.Black
            )
            Spacer(modifier = Modifier.width(6.dp))
            InfoTooltip(infoText)
        }

        Spacer(modifier = Modifier.weight(0.2f))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(2f)
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = Poppins,
                    color = Color.Black
                ),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier
                    .menuAnchor()
                    .width(110.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontFamily = Poppins) },
                        onClick = {
                            onValueSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun InfoTooltip(text: String) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(26.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.HelpOutline,
            contentDescription = "Info",
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK", fontFamily = Poppins)
                }
            },
            text = {
                Text(
                    text = text,
                    fontFamily = Poppins,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ArucoGeneratorScreenPreview() {
    PhiLabTheme {
        ArucoGeneratorScreen(onBack = {})
    }
}