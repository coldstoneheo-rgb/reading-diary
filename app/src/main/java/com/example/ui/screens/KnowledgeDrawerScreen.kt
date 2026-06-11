package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Book
import com.example.data.Diary
import com.example.ui.viewmodel.ReadingViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDrawerScreen(
    viewModel: ReadingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Observe DB lists
    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()

    // Interactive States
    var searchKeyword by remember { mutableStateOf("") }
    var selectedNodeText by remember { mutableStateOf<String?>(null) }
    var selectedNodeDiary by remember { mutableStateOf<Diary?>(null) }
    
    // Suggestion hot-keywords based on demo diaries
    val hotTags = listOf("세계", "인격체", "자존", "신", "협동", "통찰", "인생", "독서", "기록")

    // Sort matching diaries based on "semantic relevance"
    val filteredResults = remember(searchKeyword, diaries, books) {
        val query = searchKeyword.trim().lowercase()
        if (query.isEmpty()) {
            diaries.map { Pair(it, 100) } // Return all with neutral score
        } else {
            diaries.mapNotNull { d ->
                val textToSearch = (d.selectedText + " " + d.notes).lowercase()
                val matchedBook = books.find { it.id == d.bookId }?.title?.lowercase() ?: ""
                
                val relevance = when {
                    textToSearch.contains(query) && matchedBook.contains(query) -> 98
                    textToSearch.contains(query) -> 85
                    matchedBook.contains(query) -> 70
                    else -> 0
                }
                if (relevance > 0) Pair(d, relevance) else null
            }.sortedByDescending { it.second }
        }
    }

    // Interactive mindmap positions setup
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    if (selectedNodeText != null) {
        AlertDialog(
            onDismissRequest = { selectedNodeText = null; selectedNodeDiary = null },
            title = { Text("🧬 브레인 링킹 연결 카드", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "지식 서랍 인덱싱 키워드: #$selectedNodeText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    selectedNodeDiary?.let { diary ->
                        val parentBookTitle = books.find { it.id == diary.bookId }?.title ?: "알 수 없는 책"
                        Text(
                            text = "관련 도서: $parentBookTitle (p. ${diary.page})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"${diary.selectedText}\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        if (diary.notes.isNotEmpty()) {
                            Text(
                                text = "나의 해설: ${diary.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    } ?: run {
                        Text("선택된 노드는 브레인 중심 지점입니다. 아래 검색이나 네비게이션을 통해 책과의 관계를 입체적으로 모니터링할 수 있습니다.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNodeText = null; selectedNodeDiary = null }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기억 서랍 (RAG & Obsidian)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("knowledgedrawer_back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    // Export as markdown option
                    IconButton(
                        onClick = {
                            if (diaries.isEmpty()) {
                                Toast.makeText(context, "추출하여 내보낼 다이어리 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val sb = StringBuilder()
                            sb.append("# 📚 나만의 지식 서랍 백업 (Obsidian & Logseq 연동용)\n\n")
                            diaries.forEach { d ->
                                val b = books.find { it.id == d.bookId }
                                sb.append("## [[${b?.title ?: "독서기록"}]] - p.${d.page}\n")
                                sb.append("- 저자: ${b?.author ?: "미상"}\n")
                                sb.append("- 발췌 내용:\n  > ${d.selectedText}\n")
                                if (d.notes.isNotEmpty()) {
                                    sb.append("- 나의 성찰:\n  ${d.notes}\n")
                                }
                                sb.append("- 인덱싱 시각: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(d.createdAt))}\n\n")
                            }
                            clipboardManager.setText(AnnotatedString(sb.toString()))
                            Toast.makeText(context, "Obsidian 호환 마크다운(.md) 파일 내용이 클립보드에 복사되었습니다! 메모장이나 옵시디언에 간편하게 붙여넣으세요.", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Obsidian 호환 복사", tint = MaterialTheme.colorScheme.primary)
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
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            
            // INTRO BANNER
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "단순 독서기록을 넘어선 '지식 연결망'. 수집된 글맥락을 키워드로 링킹하고 과거 독서 기억을 시각적으로 복원합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }
            }

            // SECTION 1: Obsidian Network Graph Mind-Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Title Label on Canvas Overlay
                    Text(
                        text = "Obsidian-Graph 뇌세포 신경 네트워크",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )

                    // Draw the network relations using Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("obsidian_canvas")
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val centerX = canvasWidth / 2
                        val centerY = canvasHeight / 2

                        // Draw center core brain connection
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.15f),
                            radius = 45f,
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 12f,
                            center = Offset(centerX, centerY)
                        )

                        // Draw peripheral nodes
                        val nodeCount = diaries.size.coerceAtMost(6)
                        if (nodeCount > 0) {
                            val stepAngle = 360f / nodeCount
                            for (i in 0 until nodeCount) {
                                val currentDiary = diaries[i]
                                val angleRad = Math.toRadians((i * stepAngle).toDouble())
                                val radiusDistance = 140f
                                val nodeX = centerX + (radiusDistance * cos(angleRad)).toFloat()
                                val nodeY = centerY + (radiusDistance * sin(angleRad)).toFloat()

                                // Connect center to node
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.4f),
                                    start = Offset(centerX, centerY),
                                    end = Offset(nodeX, nodeY),
                                    strokeWidth = 2.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )

                                // Connect node to neighboring node to simulate mesh
                                if (i > 0) {
                                    val prevAngleRad = Math.toRadians(((i - 1) * stepAngle).toDouble())
                                    val prevNodeX = centerX + (radiusDistance * cos(prevAngleRad)).toFloat()
                                    val prevNodeY = centerY + (radiusDistance * sin(prevAngleRad)).toFloat()

                                    drawLine(
                                        color = secondaryColor.copy(alpha = 0.2f),
                                        start = Offset(prevNodeX, prevNodeY),
                                        end = Offset(nodeX, nodeY),
                                        strokeWidth = 1.5f
                                    )
                                }

                                // Draw single circular node
                                drawCircle(
                                    color = secondaryColor.copy(alpha = 0.85f),
                                    radius = 8f,
                                    center = Offset(nodeX, nodeY)
                                )
                            }
                        }
                    }

                    // Floating Interactive Tag Box representing Obsidian Nodes
                    Box(modifier = Modifier.fillMaxSize()) {
                        val positions = listOf(
                            Pair(0.18f, 0.28f),
                            Pair(0.78f, 0.22f),
                            Pair(0.20f, 0.72f),
                            Pair(0.76f, 0.74f),
                            Pair(0.50f, 0.80f)
                        )
                        
                        diaries.take(positions.size).forEachIndexed { index, diary ->
                            val pos = positions[index]
                            val keyword = remember(diary) {
                                val words = diary.selectedText.split(" ")
                                val clean = words.find { it.length >= 2 } ?: "키워드"
                                clean.replace(Regex("[^가-힣a-zA-Z]"), "")
                            }

                            Box(
                                modifier = Modifier
                                    .align(
                                        Alignment.TopStart
                                    )
                                    .offset(
                                        x = (230 * pos.first).dp,
                                        y = (130 * pos.second).dp
                                    )
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(30.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        RoundedCornerShape(30.dp)
                                    )
                                    .clickable {
                                        selectedNodeText = keyword
                                        selectedNodeDiary = diary
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Notice overlay instructing user
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Text(
                            "💡 동그랗게 흐르는 노드를 터치하여 책맥락 지식을 들여다보세요.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // SECTION 2: Vector Semantic retrieval simulation Search
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🧬 지능형 기억 연관성 인덱스 검색",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("발췌 문맥, 생각, 책제목 키워드를 쳐보세요...") },
                    leadingIcon = { Icon(Icons.Default.ManageSearch, contentDescription = null) },
                    trailingIcon = {
                        if (searchKeyword.isNotEmpty()) {
                            IconButton(onClick = { searchKeyword = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_search_field"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // Quick tags click interaction
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(hotTags) { tag ->
                        SuggestionChip(
                            onClick = { searchKeyword = tag },
                            label = { Text("#$tag") }
                        )
                    }
                }
            }

            // Results matching view
            Text(
                "연결 성공 지식 기록 (${filteredResults.size}개 발견)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "일치하는 독서 기억을 찾지 못했습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("rag_results_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredResults) { (diary, score) ->
                        val matchedBook = books.find { it.id == diary.bookId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = matchedBook?.title ?: "소속 도서 정보 없음",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (score > 80) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                    ) {
                                        Text(
                                            text = "🧬 매칭 연동률: $score%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = if (score > 80) Color(0xFF2E7D32) else Color(0xFFE65100),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = diary.selectedText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (diary.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "나의 사색: ${diary.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Book ID: ${diary.bookId} • Page: ${diary.page} • " + SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(diary.createdAt)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
