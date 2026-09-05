package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * OCR 입력용 비트맵 디코드. 원본 해상도(12MP+)를 그대로 올리면 ARGB_8888 기준 수십 MB가 순간 할당되어
 * 저사양 기기에서 OOM/ANR이 난다. 긴 변을 [MAX_SIDE] 안팎으로 제한해 디코드한다(ML Kit 권장 글자 크기는 유지).
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

    /** 반환 null = 디코드 실패(파일 없음·손상). 호출자는 이를 오류로 처리하고 대체 이미지를 쓰지 않는다. */
    fun decodeDownsampled(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
