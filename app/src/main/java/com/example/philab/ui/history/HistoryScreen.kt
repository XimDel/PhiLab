package com.example.philab.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.philab.data.local.database.PhiLabDatabase
import com.example.philab.data.repository.SessionRepository
import com.example.philab.ui.theme.AppDrawables
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentGreen   = Color(0xFF1D9E75)
private val AccentRed     = Color(0xFFE53935)
private val AccentBlue    = Color(0xFF2196F3)
private val TextPrimary   = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF7A7A8C)
private val BgRowEven     = Color.White.copy(alpha = 0.70f)
private val BgRowOdd      = Color.White.copy(alpha = 0.50f)
private val BgHeader      = Color.White.copy(alpha = 0.85f)
private val BorderColor   = Color(0xFFCCCCDD)
private val TABLE_MAX_HEIGHT = 252.dp

/**
 * Pantalla de historial de experimentos.
 *
 * Muestra una tabla paginada con todas las sesiones guardadas, permite seleccionar
 * una sesión y ofrece tres acciones sobre ella: consultar sus resultados, renombrar
 * el experimento y eliminarlo permanentemente.
 *
 * La pantalla se construye sobre un fondo decorativo y gestiona internamente los
 * diálogos de renombrado y confirmación de eliminación. El estado de sesiones
 * proviene de [HistoryViewModel], que se crea mediante [HistoryViewModelFactory]
 * a partir del [SessionRepository] local.
 *
 * @param onBack        Callback invocado al pulsar el botón de retroceso.
 * @param onOpenSession Callback invocado al confirmar "Consultar" con el
 *                      identificador de la sesión seleccionada.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(
            SessionRepository(PhiLabDatabase.getInstance(context).sessionDao())
        )
    )

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            Image(
                painter = androidx.compose.ui.res.painterResource(id = AppDrawables.SUB_BACKGROUND),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
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
                    text = "HISTORIAL DE\nEXPERIMENTOS",
                    fontSize = 30.sp,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no hay experimentos guardados",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgHeader)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Spacer(modifier = Modifier.width(32.dp))
                            Text(
                                text = "Nombre experimento",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Fecha",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        HorizontalDivider(color = BorderColor)

                        LazyColumn(modifier = Modifier.heightIn(max = TABLE_MAX_HEIGHT)) {
                            itemsIndexed(sessions) { index, session ->
                                val isSelected = selectedId == session.idSession
                                val bg = when {
                                    isSelected       -> AccentGreen.copy(alpha = 0.15f)
                                    index % 2 == 0   -> BgRowEven
                                    else             -> BgRowOdd
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .clickable {
                                            selectedId =
                                                if (isSelected) null else session.idSession
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) AccentGreen
                                                else Color.White.copy(alpha = 0.8f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentGreen else BorderColor,
                                                RoundedCornerShape(4.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = session.experimentName,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )

                                    Text(
                                        text = dateFormatter.format(Date(session.recordedAt)),
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                if (index < sessions.lastIndex) {
                                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    ActionButton(
                        icon = Icons.Filled.Search,
                        label = "Consultar",
                        color = AccentGreen,
                        enabled = selectedId != null
                    ) { selectedId?.let { onOpenSession(it) } }

                    ActionButton(
                        icon = Icons.Filled.Edit,
                        label = "Modificar\nnombre",
                        color = AccentBlue,
                        enabled = selectedId != null
                    ) { showRenameDialog = true }

                    ActionButton(
                        icon = Icons.Filled.Delete,
                        label = "Eliminar",
                        color = AccentRed,
                        enabled = selectedId != null
                    ) { showDeleteConfirm = true }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showRenameDialog && selectedId != null) {
        val session = sessions.firstOrNull { it.idSession == selectedId }
        if (session != null) {
            RenameDialog(
                currentName = session.experimentName,
                onConfirm = { newName ->
                    viewModel.rename(session.idSession, newName)
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false }
            )
        }
    }

    if (showDeleteConfirm && selectedId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar experimento") },
            text = {
                val name =
                    sessions.firstOrNull { it.idSession == selectedId }?.experimentName ?: ""
                Text("¿Eliminar \"$name\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedId?.let { viewModel.delete(it) }
                    selectedId = null
                    showDeleteConfirm = false
                }) { Text("Eliminar", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Botón de acción compuesto por un icono circular y una etiqueta de texto debajo.
 *
 * Se muestra habilitado o deshabilitado según [enabled], cambiando el color de
 * fondo, borde e icono en consecuencia.
 *
 * @param icon    Icono vectorial que representa la acción.
 * @param label   Texto descriptivo mostrado debajo del icono; admite saltos de línea.
 * @param color   Color de acento aplicado al fondo, borde e icono cuando está habilitado.
 * @param enabled Controla si el botón acepta interacciones del usuario.
 * @param onClick Callback invocado al pulsar el botón cuando está habilitado.
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (enabled) color.copy(alpha = 0.12f)
                    else Color.Gray.copy(alpha = 0.08f)
                )
                .border(
                    1.5.dp,
                    if (enabled) color.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) color else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) color else Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

/**
 * Diálogo modal para cambiar el nombre de un experimento.
 *
 * Muestra un [OutlinedTextField] inicializado con [currentName]. Si el usuario
 * confirma con el campo vacío o solo espacios, se conserva el nombre original.
 *
 * @param currentName Nombre actual del experimento, usado como valor inicial del campo.
 * @param onConfirm   Callback invocado con el nuevo nombre al pulsar "Guardar".
 * @param onDismiss   Callback invocado al cancelar o cerrar el diálogo.
 */
@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Modificar nombre", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val final = text.trim().ifBlank { currentName }
                            onConfirm(final)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("Guardar", color = Color.White) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryModuleScreenPreview() {
    PhiLabTheme {
        HistoryScreen(
            onBack = {},
            onOpenSession = {}
        )
    }
}