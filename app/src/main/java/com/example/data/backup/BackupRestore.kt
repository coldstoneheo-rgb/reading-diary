package com.example.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.data.ALL_MIGRATIONS
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.photo.DiaryPhotoStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** 복원 결과. [Restored]의 수는 복원된 행·들여온 사진 파일 수. */
sealed class RestoreOutcome {
    /** [photoFiles]: 복원 뒤 이 기기에 파일이 있는 사진 경로 수. [missingPhotos]: 파일이 없어 «사진 없음»으로 보일 경로 수. */
    data class Restored(val books: Int, val diaries: Int, val photoRows: Int, val photoFiles: Int, val missingPhotos: Int) : RestoreOutcome()
    data class Rejected(val reason: BackupArchive.Reason?) : RestoreOutcome()
    /** 교체 도중 실패 — 트랜잭션이 되돌아가 기존 기록은 그대로다. */
    object Failed : RestoreOutcome()
}

/**
 * 백업 zip 만들기·복원(명세 §3.6, ADR-006 Q-b). 앱 서버 0 · 자동 실행 0 · 네트워크 0 — 스트림은 사용자가 SAF로 고른 위치에서 온다.
 *
 * DB는 Room 파일을 그대로 복사하지 않고, 같은 스키마의 **스냅숏 DB**를 새로 만들어 행을 옮긴 뒤 그 파일을 담는다
 * (WAL·동시 쓰기와 무관한 일관된 사본). 복원은 스냅숏을 같은 [ALL_MIGRATIONS]로 열어(낮은 버전은 여기서 올라간다) 행을 읽고,
 * 살아 있는 DB에서 한 트랜잭션으로 전부 지우고 넣는다 — 병합 0, 실패하면 되돌아간다.
 * 호출자는 사진 저장과 겹치지 않게 직렬화한다(ReadingViewModel.photoIoMutex).
 */
class BackupRestore(
    private val context: Context,
    private val database: AppDatabase,
    private val photoStore: DiaryPhotoStore
) {
    private val appContext = context.applicationContext

    /** [out]에 zip을 쓴다. 반환 = 담은 manifest. [out]은 닫지 않는다. */
    suspend fun createBackup(out: OutputStream, now: Long = System.currentTimeMillis(), onProgress: (Int, Int) -> Unit = { _, _ -> }): BackupManifest {
        val dao = database.backupDao()
        val rows = database.withTransaction { Rows(dao.bookcases(), dao.books(), dao.diaries(), dao.photos()) }
        val photoFiles = rows.photos.map { it.path }.distinct().mapNotNull { path -> photoStore.fileFor(path)?.let { path to it } }
        val manifest = BackupManifest(
            formatVersion = BackupManifest.FORMAT_VERSION,
            roomVersion = ROOM_VERSION,
            createdAt = now,
            bookcases = rows.bookcases.size,
            books = rows.books.size,
            diaries = rows.diaries.size,
            photoRows = rows.photos.size,
            photoFiles = photoFiles.size
        )
        val name = "backup_snapshot_${UUID.randomUUID()}"
        try {
            val snapshot = snapshotBuilder(name).build()
            try {
                snapshot.withTransaction { rows.insertInto(snapshot) }
            } finally {
                snapshot.close()
            }
            BackupArchive.write(out, manifest, appContext.getDatabasePath(name), photoFiles, onProgress)
        } finally {
            appContext.deleteDatabase(name)
        }
        return manifest
    }

    /**
     * [input]의 zip으로 지금 기록을 **교체**한다. 임시 위치에 풀어 검증 → 스냅숏을 열어 행 수를 manifest와 대조 →
     * 사진 파일 들여오기(이미 있는 이름은 그대로) → 한 트랜잭션으로 교체. 어느 단계든 실패하면 기존 기록은 그대로이고,
     * 이번에 새로 들여온 사진 파일만 지운다. 성공하면 예전 사진 중 더는 참조되지 않는 파일을 정리한다.
     * [beforeCommit]: 테스트가 교체 트랜잭션 안(지운 뒤·넣은 뒤)에서 실패를 주입하는 자리.
     */
    suspend fun restore(input: InputStream, beforeCommit: suspend () -> Unit = {}): RestoreOutcome {
        val tempDir = File(appContext.cacheDir, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        val snapshotName = "restore_snapshot_${UUID.randomUUID()}"
        val added = mutableListOf<String>()
        try {
            val extracted = when (val r = BackupArchive.extract(input, tempDir, ROOM_VERSION, MIN_ROOM_VERSION)) {
                is BackupArchive.Result.Invalid -> return RestoreOutcome.Rejected(r.reason)
                is BackupArchive.Result.Ok -> r.extracted
            }
            val snapshotFile = appContext.getDatabasePath(snapshotName)
            snapshotFile.parentFile?.mkdirs()
            if (!extracted.dbFile.renameTo(snapshotFile)) extracted.dbFile.copyTo(snapshotFile, overwrite = true)
            // 프레임워크 기본 오류 처리기는 손상 파일을 지우고 빈 DB로 다시 연다 → 빈 기록으로 «교체»될 수 있다.
            // Room으로 열기 전에 읽기 전용 + 삭제하지 않는 처리기로 무결성을 먼저 본다.
            if (!passesIntegrityCheck(snapshotFile)) return RestoreOutcome.Rejected(BackupArchive.Reason.BAD_DATABASE)
            val rows = try {
                val snapshot = snapshotBuilder(snapshotName).build()
                try {
                    val dao = snapshot.backupDao()
                    snapshot.withTransaction { Rows(dao.bookcases(), dao.books(), dao.diaries(), dao.photos()) }
                } finally {
                    snapshot.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return RestoreOutcome.Rejected(BackupArchive.Reason.BAD_DATABASE) // DB 파일 손상·열 수 없는 버전
            }
            val m = extracted.manifest
            if (rows.bookcases.size != m.bookcases || rows.books.size != m.books || rows.diaries.size != m.diaries || rows.photos.size != m.photoRows) {
                return RestoreOutcome.Rejected(BackupArchive.Reason.BAD_MANIFEST)
            }

            // 들여오기 → 교체 → 정리는 취소되지 않게 한 덩어리로 끝낸다. 커밋 직후 취소되면 방금 들여온(이미 참조되는) 사진을
            // 지워 버리는 경로가 생긴다(critic P5 사전 지적 4).
            val committed = withContext(NonCancellable) {
                try {
                    for (path in rows.photos.map { it.path }.distinct()) {
                        if (path !in extracted.photoNames) continue // zip에 없는 사진 → 행은 남고 «사진 없음»(dangling)
                        if (photoStore.importFile(path, File(extracted.photoDir, path))) added += path
                    }
                    database.withTransaction {
                        val dao = database.backupDao()
                        dao.deletePhotos()
                        dao.deleteDiaries()
                        dao.deleteBooks()
                        dao.deleteBookcases()
                        rows.insertInto(database)
                        beforeCommit()
                    }
                    true
                } catch (e: Exception) {
                    added.forEach { runCatching { photoStore.delete(it) } }
                    false
                }
            }
            if (!committed) return RestoreOutcome.Failed
            // 예시 책 표식은 책 id로 저장돼 있어, 교체 뒤에는 다른 책을 가리킬 수 있다 → 지운다(지우기 버튼이 진짜 책을 지우지 않게).
            AppDatabase.clearSampleMarks(appContext)
            val paths = rows.photos.map { it.path }.distinct()
            withContext(NonCancellable) { runCatching { photoStore.sweep(paths.toSet()) } }
            val present = paths.count { photoStore.fileFor(it) != null }
            return RestoreOutcome.Restored(rows.books.size, rows.diaries.size, rows.photos.size, present, paths.size - present)
        } finally {
            appContext.deleteDatabase(snapshotName)
            tempDir.deleteRecursively()
        }
    }

    private data class Rows(val bookcases: List<Bookcase>, val books: List<Book>, val diaries: List<Diary>, val photos: List<DiaryPhoto>) {
        suspend fun insertInto(db: AppDatabase) {
            val dao = db.backupDao()
            dao.insertBookcases(bookcases)
            dao.insertBooks(books)
            dao.insertDiaries(diaries)
            dao.insertPhotos(photos)
        }
    }

    private fun passesIntegrityCheck(file: File): Boolean = try {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ) { throw android.database.sqlite.SQLiteException("corrupt backup database") }.use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
        }
    } catch (e: Exception) {
        false
    }

    /** 앱 빌더와 같은 마이그레이션, 콜백 없음(시드 책장을 넣지 않는다), 단일 파일(TRUNCATE 저널). */
    private fun snapshotBuilder(name: String): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder(appContext, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

    companion object {
        private const val CREATE_SNAPSHOT_PREFIX = "backup_snapshot_"
        private const val RESTORE_SNAPSHOT_PREFIX = "restore_snapshot_"
        private const val RESTORE_DIR_PREFIX = "restore_"

        /**
         * 만들기·복원 도중 프로세스가 죽으면 남는 스냅숏 DB(기록 전체 사본 — 자동 백업에도 실린다)와 임시 해제 폴더를 지운다.
         * 앱 시작 정리(사진 sweep과 같은 자리, photoIoMutex 안)에서 1회 부른다. 반환 = 지운 항목 수.
         */
        fun cleanupLeftovers(context: Context): Int {
            val app = context.applicationContext
            var removed = 0
            app.databaseList().filter { it.startsWith(CREATE_SNAPSHOT_PREFIX) || it.startsWith(RESTORE_SNAPSHOT_PREFIX) }
                .filterNot { it.endsWith("-journal") || it.endsWith("-wal") || it.endsWith("-shm") }
                .forEach { if (app.deleteDatabase(it)) removed++ }
            app.cacheDir.listFiles()?.filter { it.isDirectory && it.name.startsWith(RESTORE_DIR_PREFIX) }
                ?.forEach { if (it.deleteRecursively()) removed++ }
            return removed
        }

        /** `@Database(version = …)`와 같아야 한다(BackupArchiveTest.roomVersionConstantMatchesLatestExportedSchema가 스키마 JSON 최신 번호와 대조). */
        const val ROOM_VERSION = 3
        /** 등록된 마이그레이션의 가장 낮은 시작 버전(MIGRATION_2_3). 그보다 낮은 백업은 열 수 없다. */
        const val MIN_ROOM_VERSION = 2
    }
}
