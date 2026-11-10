package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.Book
import com.example.ui.screens.ExportResultDialog
import com.example.ui.screens.KnowledgeDrawerContent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExportState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** STAGE2-PLAN ②: 결과 다이얼로그가 «만든 파일 N · 건너뛴 파일 M(목록)»을 보이고, 내보내기 버튼은 콜백이 있을 때만 그린다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportResultDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun doneShowsCountsSkippedListAndRenames() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ExportResultDialog(
          state = ExportState.Done(
            created = listOf("나.md", "가 (1).md"),
            skipped = listOf("다.md"),
            renamed = listOf("가.md" to "가 (1).md")
          ),
          onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("knowledgedrawer_export_result").assertIsDisplayed()
    composeTestRule.onNodeWithText("만든 파일 2 · 건너뛴 파일 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("· 다.md").assertIsDisplayed()
    composeTestRule.onNodeWithText("이미 같은 이름이 있어 건너뛴 파일(덮어쓰지 않았습니다):").assertIsDisplayed()
    composeTestRule.onNodeWithText("· 가.md → 가 (1).md").assertIsDisplayed()
  }

  @Test
  fun errorShowsFailedFileAndPartialCount() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ExportResultDialog(state = ExportState.Error("나.md", listOf("가.md")), onDismiss = {})
      }
    }
    composeTestRule.onNodeWithText("«나.md» 파일을 만들지 못했습니다.").assertIsDisplayed()
    composeTestRule.onNodeWithText("그 전에 만든 파일 1개는 폴더에 남아 있습니다. 다시 내보내면 그 파일들은 건너뜁니다.").assertIsDisplayed()
  }

  @Test
  fun secondRunWithNothingCreatedSaysSo() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ExportResultDialog(state = ExportState.Done(emptyList(), listOf("가.md"), emptyList()), onDismiss = {})
      }
    }
    composeTestRule.onNodeWithText("만든 파일 0 · 건너뛴 파일 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("새로 만든 파일이 없습니다(모두 이미 있음).").assertIsDisplayed()
  }

  @Test
  fun idleDrawsNothing() {
    composeTestRule.setContent { MyApplicationTheme { ExportResultDialog(state = ExportState.Idle, onDismiss = {}) } }
    composeTestRule.onNodeWithTag("knowledgedrawer_export_result").assertDoesNotExist()
  }

  @Test
  fun exportButtonOnlyWhenCallbackGiven() {
    val books = listOf(Book(id = 1, title = "가", author = "김", totalPages = 10, bookcaseId = 1, status = "READING"))
    var withCallback by mutableStateOf(false)
    composeTestRule.setContent {
      MyApplicationTheme {
        KnowledgeDrawerContent(
          books = books, diaries = emptyList(), sharedWords = emptyList(), onBack = {},
          onExportMarkdown = if (withCallback) ({}) else null
        )
      }
    }
    composeTestRule.onNodeWithTag("knowledgedrawer_export_markdown").assertDoesNotExist()
    withCallback = true
    composeTestRule.onNodeWithTag("knowledgedrawer_export_markdown").assertIsDisplayed()
  }
}
