package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ocr.UnderlinePreselect

/**
 * 밑줄 «미리 선택»을 결과 화면에 보여 주고 사용자가 고치게 한다(SPIKE §4 PR 3).
 *
 * - 미리 선택된 어절이 하나도 없으면(값 없음 · 0개) **아무것도 그리지 않는다** — 기존 화면과 같다.
 * - 미리 선택된 어절이 있는 엔진 줄만 줄 단위로 어절 칩을 보여 준다(그 줄의 다른 어절도 칩으로 — 놓친 어절을 켤 수 있게).
 * - 칩을 눌러 켜고 끄는 것은 [chosen]만 바꾼다. 칩을 고친 뒤 편집창 글은 사용자가 [onApply] 버튼을 눌렀을 때만 바뀐다(자동 저장 없음).
 *   처음 켜진 어절로 편집창을 채우는 것은 부르는 쪽(OcrDiaryScreen)이 한다 — 이 패널은 편집창을 모른다.
 *
 * 상태는 부르는 쪽이 들고 있다(stateless — 스크린샷 · 단위 테스트).
 * @param chosen [words]와 같은 길이. 지금 켜진 어절.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnderlinePreselectPanel(
    words: List<UnderlinePreselect.Word>?,
    chosen: List<Boolean>,
    onToggle: (Int) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (words == null || words.none { it.selected } || chosen.size != words.size) return
    val shownLines = words.filter { it.selected }.map { it.lineIndex }.distinct()
    Card(
        modifier = modifier.fillMaxWidth().testTag("underline_preselect_panel"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "밑줄로 보이는 어절을 골라 두었어요 · 지금 ${chosen.count { it }}개 선택",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "어절을 눌러 켜고 끌 수 있어요. 버튼을 누르기 전에는 위 문장이 바뀌지 않아요.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (line in shownLines) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    words.forEachIndexed { i, w ->
                        if (w.lineIndex != line) return@forEachIndexed
                        FilterChip(
                            selected = chosen[i],
                            onClick = { onToggle(i) },
                            label = { Text(w.text, fontSize = 14.sp) },
                            // 켜짐을 색만이 아니라 체크 표시로도 보인다.
                            leadingIcon = if (chosen[i]) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.testTag("preselect_word_$i")
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onApply,
                enabled = chosen.any { it },
                modifier = Modifier.fillMaxWidth().testTag("preselect_apply_button")
            ) {
                Text("고른 어절로 문장 바꾸기", fontSize = 14.sp)
            }
        }
    }
}

/** 켜진 어절을 엔진 줄 순서 · 줄 안 순서대로 공백 하나로 잇는다. */
fun preselectedText(words: List<UnderlinePreselect.Word>, chosen: List<Boolean>): String =
    words.filterIndexed { i, _ -> chosen.getOrElse(i) { false } }.joinToString(" ") { it.text.trim() }.trim()
