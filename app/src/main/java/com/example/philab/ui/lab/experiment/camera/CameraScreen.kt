package com.example.philab.ui.lab.experiment.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.philab.ui.theme.PhiLabTheme

@Composable
fun CameraScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CameraScreen")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Aquí se inicializará CameraX y el overlay en tiempo real.")
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraScreenPreview() {
    PhiLabTheme {
        CameraScreen(onBack = {})
    }
}