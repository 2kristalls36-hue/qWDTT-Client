package com.wdtt.client.lite

/**
 * Connection power shown to the user. The number of tunnel workers stays internal:
 * the interface never shows it (see CLAUDE.md, «Тексты интерфейса»).
 */
enum class PowerLevel(val title: String, val description: String, internal val workers: Int) {
    Stable(
        "Стабильный",
        "Устойчивое соединение с низкой нагрузкой на батарею и сервер.\n" +
            "Подходит для комфортного просмотра видео в качестве 1080p.",
        9,
    ),
    Balanced(
        "Сбалансированный",
        "Оптимальное соотношение скорости и нагрузки при среднем расходе батареи.\n" +
            "Подходит для комфортного просмотра видео в качестве 1440p.",
        18,
    ),
    Maximum(
        "Максимальный",
        "Наибольшая скорость при повышенной нагрузке на батарею и сервер.\n" +
            "Подходит для комфортного просмотра видео в качестве 4K.",
        27,
    );

    companion object {
        val Default = Balanced
        fun fromIndex(index: Int): PowerLevel = entries.getOrElse(index) { Default }
    }
}
