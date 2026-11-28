package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.ReadingViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 고른 테마가 앱을 완전히 껐다 켜도 남는다: 새 ViewModel이 저장값을 읽는다(디스크 반영은 SharedPreferences.apply에 맡긴다).
 * 저장값이 없거나 범위 밖이면 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePersistenceTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences("diary_general_settings", Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @After
    fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private object NoCallExtractor : TextExtractor {
        override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
    }

    private fun freshViewModel() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }

    @Test
    fun noStoredValue_opensDefaultTheme() {
        assertEquals(1, freshViewModel().currentThemeId.value)
    }

    @Test
    fun selectedTheme_survivesRestart() {
        freshViewModel().selectTheme(9)
        assertEquals(9, freshViewModel().currentThemeId.value)

        freshViewModel().selectTheme(2)
        assertEquals(2, freshViewModel().currentThemeId.value)
    }

    @Test
    fun storedValueOutOfRange_opensDefaultTheme() {
        prefs.edit().putInt("theme_id", 42).commit()
        assertEquals(1, freshViewModel().currentThemeId.value)
        prefs.edit().putInt("theme_id", 0).commit()
        assertEquals(1, freshViewModel().currentThemeId.value)
    }

    @Test
    fun themeIdOrDefault_keepsOnlyExistingThemes() {
        (1..9).forEach { assertEquals(it, ReadingViewModel.themeIdOrDefault(it)) }
        listOf(-1, 0, 10, Int.MAX_VALUE).forEach { assertEquals(1, ReadingViewModel.themeIdOrDefault(it)) }
    }
}
