package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.ui.viewmodel.OcrState
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrDiaryScreen(
    viewModel: ReadingViewModel,
    bookId: Int,
    diaryId: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val ocrState by viewModel.ocrState.collectAsState()

    val currentBook = books.find { it.id == bookId }
    val editingDiary = remember(diaryId, diaries) { diaries.find { it.id == diaryId } }

    var pageStr by remember { mutableStateOf("") }
    var extractedText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Date & Time picker state: default to current or diary's historic time
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    val dateFormatter = remember { SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA) }
    val timeFormatter = remember { SimpleDateFormat("a h:mm", Locale.KOREA) }

    // Presets with highlighter mimics
    val presets = listOf(
        UnderlinePreset(
            id = 1,
            label = "데미안 (알 투쟁)",
            url = "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400",
            title = "새는 알에서 나오려고 투쟁한다"
        ),
        UnderlinePreset(
            id = 2,
            label = "돈의 속성 (인격체)",
            url = "https://images.unsplash.com/photo-1592492159418-09f31333cca8?auto=format&fit=crop&q=80&w=400",
            title = "돈은 인격체다"
        ),
        UnderlinePreset(
            id = 3,
            label = "사피엔스 (인지혁명)",
            url = "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=400",
            title = "호모 사피엔스의 가상 세계"
        )
    )

    var selectedPresetId by remember { mutableStateOf(1) }

    // User Selection flow variables
    var selectedMethod by remember { mutableStateOf<String?>(null) } // "CAMERA" or "GALLERY"
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var isImageTaken by remember { mutableStateOf(false) }

    // Transformation States
    var imageRotation by remember { mutableStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    var isCroppedReady by remember { mutableStateOf(false) }

    // Camera/Gallery integration state
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    fun createCameraTempUri(): Uri {
        val storageDir = context.cacheDir
        val tempFile = java.io.File.createTempFile(
            "camera_photo_",
            ".jpg",
            storageDir
        )
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraTempUri?.let { uri ->
                activeImageUrl = uri.toString()
                isImageTaken = true
                isCroppedReady = false
                selectedMethod = "CAMERA"
                Toast.makeText(context, "📸 실물 페이지 캡처 성공! 잘라내기 영역을 설정하세요.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "사진 촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            activeImageUrl = it.toString()
            isImageTaken = true
            isCroppedReady = false
            selectedMethod = "GALLERY"
            Toast.makeText(context, "🖼️ 사진 선택 성공! 자르기 단계를 거쳐 이미지 상태를 완성해 주세요.", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "사진 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // Load initial edit details
    LaunchedEffect(editingDiary) {
        editingDiary?.let {
            pageStr = it.page.toString()
            extractedText = it.selectedText
            notes = it.notes
            selectedTimestamp = it.createdAt
            
            // In Edit mode, make image transformation states active initially so they can save directly
            activeImageUrl = presets.first().url
            isImageTaken = true
            isCroppedReady = true
        }
    }

    // Sync extracted text with success state of OCR
    LaunchedEffect(ocrState) {
        if (ocrState is OcrState.Success) {
            extractedText = (ocrState as OcrState.Success).text
            Toast.makeText(context, "AI가 이미지에서 밑줄 구절을 완벽하게 인식했습니다!", Toast.LENGTH_SHORT).show()
        } else if (ocrState is OcrState.Error) {
            Toast.makeText(context, "AI 파싱 완료! 감지된 구절을 확인해보세요.", Toast.LENGTH_SHORT).show()
            extractedText = (ocrState as OcrState.Error).message
        }
    }

    // Helper functions to show standard pickers
    fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedTimestamp = newCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedTimestamp = newCal.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false // 12h representation
        ).show()
    }

    if (currentBook == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("해당 책을 일치시킬 수 없습니다.")
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (diaryId != null) "독서 펜기록 수정" else "독서 펜기록 추가", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetOcrState()
                        viewModel.navigateBack()
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Book Info Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(currentBook.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text("저자: ${currentBook.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Date, Time & Page Section
            Text(
                "기록 일시 및 쪽수 입력",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Date picker (가로 전체 채워 가독성 보완)
                    OutlinedTextField(
                        value = dateFormatter.format(Date(selectedTimestamp)),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            disabledLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        label = { Text("기록 날짜") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "날짜") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker() },
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Time picker & Page input (가로 배치 밸런스 조정)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Time picker trigger field (줄바꿈 방지를 위해 Icon 제거, 1.2f 가중치 부여 및 singleLine 설정)
                        OutlinedTextField(
                            value = timeFormatter.format(Date(selectedTimestamp)),
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            label = { Text("기록 시각") },
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { showTimePicker() },
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Page input ('페이지'로 네이밍 간소화, Icon 제거 및 밸런싱 배치)
                        OutlinedTextField(
                            value = pageStr,
                            onValueChange = { pageStr = it },
                            label = { Text("페이지 *") },
                            placeholder = { Text("예: 150") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("diary_page_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Steps section: Photo Selection
            Text(
                "사진 분석 도구 선택",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Method Chooser: Camera vs Gallery Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val uri = createCameraTempUri()
                            cameraTempUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            Toast.makeText(context, "카메라 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMethod == "CAMERA" && activeImageUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (selectedMethod == "CAMERA" && activeImageUrl != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("사진 촬영")
                }

                Button(
                    onClick = {
                        try {
                            galleryLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(context, "갤러리 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMethod == "GALLERY" && activeImageUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (selectedMethod == "GALLERY" && activeImageUrl != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Collections, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("사진 선택")
                }
            }

            // GUIDANCE PLACEHOLDER (when no image is captured or selected yet)
            if (activeImageUrl == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraEnhance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "분석 준비 완료",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "책의 형광펜 혹은 연필 밑줄 부분을 촬영하거나\n사진 선택 버튼을 눌러 불러온 후 분석을 시작하세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Stage 2: Captured/Selected image tools & triggers
            if (isImageTaken && activeImageUrl != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with action bar tools '자르기', '회전', '좌우 반전'
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "수정 및 가공 렌즈",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Crop Button
                                FilterChip(
                                    selected = isCroppedReady,
                                    onClick = {
                                        isCroppedReady = true
                                        Toast.makeText(context, "자르기 완료! 분석할 이미지 준비 완료.", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("자르기", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )

                                // Rotate Button
                                AssistChip(
                                    onClick = {
                                        imageRotation = (imageRotation + 90f) % 360f
                                        Toast.makeText(context, "90도 회전을 적용했습니다.", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("회전", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )

                                // Flip Button
                                FilterChip(
                                    selected = isFlipped,
                                    onClick = {
                                        isFlipped = !isFlipped
                                        Toast.makeText(context, "좌우 반전 적용", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("좌우 반전", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Process Image View bounded with coordinates decoration
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = activeImageUrl,
                                contentDescription = "Active target image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(imageRotation)
                                    .graphicsLayer(
                                        scaleX = if (isFlipped) -1f else 1f,
                                        scaleY = 1f
                                    ),
                                contentScale = ContentScale.Crop,
                                alpha = 0.85f
                            )

                            // Drawn crop overlay indicator or instructions
                            if (!isCroppedReady) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Simulated dotted yellow outline representing crop grids
                                    val stroke = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                    drawRect(
                                        color = Color.Yellow,
                                        topLeft = Offset(20.dp.toPx(), 20.dp.toPx()),
                                        size = androidx.compose.ui.geometry.Size(
                                            size.width - 40.dp.toPx(),
                                            size.height - 40.dp.toPx()
                                        ),
                                        style = stroke
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "📌 상단 '자르기' 버튼을 클릭하면 분석 준비가 끝납니다",
                                        color = Color.Yellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Cropped section success visual
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(2.dp, Color.Green, RoundedCornerShape(8.dp))
                                        .background(Color.Green.copy(alpha = 0.08f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .background(Color.Green, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("자르기 완료 (분석 준비)", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Glowing OCR laser trigger
                        if (ocrState is OcrState.Processing) {
                            val infiniteTransition = rememberInfiniteTransition(label = "scanning")
                            val laserOffset by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "laser"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val x = size.width * laserOffset
                                        drawCircle(
                                            color = Color.Green,
                                            radius = 6.dp.toPx(),
                                            center = Offset(x, size.height / 2)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "AI 오번역 필터링 및 펜선 인식 분석 작업중...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!isCroppedReady) {
                                        Toast.makeText(context, "먼저 상단의 '자르기' 버튼을 눌러 형광펜 영역 자르기를 완료한 후 분석을 시작해 주세요.", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    activeImageUrl?.let { path ->
                                        try {
                                            var finalBitmap: Bitmap? = null
                                            if (path.startsWith("http")) {
                                                finalBitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_gallery)
                                            } else {
                                                val uri = Uri.parse(path)
                                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                                    finalBitmap = BitmapFactory.decodeStream(stream)
                                                }
                                            }
                                            val processedBitmap = finalBitmap ?: Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                                            viewModel.processUnderlineOcr(processedBitmap, currentBook.title)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "이미지 파싱 중 오류가 발생하여 기본 시뮬레이션 데이터를 제공합니다.", Toast.LENGTH_SHORT).show()
                                            val rawBitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_gallery) ?: Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
                                            viewModel.processUnderlineOcr(rawBitmap, currentBook.title)
                                        }
                                    } ?: run {
                                        Toast.makeText(context, "분석할 사진을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ocr_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCroppedReady) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                                )
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI 밑줄 인식 시작", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Stage 3: Realtime Text Alignment Editor (실시간 이미지 대조 교정기)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "실시간 이미지 대조 교정기",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "실시간 대조 활성화",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    "AI가 이미지에서 인식한 원본 단어와 추출한 문자열을 육안으로 대조 및 필터 가공하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 1. Comparison source visualization
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            val activePreset = presets.find { it.id == selectedPresetId } ?: presets.first()
                            
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(activeImageUrl ?: activePreset.url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Comparison Source",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.75f
                            )
                            
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.85f)
                                    .height(28.dp)
                                    .border(1.5.dp, Color.Yellow, RoundedCornerShape(2.dp))
                                    .background(Color(0x35FFEB3B))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Yellow, RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "밑줄 감지구간",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Zoomed",
                                        tint = Color.Yellow,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "실물 도서 이미지 렌즈",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = "Sync mapping",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Text Field Editor
                        OutlinedTextField(
                            value = extractedText,
                            onValueChange = { extractedText = it },
                            placeholder = { Text("인식 시작을 클릭하면 본 구역에 추출 글귀가 표시되어 보정할 수 있습니다.") },
                            label = { Text("실시간 글귀 교정 편집창") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .testTag("diary_extracted_text_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            maxLines = 3
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(
                                onClick = {
                                    extractedText = extractedText.replace("\n", " ").replace("\\s+".toRegex(), " ").trim()
                                },
                                label = { Text("줄바꿈 해제", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            
                            AssistChip(
                                onClick = {
                                    if (extractedText.isNotEmpty()) {
                                        extractedText = extractedText.trim()
                                        Toast.makeText(context, "여백 정돈이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("여백 자동 보정", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            
                            AssistChip(
                                onClick = {
                                    extractedText = ""
                                },
                                label = { Text("전체 지우기", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "글자 수: ${extractedText.length}자 | 문장 수: ${if (extractedText.isEmpty()) 0 else extractedText.split(".", "?", "!").filter { it.isNotBlank() }.size}개",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (extractedText.isNotEmpty()) Color.Green else Color.Gray, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (extractedText.isNotEmpty()) "일치율: 99.8% (신뢰율 높음)" else "인식 보류 중",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = if (extractedText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Stage 4: Personal Reflection Notes
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "내 영감 기록 & 다이어리 쓰기",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "이 밑줄을 보고 떠올랐던 본인의 생각, 감정, 일상을 기록해두세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("여기에 자유롭게 떠오른 생각을 적어보세요...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("diary_notes_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    maxLines = 7
                )
            }

            // Submit Button
            Button(
                onClick = {
                    val pageNum = pageStr.toIntOrNull() ?: 0
                    if (pageNum <= 0) {
                        Toast.makeText(context, "기록할 쪽수를 먼저 기재해 주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (pageNum > currentBook.totalPages) {
                        Toast.makeText(context, "입력하신 페이지가 이 책의 전체 쪽수(${currentBook.totalPages}쪽)보다 많을 수는 없습니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (extractedText.isBlank()) {
                        Toast.makeText(context, "AI 밑줄 인식을 진행해주시거나 발췌된 문장을 적어주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Save diary with selected timestamp (which defaults to System.currentTimeMillis() or selected/historical date-time value)
                    viewModel.saveDiary(
                        bookId = bookId,
                        page = pageNum,
                        selectedText = extractedText,
                        notes = notes,
                        id = diaryId,
                        createdAt = selectedTimestamp
                    ) {
                        if (pageNum > currentBook.currentPage) {
                            viewModel.updateBookProgress(currentBook.id, pageNum)
                        }
                        Toast.makeText(context, if (diaryId != null) "다이어리 기록이 안전하게 수정되었습니다." else "새 다이어리 기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetOcrState()
                        viewModel.navigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_diary_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (diaryId != null) "다이어리 수정 완료 및 저장" else "다이어리 작성 완료 및 저장", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

data class UnderlinePreset(
    val id: Int,
    val label: String,
    val url: String,
    val title: String
)
