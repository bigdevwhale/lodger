package app.lodger.ui.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import app.lodger.domain.Room
import kotlin.random.Random

/**
 * Drawn rooms of an old flat, from the design prototype. They stand in for real photos in the demo and
 * in screenshots; the app itself only ever shows the user's own photos.
 */
object Rooms {
    const val W = 360f
    const val H = 240f

    fun draw(room: Room, width: Int = 960): Bitmap {
        val height = (width * H / W).toInt()
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(width / W, height / H)
        val d = Draw(c, Random(room.ordinal + 7))
        when (room) {
            Room.HALL -> d.hall()
            Room.KITCHEN -> d.kitchen()
            Room.ROOM -> d.room()
            Room.BATH -> d.bath()
            Room.BALCONY -> d.balcony()
        }
        d.grain()
        return b
    }

    private class Draw(val c: Canvas, val r: Random) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun col(v: Long, a: Float = 1f) = ((v and 0xFFFFFF) or ((a * 255).toLong() shl 24)).toInt()
        fun fill(v: Long, a: Float = 1f) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; color = col(v, a) }
        fun stroke(v: Long, w: Float, a: Float = 1f) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = w; color = col(v, a) }
        fun rect(x: Float, y: Float, w: Float, h: Float, paint: Paint) = c.drawRect(x, y, x + w, y + h, paint)
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float, paint: Paint) = c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
        fun path(d: String, paint: Paint) = c.drawPath(PathParser.createPathFromPathData(d), paint)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) = c.drawLine(x1, y1, x2, y2, paint)

        fun stripes(step: Int, a: Float, vertical: Boolean = true) {
            val s = stroke(0xE0CCA0, 1f, a)
            if (vertical) for (x in 0..360 step step) line(x.toFloat(), 0f, x.toFloat(), 240f, s)
            else for (y in 0..240 step step) line(0f, y.toFloat(), 360f, y.toFloat(), s)
        }

        fun night(x: Float, y: Float, w: Float, h: Float) {
            p.reset(); p.shader = LinearGradient(0f, y, 0f, y + h, col(0x0A0E19), col(0x252A39), Shader.TileMode.CLAMP)
            rect(x, y, w, h, p); p.shader = null
        }

        fun mirror(x: Float, y: Float, w: Float, h: Float, round: Boolean) {
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(x + w * .38f, y + h * .32f, maxOf(w, h) * .85f, col(0x8F8572), col(0x2F2A23), Shader.TileMode.CLAMP)
            if (round) oval(x + w / 2, y + h / 2, w / 2, h / 2, p) else rect(x, y, w, h, p)
            p.shader = null
        }

        fun hall() {
            rect(0f, 0f, 360f, 240f, fill(0x3D3127)); stripes(18, .05f)
            rect(0f, 180f, 360f, 60f, fill(0x241C15)); line(0f, 180f, 360f, 180f, stroke(0x15100B, 3f))
            rect(40f, 36f, 84f, 144f, fill(0x2B221A)); rect(40f, 36f, 84f, 144f, stroke(0x18120D, 3f))
            rect(52f, 50f, 60f, 50f, stroke(0x1F1812, 2f)); rect(52f, 110f, 60f, 58f, stroke(0x1F1812, 2f))
            c.drawCircle(113f, 114f, 3.5f, fill(0x8A7552))
            mirror(175f, 46f, 60f, 92f, true); oval(205f, 92f, 30f, 46f, stroke(0x6B5636, 4f))
            line(266f, 56f, 344f, 56f, stroke(0x6B5636, 3f))
            path("M286 56q-16 42-9 96h30q5-52-8-96z", fill(0x251D16)); path("M322 56q-6 30-2 58h16q2-30-6-58z", fill(0x2E241B))
            path("M58 196h22v8h-24zM86 198h22v8h-24z", fill(0x120D09))
            rect(290f, 150f, 40f, 26f, fill(0x4A3A2B))
        }

        fun kitchen() {
            rect(0f, 0f, 360f, 240f, fill(0x3F3428)); stripes(22, .04f)
            rect(0f, 190f, 360f, 50f, fill(0x261D15)); line(0f, 190f, 360f, 190f, stroke(0x15100B, 3f))
            night(210f, 34f, 112f, 88f); rect(210f, 34f, 112f, 88f, stroke(0x6B5A44, 5f))
            c.drawCircle(300f, 54f, 6f, fill(0xDDD5B8, .75f))
            line(266f, 34f, 266f, 122f, stroke(0x6B5A44, 3f)); line(210f, 72f, 322f, 72f, stroke(0x6B5A44, 3f))
            rect(18f, 112f, 74f, 78f, fill(0x4D443A)); rect(28f, 140f, 54f, 36f, fill(0x2A241E))
            oval(38f, 112f, 10f, 2.6f, fill(0x1B1611)); oval(72f, 112f, 10f, 2.6f, fill(0x1B1611))
            rect(112f, 20f, 36f, 24f, fill(0x2B241C)); rect(112f, 20f, 36f, 24f, stroke(0x7A6A50, 2f))
            for (y in listOf(26f, 31f, 36f, 41f)) line(116f, y, 144f, y, stroke(0x7A6A50, 1f))
            rect(186f, 150f, 156f, 9f, fill(0x5B4733)); rect(196f, 159f, 6f, 32f, fill(0x3D2F22)); rect(326f, 159f, 6f, 32f, fill(0x3D2F22))
        }

        fun room() {
            rect(0f, 0f, 360f, 240f, fill(0x3A2F26)); stripes(26, .05f)
            oval(136f, 92f, 60f, 50f, fill(0xE0B060, .07f))
            rect(0f, 186f, 360f, 54f, fill(0x231B14)); oval(220f, 212f, 118f, 16f, fill(0x3B2219, .85f))
            night(34f, 34f, 92f, 112f); rect(34f, 34f, 92f, 112f, stroke(0x5F4F3C, 5f))
            line(80f, 34f, 80f, 146f, stroke(0x5F4F3C, 3f)); line(34f, 88f, 126f, 88f, stroke(0x5F4F3C, 3f))
            rect(28f, 144f, 104f, 6f, fill(0x5F4F3C))
            path("M24 24q16 62 0 128h18q-8-64 0-128z", fill(0x5A2F26)); path("M136 24q-16 62 0 128h-18q8-64 0-128z", fill(0x5A2F26))
            c.drawRoundRect(RectF(150f, 126f, 300f, 166f), 8f, 8f, fill(0x4A3528)); c.drawRoundRect(RectF(144f, 150f, 306f, 184f), 8f, 8f, fill(0x553D2E))
            rect(312f, 24f, 48f, 162f, fill(0x2C2219)); line(336f, 24f, 336f, 186f, stroke(0x16100B, 2f))
            line(138f, 186f, 138f, 98f, stroke(0x1A140F, 3f)); path("M124 98h28l-6-22h-16z", fill(0x7A5A32))
        }

        fun bath() {
            rect(0f, 0f, 360f, 240f, fill(0x454036)); stripes(20, .07f); stripes(20, .07f, vertical = false)
            rect(0f, 192f, 360f, 48f, fill(0x28241E))
            path("M16 132h178v34q0 26-26 26H42q-26 0-26-26z", fill(0xCFC6B2, .82f)); c.drawRoundRect(RectF(12f, 128f, 198f, 136f), 4f, 4f, fill(0xE2DAC8))
            path("M40 128v-16h14", stroke(0x9A8F78, 4f))
            mirror(238f, 36f, 74f, 84f, false); rect(238f, 36f, 74f, 84f, stroke(0x8A7A5E, 3f))
            path("M244 136h62q-4 22-31 22t-31-22z", fill(0xD9D1BF)); rect(270f, 156f, 10f, 36f, fill(0xCFC6B2))
            rect(318f, 14f, 30f, 22f, fill(0x2B2620)); rect(318f, 14f, 30f, 22f, stroke(0x8A7A5E, 2f))
            for (y in listOf(20f, 25f, 30f)) line(322f, y, 344f, y, stroke(0x8A7A5E, 1f))
        }

        fun balcony() {
            night(0f, 0f, 360f, 240f)
            repeat(46) { c.drawCircle(r.nextFloat() * 360f, r.nextFloat() * 130f, .5f + r.nextFloat(), fill(0xECE5CC, .4f + r.nextFloat() * .6f)) }
            c.drawCircle(292f, 46f, 16f, fill(0xE6DFC4, .9f)); c.drawCircle(299f, 41f, 15f, fill(0x10162A))
            for ((x, y, w) in listOf(Triple(40f, 118f, 60f), Triple(104f, 98f, 70f), Triple(178f, 126f, 44f), Triple(226f, 108f, 58f), Triple(288f, 122f, 72f))) {
                rect(x, y, w, 200f - y, fill(0x080A10))
                var yy = y + 10
                while (yy < 196) {
                    var xx = x + 6
                    while (xx < x + w - 8) { if (r.nextFloat() < .22f) rect(xx, yy, 6f, 7f, fill(0xD8B25A, .35f + r.nextFloat() * .5f)); xx += 13 }
                    yy += 14
                }
            }
            rect(0f, 150f, 360f, 4f, fill(0x2E2822))
            for (k in 0 until 20) line(40f + k * 17, 154f, 40f + k * 17, 204f, stroke(0x2E2822, 2f))
            rect(0f, 204f, 360f, 36f, fill(0x1A1612)); rect(0f, 0f, 36f, 240f, fill(0x2D251D))
        }

        /** A little sensor noise, so the drawing reads as a photo taken in a dim flat. */
        fun grain() {
            repeat(5000) { rect(r.nextFloat() * 360f, r.nextFloat() * 240f, .5f, .5f, fill(if (r.nextBoolean()) 0xFFFFFF else 0x000000, .07f)) }
        }
    }
}
