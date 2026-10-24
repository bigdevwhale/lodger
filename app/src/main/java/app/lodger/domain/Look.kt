package app.lodger.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Reads a room photo for places to put marks. Works on a small brightness grid (0..1, row by row):
 * the darkest patch is where someone stands, the brightest is glass, the calmest stretches low,
 * middle and high are floor, table and wall.
 */
object Look {
    fun fallback(s: Spot): Pair<Float, Float> = when (s) {
        Spot.FLOOR -> .5f to .86f
        Spot.TABLE -> .62f to .64f
        Spot.WALL -> .7f to .32f
        Spot.GLASS -> .3f to .3f
        Spot.DARK -> .78f to .55f
        Spot.HIGH -> .5f to .1f
    }

    fun spots(lum: FloatArray, w: Int, h: Int): Map<Spot, Pair<Float, Float>> {
        require(lum.size == w * h && w >= 8 && h >= 8)
        val g = Grid(lum, w, h)
        val dark = g.best(.12f to .88f, .3f to .95f, .14f, .32f) { m, _ -> m }
        val glass = g.best(.08f to .92f, .04f to .7f, .16f, .14f) { m, v -> -m + v * .5f }
        val floor = g.best(.15f to .85f, .78f to .96f, .34f, .08f) { m, v -> v + abs(m - .35f) * .1f }
        val table = g.best(.15f to .85f, .52f to .76f, .26f, .08f) { m, v -> v + abs(m - .45f) * .2f }
        val wall = g.best(.12f to .88f, .14f to .52f, .2f, .12f) { _, v -> v }.let { w0 ->
            // A wall mark shouldn't sit on the window or in the dark corner.
            if (near(w0, glass) || near(w0, dark)) g.best(.12f to .88f, .14f to .52f, .2f, .12f) { _, v -> v }.let { other(it, glass, dark) } else w0
        }
        val high = g.best(.1f to .9f, .02f to .26f, .16f, .1f) { m, v -> m + v }
        return mapOf(Spot.DARK to dark, Spot.GLASS to glass, Spot.FLOOR to floor, Spot.TABLE to table, Spot.WALL to wall, Spot.HIGH to high)
    }

    private fun near(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second) < .18f

    private fun other(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> {
        val x = if (p.first < .5f) p.first + .35f else p.first - .35f
        return (x to p.second).takeIf { !near(it, a) && !near(it, b) } ?: p
    }

    private class Grid(val lum: FloatArray, val w: Int, val h: Int) {
        /** Mean and variance of a window. */
        fun stat(x0: Int, y0: Int, ww: Int, hh: Int): Pair<Float, Float> {
            var s = 0f; var q = 0f
            for (y in y0 until y0 + hh) for (x in x0 until x0 + ww) { val v = lum[y * w + x]; s += v; q += v * v }
            val n = (ww * hh).toFloat()
            val m = s / n
            return m to max(0f, q / n - m * m)
        }

        /** The centre of the window (of [fw]×[fh] of the frame) inside the given ranges with the lowest score. */
        fun best(xs: Pair<Float, Float>, ys: Pair<Float, Float>, fw: Float, fh: Float, score: (Float, Float) -> Float): Pair<Float, Float> {
            val ww = max(1, (fw * w).toInt()); val hh = max(1, (fh * h).toInt())
            val x0 = (xs.first * w).toInt().coerceIn(0, w - ww); val x1 = ((xs.second * w).toInt() - ww).coerceIn(x0, w - ww)
            val y0 = (ys.first * h).toInt().coerceIn(0, h - hh); val y1 = ((ys.second * h).toInt() - hh).coerceIn(y0, h - hh)
            var bx = x0; var by = y0; var bs = Float.MAX_VALUE
            for (y in y0..y1) for (x in x0..x1) {
                val (m, v) = stat(x, y, ww, hh)
                // A slight pull to the middle, so ties don't all end in a corner.
                val s = score(m, v) + abs((x + ww / 2f) / w - .5f) * .02f
                if (s < bs) { bs = s; bx = x; by = y }
            }
            return (bx + ww / 2f) / w to (by + hh / 2f) / h
        }
    }
}
