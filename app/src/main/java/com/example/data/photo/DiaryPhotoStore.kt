package com.example.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import com.example.data.ocr.BitmapDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 일기 사진 저장소 경계(명세 PHRASE-RECALL-SPEC-2026-11 §3.3, ADR-006).
 * 보관 사진은 자동으로는 기기 밖으로 나가지 않는다(사용자가 직접 실행하는 P5 zip 내보내기만 예외):
 * 루트가 noBackupFilesDir이라 클라우드 자동 백업·기기 간 자동 이전에서 빠진다.
 * 이 파일은 저장소 계층만 맡는다. 행과의 연결은 ReadingRepository·ReadingViewModel이 한다.
 */
interface DiaryPhotoStore {
    /** 디코드·회전·축소한 JPEG를 앱 내부 저장소에 쓰고 상대 경로를 돌려준다. 실패 시 null(일기 저장은 계속). */
    suspend fun save(source: Uri, rotationDegrees: Float, flipped: Boolean): String?

    /** 저장소 밖 경로(../ 등)와 파일 부재는 null — 화면은 «사진 없음»으로 그린다(dangling, §2.4). */
    fun fileFor(relativePath: String): File?

    suspend fun delete(relativePath: String)

    /**
     * 참조되지 않는 파일 삭제. 반환 = 지운 개수.
     * [save]와 동시에 부르지 않는다: 참조 목록을 만든 뒤 저장된 새 파일은 미참조로 보여 지워진다(호출자 = 앱 시작 1회, P4).
     */
    suspend fun sweep(referenced: Set<String>): Int
}

class InternalDiaryPhotoStore(context: Context) : DiaryPhotoStore {
    private val appContext = context.applicationContext

    /** `noBackupFilesDir/diary_photos/`. 백업 XML 변경 없이 자동 백업에서 빠진다(ADR-006 Q-b). */
    val root: File = File(appContext.noBackupFilesDir, DIR_NAME)

    override suspend fun save(source: Uri, rotationDegrees: Float, flipped: Boolean): String? =
        withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var target: File? = null
            try {
                // 크롭 없음: «당시 사진» 복기가 목적이라 페이지 전체를 남긴다(§2.4). 순서는 decodeForOcr와 같다(뒤집기 → 회전).
                val base = BitmapDecoding.decodeDownsampled(appContext, source) ?: return@withContext null
                val matrix = Matrix().apply {
                    if (flipped) postScale(-1f, 1f)
                    if (rotationDegrees != 0f) postRotate(rotationDegrees)
                }
                bitmap = if (matrix.isIdentity) base
                    else Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true).also { if (it !== base) base.recycle() }
                // mkdirs가 먼저: 동시 저장 둘이 같이 루트를 만들 때 늦은 쪽이 실패로 읽히지 않게.
                if (!root.mkdirs() && !root.isDirectory) return@withContext null
                val name = "${UUID.randomUUID()}.jpg"
                val file = File(root, name).also { target = it }
                val ok = file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (ok) name else { file.delete(); null }
            } catch (e: OutOfMemoryError) {
                target?.delete()
                null
            } catch (e: Exception) {
                target?.delete()
                null
            } finally {
                bitmap?.recycle()
            }
        }

    override fun fileFor(relativePath: String): File? {
        val file = resolve(relativePath) ?: return null
        return file.takeIf { it.isFile }
    }

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) { resolve(relativePath)?.delete() }
    }

    override suspend fun sweep(referenced: Set<String>): Int = withContext(Dispatchers.IO) {
        val keep = referenced.mapNotNull { canonicalOrNull(resolve(it)) }.toSet()
        // 정규화에 실패한 파일은 지우지 않고 건너뛴다(sweep 전체가 예외로 끝나지 않게).
        root.listFiles()?.count { f ->
            val path = canonicalOrNull(f)
            f.isFile && path != null && path !in keep && f.delete()
        } ?: 0
    }

    private fun canonicalOrNull(file: File?): String? = try {
        file?.canonicalPath
    } catch (e: Exception) {
        null
    }

    /** 루트 바로 아래 파일만 허용한다. 빈 값·절대 경로·`..`·하위 폴더는 null. */
    private fun resolve(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val candidate = File(root, relativePath)
        return try {
            candidate.takeIf { it.canonicalFile.parentFile == root.canonicalFile }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val DIR_NAME = "diary_photos"
        const val JPEG_QUALITY = 85
    }
}

/**
 * 촬영 임시 파일(`cacheDir/camera_photo_*.jpg`, OcrDiaryScreen) 정리 — ADR-002 «후속 발견» 해소.
 * 반환 = 지운 개수. 다른 캐시 파일은 건드리지 않는다.
 */
object CameraTempFiles {
    const val PREFIX = "camera_photo_"
    const val SUFFIX = ".jpg"

    fun deleteAll(cacheDir: File): Int = list(cacheDir).count { it.delete() }

    /** 지금 있는 촬영 임시 파일. 저장이 진행 중이면 이 목록만 저장 뒤에 지운다(그 사이 새로 찍은 파일은 건드리지 않게). */
    fun list(cacheDir: File): List<File> =
        cacheDir.listFiles()?.filter { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }.orEmpty()
}
