package com.example.philab.ui.theory.module

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins

@Composable
fun TheoryModuleScreen(
    onBack: () -> Unit,
    onOpenArticle: (articleIndex: Int) -> Unit,
) {
    // TODO: Cambiar navegacion a articulos
    val items = listOf(
        "Artículo 1",
        "Artículo 2",
        "Artículo 3",
        "Artículo 4",
        "Artículo 5",
        "Artículo 6"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.pl_modulebackground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(160.dp))

            Text(
                text = "¡Aprende más\nsobre Física!",
                fontSize = 43.sp,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.Black,
                lineHeight = 52.sp
            )

            Spacer(modifier = Modifier.height(35.dp))

            ArticlesBox(
                titles = items,
                onOpenArticle = onOpenArticle
            )
        }
    }
}

@Composable
private fun ArticlesBox(
    titles: List<String>,
    onOpenArticle: (articleIndex: Int) -> Unit
) {
    val frameShape = RoundedCornerShape(12.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp),
        shape = frameShape,
        color = Color(0xFFAECFFF).copy(alpha = 0.92f),
        border = BorderStroke(2.dp, Color.Black)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(titles) { index, title ->
                ArticleCardPlaceholder(
                    title = title,
                    onClick = { onOpenArticle(index) }
                )
            }
        }
    }
}

@Composable
private fun ArticleCardPlaceholder(
    title: String,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(cardShape)
            .background(Color(0xFF3F51B5).copy(alpha = 0.75f))
            .border(2.dp, Color.Black, cardShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 15.sp
        )

        // Placeholder icono nav
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TheoryModuleScreenPreview() {
    PhiLabTheme {
        TheoryModuleScreen(
            onBack = {},
            onOpenArticle = {},
        )
    }
}