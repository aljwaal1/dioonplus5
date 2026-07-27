package com.dioonplus.app.util

/** Supported display currencies. Changing this selection never converts stored debt values. */
enum class CurrencyOption(
    val code: String,
    val arabicName: String,
    val symbol: String,
) {
    JOD("JOD", "الدينار الأردني", "د.أ"),
    SAR("SAR", "الريال السعودي", "ر.س"),
    YER("YER", "الريال اليمني", "ر.ي"),
    EGP("EGP", "الجنيه المصري", "ج.م"),
    USD("USD", "الدولار الأمريكي", "$"),
    EUR("EUR", "اليورو", "€"),
    AED("AED", "الدرهم الإماراتي", "د.إ"),
    KWD("KWD", "الدينار الكويتي", "د.ك"),
    QAR("QAR", "الريال القطري", "ر.ق"),
    OMR("OMR", "الريال العُماني", "ر.ع"),
    BHD("BHD", "الدينار البحريني", "د.ب"),
    IQD("IQD", "الدينار العراقي", "د.ع"),
    SYP("SYP", "الليرة السورية", "ل.س"),
    LBP("LBP", "الليرة اللبنانية", "ل.ل"),
    SDG("SDG", "الجنيه السوداني", "ج.س"),
    LYD("LYD", "الدينار الليبي", "د.ل"),
    TND("TND", "الدينار التونسي", "د.ت"),
    DZD("DZD", "الدينار الجزائري", "د.ج"),
    MAD("MAD", "الدرهم المغربي", "د.م"),
    TRY("TRY", "الليرة التركية", "₺"),
    GBP("GBP", "الجنيه الإسترليني", "£"),
    SEK("SEK", "الكرونة السويدية", "kr"),
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
