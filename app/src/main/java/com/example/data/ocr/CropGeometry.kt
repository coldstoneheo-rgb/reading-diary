package com.example.data.ocr

/**
 * 미리보기 상자 안에서 사진이 실제로 그려지는 사각형(ContentScale.Fit, 사용자 회전 반영).
 * 자르기 틀은 상자가 아니라 이 사각형 기준 비율로 그려야 [BitmapDecoding.cropRect]가 자르는 영역과 같아진다.
 * 상자 기준으로 그리면 세로 사진의 좌우 여백(검은 띠)만큼 틀이 실제보다 바깥에 보여 줄 머리·끝 글자가 잘린다.
 * 순수 함수라 JVM에서 테스트한다.
 */
object CropGeometry {
    data class Frame(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    /** 90°/270° 회전이면 가로세로가 바뀐다. */
    fun isQuarterTurn(rotationDegrees: Float): Boolean {
        val d = ((rotationDegrees % 360f) + 360f) % 360f
        return d == 90f || d == 270f
    }

    /**
     * [imageWidth]×[imageHeight](EXIF 반영 뒤 크기)를 [rotationDegrees]만큼 돌려 [boxWidth]×[boxHeight]에
     * 비율 유지로 맞췄을 때 화면에 보이는 사각형. 이미지 크기를 모르면(0 이하) 상자 전체.
     */
    fun displayedImageRect(
        boxWidth: Float,
        boxHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Float
    ): Frame {
        if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0f || boxHeight <= 0f) {
            return Frame(0f, 0f, boxWidth, boxHeight)
        }
        val quarter = isQuarterTurn(rotationDegrees)
        val rw = (if (quarter) imageHeight else imageWidth).toFloat()
        val rh = (if (quarter) imageWidth else imageHeight).toFloat()
        val scale = minOf(boxWidth / rw, boxHeight / rh)
        val w = rw * scale
        val h = rh * scale
        return Frame((boxWidth - w) / 2f, (boxHeight - h) / 2f, w, h)
    }

    /** 자르기 비율(0..1)을 [image] 사각형 안의 화면 좌표로. */
    fun cropFrame(image: Frame, left: Float, top: Float, right: Float, bottom: Float): Frame = Frame(
        image.left + left * image.width,
        image.top + top * image.height,
        (right - left) * image.width,
        (bottom - top) * image.height
    )
}
