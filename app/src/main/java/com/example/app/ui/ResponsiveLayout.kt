package com.example.app.ui

import androidx.compose.ui.unit.Dp

// WindowWidthClass is now provided by the shared DesignSystem library.
typealias WindowWidthClass = com.workspace.design.WindowWidthClass

fun windowWidthClass(maxWidth: Dp): WindowWidthClass =
    com.workspace.design.windowWidthClass(maxWidth)
