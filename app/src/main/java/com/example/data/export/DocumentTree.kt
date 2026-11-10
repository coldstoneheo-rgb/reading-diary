package com.example.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 내보내기 쓰기 경계(STAGE2-PLAN ②, ADR-004 Q7). **새 문서를 만들고 거기에만 쓴다** —
 * 이미 있는 문서를 여는 API는 이 인터페이스에 없다. 덮어쓰기·병합 경로가 구조적으로 0이다.
 */
interface DocumentTree {
    /** 대상 폴더에 이미 있는 파일 이름(확장자 포함). */
    fun existingNames(): Set<String>

    /**
     * 새 문서를 만들고 [content]를 쓴다. 성공하면 프로바이더가 실제로 붙인 이름(같은 이름이 생겨 «이름 (1).md»처럼 바꾸는
     * 프로바이더가 있다)을, 실패하면 null을 돌려준다.
     */
    fun createAndWrite(fileName: String, mimeType: String, content: String): String?
}

/**
 * 사용자가 `OpenDocumentTree`로 고른 폴더 아래 [MarkdownExport.FOLDER_NAME] 하위 폴더(없으면 생성, 있으면 재사용)에 쓴다.
 * `DocumentsContract.createDocument` + 새 URI의 `openOutputStream`만 쓴다. 새 의존성 0.
 */
class SafDocumentTree(context: Context, private val treeUri: Uri) : DocumentTree {
    private val resolver = context.contentResolver

    /** 하위 폴더 문서 URI. 처음 쓸 때 찾거나 만든다. 만들지 못하면 예외. */
    private val folderUri: Uri by lazy {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        children(rootId).firstOrNull { it.name == MarkdownExport.FOLDER_NAME && it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
            ?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id) }
            ?: DocumentsContract.createDocument(resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, MarkdownExport.FOLDER_NAME)
            ?: error("folder not created")
    }

    override fun existingNames(): Set<String> =
        children(DocumentsContract.getDocumentId(folderUri)).map { it.name }.toSet()

    override fun createAndWrite(fileName: String, mimeType: String, content: String): String? {
        val created = try {
            DocumentsContract.createDocument(resolver, folderUri, mimeType, fileName)
        } catch (e: Exception) {
            null
        } ?: return null
        try {
            val stream = resolver.openOutputStream(created) ?: error("no output stream")
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            // 방금 만든 빈 문서만 지운다(남겨 두면 다음 내보내기가 그 이름을 영원히 건너뛴다). 원래 있던 문서는 건드리지 않는다.
            try { DocumentsContract.deleteDocument(resolver, created) } catch (_: Exception) {}
            return null
        }
        // 다 쓴 뒤의 이름 조회 실패는 쓰기 실패가 아니다.
        return try { displayName(created) } catch (e: Exception) { null } ?: fileName
    }

    private data class Child(val id: String, val name: String, val mime: String)

    private fun children(parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<Child>()
        resolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                result += Child(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return result
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
