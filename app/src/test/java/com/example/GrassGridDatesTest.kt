package com.example

import com.example.ui.screens.grassGridDates
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 통계 «독서 잔디»: 어느 요일이든 오늘이 격자 안(마지막 열)에 들고, 열은 일요일 ~ 토요일 15주다. */
class GrassGridDatesTest {

  private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

  // grassGridDates는 기본 로캘로 날짜 글자를 만든다 — 기대값(서기 · 아라비아 숫자)과 맞게 시험 동안 고정한다.
  private var savedLocale: Locale? = null
  @Before fun fixLocale() { savedLocale = Locale.getDefault(); Locale.setDefault(Locale.US) }
  @After fun restoreLocale() { savedLocale?.let { Locale.setDefault(it) } }

  private fun day(s: String): Calendar = Calendar.getInstance().apply { time = fmt.parse(s)!!; set(Calendar.HOUR_OF_DAY, 15) }

  @Test
  fun todayIsInTheLastColumn_onEveryWeekday() {
    // 2026-11-22(일) ~ 2026-11-28(토)
    for (d in 22..28) {
      val today = "2026-11-%02d".format(d)
      val grid = grassGridDates(day(today))
      assertEquals(15, grid.size)
      grid.forEach { assertEquals(7, it.size) }
      assertTrue("$today 이(가) 마지막 열에 없다: ${grid.last()}", today in grid.last())
      assertEquals("마지막 열은 그 주 일요일에서 시작", "2026-11-22", grid.last().first())
      assertEquals("첫 열은 14주 전 일요일", "2026-08-16", grid.first().first())
    }
  }

  @Test
  fun columnsAreConsecutiveDays() {
    val flat = grassGridDates(day("2026-11-25")).flatten()
    assertEquals(105, flat.size)
    assertEquals(105, flat.toSet().size)
    val c = day(flat.first())
    for (s in flat) {
      assertEquals(fmt.format(c.time), s)
      c.add(Calendar.DAY_OF_YEAR, 1)
    }
  }
}
