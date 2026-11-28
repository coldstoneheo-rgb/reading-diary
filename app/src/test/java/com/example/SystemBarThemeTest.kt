package com.example

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.isDarkAppTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Before
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
    /** ViewModel이 실제로 받은 Application(전역 팩토리 싱글턴 때문에 앞 시험의 것일 수 있다 · SystemBarColdStartTest 주석). */
    private var vmApp: android.app.Application? = null

    @After
    fun tearDown() {
        val app = vmApp ?: controller?.get()?.application
        controller?.pause()?.stop()?.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        // selectTheme은 이제 저장한다 — VM이 쓴 그 prefs를 지워 뒤 시험이 이 테마로 시작하지 않게 한다.
        app?.getSharedPreferences("diary_general_settings", android.content.Context.MODE_PRIVATE)?.edit()?.clear()?.commit()
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
        vmApp = vm.getApplication()
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

/**
 * 차가운 시작: 시스템 밝은 + 저장된 앱 테마 9(심플블랙). setContent가 그리기 전, onCreate 직후부터 흰 아이콘이다
 * (onCreate 선적용 · 저장값 동기 로드 — 밝은 → 어두운 깜빡임 없음).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SystemBarColdStartTest {
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        val app = controller?.get()?.application
        controller?.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        app?.getSharedPreferences("diary_general_settings", android.content.Context.MODE_PRIVATE)?.edit()?.clear()?.commit()
    }

    /**
     * `ViewModelProvider.AndroidViewModelFactory.getInstance(app)`는 프로세스 전역 싱글턴이라 처음 받은 Application을 계속 쓴다.
     * 실제 앱은 프로세스당 Application이 하나라 문제가 없지만, Robolectric은 시험마다 Application을 새로 만들어서
     * 앞 시험의 Application(다른 데이터 폴더 · 다른 prefs)이 ViewModel에 들어간다(전체 묶음에서만 저장값 대신 1을 읽던 까닭 ·
     * 진단: VM의 Application이 앞서 돈 DiaryDraftRestoreTest의 것이었다). 이 시험은 저장값을 읽는 길을 보므로 싱글턴을 비운다.
     */
    private val factoryFields = ViewModelProvider.AndroidViewModelFactory::class.java.let { cls ->
        cls.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == cls }.onEach { it.isAccessible = true }
    }
    private var savedFactories: List<Any?> = emptyList()

    @Before
    fun resetGlobalViewModelFactory() {
        check(factoryFields.isNotEmpty()) { "AndroidViewModelFactory 싱글턴 필드를 찾지 못함(lifecycle 판 변경?)" }
        savedFactories = factoryFields.map { it.get(null) }
        factoryFields.forEach { it.set(null, null) }
    }

    /** 뒤 시험들이 보던 전역 상태를 그대로 되돌린다(이 시험만 바꾸고 끝낸다). */
    @After
    fun restoreGlobalViewModelFactory() {
        factoryFields.zip(savedFactories).forEach { (f, v) -> f.set(null, v) }
    }

    @Test
    fun systemLight_savedDarkTheme_iconsLightFromOnCreate() {
        val c = Robolectric.buildActivity(MainActivity::class.java).also { controller = it }
        // 이 시험의 Application에 저장값을 둔다(위 @Before로 ViewModel도 같은 Application을 받는다).
        c.get().application.getSharedPreferences("diary_general_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("theme_id", 9).commit()
        c.create()
        val activity = c.get()
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val vmTheme = ViewModelProvider(activity)[ReadingViewModel::class.java].currentThemeId.value
        assertEquals(9, vmTheme)
        assertFalse("상태 표시줄 아이콘이 흰색이어야 한다", insets.isAppearanceLightStatusBars)
        assertFalse("내비게이션 바 아이콘이 흰색이어야 한다", insets.isAppearanceLightNavigationBars)
    }
}

