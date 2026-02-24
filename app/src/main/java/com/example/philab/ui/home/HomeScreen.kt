package com.example.philab.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.R
import com.example.philab.ui.theme.PhiLabTestTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartExperiment: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    HomeContent(
        isReady = state.isReady,
        onOpenTheory = {
            // TODO: crear TheoryModuleScreen, agregar navegacion
            // navController.navigate(Routes.THEORY_Module)
        },
        onStartExperiment = {
            viewModel.onEvent(HomeEvent.StartExperiment)
            onStartExperiment()
        },
        onOpenHistory = {
            viewModel.onEvent(HomeEvent.OpenHistory)
            onOpenHistory()
        }
    )
}

@Composable
private fun HomeContent(
    isReady: Boolean,
    onOpenTheory: () -> Unit,
    onStartExperiment: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Background
        Image(
            painter = painterResource(id = R.drawable.homescreenbackground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Botones
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, top = 300.dp, end = 0.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            HomeMenuButton(
                text = "Módulo\nTeórico",
                containerColor = Color(0xFFEED0D0),
                contentColor = Color(0xFF983939),
                enabled = true,
                onClick = onOpenTheory
            )

            HomeMenuButton(
                text = "Módulo\nPráctico",
                containerColor = Color(0xFFAECFFF),
                contentColor = Color(0xFF0B3A78),
                enabled = isReady,
                onClick = onStartExperiment
            )

            HomeMenuButton(
                text = "Historial y\nReportes",
                containerColor = Color(0xFF5CF68C),
                contentColor = Color(0xFF1B5E20),
                enabled = true,
                onClick = onOpenHistory
            )
        }
    }
}

@Composable
private fun HomeMenuButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(vertical = 17.dp)
            .width(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 25.sp,
            modifier = Modifier.padding(vertical = 5.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PhiLabTestTheme {
        HomeContent(
            isReady = true,
            onOpenTheory = {},
            onStartExperiment = {},
            onOpenHistory = {}
        )
    }
}