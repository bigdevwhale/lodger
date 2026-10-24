package app.lodger.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lodger.container
import app.lodger.domain.Engine
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Label
import app.lodger.ui.components.Page
import app.lodger.ui.components.Sheet
import app.lodger.ui.components.paper
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T

/** The story put together: who the lodger was, and the one thing left undone that you can do for them. */
@Composable
fun TruthPage(h: Home, nav: Nav) {
    val c = LocalContext.current.container
    val l = h.lodger ?: return
    LaunchedEffect(l.id) { if (!h.h.solved && Engine.ready(l, h.h, h.night)) c.solve() }
    var name by rememberSaveable { mutableStateOf(h.h.names[l.id].orEmpty()) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Page(h.w.t("История", "The story"), onClose = nav.close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(Modifier.fillMaxWidth().paper()) {
                Text(l.kind.uppercase(), style = T.Stamp)
                Text(l.name, style = T.Display.copy(color = C.ink), modifier = Modifier.padding(top = 2.dp))
                Text("${l.years} · ${l.role}", style = T.Italic.copy(color = C.inkSoft), modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Text(l.truth, style = T.Body.copy(color = C.ink))
                Column(Modifier.padding(top = 18.dp).fillMaxWidth().border(2.dp, C.ink).background(C.paper2.copy(alpha = .35f)).padding(14.dp)) {
                    Text(h.w.t("НЕЗАКОНЧЕННОЕ ДЕЛО", "UNFINISHED BUSINESS"), style = T.Stamp.copy(color = C.red))
                    Text(l.task.title, style = T.Heading.copy(color = C.ink), modifier = Modifier.padding(top = 4.dp))
                    Text(l.task.text, style = T.Body.copy(color = C.ink), modifier = Modifier.padding(top = 6.dp))
                    if (l.task.askName) {
                        BasicTextField(
                            name, { name = it.take(24) },
                            textStyle = T.Note.copy(color = C.ink), cursorBrush = SolidColor(C.ink), singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().background(C.paper).border(1.dp, C.inkSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                            decorationBox = { inner -> if (name.isEmpty()) Text(h.w.t("Имя для него", "A name for him"), style = T.Note.copy(color = C.inkSoft)); inner() },
                        )
                    }
                }
            }
            Text(
                h.w.t("Сделай это по-настоящему, не в телефоне. Когда сделаешь — вернись сюда.", "Do it for real, not on the phone. When it's done, come back here."),
                style = T.Small, modifier = Modifier.padding(top = 14.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(h.w.t("Позже", "Later"), Modifier.weight(1f), Kind.GHOST, onClick = nav.close)
                Btn(h.w.t("Сделано", "Done"), Modifier.weight(1f), Kind.AMBER, enabled = !l.task.askName || name.isNotBlank()) { confirm = true }
            }
        }
    }
    Sheet(visible = confirm, onClose = { confirm = false }) {
        Label(h.w.t("Правда сделано?", "Really done?"))
        Text(
            h.w.t("Жилец уйдёт только тогда, когда дело и правда сделано. Он узнает.", "The lodger only leaves once it's truly done. They'll know."),
            style = T.Body, modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(h.w.t("Ещё нет", "Not yet"), Modifier.weight(1f), Kind.GHOST) { confirm = false }
            Btn(h.w.t("Да, сделано", "Yes, it's done"), Modifier.weight(1f), Kind.AMBER) {
                confirm = false
                val n = name.takeIf { l.task.askName }
                c.sfx.creak()
                c.write { leave(l, n) }
                nav.farewell(l, n)
            }
        }
    }
}
