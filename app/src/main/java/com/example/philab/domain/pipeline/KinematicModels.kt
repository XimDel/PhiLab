package com.example.philab.domain.pipeline

// ─────────────────────────────────────────────────────────────────────────────
// MODELOS DE DATOS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Punto crudo tal como viene del SessionRecorder.
 * tSec: tiempo en segundos desde el inicio de la sesión.
 * x   : posición horizontal en cm (o px si no hay calibración).
 */
data class RawPoint(
    val tSec: Float,
    val x: Float,
    val y: Float
)

/**
 * Punto después de la etapa de limpieza.
 * isValid=false indica outlier detectado (no se usa en derivadas).
 * Si se interpoló, isInterpolated=true.
 */
data class CleanPoint(
    val tSec: Float,
    val x: Float,
    val y: Float,
    val isValid: Boolean,
    val isInterpolated: Boolean = false
)

/**
 * Punto con cinemática calculada.
 * velocity: dx/dt en unidad/s
 * acceleration: dv/dt en unidad/s²
 */
data class MotionPoint(
    val tSec: Float,
    val x: Float,
    val y: Float,
    val velocity: Float,
    val acceleration: Float
)

/**
 * Estructura final lista para graficar.
 * Cada lista puede tener distinta cantidad de puntos (downsampling independiente).
 */
data class ChartData(
    val position: List<Pair<Float, Float>>,      // (t, x)
    val velocity: List<Pair<Float, Float>>,      // (t, v)
    val acceleration: List<Pair<Float, Float>>,  // (t, a)
    val unit: String,                            // "cm" o "px"
    val totalPoints: Int,                        // puntos originales antes de downsample
    val cleanedPoints: Int,                      // puntos válidos después de limpieza
    val outliersRemoved: Int,                    // cuántos outliers se eliminaron
    val gapsInterpolated: Int                    // cuántos gaps se rellenaron
)

/**
 * Resultado completo del pipeline, incluyendo datos intermedios
 * para depuración y los datos finales para UI.
 */
data class PipelineResult(
    val raw: List<RawPoint>,
    val clean: List<CleanPoint>,
    val motion: List<MotionPoint>,
    val chart: ChartData
)

/**
 * Configuración del pipeline. Permite ajustar el comportamiento
 * sin tocar el código de las etapas.
 */
data class PipelineConfig(
    // ── Outlier Detection ──
    val madMultiplier: Float = 3.5f,        // qué tan agresiva es la detección MAD
    val windowSize: Int = 5,                // ventana local para detección
    val velocityMadMultiplier: Float = 4f,  // MAD sobre velocidades instantáneas
    val minPointsForStats: Int = 5,         // mínimo de puntos para calcular estadísticos

    // ── Gap Handling ──
    val maxGapToInterpolate: Float = 0.5f,  // segundos; gaps mayores se ignoran
    val minDtMs: Float = 0.005f,            // dt mínimo en segundos (5 ms) — anti-jitter

    // ── Smoothing ──
    val smoothingWindowSize: Int = 5,       // ventana del filtro LOWESS simplificado
    val smoothingPasses: Int = 2,           // pasadas del suavizado

    // ── Downsampling ──
    val maxChartPoints: Int = 400,          // límite de puntos por gráfica
    val minChartPoints: Int = 50            // mínimo para no perder forma
)