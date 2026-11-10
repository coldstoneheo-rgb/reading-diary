package com.example

import android.net.Uri
import com.example.data.photo.DiaryPhotoStore
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** 파일 없이 저장·삭제·sweep 호출을 기록한다. [gate]를 걸면 save가 그 자리에서 기다린다(경쟁 시나리오용). */
class FakeDiaryPhotoStore(
    private val failFor: Set<Uri> = emptySet(),
    var gate: CompletableDeferred<Unit>? = null,
    private val failDelete: Boolean = false
) : DiaryPhotoStore {
    val files = linkedSetOf<String>()
    val deleted = mutableListOf<String>()
    val sweepRefs = mutableListOf<Set<String>>()
    private var next = 0

    fun seed(vararg paths: String) { files += paths }

    override suspend fun save(source: Uri, rotationDegrees: Float, flipped: Boolean): String? {
        gate?.await()
        if (source in failFor) return null
        return "new${next++}.jpg".also { files += it }
    }

    override fun fileFor(relativePath: String): File? =
        if (relativePath in files) File("fake", relativePath) else null

    override suspend fun delete(relativePath: String) {
        deleted += relativePath
        if (failDelete) throw java.io.IOException("delete failed")
        files -= relativePath
    }

    override suspend fun importFile(relativePath: String, source: File): Boolean {
        if (relativePath in files) return false
        files += relativePath
        return true
    }

    override suspend fun sweep(referenced: Set<String>): Int {
        sweepRefs += referenced
        val orphans = files.filter { it !in referenced }
        files -= orphans.toSet()
        return orphans.size
    }
}
