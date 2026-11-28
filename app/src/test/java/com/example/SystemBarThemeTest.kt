package com.example

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.isDarkAppTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** 테마 id → 어두운 바탕 여부를 고정한다. 상태 표시줄 · 내비게이션 바 아이콘 밝기가 이 값을 따른다. */
class AppThemeDarknessTest {
    @Test
    fun themeIds_mapToDarkness() {
        val expected = mapOf(
            1 to false, 2 to true, 3 to false, 4 to false, 5 to true,
            6 to false, 7 to false, 8 to false, 9 to true,
        )
        expected.forEach { (id, dark) -> assertEquals("theme $id", dark, isDarkAppTheme(id)) }
    }

    @Test
    fun unknownThemeId_isLight_likeDefaultTheme() {
        assertFalse(isDarkAppTheme(0))
        assertFalse(isDarkAppTheme(99))
    }
}

/** 시스템 모드와 앱 테마가 엇갈려도 아이콘 밝기는 앱 테마를 따르고, 테마를 바꾸면 다시 켜지 않아도 따라온다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SystemBarFollowsAppThemeTest {
    @get:Rule val composeTestRule = createEmptyComposeRule()
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun lightIcons(activity: MainActivity): Pair<Boolean, Boolean> {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        return insets.isAppearanceLightStatusBars to insets.isAppearanceLightNavigationBars
    }

    @Test
    fun systemDark_appLight_thenSwitchTheme_iconsFollowAppTheme() {
        RuntimeEnvironment.setQualifiers("+night") // 시스템 어두운 모드
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().also { controller = it }
        val activity = c.get()
        val vm = ViewModelProvider(activity)[ReadingViewModel::class.java]
        composeTestRule.waitForIdle()

        // 시스템 어두운 + 앱 밝은 테마(기본 1): 밝은 바탕이므로 어두운 아이콘(= light appearance).
        assertEquals(true to true, lightIcons(activity))

        vm.selectTheme(9) // 심플블랙
        composeTestRule.waitForIdle()
        assertEquals(false to false, lightIcons(activity))

        vm.selectTheme(1)
        composeTestRule.waitForIdle()
        assertTrue(lightIcons(activity).first)
        assertTrue(lightIcons(activity).second)
    }
}
