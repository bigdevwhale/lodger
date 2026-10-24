package app.lodger.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.lodger.container
import app.lodger.data.Photos
import app.lodger.domain.Room

/** Shoots with the phone's camera app or picks from the gallery; [onDone] gets the room and the saved file's relative name. */
class Camera(val shoot: (Room) -> Unit, val pick: (Room) -> Unit)

@Composable
fun rememberCamera(onDone: (Room, String) -> Unit): Camera {
    val context = LocalContext.current
    val c = context.container
    // Saved across the activity being killed while the camera app is open, which some phones do.
    val rel: MutableState<String?> = rememberSaveable { mutableStateOf(null) }
    val room: MutableState<String?> = rememberSaveable { mutableStateOf(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = rel.value
        val r = room.value?.let(Room::of)
        if (ok && f != null && r != null && c.file(f).length() > 0) onDone(r, f) else f?.let { c.file(it).delete() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val r = room.value?.let(Room::of)
        if (uri == null || r == null) return@rememberLauncherForActivityResult
        val f = c.newFile(r)
        if (Photos.copy(context, uri, c.file(f))) onDone(r, f)
    }
    return remember {
        Camera(
            shoot = { r ->
                val f = c.newFile(r)
                rel.value = f; room.value = r.key
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", c.file(f))
                runCatching { take.launch(uri) }
            },
            pick = { r ->
                room.value = r.key
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
