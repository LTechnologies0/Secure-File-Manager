package ltechnologies.onionphone.securefilemanager.transfer

data class TransferResult(
    val success: Boolean,
    val transferredCount: Int,
    val expectedCount: Int,
    val destinationPath: String,
    val failedPaths: List<String> = emptyList(),
)
