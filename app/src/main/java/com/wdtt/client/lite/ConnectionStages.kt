package com.wdtt.client.lite

import com.wdtt.client.ConnectionPipelineState
import com.wdtt.client.ConnectionStep

/** One connection attempt as the home screen shows it: which stage, out of how many. */
data class StageView(
    /** 0-based index of the current stage; equals [total] once connected. */
    val index: Int,
    val total: Int,
    val title: String,
    val failed: Boolean,
)

/**
 * Connection stages reported by the client, in user words: no protocol names or
 * internal terms (interface text rules in CLAUDE.md).
 */
object ConnectionStages {
    fun title(step: ConnectionStep): String = when (step) {
        ConnectionStep.DNS -> "Проверка сети"
        ConnectionStep.VK -> "Получение доступа к звонку"
        ConnectionStep.CAPTCHA -> "Ожидание проверки VK"
        ConnectionStep.WRAP -> "Подготовка соединения"
        ConnectionStep.TURN -> "Подключение через звонок"
        ConnectionStep.DTLS -> "Защита соединения"
        ConnectionStep.WORKERS -> "Соединение с сервером"
        ConnectionStep.VPN, ConnectionStep.RAW, ConnectionStep.SOCKS -> "Запуск туннеля"
        ConnectionStep.DONE -> "Подключено"
    }

    fun view(state: ConnectionPipelineState): StageView? {
        if (!state.visible) return null
        val steps = state.stepsToShow()
        val step = state.failed ?: state.current ?: return null
        val index = when {
            step == ConnectionStep.DONE -> steps.size
            step in steps -> steps.indexOf(step)
            // A step the list does not show (e.g. the handshake in RAW): count what is done.
            else -> steps.count { it in state.completed }.coerceAtMost(steps.size - 1)
        }
        return StageView(index = index, total = steps.size, title = title(step), failed = state.failed != null)
    }

    /** Status line under the button. */
    fun line(view: StageView): String = when {
        view.failed -> "Этап «${view.title}» не выполнен."
        view.index >= view.total -> "Подключено"
        else -> view.title
    }
}
