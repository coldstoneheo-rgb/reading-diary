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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    modifier: Modifier = Modifier,
    /** 사용자가 기본값에서 바꾼 줄 이음 자리([lineJoins]의 값). */
    flippedJoins: Set<Int> = emptySet(),
    onToggleJoin: (Int) -> Unit = {}
) {
    if (words == null || words.none { it.selected } || chosen.size != words.size) return
    val shownLines = words.filter { it.selected }.map { it.lineIndex }.distinct()
    val joins = lineJoins(words, chosen).toSet()
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
                        if (i in joins) {
                            // 줄 이음 자리: 앞 줄 끝 어절과 이 어절을 붙일지(기본) 띄울지. 한 번 누르면 바뀐다.
                            val glued = isGlued(words, i, flippedJoins)
                            FilterChip(
                                selected = glued,
                                onClick = { onToggleJoin(i) },
                                label = { Text(if (glued) "⌒ 붙임" else "␣ 띄움", fontSize = 14.sp) },
                                modifier = Modifier
                                    .testTag("preselect_join_$i")
                                    .semantics {
                                        contentDescription = "앞 줄 끝 «${words[i - 1].text.trim()}»과 «${w.text.trim()}» 사이"
                                        stateDescription = if (glued) "붙임" else "띄움"
                                    }
                            )
                        }
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
            // 버튼을 누르면 편집창에 들어갈 글 그대로(줄 이음 반영).
            Text(
                text = preselectedText(words, chosen, flippedJoins),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("preselect_preview")
            )
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

/**
 * 줄 이음 자리: 켜진 어절 i와 바로 앞 어절 i−1이 다른 엔진 줄에 있는 곳(앞 줄 마지막 어절 → 다음 줄 첫 어절, 둘 다 켜짐).
 * 값 = 뒤 어절 순번 i.
 */
fun lineJoins(words: List<UnderlinePreselect.Word>, chosen: List<Boolean>): List<Int> =
    (1 until words.size).filter { i ->
        chosen.getOrElse(i) { false } && chosen.getOrElse(i - 1) { false } && words[i].lineIndex != words[i - 1].lineIndex
    }

private val CLOSERS = "\"'”’)]」』》〉"
private val SENTENCE_END = ".?!…。,;:·"

/**
 * 줄 이음 기본값(ADR-002 Q4 개정 · 2026-11-28): 책은 낱말 중간에서 줄을 바꾸는 일이 더 많아(기준 자료 105곳 중 67) 기본은 붙임.
 * 앞 줄 끝 어절이 문장부호(닫는 따옴표 · 괄호 뒤 포함)로 끝나면 띄움.
 */
fun defaultGlued(prevWord: String): Boolean {
    val t = prevWord.trim().trimEnd { it in CLOSERS }
    return t.isNotEmpty() && t.last() !in SENTENCE_END
}

/** 자리 i가 지금 붙는지: 기본값을 사용자가 바꿨으면([flipped]) 뒤집는다. */
fun isGlued(words: List<UnderlinePreselect.Word>, i: Int, flipped: Set<Int>): Boolean =
    defaultGlued(words[i - 1].text) != (i in flipped)

/**
 * 켜진 어절을 엔진 줄 순서 · 줄 안 순서대로 잇는다. 같은 줄 안 · 떨어진 조각 사이는 공백 하나,
 * 줄 이음 자리([lineJoins])는 [isGlued]이면 공백 없이 붙인다. 그 밖의 띄어쓰기는 고치지 않는다(ADR-002 Q4).
 */
fun preselectedText(words: List<UnderlinePreselect.Word>, chosen: List<Boolean>, flipped: Set<Int> = emptySet()): String {
    val joins = lineJoins(words, chosen).toSet()
    val sb = StringBuilder()
    words.forEachIndexed { i, w ->
        if (!chosen.getOrElse(i) { false }) return@forEachIndexed
        val text = w.text.trim()
        if (sb.isNotEmpty() && !(i in joins && isGlued(words, i, flipped))) sb.append(' ')
        sb.append(text)
    }
    return sb.toString().trim()
}
