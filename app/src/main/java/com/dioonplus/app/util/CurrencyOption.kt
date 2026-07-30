package com.dioonplus.app.util

/** Supported display currencies. Changing this selection never converts stored debt values. */
enum class CurrencyOption(
    val code: String,
    val arabicName: String,
    val symbol: String,
    val fractionDigits: Int,
) {
    JOD("JOD", "الدينار الأردني", "د.أ", 3),
    SAR("SAR", "الريال السعودي", "ر.س", 2),
    YER("YER", "الريال اليمني", "ر.ي", 2),
    EGP("EGP", "الجنيه المصري", "ج.م", 2),
    USD("USD", "الدولار الأمريكي", "$", 2),
    EUR("EUR", "اليورو", "€", 2),
    AED("AED", "الدرهم الإماراتي", "د.إ", 2),
    KWD("KWD", "الدينار الكويتي", "د.ك", 3),
    QAR("QAR", "الريال القطري", "ر.ق", 2),
    OMR("OMR", "الريال العُماني", "ر.ع", 3),
    BHD("BHD", "الدينار البحريني", "د.ب", 3),
    IQD("IQD", "الدينار العراقي", "د.ع", 3),
    SYP("SYP", "الليرة السورية", "ل.س", 2),
    LBP("LBP", "الليرة اللبنانية", "ل.ل", 2),
    SDG("SDG", "الجنيه السوداني", "ج.س", 2),
    LYD("LYD", "الدينار الليبي", "د.ل", 3),
    TND("TND", "الدينار التونسي", "د.ت", 3),
    DZD("DZD", "الدينار الجزائري", "د.ج", 2),
    MAD("MAD", "الدرهم المغربي", "د.م", 2),
    TRY("TRY", "الليرة التركية", "₺", 2),
    GBP("GBP", "الجنيه الإسترليني", "£", 2),
    SEK("SEK", "الكرونة السويدية", "kr", 2),
    ;

    val displayName: String get() = "$arabicName ($code)"

    companion object {
        fun fromCode(code: String?): CurrencyOption =
            entries.firstOrNull { it.code == code } ?: JOD
    }
}

object CurrencySettings {
    @Volatile
    var current: CurrencyOption = CurrencyOption.JOD
}
