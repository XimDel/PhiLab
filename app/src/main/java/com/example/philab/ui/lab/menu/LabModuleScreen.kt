package com.example.philab.ui.lab.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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

@Composable
fun LabModuleScreen(
    onBack: () -> Unit,
    onStartExperiment: () -> Unit,
    onHowItWorks: () -> Unit,
    onOpenArucoGenerator: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize()) {

            // Background
            Image(
                painter = painterResource(id = R.drawable.pl_modulescreen),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // flecha atras
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 14.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        //TODO: Agregar volver al HomeScreen
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

                Spacer(modifier = Modifier.height(110.dp))

                Text(
                    text = "Laboratorio\nPortatil",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 52.sp
                )

                Spacer(modifier = Modifier.height(34.dp))

                LabMenuButton(
                    iconRes = R.drawable.cameraicon,
                    text = "Iniciar\nExperimento",
                    containerColor = Color(0xFFBDB4CC),
                    contentColor = Color(0xFF551865),
                    onClick = onStartExperiment
                )

                LabMenuButton(
                    iconRes = R.drawable.helpicon,
                    text = "¿Cómo\nFunciona?",
                    containerColor = Color(0xFF00D0EF),
                    contentColor = Color(0xFF231E5D),
                    onClick = onHowItWorks
                )

                LabMenuButton(
                    iconRes = R.drawable.aruco,
                    text = "Generador\nde ArUco",
                    containerColor = Color(0xFFC5D2C0),
                    contentColor = Color(0xFF1A1A1A),
                    onClick = onOpenArucoGenerator
                )
            }
        }
    }
}

@Composable
private fun LabMenuButton(
    iconRes: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(vertical = 17.dp)
            .fillMaxWidth(0.82f)
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = text,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 25.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 5.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LabModuleScreenPreview() {
    PhiLabTheme {
        LabModuleScreen(
            onBack = {},
            onStartExperiment = {},
            onHowItWorks = {},
            onOpenArucoGenerator = {}
        )
    }
}