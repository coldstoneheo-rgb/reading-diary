package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

/**
 * 표지가 없거나 예전 스톡 사진이던 책의 로컬 표지(ADR-003-2). 제목 첫 글자 + 제목으로 고른 테마 색. 네트워크 0.
 */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceVariant to scheme.onSurfaceVariant
    )
    val (background, content) = palette[placeholderColorIndex(title, palette.size)]
    Box(
        modifier = modifier.background(background).testTag("cover_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = placeholderInitial(title),
            color = content,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

/** 같은 제목이면 항상 같은 색. 앞뒤 공백은 무시한다. */
fun placeholderColorIndex(title: String, paletteSize: Int): Int {
    require(paletteSize > 0) { "paletteSize must be positive" }
    return Math.floorMod(title.trim().hashCode(), paletteSize)
}

/** 제목의 첫 글자(서로게이트 쌍 포함 1개 코드포인트). 빈 제목이면 "?". */
fun placeholderInitial(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
