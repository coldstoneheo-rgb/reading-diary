package com.example.data.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit Text Recognition v2(한국어 스크립트)로 온디바이스 추출. 언번들 배포(ADR-002 Q1):
 * 모델은 Google Play 서비스가 첫 사용 시 내려받는다. 사진은 기기 밖으로 나가지 않는다.
 *
 * 인식된 텍스트는 로그에 남기지 않는다.
 */
class MlKitTextExtractor : TextExtractor {

    override suspend fun extract(bitmap: Bitmap): OcrOutcome {
        // getClient도 try 안에: ML Kit 초기화 Provider가 없는 기기/프로세스에서는 여기서 IllegalStateException이 난다
        var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        return try {
            recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            val image = InputImage.fromBitmap(bitmap, 0)
            val blocks = suspendCancellableCoroutine<List<OcrBlock>> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val mapped = text.textBlocks.map { block ->
                            val box = block.boundingBox
                            OcrBlock(
                                lines = block.lines.map { it.text },
                                top = box?.top ?: 0,
                                left = box?.left ?: 0
                            )
                        }
                        if (cont.isActive) cont.resume(mapped)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                    }
            }
            val assembled = OcrTextAssembler.assemble(blocks)
            if (assembled.isBlank()) OcrOutcome.NoText else OcrOutcome.Text(assembled)
        } catch (e: CancellationException) {
            throw e // 구조적 동시성: 취소는 결과로 바꾸지 않는다
        } catch (e: MlKitException) {
            Log.w(TAG, "ML Kit unavailable: code=${e.errorCode}")
            OcrOutcome.Failed(messageFor(e.errorCode))
        } catch (e: IllegalStateException) {
            // getClient 단계: ML Kit 초기화 Provider가 없는 프로세스/기기
            Log.w(TAG, "ML Kit not initialized")
            OcrOutcome.Failed("이 기기에서 글자 인식을 시작하지 못했어요(Google Play 서비스 필요). 직접 입력해 주세요.")
        } finally {
            recognizer?.close()
        }
        // 그 밖의 예외는 ViewModel의 마지막 방어선(ENGINE_ERROR_MESSAGE)이 받는다
    }

    private fun messageFor(code: Int): String = when (code) {
        MlKitException.UNAVAILABLE ->
            "글자 인식 모델을 준비하는 중이에요(최초 1회, Google Play 서비스가 내려받습니다). 잠시 후 다시 시도하거나 직접 입력해 주세요."
        MlKitException.NETWORK_ISSUE ->
            "인식 모델을 처음 내려받으려면 인터넷 연결이 필요해요. 연결 후 다시 시도하거나 직접 입력해 주세요."
        else ->
            "이 기기에서는 글자 인식을 사용할 수 없어요(Google Play 서비스 필요). 직접 입력해 주세요."
    }

    private companion object {
        const val TAG = "MlKitTextExtractor"
    }
}
