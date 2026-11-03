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
 * 스키마 JSON을 `app/schemas/`로 내보낸다(ADR-004 1단계 ③). 정본은 커밋된 `2.json`·`3.json`이다.
 *
 * v3(ADR-006, 명세 PHRASE-RECALL-SPEC-2026-11 §3.4): `diary_photos` 테이블 추가, [MIGRATION_2_3]으로 옮긴다.
 * 파괴적 폴백은 쓰지 않는다 — 마이그레이션이 없거나 실패하면 앱이 열리지 않는다(조용한 전체 삭제보다 낫다, ADR-006 리스크 표).
 * 버전을 올릴 때는 반드시 `Migration`을 붙이고 [ALL_MIGRATIONS]에 넣는다(v2→v3는 MigrationTest M1~M7이 검증한다. 다음 버전의 마이그레이션 누락을 자동으로 잡는 테스트는 아직 없다).
 */
@Database(entities = [Bookcase::class, Book::class, Diary::class, DiaryPhoto::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookcaseDao(): BookcaseDao
    abstract fun bookDao(): BookDao
    abstract fun diaryDao(): DiaryDao
    abstract fun diaryPhotoDao(): DiaryPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder(context, DB_NAME).build()
                INSTANCE = instance
                instance
            }
        }

        const val DB_NAME = "reading_diary_db"

        /** 앱이 쓰는 빌더 설정 전부(마이그레이션·콜백). MigrationTest M4가 같은 설정으로 연다. */
        internal fun builder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(DatabaseCallback(context.applicationContext))

        internal fun clearSampleMarks(context: Context) {
            context.getSharedPreferences(SampleBooks.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(SampleBooks.KEY).commit()
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
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
