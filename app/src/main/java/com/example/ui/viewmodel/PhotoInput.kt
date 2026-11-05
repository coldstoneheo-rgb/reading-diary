package com.example.ui.viewmodel

import android.net.Uri

/** 화면이 ViewModel에 넘기는 저장 의도(명세 §3.5). «변경 없음»과 «목록 지정»을 null 하나로 겸하지 않는다. */
sealed interface PhotoInput {
    data object Unchanged : PhotoInput
    /** 목록 순서 = 표시 순서. 목록에 없는 기존 사진은 삭제된다. */
    data class Slots(val slots: List<PhotoInputSlot>) : PhotoInput
}

sealed interface PhotoInputSlot {
    data class Existing(val photoId: Int) : PhotoInputSlot
    data class New(val source: Uri, val rotation: Float = 0f, val flipped: Boolean = false) : PhotoInputSlot
    /** 기존 사진 교체. 새 사진 저장에 실패하면 기존 사진을 그대로 둔다(교체가 곧 유실이 되지 않게). */
    data class Replace(val photoId: Int, val source: Uri, val rotation: Float = 0f, val flipped: Boolean = false) : PhotoInputSlot
}

/** 편집 화면의 사진 목록 계산(순수 함수). */
object PhotoDrafts {
    /** 기록당 사진 상한. 레퍼런스 앱 실사용(한 기록 3장)의 3배 여유이며 P5 zip 크기도 기록당 약 10MB로 묶인다. 사용자 확인 뒤 여기서만 바꾼다. */
    const val MAX_PER_DIARY = 10

    fun canAdd(drafts: List<PhotoInputSlot>): Boolean = drafts.size < MAX_PER_DIARY

    fun add(drafts: List<PhotoInputSlot>, source: Uri): List<PhotoInputSlot> =
        if (canAdd(drafts)) drafts + PhotoInputSlot.New(source) else drafts

    fun removeAt(drafts: List<PhotoInputSlot>, index: Int): List<PhotoInputSlot> =
        if (index in drafts.indices) drafts.filterIndexed { i, _ -> i != index } else drafts

    /** 기존 사진은 [PhotoInputSlot.Replace]로 바꾸고, 아직 저장 전인 새 사진은 그냥 갈아 끼운다. */
    fun replaceAt(drafts: List<PhotoInputSlot>, index: Int, source: Uri): List<PhotoInputSlot> {
        val current = drafts.getOrNull(index) ?: return drafts
        val next = when (current) {
            is PhotoInputSlot.Existing -> PhotoInputSlot.Replace(current.photoId, source)
            is PhotoInputSlot.Replace -> PhotoInputSlot.Replace(current.photoId, source)
            is PhotoInputSlot.New -> PhotoInputSlot.New(source)
        }
        return drafts.mapIndexed { i, slot -> if (i == index) next else slot }
    }

    /** 불러온 목록과 같으면 사진 행을 건드리지 않는다. */
    fun toInput(drafts: List<PhotoInputSlot>, loadedIds: List<Int>): PhotoInput =
        if (drafts == loadedIds.map { PhotoInputSlot.Existing(it) }) PhotoInput.Unchanged else PhotoInput.Slots(drafts)

    /** rememberSaveable용. 카메라 앱을 다녀오면 Activity가 다시 만들어질 수 있어 편집 중 목록을 저장한다. */
    fun encode(drafts: List<PhotoInputSlot>): ArrayList<String> = ArrayList(
        drafts.map { slot ->
            when (slot) {
                is PhotoInputSlot.Existing -> "E|${slot.photoId}"
                is PhotoInputSlot.New -> "N|${slot.rotation}|${slot.flipped}|${slot.source}"
                is PhotoInputSlot.Replace -> "R|${slot.photoId}|${slot.rotation}|${slot.flipped}|${slot.source}"
            }
        }
    )

    fun decode(encoded: List<String>): List<PhotoInputSlot> = encoded.mapNotNull { line ->
        when (line.substringBefore('|')) {
            "E" -> line.split('|', limit = 2).getOrNull(1)?.toIntOrNull()?.let { PhotoInputSlot.Existing(it) }
            "N" -> line.split('|', limit = 4).takeIf { it.size == 4 }?.let {
                PhotoInputSlot.New(Uri.parse(it[3]), it[1].toFloat(), it[2].toBoolean())
            }
            "R" -> line.split('|', limit = 5).takeIf { it.size == 5 }?.let {
                PhotoInputSlot.Replace(it[1].toInt(), Uri.parse(it[4]), it[2].toFloat(), it[3].toBoolean())
            }
            else -> null
        }
    }
}
