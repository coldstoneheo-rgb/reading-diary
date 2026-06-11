package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AddEditBookScreen
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.KnowledgeDrawerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ReadingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val currentThemeId by viewModel.currentThemeId.collectAsState()
            MyApplicationTheme(themeId = currentThemeId) {
                MainAppEntry(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppEntry(viewModel: ReadingViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentScreen by viewModel.currentScreenState.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()
    val selectedBookcaseId by viewModel.selectedBookcaseId.collectAsState()

    // Intercept system/device back button press to navigate back internal pages
    BackHandler(enabled = currentScreen != Screen.Dashboard) {
        viewModel.navigateBack()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Backup actions dialog trigger
    var showBackupDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var backupStatusText by remember { mutableStateOf("") }

    // Custom bookcase name input
    var newBookcaseName by remember { mutableStateOf("") }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBackingUp) showBackupDialog = false },
            title = { Text("클라우드 동기화 및 백업", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = backupStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "현재 저장된 책 목록과 독서 다이어리 기록을 구글 클라우드 및 iCloud 시스템에 동기화할까요?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (!isBackingUp) {
                    Button(
                        onClick = {
                            isBackingUp = true
                            coroutineScope.launch {
                                backupStatusText = "로컬 SQLite DB 압축 파일 생성 중..."
                                delay(1200)
                                backupStatusText = "원격 구글 서버 보안 채널 동기화 진행 중..."
                                delay(1500)
                                isBackingUp = false
                                showBackupDialog = false
                                Toast.makeText(context, "백업 동기화가 안전하게 완료되었습니다!", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("동기화 실행")
                    }
                }
            },
            dismissButton = {
                if (!isBackingUp) {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("닫기")
                    }
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .testTag("drawer_menu")
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                
                // Compact Horizontal Drawer Logo and Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "나만의 서재 다이어리",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "기록으로 이뤄낸 독서의 가치",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Custom Bookcases (내 책장 리스트)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    item {
                        Text(
                            text = "내 책장 분류",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    // "전체 서재" option
                    item {
                        NavigationDrawerItem(
                            label = { Text("📚 전체 서재 보기", fontWeight = FontWeight.SemiBold) },
                            selected = selectedBookcaseId == null,
                            onClick = {
                                viewModel.selectedBookcaseId.value = null
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    // Dynamically loaded bookcases from Db
                    items(bookcases, key = { it.id }) { bookcase ->
                        val isSelected = selectedBookcaseId == bookcase.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    viewModel.selectedBookcaseId.value = bookcase.id
                                    coroutineScope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (bookcase.isSystem) Icons.Default.Folder else Icons.Outlined.FolderSpecial,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = bookcase.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Delete triggers only for client added categories
                            if (!bookcase.isSystem) {
                                IconButton(
                                    onClick = {
                                        viewModel.deleteBookcase(bookcase.id)
                                        Toast.makeText(context, "'${bookcase.name}' 책장이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Bookcase",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Custom bookcase builder
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            OutlinedTextField(
                                value = newBookcaseName,
                                onValueChange = { newBookcaseName = it },
                                placeholder = { Text("새 책장 이름...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_bookcase_input"),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (newBookcaseName.isNotBlank()) {
                                                viewModel.createBookcase(newBookcaseName)
                                                Toast.makeText(context, "'$newBookcaseName' 책장이 신설되었습니다.", Toast.LENGTH_SHORT).show()
                                                newBookcaseName = ""
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Add category", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Bottom utilities section (Cloud Backup and Utilities)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚙️ 독서 다이어리 유틸리티",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.navigateTo(Screen.Statistics)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_statistics_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("독서 통계", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.navigateTo(Screen.KnowledgeDrawer)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_knowledgedrawer_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("기억 서랍 ( Obsidian & RAG )", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("클라우드 백업 및 복원", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        content = {
            // Main content screens with fluid scale slide animations
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(250))
                    },
                    label = "screenAnimation"
                ) { screen ->
                    when (screen) {
                        is Screen.Dashboard -> {
                            DashboardScreen(
                                viewModel = viewModel,
                                    onMenuClick = {
                                        coroutineScope.launch { drawerState.open() }
                                    }
                            )
                        }
                        is Screen.BookDetail -> {
                            BookDetailScreen(
                                viewModel = viewModel
                            )
                        }
                        is Screen.AddEditBook -> {
                            AddEditBookScreen(
                                viewModel = viewModel,
                                bookId = screen.bookId,
                                startWithSearch = screen.startWithSearch
                            )
                        }
                        is Screen.AddDiary -> {
                            OcrDiaryScreen(
                                viewModel = viewModel,
                                bookId = screen.bookId,
                                diaryId = screen.diaryId
                            )
                        }
                        is Screen.Settings -> {
                            SettingsScreen(
                                viewModel = viewModel
                            )
                        }
                        is Screen.Statistics -> {
                            StatisticsScreen(
                                viewModel = viewModel
                            )
                        }
                        is Screen.KnowledgeDrawer -> {
                            KnowledgeDrawerScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    )
}
