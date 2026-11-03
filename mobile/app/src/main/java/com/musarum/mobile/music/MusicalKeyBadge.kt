package com.musarum.mobile.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge con la tonalidad musical: "C#m · 8A" (modo normal) o solo "8A"
 * (modo compact, para filas). Ported del Musarum viejo.
 */
@Composable
fun MusicalKeyBadge(
    initialKey: String?,
    camelotKey: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (initialKey.isNullOrBlank() && camelotKey.isNullOrBlank()) return

    val display = when {
        compact && !camelotKey.isNullOrBlank() -> camelotKey
        compact && !initialKey.isNullOrBlank() -> initialKey
        !initialKey.isNullOrBlank() && !camelotKey.isNullOrBlank() -> "$initialKey · $camelotKey"
        !initialKey.isNullOrBlank() -> initialKey
        else -> camelotKey ?: ""
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = display,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 9.sp else 11.sp
        )
    }
}
