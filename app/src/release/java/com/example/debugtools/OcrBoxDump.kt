package com.example.debugtools

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import com.google.mlkit.vision.text.Text

/**
 * 릴리스 변형용 빈 스텁. 상자 덤프(src/debug)는 디버그 빌드에만 있다 — 여기에는 파일 쓰기 · 공유 · 메뉴 · 권한이 없다.
 * main 코드가 같은 이름을 부르므로 두 변형 모두 컴파일된다. 본문을 채우지 않는다.
 */
object OcrBoxDump {
    @Suppress("UNUSED_PARAMETER")
    fun reset() {}

    @Suppress("UNUSED_PARAMETER")
    fun record(text: Text) {}

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun finish(
        context: Context, imageUri: Uri, rotationDegrees: Float, flipped: Boolean,
        cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
        bitmapWidth: Int, bitmapHeight: Int
    ) {}
}

/** 릴리스에는 설정 카드가 없다. */
@Composable
fun OcrBoxDumpSection() {}
