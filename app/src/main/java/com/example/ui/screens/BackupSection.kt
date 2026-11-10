package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.BackupState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 백업 파일 이름 제안. 사용자가 저장 창에서 바꿀 수 있다. */
object BackupFileNames {
    fun suggested(now: Long): String =
        "독서다이어리-백업-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now)) + ".zip"
}

/**
 * 설정의 백업 파일 메뉴(명세 §3.6). 상태만 받는 stateless 컴포저블.
 * «백업»이라 부르는 것은 이 기능뿐이다 — 복원이 있다(ADR-005 §6 개정). 마크다운 내보내기는 백업이 아니다.
 */
@Composable
fun BackupSection(state: BackupState, onCreate: () -> Unit, onRestore: () -> Unit) {
    val running = state is BackupState.Running
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "백업 파일: 책·책장·일기·기록 사진을 zip 파일 하나로 만들어 내가 고른 위치(기기 폴더, Google 드라이브 등)에 저장합니다. " +
                "API 키와 앱 설정은 넣지 않습니다. 버튼을 누를 때만 만들어집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCreate,
                enabled = !running,
                modifier = Modifier.weight(1f).testTag("settings_backup_create")
            ) { Text("백업 파일 만들기") }
            OutlinedButton(
                onClick = onRestore,
                enabled = !running,
                modifier = Modifier.weight(1f).testTag("settings_backup_restore")
            ) { Text("백업 파일에서 복원") }
        }
    }
}

/** 복원 확인. 교체(병합 0)임을 분명히 적는다. */
@Composable
fun RestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_backup_restore_dialog"),
        title = { Text("백업 파일로 복원할까요?") },
        text = {
            Text(
                "지금 기록이 백업 파일 내용으로 바뀝니다. 지금 기록과 합치지 않으며, 바뀐 뒤에는 되돌릴 수 없습니다. " +
                    "필요하면 먼저 «백업 파일 만들기»로 지금 기록을 저장해 두세요. 파일을 검사해 문제가 있으면 바꾸지 않습니다."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("settings_backup_restore_confirm")) { Text("복원하기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 진행 표시. 도중에 닫을 수 없다(복원 중 화면 조작으로 기록이 바뀌지 않게). */
@Composable
fun BackupProgressDialog(state: BackupState) {
    if (state !is BackupState.Running) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag("settings_backup_progress"),
        title = { Text(if (state.restoring) "복원하는 중…" else "백업 파일을 만드는 중…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.total > 0) {
                    LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    Text("사진 ${state.done} / ${state.total}")
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                Text("앱을 닫지 말고 기다려 주세요.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {}
    )
}

@Composable
fun BackupResultDialog(state: BackupState, onDismiss: () -> Unit) {
    val (title, body) = when (state) {
        is BackupState.Created -> "백업 파일을 만들었어요" to
            "책 ${state.books}권 · 기록 ${state.diaries}개 · 사진 ${state.photos}장을 담았습니다."
        is BackupState.Restored -> "복원했어요" to buildString {
            append("책 ${state.books}권 · 기록 ${state.diaries}개 · 사진 ${state.photos}장으로 바꿨습니다.")
            if (state.missingPhotos > 0) append(" 백업 파일에 없던 사진 ${state.missingPhotos}장은 «사진 없음»으로 보입니다.")
        }
        is BackupState.Error -> (if (state.restoring) "복원하지 못했어요" else "백업 파일을 만들지 못했어요") to state.message
        else -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_backup_result"),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } }
    )
}
