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
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins

/**
 * Pantalla principal de la aplicación.
 *
 * Presenta el menú inicial con accesos a los distintos módulos:
 * conceptos teóricos, laboratorio y resultados.
 *
 * @param onOpenTheory Acción para navegar al módulo teórico.
 * @param onOpenHistory Acción para navegar al historial de resultados.
 * @param onOpenLab Acción para navegar al módulo de laboratorio.
 */
@Composable
fun HomeScreen(
    onOpenTheory: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLab: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.pl_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 25.dp, top = 310.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            HomeMenuButton(
                text = "Conceptos",
                containerColor = Color(0xFFF2B4A5),
                contentColor = Color(0xFF983939),
                onClick = onOpenTheory
            )

            HomeMenuButton(
                text = "Laboratorio",
                containerColor = Color(0xFFAECFFF),
                contentColor = Color(0xFF0B3A78),
                onClick = onOpenLab
            )

            HomeMenuButton(
                text = "Resultados",
                containerColor = Color(0xFF76BD9D),
                contentColor = Color(0xFF1B5E20),
                onClick = onOpenHistory
            )
        }
    }
}

/**
 * Botón reutilizable del menú principal.
 *
 * Define el estilo visual y comportamiento de los botones mostrados
 * en la pantalla de inicio.
 *
 * @param text Texto del botón.
 * @param containerColor Color de fondo del botón.
 * @param contentColor Color del texto.
 * @param onClick Acción ejecutada al presionar el botón.
 */
@Composable
private fun HomeMenuButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 18.dp)
            .width(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(vertical = 5.dp)
        )
    }
}

/**
 * Vista previa de la pantalla principal para herramientas de diseño.
 */
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PhiLabTheme {
        HomeScreen(
            onOpenTheory = {},
            onOpenLab = {},
            onOpenHistory = {}
        )
    }
}