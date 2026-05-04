package com.example.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded
}

fun windowWidthClass(maxWidth: Dp): WindowWidthClass {
    return when {
        maxWidth < 600.dp -> WindowWidthClass.Compact
        maxWidth < 840.dp -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
}
