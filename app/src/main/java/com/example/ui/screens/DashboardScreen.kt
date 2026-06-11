package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.data.Bookcase
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ReadingViewModel,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.filteredBooks.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedBookcaseId by viewModel.selectedBookcaseId.collectAsState()
    var showFabMenu by remember { mutableStateOf(false) }

    val currentBookcaseName = bookcases.find { it.id == selectedBookcaseId }?.name ?: "전체 내역"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "독서 다이어리",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = currentBookcaseName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (selectedBookcaseId != null) {
                        IconButton(onClick = { viewModel.selectedBookcaseId.value = null }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Clear Category Base Filter",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)
            ) {
                if (showFabMenu) {
                    // Option 1: Search & Add Book
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable {
                            showFabMenu = false
                            viewModel.navigateTo(Screen.AddEditBook(startWithSearch = true))
                        }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "검색해서 책 추가",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                viewModel.navigateTo(Screen.AddEditBook(startWithSearch = true))
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "검색해서 책 추가")
                        }
                    }

                    // Option 2: Direct Input
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable {
                            showFabMenu = false
                            viewModel.navigateTo(Screen.AddEditBook(startWithSearch = false))
                        }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "직접 입력해서 책 추가",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                viewModel.navigateTo(Screen.AddEditBook(startWithSearch = false))
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "직접 입력해서 책 추가")
                        }
                    }
                }

                // Main FAB
                val rotationAngle by animateFloatAsState(
                    targetValue = if (showFabMenu) 45f else 0f,
                    label = "fab_rotation"
                )
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = if (showFabMenu) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    contentColor = if (showFabMenu) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_book_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "책 등록 옵션 열기",
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Stats Indicator Row
                DashboardStatsRow(books = books)

                Spacer(modifier = Modifier.height(8.dp))

                // State Category Selector (Chips)
                FilterChipRow(
                    currentFilter = selectedFilter,
                    onFilterSelected = { viewModel.selectedFilter.value = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Grid of Books
                if (books.isEmpty()) {
                    EmptyStateView(
                        filter = selectedFilter,
                        bookcaseName = currentBookcaseName,
                        onAddClick = { viewModel.navigateTo(Screen.AddEditBook(startWithSearch = true)) }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("books_grid")
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookCardItem(
                                book = book,
                                onClick = { viewModel.navigateTo(Screen.BookDetail(book.id)) }
                            )
                        }
                    }
                }
            }

            if (showFabMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showFabMenu = false }
                )
            }
        }
    }
}

@Composable
fun DashboardStatsRow(books: List<Book>) {
    val total = books.size
    val readingCount = books.count { it.status == "READING" }
    val completedCount = books.count { it.status == "COMPLETED" }
    val toReadCount = books.count { it.status == "TO_READ" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = "전체", count = total, icon = Icons.Default.MenuBook, color = MaterialTheme.colorScheme.primary)
            Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            StatItem(label = "읽는 중", count = readingCount, icon = Icons.Default.PlayArrow, color = MaterialTheme.colorScheme.secondary)
            Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            StatItem(label = "완독", count = completedCount, icon = Icons.Default.CheckCircleOutline, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(
    currentFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val filters = listOf(
            Triple("ALL", "전체 책", Icons.Outlined.FormatListBulleted),
            Triple("READING", "읽는 중", Icons.Outlined.ChromeReaderMode),
            Triple("TO_READ", "읽을 책", Icons.Outlined.BookmarkBorder),
            Triple("COMPLETED", "읽은 책", Icons.Outlined.TaskAlt)
        )

        filters.forEach { (tag, label, icon) ->
            val isSelected = currentFilter == tag
            
            val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            val borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

            Surface(
                onClick = { onFilterSelected(tag) },
                shape = RoundedCornerShape(20.dp),
                color = backgroundColor,
                contentColor = contentColor,
                border = if (isSelected) null else BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .testTag("filter_chip_$tag")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(15.dp),
                        tint = iconColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCardItem(
    book: Book,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag("book_card_${book.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3D Physical Book Cover Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .aspectRatio(0.72f)
        ) {
            // Pages stack background simulation (pages stacked on the right and bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, top = 2.dp)
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                        clip = false
                    )
                    .background(Color.White, shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .border(
                        width = 0.5.dp,
                        color = Color(0xFFD6CEB5).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                    )
            )

            // The actual book cover on top, slightly offset to left/top
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 5.dp, bottom = 4.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(topStart = 1.dp, bottomStart = 1.dp, topEnd = 2.dp, bottomEnd = 2.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(topStart = 1.dp, bottomStart = 1.dp, topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.cleanCoverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Complete Badge: Yellow shiny medal icon sticker overlay
                if (book.status == "COMPLETED") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .shadow(1.5.dp, shape = androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFFFF9C4), shape = androidx.compose.foundation.shape.CircleShape)
                            .border(0.8.dp, Color(0xFFFBC02D), shape = androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "완독",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                lineHeight = 8.sp
                            )
                        )
                    }
                }

                // Shaded Bottom Overlay with Progress % (only for reading status with some progress, or non-completed active reading)
                if (book.status == "READING" || (book.currentPage >= 0 && book.status != "COMPLETED" && book.status != "TO_READ")) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${(book.progressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // Ratings or indicator row (fixed height to align book titles on a horizontal line)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (book.status == "COMPLETED") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val isFilled = index < book.rating
                        Icon(
                            imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (isFilled) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        // Centered Book Title only
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun EmptyStateView(
    filter: String,
    bookcaseName: String,
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val message = when (filter) {
                "READING" -> "현재 읽는 중인 책이 없습니다."
                "TO_READ" -> "다음에 읽을 대기 목록이 비어 있습니다."
                "COMPLETED" -> "아직 완독한 도서가 없습니다."
                else -> "'$bookcaseName' 책장에 등록된 책이 없습니다."
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "새로운 도서를 추가해 독서의 발자취를 남겨보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("첫 도서 등록하기")
            }
        }
    }
}
