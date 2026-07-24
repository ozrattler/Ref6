package com.refsix.wear.ui.screens

fun formatTime12h(hhmm: String): String {
    if (hhmm.isBlank()) return hhmm
    val parts = hhmm.split(":")
    if (parts.size < 2) return hhmm
    val h = parts[0].toIntOrNull() ?: return hhmm
    val m = parts[1].toIntOrNull() ?: return hhmm
    val period = if (h < 12) "AM" else "PM"
    val hour = if (h % 12 == 0) 12 else h % 12
    return "$hour:${m.toString().padStart(2, '0')}$period"
}
