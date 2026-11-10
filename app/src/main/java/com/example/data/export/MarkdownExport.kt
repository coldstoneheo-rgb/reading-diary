package com.example.data.export

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.SharedWord

/** 만들 파일 하나. [fileName]은 확장자(.md)까지 포함한다. */
data class ExportFile(val fileName: String, val content: String)

/** 내보내기 계획. [skipped]는 폴더에 이미 같은 이름이 있어 쓰지 않을 파일 이름(확장자 포함). */
data class ExportPlan(val toCreate: List<ExportFile>, val skipped: List<String>)

/**
 * 마크다운 내보내기(STAGE2-PLAN ②, ADR-004 Q6·Q7)의 순수 부분. Android·Room에 의존하지 않아 JVM 테스트가 가능하다.
 * 쓰기는 [DocumentTree]가 하며, 이미 있는 이름은 이 계획 단계에서 빠진다 — 덮어쓰기·병합 0, 키워드 스텁 노트 0.
 */
object MarkdownExport {

    const val FOLDER_NAME = "독서 다이어리"
    const val INDEX_NAME = "독서 다이어리 목록"
    const val EXTENSION = ".md"
    const val MIME_TYPE = "text/markdown"
    private const val MAX_NAME_LENGTH = 120
    /** 이름 본체의 UTF-8 상한. 접미 « (999)»(6바이트)·«.md»(3바이트)를 붙여도 255바이트를 넘지 않는다. */
    const val MAX_NAME_BYTES = 240
    private const val EMPTY_TITLE = "제목 없음"

    /**
     * 파일 이름에 쓸 수 없는 문자. 파일 시스템 금지 문자 `/ \ : * ? " < > |`에 더해,
     * 인덱스 노트의 `[[이름]]` 링크를 깨는 `[ ] # ^`도 뺀다(위키링크 문법이 이 문자를 구분자로 쓴다).
     */
    private val FORBIDDEN = Regex("[/\\\\:*?\"<>|\\[\\]#^\\p{Cntrl}]")

    /**
     * 제목 → 확장자 없는 파일 이름. 120자 컷, 빈 값이면 «제목 없음».
     * 안드로이드 파일 시스템의 이름 한도는 255 **바이트**라 한글(3바이트)·이모지(4바이트)는 120자 안에서도 넘친다 →
     * UTF-8 [MAX_NAME_BYTES]바이트에서도 코드포인트 경계로 자른다(« (99)»·«.md» 여유). 계획의 «120자 컷»에 더한 규칙.
     */
    fun sanitize(title: String): String {
        var name = FORBIDDEN.replace(title, "").trim().trim('.').trim()
        var end = 0
        var bytes = 0
        var count = 0
        while (end < name.length && count < MAX_NAME_LENGTH) {
            val cp = name.codePointAt(end)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_NAME_BYTES) break
            bytes += size
            count++
            end += Character.charCount(cp)
        }
        if (end < name.length) name = name.substring(0, end).trim().trimEnd('.').trim()
        return name.ifEmpty { EMPTY_TITLE }
    }

    /**
     * 책 id → 확장자 없는 파일 이름. 같은 이름(대소문자 무시 — 대소문자를 가리지 않는 저장소가 있다)의 다른 책은
     * id 오름차순으로 « (2)», « (3)». 인덱스 노트 이름과 같으면 그 책도 « (2)»부터 붙는다.
     */
    fun fileNames(books: List<Book>): Map<Int, String> {
        val taken = hashSetOf(INDEX_NAME.lowercase())
        val result = LinkedHashMap<Int, String>()
        for (book in books.sortedBy { it.id }) {
            val base = sanitize(book.title)
            var candidate = base
            var n = 2
            while (candidate.lowercase() in taken) {
                candidate = "$base ($n)"
                n++
            }
            taken += candidate.lowercase()
            result[book.id] = candidate
        }
        return result
    }

    /** YAML frontmatter(title, author, status, rating, pages, exported) + 기록(쪽·발췌 인용·메모) + 이 책에 걸린 함께 나온 단어. */
    fun bookNote(book: Book, diaries: List<Diary>, linkedWords: List<String>, exportedDate: String): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("title: ").append(yamlString(book.title)).append('\n')
        sb.append("author: ").append(yamlString(book.author)).append('\n')
        sb.append("status: ").append(yamlString(statusLabel(book.status))).append('\n')
        sb.append("rating: ").append(book.rating).append('\n')
        sb.append("pages: ").append(book.totalPages).append('\n')
        sb.append("exported: ").append(exportedDate).append('\n')
        sb.append("---\n\n")
        sb.append("# ").append(book.title.trim().ifEmpty { EMPTY_TITLE }).append("\n\n")
        sb.append("## 기록\n\n")
        val ordered = diaries.sortedWith(compareBy({ it.page }, { it.createdAt }, { it.id }))
        if (ordered.isEmpty()) {
            sb.append("아직 남긴 기록이 없습니다.\n")
        }
        for (diary in ordered) {
            sb.append("### p.").append(diary.page).append("\n\n")
            val quote = diary.selectedText.trim()
            if (quote.isNotEmpty()) {
                quote.lines().forEach { line -> sb.append("> ").append(line).append('\n') }
                sb.append('\n')
            }
            val notes = diary.notes.trim()
            if (notes.isNotEmpty()) sb.append(notes).append("\n\n")
        }
        if (linkedWords.isNotEmpty()) {
            sb.append(if (ordered.isEmpty()) "\n" else "")
            sb.append("## 함께 나온 단어\n\n")
            sb.append(linkedWords.joinToString(" ") { "[[$it]]" }).append('\n')
        }
        return sb.toString()
    }

    /** 내보낸 책 목록. `[[파일 이름]]`으로 책 노트를 잇는다. */
    fun indexNote(books: List<Book>, fileNames: Map<Int, String>): String {
        val sb = StringBuilder("# ").append(INDEX_NAME).append("\n\n")
        books.sortedBy { it.id }.forEach { book ->
            val name = fileNames[book.id] ?: return@forEach
            sb.append("- [[").append(name).append("]]")
            if (book.author.isNotBlank()) sb.append(" — ").append(book.author.trim())
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * 책당 1파일 + 인덱스 노트 1장. [existingNames]에 이미 있는 이름(대소문자 무시)은 [ExportPlan.toCreate]에 넣지 않고 [ExportPlan.skipped]로.
     * 인덱스 노트도 예외 없음(있으면 건너뜀 — 알려진 한계). 공유 단어는 그 책이 걸린 것만 링크한다.
     */
    fun plan(
        books: List<Book>,
        diaries: List<Diary>,
        sharedWords: List<SharedWord>,
        existingNames: Set<String>,
        exportedDate: String
    ): ExportPlan {
        val existing = existingNames.map { it.lowercase() }.toSet()
        val names = fileNames(books)
        val diariesByBook = diaries.groupBy { it.bookId }
        val toCreate = mutableListOf<ExportFile>()
        val skipped = mutableListOf<String>()

        fun add(fileName: String, content: () -> String) {
            if (fileName.lowercase() in existing) skipped += fileName else toCreate += ExportFile(fileName, content())
        }

        for (book in books.sortedBy { it.id }) {
            val words = sharedWords.filter { book.id in it.bookIds }.map { it.word }
            add(names.getValue(book.id) + EXTENSION) {
                bookNote(book, diariesByBook[book.id].orEmpty(), words, exportedDate)
            }
        }
        add(INDEX_NAME + EXTENSION) { indexNote(books, names) }
        return ExportPlan(toCreate, skipped)
    }

    private fun statusLabel(status: String): String = when (status) {
        "READING" -> "읽는 중"
        "TO_READ" -> "읽을 책"
        "COMPLETED" -> "다 읽음"
        else -> status
    }

    /** 큰따옴표 YAML 문자열. 역슬래시·따옴표·줄바꿈을 이스케이프한다. */
    private fun yamlString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
