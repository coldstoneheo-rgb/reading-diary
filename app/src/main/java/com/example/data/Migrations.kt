package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: `diary_photos` 테이블 1개 + diaryId 인덱스(ADR-006 결정, 명세 PHRASE-RECALL-SPEC-2026-11 §3.4).
 * `diaries` 등 기존 테이블은 건드리지 않는다. 문자열은 컴파일이 만든 `app/schemas/.../3.json`의 createSql과 같아야 한다
 * (정본 = 3.json, MigrationTest M1이 검증).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `diary_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `diaryId` INTEGER NOT NULL, `path` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`diaryId`) REFERENCES `diaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_photos_diaryId` ON `diary_photos` (`diaryId`)")
    }
}

/** 앱이 등록하는 마이그레이션 전부. 빌더와 테스트가 같은 목록을 쓴다. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)
