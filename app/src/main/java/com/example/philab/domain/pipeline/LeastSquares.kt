package com.example.philab.domain.pipeline

import kotlin.math.abs

/**
 * Regresión polinomial por mínimos cuadrados ordinarios, con matriz de covarianza
 * de los coeficientes para propagar incertidumbres.
 *
 * Trabaja en doble precisión y con el tiempo centrado y escalado a `[-1, 1]`
 * aproximadamente, para que la matriz normal esté bien condicionada aunque los
 * tiempos sean grandes o el polinomio sea de grado 3.
 */
object LeastSquares {

    /**
     * Resultado de un ajuste polinomial `y = c0 + c1·τ + c2·τ² + …` con `τ = (t - center) / scale`.
     *
     * @property coefficients Coeficientes `c0..cd` en la variable normalizada τ.
     * @property covariance Matriz de covarianza de los coeficientes (`σ² · (XᵀX)⁻¹`).
     * @property center Tiempo usado como origen de τ.
     * @property scale Factor de escala del tiempo (semi-rango de los datos).
     * @property rss Suma de cuadrados de los residuos.
     * @property dof Grados de libertad (`n - (d + 1)`).
     */
    class PolyFit(
        val coefficients: DoubleArray,
        val covariance: Array<DoubleArray>,
        val center: Double,
        val scale: Double,
        val rss: Double,
        val dof: Int
    ) {
        /** Varianza estimada del ruido de las observaciones. */
        val residualVariance: Double get() = if (dof > 0) rss / dof else 0.0

        /** Evalúa el polinomio en el tiempo [t]. */
        fun valueAt(t: Double): Double {
            val tau = (t - center) / scale
            var result = 0.0
            for (k in coefficients.indices.reversed()) result = result * tau + coefficients[k]
            return result
        }

        /**
         * Varianza de una combinación lineal `gᵀ·c` de los coeficientes.
         *
         * @param g Gradiente de la cantidad derivada respecto a cada coeficiente.
         */
        fun varianceOf(g: DoubleArray): Double {
            var v = 0.0
            for (i in g.indices) for (j in g.indices) v += g[i] * covariance[i][j] * g[j]
            return v.coerceAtLeast(0.0)
        }
    }

    /**
     * Ajusta un polinomio de grado [degree] a los pares `(t[i], y[i])` con índice en [indices].
     *
     * @param center Origen del tiempo. Por defecto, el punto medio del rango.
     * @return El ajuste, o `null` si hay menos puntos que coeficientes o la matriz es singular.
     */
    fun fit(
        t: DoubleArray,
        y: DoubleArray,
        degree: Int,
        indices: IntArray = IntArray(t.size) { it },
        center: Double? = null
    ): PolyFit? {
        val p = degree + 1
        if (indices.size < p) return null

        var tMin = Double.POSITIVE_INFINITY
        var tMax = Double.NEGATIVE_INFINITY
        for (i in indices) { tMin = minOf(tMin, t[i]); tMax = maxOf(tMax, t[i]) }
        val c = center ?: ((tMin + tMax) / 2.0)
        val s = maxOf(abs(tMax - c), abs(tMin - c)).takeIf { it > 1e-12 } ?: return null

        val ata = Array(p) { DoubleArray(p) }
        val aty = DoubleArray(p)
        val powers = DoubleArray(2 * p - 1)
        for (i in indices) {
            val tau = (t[i] - c) / s
            powers[0] = 1.0
            for (k in 1 until powers.size) powers[k] = powers[k - 1] * tau
            for (r in 0 until p) {
                aty[r] += powers[r] * y[i]
                for (col in 0 until p) ata[r][col] += powers[r + col]
            }
        }

        val inv = invert(ata) ?: return null
        val coef = DoubleArray(p) { r -> (0 until p).sumOf { col -> inv[r][col] * aty[col] } }

        var rss = 0.0
        for (i in indices) {
            val tau = (t[i] - c) / s
            var model = 0.0
            for (k in coef.indices.reversed()) model = model * tau + coef[k]
            val r = y[i] - model
            rss += r * r
        }

        val dof = indices.size - p
        val sigma2 = if (dof > 0) rss / dof else 0.0
        val cov = Array(p) { r -> DoubleArray(p) { col -> inv[r][col] * sigma2 } }
        return PolyFit(coef, cov, c, s, rss, dof)
    }

    /** Invierte una matriz cuadrada pequeña por Gauss-Jordan con pivoteo parcial. */
    private fun invert(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { r -> DoubleArray(2 * n) { col -> if (col < n) m[r][col] else if (col - n == r) 1.0 else 0.0 } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-12) return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val div = a[col][col]
            for (k in 0 until 2 * n) a[col][k] /= div
            for (r in 0 until n) {
                if (r == col) continue
                val factor = a[r][col]
                if (factor == 0.0) continue
                for (k in 0 until 2 * n) a[r][k] -= factor * a[col][k]
            }
        }
        return Array(n) { r -> DoubleArray(n) { col -> a[r][col + n] } }
    }
}
