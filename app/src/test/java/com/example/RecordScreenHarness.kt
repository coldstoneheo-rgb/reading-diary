package com.example

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ui.theme.MyApplicationTheme

/**
 * 기록 쓰기 화면(R1) 시험 · 캡처의 공통 틀: 테마 · 글꼴 배율 · 키보드(IME 인셋) 흉내.
 *
 * Robolectric에는 실제 키보드가 없다. 화면이 받는 것과 같은 IME 인셋을 Compose 뷰에 직접 보내고([sendIme]),
 * 키보드가 덮는 자리를 회색 판으로 그려 캡처에서 겹침이 보이게 한다. 창 단위의 밀어 올림(adjustPan)은 흉내 낼 수 없다 —
 * 그 부분은 창의 softInputMode 값으로 따로 단언한다.
 */
internal object RecordScreenHarness {
  const val LIGHT = 1
  const val DARK = 9 // Theme.kt «Dark»
  const val IME_DP = 300

  @Composable
  fun Frame(themeId: Int = LIGHT, fontScale: Float = 1f, imeDp: Int = 0, onView: (View) -> Unit = {}, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect { onView(view) }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
      MyApplicationTheme(themeId = themeId) {
        Box(Modifier.fillMaxSize()) {
          content()
          if (imeDp > 0) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(imeDp.dp).background(Color(0xFF8E8E8E))) {
              Text("키보드 자리(시험 그림)", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
          }
        }
      }
    }
  }

  /** 키보드가 [px]만큼 올라온 것처럼 IME 인셋을 보낸다(0 = 내림). UI 스레드에서 부른다. */
  fun sendIme(view: View, px: Int) {
    val insets = WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
      .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
      .build()
    ViewCompat.dispatchApplyWindowInsets(view, insets)
  }
}
