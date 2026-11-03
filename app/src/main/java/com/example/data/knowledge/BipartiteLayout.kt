package com.example.data.knowledge

import kotlin.math.hypot

/** 이분 그래프의 두 열. 왼쪽 = 책, 오른쪽 = 함께 나온 단어. */
enum class GraphSide { BOOK, WORD }

/**
 * 그래프 노드 하나. [key]는 책이면 `bookId.toString()`, 단어면 단어 자체다.
 * 좌표는 [BipartiteLayout.layout]에 넘긴 width·height 기준이다.
 */
data class GraphNode(val key: String, val label: String, val side: GraphSide, val x: Float, val y: Float)

/** 책 ↔ 단어 엣지. 같은 책 안의 반복은 엣지가 아니다(ADR-003 Q1) — [SharedWord.bookIds]만 쓴다. */
data class GraphEdge(val bookId: Int, val word: String)

data class BipartiteGraph(
    val bookNodes: List<GraphNode>,
    val wordNodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    /** 상한 때문에 잘린 단어·책·엣지가 있으면 true. 화면이 "일부만 표시" 한 줄을 띄운다(숨김을 숨기지 않는다). */
    val truncated: Boolean
) {
    companion object {
        val EMPTY = BipartiteGraph(emptyList(), emptyList(), emptyList(), truncated = false)
    }
}

sealed interface GraphHit {
    data class Word(val word: String) : GraphHit
    data class Edge(val bookId: Int, val word: String) : GraphHit
}

/**
 * 기억 서랍 «그림» 보기의 좌표 계산(ADR-003 Q3 2단계, STAGE2-PLAN ①). Compose에 의존하지 않는 순수 함수라 JVM 테스트가 가능하다.
 * 힘 기반·난수를 쓰지 않는다 — 같은 입력이면 같은 좌표(스크린샷 기준이 흔들리지 않는다).
 */
object BipartiteLayout {
    const val MAX_BOOKS = 10
    const val MAX_WORDS = 12
    const val MAX_EDGES = 40

    /**
     * 규칙: 단어는 [sharedWords] 순서(KeywordLinks.build 정렬)로 앞 [MAX_WORDS]개 → 그 단어들이 걸린 책을
     * 엣지 수 내림차순 → 제목 오름차순 → id 오름차순으로 앞 [MAX_BOOKS]권 → 엣지는 단어 순서(같은 단어 안에서는 책 id 순)로 [MAX_EDGES]개까지.
     * 엣지가 하나도 남지 않은 노드는 그리지 않는다. 상한 때문에 무엇이든 잘렸으면 [BipartiteGraph.truncated].
     * 각 열은 `padding..height-padding`에 균등 간격(1개면 세로 가운데), 책 열 x = padding, 단어 열 x = width - padding.
     * [titleById]에 없는 책(지워진 책)은 그리지 않는다.
     */
    fun layout(
        sharedWords: List<SharedWord>,
        titleById: Map<Int, String>,
        width: Float,
        height: Float,
        padding: Float = 24f
    ): BipartiteGraph {
        if (sharedWords.isEmpty()) return BipartiteGraph.EMPTY
        val words = sharedWords.take(MAX_WORDS)

        val edgeCountByBook = HashMap<Int, Int>()
        for (w in words) for (id in w.bookIds) {
            if (id in titleById) edgeCountByBook[id] = (edgeCountByBook[id] ?: 0) + 1
        }
        val rankedBooks = edgeCountByBook.keys.sortedWith(
            compareByDescending<Int> { edgeCountByBook.getValue(it) }
                .thenBy { titleById.getValue(it) }
                .thenBy { it }
        )
        val keptBooks = rankedBooks.take(MAX_BOOKS).toSet()

        val candidateEdges = words.flatMap { w ->
            w.bookIds.filter { it in keptBooks }.sorted().map { GraphEdge(it, w.word) }
        }
        val edges = candidateEdges.take(MAX_EDGES)

        val wordsWithEdges = edges.map { it.word }.toSet()
        val booksWithEdges = edges.map { it.bookId }.toSet()
        val shownWords = words.filter { it.word in wordsWithEdges }
        val shownBooks = rankedBooks.filter { it in booksWithEdges }

        // 상한 때문에 잃은 것만 센다. 책·엣지 상한으로 엣지를 모두 잃은 단어는 앞의 두 조건이 이미 잡는다.
        // 지워진 책(titleById에 없음) 때문에 빠진 것은 "일부만 표시"가 아니다.
        val truncated = sharedWords.size > words.size ||
            rankedBooks.size > keptBooks.size ||
            candidateEdges.size > edges.size

        val bookNodes = shownBooks.mapIndexed { i, id ->
            GraphNode(id.toString(), titleById.getValue(id), GraphSide.BOOK, padding, columnY(i, shownBooks.size, height, padding))
        }
        val wordNodes = shownWords.mapIndexed { i, w ->
            GraphNode(w.word, w.word, GraphSide.WORD, width - padding, columnY(i, shownWords.size, height, padding))
        }
        return BipartiteGraph(bookNodes, wordNodes, edges, truncated)
    }

    /**
     * 탭 좌표 → 단어 노드(중심에서 [nodeRadius] 안, 가장 가까운 것) 우선,
     * 아니면 선분까지 거리 ≤ [edgeTolerance]인 엣지 중 가장 가까운 것(같으면 앞의 엣지). 없으면 null.
     * 책 노드는 결과가 아니다 — 카드는 단어 기준으로 열린다.
     */
    fun hitTest(graph: BipartiteGraph, x: Float, y: Float, nodeRadius: Float, edgeTolerance: Float): GraphHit? {
        graph.wordNodes
            .map { it to hypot(it.x - x, it.y - y) }
            .filter { it.second <= nodeRadius }
            .minByOrNull { it.second }
            ?.let { return GraphHit.Word(it.first.key) }

        val bookByKey = graph.bookNodes.associateBy { it.key }
        val wordByKey = graph.wordNodes.associateBy { it.key }
        var best: GraphEdge? = null
        var bestDistance = Float.MAX_VALUE
        for (e in graph.edges) {
            val b = bookByKey[e.bookId.toString()] ?: continue
            val w = wordByKey[e.word] ?: continue
            val d = distanceToSegment(x, y, b.x, b.y, w.x, w.y)
            if (d <= edgeTolerance && d < bestDistance) {
                best = e
                bestDistance = d
            }
        }
        return best?.let { GraphHit.Edge(it.bookId, it.word) }
    }

    private fun columnY(index: Int, count: Int, height: Float, padding: Float): Float =
        if (count <= 1) height / 2f
        else padding + index * (height - 2f * padding) / (count - 1)

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
