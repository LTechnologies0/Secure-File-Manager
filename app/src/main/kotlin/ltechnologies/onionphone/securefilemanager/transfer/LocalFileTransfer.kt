package ltechnologies.onionphone.securefilemanager.transfer

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.isOpenPgpFile
import ltechnologies.onionphone.securefilemanager.extensions.isPathOnHidden
import ltechnologies.onionphone.securefilemanager.extensions.needsStupidWritePermissions
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object LocalFileTransfer {
    const val BUFFER_SIZE = 256 * 1024

    fun canUseChannelTransfer(
        context: Context,
        sourcePath: String,
        destinationPath: String,
    ): Boolean =
        !context.needsStupidWritePermissions(sourcePath) &&
            !context.needsStupidWritePermissions(destinationPath) &&
            !context.isPathOnHidden(sourcePath) &&
            !context.isPathOnHidden(destinationPath)

    fun copyFileChannel(context: Context, source: File, destination: File): Long {
        if (context.isPathOnHidden(source.absolutePath) || context.isPathOnHidden(destination.absolutePath)) {
            require(!source.absolutePath.isOpenPgpFile() && !destination.absolutePath.isOpenPgpFile()) {
                "PGP files must not use HiddenFileCrypto transfer"
            }
            return copyStreams(
                HiddenFileCrypto.openInput(context, source.absolutePath),
                HiddenFileCrypto.openOutput(context, destination.absolutePath),
                BUFFER_SIZE,
            ) { }
        }
        FileInputStream(source).channel.use { input ->
            FileOutputStream(destination).channel.use { output ->
                val size = input.size()
                var transferred = 0L
                while (transferred < size) {
                    transferred += input.transferTo(transferred, size - transferred, output)
                }
                return transferred
            }
        }
    }

    fun copyStreams(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int,
        onChunk: (Long) -> Unit,
    ): Long {
        var copied = 0L
        val buffer = ByteArray(bufferSize)
        input.use { inp ->
            output.use { out ->
                var bytes = inp.read(buffer)
                while (bytes >= 0) {
                    if (bytes > 0) {
                        out.write(buffer, 0, bytes)
                        copied += bytes
                        onChunk(bytes.toLong())
                    }
                    bytes = inp.read(buffer)
                }
                out.flush()
            }
        }
        return copied
    }
}
