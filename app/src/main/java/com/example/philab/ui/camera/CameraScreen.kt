package com.example.philab.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.philab.ui.theme.PhiLabTestTheme

@Composable
fun CameraScreen() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    CameraContent(
        hasCameraPermission = hasCameraPermission,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
    )
}

@Composable
private fun CameraContent(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCameraPermission) {
            Text("Permiso autorizado (siguiente: abrir CameraX)")
        } else {
            Text("Necesitamos permiso para usar la cámara")
            Button(onClick = onRequestPermission) {
                Text("Dar permiso")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraScreenPreview_PermissionDenied() {
    PhiLabTestTheme {
        CameraContent(
            hasCameraPermission = false,
            onRequestPermission = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraScreenPreview_PermissionGranted() {
    PhiLabTestTheme {
        CameraContent(
            hasCameraPermission = true,
            onRequestPermission = {}
        )
    }
}
