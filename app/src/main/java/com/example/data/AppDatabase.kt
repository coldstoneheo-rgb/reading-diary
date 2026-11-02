package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 스키마 JSON을 `app/schemas/`로 내보낸다(ADR-004 1단계 ③). **버전은 2 그대로다 — 이 PR은 스키마를 바꾸지 않는다.**
 *
 * 왜 지금 켜는가: 현재 [Room.databaseBuilder]가 `fallbackToDestructiveMigration()`을 쓰고 있어,
 * 훗날 누군가 `version`을 올리면 마이그레이션 없이 **기존 설치의 데이터가 조용히 전부 삭제된다.**
 * 안전한 마이그레이션을 쓰려면 "현재 v2가 정확히 어떤 스키마인가"를 파일로 갖고 있어야 하고,
 * 그 정본은 지금(v2가 실제로 배포된 상태에서) 뽑아 커밋해 두어야만 신뢰할 수 있다.
 * 이 PR은 그 정본만 만든다. `fallbackToDestructiveMigration` 제거와 `Migration(2,3)`은 실제로 스키마가 필요해질 때 별도 PR에서.
 */
@Database(entities = [Bookcase::class, Book::class, Diary::class], version = 2, exportSchema = true)
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
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        internal fun clearSampleMarks(context: Context) {
            context.getSharedPreferences(SampleBooks.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(SampleBooks.KEY).commit()
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            /** 파괴적 마이그레이션은 onCreate를 부르지 않고 테이블을 다시 만든다 — 같은 이유로 표식을 지운다. */
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                clearSampleMarks(context)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // DB가 새로 만들어졌으면(신규 설치·DB 없이 설정만 복원) 예전 예시 표식은 무효다.
                // 남겨 두면 id가 다시 1부터 매겨진 진짜 책을 예시로 오인할 수 있다(SampleBooks).
                clearSampleMarks(context)
                CoroutineScope(Dispatchers.IO).launch {
                    val dbInstance = getDatabase(context)
                    val bookcaseDao = dbInstance.bookcaseDao()

                    if (bookcaseDao.getCount() == 0) {
                        bookcaseDao.insert(Bookcase(name = "소설", isSystem = true))
                        bookcaseDao.insert(Bookcase(name = "재테크", isSystem = true))
                        bookcaseDao.insert(Bookcase(name = "자기계발", isSystem = true))
                        bookcaseDao.insert(Bookcase(name = "인문", isSystem = true))
                        bookcaseDao.insert(Bookcase(name = "기본 책장", isSystem = true))

                        // 책·일기 시드 없음: 신규 설치는 빈 서재로 시작한다. 예시는 설정 «예시 2권 넣기»(ADR-003-1, SampleBooks).
                    }
                }
            }
        }
    }
}
