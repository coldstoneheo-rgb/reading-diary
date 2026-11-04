package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookcases")
data class Bookcase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isSystem: Boolean = false
)

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = Bookcase::class,
            parentColumns = ["id"],
            childColumns = ["bookcaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int = 0,
    val coverUrl: String = "",
    val bookcaseId: Int,
    val status: String, // "READING", "TO_READ", "COMPLETED"
    val startDate: String = "",
    val endDate: String? = null,
    val rating: Int = 0
) {
    val progressPercent: Float
        get() = if (totalPages > 0) (currentPage.toFloat() / totalPages.toFloat()) else 0f

    /** 화면에 그릴 표지 URL. 표지가 없거나 예전 스톡 사진이면 ""(화면은 CoverPlaceholder). DB 값은 고치지 않는다. */
    val cleanCoverUrl: String
        get() = displayableCoverUrl(coverUrl)
}

@Entity(
    tableName = "diaries",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Diary(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val page: Int,
    val selectedText: String, // OCR extracted underline
    val notes: String,        // User diary notes
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 일기 1 : 사진 N(ADR-006 결정, Room v3). 파일 자체는 [com.example.data.photo.DiaryPhotoStore]가 가진다 — 이 행은 상대 경로만.
 * 일기가 지워지면 행도 CASCADE로 지워진다(파일 삭제는 저장 경로·sweep 몫).
 */
@Entity(
    tableName = "diary_photos",
    foreignKeys = [
        ForeignKey(
            entity = Diary::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("diaryId")]
)
data class DiaryPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val diaryId: Int,
    val path: String,        // noBackupFilesDir/diary_photos/ 기준 상대 경로(UUID.jpg)
    val sortOrder: Int,      // 0부터, 화면 표시 순서
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ADR-003-2: 예전 버전이 표지 없는 책에 넣던 스톡 사진 호스트. 그 URL은 이미 기존 설치의 Room에 저장돼 있으므로
 * 코드 폴백만 지워서는 계속 불려 나간다 → 표시 시점에 걸러 플레이스홀더로 바꾼다. 판정 상수는 이 한 곳뿐.
 */
private const val STOCK_COVER_HOST = "images.unsplash.com"

fun isStockCover(url: String): Boolean = url.contains(STOCK_COVER_HOST)

/** 네트워크로 불러올 표지면 그 URL, 아니면 "". */
fun displayableCoverUrl(url: String): String =
    if (url.startsWith("http") && !isStockCover(url)) url else ""
