package com.example.data.backup

import org.json.JSONObject

/**
 * 백업 zip의 `manifest.json`(명세 §3.6). 형식 버전 · Room 버전 · 만든 시각 · 기록·사진 수.
 * 가져오기는 이 파일이 없거나 읽히지 않으면 거부한다.
 */
data class BackupManifest(
    val formatVersion: Int,
    val roomVersion: Int,
    val createdAt: Long,
    val bookcases: Int,
    val books: Int,
    val diaries: Int,
    /** `diary_photos` 행 수. */
    val photoRows: Int,
    /** zip에 실제로 담긴 사진 파일 수(기기에 없던 사진은 빠진다 → 복원 뒤 «사진 없음»). */
    val photoFiles: Int
) {
    fun toJson(): String = JSONObject()
        .put(KEY_FORMAT, formatVersion)
        .put(KEY_ROOM, roomVersion)
        .put(KEY_CREATED, createdAt)
        .put(KEY_BOOKCASES, bookcases)
        .put(KEY_BOOKS, books)
        .put(KEY_DIARIES, diaries)
        .put(KEY_PHOTO_ROWS, photoRows)
        .put(KEY_PHOTO_FILES, photoFiles)
        .toString(2)

    companion object {
        const val FORMAT_VERSION = 1
        private const val KEY_FORMAT = "format"
        private const val KEY_ROOM = "roomVersion"
        private const val KEY_CREATED = "createdAt"
        private const val KEY_BOOKCASES = "bookcases"
        private const val KEY_BOOKS = "books"
        private const val KEY_DIARIES = "diaries"
        private const val KEY_PHOTO_ROWS = "photoRows"
        private const val KEY_PHOTO_FILES = "photoFiles"

        /** 손상(JSON 아님·필수 키 없음·음수)이면 null. */
        fun parse(text: String): BackupManifest? = try {
            val o = JSONObject(text)
            BackupManifest(
                formatVersion = o.getInt(KEY_FORMAT),
                roomVersion = o.getInt(KEY_ROOM),
                createdAt = o.getLong(KEY_CREATED),
                bookcases = o.getInt(KEY_BOOKCASES),
                books = o.getInt(KEY_BOOKS),
                diaries = o.getInt(KEY_DIARIES),
                photoRows = o.getInt(KEY_PHOTO_ROWS),
                photoFiles = o.getInt(KEY_PHOTO_FILES)
            ).takeIf { m -> listOf(m.roomVersion, m.bookcases, m.books, m.diaries, m.photoRows, m.photoFiles).all { it >= 0 } }
        } catch (e: Exception) {
            null
        }
    }
}
