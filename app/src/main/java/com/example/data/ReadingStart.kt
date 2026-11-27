package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «읽기 시작»(HO-2026-1127 조건 4). 순수 함수 — JVM 테스트.
 *
 * 시작 시각은 Room 스키마를 바꾸지 않고 기존 `Book.startDate` 문자열에 담는다:
 * - 새 값 = `yyyy-MM-dd HH:mm`(읽기 시작을 누른 시각 · 사용자가 고친 시각)
 * - 옛 값 = `yyyy-MM-dd`(이전 앱이 진척도를 처음 움직인 날에 적은 값) — 시각 모름으로 그대로 읽는다.
 * `startDate`는 상세 화면 표시 · 백업(DB 통째) · [isStarted]를 거친 서재 상태([shelfStatus]) 외에는 읽지 않는다 — 두 꼴이 섞여도 계산이 깨지지 않는다(비었는지만 본다).
 *
 * 옮김 규칙(이미 있는 책): 아래 넷 중 하나라도 맞으면 «이미 읽기 시작한 책»으로 본다 — 사용자가 다시 누를 일이 없다.
 * 시작 날짜가 있음 · 완독 · 읽은 쪽 > 0 · 기록이 1개 이상. 넷 다 아니면(«읽고 있는 책»으로 등록만 하고 손대지 않은 책 포함)
 * 상세 화면이 «읽기 시작» 버튼을 먼저 보인다.
 */
object ReadingStart {

    private const val DATE_TIME = "yyyy-MM-dd HH:mm"
    private const val DATE = "yyyy-MM-dd"

    fun isStarted(book: Book, diaryCount: Int): Boolean =
        book.startDate.isNotBlank() || book.status == "COMPLETED" || book.currentPage > 0 || diaryCount > 0

    /**
     * 서재 · 편집 · 통계가 보이는 상태. 기준은 상세의 [isStarted]다 — 두 방향 다 맞춘다.
     * - 상태가 «읽는 중»이어도 시작하지 않은 책(예: #89 전에 «읽고 있는 책»으로 등록만 한 책) → «읽을 책».
     * - 상태가 «읽을 책»이어도 시작한 책(기록이 있음 · 시작 값이 있음 등 — 상세가 진척도 · 기록을 연다) → «읽는 중».
     * 그래야 서재와 상세가 같은 책을 다르게 말하지 않는다. DB 값은 바꾸지 않는다(사용자가 고쳐 저장하거나 «읽기 시작»을 누를 때 바뀐다).
     */
    fun shelfStatus(book: Book, diaryCount: Int): String = when {
        book.status == "READING" && !isStarted(book, diaryCount) -> "TO_READ"
        book.status == "TO_READ" && isStarted(book, diaryCount) -> "READING"
        else -> book.status
    }

    /** 저장할 시작 값(분 단위). */
    fun format(millis: Long): String = SimpleDateFormat(DATE_TIME, Locale.US).format(Date(millis))

    /** 저장된 값 → 시각(밀리초). 옛 날짜만 있는 값은 그날 0시. 비었거나 읽을 수 없으면 null. */
    fun parse(startDate: String): Long? {
        val s = startDate.trim()
        if (s.isEmpty()) return null
        for (pattern in listOf(DATE_TIME, DATE)) {
            val f = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = java.text.ParsePosition(0)
            val d = f.parse(s, pos)
            if (d != null && pos.index == s.length) return d.time
        }
        return null
    }

    /**
     * 시작으로 고를 수 있는 가장 늦은 시각: 지금 · 완독일 그날 끝(23:59:59.999) 중 이른 것.
     * 완독일이 없거나 읽을 수 없으면 지금.
     */
    fun latestAllowed(nowMillis: Long, endDate: String?): Long {
        val end = endDate?.let { parse(it) } ?: return nowMillis
        // 그날 끝 = 다음 날 0시 − 1ms(달력으로 더한다 — 일광절약시간이 있는 날은 23 · 25시간이다).
        val endOfDay = if (hasTime(endDate)) end else java.util.Calendar.getInstance().apply { timeInMillis = end; add(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis - 1
        return minOf(nowMillis, endOfDay)
    }

    /**
     * 상세 화면의 기간 줄. 시작한 책(옮김 규칙상 시작 · [isStarted])에는 시작 값이 비어도
     * «아직 읽지 않은 도서»나 빈 시작 자리(«독서 기간:  ~ …»)를 보이지 않는다 — «시작 기록 없음»으로 적고 ✎로 채운다.
     */
    fun periodLabel(book: Book, started: Boolean): String {
        val start = book.startDate.trim()
        return when {
            !started -> "아직 읽지 않은 도서"
            book.status == "COMPLETED" && start.isNotEmpty() -> "독서 기간: $start ~ ${book.endDate ?: "완료"}"
            book.status == "COMPLETED" -> if (book.endDate.isNullOrBlank()) "완독 · 시작 기록 없음" else "완독: ${book.endDate} · 시작 기록 없음"
            start.isNotEmpty() -> "독서 시작: $start"
            else -> "읽는 중 · 시작 기록 없음"
        }
    }

    /** 시각까지 적힌 값인가(옛 날짜만 있는 값 = false). */
    fun hasTime(startDate: String): Boolean = startDate.trim().length > DATE.length && parse(startDate) != null
}
