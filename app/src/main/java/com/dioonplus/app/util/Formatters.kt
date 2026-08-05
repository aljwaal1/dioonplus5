package com.dioonplus.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val arabicLocale = Locale("ar", "JO")
private val englishNumberLocale = Locale.US
private const val LTR_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'
const val STORAGE_FRACTION_DIGITS = 3

fun formatMoney(cents: Long, includeSign: Boolean = false): String {
    val currency = CurrencySettings.current
    val value = BigDecimal.valueOf(cents, STORAGE_FRACTION_DIGITS).abs()
        .setScale(currency.fractionDigits, RoundingMode.HALF_UP)
    val formatter = NumberFormat.getNumberInstance(englishNumberLocale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = currency.fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
    val sign = when {
        !includeSign -> ""
        cents > 0 -> "+"
        cents < 0 -> "−"
        else -> ""
    }
    return "$LTR_ISOLATE$sign${formatter.format(value)}$POP_DIRECTIONAL_ISOLATE ${currency.symbol}"
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
            .setScale(CurrencySettings.current.fractionDigits, RoundingMode.HALF_UP)
            .movePointRight(STORAGE_FRACTION_DIGITS)
            .longValueExact()
            .takeIf { it > 0 }
    }.getOrNull()
}

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy، h:mm a", arabicLocale)
        .format(Date(timestamp))
        .toEnglishDigits()

fun formatDay(timestamp: Long): String =
    SimpleDateFormat("EEE", arabicLocale).format(Date(timestamp))

fun String.toEnglishDigits(): String = buildString(length) {
    for (character in this@toEnglishDigits) {
        append(
            when (character) {
                in '٠'..'٩' -> '0' + (character - '٠')
                in '۰'..'۹' -> '0' + (character - '۰')
                else -> character
            },
        )
    }
}

fun fileSafeDate(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(timestamp))
