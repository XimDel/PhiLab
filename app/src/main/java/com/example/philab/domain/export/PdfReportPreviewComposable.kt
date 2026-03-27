package com.example.philab.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Paleta espejo del PdfExporter
// ─────────────────────────────────────────────────────────────────────────────

private val PdfBgPage = Color(0xFF12121F)
private val PdfBgHeader = Color(0xFF1A1A2E)
private val PdfBgSection = Color(0xFF1E1E35)
private val PdfBgRowA = Color(0xFF1A1A2E)
private val PdfBgRowB = Color(0xFF16162A)
private val PdfAccent = Color(0xFF26D9A0)
private val PdfAccent2 = Color(0xFF4FC3F7)
private val PdfText = Color(0xFFFFFFFF)
private val PdfTextSecondary = Color(0xFF9090B0)
private val PdfDivider = Color(0xFF252540)

private data class PreviewPoint(
    val index: Int,
    val tSeconds: Double,
    val x: Double,
    val y: Double
)

private data class PreviewPdfReport(
    val label: String,
    val date: String,
    val duration: String,
    val samples: Int,
    val frequency: String,
    val scale: String,
    val unit: String,
    val trackedObject: String,
    val distance: String,
    val displacement: String,
    val avgSpeed: String,
    val avgAccel: String,
    val points: List<PreviewPoint>
)

@Composable
private fun PdfReportPreviewScreen(
    report: PreviewPdfReport,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PdfBgPage)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PdfHeader(
            label = report.label,
            date = report.date
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            PdfSectionTitle("METADATA DE LA SESIÓN")

            PdfKeyValueCard(
                rows = listOf(
                    "Fecha y hora" to report.date,
                    "Duración" to report.duration,
                    "Muestras" to report.samples.toString(),
                    "Frecuencia" to report.frequency,
                    "Escala (cm/px)" to report.scale,
                    "Unidad" to report.unit,
                    "Objeto seguido" to report.trackedObject
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            PdfSectionTitle("RESUMEN CINEMÁTICO")

            PdfKpiGrid(
                kpis = listOf(
                    Triple("Distancia total", report.distance, PdfAccent),
                    Triple("Desplazamiento", report.displacement, PdfAccent2),
                    Triple("Velocidad media", report.avgSpeed, PdfAccent),
                    Triple("Aceleración media", report.avgAccel, PdfAccent2)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            PdfSectionTitle("DATOS CAPTURADOS (${report.points.size} puntos)")

            PdfTable(
                headers = listOf("#", "t (s)", "x (${report.unit})", "y (${report.unit})"),
                rows = report.points.map {
                    listOf(
                        it.index.toString(),
                        String.format("%.3f", it.tSeconds),
                        String.format("%.2f", it.x),
                        String.format("%.2f", it.y)
                    )
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "PhiLab · Vista previa del PDF",
                color = PdfTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PdfHeader(
    label: String,
    date: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PdfBgHeader)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "PhiLab",
                        color = PdfAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reporte de experimento",
                        color = PdfTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = label,
                        color = PdfText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = date,
                        color = PdfTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(PdfAccent)
        )
    }
}

@Composable
private fun PdfSectionTitle(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = PdfAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(2.dp)
                .background(PdfAccent)
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun PdfKeyValueCard(
    rows: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PdfBgSection)
            .padding(vertical = 6.dp)
    ) {
        rows.forEachIndexed { index, (key, value) ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = PdfDivider,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key,
                    color = PdfTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = value,
                    color = PdfText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun PdfKpiGrid(
    kpis: List<Triple<String, String, Color>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        kpis.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { (label, value, accentColor) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PdfBgSection)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(vertical = 8.dp)
                                .width(4.dp)
                                .height(56.dp)
                                .background(accentColor)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                color = PdfTextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = value,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PdfTable(
    headers: List<String>,
    rows: List<List<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(0.6.dp, PdfDivider, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PdfAccent)
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            headers.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    color = PdfBgHeader,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) PdfBgRowA else PdfBgRowB)
                    .padding(vertical = 9.dp, horizontal = 8.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        modifier = Modifier.weight(1f),
                        color = PdfText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(
    name = "PDF Preview - PhiLab",
    showBackground = true,
    backgroundColor = 0xFF12121F,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun PdfReportPreview() {
    val report = PreviewPdfReport(
        label = "Pelota",
        date = "26/03/2026  18:42:10",
        duration = "12.50 s",
        samples = 8,
        frequency = "30 Hz",
        scale = "0.041 cm/px",
        unit = "cm",
        trackedObject = "Sports Ball",
        distance = "120.50 cm",
        displacement = "95.20 cm",
        avgSpeed = "10.30 cm/s",
        avgAccel = "1.80 cm/s²",
        points = listOf(
            PreviewPoint(1, 0.000, 0.00, 0.00),
            PreviewPoint(2, 0.250, 8.20, 0.90),
            PreviewPoint(3, 0.500, 18.40, 1.60),
            PreviewPoint(4, 0.750, 30.10, 2.40),
            PreviewPoint(5, 1.000, 43.80, 3.10),
            PreviewPoint(6, 1.250, 59.00, 4.00),
            PreviewPoint(7, 1.500, 76.20, 5.10),
            PreviewPoint(8, 1.750, 95.20, 6.30)
        )
    )

    Surface(color = PdfBgPage) {
        PdfReportPreviewScreen(report = report)
    }
}