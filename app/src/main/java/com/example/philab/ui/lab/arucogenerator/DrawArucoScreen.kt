package com.example.philab.ui.lab.arucogenerator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.R
import com.example.philab.ui.theme.AppDrawables
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins

private data class ArucoStep(
    val number: Int,
    val title: String,
    val description: String,
    val imageRes: Int
)

private val steps = listOf(
    ArucoStep(
        number = 1,
        title = "Dibuja el cuadrado base",
        description = "Con un lápiz y regla dibuja un cuadrado del tamaño elegido. \n Ejemplo: 12 cm × 12 cm.",
        imageRes = R.drawable.aruco1
    ),
    ArucoStep(
        number = 2,
        title = "Divide en una grilla",
        description = "Divide el cuadrado en una grilla de 6 × 6. Todos los cuadritos deben ser iguales.\n En el ejemplo de 12 cm, cada uno mide 2 cm × 2 cm.",
        imageRes = R.drawable.aruco2
    ),
    ArucoStep(
        number = 3,
        title = "Haz el borde negro",
        description = "Rellena con marcador negro los cuadros del borde (todo el contorno). Esto deja una grilla interna de 4 × 4 (como nuestro diccionario), que es donde vive el patrón del ArUco.",
        imageRes = R.drawable.aruco3
    ),
    ArucoStep(
        number = 4,
        title = "Dibuja el patrón interno",
        description = "Copia con marcador negro el patrón de cuadros blancos y negros del ArUco que elegiste desde el generador o como el de la imagen abajo.",
        imageRes = R.drawable.aruco4
    )
)

private val materials = listOf("Regla", "Papel", "Lápiz", "Marcador negro")

@Composable
fun DrawArucoScreen(
    onBack: () -> Unit,
) {
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
                    text = "Cómo dibujar\n un ArUco",
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Poppins,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(5.dp))

                // ── SCROLLEABLE
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {

                    // Nota
                    item {
                        Surface(
                            color = Color(0xFFFFF9C4),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Para mayor precisión, usa el generador de ArUco e imprímelo en PDF a escala.",
                                fontFamily = Poppins,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF5D4037),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Materiales
                    item {
                        SectionCard {
                            Text(
                                text = "Materiales",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            materials.forEach { material ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(Color(0xFF48835E), shape = RoundedCornerShape(50))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = material,
                                        fontFamily = Poppins,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Antes de empezar
                    item {
                        SectionCard {
                            Text(
                                text = "Antes de empezar",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Elige el tamaño de tu ArUco (entre 1 y 20 cm). En esta guía usaremos 12 cm como ejemplo.",
                                fontFamily = Poppins,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = Color(0xFF333333)
                            )
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                    }

                    // Título sección pasos
                    item {
                        Text(
                            text = "Pasos:",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }

                    // Pasos
                    items(steps) { step ->
                        StepCard(step = step)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Mensaje final
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "¡Listo! Ya puedes usar tu ArUco en tus experimentos.",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF48835E),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Image(
                            painter = painterResource(id = R.drawable.arucotest),
                            contentDescription = "ArUco generado",
                            modifier = Modifier
                                .size(300.dp)
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.75f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    }
}

@Composable
private fun StepCard(step: ArucoStep) {
    Surface(
        color = Color.White.copy(alpha = 0.75f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF48835E), shape = RoundedCornerShape(50))
                ) {
                    Text(
                        text = step.number.toString(),
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step.title,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = step.description,
                fontFamily = Poppins,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = step.imageRes),
                contentDescription = "Paso ${step.number}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawArucoScreenPreview() {
    PhiLabTheme {
        DrawArucoScreen(onBack = {})
    }
}