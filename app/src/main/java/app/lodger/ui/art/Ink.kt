package app.lodger.ui.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.lodger.domain.Mark
import app.lodger.domain.Pos
import app.lodger.domain.Trace
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a layer meets the photo: lying on top (shadows, dust and things), or screened over it (breath,
 * frost, glints). Darkness is drawn as dark colour with alpha rather than multiplied: PorterDuff's
 * multiply blackens transparent pixels, and true blend modes need Android 10.
 */
enum class Blend { NORMAL, SCREEN }

/** A rendered piece of a mark, placed at [x], [y] in the photo's pixels. */
class Layer(val bmp: Bitmap, val x: Float, val y: Float, val blend: Blend)

/**
 * The lodger's marks, painted into the photo's own pixel space. Solid things are dimmed to the photo's
 * light; light things (breath on glass, frost, a greasy palm on a mirror) screen over it.
 * Edges are broken with noise and softened with blur; marks on the floor lie flat in perspective.
 */
object Ink {
    /** [lum] is the photo's brightness around the spot (0..1); [hand] is the lodger's handwriting. */
    fun layers(t: Trace, p: Pos, w: Int, h: Int, seed: Int, lum: Float, hand: Typeface?): List<Layer> {
        val k = Ctx(w, h, p.x * w, p.y * h, Random(seed), lum.coerceIn(0f, 1f), hand)
        return when (t.mark) {
            Mark.FOOTPRINTS -> k.steps(wet = false)
            Mark.WET -> k.steps(wet = true)
            Mark.PAWS -> k.paws()
            Mark.HAND -> k.hand(onGlass = t.at != "wall")
            Mark.FIGURE -> figure(w, h, p, if (t.small) .17f else .46f, standing = !t.small)
            Mark.EYES -> k.eyes()
            Mark.CHAIR -> k.chair()
            Mark.CUP -> k.cup()
            Mark.CRUMBS -> k.crumbs()
            Mark.THREAD -> k.thread()
            Mark.WRITING -> k.writing(t.sign)
            Mark.FROST -> k.frost()
            Mark.DOOR -> k.gap()
            Mark.LETTER -> k.letter()
            Mark.KEYS -> k.keys()
            Mark.LIGHT -> k.light()
            Mark.CAT -> k.cat()
        }
    }

    /** Someone standing in the dark part of the room, [size] of the frame's height tall. */
    fun figure(w: Int, h: Int, p: Pos, size: Float, standing: Boolean = true): List<Layer> {
        val hf = size * h
        val cx = p.x * w
        // The dark spot is the middle of a patch: stand on its lower edge, never below the frame.
        val feet = if (standing) min(p.y * h + hf * .42f, h * .98f) else p.y * h + hf * .5f
        val pad = Pad(hf * .7f, hf * 1.15f, cx, feet - hf * .5f)
        val path = person(hf).apply { offset(0f, hf * .5f) }
        pad.c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); alpha = 80; maskFilter = BlurMaskFilter(hf * .07f, BlurMaskFilter.Blur.NORMAL) })
        pad.c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, -hf * .5f, 0f, hf * .5f, intArrayOf(0xD8000000.toInt(), 0xC0000000.toInt(), 0x30000000), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            maskFilter = BlurMaskFilter(hf * .012f, BlurMaskFilter.Blur.NORMAL)
        })
        pad.noise(hf * .09f, .22f, Random(hf.toInt()))
        return listOf(pad.layer(Blend.NORMAL))
    }

    /** A human outline [hf] tall, feet at y = 0, drawn as one closed path. */
    private fun person(hf: Float): Path {
        // The right half, from the crown down to the inside of the leg; the left is its mirror.
        val half = listOf(
            0f to -1f, .045f to -.985f, .062f to -.94f, .058f to -.89f, .04f to -.85f, .036f to -.82f,
            .09f to -.8f, .135f to -.77f, .15f to -.72f, .155f to -.6f, .16f to -.47f, .155f to -.39f, .14f to -.36f,
            .125f to -.4f, .118f to -.55f, .11f to -.6f, .105f to -.45f, .11f to -.36f, .095f to -.2f, .085f to -.04f,
            .095f to 0f, .035f to 0f, .03f to -.05f, .02f to -.3f, 0f to -.34f,
        )
        val pts = half + half.reversed().drop(1).dropLast(1).map { -it.first to it.second }
        return Path().apply {
            moveTo(pts[0].first * hf, pts[0].second * hf)
            for (i in 1..pts.size) {
                val (x0, y0) = pts[i - 1]; val (x1, y1) = pts[i % pts.size]
                quadTo(x0 * hf, y0 * hf, (x0 + x1) / 2 * hf, (y0 + y1) / 2 * hf)
            }
            close()
        }
    }

    /** Lays the layers over a photo: for tests, thumbnails and the share card. */
    fun composite(photo: Bitmap, layers: List<Layer>): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        for (l in layers) c.drawBitmap(l.bmp, l.x, l.y, Paint().apply {
            xfermode = if (l.blend == Blend.SCREEN) PorterDuffXfermode(PorterDuff.Mode.SCREEN) else null
        })
        return out
    }

    /** A transparent bitmap centred on (cx, cy) in the photo, with its canvas's origin at that centre. */
    private class Pad(w: Float, h: Float, val cx: Float, val cy: Float) {
        val bw = max(2, ceil(w).toInt()); val bh = max(2, ceil(h).toInt())
        val bmp: Bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { translate(bw / 2f, bh / 2f) }

        fun layer(b: Blend) = Layer(bmp, cx - bw / 2f, cy - bh / 2f, b)

        /** Breaks the alpha up with soft value noise: [cell] is the blob size, [depth] how much is eaten. */
        fun noise(cell: Float, depth: Float, r: Random) {
            val gw = (bw / cell).toInt() + 3; val gh = (bh / cell).toInt() + 3
            val g = FloatArray(gw * gh) { r.nextFloat() }
            val fine = FloatArray(gw * gh) { r.nextFloat() }
            val px = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val fx = x / cell; val fy = y / cell
                val v = .7f * lerp(g, gw, fx, fy) + .3f * lerp(fine, gw, (fx * 2.7f) % (gw - 2), (fy * 2.7f) % (gh - 2))
                val a = (1f - depth * (1f - smooth(v))).coerceIn(0f, 1f)
                px[y * bw + x] = (a * 255).toInt() shl 24
            }
            val m = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawBitmap(m, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        }

        /** Brightens or darkens what's drawn, to sit in the photo's light. */
        fun tint(k: Float) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.eraseColor(0)
            Canvas(bmp).drawBitmap(copy, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(k, k, k, 1f) }) })
        }

        /** Melts blurred shapes into one organic outline (the metaball trick). */
        fun melt(sharp: Float, cut: Float) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.eraseColor(0)
            Canvas(bmp).drawBitmap(copy, 0f, 0f, Paint().apply {
                colorFilter = ColorMatrixColorFilter(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, sharp, -cut * 255f * sharp))
            })
        }

        private fun smooth(v: Float) = v * v * (3 - 2 * v)
        private fun lerp(g: FloatArray, gw: Int, fx: Float, fy: Float): Float {
            val x0 = fx.toInt(); val y0 = fy.toInt(); val tx = smooth(fx - x0); val ty = smooth(fy - y0)
            fun at(x: Int, y: Int) = g[(y.coerceAtMost(g.size / gw - 1)) * gw + x.coerceAtMost(gw - 1)]
            val a = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
            val b = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
            return a + (b - a) * ty
        }
    }

    private class Ctx(val w: Int, val h: Int, val cx: Float, val cy: Float, val r: Random, val lum: Float, val hand: Typeface?) {
        /** One unit: about a third of a percent of the frame, whatever its shape. */
        val u = sqrt(w.toFloat() * h) / 300f
        /** Things further up the floor are further away, and smaller. */
        fun depth(y: Float) = (.5f + .75f * (y / h)).coerceIn(.45f, 1.3f)
        /** How bright a solid object should be in this light. */
        val light = (.35f + .9f * lum).coerceIn(.35f, 1.1f)

        fun paint(color: Int, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }

        fun stroke(color: Int, width: Float, blur: Float = 0f) = paint(color, blur).apply {
            style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

        // ---------- on the floor ----------

        /** A shoe sole, toe up, about 31 units long. */
        fun sole(left: Boolean) = Path().apply {
            moveTo(0f, -15f)
            cubicTo(6f, -15f, 6.6f, -8f, 6f, -3f); cubicTo(5.5f, 1f, 3.5f, 3f, 3.5f, 6f); cubicTo(3.5f, 9f, 4.5f, 10f, 4.5f, 12f)
            cubicTo(4.5f, 15f, 2f, 16f, 0f, 16f); cubicTo(-2f, 16f, -4.5f, 15f, -4.5f, 12f); cubicTo(-4.5f, 10f, -3.5f, 9f, -3.5f, 6f)
            cubicTo(-3.5f, 3f, -5.6f, 1f, -5.2f, -3f); cubicTo(-5.6f, -9f, -5f, -15f, 0f, -15f); close()
            if (left) transform(Matrix().apply { setScale(-1f, 1f) })
        }

        fun steps(wet: Boolean): List<Layer> {
            val n = 5
            val heading = (if (r.nextBoolean()) 0f else 180f) + (r.nextFloat() - .5f) * 40f
            val rad = heading * PI.toFloat() / 180f
            val dx = cos(rad)
            // A step is about two and a half foot-lengths; the sole drawn is 31 units long at scale .8.
            val stride = 52f * u
            val dark = Pad(w * 1.1f, 110f * u, cx, cy)
            val shine = if (wet) Pad(w * 1.1f, 110f * u, cx, cy) else null
            for (i in 0 until n) {
                val s = (i - (n - 1) / 2f) * stride
                val left = i % 2 == 0
                val side = if (left) -6f * u else 6f * u
                // Along the walk and across it on the floor, then flattened by the view.
                val x = s * dx - side * sin(rad)
                val y = (s * sin(rad) + side * dx) * .42f
                val d = depth(cy + y)
                val fade = .45f + .55f * i / (n - 1)
                val sole = sole(left)
                for (pad in listOfNotNull(dark, shine)) {
                    pad.c.save()
                    pad.c.translate(x, y); pad.c.scale(.8f * u * d, .8f * u * d * .42f); pad.c.rotate(heading + 90f)
                    if (pad === dark) {
                        pad.c.drawPath(sole, paint(if (wet) 0xFF1E2A33.toInt() else 0xFF20160E.toInt(), .5f).apply { alpha = ((if (wet) 150 else 170) * fade).toInt() })
                        if (!wet) {
                            // Tread: a little sole pattern, lifted out of the dust.
                            pad.c.save(); pad.c.clipPath(sole)
                            var ty = -15f
                            while (ty < 16f) { pad.c.drawRect(-7f, ty, 7f, ty + .7f, paint(0).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); alpha = 150 }); ty += 2.2f }
                            pad.c.restore()
                        }
                    } else {
                        pad.c.translate(-.5f, -.8f)
                        pad.c.drawPath(sole, stroke(0xFFFFFFFF.toInt(), .9f, .6f).apply { alpha = (70 * fade).toInt() })
                    }
                    pad.c.restore()
                }
            }
            dark.noise(4f * u, .55f, r)
            val out = mutableListOf(dark.layer(Blend.NORMAL))
            shine?.let { it.noise(3f * u, .6f, r); out += it.layer(Blend.SCREEN) }
            return out
        }

        fun paws(): List<Layer> {
            val pad = Pad(w * .8f, 60f * u, cx, cy)
            val heading = if (r.nextBoolean()) 0f else 180f
            val main = Path().apply {
                moveTo(-4f, 1f); cubicTo(-4.5f, -1.5f, -2f, -2.6f, 0f, -2f); cubicTo(2f, -2.6f, 4.5f, -1.5f, 4f, 1f)
                cubicTo(3.8f, 3.4f, 2f, 3.6f, 1.2f, 3f); cubicTo(.6f, 3.6f, -.6f, 3.6f, -1.2f, 3f); cubicTo(-2f, 3.6f, -3.8f, 3.4f, -4f, 1f); close()
                for ((x, y) in listOf(-3.7f to -4.4f, -1.25f to -6.1f, 1.25f to -6.1f, 3.7f to -4.4f)) addOval(RectF(x - 1.25f, y - 1.6f, x + 1.25f, y + 1.6f), Path.Direction.CW)
            }
            for (i in 0 until 8) {
                val s = (i - 3.5f) * 15f * u
                val x = if (heading == 0f) s else -s
                val y = s * .06f + (if (i % 2 == 0) -1.2f else 1.2f) * u
                val d = depth(cy + y)
                pad.c.save(); pad.c.translate(x, y); pad.c.scale(u * d, u * d * .45f); pad.c.rotate(heading + 90f)
                pad.c.drawPath(main, paint(0xFF1C140D.toInt(), .3f).apply { alpha = (120 + 15 * i).coerceAtMost(220) })
                pad.c.restore()
            }
            pad.noise(3f * u, .6f, r)
            return listOf(pad.layer(Blend.NORMAL))
        }

        fun crumbs(): List<Layer> {
            val pad = Pad(110f * u, 44f * u, cx, cy)
            val d = depth(cy)
            val colors = intArrayOf(0xFF9A6A3A.toInt(), 0xFFB98A54.toInt(), 0xFFD2AA74.toInt(), 0xFF7A5230.toInt())
            repeat(34) { i ->
                val an = PI + i / 33.0 * PI + (r.nextFloat() - .5f) * .12f
                val rr = (30f + r.nextFloat() * 5f) * u * d
                val x = cos(an).toFloat() * rr; val y = 3f * u + sin(an).toFloat() * rr * .4f
                val sz = (1.1f + r.nextFloat() * 1.6f) * u * d
                val crumb = Path().apply {
                    val k = 5 + r.nextInt(3)
                    for (j in 0 until k) {
                        val a = j * 2 * PI / k; val rad = sz * (.6f + r.nextFloat() * .6f)
                        val px = x + cos(a).toFloat() * rad; val py = y + sin(a).toFloat() * rad * .8f
                        if (j == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                pad.c.save(); pad.c.translate(.4f * u, .5f * u); pad.c.drawPath(crumb, paint(0x99000000.toInt(), .6f * u)); pad.c.restore()
                pad.c.drawPath(crumb, paint(colors[r.nextInt(colors.size)]))
            }
            pad.tint(light)
            return listOf(pad.layer(Blend.NORMAL))
        }

        fun thread(): List<Layer> {
            val pad = Pad(min(w * .9f, 190f * u), 70f * u, cx, cy)
            val d = depth(cy)
            val path = Path()
            var x = -70f * u * d; var y = (r.nextFloat() - .5f) * 10f * u
            path.moveTo(x, y)
            repeat(6) {
                val nx = -70f * u * d + r.nextFloat() * 140f * u * d; val ny = (r.nextFloat() - .5f) * 24f * u * d
                path.cubicTo(x + (r.nextFloat() - .5f) * 50f * u, y + (r.nextFloat() - .5f) * 26f * u * d, nx + (r.nextFloat() - .5f) * 50f * u, ny + (r.nextFloat() - .5f) * 26f * u * d, nx, ny)
                x = nx; y = ny
            }
            path.transform(Matrix().apply { setScale(1f, .5f) })
            pad.c.save(); pad.c.translate(.7f * u, 1.2f * u); pad.c.drawPath(path, stroke(0x88000000.toInt(), 1.6f * u, 1.1f * u)); pad.c.restore()
            pad.c.drawPath(path, stroke(0xFF7A3A30.toInt(), .9f * u))
            pad.c.drawPath(path, stroke(0xFFA8605A.toInt(), .3f * u).apply { pathEffect = DashPathEffect(floatArrayOf(1.1f * u, 1.3f * u), 0f); alpha = 170 })
            // Fuzz: wool hairs standing off the yarn.
            val pm = PathMeasure(path, false)
            val pos = FloatArray(2)
            val hair = stroke(0xFF9A5248.toInt(), .14f * u).apply { alpha = 90 }
            do {
                val len = pm.length
                var s = 0f
                while (s < len) {
                    pm.getPosTan(s, pos, null)
                    val a = r.nextFloat() * 2 * PI.toFloat(); val l = (.5f + r.nextFloat()) * u
                    pad.c.drawLine(pos[0], pos[1], pos[0] + cos(a) * l, pos[1] + sin(a) * l, hair)
                    s += .9f * u
                }
            } while (pm.nextContour())
            pad.tint(light * 1.1f)
            return listOf(pad.layer(Blend.NORMAL))
        }

        fun letter(): List<Layer> {
            val pad = Pad(70f * u, 50f * u, cx, cy)
            val d = depth(cy)
            pad.c.save(); pad.c.scale(d, d * .55f); pad.c.rotate(-14f)
            pad.c.drawRoundRect(RectF(-19f * u, -11f * u, 21f * u, 15f * u), 2f * u, 2f * u, paint(0x99000000.toInt(), 2.5f * u))
            pad.c.drawRect(-20f * u, -13f * u, 20f * u, 13f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(-20f * u, -13f * u, 20f * u, 13f * u, 0xFFE6DAC0.toInt(), 0xFFC9B892.toInt(), Shader.TileMode.CLAMP)
            })
            val flap = Path().apply { moveTo(-20f * u, -13f * u); lineTo(0f, 2f * u); lineTo(20f * u, -13f * u) }
            pad.c.drawPath(flap, stroke(0x55806A48, .5f * u))
            pad.c.drawLine(-20f * u, 13f * u, -4f * u, 0f, stroke(0x33806A48, .4f * u)); pad.c.drawLine(20f * u, 13f * u, 4f * u, 0f, stroke(0x33806A48, .4f * u))
            pad.c.drawRect(11f * u, -10f * u, 17f * u, -3f * u, paint(0xFFA4473A.toInt()))
            pad.c.drawRect(11.8f * u, -9.2f * u, 16.2f * u, -3.8f * u, stroke(0xFFE6DAC0.toInt(), .4f * u))
            pad.c.drawCircle(9.5f * u, -5f * u, 4f * u, stroke(0x66303030, .35f * u))
            pad.c.restore()
            pad.noise(4f * u, .18f, r)
            pad.tint(light)
            return listOf(pad.layer(Blend.NORMAL))
        }

        fun chair(): List<Layer> {
            val d = depth(cy)
            val k = u * d * 1.15f
            val shadow = Pad(70f * k, 20f * k, cx, cy + 2f * k)
            shadow.c.drawOval(RectF(-24f * k, -4f * k, 26f * k, 5f * k), paint(0xAA000000.toInt(), 4f * k))
            val pad = Pad(60f * k, 80f * k, cx, cy - 34f * k)
            pad.c.translate(0f, 34f * k)
            val wood = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(-18f * k, -60f * k, 18f * k, 0f, 0xFF5A3F2A.toInt(), 0xFF24170E.toInt(), Shader.TileMode.CLAMP)
                maskFilter = BlurMaskFilter(.45f * k, BlurMaskFilter.Blur.NORMAL)
            }
            fun leg(x0: Float, y0: Float, x1: Float, y1: Float, wd: Float) = pad.c.drawPath(Path().apply {
                moveTo((x0 - wd) * k, y0 * k); lineTo((x0 + wd) * k, y0 * k); lineTo((x1 + wd * .7f) * k, y1 * k); lineTo((x1 - wd * .7f) * k, y1 * k); close()
            }, wood)
            leg(-10f, -31f, -10.5f, -6f, .9f); leg(16f, -31f, 16.5f, -6f, .9f)
            // The back: two posts, a curved top rail, three spindles.
            leg(-11f, -32f, -12f, -70f, 1.1f); leg(17f, -32f, 16.5f, -70f, 1.1f)
            pad.c.drawPath(Path().apply {
                moveTo(-13f * k, -66f * k); quadTo(2f * k, -71f * k, 17.5f * k, -66f * k); lineTo(17.5f * k, -71f * k); quadTo(2f * k, -76f * k, -13f * k, -71f * k); close()
            }, wood)
            for (sx in listOf(-5f, 2.5f, 10f)) leg(sx, -34f, sx, -66f, .55f)
            pad.c.drawPath(Path().apply {
                moveTo(-15f * k, -26f * k); lineTo(13f * k, -26f * k); lineTo(18f * k, -32f * k); lineTo(-11f * k, -32f * k); close()
            }, wood)
            pad.c.drawRect(-15f * k, -26f * k, 13f * k, -23.5f * k, Paint(wood).apply { shader = null; color = 0xFF1C120B.toInt() })
            leg(-14f, -24f, -14.5f, 0f, 1f); leg(12f, -24f, 12.5f, 0f, 1f)
            pad.tint(light)
            return listOf(shadow.layer(Blend.NORMAL), pad.layer(Blend.NORMAL))
        }

        fun cup(): List<Layer> {
            val d = depth(cy)
            val k = u * d
            val shadow = Pad(50f * k, 16f * k, cx, cy + 8f * k)
            shadow.c.drawOval(RectF(-19f * k, -3f * k, 19f * k, 4f * k), paint(0xAA000000.toInt(), 2.5f * k))
            val pad = Pad(50f * k, 36f * k, cx, cy)
            val china = { x0: Float, x1: Float -> Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0xFFF4EEE2.toInt(), 0xFFE2D8C6.toInt(), 0xFF9C9180.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP) } }
            pad.c.drawOval(RectF(-17f * k, 3f * k, 17f * k, 10f * k), china(-17f * k, 17f * k))
            pad.c.drawOval(RectF(-12f * k, 4.5f * k, 12f * k, 8.5f * k), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 })
            val body = Path().apply {
                moveTo(-9f * k, -7f * k); cubicTo(-9f * k, 1f * k, -7f * k, 6f * k, -4.5f * k, 6.5f * k); lineTo(4.5f * k, 6.5f * k)
                cubicTo(7f * k, 6f * k, 9f * k, 1f * k, 9f * k, -7f * k); close()
            }
            pad.c.drawPath(body, china(-9f * k, 9f * k))
            pad.c.drawArc(RectF(6.5f * k, -5f * k, 13.5f * k, 3f * k), -80f, 170f, false, stroke(0xFFB8AD99.toInt(), 1.6f * k))
            pad.c.drawOval(RectF(-9f * k, -9f * k, 9f * k, -5f * k), paint(0xFFEDE6D8.toInt()))
            pad.c.drawOval(RectF(-8f * k, -8.5f * k, 8f * k, -5.5f * k), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(-2f * k, -7.5f * k, 8f * k, 0xFF7A4A22.toInt(), 0xFF3A200E.toInt(), Shader.TileMode.CLAMP)
            })
            pad.c.drawOval(RectF(-4.5f * k, -8f * k, -.5f * k, -7f * k), paint(0x40FFFFFF, .4f * k))
            pad.tint(light)
            return listOf(shadow.layer(Blend.NORMAL), pad.layer(Blend.NORMAL))
        }

        // ---------- in the dark ----------

        fun eyes(): List<Layer> {
            val pad = Pad(40f * u, 22f * u, cx, cy)
            val warm = r.nextBoolean()
            val col = if (warm) 0xFFF2CF6A.toInt() else 0xFFCFE7B0.toInt()
            pad.c.rotate((r.nextFloat() - .5f) * 10f)
            for (sx in listOf(-6f, 6f)) {
                val e = RectF((sx - 2.6f) * u, -1.2f * u, (sx + 2.6f) * u, 1.2f * u)
                pad.c.drawOval(RectF(e.left - 3f * u, e.top - 3f * u, e.right + 3f * u, e.bottom + 3f * u), paint(col, 3.5f * u).apply { alpha = 70 })
                pad.c.drawOval(e, paint(col, .3f * u))
                pad.c.drawOval(RectF((sx - .45f) * u, -1.1f * u, (sx + .45f) * u, 1.1f * u), paint(0xFF120A04.toInt(), .15f * u))
                pad.c.drawCircle((sx - .9f) * u, -.4f * u, .35f * u, paint(0xFFFFFFFF.toInt()))
            }
            return listOf(pad.layer(Blend.SCREEN))
        }

        /** A door left ajar into somewhere darker: a tall, soft pool of shadow. */
        fun gap(): List<Layer> {
            val gw = 60f * u; val gh = h * .55f
            val pad = Pad(gw, gh, cx, cy)
            pad.c.drawOval(RectF(-gw * .32f, -gh * .5f, gw * .32f, gh * .5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD0000000.toInt(); maskFilter = BlurMaskFilter(gw * .16f, BlurMaskFilter.Blur.NORMAL)
            })
            pad.c.drawRect(-gw / 2, -gh / 2, gw / 2, gh / 2, Paint().apply {
                shader = LinearGradient(0f, -gh / 2, 0f, gh / 2, intArrayOf(0xFF000000.toInt(), 0x00000000, 0x00000000, 0xFF000000.toInt()), floatArrayOf(0f, .2f, .8f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            })
            pad.noise(9f * u, .4f, r)
            return listOf(pad.layer(Blend.NORMAL))
        }

        fun cat(): List<Layer> {
            val hc = h * .16f
            val k = hc / 40f
            val pad = Pad(50f * k, 48f * k, cx, cy - 18f * k)
            pad.c.translate(0f, 20f * k)
            val body = Path().apply {
                moveTo(-9f * k, 0f)
                cubicTo(-13f * k, -8f * k, -12f * k, -18f * k, -7f * k, -24f * k)
                cubicTo(-9f * k, -27f * k, -9.5f * k, -31f * k, -8f * k, -33f * k)
                lineTo(-9f * k, -40f * k); lineTo(-4f * k, -35.5f * k)
                quadTo(0f, -36.5f * k, 4f * k, -35.5f * k)
                lineTo(9f * k, -40f * k); lineTo(8f * k, -33f * k)
                cubicTo(9.5f * k, -31f * k, 9f * k, -27f * k, 7f * k, -24f * k)
                cubicTo(12f * k, -18f * k, 13f * k, -8f * k, 9f * k, 0f)
                close()
                moveTo(8f * k, -1f * k); cubicTo(18f * k, -1f * k, 21f * k, -6f * k, 18f * k, -12f * k)
                lineTo(16.5f * k, -11f * k); cubicTo(18.5f * k, -6f * k, 15f * k, -3.5f * k, 8f * k, -3.5f * k); close()
            }
            pad.c.drawPath(body, paint(0x66000000, 4f * k))
            pad.c.drawPath(body, paint(0xEE050302.toInt(), .7f * k))
            val eyes = Pad(50f * k, 48f * k, cx, cy - 18f * k)
            eyes.c.translate(0f, 20f * k)
            for (sx in listOf(-3.2f, 3.2f)) eyes.c.drawOval(RectF((sx - 1.2f) * k, -31f * k, (sx + 1.2f) * k, -29.8f * k), paint(0xFFC9E09A.toInt(), .4f * k))
            return listOf(pad.layer(Blend.NORMAL), eyes.layer(Blend.SCREEN))
        }

        // ---------- on glass and walls ----------

        fun hand(onGlass: Boolean): List<Layer> {
            val s = u * 1.05f
            val pad = Pad(70f * s, 90f * s, cx, cy)
            pad.c.rotate((r.nextFloat() - .5f) * 22f)
            val blob = paint(0xFFFFFFFF.toInt(), 2.6f * s)
            pad.c.drawOval(RectF(-12f * s, -4f * s, 12f * s, 24f * s), blob)
            // Fingers: index, middle, ring, little — long and thin — and the thumb.
            val fingers = listOf(
                floatArrayOf(-7.5f, -2f, -10.5f, -32f, 5.2f), floatArrayOf(-2f, -3f, -2.8f, -37f, 5.6f), floatArrayOf(3.5f, -2f, 5.2f, -33f, 5.2f),
                floatArrayOf(8.5f, 1f, 12.5f, -22f, 4.4f), floatArrayOf(-11f, 10f, -22f, -4f, 5.8f),
            )
            for (f in fingers) pad.c.drawLine(f[0] * s, f[1] * s, f[2] * s, f[3] * s, stroke(0xFFFFFFFF.toInt(), f[4] * s, 2.4f * s))
            pad.melt(7f, .42f)
            // Whorls on the fingertips and creases in the palm, where the skin didn't touch.
            val cut = stroke(0, .3f * s).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); alpha = 80 }
            for (f in fingers) for (ring in 1..5) {
                val rr = ring * .75f * s
                pad.c.drawOval(RectF(f[2] * s - rr, (f[3] + 3.5f) * s - rr * 1.25f, f[2] * s + rr, (f[3] + 3.5f) * s + rr * 1.25f), cut)
            }
            val crease = stroke(0, .6f * s).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); alpha = 100 }
            pad.c.drawPath(Path().apply { moveTo(-11f * s, 6f * s); quadTo(-2f * s, 3f * s, 10f * s, 8f * s) }, crease)
            pad.c.drawPath(Path().apply { moveTo(-10f * s, 12f * s); quadTo(-1f * s, 9f * s, 7f * s, 14f * s) }, crease)
            pad.c.drawPath(Path().apply { moveTo(-8f * s, 2f * s); quadTo(-9f * s, 14f * s, -3f * s, 22f * s) }, crease)
            pad.noise(3.2f * s, .75f, r)
            if (!onGlass) return listOf(recolor(pad, 0xFF3A2C20.toInt(), .3f).layer(Blend.NORMAL))
            // On glass: skin grease dulls the shine a little and catches the light a little.
            val sheen = Pad(pad.bw.toFloat(), pad.bh.toFloat(), cx, cy)
            sheen.c.translate(-sheen.bw / 2f, -sheen.bh / 2f)
            sheen.c.drawBitmap(pad.bmp, -.6f * s, -.6f * s, null)
            recolor(sheen, 0xFFF4EEE4.toInt(), .14f)
            return listOf(recolor(pad, 0xFF2A2420.toInt(), .24f).layer(Blend.NORMAL), sheen.layer(Blend.SCREEN))
        }

        /** Repaints a pad's shape in one colour at [alpha], keeping its alpha texture. */
        fun recolor(p: Pad, color: Int, alpha: Float): Pad {
            val copy = p.bmp.copy(Bitmap.Config.ARGB_8888, false)
            p.bmp.eraseColor(0)
            Canvas(p.bmp).drawBitmap(copy, 0f, 0f, Paint().apply {
                val a = alpha
                colorFilter = ColorMatrixColorFilter(floatArrayOf(
                    0f, 0f, 0f, 0f, (color shr 16 and 0xFF).toFloat(), 0f, 0f, 0f, 0f, (color shr 8 and 0xFF).toFloat(),
                    0f, 0f, 0f, 0f, (color and 0xFF).toFloat(), 0f, 0f, 0f, a, 0f,
                ))
            })
            return p
        }

        fun writing(text: String): List<Layer> {
            val size = (if (text.length > 6) 22f else 34f) * u
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = hand ?: Typeface.SERIF; textAlign = Paint.Align.CENTER }
            val tw = tp.measureText(text)
            val pad = Pad(tw + 50f * u, size * 2.6f, cx, cy)
            // Breath on the glass…
            pad.c.drawOval(RectF(-tw / 2 - 22f * u, -size * 1.1f, tw / 2 + 22f * u, size * 1.0f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(0f, 0f, tw / 2 + 22f * u, 0x70E8ECEE, 0x00E8ECEE, Shader.TileMode.CLAMP)
            })
            repeat(260) { pad.c.drawCircle((r.nextFloat() - .5f) * (tw + 40f * u), (r.nextFloat() - .5f) * size * 1.8f, (.15f + r.nextFloat() * .4f) * u, paint(0x50FFFFFF)) }
            pad.noise(5f * u, .5f, r)
            // …wiped away by a finger.
            pad.c.save(); pad.c.rotate(-4f)
            val wipe = Paint(tp).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); style = Paint.Style.FILL_AND_STROKE; strokeWidth = size * .05f }
            pad.c.drawText(text, 0f, size * .35f, wipe)
            // Runs: a few drops crawling down from the letters.
            repeat(3 + r.nextInt(3)) {
                val x = (r.nextFloat() - .5f) * tw * .9f
                val y0 = size * .2f; val len = (6f + r.nextFloat() * 14f) * u
                pad.c.drawLine(x, y0, x + .4f * u, y0 + len, stroke(0xFF000000.toInt(), .8f * u).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                pad.c.drawCircle(x + .4f * u, y0 + len, 1.1f * u, paint(0xFF000000.toInt()).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                pad.c.drawCircle(x + .1f * u, y0 + len - .3f * u, .45f * u, paint(0x90FFFFFF.toInt()))
            }
            pad.c.restore()
            return listOf(pad.layer(Blend.SCREEN))
        }

        fun frost(): List<Layer> {
            val pad = Pad(120f * u, 120f * u, cx, cy)
            pad.c.drawCircle(0f, 0f, 55f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 55f * u, 0x48DCEAF4, 0x00DCEAF4, Shader.TileMode.CLAMP) })
            pad.noise(7f * u, .7f, r)
            val line = stroke(0x70EEF6FF, .22f * u, .12f * u)
            // Fern frost: crooked stems growing from the edge, each throwing off short barbs to one side or both.
            fun fern(x: Float, y: Float, a0: Float, len: Float, depth: Int) {
                var px = x; var py = y; var a = a0
                val steps = (len / (1.6f * u)).toInt().coerceAtLeast(2)
                val path = Path().apply { moveTo(px, py) }
                for (i in 0 until steps) {
                    a += (r.nextFloat() - .5f) * .35f
                    val nx = px + cos(a) * 1.6f * u; val ny = py + sin(a) * 1.6f * u
                    path.lineTo(nx, ny)
                    if (depth > 0 && i % 2 == 1 && i < steps - 1) {
                        val l = len * (1f - i / steps.toFloat()) * (.25f + r.nextFloat() * .2f)
                        if (r.nextFloat() < .85f) fern(nx, ny, a + .9f + r.nextFloat() * .3f, l, depth - 1)
                        if (r.nextFloat() < .6f) fern(nx, ny, a - .9f - r.nextFloat() * .3f, l, depth - 1)
                    }
                    px = nx; py = ny
                }
                pad.c.drawPath(path, line)
            }
            repeat(7) {
                val a = r.nextFloat() * 6.28f
                val rr = (30f + r.nextFloat() * 22f) * u
                fern(cos(a) * rr, sin(a) * rr, a + PI.toFloat() + (r.nextFloat() - .5f) * .8f, (18f + r.nextFloat() * 22f) * u, 2)
            }
            return listOf(pad.layer(Blend.SCREEN))
        }

        fun keys(): List<Layer> {
            val pad = Pad(50f * u, 70f * u, cx, cy)
            val shade = Pad(50f * u, 70f * u, cx + 1.5f * u, cy + 2f * u)
            for ((p, shadow) in listOf(shade to true, pad to false)) {
                val brass = if (shadow) paint(0x99000000.toInt(), 1.4f * u) else Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(-6f * u, -10f * u, 6f * u, 20f * u, 0xFFE0C47A.toInt(), 0xFF7A5F2A.toInt(), Shader.TileMode.CLAMP)
                }
                val ring = Paint(brass).apply { style = Paint.Style.STROKE; strokeWidth = .9f * u }
                if (!shadow) p.c.drawCircle(0f, -26f * u, 1.3f * u, paint(0xFF2A221A.toInt()))
                p.c.drawLine(0f, -26f * u, 0f, -12f * u, if (shadow) Paint(brass).apply { strokeWidth = .5f * u } else stroke(0xFF9A8A70.toInt(), .45f * u))
                p.c.drawCircle(0f, -8f * u, 4f * u, ring)
                for ((ang, len) in listOf(-8f to 20f, 14f to 16f)) {
                    p.c.save(); p.c.translate(0f, -4.5f * u); p.c.rotate(ang)
                    p.c.drawCircle(0f, 2.6f * u, 2.6f * u, Paint(brass).apply { style = Paint.Style.FILL })
                    p.c.drawRect(-.9f * u, 4.5f * u, .9f * u, (4.5f + len) * u, brass)
                    for (tth in listOf(.55f, .75f, .9f)) p.c.drawRect(.9f * u, (4.5f + len * tth) * u, 2.4f * u, (5.6f + len * tth) * u, brass)
                    if (!shadow) p.c.drawCircle(0f, 2.6f * u, .9f * u, paint(0).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
                    p.c.restore()
                }
                p.c.save(); p.c.translate(3.5f * u, -6f * u); p.c.rotate(24f)
                p.c.drawRect(0f, -2.5f * u, 13f * u, 5.5f * u, if (shadow) brass else paint(0xFFD8C9A2.toInt()))
                if (!shadow) for (i in 0..2) p.c.drawLine(2.5f * u, (-1f + i * 2.2f) * u, (8f + r.nextFloat() * 3f) * u, (-1f + i * 2.2f) * u, stroke(0x99403020.toInt(), .35f * u))
                p.c.restore()
            }
            pad.tint(light)
            return listOf(shade.layer(Blend.NORMAL), pad.layer(Blend.NORMAL))
        }

        fun light(): List<Layer> {
            val pad = Pad(80f * u, 80f * u, cx, cy)
            pad.c.drawCircle(0f, 0f, 38f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 38f * u, 0x30FF5A3C, 0x00FF5A3C, Shader.TileMode.CLAMP) })
            pad.c.drawCircle(0f, 0f, 9f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 9f * u, 0xB0FF6A4A.toInt(), 0x00FF6A4A, Shader.TileMode.CLAMP) })
            pad.c.drawCircle(0f, 0f, 1.8f * u, paint(0xFFFFD2C0.toInt(), .5f * u))
            return listOf(pad.layer(Blend.SCREEN))
        }
    }
}
