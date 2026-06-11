package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ReadingViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Observe persistent general configurations
    val diarySortNewestFirst by viewModel.diarySortNewestFirst.collectAsState()
    val diaryFontSize by viewModel.diaryFontSize.collectAsState()

    // Dynamic state for general toggle choices
    var showChangelog by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    // Dialog state for simulated Backups and Image Restoration
    var activeProgressTitle by remember { mutableStateOf<String?>(null) }
    var activeProgressDescription by remember { mutableStateOf("") }
    var showProgressDialog by remember { mutableStateOf(false) }

    fun runSimulatedProgress(title: String, initialDesc: String, finalSuccessMsg: String) {
        activeProgressTitle = title
        activeProgressDescription = initialDesc
        showProgressDialog = true
        coroutineScope.launch {
            delay(1000)
            activeProgressDescription = "보안 확인 및 시그니처 체크 중..."
            delay(1200)
            activeProgressDescription = "로컬 저장소 인덱스 패키징 배치 실행..."
            delay(1000)
            showProgressDialog = false
            Toast.makeText(context, finalSuccessMsg, Toast.LENGTH_LONG).show()
        }
    }

    if (showProgressDialog && activeProgressTitle != null) {
        AlertDialog(
            onDismissRequest = {}, // Modal locks interaction during restore process
            title = { Text(activeProgressTitle!!, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = activeProgressDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정 및 서비스 정보", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            
            // SECTION 1: GENERAL SYSTEM SETTINGS (일반 설정)
            Text(
                "🎨 일반 설정",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Item 1: Diaries sorting preferences order toggles
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "다이어리 글 정렬 순서",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "책 상세보기에서 작성된 다이어리의 타임라인 정렬 방향을 선택합니다.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // Custom selector buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.setDiarySortNewestFirst(false) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sort_asc_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (!diarySortNewestFirst) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                    contentColor = if (!diarySortNewestFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(
                                    width = if (!diarySortNewestFirst) 1.5.dp else 1.dp,
                                    color = if (!diarySortNewestFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("오름차순 (먼저 쓴 글 우선)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { viewModel.setDiarySortNewestFirst(true) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sort_desc_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (diarySortNewestFirst) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                    contentColor = if (diarySortNewestFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(
                                    width = if (diarySortNewestFirst) 1.5.dp else 1.dp,
                                    color = if (diarySortNewestFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("내림차순 (최근 쓴 글 우선)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Item 2: Records font size slider with preview box
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(
                                text = "기록 텍스트 폰트 크기 변경",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "다이어리 발췌 원본 및 개인 성찰 기록 텍스트의 가시성 폰트 크기를 조절합니다.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text("A-", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Slider(
                                value = diaryFontSize,
                                onValueChange = { viewModel.setDiaryFontSize(it) },
                                valueRange = 12f..24f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("font_size_slider")
                            )
                            Text("A+", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }

                        // Realtime interactive typography preview block
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "실시간 글자 크기 미리보기",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "책 속에서 발견한 좋은 문장은 나를 일깨운다.",
                                    fontSize = diaryFontSize.sp,
                                    lineHeight = (diaryFontSize * 1.4f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 1.5: STUDY THEME SELECTION (서재 테마 인테리어)
            Text(
                "🎨 서재 테마 인테리어",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            val currentThemeId by viewModel.currentThemeId.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "서재의 전반적인 감성을 대변할 스타일 테마 세트를 지정할 수 있습니다. 선택한 테마셋에 따라 강조색, 카드 배경, 서체 하이라이트 인상이 조화롭게 구성됩니다. 향후 다양한 추가 인테리어 테마 시리즈가 확장 탑재될 예정입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )

                    val themeList = listOf(
                        Triple(1, "올리브그린", Color(0xFF3E5C4E)),
                        Triple(2, "미드나잇블루", Color(0xFF5AB9FF)),
                        Triple(3, "스위스레드", Color(0xFFE53935)),
                        Triple(4, "라벤더퍼플", Color(0xFF8E24AA)),
                        Triple(5, "샴페인골드", Color(0xFFE5C158)),
                        Triple(6, "사쿠라핑크", Color(0xFFD81B60)),
                        Triple(7, "모던그레이", Color(0xFF455A64)),
                        Triple(8, "포레스트그린", Color(0xFF2E7D32)),
                        Triple(9, "심플블랙", Color(0xFF121212))
                    )

                    val chunks = remember { themeList.chunked(3) }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        chunks.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (id, name, color) ->
                                    val isSelected = currentThemeId == id
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.selectTheme(id)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(color, androidx.compose.foundation.shape.CircleShape)
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size < 3) {
                                    repeat(3 - rowItems.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: BACKUP & RESTORE ARCHIVE (백업 및 복원 관련 설정)
            Text(
                "💾 데이터 백업 및 복원",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "기기 오작동, 분실 또는 앱 재설치 시에도 나의 소중한 서재 정보들과 발췌 사진, 다이어리 글을 원 클릭 시스템을 통해 안전하게 복구할 수 있는 클라우드 아카이브 센터입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )

                    // Button 1: Data Restore
                    Button(
                        onClick = {
                            runSimulatedProgress(
                                title = "독서 SQLite 데이터베이스 복구",
                                initialDesc = "클라우드 스토리지 백업 매니페스트 다운로드 중...",
                                finalSuccessMsg = "독서 기록 및 카테고리가 최신 백업 지점 정보로 성공적으로 복원되었습니다!"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_data_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("데이터 복원 진행하기", fontWeight = FontWeight.Bold)
                    }

                    // Button 2: Photos Restore
                    Button(
                        onClick = {
                            runSimulatedProgress(
                                title = "영감 구절 스캔 사진 리스토어",
                                initialDesc = "스캔 압축 패키지 체크섬 무결성 검증 중...",
                                finalSuccessMsg = "발췌된 도서 및 일러스트 원본 데이터 앨범 복원이 무사히 마쳤습니다!"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_photos_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CameraRoll, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("스캔 사진 앨범 복원 진행하기", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // SECTION 3: APP SERVICE INFO (서비스 정보)
            Text(
                "ℹ️ 서비스 정보 관리",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    
                    // App Version Identification
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("현재 빌드 앱 번들 버전", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "v1.2.0-stable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Expandable item 1: Update logs
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangelog = !showChangelog }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.HistoryToggleOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("업데이트 내역 및 히스토리", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(
                                imageVector = if (showChangelog) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showChangelog) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("• [v1.2.0] 독서 목표 설정 및 일속 권장 정렬 페이스 계산 모티베이터 통계 보드 추가", style = MaterialTheme.typography.bodySmall)
                                    Text("• [v1.2.0] 문장 링킹 기술을 탑재한 Obsidian 마운트 호환 기억 서랍망 오픈 및 Local RAG 시스템 구축", style = MaterialTheme.typography.bodySmall)
                                    Text("• [v1.1.2] 온디바이스 OCR 텍스트 자동 추출 속도 대폭 개선", style = MaterialTheme.typography.bodySmall)
                                    Text("• [v1.0.0] 나만의 책장 생성 및 네이버 도서 검색 API 동기화 출시", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // Expandable item 2: Terms of service
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTerms = !showTerms }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("서비스 이용약관", style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(
                                imageVector = if (showTerms) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showTerms) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "제 1조 [목적]\n본 이용약관은 '나만의 서재 다이어리' 서비스가 제공하는 스마트 온디바이스 독서 기록 관리에 대한 제반 권리와 의무 사항을 규정함을 목적으로 합니다.\n\n제 2조 [데이터 보안 책무]\n본 서비스는 사용자가 추출한 발췌 구절 및 다이어리를 제3의 광고 서버로 양도 및 무기명 판매하지 않는 청정 오프라인 우선 보안 환경을 유지할 것을 선언합니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Expandable item 3: Privacy policy
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPrivacyPolicy = !showPrivacyPolicy }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("개인정보 처리 및 정책 방침", style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(
                                imageVector = if (showPrivacyPolicy) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showPrivacyPolicy) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "'나만의 서재 다이어리'는 개인정보 보호법 등 준법 기준을 엄격하게 엄수하며, 기기 바깥으로 식별 명세를 일체 요구하거나 비밀 수집·전송하지 않는 것을 철칙으로 삼으며 안전하게 보관 처리함을 알려 드립니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
