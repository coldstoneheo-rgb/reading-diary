package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.PhotoDrafts

/** dangling(행은 있고 파일이 없음) 표시 문구, 명세 §2.4. */
internal const val PHOTO_NOT_ON_DEVICE = "사진 없음(이 기기에 없음)"

/**
 * 편집 화면의 보관 사진 N장(명세 §3.5). 화면은 의도만 넘긴다.
 * @param models 사진별 Coil 모델(File 또는 Uri). null = dangling.
 */
@Composable
fun DiaryPhotoEditSection(
    models: List<Any?>,
    onPick: (replaceIndex: Int?, useCamera: Boolean) -> Unit,
    onRemove: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "이 기록의 사진 (${models.size}/${PhotoDrafts.MAX_PER_DIARY})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        if (models.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                models.forEachIndexed { index, model ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("diary_photo_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "사진 ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    PHOTO_NOT_ON_DEVICE,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Row {
                            PhotoSourceMenu(
                                onPick = { camera -> onPick(index, camera) }
                            ) { open ->
                                TextButton(onClick = open, modifier = Modifier.testTag("diary_photo_replace_$index")) { Text("바꾸기") }
                            }
                            TextButton(onClick = { onRemove(index) }, modifier = Modifier.testTag("diary_photo_remove_$index")) {
                                Text("지우기", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        if (models.size < PhotoDrafts.MAX_PER_DIARY) {
            PhotoSourceMenu(onPick = { camera -> onPick(null, camera) }) { open ->
                OutlinedButton(
                    onClick = open,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diary_photo_add"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  사진 추가")
                }
            }
        }
    }
}

/** 촬영·선택 메뉴. [anchor]가 받는 함수를 부르면 메뉴가 열린다. */
@Composable
private fun PhotoSourceMenu(onPick: (useCamera: Boolean) -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("사진 촬영") },
                onClick = { open = false; onPick(true) },
                modifier = Modifier.testTag("diary_photo_from_camera")
            )
            DropdownMenuItem(
                text = { Text("사진 선택") },
                onClick = { open = false; onPick(false) },
                modifier = Modifier.testTag("diary_photo_from_gallery")
            )
        }
    }
}
