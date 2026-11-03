package app.enterlib.data

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val results = EnterLibGraph.get(applicationContext).sync.syncAll()
        return if (results.isNotEmpty() && results.all { !it.success }) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private const val PERIODIC = "enterlib_periodic_sync"
    private const val MANUAL = "enterlib_manual_sync"
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** hours <= 0 means manual only. Android may delay periodic work (Doze, battery); updates are not instant. */
    fun apply(ctx: Context, hours: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (hours <= 0) { wm.cancelUniqueWork(PERIODIC); return }
        val req = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS).setConstraints(net).build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun syncNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).build())
    }
}
