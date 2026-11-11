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

  /** 픽셀 색에 자기 원본 좌표를 적은 사진(R = x, G = y). 잘린 비트맵의 한 픽셀이 원본 어디서 왔는지 바로 읽힌다. */
  private fun writeCoordinateImage(width: Int, height: Int, name: String): Uri {
    require(width <= 256 && height <= 256)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, Color.rgb(x, y, 0))
    val file = File(context.cacheDir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
  }

  /**
   * 화면의 한 점을 미리보기 배치(OcrDiaryScreen: 회전 전 크기로 가운데 → graphicsLayer 좌우 반전 → rotate)를
   * 거꾸로 따라가 원본 사진 좌표로. CropGeometry 공식을 쓰지 않고 화면 변환만으로 계산한다.
   */
  private fun screenToSource(px: Float, py: Float, box: Pair<Float, Float>, image: CropGeometry.Frame,
                             srcW: Int, srcH: Int, rot: Float, flip: Boolean): Pair<Float, Float> {
    val cx = box.first / 2f
    val cy = box.second / 2f
    // 회전 전 컴포저블 크기(90°/270°면 화면 사각형의 가로세로를 바꿔 잡는다)
    val quarter = CropGeometry.isQuarterTurn(rot)
    val cw = if (quarter) image.height else image.width
    val ch = if (quarter) image.width else image.height
    // rotate(rot)의 역: -rot (y가 아래로 향하는 좌표에서 시계 방향 양수)
    val rad = Math.toRadians(-rot.toDouble())
    val dx = px - cx
    val dy = py - cy
    var ux = (dx * Math.cos(rad) - dy * Math.sin(rad)).toFloat()
    val uy = (dx * Math.sin(rad) + dy * Math.cos(rad)).toFloat()
    if (flip) ux = -ux
    val s = cw / srcW
    assertEquals("fit keeps aspect", s, ch / srcH, 1e-3f)
    return ((ux + cw / 2f) / s) to ((uy + ch / 2f) / s)
  }

  /**
   * 화면 틀의 왼쪽 위 모서리가 가리키는 원본 픽셀 = decodeForOcr가 실제로 잘라 낸 첫 픽셀(±2px).
   * 수정 전(틀을 상자 비율로 그림)이면 세로 사진에서 띠 폭만큼(≈ 원본 20px) 어긋나 실패한다.
   */
  @Test
  fun frameCornerOnScreenPointsAtFirstCroppedPixel() {
    data class Case(val w: Int, val h: Int, val rot: Float, val flip: Boolean, val l: Float, val t: Float, val r: Float, val b: Float)
    val cases = listOf(
      Case(200, 256, 0f, false, 0.1f, 0.1f, 0.9f, 0.9f),
      Case(200, 256, 90f, false, 0.05f, 0.2f, 0.7f, 0.95f),
      Case(256, 200, 270f, true, 0.2f, 0.1f, 1f, 0.6f),
      Case(256, 200, 180f, false, 0.3f, 0.3f, 0.5f, 1f),
      Case(200, 256, 0f, true, 0.25f, 0.05f, 0.75f, 0.5f)
    )
    val box = 324f to 320f
    for ((i, c) in cases.withIndex()) {
      val uri = writeCoordinateImage(c.w, c.h, "coord$i.png")
      val size = BitmapDecoding.orientedSize(context, uri)
      assertNotNull(size)
      val overlay = CropGeometry.overlay(box.first, box.second, size, c.rot, c.l, c.t, c.r, c.b)

      val cropped = BitmapDecoding.decodeForOcr(context, uri, c.rot, c.flip, c.l, c.t, c.r, c.b)!!
      val first = cropped.getPixel(0, 0)
      val actualX = Color.red(first) + 0.5f
      val actualY = Color.green(first) + 0.5f

      val (sx, sy) = screenToSource(overlay.crop.left, overlay.crop.top, box, overlay.image, c.w, c.h, c.rot, c.flip)
      assertEquals("case $i x", actualX, sx, 2f)
      assertEquals("case $i y", actualY, sy, 2f)
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
