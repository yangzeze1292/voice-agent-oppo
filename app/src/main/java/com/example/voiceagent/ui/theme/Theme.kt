package com.example.voiceagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = TextLight,
    background = BgDark,
    surface = Surface,
    onSurface = TextLight
)

@Composable
fun VoiceAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
