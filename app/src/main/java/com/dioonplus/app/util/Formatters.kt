package com.dioonplus.app.util

import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val arabicLocale = Locale("ar", "JO")

fun formatMoney(cents: Long, includeSign: Boolean = false): String {
    val formatter = NumberFormat.getNumberInstance(arabicLocale).apply {
        minimumFractionDigits = if (cents % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
        roundingMode = RoundingMode.HALF_UP
    }
    val absolute = kotlin.math.abs(cents).toBigDecimal().movePointLeft(2)
    val sign = when {
        !includeSign -> ""
        cents > 0 -> "+"
        cents < 0 -> "−"
        else -> ""
    }
    return "$sign${formatter.format(absolute)} د.أ"
}

fun parseMoneyToCents(value: String): Long? {
    val normalized = value
        .trim()
        .replace('٫', '.')
        .replace(',', '.')
        .replace(" ", "")
    if (normalized.isBlank()) return null
    return runCatching {
        normalized.toBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it > 0 }
    }.getOrNull()
}

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy، h:mm a", arabicLocale).format(Date(timestamp))

fun formatDay(timestamp: Long): String =
    SimpleDateFormat("EEE", arabicLocale).format(Date(timestamp))

fun fileSafeDate(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(timestamp))
