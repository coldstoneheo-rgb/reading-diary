package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.BitmapDecoding
import com.example.data.ocr.CropGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 미리보기 자르기 틀 = 실제로 잘리는 영역(2026-11-11 사용자 보고: 줄 머리·끝 1~2자 누락).
 * 예전 틀은 320dp 상자 기준 비율로 그려져, 세로 사진(ContentScale.Fit 좌우 검은 띠)에서
 * 틀이 실제 잘리는 영역보다 바깥에 보였다. 틀을 사진이 그려진 사각형 기준으로 그리는 것을 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CropGeometryTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun writeImage(width: Int, height: Int, name: String, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    val file = File(context.cacheDir, name)
    file.outputStream().use { bitmap.compress(format, 90, it) }
    bitmap.recycle()
    return file
  }

  @Test
  fun portraitPhoto_frameSitsOnImageNotOnBlackGutter() {
    // 384dp 폭 기기: 상자 324×320dp. 3:4 세로 사진은 240×320으로 맞춰지고 좌우 42dp가 검은 띠.
    val image = CropGeometry.displayedImageRect(324f, 320f, 3000, 4000, 0f)
    assertEquals(42f, image.left, 0.01f)
    assertEquals(240f, image.width, 0.01f)
    assertEquals(0f, image.top, 0.01f)
    assertEquals(320f, image.height, 0.01f)

    val frame = CropGeometry.cropFrame(image, 0.1f, 0.1f, 0.9f, 0.9f)
    // 실제 잘리는 영역의 화면 위치: 42 + 0.1×240 = 66dp (예전 틀은 0.1×324 = 32.4dp에 그려졌다)
    assertEquals(66f, frame.left, 0.01f)
    assertEquals(258f, frame.right, 0.01f)
    assertEquals(32f, frame.top, 0.01f)
    assertEquals(288f, frame.bottom, 0.01f)
  }

  @Test
  fun quarterTurnSwapsAspect() {
    // 4:3 가로 사진을 90° 돌리면 3:4 세로로 보인다 — 상자를 넘치지 않는다(예전엔 위아래가 잘려 보였다).
    val image = CropGeometry.displayedImageRect(324f, 320f, 4000, 3000, 90f)
    assertEquals(240f, image.width, 0.01f)
    assertEquals(320f, image.height, 0.01f)
    assertEquals(true, CropGeometry.isQuarterTurn(-90f))
    assertEquals(false, CropGeometry.isQuarterTurn(180f))
  }

  @Test
  fun unknownImageSizeFallsBackToWholeBox() {
    val image = CropGeometry.displayedImageRect(324f, 320f, 0, 0, 0f)
    assertEquals(CropGeometry.Frame(0f, 0f, 324f, 320f), image)
  }

  /** 화면 틀이 사진 사각형에서 차지하는 비율 = decodeForOcr가 실제로 잘라 낸 비트맵의 비율(±1px). */
  @Test
  fun frameOnScreenMatchesCroppedBitmap() {
    data class Case(val w: Int, val h: Int, val rot: Float, val flip: Boolean, val l: Float, val t: Float, val r: Float, val b: Float)
    val cases = listOf(
      Case(300, 400, 0f, false, 0.1f, 0.1f, 0.9f, 0.9f),
      Case(300, 400, 90f, false, 0.05f, 0.2f, 0.7f, 0.95f),
      Case(400, 300, 270f, true, 0.2f, 0f, 1f, 0.6f),
      Case(400, 300, 180f, false, 0f, 0.3f, 0.5f, 1f)
    )
    for ((i, c) in cases.withIndex()) {
      val uri = Uri.fromFile(writeImage(c.w, c.h, "geom$i.png"))
      val size = BitmapDecoding.orientedSize(context, uri)
      assertNotNull(size)
      val full = BitmapDecoding.decodeForOcr(context, uri, c.rot, c.flip, 0f, 0f, 1f, 1f)!!
      val cropped = BitmapDecoding.decodeForOcr(context, uri, c.rot, c.flip, c.l, c.t, c.r, c.b)!!

      val image = CropGeometry.displayedImageRect(324f, 320f, size!!.first, size.second, c.rot)
      // 화면 사진 사각형의 모양 = 회전 뒤 비트맵 모양
      assertEquals("case $i aspect", full.width.toFloat() / full.height, image.width / image.height, 0.01f)
      val frame = CropGeometry.cropFrame(image, c.l, c.t, c.r, c.b)
      val leftPx = (frame.left - image.left) / image.width * full.width
      val widthPx = frame.width / image.width * full.width
      val topPx = (frame.top - image.top) / image.height * full.height
      val heightPx = frame.height / image.height * full.height
      val rect = BitmapDecoding.cropRect(full.width, full.height, c.l, c.t, c.r, c.b)
      assertEquals("case $i left", rect.left.toFloat(), leftPx, 1f)
      assertEquals("case $i top", rect.top.toFloat(), topPx, 1f)
      assertEquals("case $i width", cropped.width.toFloat(), widthPx, 1f)
      assertEquals("case $i height", cropped.height.toFloat(), heightPx, 1f)
    }
  }

  @Test
  fun orientedSize_appliesExifRotation() {
    val png = writeImage(200, 100, "plain.png")
    assertEquals(200 to 100, BitmapDecoding.orientedSize(context, Uri.fromFile(png)))

    val jpg = writeImage(200, 100, "rotated.jpg", Bitmap.CompressFormat.JPEG)
    ExifInterface(jpg.absolutePath).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      saveAttributes()
    }
    assertEquals(100 to 200, BitmapDecoding.orientedSize(context, Uri.fromFile(jpg)))
    // decodeDownsampled와 같은 좌표계
    val decoded = BitmapDecoding.decodeDownsampled(context, Uri.fromFile(jpg))!!
    assertEquals(100 to 200, decoded.width to decoded.height)
  }
}
