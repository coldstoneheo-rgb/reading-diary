package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.AddEditBookScreen
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.KnowledgeDrawerScreen
import com.example.ui.screens.PhraseRecallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.isDarkAppTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ReadingViewModel by lazy { ViewModelProvider(this)[ReadingViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.restoreNavigation(
            savedInstanceState?.getStringArrayList(KEY_NAVIGATION),
            savedInstanceState?.getString(KEY_DIARY_FORM_TOKEN)
        )
        // 돌아오지 못한 촬영의 임시 파일(책 페이지 사진)을 새 시작에서 지운다. 복원 시작이면 카메라 결과가 아직 쓸 수 있어 남겨 둔다.
        if (savedInstanceState == null) viewModel.releaseCameraTempFiles(cacheDir)
        // 첫 프레임 전에 edge-to-edge를 켜고, 아이콘 밝기는 시스템 모드가 아니라 앱 테마를 따른다.
        applySystemBarStyle(viewModel.currentThemeId.value)
        setContent {
            val currentThemeId by viewModel.currentThemeId.collectAsState()
            // 설정에서 테마를 바꾸면 다시 켜지 않아도 상태 표시줄 · 내비게이션 바 아이콘이 따라온다.
            DisposableEffect(currentThemeId) {
                applySystemBarStyle(currentThemeId)
                onDispose {}
            }
            MyApplicationTheme(themeId = currentThemeId) {
                MainAppEntry(viewModel)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_NAVIGATION, ArrayList(viewModel.navigationSnapshot()))
        outState.putString(KEY_DIARY_FORM_TOKEN, viewModel.diaryFormToken.value)
    }

    private fun applySystemBarStyle(themeId: Int) {
        val dark = isDarkAppTheme(themeId)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
        )
    }

    private companion object {
        // androidx.activity enableEdgeToEdge() 기본 내비게이션 막과 같은 값. API 26-28에서만 쓰인다(29 이상은 시스템이 막을 그린다).
        val LIGHT_NAV_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
        const val KEY_NAVIGATION = "reading_diary_navigation"
        const val KEY_DIARY_FORM_TOKEN = "reading_diary_form_token"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppEntry(viewModel: ReadingViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentScreen by viewModel.currentScreenState.collectAsState()
    val diaryFormToken by viewModel.diaryFormToken.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()
    val selectedBookcaseId by viewModel.selectedBookcaseId.collectAsState()

    // Intercept system/device back button press to navigate back internal pages
    BackHandler(enabled = currentScreen != Screen.Dashboard) {
        viewModel.navigateBack()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 서랍이 열려 있으면 뒤로는 서랍만 닫는다.
    BackHandler(enabled = drawerState.isOpen) { coroutineScope.launch { drawerState.close() } }

    // 확인을 기다리는 책장 삭제
    var bookcaseToDelete by remember { mutableStateOf<com.example.data.Bookcase?>(null) }
    val allBooks by viewModel.books.collectAsState()
    val allDiaries by viewModel.diaries.collectAsState()
    bookcaseToDelete?.let { target ->
        val bookIds = allBooks.filter { it.bookcaseId == target.id }.map { it.id }.toSet()
        DeleteBookcaseDialog(
            name = target.name,
            books = bookIds.size,
            diaries = allDiaries.count { it.bookId in bookIds },
            onConfirm = {
                bookcaseToDelete = null
                viewModel.deleteBookcase(target.id)
                Toast.makeText(context, "'${target.name}' 책장이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { bookcaseToDelete = null }
        )
    }

    // Custom bookcase name input
    var newBookcaseName by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 대시보드에서만 밀어서 연다. 기록을 쓰는 중에 밀려 나와 다른 화면으로 가면 쓰던 글이 사라진다.
        gesturesEnabled = currentScreen == Screen.Dashboard || drawerState.isOpen,
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
                            text = "문장서랍",
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
                                    onClick = { bookcaseToDelete = bookcase },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "'${bookcase.name}' 책장 삭제",
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
                                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "새 책장 만들기", tint = MaterialTheme.colorScheme.primary)
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

                // Bottom utilities section (통계, 기억 서랍)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚙️ 문장서랍 유틸리티",
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
                        Text("기억 서랍", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.openPhraseRecall()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_phraserecall_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("문구 회상", fontWeight = FontWeight.Bold)
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
                            // 전환 중 다시 들어오면 나가던 구성이 재사용된다. 토큰으로 갈라 저장된 초안이 이어지지 않게 한다.
                            key(diaryFormToken) {
                                OcrDiaryScreen(
                                    viewModel = viewModel,
                                    bookId = screen.bookId,
                                    diaryId = screen.diaryId
                                )
                            }
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
                        is Screen.PhraseRecall -> {
                            PhraseRecallScreen(
                                viewModel = viewModel,
                                initialQuery = screen.initialQuery,
                                initialFilter = screen.initialFilter
                            )
                        }
                    }
                }
            }
        }
    )
}

/** 책장을 지우면 안에 든 책·기록·사진까지 함께 지워지므로(CASCADE) 무엇이 사라지는지 보여 주고 확인받는다. */
@Composable
internal fun DeleteBookcaseDialog(name: String, books: Int, diaries: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'$name' 책장을 삭제할까요?") },
        text = {
            Text(
                if (books == 0) "이 책장에는 책이 없습니다."
                else if (diaries == 0) "이 책장의 책 ${books}권이 함께 삭제되며 되돌릴 수 없습니다."
                else "이 책장의 책 ${books}권과 독서 기록 ${diaries}개(기록에 남긴 사진 포함)가 함께 삭제되며 되돌릴 수 없습니다."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_bookcase_confirm")) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
