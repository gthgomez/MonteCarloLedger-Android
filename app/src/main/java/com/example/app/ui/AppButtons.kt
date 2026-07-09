package com.example.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Buttons are now provided by the shared DesignSystem library.
// Re-export via wrapper functions so same-package callers still work.

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = com.workspace.design.AppPrimaryButton(text, onClick, modifier)

@Composable
fun AppNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = com.workspace.design.AppNeutralButton(text, onClick, modifier)

@Composable
fun AppSuccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = com.workspace.design.AppSuccessButton(text, onClick, modifier)

@Composable
fun AppDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = com.workspace.design.AppDestructiveButton(text, onClick, modifier)
