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
 * `startDate`를 읽는 곳은 화면 표시뿐이라(통계 · 내보내기 · 백업은 문자열 그대로 옮긴다) 두 꼴이 섞여도 계산이 깨지지 않는다.
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

    /** 시각까지 적힌 값인가(옛 날짜만 있는 값 = false). */
    fun hasTime(startDate: String): Boolean = startDate.trim().length > DATE.length && parse(startDate) != null
}
