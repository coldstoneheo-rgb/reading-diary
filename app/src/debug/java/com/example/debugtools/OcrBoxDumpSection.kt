package com.example.debugtools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 디버그 빌드 전용 설정 카드: 상자 덤프 개수 · 공유 · 모두 지우기. 릴리스 변형은 빈 스텁(src/release). */
@Composable
fun OcrBoxDumpSection() {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(OcrBoxDump.files(context).size) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("debug_ocr_dump_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("개발용 · 글자 상자 덤프 (디버그 빌드에만 있음)", style = MaterialTheme.typography.titleSmall)
            Text(
                "글자 인식을 할 때마다 줄·어절 상자를 TSV 파일로 이 기기 앱 저장소에 남깁니다(자동 백업 제외 · 인터넷 전송 없음). " +
                    "«덤프 공유»를 누를 때만 공유 창으로 TSV 파일만 보냅니다(사진은 보내지 않음). 지금 ${count}개.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    count = OcrBoxDump.files(context).size
                    val intent = OcrBoxDump.shareIntent(context)
                    if (intent == null) Toast.makeText(context, "덤프가 없어요", Toast.LENGTH_SHORT).show()
                    else context.startActivity(intent)
                }) { Text("덤프 공유") }
                OutlinedButton(onClick = {
                    val n = OcrBoxDump.deleteAll(context)
                    count = OcrBoxDump.files(context).size
                    Toast.makeText(context, "${n}개 지움", Toast.LENGTH_SHORT).show()
                }) { Text("모두 지우기") }
            }
        }
    }
}
