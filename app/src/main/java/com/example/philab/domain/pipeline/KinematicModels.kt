package com.example.philab.domain.pipeline

/**
 * Punto crudo tal como llega desde el [com.example.philab.domain.experiment.SessionRecorder],
 * antes de cualquier limpieza o transformación.
 *
 * @property tSec Tiempo en segundos desde el inicio de la sesión.
 * @property x Posición horizontal en centímetros, o en píxeles si no hay calibración activa.
 * @property y Posición vertical en centímetros, o en píxeles si no hay calibración activa.
 */
data class RawPoint(
    val tSec: Float,
    val x: Float,
    val y: Float
)

/**
 * Punto tras la etapa de detección de outliers y suavizado de posición.
 *
 * @property tSec Tiempo en segundos desde el inicio de la sesión.
 * @property x Posición horizontal, potencialmente suavizada.
 * @property y Posición vertical, potencialmente suavizada.
 * @property isValid `false` si el punto fue marcado como outlier y debe excluirse
 *   del cálculo de derivadas.
 * @property isInterpolated `true` si el valor fue generado por interpolación para
 *   rellenar un gap. Reservado para uso futuro; actualmente el pipeline no interpola.
 */
data class CleanPoint(
    val tSec: Float,
    val x: Float,
    val y: Float,
    val isValid: Boolean,
    val isInterpolated: Boolean = false
)

/**
 * Punto con cinemática calculada por el [DerivativeCalculator].
 *
 * @property tSec Tiempo en segundos desde el inicio de la sesión.
 * @property x Posición horizontal suavizada en la unidad del experimento.
 * @property y Posición vertical suavizada en la unidad del experimento.
 * @property velocity Velocidad instantánea `dx/dt` en unidades por segundo.
 * @property acceleration Aceleración instantánea `dv/dt` en unidades por segundo al cuadrado.
 */
data class MotionPoint(
    val tSec: Float,
    val x: Float,
    val y: Float,
    val velocity: Float,
    val acceleration: Float
)

/**
 * Datos finales listos para ser graficados por la UI.
 *
 * Cada serie puede tener una cantidad de puntos diferente porque el downsampling
 * se aplica de forma independiente sobre cada una.
 *
 * @property position Serie de pares `(tiempo, posición)` para la gráfica de posición.
 * @property velocity Serie de pares `(tiempo, velocidad)` para la gráfica de velocidad.
 * @property acceleration Serie de pares `(tiempo, aceleración)` para la gráfica de aceleración.
 * @property unit Unidad de medida del experimento (`"cm"` o `"px"`).
 * @property totalPoints Número de puntos originales antes del downsampling.
 * @property cleanedPoints Número de puntos válidos tras la etapa de limpieza.
 * @property outliersRemoved Número de puntos descartados por el detector de outliers.
 * @property gapsInterpolated Número de gaps rellenados por interpolación (actualmente siempre 0).
 */
data class ChartData(
    val position: List<Pair<Float, Float>>,
    val velocity: List<Pair<Float, Float>>,
    val acceleration: List<Pair<Float, Float>>,
    val unit: String,
    val totalPoints: Int,
    val cleanedPoints: Int,
    val outliersRemoved: Int,
    val gapsInterpolated: Int
)

/**
 * Resultado completo devuelto por el [KinematicPipeline] tras procesar un experimento.
 *
 * Incluye las etapas intermedias para facilitar la depuración y el análisis,
 * además de los datos finales listos para la UI.
 *
 * @property raw Puntos tal como llegaron del conversor, sin ningún procesamiento.
 * @property clean Puntos tras la detección de outliers y el suavizado de posición.
 * @property motion Puntos con velocidad y aceleración calculadas y suavizadas.
 * @property chart Datos reducidos y organizados por serie, listos para graficar.
 */
data class PipelineResult(
    val raw: List<RawPoint>,
    val clean: List<CleanPoint>,
    val motion: List<MotionPoint>,
    val chart: ChartData
)

/**
 * Parámetros de configuración del pipeline cinemático.
 *
 * Permite ajustar el comportamiento de cada etapa sin modificar el código
 * de los procesadores. Todos los valores tienen un valor por defecto razonable
 * para experimentos típicos de laboratorio.
 *
 * @property madMultiplier Multiplicador MAD para la detección de outliers por posición local.
 *   Valores más altos hacen la detección menos agresiva.
 * @property windowSize Tamaño de la ventana deslizante para la detección local de outliers.
 * @property velocityMadMultiplier Multiplicador MAD para la detección de outliers por velocidad instantánea.
 * @property minPointsForStats Número mínimo de puntos requeridos para calcular estadísticos MAD.
 *   Si hay menos puntos, todos se marcan como válidos.
 * @property minDtMs Intervalo de tiempo mínimo en segundos entre puntos consecutivos.
 *   Actúa como cota inferior en las divisiones para evitar inestabilidad numérica por jitter.
 * @property velocityWindowSec Ancho en segundos de la ventana de regresión local usada
 *   para la posición suavizada y la velocidad.
 * @property accelerationWindowSec Ancho en segundos de la ventana de regresión local usada
 *   para la aceleración. Es mayor porque la segunda derivada amplifica más el ruido.
 * @property minWindowPoints Número mínimo de puntos de cada ventana de regresión local.
 * @property maxChartPoints Número máximo de puntos por serie tras el downsampling.
 * @property minChartPoints Número mínimo de puntos por serie para no perder la forma de la señal.
 */
data class PipelineConfig(
    val madMultiplier: Float = 3.5f,
    val windowSize: Int = 5,
    val velocityMadMultiplier: Float = 4f,
    val minPointsForStats: Int = 5,
    val minDtMs: Float = 0.005f,
    val velocityWindowSec: Float = 0.25f,
    val accelerationWindowSec: Float = 0.5f,
    val minWindowPoints: Int = 5,
    val maxChartPoints: Int = 400,
    val minChartPoints: Int = 50
)