package com.example

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.example.data.Book
import com.example.data.Diary
import com.example.data.photo.RecallPhoto
import com.example.ui.screens.DiaryPhotoEditSection
import com.example.ui.screens.PhraseRecallContent
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/** 명세 §3.5 사진 화면 기준 이미지. 사진 자리는 Coil 인터셉터가 주는 단색이다(실제 파일 디코드는 DiaryPhotoStoreTest 몫). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PhotoScreensScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val loaded = AtomicInteger(0)
  private lateinit var savedTimeZone: TimeZone
  private lateinit var savedLocale: Locale

  @Before
  fun setUp() {
    savedTimeZone = TimeZone.getDefault(); savedLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("UTC")); Locale.setDefault(Locale.KOREA)
    // 실제 디코드 없이 파일 이름으로 정한 단색을 돌려준다(기준 이미지가 로드 시점·디코더에 흔들리지 않게).
    Coil.setImageLoader(
      ImageLoader.Builder(context)
        .allowHardware(false)
        .components {
          add(Interceptor { chain ->
            val name = (chain.request.data as File).name
            loaded.incrementAndGet()
            SuccessResult(
              drawable = ColorDrawable(if (name.startsWith("a")) Color.rgb(90, 130, 200) else Color.rgb(200, 120, 90)),
              request = chain.request,
              dataSource = DataSource.MEMORY
            )
          })
        }
        .build()
    )
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(savedTimeZone); Locale.setDefault(savedLocale)
    Coil.reset()
  }

  private fun photoFile(name: String) = File(context.cacheDir, name)

  private fun awaitImages(count: Int) {
    composeTestRule.waitUntil(15_000) { loaded.get() >= count }
    composeTestRule.waitForIdle()
  }

  private val noon = 1_780_000_000_000L + 12L * 3600 * 1000
  private val books = listOf(Book(id = 1, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING"))
  private val diaries = listOf(
    Diary(id = 10, bookId = 1, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다.", notes = "", createdAt = noon),
    Diary(id = 11, bookId = 1, page = 40, selectedText = "파일이 사라진 기록의 세계", notes = "", createdAt = noon - 1000),
    Diary(id = 12, bookId = 1, page = 15, selectedText = "사진 기능 이전에 남긴 세계", notes = "", createdAt = noon - 2000)
  )

  @Test
  fun recallCards_thumbnail_dangling_noPhoto_screenshot() {
    val photos = mapOf(
      10 to listOf(RecallPhoto(1, photoFile("a.png")), RecallPhoto(2, photoFile("b.png"))),
      11 to listOf(RecallPhoto(3, null))
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries, sharedWords = emptyList(), photosByDiary = photos,
          onOpenBook = { _, _, _ -> }, onBack = {}, initialQuery = "세계"
        )
      }
    }
    awaitImages(1)

    // 카드가 clickable이라 하위 태그는 병합 트리에서 카드 태그에 가려진다.
    composeTestRule.onAllNodesWithTag("phraserecall_thumbnail", useUnmergedTree = true).assertCountEquals(1)
    composeTestRule.onNodeWithText("사진 없음(이 기기에 없음)").assertIsDisplayed()
    composeTestRule.onNodeWithText("사진 없음").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/phrase_recall_photo_cards.png")
  }

  @Test
  fun recallCard_expanded_showsPager_andOpensViewer() {
    val photos = mapOf(
      10 to listOf(RecallPhoto(1, photoFile("a.png")), RecallPhoto(2, photoFile("b.png")))
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries.take(1), sharedWords = emptyList(), photosByDiary = photos,
          onOpenBook = { _, _, _ -> }, onBack = {}, initialQuery = "세계"
        )
      }
    }
    awaitImages(1)
    composeTestRule.onAllNodesWithTag("phraserecall_result").onFirst().performClick()
    composeTestRule.onNodeWithTag("phraserecall_photo_pager", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("1 / 2", useUnmergedTree = true).assertIsDisplayed()
    awaitImages(2)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/phrase_recall_photo_expanded.png")

    composeTestRule.onNodeWithTag("phraserecall_photo_pager", useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithTag("phraserecall_photo_viewer").assertIsDisplayed()
  }

  /** 명세 §3.5 dangling: 복기(펼친 카드)에서도 깨진 이미지 대신 안내를 보인다. */
  @Test
  fun recallCard_expanded_danglingShowsNotice() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries.take(1), sharedWords = emptyList(),
          photosByDiary = mapOf(10 to listOf(RecallPhoto(1, null))),
          onOpenBook = { _, _, _ -> }, onBack = {}, initialQuery = "세계"
        )
      }
    }
    composeTestRule.onAllNodesWithTag("phraserecall_result").onFirst().performClick()
    composeTestRule.onNodeWithTag("phraserecall_photo_pager", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("사진 없음(이 기기에 없음)", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun diaryEdit_photoSection_screenshot() {
    val models: List<Any?> = listOf(photoFile("a.png"), null, photoFile("b.png"))
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          DiaryPhotoEditSection(models = models, onPick = { _, _ -> }, onRemove = {})
        }
      }
    }
    awaitImages(2)

    composeTestRule.onNodeWithTag("diary_photo_add").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/diary_edit_photos.png")
  }
}
