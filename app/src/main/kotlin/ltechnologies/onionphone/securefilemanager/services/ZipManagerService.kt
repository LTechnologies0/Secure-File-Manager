package ltechnologies.onionphone.securefilemanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.APP_CHANNEL_ID
import ltechnologies.onionphone.securefilemanager.helpers.ZipManagerEvents
import ltechnologies.onionphone.securefilemanager.helpers.getNotificationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

class ZipManagerService : Service() {

    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotification: NotificationCompat.Builder
    private lateinit var mProgressMonitor: ProgressMonitor

    private val mNotificationId = getNotificationId()
    private var mRunning = false
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        this.mNotificationManager = this.getNotificationManager()
        this.mNotificationManager.createNotificationChannel(this.getNotificationChannel())
        this.mNotification = this.getNotificationBuilder()

        this.startForeground(this.mNotificationId, this.mNotification.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_COMPRESSION -> {
                if (this.setRunning()) {
                    val extras = intent.extras ?: return START_NOT_STICKY
                    val paths = extras.getStringArrayList(EXTRA_PATH) ?: return START_NOT_STICKY
                    val destination = extras.getString(EXTRA_DESTINATION) ?: return START_NOT_STICKY
                    this.compress(paths, destination, extras.getCharArray(EXTRA_PASSWORD))
                }
            }
            ACTION_DECOMPRESSION -> {
                if (this.setRunning()) {
                    val extras = intent.extras ?: return START_NOT_STICKY
                    val path = extras.getString(EXTRA_PATH) ?: return START_NOT_STICKY
                    this.decompress(path, extras.getString(EXTRA_DESTINATION), extras.getCharArray(EXTRA_PASSWORD))
                }
            }
            ACTION_STOP -> {
                this.stopAction()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceJob.cancel()
        this.mNotificationManager.cancel(this.mNotificationId)
        ZipManagerEvents.notifyComplete()
        super.onDestroy()
    }

    private fun setRunning(): Boolean {
        if (this.mRunning) {
            this.toast(R.string.compression_action_collision)
            return false
        }
        this.mRunning = true
        return true
    }

    private fun compress(
        sourcePaths: List<String>,
        targetPath: String,
        @Suppress("UNUSED_PARAMETER") password: CharArray?,
    ) {
        serviceScope.launch {
            try {
                toast(R.string.compressing)

                val parameters = ZipParameters()

                createZipFile(targetPath, null).also { zipFile: ZipFile ->
                    zipFile.insertAll(this@ZipManagerService, sourcePaths, parameters)
                    handleNotification(ACTION_COMPRESSION, targetPath)
                }
            } catch (exception: Exception) {
                showErrorToast(exception)
                stopSelf()
            }
        }
    }

    private fun decompress(path: String, destination: String?, password: CharArray?) {
        try {
            createZipFile(path, password).also { zipFile: ZipFile ->
                this.toast(R.string.decompressing)
                val destinationPath = destination ?: zipFile.file.absolutePath.getParentPath()
                zipFile.extractAll(destinationPath)
                this.handleNotification(ACTION_DECOMPRESSION)
            }
        } catch (e: Exception) {
            this.toast(R.string.decompressing_failed)
            val message = e.message
            if (e is ZipException && message != null) {
                this.toast(message)
            } else {
                this.showErrorToast(e)
            }
        }
    }

    private fun stopAction() {
        this.mNotification.apply {
            setContentTitle(getString(R.string.canceling))
            setContentText(null)
            mNotificationManager.notify(mNotificationId, build())
        }
        this.mProgressMonitor.isCancelAllTasks = true
    }

    private fun createZipFile(path: String, password: CharArray?): ZipFile {
        val zipFile: ZipFile =
            if (password == null || password.isEmpty()) ZipFile(path)
            else ZipFile(path, password)

        zipFile.isRunInThread = true
        this.mProgressMonitor = zipFile.progressMonitor

        return zipFile
    }

    private fun handleNotification(
        action: String,
        targetPath: String? = null
    ) {
        serviceScope.launch {
            while (mProgressMonitor.state != ProgressMonitor.State.READY) {
                updateNotification(action)
                delay(PROGRESS_RECHECK_INTERVAL)
            }

            onComplete(action, targetPath)
        }
    }

    private fun updateNotification(action: String) {
        if (this.mProgressMonitor.fileName != null) {
            this.mNotification.apply {
                setContentTitle(
                    when (action) {
                        ACTION_COMPRESSION -> getString(R.string.compressing)
                        ACTION_DECOMPRESSION -> getString(R.string.decompressing)
                        else -> null
                    }
                )
                setContentText(mProgressMonitor.fileName.getFilenameFromPath())
                setSubText("${mProgressMonitor.percentDone}%")
                setProgress(100, mProgressMonitor.percentDone, false)
                mNotificationManager.notify(mNotificationId, build())
            }
        }
    }

    private fun onComplete(action: String, targetPath: String?) {
        when (this.mProgressMonitor.result) {
            ProgressMonitor.Result.CANCELLED,
            ProgressMonitor.Result.ERROR -> {
                if (targetPath != null) {
                    File(targetPath).delete()
                }
            }
            else -> {
                // nothing
            }
        }

        val text = getCompleteText(action)
        if (text != null) {
            this.toast(text)
        }
        this.stopSelf()
    }

    private fun getCompleteText(action: String): String? {
        return if (mProgressMonitor.result == ProgressMonitor.Result.SUCCESS) {
            when (action) {
                ACTION_COMPRESSION -> getString(R.string.compression_successful)
                ACTION_DECOMPRESSION -> getString(R.string.decompression_successful)
                else -> null
            }
        } else {
            when (action) {
                ACTION_COMPRESSION -> getString(R.string.compressing_failed)
                ACTION_DECOMPRESSION -> getString(R.string.decompressing_failed)
                else -> null
            }
        }
    }

    private fun getNotificationChannel(): NotificationChannel =
        NotificationChannel(
            APP_CHANNEL_ID,
            APP_CHANNEL_ID,
            NotificationManager.IMPORTANCE_LOW
        )

    private fun getNotificationBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, APP_CHANNEL_ID)
            .setContentTitle(getString(R.string.starting))
            .setSmallIcon(R.drawable.ic_shield_lock_vector)
            .setShowWhen(false)
            .setSound(null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(this.getStopAction())

    private fun getStopAction(): NotificationCompat.Action? {
        val broadcastIntent: Intent =
            Intent(this, this::class.java).apply {
                action = ACTION_STOP
            }

        val actionIntent: PendingIntent =
            PendingIntent.getService(
                this,
                STOP_REQUEST_CODE,
                broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_stop_vector,
            this.getString(R.string.stop),
            actionIntent
        ).build()
    }

    companion object {
        // Action
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_COMPRESSION = "ACTION_COMPRESSION"
        const val ACTION_DECOMPRESSION = "ACTION_DECOMPRESSION"

        // Extra
        const val EXTRA_PATH = "EXTRA_PATH"
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
        const val EXTRA_PASSWORD = "EXTRA_PASSWORD"

        // Other
        private const val PROGRESS_RECHECK_INTERVAL = 500L
        private const val STOP_REQUEST_CODE = 12
    }

}
