package com.example.philab.ui.theory.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.data.repository.ArticleRepository
import com.example.philab.domain.model.Article
import com.example.philab.ui.theme.Poppins
import com.example.philab.ui.theme.AppDrawables

@Composable
fun ArticleScreen(
    articleId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // JSON
    val articles: List<Article> = remember {
        ArticleRepository.loadArticles(context)
    }

    val article = remember(articleId, articles) {
        articles.find { it.id == articleId }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = AppDrawables.SUB_BACKGROUND),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Contenido
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Si no existe artículo
            if (article == null) {
                Spacer(modifier = Modifier.height(52.dp))
                Text(
                    text = "Artículo no encontrado",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )
                return@Column
            }

            // Título
            Spacer(modifier = Modifier.height(70.dp))
            Text(
                text = article.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                fontSize = 30.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Poppins,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Scroll
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {

                // Imagen
                val imageRes = remember(article.image) {
                    context.resources.getIdentifier(article.image, "drawable", context.packageName)
                }

                if (imageRes != 0) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 17.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.FillWidth
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cuerpo
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp)
                        .padding(bottom = 22.dp),
                    color = Color.White.copy(alpha = 0.55f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = article.content,
                        modifier = Modifier.padding(16.dp),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Justify
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// Solo para el preview
@Preview(showBackground = true)
@Composable
private fun ArticleScreenPreview() {
    ArticleScreen(
        articleId = "aceleracion",
        onBack = {}
    )
}