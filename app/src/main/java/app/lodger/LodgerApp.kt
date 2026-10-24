package app.lodger

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.lodger.data.DemoSeed
import app.lodger.data.Photos
import app.lodger.data.Store
import app.lodger.domain.Engine
import app.lodger.domain.House
import app.lodger.domain.Lodger
import app.lodger.domain.Lodgers
import app.lodger.domain.Room
import app.lodger.notify.Knock
import app.lodger.sound.Sfx
import app.lodger.sound.Static
import app.lodger.sound.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val store = Store(app)
    val sfx = Sfx(app)
    val static = Static()
    val voice = Voice(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stories = HashMap<Boolean, Lodgers>()

    /** The stories in one language, read from assets once. */
    fun lodgers(ru: Boolean): Lodgers = synchronized(stories) {
        stories.getOrPut(ru) { Lodgers.parse(app.assets.open(Lodgers.file(ru)).bufferedReader().use { it.readText() }) }
    }

    fun file(rel: String) = File(app.filesDir, rel)
    fun newFile(room: Room) = "rooms/${room.key}-${System.currentTimeMillis()}.jpg".also { file(it).parentFile?.mkdirs() }

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    private suspend fun change(f: (House) -> House) {
        store.change(f)
        Knock.plan(app)
    }

    /** A new photo of a room: read it for spots and keep it in place of the old one. */
    suspend fun photo(room: Room, rel: String) {
        val p = Photos.read(file(rel), rel) ?: return
        var old: String? = null
        change { h ->
            old = h.photos[room]?.file
            h.copy(photos = h.photos + (room to p), absent = h.absent - room)
        }
        old?.takeIf { it != rel }?.let { file(it).delete() }
    }

    /** The rooms are photographed: the first lodger moves in. */
    suspend fun moveIn() = change { Engine.settle(it, System.currentTimeMillis()) }

    suspend fun absent(room: Room, on: Boolean) = change { h ->
        if (on) {
            h.photos[room]?.let { file(it.file).delete() }
            h.copy(absent = h.absent + room, photos = h.photos - room)
        } else h.copy(absent = h.absent - room)
    }

    suspend fun word(k: Int) = change { it.copy(words = it.words + k) }
    suspend fun solve() = change { it.copy(solved = true) }
    suspend fun seen(key: String) { if (key !in store.now().seen) store.change { it.copy(seen = it.seen + key) } }
    suspend fun unseen(prefix: String) = store.change { h -> h.copy(seen = h.seen.filterNot { it.startsWith(prefix) }.toSet()) }
    suspend fun leave(l: Lodger, name: String?) = change { Engine.leave(it, l, System.currentTimeMillis(), ZoneId.systemDefault(), name) }

    /** Lets the next lodger in now instead of after the empty nights (from the settings, for the impatient). */
    suspend fun callNext() = change { h -> if (h.arrived > System.currentTimeMillis()) h.copy(arrived = System.currentTimeMillis(), rung = 0) else h }
}

class LodgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Knock.channel(this)
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(container)
            container.sfx.load()
            Knock.plan(this@LodgerApp)
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as LodgerApp).container

/** Resources in the in-app language, which AppCompat doesn't localise outside activities before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** Is this context in Russian? */
val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
