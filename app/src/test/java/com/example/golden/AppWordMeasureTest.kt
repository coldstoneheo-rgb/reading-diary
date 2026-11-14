package com.example.golden

import com.example.data.ocr.UnderlineDetector.Box
import com.example.data.ocr.UnderlinePreselect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 앱 어절 판정 측정의 글자 맞춤 · 줄 제외 후보. 글은 지어낸 견본이다. */
class AppWordMeasureTest {

    private fun sel(gold: String, engine: String, selected: List<Boolean>) =
        AppWordMeasure.tokensSelected(gold.split(" "), engine.split(" "), selected)

    @Test
    fun sameWords_selectionPassesThrough() {
        assertEquals(setOf(1, 2), sel("오늘 아침 바람이 차다", "오늘 아침 바람이 차다", listOf(false, true, true, false)))
    }

    @Test
    fun engineMergesTwoAnswerWords_bothCountAsSelected() {
        // 엔진이 «아침 바람이»를 한 어절로 붙이면 화면에서도 둘이 함께 선택된다.
        assertEquals(setOf(1, 2), sel("오늘 아침 바람이 차다", "오늘 아침바람이 차다", listOf(false, true, false)))
    }

    @Test
    fun engineSplitsAnAnswerWord_needsHalfOfItsLettersSelected() {
        // «바람이» = 엔진 «바» + «람이». 3글자 중 2글자가 선택되면 선택, 1글자만이면 아님.
        assertEquals(setOf(1), sel("아침 바람이", "아침 바 람이", listOf(false, false, true)))
        assertEquals(emptySet<Int>(), sel("아침 바람이", "아침 바 람이", listOf(false, true, false)))
    }

    @Test
    fun recognitionTypo_stillMapsByPosition() {
        assertEquals(setOf(1), sel("오늘 아침 바람", "오늘 아칩 바람", listOf(false, true, false)))
    }

    @Test
    fun answerWordWithNoEngineLetters_isNotSelected() {
        // 엔진이 마지막 어절을 통째로 놓쳤다.
        assertEquals(setOf(0), sel("오늘 아침 바람", "오늘 아침", listOf(true, false)))
    }

    @Test
    fun selectedEngineWordWithNoAnswerLetters_isCountedAsUnmatched() {
        // 같은 높이의 여백 글(«메모»)이 본문 줄에 묶여 선택됐다 — 정답 어절에는 안 붙지만 화면엔 선택됨으로 뜬다.
        val a = AppWordMeasure.align("오늘 아침".split(" "), "오늘 아침 메모".split(" "), listOf(false, true, true))
        assertEquals(setOf(1), a.tokens)
        assertEquals(1, a.unmatchedSelected)
        assertEquals(0, AppWordMeasure.align("오늘 아침".split(" "), "오늘 아침".split(" "), listOf(true, true)).unmatchedSelected)
    }

    @Test
    fun unsureAnswerLetter_matchesAnyLetter() {
        // 정답의 «확신 없는 글자»는 어느 글자와도 같다(GoldenSet.editDistance와 같은 규칙).
        assertEquals(setOf(1), sel("오늘 아${GoldenSet.UNSURE}", "오늘 아침", listOf(false, true)))
    }

    private fun line(top: Int, left: Int, right: Int, n: Int) =
        UnderlinePreselect.Line(Box(left, top, right, top + 40), List(n) { it })

    @Test
    fun filters_dropOnlyTheIntendedLines() {
        // 쪽 머리 짧은 줄(y 20) · 본문 4줄(간격 60) · 쪽 번호 짧은 줄(y 600)
        val lines = listOf(line(20, 900, 1100, 2)) + (0 until 4).map { line(150 + it * 60, 100, 1100, 8) } + line(600, 1000, 1060, 1)
        val h = 640
        fun dropped(name: String) = AppWordMeasure.FILTERS.first { it.name.startsWith(name) }.let { f -> lines.indices.filter { f.drop(lines, it, h) } }
        assertEquals(emptyList<Int>(), dropped("제외 없음"))
        assertEquals(listOf(0, 5), dropped("맨 위 · 맨 아래 줄 제외"))
        assertEquals(listOf(0, 5), dropped("맨 위 · 맨 아래 줄 중 폭"))
        assertEquals(listOf(0, 5), dropped("맨 위 · 맨 아래 줄 중 이웃"))
        assertEquals(listOf(0, 5), dropped("어절 2개 이하"))
        // 위 8% = y < 51.2 · 아래 8% = bottom > 588.8: 머리 줄(20..60)과 번호 줄(600..640)만.
        assertEquals(listOf(0, 5), dropped("쪽 높이 위 8%"))
        // 본문만 있는 쪽(머리 · 번호 없음)에서는 폭 · 간격 후보가 본문 줄을 빼지 않는다.
        val body = lines.subList(1, 5)
        assertTrue(AppWordMeasure.FILTERS.first { it.name.startsWith("맨 위 · 맨 아래 줄 중 폭") }.let { f -> body.indices.none { f.drop(body, it, h) } })
        assertTrue(AppWordMeasure.FILTERS.first { it.name.startsWith("맨 위 · 맨 아래 줄 중 이웃") }.let { f -> body.indices.none { f.drop(body, it, h) } })
    }
}
