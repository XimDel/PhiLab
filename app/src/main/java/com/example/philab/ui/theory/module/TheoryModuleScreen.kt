package com.example.philab.ui.theory.module

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.philab.ui.theme.PhiLabTheme
import com.example.philab.ui.theme.Poppins
import com.example.philab.ui.theme.AppDrawables

/**
 * Pantalla principal del módulo teórico.
 *
 * Muestra una lista de artículos de física en formato de cuadrícula,
 * permitiendo al usuario navegar entre ellos o regresar a la pantalla anterior.
 *
 * @param onBack Acción ejecutada al presionar el botón de retroceso.
 * @param onOpenArticle Callback que se ejecuta al seleccionar un artículo.
 */
@Composable
fun TheoryModuleScreen(
    onBack: () -> Unit,
    onOpenArticle: (articleId: String) -> Unit,
) {
    val context = LocalContext.current

    val articles: List<Article> = remember {
        ArticleRepository.loadArticles(context)
    }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.Black
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(160.dp))

            Text(
                text = "¡Aprende más\nsobre Física!",
                fontSize = 30.sp,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.Black,
                lineHeight = 52.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.weight(1f)
            ) {
                ArticlesBox(
                    articles = articles,
                    onOpenArticle = onOpenArticle
                )
            }
        }
    }
}

/**
 * Contenedor que muestra la lista de artículos en una cuadrícula escalonada.
 *
 * @param articles Lista de artículos a mostrar.
 * @param onOpenArticle Acción ejecutada al seleccionar un artículo.
 */
@Composable
private fun ArticlesBox(
    articles: List<Article>,
    onOpenArticle: (articleId: String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 17.dp)
            .padding(bottom = 22.dp),
        color = Color.White.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.15f))
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles, key = { it.id }) { article ->
                ArticlePreviewCard(
                    article = article,
                    onClick = { onOpenArticle(article.id) }
                )
            }
        }
    }
}

/**
 * Tarjeta de vista previa de un artículo.
 *
 * Muestra la imagen de portada, el título y un fragmento del contenido.
 *
 * @param article Artículo a representar.
 * @param onClick Acción ejecutada al presionar la tarjeta.
 */
@Composable
private fun ArticlePreviewCard(
    article: Article,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f))
    ) {
        Column {

            ArticleCoverImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                imageName = article.image
            )

            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = article.title,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF1F1F1F)
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = articlePreviewText(article.content, 47),
                    fontFamily = Poppins,
                    fontSize = 12.sp,
                    color = Color(0xFF616161),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Muestra la imagen de portada de un artículo.
 *
 * Intenta resolver dinámicamente el recurso a partir del nombre,
 * utilizando una imagen por defecto en caso de no encontrarlo.
 *
 * @param modifier Modificador de Compose.
 * @param imageName Nombre del recurso drawable.
 */
@Composable
private fun ArticleCoverImage(
    modifier: Modifier,
    imageName: String
) {
    val context = LocalContext.current
    val fallbackRes = AppDrawables.SUB_BACKGROUND

    val resId = remember(imageName) {
        val id = context.resources.getIdentifier(imageName, "drawable", context.packageName)
        if (id != 0) id else fallbackRes
    }

    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        contentScale = ContentScale.Crop
    )
}

/**
 * Genera un texto resumido a partir del contenido de un artículo.
 *
 * Elimina saltos de línea y espacios innecesarios, y recorta el texto
 * a una longitud máxima definida.
 *
 * @param content Texto completo del artículo.
 * @param maxChars Número máximo de caracteres permitidos.
 * @return Texto resumido listo para visualización.
 */
private fun articlePreviewText(content: String, maxChars: Int): String {
    val raw = content
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return if (raw.length <= maxChars) raw else raw.take(maxChars).trimEnd() + "…"
}

/**
 * Vista previa de la pantalla del módulo teórico para herramientas de diseño.
 */
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