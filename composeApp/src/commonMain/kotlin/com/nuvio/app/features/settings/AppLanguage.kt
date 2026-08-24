package com.nuvio.app.features.settings

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.lang_burmese
import nuvio.composeapp.generated.resources.lang_chinese
import nuvio.composeapp.generated.resources.lang_chinese_simplified
import nuvio.composeapp.generated.resources.lang_chinese_traditional
import nuvio.composeapp.generated.resources.lang_english
import nuvio.composeapp.generated.resources.settings_appearance_app_language_device
import org.jetbrains.compose.resources.StringResource

enum class AppLanguage(
    val code: String,
    val labelRes: StringResource,
) {
    DEVICE("device", Res.string.settings_appearance_app_language_device),
    BURMESE("my", Res.string.lang_burmese),
    ENGLISH("en", Res.string.lang_english),
    CHINESE_SIMPLIFIED("zh-CN", Res.string.lang_chinese_simplified),
    CHINESE_TRADITIONAL("zh-TW", Res.string.lang_chinese_traditional),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) || (code?.startsWith("zh", ignoreCase = true) == true && it.code.startsWith("zh")) } ?: DEVICE
    }
}
