package com.pxr.cymatic.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibrarySyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val result = runCatching { LibrarySyncManager.sync(applicationContext) }
            result.exceptionOrNull()?.takeUnless { it is SyncCancelledException }?.let { error ->
                SettingsStore.setSyncResult(
                    System.currentTimeMillis(),
                    "Automatic sync failed: ${error.message ?: "unknown error"}",
                )
            }
            val shouldRetry = result.exceptionOrNull()?.let { it !is SyncCancelledException } == true
            jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        LibrarySyncManager.cancel()
        job?.cancel()
        job = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x43594D
        private const val MINIMUM_INTERVAL_MS = 15 * 60 * 1000L

        suspend fun reschedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            scheduler.cancel(JOB_ID)
            val network = SyncNetwork.from(SettingsStore.syncNetworkFlow.first())
            if (network == SyncNetwork.NEVER ||
                SettingsStore.syncDirectoryFlow.first().isBlank() ||
                !SyncCredentialStore.hasPassword(context)
            ) return

            val intervalMs = (SettingsStore.syncIntervalHoursFlow.first() * 60L * 60L * 1000L)
                .coerceAtLeast(MINIMUM_INTERVAL_MS)
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, LibrarySyncJobService::class.java),
            )
                .setRequiredNetworkType(
                    if (network == SyncNetwork.WIFI) JobInfo.NETWORK_TYPE_UNMETERED
                    else JobInfo.NETWORK_TYPE_ANY
                )
                .setPeriodic(intervalMs)
                .setPersisted(true)
                .build()
            scheduler.schedule(info)
        }
    }
}
