package com.wdtt.client.ui

/** Theme mode stored in settings: "system", "light" or "dark". */
internal fun themeModeIsDark(themeMode: String, systemDark: Boolean): Boolean =
    when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
