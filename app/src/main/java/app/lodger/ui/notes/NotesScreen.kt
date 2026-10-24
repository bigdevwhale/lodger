package app.lodger.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.lodger.domain.Engine
import app.lodger.domain.Note
import app.lodger.ui.Home
import app.lodger.ui.components.Label
import app.lodger.ui.components.paper
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T
import kotlin.random.Random

@Composable
fun NotesScreen(h: Home) {
    val l = h.lodger ?: return
    val notes = Engine.notes(l, h.night).sortedByDescending { it.night }
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(h.w.t("Записок пока нет.", "No notes yet."), style = T.Italic.copy(color = C.dim)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 16.dp)) {
        item { Label(h.w.t("Записки", "Notes"), Modifier.padding(bottom = 14.dp)) }
        items(notes, key = { it.night }) { n -> Scrap(h, n, fresh = n.night == h.night) }
    }
}

/** A note on a scrap of paper, slightly askew, held by a strip of tape. */
@Composable
fun Scrap(h: Home, n: Note, fresh: Boolean) {
    val tilt = Random(n.night * 17 + n.text.length).nextFloat() * 4f - 2f
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(
            Modifier.fillMaxWidth().rotate(tilt).shadow(6.dp)
                .then(if (fresh) Modifier.border(2.dp, C.red) else Modifier).paper(),
        ) {
            Text(h.w.t("НОЧЬ ${n.night} · ${n.time}", "NIGHT ${n.night} · ${n.time}"), style = T.Stamp)
            Text(n.text, style = T.Note, modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = 2.dp).size(52.dp, 14.dp).rotate(-3f).background(Color(0xAAC9B88E)))
    }
}
