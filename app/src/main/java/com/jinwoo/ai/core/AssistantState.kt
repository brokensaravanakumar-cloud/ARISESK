package com.jinwoo.ai.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Shared, in-process state that the service writes and the Compose UI observes. */
object AssistantState {
    enum class Phase { OFF, WAKE, ACTIVATED, LISTENING, THINKING, SPEAKING, CONFIRM }

    data class Ui(
        val phase: Phase = Phase.OFF,
        val transcript: String = "",
        val partial: String = "",
        val reply: String = "",
        val notice: String = "",
        val noticeId: Long = 0,
        val activationId: Long = 0,
        val confirmQuestion: String? = null
    )

    private val _flow = MutableStateFlow(Ui())
    val flow: StateFlow<Ui> = _flow.asStateFlow()

    fun update(block: (Ui) -> Ui) {
        _flow.update(block)
    }

    fun notice(text: String) = update { it.copy(notice = text, noticeId = it.noticeId + 1) }
}

/** True while the app's UI is on screen. Used to decide whether we may open other apps directly. */
object Visibility {
    @Volatile
    var appVisible: Boolean = false
}
