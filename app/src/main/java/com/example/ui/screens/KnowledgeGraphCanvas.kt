package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.knowledge.BipartiteGraph
import com.example.data.knowledge.BipartiteLayout
import com.example.data.knowledge.GraphEdge
import com.example.data.knowledge.GraphHit
import com.example.data.knowledge.SharedWord

/** 그림 보기의 높이. 상한 12개 단어가 들어가도 라벨이 겹치지 않는 높이. */
internal val GraphHeight = 280.dp

/** 좌우 라벨 칸 폭. 책 제목은 왼쪽 칸, 단어는 오른쪽 칸에 쓰고 그 사이에 선을 긋는다. */
internal val GraphLabelGutter = 104.dp

/** 노드 열이 캔버스 위·아래·라벨 칸에서 떨어진 거리(BipartiteLayout의 padding). */
internal val GraphPadding = 16.dp

private val NodeRadius = 6.dp
private val TouchRadius = 24.dp
private val EdgeTolerance = 12.dp

/**
 * [sharedWords]를 캔버스 좌표의 그래프로. 좌표 규칙은 [BipartiteLayout]이 정하고, 여기서는 라벨 칸만큼 오른쪽으로 민다
 * — 캔버스·탭 판정이 같은 좌표를 쓰게 하기 위해서다. 순수 계산이라 `remember(sharedWords, size)`로 부른다.
 */
internal fun knowledgeGraphFor(
    sharedWords: List<SharedWord>,
    titleById: Map<Int, String>,
    widthPx: Float,
    heightPx: Float,
    density: Density
): BipartiteGraph {
    val gutter = with(density) { GraphLabelGutter.toPx() }
    val padding = with(density) { GraphPadding.toPx() }
    val inner = BipartiteLayout.layout(sharedWords, titleById, (widthPx - 2 * gutter).coerceAtLeast(2 * padding), heightPx, padding)
    return inner.copy(
        bookNodes = inner.bookNodes.map { it.copy(x = it.x + gutter) },
        wordNodes = inner.wordNodes.map { it.copy(x = it.x + gutter) }
    )
}

/**
 * 책(왼쪽) ｜ 함께 나온 단어(오른쪽) 이분 그래프(ADR-003 Q3 2단계). 좌표는 [graph]가 이미 캔버스 좌표로 갖고 있다.
 * 단어 노드나 선을 누르면 [onHit] — 화면은 그 단어의 원문 카드를 연다. [selected]와 이어진 선은 굵게 그린다.
 */
@Composable
fun KnowledgeGraphCanvas(
    graph: BipartiteGraph,
    onHit: (GraphHit) -> Unit,
    modifier: Modifier = Modifier,
    selected: GraphHit? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val bookStyle = labelStyle.copy(color = colors.onSurface.copy(alpha = 0.8f))
    val wordStyle = labelStyle.copy(color = colors.primary, fontWeight = FontWeight.Bold)
    val edgeColor = colors.outline.copy(alpha = 0.45f)
    val highlightColor = colors.primary

    Canvas(
        modifier = modifier
            .testTag("knowledgedrawer_graph")
            .semantics {
                contentDescription = "책 ${graph.bookNodes.size}권과 함께 나온 단어 ${graph.wordNodes.size}개를 잇는 그림"
            }
            .pointerInput(graph) {
                detectTapGestures { tap ->
                    BipartiteLayout.hitTest(graph, tap.x, tap.y, TouchRadius.toPx(), EdgeTolerance.toPx())?.let(onHit)
                }
            }
    ) {
        val bookByKey = graph.bookNodes.associateBy { it.key }
        val wordByKey = graph.wordNodes.associateBy { it.key }
        val thin = 1.5.dp.toPx()
        val thick = 3.dp.toPx()
        val radius = NodeRadius.toPx()
        val gap = 6.dp.toPx()

        // 강조 선은 위에 오도록 나중에 그린다.
        val (strong, weak) = graph.edges.partition { isHighlighted(it, selected) }
        for ((edges, color, width) in listOf(Triple(weak, edgeColor, thin), Triple(strong, highlightColor, thick))) {
            for (e in edges) {
                val b = bookByKey[e.bookId.toString()] ?: continue
                val w = wordByKey[e.word] ?: continue
                drawLine(color, Offset(b.x, b.y), Offset(w.x, w.y), strokeWidth = width)
            }
        }

        for (node in graph.bookNodes) {
            drawCircle(colors.secondary, radius, Offset(node.x, node.y))
            val maxWidth = (node.x - radius - gap).toInt().coerceAtLeast(1)
            drawLabel(textMeasurer, node.label, bookStyle, maxWidth, rightEdge = node.x - radius - gap, centerY = node.y)
        }
        for (node in graph.wordNodes) {
            val isSelected = (selected as? GraphHit.Word)?.word == node.key || (selected as? GraphHit.Edge)?.word == node.key
            drawCircle(colors.primary, if (isSelected) radius * 1.5f else radius, Offset(node.x, node.y))
            val left = node.x + radius + gap
            val maxWidth = (size.width - left).toInt().coerceAtLeast(1)
            drawLabel(textMeasurer, "#${node.label}", wordStyle, maxWidth, leftEdge = left, centerY = node.y)
        }
    }
}

private fun isHighlighted(edge: GraphEdge, selected: GraphHit?): Boolean = when (selected) {
    is GraphHit.Word -> edge.word == selected.word
    is GraphHit.Edge -> edge.word == selected.word && edge.bookId == selected.bookId
    null -> false
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    centerY: Float,
    leftEdge: Float? = null,
    rightEdge: Float? = null
) {
    val layout = textMeasurer.measure(
        AnnotatedString(text),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth)
    )
    val x = leftEdge ?: ((rightEdge ?: 0f) - layout.size.width)
    drawText(layout, topLeft = Offset(x, centerY - layout.size.height / 2f))
}
