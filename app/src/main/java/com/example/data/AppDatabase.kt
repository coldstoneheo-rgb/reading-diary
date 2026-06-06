package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Bookcase::class, Book::class, Diary::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookcaseDao(): BookcaseDao
    abstract fun bookDao(): BookDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reading_diary_db"
                )
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val dbInstance = getDatabase(context)
                    val bookcaseDao = dbInstance.bookcaseDao()
                    val bookDao = dbInstance.bookDao()
                    val diaryDao = dbInstance.diaryDao()

                    if (bookcaseDao.getCount() == 0) {
                        val novelId = bookcaseDao.insert(Bookcase(name = "소설", isSystem = true)).toInt()
                        val financeId = bookcaseDao.insert(Bookcase(name = "재테크", isSystem = true)).toInt()
                        val selfId = bookcaseDao.insert(Bookcase(name = "자기계발", isSystem = true)).toInt()
                        val humanId = bookcaseDao.insert(Bookcase(name = "인문", isSystem = true)).toInt()
                        val defaultId = bookcaseDao.insert(Bookcase(name = "기본 책장", isSystem = true)).toInt()

                        // Seed sample books
                        val book1Id = bookDao.insert(
                            Book(
                                title = "데미안 (Demian)",
                                author = "헤르만 헤세",
                                totalPages = 240,
                                currentPage = 120,
                                coverUrl = "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400",
                                bookcaseId = novelId,
                                status = "READING",
                                startDate = "2026-05-20"
                            )
                        ).toInt()

                        val book2Id = bookDao.insert(
                            Book(
                                title = "돈의 속성",
                                author = "김승호",
                                totalPages = 390,
                                currentPage = 390,
                                coverUrl = "https://images.unsplash.com/photo-1592492159418-09f31333cca8?auto=format&fit=crop&q=80&w=400",
                                bookcaseId = financeId,
                                status = "COMPLETED",
                                startDate = "2026-05-01",
                                endDate = "2026-05-15"
                            )
                        ).toInt()

                        val book3Id = bookDao.insert(
                            Book(
                                title = "사피엔스 (Sapiens)",
                                author = "유발 하라리",
                                totalPages = 630,
                                currentPage = 0,
                                coverUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=400",
                                bookcaseId = humanId,
                                status = "TO_READ"
                            )
                        ).toInt()

                        // Seed sample diaries
                        diaryDao.insert(
                            Diary(
                                bookId = book1Id,
                                page = 45,
                                selectedText = "내 안에서 솟아 나오려는 것, 바로 그것을 살아보려고 했다. 왜 그것이 그토록 어려웠을까?",
                                notes = "내 삶의 주인이 되는 것의 찬란함과 두려움을 일깨워주는 위대한 문장. 나는 과연 온전히 나로서 살아보고 있는가."
                            )
                        )
                        diaryDao.insert(
                            Diary(
                                bookId = book1Id,
                                page = 92,
                                selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다. 태어나려는 자는 하나의 세계를 깨뜨려야 한다.",
                                notes = "성장에 필수적인 고통과 가치관 극복에 관한 불후의 교훈. 새로운 시각을 끊임없이 깨어나야 한다."
                            )
                        )
                        diaryDao.insert(
                            Diary(
                                bookId = book2Id,
                                page = 112,
                                selectedText = "돈은 인격체다. 자기를 소중히 대하는 사람에게 머물며 함부로 다루면 언제든 떠나갈 궁리를 한다.",
                                notes = "돈을 단순 욕망이 아닌 인격으로 대하라는 혜안. 나의 자산관리 습관이 돈을 인격적으로 존중하고 있었는지 생각하게 된다."
                            )
                        )
                    }
                }
            }
        }
    }
}
