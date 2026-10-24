package app.lodger.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lodger.MainActivity
import app.lodger.R
import app.lodger.container
import app.lodger.domain.Engine
import app.lodger.localized
import app.lodger.ru
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The lodger's notes, as notifications: one a night at most, and only in the evening — a note written
 * at three in the morning arrives at the chosen evening hour instead.
 */
object Knock {
    private const val CHANNEL = "notes"
    private const val WORK = "knock"

    fun channel(c: Context) {
        val ru = c.localized().ru
        val ch = NotificationChannel(CHANNEL, if (ru) "Записки жильца" else "The lodger's notes", NotificationManager.IMPORTANCE_DEFAULT)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Schedules the next note. Never throws: a missing WorkManager (as in unit tests) just means no note. */
    suspend fun plan(c: Context) {
        runCatching { schedule(c) }
    }

    private suspend fun schedule(c: Context) {
        val app = c.applicationContext
        val k = app.container
        val wm = WorkManager.getInstance(app)
        val now = System.currentTimeMillis()
        val next = Engine.nextNote(k.store.now(), k.lodgers(app.localized().ru), now, k.store.settingsNow().evening, ZoneId.systemDefault())
        if (next == null) { wm.cancelUniqueWork(WORK); return }
        val (note, at) = next
        wm.enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KnockWorker>().setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("night" to note.night)).build(),
        )
    }

    suspend fun ring(c: Context, night: Int) {
        val k = c.container
        val h = k.store.change { if (it.rung < night) it.copy(rung = night) else it }
        val lc = c.localized()
        val l = Engine.ORDER.getOrNull(h.index)?.let { k.lodgers(lc.ru)[it] } ?: return
        val note = l.notes.firstOrNull { it.night == night } ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_NOTES, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_door)
            .setContentTitle(if (lc.ru) "Записка · ${note.time}" else "A note · ${note.time}")
            .setContentText(note.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(1, n)
    }
}

class KnockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val night = inputData.getInt("night", 0)
        if (night > 0) Knock.ring(applicationContext, night)
        Knock.plan(applicationContext)
        return Result.success()
    }
}
