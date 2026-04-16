package com.example.philab.core.camera

import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/**
 * Rastrea un objeto usando flujo óptico (Lucas-Kanade) de forma estable.
 *
 * La idea es detectar puntos “buenos” dentro de la región del objeto y seguirlos
 * de un frame a otro. En lugar de recalcular el tamaño del bounding box cada vez,
 * simplemente lo movemos usando la mediana del desplazamiento de esos puntos.
 * Así evitamos que el cuadro se deforme o se “encoga” con el tiempo.
 *
 * Para conseguir los puntos a rastrear sigue este orden:
 * 1. Intenta detectar puntos (Shi-Tomasi) dentro del bbox.
 * 2. Si no hay suficientes, busca en una zona un poco más grande alrededor.
 * 3. Si aún falla, usa una grilla de puntos en el borde como último recurso.
 */
class OpticalFlowTracker {

    /**
     * Resultado posible de cada llamada a [update].
     */
    sealed class TrackResult {
        /**
         * El objeto fue rastreado correctamente en este fotograma.
         *
         * @property bbox Bounding box actualizado del objeto.
         */
        data class Found(val bbox: Rect) : TrackResult()

        /** El objeto se perdió y el tracker fue reiniciado. */
        object Lost : TrackResult()

        /**
         * El tracker acaba de ser inicializado. Se emite durante exactamente un fotograma
         * para indicar que aún no hay desplazamiento calculable.
         */
        object JustInitialized : TrackResult()
    }

    private var prevGray = Mat()
    private var trackedPoints = MatOfPoint2f()
    private var initialized = false
    private var justInited = false

    private var currentBbox = Rect()

    private var framesSinceInit = 0
    private var framesSinceFeatureReinit = 0

    /** Número de fotogramas de gracia tras la inicialización antes de evaluar pérdida. */
    val GRACE_FRAMES = 4

    /** Fotogramas transcurridos desde la última llamada a [init]. */
    val framesAfterInit: Int get() = framesSinceInit

    private val lkWinSize = Size(15.0, 15.0)
    private val lkMaxLevel = 2
    private val lkCriteria = TermCriteria(
        TermCriteria.COUNT or TermCriteria.EPS, 20, 0.03
    )

    private val MIN_VALID_POINTS = 1
    private val REINIT_FEATURES_EVERY = 25
    private val MAX_FEATURES = 60
    private val QUALITY_LEVEL = 0.01
    private val MIN_DISTANCE = 4.0

    private val cornersRaw = MatOfPoint()
    private val TAG = "OptFlowTracker"

    /**
     * Inicializa el tracker sobre un fotograma en escala de grises y un bounding box.
     *
     * Extrae puntos de interés dentro de [bbox], clona el fotograma como referencia
     * para el siguiente [update] y registra el tamaño del bbox original.
     * Si el Mat es inválido o el bbox resultante es demasiado pequeño, la inicialización
     * falla silenciosamente y el tracker permanece inactivo.
     *
     * @param gray Fotograma actual en escala de grises.
     * @param bbox Región del objeto a rastrear en coordenadas del fotograma.
     */
    fun init(gray: Mat, bbox: Rect) {
        reset()
        if (!isValid(gray)) {
            Log.i(TAG, "INIT FAIL: gray inválido cols=${gray.cols()} rows=${gray.rows()}")
            return
        }

        val safe = bbox.clamp(gray.cols(), gray.rows())
        Log.i(TAG, "INIT: bbox original=$bbox → safe=$safe imageSize=${gray.cols()}x${gray.rows()}")

        if (safe.width < 10 || safe.height < 10) {
            Log.i(TAG, "INIT FAIL: bbox demasiado pequeño ${safe.width}x${safe.height}")
            return
        }

        val strategy = loadFeaturesDetailed(gray, safe)
        Log.i(TAG, "INIT: feature strategy=$strategy pts=${trackedPoints.rows()}")

        if (trackedPoints.rows() < MIN_VALID_POINTS) {
            Log.i(TAG, "INIT FAIL: insuficientes puntos ${trackedPoints.rows()}")
            return
        }

        prevGray = gray.clone()
        currentBbox = safe
        initialized = true
        justInited = true
        framesSinceInit = 0
        framesSinceFeatureReinit = 0
        Log.i(TAG, "INIT OK: ${trackedPoints.rows()} puntos, bbox=$safe")
    }

    /**
     * Actualiza el tracker con el fotograma actual y devuelve el resultado del rastreo.
     *
     * Ejecuta LK entre el fotograma anterior y [gray], filtra los puntos válidos,
     * calcula el desplazamiento mediano y lo aplica al bbox preservando su tamaño.
     * Cada [REINIT_FEATURES_EVERY] fotogramas recarga los features sobre el bbox actualizado.
     *
     * @param gray Fotograma actual en escala de grises.
     * @return [TrackResult.Found] con el nuevo bbox, [TrackResult.JustInitialized] en el primer
     *   fotograma tras [init], o [TrackResult.Lost] si el rastreo falló.
     */
    fun update(gray: Mat): TrackResult {
        if (!initialized) return TrackResult.Lost

        framesSinceInit++

        if (justInited) {
            justInited = false
            Log.i(TAG, "UPDATE frame=$framesSinceInit: JustInitialized")
            return TrackResult.JustInitialized
        }

        if (!isValid(gray) || !isValid(prevGray)) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL mat inválido")
            reset(); return TrackResult.Lost
        }

        val ptCount = trackedPoints.rows()
        if (ptCount < MIN_VALID_POINTS) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL pocos puntos $ptCount")
            reset(); return TrackResult.Lost
        }

        val sameSize = gray.cols() == prevGray.cols() && gray.rows() == prevGray.rows()
        if (!sameSize) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL tamaño cambió")
            reset(); return TrackResult.Lost
        }

        Log.i(TAG, "UPDATE frame=$framesSinceInit: LK con $ptCount puntos, img=${gray.cols()}x${gray.rows()}")

        val nextPts    = MatOfPoint2f()
        val status     = MatOfByte()
        val err        = MatOfFloat()
        val ptsBack    = MatOfPoint2f()
        val statusBack = MatOfByte()
        val errBack    = MatOfFloat()

        try {
            Video.calcOpticalFlowPyrLK(
                prevGray, gray, trackedPoints, nextPts,
                status, err, lkWinSize, lkMaxLevel, lkCriteria
            )

            val fwdStatus = status.toArray()
            val fwdNext   = nextPts.toArray()
            val fwdPrev   = trackedPoints.toArray()
            val fwdErr    = err.toArray()

            Log.i(TAG, "LK result: statusArr=${fwdStatus.size} nextArr=${fwdNext.size}")

            if (fwdStatus.isEmpty()) {
                Log.i(TAG, "LK FAIL: status array vacío → Lost")
                reset(); return TrackResult.Lost
            }

            val statusOk   = fwdStatus.count { it.toInt() == 1 }
            val statusFail = fwdStatus.count { it.toInt() == 0 }
            val avgErr = if (fwdErr.isNotEmpty()) fwdErr.map { it.toDouble() }.average() else -1.0
            Log.i(TAG, "LK status: OK=$statusOk FAIL=$statusFail avgErr=${"%.2f".format(avgErr)}")

            fwdPrev.take(3).forEachIndexed { i, p ->
                val s = if (i < fwdStatus.size) fwdStatus[i].toInt() else -1
                val n = if (i < fwdNext.size) fwdNext[i] else Point(-1.0, -1.0)
                Log.i(TAG, "  pt[$i] prev=(${p.x.toInt()},${p.y.toInt()}) → next=(${n.x.toInt()},${n.y.toInt()}) status=$s")
            }

            val validNext = mutableListOf<Point>()
            val dxList    = mutableListOf<Double>()
            val dyList    = mutableListOf<Double>()
            var outOfBounds = 0

            for (i in fwdStatus.indices) {
                if (fwdStatus[i].toInt() != 1) continue
                val pNext = if (i < fwdNext.size) fwdNext[i] else continue
                val pPrev = if (i < fwdPrev.size) fwdPrev[i] else continue

                if (pNext.x >= 0 && pNext.x < gray.cols() &&
                    pNext.y >= 0 && pNext.y < gray.rows()) {
                    validNext.add(pNext)
                    dxList.add(pNext.x - pPrev.x)
                    dyList.add(pNext.y - pPrev.y)
                } else {
                    outOfBounds++
                }
            }

            Log.i(TAG, "valid=${validNext.size} outOfBounds=$outOfBounds MIN=$MIN_VALID_POINTS")

            if (validNext.size < MIN_VALID_POINTS) {
                Log.i(TAG, "RESULT: Lost (valid=${validNext.size})")
                reset(); return TrackResult.Lost
            }

            val medianDx = median(dxList)
            val medianDy = median(dyList)

            val newX = (currentBbox.x + medianDx).toInt().coerceIn(0, gray.cols() - 1)
            val newY = (currentBbox.y + medianDy).toInt().coerceIn(0, gray.rows() - 1)

            val newBbox = Rect(newX, newY, currentBbox.width, currentBbox.height)
                .clamp(gray.cols(), gray.rows())

            currentBbox = newBbox

            framesSinceFeatureReinit++
            if (framesSinceFeatureReinit >= REINIT_FEATURES_EVERY) {
                framesSinceFeatureReinit = 0
                loadFeaturesDetailed(gray, newBbox)
            } else {
                trackedPoints.release()
                trackedPoints = MatOfPoint2f(*validNext.toTypedArray())
            }

            if (!prevGray.empty()) prevGray.release()
            prevGray = gray.clone()

            Log.i(TAG, "RESULT: Found bbox=$newBbox dx=${"%.1f".format(medianDx)} dy=${"%.1f".format(medianDy)}")
            return TrackResult.Found(newBbox)

        } catch (t: Throwable) {
            Log.e(TAG, "UPDATE EXCEPCIÓN: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            reset(); return TrackResult.Lost
        } finally {
            nextPts.release();    status.release();     err.release()
            ptsBack.release();    statusBack.release(); errBack.release()
        }
    }

    /**
     * Reinicia completamente el tracker, liberando todos los recursos nativos de OpenCV.
     *
     * Tras esta llamada [isActive] devuelve `false` y el tracker queda listo para
     * una nueva llamada a [init].
     */
    fun reset() {
        initialized = false
        justInited = false
        framesSinceInit = 0
        framesSinceFeatureReinit = 0
        currentBbox = Rect()
        try { trackedPoints.release() } catch (_: Throwable) {}
        trackedPoints = MatOfPoint2f()
        try { if (!prevGray.empty()) prevGray.release() } catch (_: Throwable) {}
        prevGray = Mat()
    }

    /**
     * Indica si el tracker fue inicializado y está rastreando activamente un objeto.
     *
     * @return `true` si [init] fue exitoso y no se ha llamado a [reset].
     */
    fun isActive() = initialized

    /**
     * Calcula la mediana de una lista de valores [Double].
     *
     * Se usa en lugar de la media para tolerar outliers de LK, como features que
     * se desplazan hacia regiones de fondo con textura similar al objeto.
     *
     * @param values Lista de desplazamientos en una dimensión.
     * @return Mediana de los valores, o `0.0` si la lista está vacía.
     */
    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
        else sorted[mid]
    }

    /**
     * Intenta cargar features de interés dentro de [bbox] usando tres estrategias en cascada.
     *
     * Registra en logcat la estrategia utilizada y el número de puntos obtenidos.
     *
     * @param gray Fotograma actual en escala de grises.
     * @param bbox Región sobre la que extraer los features.
     * @return Cadena descriptiva de la estrategia aplicada y la cantidad de puntos cargados.
     */
    private fun loadFeaturesDetailed(gray: Mat, bbox: Rect): String {
        val roi = Mat(gray, bbox)
        cornersRaw.release()
        var shiTomasiCount = 0
        try {
            Imgproc.goodFeaturesToTrack(roi, cornersRaw, MAX_FEATURES, QUALITY_LEVEL, MIN_DISTANCE)
            shiTomasiCount = cornersRaw.rows()
            Log.i(TAG, "  Shi-Tomasi en bbox $bbox → $shiTomasiCount corners")
        } catch (e: Throwable) {
            Log.e(TAG, "  Shi-Tomasi EXCEPCIÓN: ${e.message}")
        } finally {
            roi.release()
        }

        if (shiTomasiCount >= MIN_VALID_POINTS) {
            val arr = cornersRaw.toArray()
            val shifted = Array(arr.size) { i -> Point(arr[i].x + bbox.x, arr[i].y + bbox.y) }
            try { trackedPoints.release() } catch (_: Throwable) {}
            trackedPoints = MatOfPoint2f(*shifted)
            return "shi-tomasi($shiTomasiCount)"
        }

        val expandX = (bbox.width * 0.25).toInt().coerceAtLeast(6)
        val expandY = (bbox.height * 0.25).toInt().coerceAtLeast(6)
        val ringBbox = Rect(
            (bbox.x - expandX).coerceAtLeast(0),
            (bbox.y - expandY).coerceAtLeast(0),
            (bbox.width + 2 * expandX).coerceAtMost(gray.cols() - (bbox.x - expandX).coerceAtLeast(0)),
            (bbox.height + 2 * expandY).coerceAtMost(gray.rows() - (bbox.y - expandY).coerceAtLeast(0))
        )
        val roiRing = Mat(gray, ringBbox)
        cornersRaw.release()
        var ringCount = 0
        try {
            Imgproc.goodFeaturesToTrack(roiRing, cornersRaw, MAX_FEATURES, QUALITY_LEVEL, MIN_DISTANCE)
            ringCount = cornersRaw.rows()
            Log.i(TAG, "  Shi-Tomasi en ring $ringBbox → $ringCount corners")
        } catch (e: Throwable) {
            Log.e(TAG, "  Shi-Tomasi ring EXCEPCIÓN: ${e.message}")
        } finally {
            roiRing.release()
        }

        if (ringCount >= MIN_VALID_POINTS) {
            val arr = cornersRaw.toArray()
            val shifted = Array(arr.size) { i -> Point(arr[i].x + ringBbox.x, arr[i].y + ringBbox.y) }
            try { trackedPoints.release() } catch (_: Throwable) {}
            trackedPoints = MatOfPoint2f(*shifted)
            return "ring-shi-tomasi($ringCount)"
        }

        val borderPts = borderGridPoints(bbox, gray.cols(), gray.rows())
        Log.i(TAG, "  Grilla borde → ${borderPts.size} puntos")
        try { trackedPoints.release() } catch (_: Throwable) {}
        trackedPoints = MatOfPoint2f(*borderPts.toTypedArray())
        return "border-grid(${borderPts.size})"
    }

    /**
     * Genera una grilla de puntos distribuidos uniformemente sobre el borde del [bbox].
     *
     * Se usa como estrategia de último recurso cuando Shi-Tomasi no encuentra features
     * ni en el interior ni en el anillo expandido del bbox.
     *
     * @param bbox Región cuyo borde se va a muestrear.
     * @param imgW Ancho del fotograma, usado para descartar puntos fuera de límites.
     * @param imgH Alto del fotograma, usado para descartar puntos fuera de límites.
     * @return Lista de puntos válidos sobre el perímetro del bbox.
     */
    private fun borderGridPoints(bbox: Rect, imgW: Int, imgH: Int): List<Point> {
        val pts = mutableListOf<Point>()
        val steps = 8
        for (i in 0 until steps) {
            val x = bbox.x + bbox.width.toDouble() * i / (steps - 1)
            listOf(bbox.y.toDouble(), (bbox.y + bbox.height - 1).toDouble()).forEach { y ->
                if (x in 0.0..(imgW - 1).toDouble() && y in 0.0..(imgH - 1).toDouble())
                    pts.add(Point(x, y))
            }
        }
        for (i in 1 until steps - 1) {
            val y = bbox.y + bbox.height.toDouble() * i / (steps - 1)
            listOf(bbox.x.toDouble(), (bbox.x + bbox.width - 1).toDouble()).forEach { x ->
                if (x in 0.0..(imgW - 1).toDouble() && y in 0.0..(imgH - 1).toDouble())
                    pts.add(Point(x, y))
            }
        }
        return pts
    }

    /**
     * Verifica que un [Mat] de OpenCV es válido y utilizable.
     *
     * @param mat Mat a verificar.
     * @return `true` si el Mat tiene dirección nativa válida, dimensiones mayores a cero y no está vacío.
     */
    private fun isValid(mat: Mat): Boolean = try {
        !mat.empty() && mat.cols() > 0 && mat.rows() > 0 && mat.nativeObjAddr != 0L
    } catch (_: Throwable) { false }

    /**
     * Restringe el bounding box para que quede completamente dentro de una imagen de tamaño [w]×[h].
     *
     * @receiver Rect a restringir.
     * @param w Ancho máximo de la imagen.
     * @param h Alto máximo de la imagen.
     * @return Nuevo [Rect] con coordenadas y dimensiones dentro de los límites de la imagen.
     */
    private fun Rect.clamp(w: Int, h: Int): Rect {
        val x1 = x.coerceIn(0, (w - 1).coerceAtLeast(0))
        val y1 = y.coerceIn(0, (h - 1).coerceAtLeast(0))
        val x2 = (x + width).coerceIn(x1 + 1, w)
        val y2 = (y + height).coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
    }
}