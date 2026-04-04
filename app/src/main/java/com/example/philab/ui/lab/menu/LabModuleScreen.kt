package com.example.philab.ui.lab.menu

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
import com.example.philab.ui.theme.Poppins
import com.example.philab.ui.theme.AppDrawables

@Composable
fun LabModuleScreen(
    onBack: () -> Unit,
    onStartExperiment: () -> Unit,
    onHowItWorks: () -> Unit,
    onOpenArucoGenerator: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize()) {

            Image(
                painter = painterResource(id = AppDrawables.SUB_BACKGROUND),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

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
                    text = "LABORATORIO\nPORTATIL",
                    fontSize = 30.sp,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 52.sp
                )

                Spacer(modifier = Modifier.height(35.dp))

                LabMenuButton(
                    iconRes = R.drawable.cameraicon,
                    text = "Iniciar\nExperimento",
                    containerColor = Color(0xFFAECFFF),
                    contentColor = Color(0xFF263070),
                    onClick = onStartExperiment
                )

                LabMenuButton(
                    iconRes = R.drawable.helpicon,
                    text = "¿Cómo\nFunciona?",
                    containerColor = Color(0xFFA0D8A2),
                    contentColor = Color(0xFF184919),
                    onClick = onHowItWorks
                )

                LabMenuButton(
                    iconRes = R.drawable.aruco,
                    text = "Marcador\n ArUco",
                    containerColor = Color(0xFFD9E2E1),
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
            .padding(vertical = 16.dp)
            .fillMaxWidth(0.73f)
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

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                fontSize = 18.sp,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier
                    .weight(1.6f)
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