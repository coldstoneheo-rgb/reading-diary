package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.SharedWord
import com.example.ui.screens.ConnectionView
import com.example.ui.screens.GraphLabelGutter
import com.example.ui.screens.GraphPadding
import com.example.ui.screens.KnowledgeDrawerContent
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** STAGE2-PLAN ① 그림 보기: 목록 기본 · 전환 · 단어/선 탭 → 원문 카드 · 상한 고지. 스크린샷 비교는 하지 않는다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class KnowledgeDrawerGraphTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val books = listOf(
    Book(id = 1, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING"),
    Book(id = 2, title = "사피엔스", author = "유발 하라리", totalPages = 630, bookcaseId = 2, status = "TO_READ")
  )
  private val diaries = listOf(
    Diary(id = 10, bookId = 1, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다.", notes = ""),
    Diary(id = 11, bookId = 2, page = 31, selectedText = "인간은 상상 속의 세계를 공유함으로써 협동한다.", notes = "")
  )

  private fun show(sharedWords: List<SharedWord> = KeywordLinks.build(books, diaries), view: ConnectionView = ConnectionView.LIST) {
    composeTestRule.setContent {
      MyApplicationTheme {
        KnowledgeDrawerContent(books = books, diaries = diaries, sharedWords = sharedWords, onBack = {}, initialConnectionView = view)
      }
    }
  }

  @Test
  fun listIsDefault_toggleSwitchesToGraphAndBack() {
    show()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertIsDisplayed()
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").assertDoesNotExist()

    composeTestRule.onNodeWithTag("knowledgedrawer_view_toggle").performClick()
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").assertIsDisplayed()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertDoesNotExist()

    composeTestRule.onNodeWithTag("knowledgedrawer_view_toggle").performClick()
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").assertDoesNotExist()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertIsDisplayed()
  }

  @Test
  fun noConnections_noToggle() {
    show(sharedWords = emptyList())
    composeTestRule.onNodeWithTag("knowledgedrawer_view_toggle").assertDoesNotExist()
    composeTestRule.onNodeWithTag("knowledgedrawer_connections_empty").assertIsDisplayed()
  }

  @Test
  fun tappingWordNode_opensItsQuoteCardFromBothBooks() {
    show(view = ConnectionView.GRAPH)
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertDoesNotExist()
    // 단어 1개 → 오른쪽 열 세로 가운데. x = 폭 - 라벨 칸 - padding (knowledgeGraphFor 규칙)
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").performTouchInput {
      click(Offset(width - GraphLabelGutter.toPx() - GraphPadding.toPx(), height / 2f))
    }
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertExists()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_highlight_1").assertDoesNotExist()
  }

  @Test
  fun tappingEdge_opensTheSameCard_withThatBookHighlighted() {
    show(view = ConnectionView.GRAPH)
    // 책 2권 동률(엣지 1개씩) → 제목순: 데미안(id 1)이 위(y = padding). 그 책 ↔ «세계» 선의 가운데를 누른다.
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").performTouchInput {
      val bookX = GraphLabelGutter.toPx() + GraphPadding.toPx()
      val bookY = GraphPadding.toPx()
      val wordX = width - GraphLabelGutter.toPx() - GraphPadding.toPx()
      val wordY = height / 2f
      click(Offset((bookX + wordX) / 2f, (bookY + wordY) / 2f))
    }
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_세계").assertExists()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_highlight_1").assertExists()
    composeTestRule.onNodeWithTag("knowledgedrawer_connection_highlight_2").assertDoesNotExist()
  }

  @Test
  fun overTheWordLimit_showsPartialNotice_withinLimit_doesNot() {
    val thirteen = (1..13).map { SharedWord(word = "단어%02d".format(it), userTagged = false, bookIds = setOf(1, 2), quotes = emptyList()) }
    show(sharedWords = thirteen, view = ConnectionView.GRAPH)
    composeTestRule.onNodeWithTag("knowledgedrawer_graph_truncated").assertIsDisplayed()
  }

  @Test
  fun withinTheLimits_noPartialNotice() {
    show(view = ConnectionView.GRAPH)
    composeTestRule.onNodeWithTag("knowledgedrawer_graph").assertIsDisplayed()
    composeTestRule.onNodeWithTag("knowledgedrawer_graph_truncated").assertDoesNotExist()
  }
}
