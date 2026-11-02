package com.example

import com.example.data.Book
import com.example.data.displayableCoverUrl
import com.example.data.isStockCover
import com.example.ui.components.placeholderColorIndex
import com.example.ui.components.placeholderInitial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADR-003-2: 표지 없는 책에 스톡 사진(Unsplash)을 불러오지 않는다. 기존 설치의 DB에 저장된 스톡 URL도
 * 표시 시점에 걸러 로컬 플레이스홀더로 바꾼다(DB 값은 그대로).
 */
class CoverFallbackTest {

  private val storedStock = "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400"
  private val naver = "https://shopping-phinf.pstatic.net/main_1234567/1234567.jpg"
  private val google = "http://books.google.com/books/content?id=abc&printsec=frontcover&img=1"

  private fun book(coverUrl: String, title: String = "어린 왕자") =
    Book(title = title, author = "", totalPages = 100, coverUrl = coverUrl, bookcaseId = 1, status = "TO_READ")

  @Test
  fun cleanCoverUrl_isEmptyForMissingOrStockCovers() {
    assertEquals("", book("").cleanCoverUrl)
    assertEquals("", book("   ").cleanCoverUrl)
    assertEquals("", book(storedStock).cleanCoverUrl)
    assertEquals("", book("content://not-a-network-cover").cleanCoverUrl)
  }

  @Test
  fun cleanCoverUrl_keepsSearchResultCovers() {
    assertEquals(naver, book(naver).cleanCoverUrl)
    assertEquals(google, book(google).cleanCoverUrl)
  }

  @Test
  fun stockCoverDetection() {
    assertTrue(isStockCover(storedStock))
    assertFalse(isStockCover(naver))
    assertFalse(isStockCover(""))
    assertEquals("", displayableCoverUrl(storedStock))
  }

  @Test
  fun placeholderColorIndex_isDeterministicAndInRange() {
    val titles = listOf("", " ", "어린 왕자", "진달래꽃", "하늘과 바람과 별과 시", "A", "😀 이모지")
    for (t in titles) {
      val i = placeholderColorIndex(t, 4)
      assertTrue("$t -> $i", i in 0 until 4)
      assertEquals(i, placeholderColorIndex(t, 4))
    }
    assertEquals(placeholderColorIndex("진달래꽃", 4), placeholderColorIndex("  진달래꽃 ", 4))
  }

  @Test
  fun placeholderInitial_takesFirstCodePoint() {
    assertEquals("진", placeholderInitial("진달래꽃"))
    assertEquals("A", placeholderInitial(" apple"))
    assertEquals("😀", placeholderInitial("😀 이모지"))
    assertEquals("?", placeholderInitial("  "))
  }

  /** 폴백을 지운 뒤에도 화면 문구가 «자동 매칭되는 표지»를 약속하지 않게(ADR-003 정직한 문구). */
  @Test
  fun noStockCoverPromiseInProductionCopy() {
    val forbidden = listOf("자동 매칭", "명화 디자인")
    val offenders = File("src/main").walkTopDown()
      .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
      .flatMap { f -> forbidden.filter { f.readText().contains(it) }.map { "${f.path}: \"$it\"" } }
      .toList()
    assertTrue("stale stock-cover copy found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
  }

  /** STAGE2-PLAN ④ 완료 조건: 스톡 사진 URL 리터럴 0, 판정용 호스트 상수 1곳. */
  @Test
  fun noStockPhotoUrlLiteralsInProductionSources() {
    val main = File("src/main")
    assertTrue("expected app/src/main to exist", main.isDirectory)
    val files = main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
    val photoLiterals = files.filter { it.readText().contains("images.unsplash.com/photo-") }
    assertTrue("stock photo URLs found: $photoLiterals", photoLiterals.isEmpty())
    val hostMentions = files.sumOf { f -> Regex("images\\.unsplash\\.com").findAll(f.readText()).count() }
    assertEquals("the stock host must appear once (isStockCover)", 1, hostMentions)
  }
}
