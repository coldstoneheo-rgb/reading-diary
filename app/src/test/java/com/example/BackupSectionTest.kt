package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.screens.BackupFileNames
import com.example.ui.screens.BackupProgressDialog
import com.example.ui.screens.BackupResultDialog
import com.example.ui.screens.BackupSection
import com.example.ui.screens.RestoreConfirmDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BackupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P5 설정 메뉴 2개(testTag) · 확인 다이얼로그(교체 고지) · 진행 표시 · 결과. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupSectionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun twoMenusCallTheirActions_andAreDisabledWhileRunning() {
    var created = 0
    var restored = 0
    var state by androidx.compose.runtime.mutableStateOf<BackupState>(BackupState.Idle)
    composeTestRule.setContent {
      MyApplicationTheme { BackupSection(state = state, onCreate = { created++ }, onRestore = { restored++ }) }
    }
    composeTestRule.onNodeWithTag("settings_backup_create").performClick()
    composeTestRule.onNodeWithTag("settings_backup_restore").performClick()
    assertEquals(1, created)
    assertEquals(1, restored)
    state = BackupState.Running(restoring = false, done = 0, total = 0)
    composeTestRule.onNodeWithTag("settings_backup_create").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("settings_backup_restore").assertIsNotEnabled()
  }

  @Test
  fun confirmDialogSaysRecordsAreReplaced() {
    var confirmed = false
    composeTestRule.setContent { MyApplicationTheme { RestoreConfirmDialog(onConfirm = { confirmed = true }, onDismiss = {}) } }
    composeTestRule.onNodeWithTag("settings_backup_restore_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("지금 기록이 백업 파일 내용으로 바뀝니다", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_backup_restore_confirm").performClick()
    assertTrue(confirmed)
  }

  @Test
  fun progressShowsPhotoCount() {
    composeTestRule.setContent { MyApplicationTheme { BackupProgressDialog(BackupState.Running(restoring = false, done = 3, total = 10)) } }
    composeTestRule.onNodeWithTag("settings_backup_progress").assertIsDisplayed()
    composeTestRule.onNodeWithText("사진 3 / 10").assertIsDisplayed()
  }

  @Test
  fun restoredResultMentionsMissingPhotos() {
    composeTestRule.setContent { MyApplicationTheme { BackupResultDialog(BackupState.Restored(2, 3, 2, 1), onDismiss = {}) } }
    composeTestRule.onNodeWithText("«사진 없음»", substring = true).assertIsDisplayed()
  }

  @Test
  fun suggestedFileNameIsZip() {
    assertTrue(BackupFileNames.suggested(0L).matches(Regex("독서다이어리-백업-\\d{8}-\\d{4}\\.zip")))
  }
}
