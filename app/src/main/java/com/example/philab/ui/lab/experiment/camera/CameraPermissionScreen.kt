package com.example.philab.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.philab.R
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins
import com.example.philab.ui.theme.AppDrawables

/**
 * Pantalla que gestiona el flujo de solicitud del permiso de cámara.
 *
 * Comprueba el estado del permiso en el momento de composición, escucha el ciclo de
 * vida para revalidarlo al volver desde Ajustes del sistema, y delega la presentación
 * visual en [CameraPermissionContent].
 *
 * El flujo implementado es el siguiente:
 * - Si el permiso ya está concedido, invoca [onPermissionGranted] inmediatamente.
 * - Si el permiso no ha sido solicitado aún, muestra el botón "Dar permiso".
 * - Si el usuario denegó el permiso y marcó "No volver a preguntar", muestra el
 *   botón "Ir a Ajustes" que abre la pantalla de permisos de la app en el sistema.
 * - Al volver desde Ajustes con el permiso concedido, navega automáticamente.
 *
 * @param onPermissionGranted Callback invocado cuando el permiso de cámara está disponible.
 * @param onBack              Callback invocado al pulsar el botón de retroceso.
 */
@Composable
fun CameraPermissionScreen(
    onPermissionGranted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    var hasCameraPermission by remember { mutableStateOf(checkPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var shouldGoToSettings by remember { mutableStateOf(false) }

    fun updateSettingsFlag(permissionGranted: Boolean) {
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.CAMERA
        )
        shouldGoToSettings = !permissionGranted && askedOnce && !showRationale
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        askedOnce = true
        hasCameraPermission = granted
        updateSettingsFlag(granted)
        if (granted) onPermissionGranted()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = checkPermission()
                hasCameraPermission = grantedNow
                updateSettingsFlag(grantedNow)
                if (grantedNow) onPermissionGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) onPermissionGranted()
    }

    if (!hasCameraPermission) {
        CameraPermissionContent(
            showGoToSettings    = shouldGoToSettings,
            onRequestPermission = {
                askedOnce = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = { openAppSettings(context) },
            onBack         = onBack
        )
    }
}

/**
 * Contenido visual de la pantalla de permiso de cámara.
 *
 * Muestra un icono de cámara, un mensaje explicativo y uno de dos botones de acción
 * según si el usuario debe ser dirigido a los Ajustes del sistema o puede solicitar
 * el permiso directamente desde la app.
 *
 * @param showGoToSettings    Si `true`, muestra el botón "Ir a Ajustes" en lugar de
 *                            "Dar permiso".
 * @param onRequestPermission Callback para lanzar el diálogo del sistema de solicitud
 *                            de permiso.
 * @param onOpenSettings      Callback para abrir la pantalla de permisos de la app en
 *                            los Ajustes del sistema.
 * @param onBack              Callback invocado al pulsar "Volver".
 */
@Composable
private fun CameraPermissionContent(
    showGoToSettings: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = AppDrawables.SUB_BACKGROUND),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(id = R.drawable.cameraicon),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (showGoToSettings) {
                    "El permiso de cámara está desactivado.\nActívalo en Ajustes para continuar."
                } else {
                    "Se necesita autorización para usar la cámara."
                },
                fontWeight = FontWeight.Normal,
                fontFamily = Poppins,
                textAlign = TextAlign.Center,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (showGoToSettings) {
                Button(
                    onClick = onOpenSettings,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5061C7),
                        contentColor   = Color(0xFFFFFFFF)
                    )
                ) {
                    Text(
                        text = "Ir a Ajustes",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Poppins,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5061C7),
                        contentColor   = Color(0xFFFFFFFF)
                    )
                ) {
                    Text(
                        text = "Dar permiso",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Poppins,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBack,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.55f),
                    contentColor   = Color.Black
                )
            ) {
                Text(text = "Volver", fontWeight = FontWeight.SemiBold, fontFamily = Poppins)
            }
        }
    }
}

/**
 * Abre la pantalla de permisos de la aplicación en los Ajustes del sistema,
 * permitiendo al usuario conceder manualmente el permiso de cámara.
 *
 * @param context Contexto usado para construir y lanzar el [Intent].
 */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Recorre la cadena de [ContextWrapper] hasta encontrar el [Activity] subyacente.
 *
 * @return El [Activity] que contiene este contexto.
 * @throws IllegalStateException si no se encuentra ningún [Activity] en la cadena.
 */
private fun Context.findActivity(): Activity {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("Activity not found in context chain.")
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionPreview_Request() {
    PhiLabTheme {
        CameraPermissionContent(
            showGoToSettings    = false,
            onRequestPermission = {},
            onOpenSettings      = {},
            onBack              = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionPreview_Settings() {
    PhiLabTheme {
        CameraPermissionContent(
            showGoToSettings    = true,
            onRequestPermission = {},
            onOpenSettings      = {},
            onBack              = {}
        )
    }
}