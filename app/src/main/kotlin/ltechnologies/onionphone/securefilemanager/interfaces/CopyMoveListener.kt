package ltechnologies.onionphone.securefilemanager.interfaces

import ltechnologies.onionphone.securefilemanager.helpers.EncryptionAction
import ltechnologies.onionphone.securefilemanager.helpers.HideAction

interface CopyMoveListener {
    fun copySucceeded(
        copyOnly: Boolean,
        copiedAll: Boolean,
        destinationPath: String,
        encryptionAction: EncryptionAction = EncryptionAction.NONE,
        hideAction: HideAction
    )

    fun copyFailed(encryptionAction: EncryptionAction = EncryptionAction.NONE)
}
