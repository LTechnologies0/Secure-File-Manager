package ltechnologies.onionphone.securefilemanager.activities

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ProcessLifecycleOwner
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.dialogs.FileConflictDialog
import ltechnologies.onionphone.securefilemanager.dialogs.FilePickerDialog
import ltechnologies.onionphone.securefilemanager.dialogs.PasswordPromptDialog
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import ltechnologies.onionphone.securefilemanager.interfaces.CopyMoveListener
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.observers.AuthenticationObserver
import ltechnologies.onionphone.securefilemanager.receivers.LockReceiver
import ltechnologies.onionphone.securefilemanager.services.TransferService
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import ltechnologies.onionphone.securefilemanager.storage.RemoteTransfer
import ltechnologies.onionphone.securefilemanager.storage.TrashManager
import ltechnologies.onionphone.securefilemanager.openpgp.PgpShieldBridge
import ltechnologies.onionphone.securefilemanager.transfer.TransferEngine
import com.google.android.material.snackbar.Snackbar
import androidx.annotation.StringRes
import net.lingala.zip4j.ZipFile
import java.io.File
import java.util.*

abstract class BaseAbstractActivity : AppCompatActivity() {

    private lateinit var mAuthenticationObserver: AuthenticationObserver

    private var lockReceiver: LockReceiver = LockReceiver()
    private var actionOnPermission: ((granted: Boolean) -> Unit)? = null

    var copyMoveCallback: ((destinationPath: String, copiedAll: Boolean) -> Unit)? = null
    var transferCompleteCallback: ((success: Boolean) -> Unit)? = null
    private var pendingExportPath: String? = null
    var checkedDocumentPath = ""
    var onPgpShieldResult: (() -> Unit)? = null
    private var pendingPgpShieldAction: String? = null
    var isFragmentActionModeActive = false
        private set

    fun setFragmentActionModeActive(active: Boolean) {
        isFragmentActionModeActive = active
        invalidateOptionsMenu()
        onFragmentActionModeActiveChanged(active)
    }

    protected open fun onFragmentActionModeActiveChanged(active: Boolean) = Unit

    private val pgpShieldLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pendingPgpShieldAction
        pendingPgpShieldAction = null
        when (result.resultCode) {
            RESULT_OK -> showPgpShieldSnackbar(
                if (action == PgpShieldBridge.ACTION_ENCRYPT ||
                    action == PgpShieldBridge.ACTION_ENCRYPT_FOLDER
                ) {
                    R.string.encrypting_success
                } else {
                    R.string.decrypting_success
                },
            )
            RESULT_CANCELED -> Unit
            else -> showPgpShieldSnackbar(
                if (action == PgpShieldBridge.ACTION_ENCRYPT ||
                    action == PgpShieldBridge.ACTION_ENCRYPT_FOLDER
                ) {
                    R.string.encryption_failed
                } else {
                    R.string.decryption_failed
                },
            )
        }
        onPgpShieldResult?.invoke()
        onPgpShieldResult = null
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleCreateDocumentExport(result.data?.data)
        }
    }

    private val openPrivateKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                funAfterPrivateKeyPick?.invoke(uri.toString())
            }
        }
        funAfterPrivateKeyPick = null
    }

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        handleOpenDocumentTreeResult(result.resultCode, result.data)
    }

    private fun showPgpShieldSnackbar(@StringRes messageId: Int) {
        Snackbar.make(findViewById(android.R.id.content), messageId, Snackbar.LENGTH_SHORT).show()
    }

    fun launchPgpShield(paths: List<String>, action: String) {
        pendingPgpShieldAction = action
        val intent = PgpShieldBridge.buildIntent(this, paths, action)
        if (intent == null) {
            pendingPgpShieldAction = null
            // #region agent log
            ltechnologies.onionphone.securefilemanager.helpers.DebugAgentLog.log(
                location = "BaseAbstractActivity.kt:launchPgpShield",
                message = "intent build failed",
                data = mapOf("action" to action, "pathCount" to paths.size),
                hypothesisId = "B",
            )
            // #endregion
            toast(
                if (PgpShieldBridge.resolvePackage(this, action) == null) {
                    R.string.pgpshield_missing
                } else {
                    R.string.unknown_error_occurred
                },
            )
            return
        }
        // #region agent log
        ltechnologies.onionphone.securefilemanager.helpers.DebugAgentLog.log(
            location = "BaseAbstractActivity.kt:launchPgpShield",
            message = "launching pgp-shield",
            data = mapOf(
                "action" to action,
                "pathCount" to paths.size,
                "hasData" to (intent.data != null),
                "hasExtraStream" to intent.hasExtra(Intent.EXTRA_STREAM),
            ),
            hypothesisId = "B",
        )
        // #endregion
        pgpShieldLauncher.launch(intent)
    }

    fun launchPgpShieldIntent(intent: Intent, action: String) {
        pendingPgpShieldAction = action
        pgpShieldLauncher.launch(intent)
    }

    fun launchPgpShieldEncrypt(paths: List<String>) {
        launchPgpShield(paths, PgpShieldBridge.ACTION_ENCRYPT)
    }

    fun launchPgpShieldDecrypt(paths: List<String>) {
        launchPgpShield(paths, PgpShieldBridge.ACTION_DECRYPT)
    }

    companion object {
        private var funAfterSAFPermissionHolder: java.lang.ref.WeakReference<((success: Boolean) -> Unit)>? = null
        private var funAfterPrivateKeyPickHolder: java.lang.ref.WeakReference<((uri: String) -> Unit)>? = null

        var funAfterSAFPermission: ((success: Boolean) -> Unit)?
            get() = funAfterSAFPermissionHolder?.get()
            set(value) {
                funAfterSAFPermissionHolder = value?.let { java.lang.ref.WeakReference(it) }
            }

        var funAfterPrivateKeyPick: ((uri: String) -> Unit)?
            get() = funAfterPrivateKeyPickHolder?.get()
            set(value) {
                funAfterPrivateKeyPickHolder = value?.let { java.lang.ref.WeakReference(it) }
            }
    }

    private val transferCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TransferService.ACTION_TRANSFER_COMPLETE) {
                return
            }
            val copyOnly = intent.getBooleanExtra(TransferService.EXTRA_COPY_ONLY, true)
            val copiedAll = intent.getBooleanExtra(TransferService.EXTRA_COPIED_ALL, false)
            val destination = intent.getStringExtra(TransferService.EXTRA_DESTINATION) ?: return
            val encryptionAction =
                EncryptionAction.entries[intent.getIntExtra(TransferService.EXTRA_ENCRYPTION_ACTION, 0)]
            val hideAction =
                HideAction.entries[intent.getIntExtra(TransferService.EXTRA_HIDE_ACTION, 0)]
            if (intent.getBooleanExtra(TransferService.EXTRA_SUCCESS, false)) {
                copyMoveListener.copySucceeded(
                    copyOnly,
                    copiedAll,
                    destination,
                    encryptionAction,
                    hideAction,
                )
            } else {
                copyMoveListener.copyFailed(encryptionAction)
            }
            transferCompleteCallback?.invoke(intent.getBooleanExtra(TransferService.EXTRA_SUCCESS, false))
            transferCompleteCallback = null
        }
    }

    private val copyMoveListener = object : CopyMoveListener {
        override fun copySucceeded(
            copyOnly: Boolean,
            copiedAll: Boolean,
            destinationPath: String,
            encryptionAction: EncryptionAction,
            hideAction: HideAction
        ) {
            when {
                isEncryption(encryptionAction) -> {
                    toast(if (copiedAll) R.string.encrypting_success else R.string.encrypting_success_partial)
                }
                isDecryption(encryptionAction) -> {
                    toast(if (copiedAll) R.string.decrypting_success else R.string.decrypting_success_partial)
                }
                isHide(hideAction) ->
                    toast(if (copiedAll) R.string.hiding_success else R.string.hiding_success_partial)
                isUnhide(hideAction) ->
                    toast(if (copiedAll) R.string.unhiding_success else R.string.unhiding_success_partial)
                copyOnly -> {
                    toast(if (copiedAll) R.string.copying_success else R.string.copying_success_partial)
                }
                else -> {
                    toast(if (copiedAll) R.string.moving_success else R.string.moving_success_partial)
                }
            }

            copyMoveCallback?.invoke(destinationPath, copiedAll)
            copyMoveCallback = null
        }

        override fun copyFailed(encryptionAction: EncryptionAction) {
            toast(
                when (encryptionAction) {
                    EncryptionAction.ENCRYPT -> R.string.encryption_failed
                    EncryptionAction.DECRYPT -> R.string.decryption_failed
                    else -> R.string.copy_move_failed
                }
            )
            copyMoveCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        this.mAuthenticationObserver = AuthenticationObserver(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this.mAuthenticationObserver)

        ContextCompat.registerReceiver(
            this,
            this.lockReceiver,
            LockReceiver.getIntent(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val hiddenPath = this.getHiddenPath()
        this.config.hiddenPath = hiddenPath
        if (!getDoesFilePathExist(hiddenPath)) {
            createDirectorySync(hiddenPath)
        }
        ensureBackgroundThread {
            TrashManager.purgeExpired(applicationContext)
        }

        this.addFlagsSecure()
        this.setTheme()

        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            transferCompleteReceiver,
            IntentFilter(TransferService.ACTION_TRANSFER_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        WindowInsetsUtil.apply(this)
    }

    override fun onResume() {
        super.onResume()
        applyScreenshotBlockPolicy()
        actionOnPermission?.let {
            if (hasPermission(PERMISSION_WRITE_STORAGE)) {
                actionOnPermission = null
                it(true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        actionOnPermission = null
    }

    override fun onDestroy() {
        unregisterReceiver(transferCompleteReceiver)
        funAfterSAFPermission = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this.mAuthenticationObserver)
        this.unregisterReceiver(this.lockReceiver)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun launchOpenDocumentTree(path: String) {
        checkedDocumentPath = path
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            if (resolveActivity(packageManager) == null) {
                type = "*/*"
            }
        }
        if (intent.resolveActivity(packageManager) != null) {
            openDocumentTreeLauncher.launch(intent)
        } else {
            toast(R.string.unknown_error_occurred)
        }
    }

    private fun handleOpenDocumentTreeResult(resultCode: Int, resultData: Intent?) {
        val partition = try {
            checkedDocumentPath.substring(9, 18)
        } catch (e: Exception) {
            ""
        }
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val treeUri = resultData.data ?: return
            val dataString = resultData.dataString.orEmpty()
            val isProperPartition = partition.isEmpty() || dataString.contains(partition)
            if (isProperSDFolder(treeUri) && isProperPartition) {
                saveTreeUri(resultData)
                funAfterSAFPermission?.invoke(true)
                funAfterSAFPermission = null
            } else {
                toast(R.string.wrong_root_selected)
                launchOpenDocumentTree(checkedDocumentPath)
            }
        }
    }

    private fun saveTreeUri(resultData: Intent) {
        val treeUri = resultData.data ?: return
        config.treeUri = treeUri.toString()

        val takeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        applicationContext.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
    }

    private fun isProperSDFolder(uri: Uri) =
        isExternalStorageDocument(uri) && isRootUri(uri) && !isInternalStorage(uri)

    private fun isRootUri(uri: Uri) = DocumentsContract.getTreeDocumentId(uri).endsWith(":")

    private fun isInternalStorage(uri: Uri) =
        isExternalStorageDocument(uri) && DocumentsContract.getTreeDocumentId(uri)
            .contains("primary")

    private fun isExternalStorageDocument(uri: Uri) =
        "com.android.externalstorage.documents" == uri.authority

    fun copyMoveFilesTo(
        fileDirItems: ArrayList<FileDirItem>,
        source: String,
        destination: String,
        isCopyOperation: Boolean,
        copyPhotoVideoOnly: Boolean,
        encryptionAction: EncryptionAction = EncryptionAction.NONE,
        hideAction: HideAction = HideAction.NONE,
        callback: (destinationPath: String, copiedAll: Boolean) -> Unit
    ) {
        if (source == destination && isNotEncryption(encryptionAction)) {
            toast(R.string.source_and_destination_same)
            return
        }

        if (!getDoesFilePathExist(destination)) {
            toast(R.string.invalid_destination)
            return
        }

        if (RemotePath.isRemote(destination)) {
            ensureBackgroundThread {
                val (ok, fail) = RemoteTransfer.copyToRemote(
                    this,
                    fileDirItems,
                    destination,
                    isCopyOperation,
                )
                runOnUiThread {
                    callback(destination, fail == 0 && ok > 0)
                }
            }
            return
        }

        if (fileDirItems.any { RemotePath.isRemote(it.path) }) {
            ensureBackgroundThread {
                val copiedAll = RemoteTransfer.copyFromRemote(
                    this,
                    fileDirItems,
                    destination,
                    isCopyOperation,
                )
                runOnUiThread {
                    callback(destination, copiedAll)
                }
            }
            return
        }

        handleSAFDialog(destination) {
            if (!it) {
                copyMoveListener.copyFailed(EncryptionAction.NONE)
                return@handleSAFDialog
            }

            copyMoveCallback = callback
            var fileCountToCopy = fileDirItems.size
            if (isCopyOperation || !isNotEncryption(encryptionAction)) {
                startCopyMove(
                    fileDirItems,
                    destination,
                    isCopyOperation,
                    copyPhotoVideoOnly,
                    encryptionAction,
                    hideAction
                )
            } else {
                if (
                    isPathOnSD(source) ||
                    isPathOnSD(destination) ||
                    fileDirItems.first().isDirectory
                ) {
                    handleSAFDialog(source) { success ->
                        if (success) {
                            startCopyMove(
                                fileDirItems,
                                destination,
                                isCopyOperation,
                                copyPhotoVideoOnly,
                                encryptionAction,
                                hideAction
                            )
                        }
                    }
                } else {
                    try {
                        checkConflicts(
                            fileDirItems,
                            destination,
                            0,
                            LinkedHashMap(),
                            encryptionAction
                        ) { resolutions ->
                            ensureBackgroundThread {
                                toast(R.string.moving)
                                val updatedPaths = ArrayList<String>(fileDirItems.size)
                                val destinationFolder = File(destination)
                                for (oldFileDirItem in fileDirItems) {
                                    var newFile = File(destinationFolder, oldFileDirItem.name)
                                    if (newFile.exists()) {
                                        when {
                                            getConflictResolution(
                                                resolutions,
                                                newFile.absolutePath
                                            ) == CONFLICT_SKIP -> fileCountToCopy--
                                            getConflictResolution(
                                                resolutions,
                                                newFile.absolutePath
                                            ) == CONFLICT_KEEP_BOTH -> newFile =
                                                getAlternativeFile(newFile)
                                            else ->
                                                // this file is guaranteed to be on the internal storage, so just delete it this way
                                                newFile.delete()
                                        }
                                    }

                                    if (!newFile.exists() &&
                                        TransferEngine.tryFastMove(File(oldFileDirItem.path), newFile)
                                    ) {
                                        if (!config.keepLastModified) {
                                            newFile.setLastModified(System.currentTimeMillis())
                                        }
                                        updatedPaths.add(newFile.absolutePath)
                                        deleteFromMediaStore(oldFileDirItem.path)
                                    }
                                }

                                runOnUiThread {
                                    if (updatedPaths.isEmpty()) {
                                        copyMoveListener.copySucceeded(
                                            false,
                                            fileCountToCopy == 0,
                                            destination,
                                            encryptionAction,
                                            hideAction
                                        )
                                    } else {
                                        copyMoveListener.copySucceeded(
                                            false,
                                            fileCountToCopy <= updatedPaths.size,
                                            destination,
                                            encryptionAction,
                                            hideAction
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        }
    }

    fun getEncryptedFile(file: File): File = file

    fun getDecryptedFile(file: File): File = file

    fun beginExportDocument(sourcePath: String) {
        if (sourcePath.isOpenPgpFile()) {
            toast(R.string.share_encrypted_file_confirmation)
            return
        }
        pendingExportPath = sourcePath
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = sourcePath.getMimeType()
            putExtra(Intent.EXTRA_TITLE, sourcePath.getFilenameFromPath())
        }
        createDocumentLauncher.launch(intent)
    }

    fun pickPrivateKey(callback: (uri: String) -> Unit) {
        funAfterPrivateKeyPick = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        openPrivateKeyLauncher.launch(intent)
    }

    private fun handleCreateDocumentExport(destination: Uri?) {
        val sourcePath = pendingExportPath ?: return
        pendingExportPath = null
        if (destination == null) {
            return
        }
        ensureBackgroundThread {
            try {
                contentResolver.openOutputStream(destination)?.use { output ->
                    getFileInputStreamSync(sourcePath)?.use { input ->
                        input.copyTo(output)
                    }
                }
                runOnUiThread { toast(R.string.file_saved) }
            } catch (e: Exception) {
                runOnUiThread { showErrorToast(e) }
            }
        }
    }

    protected fun startTransfer(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        isCopyOperation: Boolean,
        copyPhotoVideoOnly: Boolean = false,
        encryptionAction: EncryptionAction = EncryptionAction.NONE,
        hideAction: HideAction = HideAction.NONE,
        conflictResolutions: LinkedHashMap<String, Int> = LinkedHashMap(),
    ) {
        val intent = TransferService.buildTransferIntent(
            this,
            files,
            destinationPath,
            isCopyOperation,
            copyPhotoVideoOnly,
            encryptionAction,
            hideAction,
            conflictResolutions,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startCopyMove(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        isCopyOperation: Boolean,
        copyPhotoVideoOnly: Boolean,
        encryptionAction: EncryptionAction,
        hideAction: HideAction
    ) {
        ensureBackgroundThread {
            val availableSpace = destinationPath.getAvailableStorageB()
            val sumToCopy = files.sumByLong { it.getProperSize() }
            runOnUiThread {
                if (availableSpace == -1L || sumToCopy < availableSpace) {
                    checkConflicts(files, destinationPath, 0, LinkedHashMap(), encryptionAction) {
                        toast(
                            when {
                                isEncryption(encryptionAction) -> {
                                    R.string.encrypting
                                }
                                isDecryption(encryptionAction) -> {
                                    R.string.decrypting
                                }
                                isHide(hideAction) -> {
                                    R.string.hiding
                                }
                                isUnhide(hideAction) -> {
                                    R.string.unhiding
                                }
                                isCopyOperation -> {
                                    R.string.copying
                                }
                                else -> {
                                    R.string.moving
                                }
                            }
                        )
                        startTransfer(
                            files,
                            destinationPath,
                            isCopyOperation,
                            copyPhotoVideoOnly,
                            encryptionAction,
                            hideAction,
                            it,
                        )
                    }
                } else {
                    val text = String.format(
                        getString(R.string.no_space),
                        sumToCopy.formatSize(),
                        availableSpace.formatSize()
                    )
                    toastLong(text)
                }
            }
        }
    }

    private fun checkConflicts(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        index: Int,
        conflictResolutions: LinkedHashMap<String, Int>,
        encryptionAction: EncryptionAction,
        callback: (resolutions: LinkedHashMap<String, Int>) -> Unit
    ) {
        if (index == files.size) {
            callback(conflictResolutions)
            return
        }

        val file = files[index]
        val newFileDirItem =
            FileDirItem("$destinationPath/${file.name}", file.name, file.isDirectory)
        val onExistsKnown: (Boolean) -> Unit = { exists ->
            if (exists) {
                if (isNotEncryption(encryptionAction)) {
                    FileConflictDialog(
                        this,
                        newFileDirItem,
                        files.size > 1
                    ) { resolution, applyForAll ->
                        if (applyForAll) {
                            conflictResolutions.clear()
                            conflictResolutions[""] = resolution
                            checkConflicts(
                                files,
                                destinationPath,
                                files.size,
                                conflictResolutions,
                                encryptionAction,
                                callback
                            )
                        } else {
                            conflictResolutions[newFileDirItem.path] = resolution
                            checkConflicts(
                                files,
                                destinationPath,
                                index + 1,
                                conflictResolutions,
                                encryptionAction,
                                callback
                            )
                        }
                    }
                } else {
                    conflictResolutions.clear()
                    checkConflicts(
                        files,
                        destinationPath,
                        files.size,
                        conflictResolutions,
                        encryptionAction,
                        callback
                    )
                }
            } else {
                checkConflicts(
                    files,
                    destinationPath,
                    index + 1,
                    conflictResolutions,
                    encryptionAction,
                    callback
                )
            }
        }
        if (RemotePath.isRemote(newFileDirItem.path)) {
            getDoesFilePathExistAsync(newFileDirItem.path, onExistsKnown)
        } else {
            onExistsKnown(getDoesFilePathExist(newFileDirItem.path))
        }
    }

    fun decompressHandle(
        path: String,
        dialogPath: String = this.config.homeFolder,
        decompress: (destination: String, password: CharArray?) -> Unit
    ) =
        FilePickerDialog(
            this,
            dialogPath,
            pickFile = false,
            showFAB = true,
        ) { destination ->
            ZipFile(path).checkDecompressionCollision(this, destination) { collision ->
                if (collision) {
                    return@checkDecompressionCollision
                }

                val zipFile = ZipFile(path)
                if (zipFile.isEncrypted) {
                    PasswordPromptDialog(
                        this,
                        String.format(
                            getString(R.string.decompress_password_title),
                            path.getFilenameFromPath()
                        )
                    ) { password ->
                        decompress.invoke(destination, password)
                    }
                } else {
                    decompress.invoke(destination, null)
                }
            }
        }

    fun handlePermission(permissionId: Int, callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (hasPermission(permissionId)) {
            callback(true)
        } else if (permissionId == PERMISSION_WRITE_STORAGE && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            actionOnPermission = callback
            requestStorageAccess()
        } else {
            actionOnPermission = callback
            ActivityCompat.requestPermissions(
                this,
                arrayOf(getPermissionString(permissionId)),
                GENERIC_PERM_HANDLER,
            )
        }
    }

    // synchronous return value determines only if we are showing the SAF dialog, callback result tells if the SD permission has been granted
    fun handleSAFDialog(path: String, callback: (success: Boolean) -> Unit): Boolean {
        return if (!packageName.startsWith("ltechnologies.onionphone.securefilemanager")) {
            callback(true)
            false
        } else if (isShowingSAFDialog(path)) {
            funAfterSAFPermission = callback
            true
        } else {
            callback(true)
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == GENERIC_PERM_HANDLER && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }

}
