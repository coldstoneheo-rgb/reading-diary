package com.example

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor as CoilInterceptor
import coil.request.SuccessResult
import com.example.data.AppDatabase
import com.example.data.Diary
import com.example.data.photo.CameraTempFiles
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.data.Book
import com.example.data.Bookcase
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowToast
import java.io.File

/** Windows에서 돌리면 FileProvider가 경로 구분자 때문에 캐시 폴더를 찾지 못한다. URI 모양만 같게 만들어 준다. */
@Implements(FileProvider::class)
class ShadowCacheFileProvider {
  companion object {
    @JvmStatic
    @Implementation
    fun getUriForFile(context: Context, authority: String, file: File): Uri =
      Uri.parse("content://$authority/cache/${file.name}")
  }
}

/**
 * «기록하기 → 사진 촬영 → 확인» 중 Activity·ViewModel이 사라지는 경우(R4).
 * 저장 번들을 Parcel로 한 번 왕복시킨 뒤 새 Activity를 띄운다. 새 Activity는 ViewModelStore도 새것이다.
 * 실제 카메라와 프로세스 사망은 아니다. 실기기 판정은 별도로 한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36], shadows = [ShadowCacheFileProvider::class])
class CameraProcessDeathTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private var bookId = 0

  private class CameraRequest(val requestCode: Int, val output: Uri)

  @Before
  fun setUp() = runBlocking {
    shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
    val db = AppDatabase.getDatabase(app)
    val caseId = db.bookcaseDao().insert(Bookcase(name = "촬영 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "촬영 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
    // 전역 ImageLoader가 앞 테스트의 메인 루퍼에 묶이면 뒤 테스트의 Compose가 idle이 되지 않는다. 디코드 없이 단색을 돌려준다.
    Coil.setImageLoader(
      ImageLoader.Builder(app).allowHardware(false).components {
        add(CoilInterceptor { chain ->
          SuccessResult(drawable = ColorDrawable(Color.GRAY), request = chain.request, dataSource = DataSource.MEMORY)
        })
      }.build()
    )
  }

  /** 살아 있는 Activity의 ViewModel 작업이 다음 테스트 클래스의 Dispatchers.setMain과 겹치지 않게 부순다. */
  @After
  fun tearDown() {
    openActivities.forEach { it.pause().stop().destroy() }
    shadowOf(Looper.getMainLooper()).idle()
    Coil.reset()
  }

  private fun vm(activity: MainActivity) = ViewModelProvider(activity)[ReadingViewModel::class.java]

  private fun settle(condition: () -> Boolean) {
    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      condition()
    }
    composeTestRule.waitForIdle()
  }

  private fun textShown(text: String) = composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  /** 책 상세 → 기록 화면 → «사진 촬영» 누름까지. */
  private fun openCamera(controller: ActivityController<MainActivity>): CameraRequest {
    val model = vm(controller.get())
    model.navigateTo(Screen.BookDetail(bookId))
    model.navigateTo(Screen.AddDiary(bookId))
    settle { textShown("사진 촬영") }
    composeTestRule.onNodeWithText("사진 촬영").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
    val started = shadowOf(controller.get()).nextStartedActivityForResult
      ?: error("카메라가 뜨지 않았다. 마지막 토스트: ${ShadowToast.getTextOfLatestToast()}")
    assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, started.intent.action)
    @Suppress("DEPRECATION")
    return CameraRequest(started.requestCode, started.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)!!)
  }

  /** 카메라가 떠 있는 동안 Activity가 부서지고, 저장 번들로 새 Activity가 뜨며 그 사이에 카메라 결과가 온다. */
  private fun killAndRestore(
    first: ActivityController<MainActivity>,
    request: CameraRequest,
    resultCode: Int,
    whileCameraIsOpen: (File) -> Unit = { it.writeBytes(JPEG_BYTES) }
  ): ActivityController<MainActivity> {
    val saved = Bundle()
    first.pause().stop().saveInstanceState(saved)
    first.destroy()
    openActivities -= first
    val tempFile = File(app.cacheDir, request.output.lastPathSegment!!)
    tempFileSurvivedDestroy = tempFile.exists()
    whileCameraIsOpen(tempFile)
    val restored = roundTrip(saved)
    val second = Robolectric.buildActivity(MainActivity::class.java)
    second.create(restored).start().restoreInstanceState(restored).postCreate(restored)
    assertTrue(
      "새 Activity의 결과 레지스트리가 저장된 요청 코드를 알아야 한다",
      second.get().activityResultRegistry.dispatchResult(request.requestCode, resultCode, null)
    )
    second.resume().visible()
    openActivities += second
    shadowOf(Looper.getMainLooper()).idle()
    return second
  }

  private fun roundTrip(bundle: Bundle): Bundle {
    val parcel = Parcel.obtain()
    try {
      parcel.writeBundle(bundle)
      parcel.setDataPosition(0)
      return parcel.readBundle(javaClass.classLoader)!!
    } finally {
      parcel.recycle()
    }
  }

  private var tempFileSurvivedDestroy = false

  private val openActivities = mutableListOf<ActivityController<MainActivity>>()

  private fun start() = Robolectric.buildActivity(MainActivity::class.java).setup().also { openActivities += it }

  /** 사용자 신고 ①: 촬영 뒤 초기 화면이 아니라 기록 화면으로 돌아와 자르기 단계가 떠야 한다. */
  @Test
  fun captureSuccess_afterRestore_returnsToDiaryScreen_andShowsCropStep() {
    val first = start()
    val request = openCamera(first)

    val second = killAndRestore(first, request, Activity.RESULT_OK)

    assertTrue("재생성 파괴가 카메라가 쓸 임시 파일을 지우면 안 된다", tempFileSurvivedDestroy)
    assertEquals(Screen.AddDiary(bookId), vm(second.get()).currentScreenState.value)
    settle { textShown("자르기 확정하기") }
    assertNotEquals(CANCELED_TOAST, ShadowToast.getTextOfLatestToast())
    composeTestRule.onNodeWithTag("camera_crop_card").captureRoboImage("src/test/screenshots/camera_restored_crop_step.png")
  }

  @Test
  fun restoredStack_backGoesToBookDetail() {
    val first = start()
    val second = killAndRestore(first, openCamera(first), Activity.RESULT_OK)

    vm(second.get()).navigateBack()

    assertEquals(Screen.BookDetail(bookId), vm(second.get()).currentScreenState.value)
  }

  /** 사용자 신고의 토스트는 결과가 RESULT_OK가 아니었음을 뜻한다. 파일이 이번 촬영으로 채워졌으면 촬영으로 본다. */
  @Test
  fun canceledResult_withFreshPhoto_stillShowsCropStep() {
    val first = start()
    val second = killAndRestore(first, openCamera(first), Activity.RESULT_CANCELED)

    assertEquals(Screen.AddDiary(bookId), vm(second.get()).currentScreenState.value)
    settle { textShown("자르기 확정하기") }
  }

  @Test
  fun canceledResult_withEmptyFile_staysCanceled() {
    val first = start()
    val second = killAndRestore(first, openCamera(first), Activity.RESULT_CANCELED) { }

    assertEquals(Screen.AddDiary(bookId), vm(second.get()).currentScreenState.value)
    settle { textShown("사진 촬영") }
    assertEquals(CANCELED_TOAST, ShadowToast.getTextOfLatestToast())
    assertFalse(textShown("자르기 확정하기"))
  }

  @Test
  fun canceledResult_withFileOlderThanCameraStart_staysCanceled() {
    val first = start()
    val second = killAndRestore(first, openCamera(first), Activity.RESULT_CANCELED) { file ->
      file.writeBytes(JPEG_BYTES)
      file.setLastModified(System.currentTimeMillis() - 60_000)
    }

    assertEquals(Screen.AddDiary(bookId), vm(second.get()).currentScreenState.value)
    settle { textShown("사진 촬영") }
    assertEquals(CANCELED_TOAST, ShadowToast.getTextOfLatestToast())
    assertFalse(textShown("자르기 확정하기"))
  }

  /** 복원 직후 책 목록이 오기 전에 «책을 찾을 수 없다»고 단정하면 안 된다. */
  @Test
  fun restoredScreen_showsNoMissingBookMessage_afterBooksArrive() {
    val first = start()
    val second = killAndRestore(first, openCamera(first), Activity.RESULT_OK)
    settle { textShown("자르기 확정하기") }
    assertFalse(textShown("해당 책을 일치시킬 수 없습니다."))
    vm(second.get()).navigateTo(Screen.AddDiary(987_654))
    settle { textShown("돌아가기") }
    assertTrue("정말 없는 책이면 안내하고 나갈 길을 준다", textShown("해당 책을 일치시킬 수 없습니다."))
  }

  /** 편집 화면의 «사진 추가 → 카메라»도 같은 재생성을 견딘다. 이 경로는 취소 토스트가 없어 조용히 사라지기 쉽다. */
  private fun openPhotoCamera(controller: ActivityController<MainActivity>, diaryId: Int): CameraRequest {
    vm(controller.get()).navigateTo(Screen.AddDiary(bookId, diaryId))
    settle { composeTestRule.onAllNodesWithTag("diary_photo_add").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("diary_photo_add").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_photo_from_camera").performClick()
    composeTestRule.waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
    val started = shadowOf(controller.get()).nextStartedActivityForResult
      ?: error("카메라가 뜨지 않았다. 마지막 토스트: ${ShadowToast.getTextOfLatestToast()}")
    @Suppress("DEPRECATION")
    return CameraRequest(started.requestCode, started.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)!!)
  }

  private fun newDiary() = runBlocking {
    AppDatabase.getDatabase(app).diaryDao().insert(Diary(bookId = bookId, page = 1, selectedText = "처음 문장", notes = "")).toInt()
  }

  private fun assertPhotoSlot(second: ActivityController<MainActivity>, diaryId: Int, expected: Boolean) {
    assertEquals(Screen.AddDiary(bookId, diaryId), vm(second.get()).currentScreenState.value)
    settle { composeTestRule.onAllNodesWithTag("diary_photo_add").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(expected, composeTestRule.onAllNodesWithTag("diary_photo_0").fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun photoCamera_resultOk_afterRestore_addsDraft() {
    val d = newDiary()
    val first = start()
    val second = killAndRestore(first, openPhotoCamera(first, d), Activity.RESULT_OK)
    assertPhotoSlot(second, d, expected = true)
  }

  @Test
  fun photoCamera_canceledWithFreshPhoto_afterRestore_addsDraft() {
    val d = newDiary()
    val first = start()
    val second = killAndRestore(first, openPhotoCamera(first, d), Activity.RESULT_CANCELED)
    assertPhotoSlot(second, d, expected = true)
  }

  @Test
  fun photoCamera_canceledWithEmptyFile_afterRestore_addsNothing() {
    val d = newDiary()
    val first = start()
    val second = killAndRestore(first, openPhotoCamera(first, d), Activity.RESULT_CANCELED) { }
    assertPhotoSlot(second, d, expected = false)
  }

  private fun tempFiles() = CameraTempFiles.list(app.cacheDir)

  /** 화면을 떠나면 책 페이지 임시 사진을 지운다. 재생성 보호가 이 삭제까지 막으면 캐시에 쌓인다. */
  @Test
  fun leavingScreen_deletesTempFiles() {
    val first = start()
    val request = openCamera(first)
    File(app.cacheDir, request.output.lastPathSegment!!).writeBytes(JPEG_BYTES)
    assertTrue(tempFiles().isNotEmpty())

    vm(first.get()).navigateBack()
    settle { tempFiles().isEmpty() }
  }

  @Test
  fun rotation_keepsScreenAndTempFile() {
    val first = start()
    val request = openCamera(first)
    val model = vm(first.get())
    File(app.cacheDir, request.output.lastPathSegment!!).writeBytes(JPEG_BYTES)
    val rotated = android.content.res.Configuration(first.get().resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    first.configurationChange(rotated)
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue("ViewModel은 살아남는다", model === vm(first.get()))
    assertEquals(Screen.AddDiary(bookId), model.currentScreenState.value)
    assertTrue(tempFiles().isNotEmpty())
  }

  @Test
  fun freshStart_deletesLeftoverTempFiles() {
    val leftover = File(app.cacheDir, CameraTempFiles.PREFIX + "old" + CameraTempFiles.SUFFIX).apply { writeBytes(JPEG_BYTES) }
    start()
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse(leftover.exists())
  }

  private companion object {
    const val CANCELED_TOAST = "사진 촬영이 취소되었습니다."
    val JPEG_BYTES = byteArrayOf(-1, -40, -1, -39)
  }
}
