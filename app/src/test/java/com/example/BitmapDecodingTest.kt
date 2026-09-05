package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.BitmapDecoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 디코드 파이프라인 회귀 테스트. 특히 "inJustDecodeBounds 디코드는 null을 돌려준다"는 계약 때문에
 * 실제 디코드가 영원히 도달 불가능해지는 버그(1차 리뷰 지적)를 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BitmapDecodingTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun writePng(width: Int, height: Int, name: String): Uri {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    val file = File(context.cacheDir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
  }

  @Test
  fun decodeDownsampled_returnsBitmapForSmallImage() {
    val uri = writePng(100, 50, "small.png")

    val decoded = BitmapDecoding.decodeDownsampled(context, uri)

    assertNotNull("bounds-only decode must not be mistaken for failure", decoded)
    assertEquals(100, decoded!!.width)
    assertEquals(50, decoded.height)
  }

  @Test
  fun decodeDownsampled_limitsLongestSide() {
    val uri = writePng(4100, 100, "wide.png")

    val decoded = BitmapDecoding.decodeDownsampled(context, uri)

    assertNotNull(decoded)
    assertEquals(4100 / 4, decoded!!.width) // inSampleSize 4 → 1025
    assertEquals(100 / 4, decoded.height)
  }

  @Test
  fun decodeDownsampled_missingFileReturnsNull() {
    assertNull(BitmapDecoding.decodeDownsampled(context, Uri.fromFile(File(context.cacheDir, "missing.png"))))
  }

  @Test
  fun decodeForOcr_appliesCropAndRotation() {
    val uri = writePng(200, 100, "crop.png")

    val cropped = BitmapDecoding.decodeForOcr(
      context, uri, rotationDegrees = 90f, flipped = false,
      cropLeft = 0f, cropTop = 0f, cropRight = 0.5f, cropBottom = 1f
    )

    assertNotNull(cropped)
    // 90도 회전 후 100×200, 가로 절반 크롭 → 50×200
    assertEquals(50, cropped!!.width)
    assertEquals(200, cropped.height)
  }

  @Test
  fun pureHelpers() {
    assertEquals(90f, BitmapDecoding.exifDegrees(ExifInterface.ORIENTATION_ROTATE_90))
    assertEquals(0f, BitmapDecoding.exifDegrees(ExifInterface.ORIENTATION_NORMAL))
    val rect = BitmapDecoding.cropRect(100, 100, 0.99f, 0.99f, 0.99f, 0.99f)
    assertEquals(1, rect.width())
    assertEquals(1, rect.height())
  }
}
