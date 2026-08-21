package com.nuvio.app.core.ui

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

enum class AppTheme {
    KHAYIN,
    DARK_INDIGO,
    GOLD,
    JADE,
    ROSE_GOLD,
    ARCTIC_BLUE,
    GRAPHITE,
    CRIMSON,
    OCEAN,
    VIOLET,
    EMERALD,
    AMBER,
    ROSE,
    WHITE,
}

val AppTheme.labelRes: StringResource
    get() = when (this) {
        AppTheme.KHAYIN -> Res.string.theme_khayin
        AppTheme.DARK_INDIGO -> Res.string.theme_dark_indigo
        AppTheme.GOLD -> Res.string.theme_gold
        AppTheme.JADE -> Res.string.theme_jade
        AppTheme.ROSE_GOLD -> Res.string.theme_rose_gold
        AppTheme.ARCTIC_BLUE -> Res.string.theme_arctic_blue
        AppTheme.GRAPHITE -> Res.string.theme_graphite
        AppTheme.CRIMSON -> Res.string.theme_crimson
        AppTheme.OCEAN -> Res.string.theme_ocean
        AppTheme.VIOLET -> Res.string.theme_violet
        AppTheme.EMERALD -> Res.string.theme_emerald
        AppTheme.AMBER -> Res.string.theme_amber
        AppTheme.ROSE -> Res.string.theme_rose
        AppTheme.WHITE -> Res.string.theme_white
    }
