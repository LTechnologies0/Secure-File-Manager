package ltechnologies.onionphone.securefilemanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.extensions.getNotificationManager
import ltechnologies.onionphone.securefilemanager.extensions.showErrorToast
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.APP_CHANNEL_ID
import ltechnologies.onionphone.securefilemanager.helpers.EncryptionAction
import ltechnologies.onionphone.securefilemanager.helpers.HideAction
import ltechnologies.onionphone.securefilemanager.helpers.getNotificationId
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.transfer.TransferEngine
import ltechnologies.onionphone.securefilemanager.transfer.TransferResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedHashMap

class TransferService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var notification: NotificationCompat.Builder
    private val notificationId = getNotificationId()
    private var running = false

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO.limitedParallelism(2))
    private var transferJob: Job? = null
    @Volatile
    private var cancelled = false

    private var lastCopiedBytes = 0L
    private var lastTotalBytes = 0L
    private var lastFileName = ""

    override fun onCreate() {
        super.onCreate()
        notificationManager = getNotificationManager()
        notificationManager.createNotificationChannel(getNotificationChannel())
        notification = getNotificationBuilder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(notificationId, notification.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRANSFER -> {
                if (setRunning()) {
                    startTransfer(intent)
                }
            }
            ACTION_STOP -> stopTransfer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        notificationManager.cancel(notificationId)
        super.onDestroy()
    }

    private fun setRunning(): Boolean {
        if (running) {
            toast(R.string.copy_move_failed)
            return false
        }
        running = true
        return true
    }

    private fun startTransfer(intent: Intent) {
        val paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: return stopSelf()
        val names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: return stopSelf()
        val isDirectory = intent.getBooleanArrayExtra(EXTRA_IS_DIRECTORY) ?: return stopSelf()
        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: return stopSelf()
        val copyOnly = intent.getBooleanExtra(EXTRA_COPY_ONLY, true)
        val copyMediaOnly = intent.getBooleanExtra(EXTRA_COPY_MEDIA_ONLY, false)
        val encryptionAction =
            EncryptionAction.entries[intent.getIntExtra(EXTRA_ENCRYPTION_ACTION, 0)]
        val hideAction = HideAction.entries[intent.getIntExtra(EXTRA_HIDE_ACTION, 0)]
        @Suppress("UNCHECKED_CAST")
        val conflicts =
            intent.getSerializableExtra(EXTRA_CONFLICT_RESOLUTIONS) as? LinkedHashMap<String, Int>
                ?: LinkedHashMap()

        val files = ArrayList<FileDirItem>(paths.size)
        for (i in paths.indices) {
            val path = paths[i]
            files.add(
                FileDirItem(
                    path,
                    names.getOrElse(i) { File(path).name },
                    isDirectory.getOrElse(i) { File(path).isDirectory },
                ),
            )
        }

        cancelled = false
        val titleRes = titleRes(copyOnly, encryptionAction, hideAction)
        notification.setContentTitle(getString(titleRes))

        val engine = TransferEngine(this) { msg -> showErrorToast(msg) }
        transferJob = scope.launch {
            val progressTicker = launch {
                while (isActive) {
                    updateNotification()
                    delay(PROGRESS_RECHECK_INTERVAL)
                }
            }
            val result = engine.run(
                files = files,
                destinationPath = destination,
                copyOnly = copyOnly,
                copyMediaOnly = copyMediaOnly,
                conflictResolutions = conflicts,
                hideAction = hideAction,
                progress = object : TransferEngine.ProgressCallback {
                    override fun onProgress(
                        copiedBytes: Long,
                        totalBytes: Long,
                        currentFileName: String,
                    ) {
                        lastCopiedBytes = copiedBytes
                        lastTotalBytes = totalBytes
                        lastFileName = currentFileName
                    }
                },
                isCancelled = { cancelled },
            )
            progressTicker.cancel()
            broadcastComplete(result, copyOnly, encryptionAction, hideAction)
            stopSelf()
        }
    }

    private fun stopTransfer() {
        cancelled = true
        transferJob?.cancel()
        notification.apply {
            setContentTitle(getString(R.string.canceling))
            setContentText(null)
            setProgress(0, 0, false)
            notificationManager.notify(notificationId, build())
        }
    }

    private fun updateNotification() {
        val percent = if (lastTotalBytes > 0L) {
            ((lastCopiedBytes * 100) / lastTotalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        notification.apply {
            setContentText(lastFileName)
            setProgress(100, percent, lastTotalBytes == 0L)
            notificationManager.notify(notificationId, build())
        }
    }

    private fun broadcastComplete(
        result: TransferResult,
        copyOnly: Boolean,
        encryptionAction: EncryptionAction,
        hideAction: HideAction,
    ) {
        running = false
        val copiedAll = result.transferredCount >= result.expectedCount
        val uiSuccess =
            result.transferredCount > 0 ||
                (result.failedPaths.isEmpty() && !cancelled)
        val intent = Intent(ACTION_TRANSFER_COMPLETE).apply {
            setPackage(applicationContext.packageName)
            putExtra(EXTRA_COPY_ONLY, copyOnly)
            putExtra(EXTRA_ENCRYPTION_ACTION, encryptionAction.ordinal)
            putExtra(EXTRA_HIDE_ACTION, hideAction.ordinal)
            putExtra(EXTRA_DESTINATION, result.destinationPath)
            putExtra(EXTRA_COPIED_ALL, copiedAll)
            putExtra(EXTRA_SUCCESS, uiSuccess)
        }
        applicationContext.sendBroadcast(intent)
    }

    private fun titleRes(
        copyOnly: Boolean,
        encryptionAction: EncryptionAction,
        hideAction: HideAction,
    ): Int =
        when {
            encryptionAction == EncryptionAction.ENCRYPT -> R.string.encrypting
            encryptionAction == EncryptionAction.DECRYPT -> R.string.decrypting
            hideAction == HideAction.HIDE -> R.string.hiding
            hideAction == HideAction.UNHIDE -> R.string.unhiding
            copyOnly -> R.string.copying
            else -> R.string.moving
        }

    private fun getNotificationChannel(): NotificationChannel =
        NotificationChannel(TRANSFER_CHANNEL_ID, getString(R.string.copying), NotificationManager.IMPORTANCE_LOW)

    private fun getNotificationBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, TRANSFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_lock_vector)
            .setShowWhen(false)
            .setSound(null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(getStopAction())

    private fun getStopAction(): NotificationCompat.Action {
        val stopIntent = Intent(this, TransferService::class.java).apply { action = ACTION_STOP }
        val pending = PendingIntent.getService(this, STOP_REQUEST_CODE, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stop_vector,
            getString(R.string.stop),
            pending,
        ).build()
    }

    companion object {
        const val ACTION_TRANSFER = "ltechnologies.onionphone.securefilemanager.TRANSFER"
        const val ACTION_STOP = "ltechnologies.onionphone.securefilemanager.TRANSFER_STOP"
        const val ACTION_TRANSFER_COMPLETE = "ltechnologies.onionphone.securefilemanager.TRANSFER_COMPLETE"

        const val EXTRA_PATHS = "EXTRA_PATHS"
        const val EXTRA_NAMES = "EXTRA_NAMES"
        const val EXTRA_IS_DIRECTORY = "EXTRA_IS_DIRECTORY"
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
        const val EXTRA_COPY_ONLY = "EXTRA_COPY_ONLY"
        const val EXTRA_COPY_MEDIA_ONLY = "EXTRA_COPY_MEDIA_ONLY"
        const val EXTRA_ENCRYPTION_ACTION = "EXTRA_ENCRYPTION_ACTION"
        const val EXTRA_HIDE_ACTION = "EXTRA_HIDE_ACTION"
        const val EXTRA_CONFLICT_RESOLUTIONS = "EXTRA_CONFLICT_RESOLUTIONS"
        const val EXTRA_COPIED_ALL = "EXTRA_COPIED_ALL"
        const val EXTRA_SUCCESS = "EXTRA_SUCCESS"

        private const val TRANSFER_CHANNEL_ID = "Transfer"
        private const val PROGRESS_RECHECK_INTERVAL = 500L
        private const val STOP_REQUEST_CODE = 13

        fun buildTransferIntent(
            context: android.content.Context,
            files: ArrayList<FileDirItem>,
            destinationPath: String,
            copyOnly: Boolean,
            copyMediaOnly: Boolean,
            encryptionAction: EncryptionAction,
            hideAction: HideAction,
            conflictResolutions: LinkedHashMap<String, Int>,
        ): Intent =
            Intent(context, TransferService::class.java).apply {
                action = ACTION_TRANSFER
                putStringArrayListExtra(EXTRA_PATHS, ArrayList(files.map { it.path }))
                putStringArrayListExtra(EXTRA_NAMES, ArrayList(files.map { it.name }))
                putExtra(EXTRA_IS_DIRECTORY, files.map { it.isDirectory }.toBooleanArray())
                putExtra(EXTRA_DESTINATION, destinationPath)
                putExtra(EXTRA_COPY_ONLY, copyOnly)
                putExtra(EXTRA_COPY_MEDIA_ONLY, copyMediaOnly)
                putExtra(EXTRA_ENCRYPTION_ACTION, encryptionAction.ordinal)
                putExtra(EXTRA_HIDE_ACTION, hideAction.ordinal)
                putExtra(EXTRA_CONFLICT_RESOLUTIONS, conflictResolutions)
            }
    }
}
