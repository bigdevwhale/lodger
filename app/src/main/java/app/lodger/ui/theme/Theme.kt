package app.lodger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lodger.R

/** An old flat at night: dark wallpaper, lamplight, yellowed paper, ink, and the red of a thread on a cork board. */
@Immutable
object C {
    val night = Color(0xFF0C0A08)
    val shell = Color(0xFF16120F)
    val panel = Color(0xFF1D1814)
    val line = Color(0xFF2E261E)
    val nav = Color(0xFF110E0B)
    val paper = Color(0xFFE8DCC2)
    val paper2 = Color(0xFFD9C9A6)
    val ink = Color(0xFF2B2119)
    val inkSoft = Color(0xFF7A6A52)
    val text = Color(0xFFE3D6BD)
    val dim = Color(0xFF8B7A62)
    val faint = Color(0xFF4A3F33)
    val amber = Color(0xFFD8A856)
    val amberHi = Color(0xFFF3E2B5)
    val red = Color(0xFFA33B2B)
    val thread = Color(0xFFB02A1C)
    val cork = Color(0xFF9A7448)
    val corkEdge = Color(0xFF4A3324)
    val wood = Color(0xFF3B2A1D)
    val woodDeep = Color(0xFF2A1E15)
    val scrim = Color(0xCC000000)
}

val Serif = FontFamily(
    Font(R.font.serif_regular, FontWeight.Normal),
    Font(R.font.serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.serif_bold, FontWeight.Bold),
)
/** Caveat: the lodger's handwriting. */
val Hand = FontFamily(Font(R.font.hand, FontWeight.Normal))
val Mono = FontFamily(Font(R.font.mono, FontWeight.Normal))

object T {
    val Display = TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 36.sp, color = C.text)
    val Title = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp, color = C.text)
    val Heading = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = C.text)
    val Body = TextStyle(fontFamily = Serif, fontSize = 16.sp, lineHeight = 23.sp, color = C.text)
    val Italic = Body.copy(fontStyle = FontStyle.Italic)
    val Small = TextStyle(fontFamily = Serif, fontSize = 13.sp, lineHeight = 18.sp, color = C.dim)
    val Label = TextStyle(fontFamily = Serif, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = .18.em, color = C.dim)
    val Brand = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = .32.em, color = C.paper)
    val Note = TextStyle(fontFamily = Hand, fontSize = 26.sp, lineHeight = 28.sp, color = C.ink)
    val Stamp = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, color = C.inkSoft)
    val Dial = TextStyle(fontFamily = Mono, fontSize = 28.sp, lineHeight = 32.sp, color = C.amber)
    val Button = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = .04.em)
}

@Composable
fun LodgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.amber, onPrimary = C.night, background = C.shell, surface = C.panel, onSurface = C.text,
            onBackground = C.text, outline = C.dim, error = C.red,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}
