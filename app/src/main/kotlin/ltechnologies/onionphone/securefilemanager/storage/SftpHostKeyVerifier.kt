package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun interface HostKeyPrompt {
    fun confirm(fingerprint: String, onResult: (Boolean) -> Unit)
}

class SftpHostKeyVerifier(
    private val context: Context,
    private val host: String,
    private val port: Int,
    private val prompt: HostKeyPrompt?,
) : HostKeyVerifier {
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = SftpHostKeyStore.fingerprint(key)
        val stored = SftpHostKeyStore.get(context, host, this.port)
        return when {
            stored == fingerprint -> true
            stored != null -> false
            prompt == null -> false
            else -> {
                val latch = CountDownLatch(1)
                var accepted = false
                prompt.confirm(fingerprint) { accept ->
                    accepted = accept
                    if (accept) {
                        SftpHostKeyStore.save(context, host, this.port, fingerprint)
                    }
                    latch.countDown()
                }
                latch.await(120, TimeUnit.SECONDS)
                accepted
            }
        }
    }
}
