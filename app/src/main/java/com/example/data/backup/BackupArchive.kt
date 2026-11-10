package com.example.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 백업 zip 쓰기·풀기·검증(명세 §3.6). `InputStream`/`OutputStream` 경계라 SAF 없이 테스트한다.
 *
 * 구성: `manifest.json` + `db/reading_diary.db`(Room DB 스냅숏 파일) + `photos/<UUID>.jpg`.
 * 키 저장소(EncryptedSharedPreferences)·설정은 **넣지 않는다** — 이 파일은 위 세 종류 말고는 쓰지도 받지도 않는다.
 */
object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DB_ENTRY = "db/reading_diary.db"
    const val PHOTO_DIR = "photos/"

    /** 압축 해제 상한(zip 폭탄 방어). 오너 실측 레퍼런스 50~58MB의 수십 배. */
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 50_000

    /** 사진 이름 규칙(DiaryPhotoStore가 만드는 이름과 같은 모양): 경로 구분자·`..` 없는 파일 이름 하나. */
    private val PHOTO_NAME = Regex("[A-Za-z0-9._-]{1,128}")

    /**
     * [photos]: (diary_photos.path, 그 파일). 파일이 없는 사진은 호출자가 빼고 넘긴다.
     * [onProgress]: 사진 하나를 담을 때마다 (담은 수, 전체 수).
     */
    fun write(
        out: OutputStream,
        manifest: BackupManifest,
        dbFile: File,
        photos: List<Pair<String, File>>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val zip = ZipOutputStream(out.buffered())
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(DB_ENTRY))
        dbFile.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        photos.forEachIndexed { i, (name, file) ->
            require(PHOTO_NAME.matches(name) && name != "." && name != "..") { "bad photo name" }
            zip.putNextEntry(ZipEntry(PHOTO_DIR + name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            onProgress(i + 1, photos.size)
        }
        zip.finish()
        zip.flush()
    }

    /** 검증을 통과해 풀린 백업. 파일은 모두 [dir] 안에 있다. */
    data class Extracted(val manifest: BackupManifest, val dbFile: File, val photoDir: File, val photoNames: Set<String>)

    sealed class Result {
        data class Ok(val extracted: Extracted) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    enum class Reason { NOT_ZIP, NO_MANIFEST, BAD_MANIFEST, UNSAFE_PATH, UNKNOWN_ENTRY, NEWER_VERSION, UNSUPPORTED_VERSION, NO_DATABASE, TOO_LARGE }

    /**
     * [input]을 빈 폴더 [dir]에 풀고 검증한다. 기존 기록은 건드리지 않는다(교체는 호출자가 검증 통과 뒤에만).
     * 거부: manifest 없음/손상 · 항목 경로 탈출(`../`, 절대 경로, 역슬래시, 드라이브) · 모르는 항목 · 앱보다 높은 Room 버전 ·
     * 형식 버전 불일치 · DB 없음 · 크기·개수 상한 초과. Invalid면 [dir]의 내용은 호출자가 지운다.
     */
    fun extract(input: InputStream, dir: File, appRoomVersion: Int, minRoomVersion: Int): Result {
        require(dir.isDirectory && dir.list().isNullOrEmpty()) { "dir must be an empty directory" }
        val photoDir = File(dir, "photos").apply { mkdirs() }
        val dbFile = File(dir, "backup.db")
        var manifestText: String? = null
        var sawDb = false
        val photoNames = linkedSetOf<String>()
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) return Result.Invalid(Reason.TOO_LARGE)
                    val name = entry.name
                    if (!isSafeName(name)) return Result.Invalid(Reason.UNSAFE_PATH)
                    val target: File? = when {
                        entry.isDirectory -> if (name == PHOTO_DIR || name == "db/") null else return Result.Invalid(Reason.UNKNOWN_ENTRY)
                        name == MANIFEST -> null
                        name == DB_ENTRY -> dbFile.also { sawDb = true }
                        name.startsWith(PHOTO_DIR) -> {
                            val photo = name.removePrefix(PHOTO_DIR)
                            if (!PHOTO_NAME.matches(photo) || photo == "." || photo == "..") return Result.Invalid(Reason.UNSAFE_PATH)
                            photoNames += photo
                            File(photoDir, photo)
                        }
                        else -> return Result.Invalid(Reason.UNKNOWN_ENTRY)
                    }
                    if (entry.isDirectory) continue
                    if (target == null) {
                        // manifest: 작아야 한다(64KB).
                        val bytes = readLimited(zip, 64 * 1024) ?: return Result.Invalid(Reason.BAD_MANIFEST)
                        total += bytes.size
                        manifestText = String(bytes, Charsets.UTF_8)
                    } else {
                        // 두 겹 확인: 이름 규칙에 더해 실제 경로가 dir 안인지.
                        if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) return Result.Invalid(Reason.UNSAFE_PATH)
                        val written = target.outputStream().use { copyLimited(zip, it, MAX_TOTAL_BYTES - total) }
                            ?: return Result.Invalid(Reason.TOO_LARGE)
                        total += written
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: java.util.zip.ZipException) {
            return Result.Invalid(Reason.NOT_ZIP)
        } catch (e: java.io.IOException) {
            return Result.Invalid(Reason.NOT_ZIP)
        }
        if (entries == 0) return Result.Invalid(Reason.NOT_ZIP)
        val text = manifestText ?: return Result.Invalid(Reason.NO_MANIFEST)
        val manifest = BackupManifest.parse(text) ?: return Result.Invalid(Reason.BAD_MANIFEST)
        if (manifest.formatVersion != BackupManifest.FORMAT_VERSION) return Result.Invalid(Reason.UNSUPPORTED_VERSION)
        if (manifest.roomVersion > appRoomVersion) return Result.Invalid(Reason.NEWER_VERSION)
        if (manifest.roomVersion < minRoomVersion) return Result.Invalid(Reason.UNSUPPORTED_VERSION)
        if (!sawDb) return Result.Invalid(Reason.NO_DATABASE)
        return Result.Ok(Extracted(manifest, dbFile, photoDir, photoNames))
    }

    /** 상대 경로·`/` 구분자만. 절대 경로·드라이브·역슬래시·`.`/`..` 구간·빈 구간은 거부. */
    internal fun isSafeName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains(':') || name.contains('\u0000')) return false
        val parts = name.removeSuffix("/").split('/')
        return parts.none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        return if (copyLimited(input, buffer, limit.toLong()) == null) null else buffer.toByteArray()
    }

    /** 복사한 바이트 수. [limit]을 넘으면 null. */
    private fun copyLimited(input: InputStream, out: OutputStream, limit: Long): Long? {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return copied
            copied += n
            if (copied > limit) return null
            out.write(buf, 0, n)
        }
    }
}
