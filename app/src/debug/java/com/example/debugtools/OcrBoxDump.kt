package com.example.debugtools

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.ocr.BitmapDecoding
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 디버그 빌드 전용(src/debug) — 기준 자료 측정용 ML Kit 상자 덤프. 릴리스 변형은 src/release의 빈 스텁을 컴파일한다.
 *
 * - 저장: `noBackupFilesDir/ocr-dump/ocr-dump-NNN-<시각>.tsv` — 앱 전용 · 권한 0 · 자동 백업·기기 이전 제외.
 * - 꺼내기: 사용자가 설정 화면 «덤프 공유»를 누를 때만, TSV 사본을 `cacheDir/ocr-dump-share/`에 두고 기존
 *   FileProvider(`${applicationId}.fileprovider`, cache-path)로 공유 시트에 넘긴다. 사진은 넘기지 않는다(TSV만).
 * - 네트워크 호출 0. 인식 글을 로그에 남기지 않는다.
 *
 * 흐름: ViewModel이 인식 전에 [reset] → ML Kit 추출기가 결과 [Text]를 [record] → ViewModel이 인식 뒤 [finish]로 파일을 쓴다.
 */
object OcrBoxDump {
    private const val TAG = "OcrBoxDump"
    private const val DIR = "ocr-dump"
    private const val SHARE_DIR = "ocr-dump-share"

    @Volatile private var pending: List<OcrBoxDumpTsv.Block>? = null

    fun reset() { pending = null }

    /** ML Kit 콜백(메인 스레드)에서 부른다 — 상자·글만 가볍게 옮겨 두고 파일 쓰기는 [finish]에서. */
    fun record(text: Text) {
        pending = text.textBlocks.map { b ->
            OcrBoxDumpTsv.Block(
                b.boundingBox?.let { OcrBoxDumpTsv.Rect(it.left, it.top, it.right, it.bottom) },
                b.lines.map { l ->
                    OcrBoxDumpTsv.Line(
                        l.text,
                        l.boundingBox?.let { OcrBoxDumpTsv.Rect(it.left, it.top, it.right, it.bottom) },
                        l.elements.map { e ->
                            OcrBoxDumpTsv.Element(e.text, e.boundingBox?.let { OcrBoxDumpTsv.Rect(it.left, it.top, it.right, it.bottom) })
                        }
                    )
                }
            )
        }
    }

    /** [record]된 결과가 있으면 TSV 1개를 쓰고 비운다. 없으면(다른 추출기·실패) 아무것도 하지 않는다. 실패는 삼킨다(디버그 도구가 인식을 막지 않게). */
    suspend fun finish(
        context: Context, imageUri: Uri, rotationDegrees: Float, flipped: Boolean,
        cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
        bitmapWidth: Int, bitmapHeight: Int
    ) {
        val blocks = pending ?: return
        pending = null
        withContext(Dispatchers.IO) {
            try {
                val (sw, sh) = BitmapDecoding.orientedSize(context, imageUri) ?: (bitmapWidth to bitmapHeight)
                val input = OcrBoxDumpTsv.Input(sw, sh, rotationDegrees, flipped, cropLeft, cropTop, cropRight, cropBottom, bitmapWidth, bitmapHeight)
                val now = Date()
                val tsv = OcrBoxDumpTsv.format(blocks, input, SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(now), "mlkit-text-recognition-korean")
                val dir = dumpDir(context)
                val seq = (dir.listFiles()?.count { it.name.endsWith(".tsv") } ?: 0) + 1
                val name = "ocr-dump-%03d-%s.tsv".format(Locale.ROOT, seq, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now))
                File(dir, name).writeText(tsv)
            } catch (e: Exception) {
                Log.w(TAG, "dump failed: ${e.javaClass.simpleName}")
            }
        }
    }

    fun dumpDir(context: Context): File = File(context.noBackupFilesDir, DIR).apply { mkdirs() }

    fun files(context: Context): List<File> =
        dumpDir(context).listFiles { f -> f.isFile && f.name.endsWith(".tsv") }?.sortedBy { it.name }.orEmpty()

    fun deleteAll(context: Context): Int = files(context).count { it.delete() }

    /** 공유 시트 인텐트. 덤프가 0개면 null. 사본만 cacheDir에 두고 원본 폴더는 공유하지 않는다. */
    fun shareIntent(context: Context): Intent? {
        val src = files(context)
        if (src.isEmpty()) return null
        val shareDir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively(); mkdirs() }
        val uris = ArrayList<Uri>(src.map { f ->
            val copy = File(shareDir, f.name).also { f.copyTo(it, overwrite = true) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
        })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri("ocr-dump", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "상자 덤프 ${uris.size}개 보내기").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
