package com.example.debugtools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ShadowCacheFileProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 덤프 공유(디버그 전용): 기본 꺼짐 · TSV 사본만 공유 · 지우기가 사본까지. 합성 파일만 쓴다. */
@RunWith(RobolectricTestRunner::class)
// Windows Robolectric에서 FileProvider가 경로 구분자로 캐시 폴더를 못 찾는 문제 — CameraProcessDeathTest와 같은 셰도(URI 모양만 같음).
@Config(sdk = [36], shadows = [ShadowCacheFileProvider::class])
class OcrBoxDumpShareTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun clean() {
    OcrBoxDump.deleteAll(context)
    OcrBoxDump.setEnabled(context, false)
  }

  @Test
  fun offByDefault_andNothingToShare() {
    assertFalse(OcrBoxDump.isEnabled(context))
    assertNull(OcrBoxDump.shareIntent(context))
    // record 없이 finish는 아무것도 쓰지 않는다.
    runBlocking { OcrBoxDump.finish(context, Uri.parse("content://x/none"), 0f, false, 0f, 0f, 1f, 1f, 10, 10) }
    assertEquals(0, OcrBoxDump.files(context).size)
  }

  @Test
  fun share_sendsOnlyTsvCopies_andDeleteAllClearsThem() {
    val dir = OcrBoxDump.dumpDir(context)
    File(dir, "ocr-dump-001-a.tsv").writeText("level\n")
    File(dir, "ocr-dump-002-b.tsv").writeText("level\n")
    File(dir, "photo.jpg").writeText("not a tsv")
    OcrBoxDump.setEnabled(context, true) // .enabled 표시 파일도 공유 대상이 아니다

    val chooser = OcrBoxDump.shareIntent(context)!!
    @Suppress("DEPRECATION")
    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
    @Suppress("DEPRECATION")
    val uris = send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
    assertEquals(2, uris.size)
    assertEquals(2, send.clipData!!.itemCount)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    val shared = File(context.cacheDir, "ocr-dump-share").listFiles()!!.map { it.name }.sorted()
    assertEquals(listOf("ocr-dump-001-a.tsv", "ocr-dump-002-b.tsv"), shared)

    assertEquals(2, OcrBoxDump.deleteAll(context))
    assertFalse(File(context.cacheDir, "ocr-dump-share").exists())
  }
}
