package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ReadingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: ReadingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Key states from database
    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val yearlyGoal by viewModel.readingGoalYearly.collectAsState()

    // Calculated metrics
    val completedBooksCount = remember(books) {
        books.count { it.status == "COMPLETED" }
    }
    val inProgressBooksCount = remember(books) {
        books.count { it.status == "READING" }
    }
    val totalPagesRead = remember(books) {
        books.sumOf { it.currentPage }
    }

    // Recommendation counts based on yearly goal
    val recommendedIntervalDays = remember(yearlyGoal) {
        if (yearlyGoal > 0) (365f / yearlyGoal).toInt().coerceAtLeast(1) else 12
    }
    val recommendedDailyPages = remember(yearlyGoal) {
        if (yearlyGoal > 0) ((yearlyGoal * 300f) / 365f).toInt().coerceAtLeast(1) else 10
    }

    // Motivation Quote based on completion status
    val motivationPhrase = remember(completedBooksCount, yearlyGoal) {
        val pct = if (yearlyGoal > 0) (completedBooksCount * 100 / yearlyGoal) else 0
        when {
            pct == 0 -> "독서는 위대한 여정의 첫 걸음입니다. 오늘의 딱 10페이지가 인생을 바꿉니다!"
            pct < 30 -> "꾸준한 흐름이 모여 큰 바다를 이룹니다. 설정된 간격에 따라 순항하고 계십니다."
            pct < 70 -> "완성도 높은 서재가 다듬어지고 있습니다! 지각적 독서 습관이 굳건해졌습니다."
            pct < 100 -> "목표가 머지 않았습니다! 당신의 통찰력이 기록 목록에서 눈부시게 피어나네요."
            else -> "🎉 연간 목표 100% 달성 성공! 한 차원 넓어진 지식 인프라를 만끽해 보세요."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("나만의 독서 통계 및 제안", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("statistics_back_button")
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
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // CARD 1: Yearly Plan Controller & Motivator
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "연간 계획",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "올해 나의 독서 페이스 플래너",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "목표 연간 독서량을 설정해 보세요. 설정된 목표 수치에 맞춰 며칠에 한 권씩 책을 완성해야 하는지, 하루 몇 페이지씩 소화해야 하는지 1:1 맞춤 일정을 똑똑하게 제안해 줍니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )

                    // Target Incrementor UI
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "연간 독서 목표량",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { if (yearlyGoal > 1) viewModel.setReadingGoalYearly(yearlyGoal - 1) },
                                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "줄이기", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            Text(
                                text = "${yearlyGoal} 권",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("total_goal_text")
                            )

                            IconButton(
                                onClick = { viewModel.setReadingGoalYearly(yearlyGoal + 1) },
                                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "늘리기", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Progress Bar
                    val percentage = if (yearlyGoal > 0) {
                        (completedBooksCount.toFloat() / yearlyGoal.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "달성 진행률: ${(percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$completedBooksCount / $yearlyGoal 권 완료",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }

                    // Calculations Output Showcase Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("권장 독서 주기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${recommendedIntervalDays}일",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("간격 한권 완료 필요", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("하루 권장 독서량", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${recommendedDailyPages}쪽",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text("매일 할당 목표치", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }

                    // Phrase Motivator Card
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = motivationPhrase,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // CARD 2: GitHub-Style 105-Day Reading Grass (Contribution Map)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "기록 잔디",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "기록 빈도 잔디밭 (최신 15주)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "총 ${diaries.size}회 작성",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "독서 도중 다이어리를 작성하고 구절을 발췌한 날짜들이 잔디밭의 녹색 불빛으로 영롱하게 채워집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )

                    // Contribution Grass Map Generator
                    // 15 columns x 7 rows = 105 days
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    
                    // Maps DateString -> count of Diaries
                    val activityMap = remember(diaries) {
                        val map = mutableMapOf<String, Int>()
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        diaries.forEach { d ->
                            val dateStr = try {
                                sdf.format(Date(d.createdAt))
                            } catch (e: Exception) {
                                ""
                            }
                            if (dateStr.isNotEmpty()) {
                                map[dateStr] = (map[dateStr] ?: 0) + 1
                            }
                        }
                        map
                    }

                    val daysGrid = remember(activityMap) {
                        val list = mutableListOf<List<Pair<String, Int>>>()
                        val calendar = Calendar.getInstance()
                        // Find the start date: 105 days ago, adjusted to start on Sunday/Monday
                        calendar.add(Calendar.DAY_OF_YEAR, -104)
                        val startDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        calendar.add(Calendar.DAY_OF_YEAR, -(startDayOfWeek - 1)) //Align to first row

                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        
                        // Create 15 columns
                        for (col in 0 until 15) {
                            val columnDays = mutableListOf<Pair<String, Int>>()
                            // 7 rows per week
                            for (row in 0 until 7) {
                                val curDate = calendar.time
                                val dateStr = sdf.format(curDate)
                                val count = activityMap[dateStr] ?: 0
                                columnDays.add(Pair(dateStr, count))
                                calendar.add(Calendar.DAY_OF_YEAR, 1)
                            }
                            list.add(columnDays)
                        }
                        list
                    }

                    // Render the Grass Calendar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Days Indicator labels (S, M, T, W, T, F, S)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            val dayLabels = listOf("일", "", "화", "", "목", "", "토")
                            repeat(7) { i ->
                                Text(
                                    text = dayLabels[i],
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    modifier = Modifier.height(13.dp)
                                )
                            }
                        }

                        // Grass blocks grid row index iteration (0..6)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            daysGrid.forEach { column ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    column.forEach { (dateStr, count) ->
                                        val color = when {
                                            count == 0 -> surfaceVariantColor.copy(alpha = 0.45f)
                                            count == 1 -> primaryColor.copy(alpha = 0.25f)
                                            count == 2 -> primaryColor.copy(alpha = 0.55f)
                                            else -> primaryColor.copy(alpha = 0.9f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(color)
                                                .clickable {
                                                    val formattedDate = try {
                                                        val orig = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                                                        SimpleDateFormat("M월 d일", Locale.KOREA).format(orig)
                                                    } catch (e: Exception) {
                                                        dateStr
                                                    }
                                                    if (count > 0) {
                                                        Toast.makeText(context, "$formattedDate: ${count}개의 독서 펜기록이 있습니다.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "$formattedDate: 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Less",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        val legendColors = listOf(
                            surfaceVariantColor.copy(alpha = 0.45f),
                            primaryColor.copy(alpha = 0.25f),
                            primaryColor.copy(alpha = 0.55f),
                            primaryColor.copy(alpha = 0.9f)
                        )
                        legendColors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(col)
                                )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            "More",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            // CARD 3: Reading Monthly Wave Chart (Canvas Plotting)
            val graphColor = MaterialTheme.colorScheme.primary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "독서 리듬",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "올해 월간 독서 리듬 분석",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "매월 추가된 독서 기록 빈도를 비교하여 나의 독서 라이프 사이클과 규칙성을 한눈에 모니터링합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )

                    // Gather month frequencies for current year
                    val monthlyLogs = remember(diaries) {
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        val counts = IntArray(12) { 0 }
                        val calendar = Calendar.getInstance()
                        diaries.forEach { d ->
                            calendar.timeInMillis = d.createdAt
                            if (calendar.get(Calendar.YEAR) == currentYear) {
                                val month = calendar.get(Calendar.MONTH) // 0..11
                                if (month in 0..11) {
                                    counts[month]++
                                }
                            }
                        }
                        counts.toList()
                    }

                    val maxLogs = remember(monthlyLogs) {
                        monthlyLogs.maxOrNull()?.coerceAtLeast(1) ?: 1
                    }

                    // Render Canvas graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val width = size.width
                            val height = size.height
                            
                            val paddingLeft = 30f
                            val paddingRight = 30f
                            val paddingTop = 20f
                            val paddingBottom = 20f
                            
                            val usableWidth = width - paddingLeft - paddingRight
                            val usableHeight = height - paddingTop - paddingBottom
                            
                            val points = monthlyLogs.mapIndexed { index, value ->
                                val x = paddingLeft + (usableWidth / 11) * index
                                val ratio = value.toFloat() / maxLogs.toFloat()
                                val y = height - paddingBottom - (usableHeight * ratio)
                                Offset(x, y)
                            }
                            
                            // Draw horizontal bottom grid line
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.15f),
                                start = Offset(paddingLeft, height - paddingBottom),
                                end = Offset(width - paddingRight, height - paddingBottom),
                                strokeWidth = 2f
                            )

                            // Build Bezier wave path
                            if (points.isNotEmpty()) {
                                val path = Path()
                                path.moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.size - 1) {
                                    val p1 = points[i]
                                    val p2 = points[i+1]
                                    val controlX1 = p1.x + (p2.x - p1.x) / 2
                                    val controlY1 = p1.y
                                    val controlX2 = p1.x + (p2.x - p1.x) / 2
                                    val controlY2 = p2.y
                                    
                                    path.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                                }
                                
                                drawPath(
                                    path = path,
                                    color = graphColor,
                                    style = Stroke(width = 5f)
                                )
                                
                                // Draw points
                                points.forEachIndexed { i, pt ->
                                    // Draw circle
                                    drawCircle(
                                        color = graphColor,
                                        radius = 8f,
                                        center = pt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4f,
                                        center = pt
                                    )
                                }
                            }
                        }
                    }

                    // Month Labels Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val months = listOf("1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월")
                        months.forEach { m ->
                            Text(
                                text = m,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Summary Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "📊 현재 독서 요약 보고",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("소장 중인 도서:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${books.size} 권", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("읽는 중인 도서:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${inProgressBooksCount} 권", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("누적 독서량 정보:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("총 ${totalPagesRead} 쪽 돌파", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
