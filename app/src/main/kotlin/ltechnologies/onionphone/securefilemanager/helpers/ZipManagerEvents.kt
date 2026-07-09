package ltechnologies.onionphone.securefilemanager.helpers

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ZipManagerEvents {
    private val _complete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val complete = _complete.asSharedFlow()

    fun notifyComplete() {
        _complete.tryEmit(Unit)
    }
}
