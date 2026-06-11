package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
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

    val cleanCoverUrl: String
        get() {
            if (coverUrl.startsWith("http")) return coverUrl
            val defaultCovers = listOf(
                "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400",
                "https://images.unsplash.com/photo-1592492159418-09f31333cca8?auto=format&fit=crop&q=80&w=400",
                "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=400",
                "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&q=80&w=400",
                "https://images.unsplash.com/photo-1610116306796-6fea9f4fae38?auto=format&fit=crop&q=80&w=400"
            )
            val index = Math.abs(title.hashCode()) % defaultCovers.size
            return defaultCovers[index]
        }
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
