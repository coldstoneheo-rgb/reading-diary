package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri

/**
 * OCR 입력용 비트맵 디코드. 원본 해상도(12MP+)를 그대로 올리면 ARGB_8888 기준 수십 MB가 순간 할당되어
 * 저사양 기기에서 OOM/ANR이 난다. 긴 변을 [MAX_SIDE] 안팎으로 제한해 디코드한다(ML Kit 권장 글자 크기는 유지).
 * 카메라 JPEG의 EXIF 회전도 적용한다(미리보기(Coil)는 EXIF를 반영하므로 크롭 좌표계를 맞추기 위해).
 */
object BitmapDecoding {
    const val MAX_SIDE = 2048

    /**
     * 원본 크기(width×height)에 대해 긴 변이 [maxSide] 이하가 되는 가장 작은 2의 거듭제곱 inSampleSize.
     * 순수 함수라 JVM에서 테스트한다.
     */
    fun inSampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest > maxSide) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    /** 미리보기에서 정한 비율(0..1) 크롭을 픽셀 사각형으로. 최소 1×1을 보장한다. 순수 함수. */
    fun cropRect(width: Int, height: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val l = (left * width).toInt().coerceIn(0, width - 1)
        val t = (top * height).toInt().coerceIn(0, height - 1)
        val r = (right * width).toInt().coerceIn(l + 1, width)
        val b = (bottom * height).toInt().coerceIn(t + 1, height)
        return Rect(l, t, r, b)
    }

    /** EXIF Orientation 태그 → 시계 방향 회전 각도(플립/전치 방향은 회전 성분만). 순수 함수. */
    fun exifDegrees(orientation: Int): Float = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }

    /** EXIF Orientation 태그에 수평 미러 성분이 있는가(2, 4, 5, 7). 미리보기(Coil)와 같은 8방향 해석. 순수 함수. */
    fun exifMirrored(orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
        else -> false
    }

    /** EXIF 8방향을 [Matrix]로. 미러를 먼저, 회전을 나중에(Coil/ExifInterface 관례). */
    fun exifMatrix(orientation: Int): Matrix = Matrix().apply {
        if (exifMirrored(orientation)) postScale(-1f, 1f)
        val d = exifDegrees(orientation)
        if (d != 0f) postRotate(d)
    }

    /** 반환 null = 디코드 실패(파일 없음·손상·메모리 부족). 호출자는 이를 오류로 처리하고 대체 이미지를 쓰지 않는다. */
    fun decodeDownsampled(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // inJustDecodeBounds 디코드는 항상 null을 돌려준다. 스트림 부재(FileNotFoundException 포함)만 실패로 본다.
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (decoded == null) {
                null
            } else {
                val orientation = try {
                    context.contentResolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } ?: ExifInterface.ORIENTATION_NORMAL
                } catch (e: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
                val exif = exifMatrix(orientation)
                if (exif.isIdentity) {
                    decoded
                } else {
                    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, exif, true)
                    if (oriented !== decoded) decoded.recycle()
                    oriented
                }
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    /**
     * OCR 파이프라인 전체: 다운샘플 디코드(+EXIF) → 사용자 회전/플립 → 비율 크롭.
     * 중간 비트맵은 즉시 회수한다. 실패(손상·메모리 부족)는 null.
     */
    fun decodeForOcr(
        context: Context,
        uri: Uri,
        rotationDegrees: Float,
        flipped: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ): Bitmap? {
        return try {
            val base = decodeDownsampled(context, uri) ?: return null
            val matrix = Matrix().apply {
                if (flipped) postScale(-1f, 1f)
                if (rotationDegrees != 0f) postRotate(rotationDegrees)
            }
            val transformed = if (matrix.isIdentity) base
                else Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true).also { if (it !== base) base.recycle() }
            val rect = cropRect(transformed.width, transformed.height, cropLeft, cropTop, cropRight, cropBottom)
            val cropped = Bitmap.createBitmap(transformed, rect.left, rect.top, rect.width(), rect.height())
            if (cropped !== transformed) transformed.recycle()
            cropped
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
