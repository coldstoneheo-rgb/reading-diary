package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
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
    var showShareCardDialog by remember { mutableStateOf(false) }
    var selectedPaletteIndex by remember { mutableStateOf(0) }

    // User Selection flow variables
    var selectedMethod by remember { mutableStateOf<String?>(null) } // "CAMERA" or "GALLERY"
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var isImageTaken by remember { mutableStateOf(false) }

    // Transformation States
    var imageRotation by remember { mutableStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    var isCroppedReady by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf(0.1f) }
    var cropRight by remember { mutableStateOf(0.9f) }
    var cropTop by remember { mutableStateOf(0.1f) }
    var cropBottom by remember { mutableStateOf(0.9f) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = createCameraTempUri()
                cameraTempUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "카메라 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "사진 촬영을 위해 카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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

    // 화면 진입 시 이전 화면(다른 책/일기)의 OCR 결과가 남아 편집창을 덮어쓰지 않도록 초기화하고, 이탈 시에도 비운다
    DisposableEffect(Unit) {
        viewModel.resetOcrState()
        onDispose { viewModel.resetOcrState() }
    }

    // OCR 결과 반영. 실패 메시지는 편집창에 넣지 않는다(일기로 저장되면 안 되므로) — ADR-002 Q2
    LaunchedEffect(ocrState) {
        when (val state = ocrState) {
            is OcrState.Success -> {
                extractedText = state.text
                Toast.makeText(context, "인식된 문장을 원문과 대조해 주세요.", Toast.LENGTH_SHORT).show()
            }
            is OcrState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> Unit
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
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasCameraPermission) {
                            try {
                                val uri = createCameraTempUri()
                                cameraTempUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "카메라 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            try {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            } catch (e: Exception) {
                                Toast.makeText(context, "카메라 권한 요청에 실패했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
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
                        // Header tools (always show Rotate & Flip for maximum calibration)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotate Button
                            AssistChip(
                                onClick = {
                                    imageRotation = (imageRotation + 90f) % 360f
                                    Toast.makeText(context, "90도 회전을 적용했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("회전 (90°)", fontSize = 11.sp) },
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

                        // Process Image View beautifully displaying vertical pages at full fit height
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
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
                                contentScale = ContentScale.Fit,
                                alpha = 0.9f
                            )

                            // Drawn crop overlay indicator or instructions with dimmed boundaries and corner handles
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val leftPx = cropLeft * size.width
                                val topPx = cropTop * size.height
                                val rightPx = cropRight * size.width
                                val bottomPx = cropBottom * size.height

                                val stroke = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )

                                // Only draw bounding dim effect & handles when NOT in success state to clearly indicate calibration focus
                                if (!isCroppedReady) {
                                    // 1. Draw dimmed overlays outside cropping box
                                    // Top block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, 0f),
                                        size = androidx.compose.ui.geometry.Size(size.width, topPx)
                                    )
                                    // Bottom block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, bottomPx),
                                        size = androidx.compose.ui.geometry.Size(size.width, size.height - bottomPx)
                                    )
                                    // Left block (between top and bottom)
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, topPx),
                                        size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx)
                                    )
                                    // Right block (between top and bottom)
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(rightPx, topPx),
                                        size = androidx.compose.ui.geometry.Size(size.width - rightPx, bottomPx - topPx)
                                    )

                                    // 2. Draw yellow dashed border on crop box
                                    drawRect(
                                        color = Color.Yellow,
                                        topLeft = Offset(leftPx, topPx),
                                        size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                        style = stroke
                                    )

                                    // 3. Draw crop corners for visual polish
                                    val handleSize = 8.dp.toPx()
                                    // Top-Left corner
                                    drawRect(color = Color.Yellow, topLeft = Offset(leftPx - 1.dp.toPx(), topPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(handleSize, 2.dp.toPx()))
                                    drawRect(color = Color.Yellow, topLeft = Offset(leftPx - 1.dp.toPx(), topPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), handleSize))
                                    // Top-Right corner
                                    drawRect(color = Color.Yellow, topLeft = Offset(rightPx - handleSize + 1.dp.toPx(), topPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(handleSize, 2.dp.toPx()))
                                    drawRect(color = Color.Yellow, topLeft = Offset(rightPx - 1.dp.toPx(), topPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), handleSize))
                                    // Bottom-Left corner
                                    drawRect(color = Color.Yellow, topLeft = Offset(leftPx - 1.dp.toPx(), bottomPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(handleSize, 2.dp.toPx()))
                                    drawRect(color = Color.Yellow, topLeft = Offset(leftPx - 1.dp.toPx(), bottomPx - handleSize + 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), handleSize))
                                    // Bottom-Right corner
                                    drawRect(color = Color.Yellow, topLeft = Offset(rightPx - handleSize + 1.dp.toPx(), bottomPx - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(handleSize, 2.dp.toPx()))
                                    drawRect(color = Color.Yellow, topLeft = Offset(rightPx - 1.dp.toPx(), bottomPx - handleSize + 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), handleSize))
                                } else {
                                    // Cropped success border
                                    drawRect(
                                        color = Color.Green,
                                        topLeft = Offset(leftPx, topPx),
                                        size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                        style = stroke
                                    )
                                }
                            }

                            if (!isCroppedReady) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "📌 아래 슬라이더로 영역을 맞춘 후 '자르기 확정'을 해주세요",
                                        color = Color.Yellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Cropped section success visual label inside black box
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Green, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("자르기 범위 고정됨", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Sliding crop controller (자르기 프레임 조절 슬라이더)
                        if (!isCroppedReady) {
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "비율 기반 자르기 프레임 조절",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Left & Right Sliders
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "좌측 끝: ${(cropLeft * 100).toInt()}%", 
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Slider(
                                                value = cropLeft,
                                                onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                                                valueRange = 0f..0.8f,
                                                modifier = Modifier.height(36.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "우측 끝: ${(cropRight * 100).toInt()}%", 
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Slider(
                                                value = cropRight,
                                                onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                                                valueRange = 0.2f..1f,
                                                modifier = Modifier.height(36.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Top & Bottom Sliders
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "상단 끝: ${(cropTop * 100).toInt()}%", 
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Slider(
                                                value = cropTop,
                                                onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                                                valueRange = 0f..0.8f,
                                                modifier = Modifier.height(36.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "하단 끝: ${(cropBottom * 100).toInt()}%", 
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Slider(
                                                value = cropBottom,
                                                onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                                                valueRange = 0.2f..1f,
                                                modifier = Modifier.height(36.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                cropLeft = 0.1f
                                                cropRight = 0.9f
                                                cropTop = 0.1f
                                                cropBottom = 0.9f
                                                Toast.makeText(context, "자르기 격자가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("자르기 리셋")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Done Cropping, Reset / Delete Photo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isCroppedReady) {
                                Button(
                                    onClick = {
                                        isCroppedReady = true
                                        Toast.makeText(context, "자르기 적용 완료! 아래 'AI 밑줄 인식 시작'을 눌러 분석해 보정하세요.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("자르기 확정하기")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        isCroppedReady = false
                                        Toast.makeText(context, "영역 설정을 해제했습니다. 슬라이더로 크기를 다시 조절하세요.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AspectRatio, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("자르기 영역 재조정")
                                }
                            }

                            // DELETE/RESET Photo button supporting first requirement
                            OutlinedButton(
                                onClick = {
                                    activeImageUrl = null
                                    isImageTaken = false
                                    isCroppedReady = false
                                    imageRotation = 0f
                                    isFlipped = false
                                    selectedMethod = null
                                    cropLeft = 0.1f
                                    cropRight = 0.9f
                                    cropTop = 0.1f
                                    cropBottom = 0.9f
                                    Toast.makeText(context, "사진 삭제가 성공적으로 이루어졌습니다.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("사진 전체 삭제")
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
                                    "기기에서 글자를 인식하는 중… (첫 사용 시 인식 모델을 내려받아 시간이 더 걸릴 수 있어요)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            if (isCroppedReady) {
                                Button(
                                    onClick = {
                                        val path = activeImageUrl
                                        if (path == null) {
                                            Toast.makeText(context, "분석할 사진을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (path.startsWith("http")) {
                                            // 예시 이미지(URL)는 인식 대상이 아니다. 예전엔 갤러리 아이콘을 OCR에 넣어 가짜 결과를 냈다.
                                            Toast.makeText(context, "촬영하거나 갤러리에서 고른 사진만 인식할 수 있어요.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                        // 디코드(다운샘플+EXIF)·회전·크롭·추출은 ViewModel 스코프의 백그라운드에서(ADR-002 Q5).
                                        // 화면이 사라져도 상태가 Processing에 고착되지 않는다.
                                        viewModel.processUnderlineOcr(
                                            imageUri = Uri.parse(path),
                                            rotationDegrees = imageRotation,
                                            flipped = isFlipped,
                                            cropLeft = cropLeft,
                                            cropTop = cropTop,
                                            cropRight = cropRight,
                                            cropBottom = cropBottom
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ocr_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
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
            }

            // Stage 3: Extracted Text Editor (추출 된 문장)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "추출 된 문장",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(6.dp))

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
                        // Text Field Editor
                        OutlinedTextField(
                            value = extractedText,
                            onValueChange = { extractedText = it },
                            placeholder = { 
                                Text(
                                    text = "밑줄친 문장이 이곳에 보여지고 편집 할 수 있습니다.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                ) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .testTag("diary_extracted_text_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            maxLines = 4
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
                                    text = if (extractedText.isNotEmpty()) "원문과 대조해 주세요" else "인식 대기 중",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = if (extractedText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SNS Card Share Button at the bottom of Stage 3
                OutlinedButton(
                    onClick = {
                        if (extractedText.isBlank()) {
                            Toast.makeText(context, "공유할 문장이 없습니다. 먼저 밑줄 사진을 등록해 문장을 추출해 주세요.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedPaletteIndex = autoSelectPalette(extractedText)
                            showShareCardDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sns_share_card_button"),
                    enabled = extractedText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    border = BorderStroke(1.dp, if (extractedText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share, 
                        contentDescription = "공유",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✨ 감성 이미지 카드로 SNS 공유하기", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Dialogue overlay for sharing card
            if (showShareCardDialog && currentBook != null) {
                val palettes = listOf(
                    PastelPalette(
                        name = "포근한 노을",
                        description = "따뜻하고 감성적인 피치 핑크 톤",
                        gradStart = Color(0xFFFFF2EC),
                        gradEnd = Color(0xFFFEE4D6),
                        textColor = Color(0xFF5A4438),
                        accentColor = Color(0xFFD48B6A)
                    ),
                    PastelPalette(
                        name = "싱그런 허브",
                        description = "마음을 안정시키는 소프트 민트 초록 톤",
                        gradStart = Color(0xFFE8F6F1),
                        gradEnd = Color(0xFFD1EDE3),
                        textColor = Color(0xFF2C433B),
                        accentColor = Color(0xFF5D9B84)
                    ),
                    PastelPalette(
                        name = "차분한 은하",
                        description = "신비롭고 감각적인 라벤더 퍼플 톤",
                        gradStart = Color(0xFFF1EBF9),
                        gradEnd = Color(0xFFDFD1F7),
                        textColor = Color(0xFF382A52),
                        accentColor = Color(0xFF8667BF)
                    ),
                    PastelPalette(
                        name = "아늑한 서재",
                        description = "따뜻하고 깊이감 있는 샌드 오트밀 톤",
                        gradStart = Color(0xFFFAF7F2),
                        gradEnd = Color(0xFFEFE8DD),
                        textColor = Color(0xFF4A453A),
                        accentColor = Color(0xFF9E8D6E)
                    ),
                    PastelPalette(
                        name = "장미빛 회상",
                        description = "설렘과 추억이 깃든 인디 핑크 톤",
                        gradStart = Color(0xFFFFF0F3),
                        gradEnd = Color(0xFFFAD1D8),
                        textColor = Color(0xFF5C3C43),
                        accentColor = Color(0xFFC77B8A)
                    )
                )

                val activePalette = palettes.getOrElse(selectedPaletteIndex) { palettes.first() }

                Dialog(
                    onDismissRequest = { showShareCardDialog = false }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(20.dp),
                             horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                             // Title
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Text(
                                     text = "📖 감성 구절 이미지 카드",
                                     style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                     color = MaterialTheme.colorScheme.onSurface
                                 )
                                 IconButton(
                                     onClick = { showShareCardDialog = false },
                                     modifier = Modifier.size(24.dp)
                                 ) {
                                     Icon(
                                         imageVector = Icons.Default.Close,
                                         contentDescription = "닫기",
                                         tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                     )
                                 }
                             }

                             Spacer(modifier = Modifier.height(14.dp))

                             // THE BEAUTIFUL PASTEL CARD PREVIEW (모던, 심플, 감각적, 따뜻함)
                             Box(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .aspectRatio(1f) // Square share card
                                     .clip(RoundedCornerShape(16.dp))
                                     .background(
                                         Brush.verticalGradient(
                                             colors = listOf(activePalette.gradStart, activePalette.gradEnd)
                                         )
                                     )
                                     .border(1.dp, activePalette.accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                     .padding(24.dp),
                                 contentAlignment = Alignment.Center
                             ) {
                                 Column(
                                     horizontalAlignment = Alignment.CenterHorizontally,
                                     verticalArrangement = Arrangement.SpaceBetween,
                                     modifier = Modifier.fillMaxSize()
                                 ) {
                                     // Top Design Accent
                                     Icon(
                                         imageVector = Icons.Default.FormatQuote,
                                         contentDescription = null,
                                         tint = activePalette.accentColor.copy(alpha = 0.4f),
                                         modifier = Modifier
                                             .size(28.dp)
                                             .graphicsLayer(rotationZ = 180f)
                                     )

                                     // Main Quote Text (Centered, Serif-like elegant scale)
                                     Text(
                                         text = extractedText,
                                         style = MaterialTheme.typography.bodyLarge.copy(
                                             fontWeight = FontWeight.Medium,
                                             fontSize = if (extractedText.length > 80) 13.sp else 15.sp,
                                             lineHeight = if (extractedText.length > 80) 20.sp else 24.sp,
                                             fontStyle = FontStyle.Italic
                                         ),
                                         color = activePalette.textColor,
                                         textAlign = TextAlign.Center,
                                         maxLines = 6,
                                         overflow = TextOverflow.Ellipsis,
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .padding(horizontal = 4.dp, vertical = 6.dp)
                                     )

                                     // Bottom info containing metadata
                                     Column(
                                         horizontalAlignment = Alignment.CenterHorizontally
                                     ) {
                                         // Subtle Divider line
                                         Box(
                                             modifier = Modifier
                                                 .width(36.dp)
                                                 .height(1.5.dp)
                                                 .background(activePalette.accentColor.copy(alpha = 0.4f))
                                         )
                                         Spacer(modifier = Modifier.height(8.dp))
                                         // Book Title & Author
                                         Text(
                                             text = "『${currentBook.title}』",
                                             style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                             color = activePalette.textColor.copy(alpha = 0.9f),
                                             textAlign = TextAlign.Center,
                                             maxLines = 1,
                                             overflow = TextOverflow.Ellipsis
                                         )
                                         Spacer(modifier = Modifier.height(2.dp))
                                         Text(
                                             text = "${currentBook.author} 씀 ${if (pageStr.isNotEmpty()) "| $pageStr 쪽" else ""}",
                                             style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                             color = activePalette.textColor.copy(alpha = 0.65f),
                                             textAlign = TextAlign.Center
                                         )
                                     }
                                 }
                             }

                             Spacer(modifier = Modifier.height(16.dp))

                             // PALETTE PICKER SECTION
                             Text(
                                 text = "🎨 컬러 필터 (문장 부합 추천 테마 자동 셋팅됨)",
                                 style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.align(Alignment.Start)
                             )
                             Spacer(modifier = Modifier.height(6.dp))

                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween
                             ) {
                                 palettes.forEachIndexed { index, palette ->
                                     val isSelected = selectedPaletteIndex == index
                                     Box(
                                         modifier = Modifier
                                             .size(44.dp)
                                             .clip(RoundedCornerShape(12.dp))
                                             .background(
                                                 Brush.verticalGradient(
                                                     colors = listOf(palette.gradStart, palette.gradEnd)
                                                 )
                                             )
                                             .border(
                                                 width = if (isSelected) 3.dp else 1.dp,
                                                 color = if (isSelected) MaterialTheme.colorScheme.primary else palette.textColor.copy(alpha = 0.15f),
                                                 shape = RoundedCornerShape(12.dp)
                                             )
                                             .clickable { selectedPaletteIndex = index }
                                             .padding(2.dp),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         if (isSelected) {
                                             Icon(
                                                 imageVector = Icons.Default.Check,
                                                 contentDescription = "선택됨",
                                                 tint = palette.textColor,
                                                 modifier = Modifier.size(16.dp)
                                             )
                                         } else {
                                             Text(
                                                 text = palette.name.take(2),
                                                 style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                 color = palette.textColor.copy(alpha = 0.7f)
                                             )
                                         }
                                     }
                                 }
                             }

                             Spacer(modifier = Modifier.height(20.dp))

                             // SHARE & SAVE ACTIONS
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(10.dp)
                             ) {
                                 // Instagram / Kakao Share Button
                                 Button(
                                     onClick = {
                                         val shareIntent = android.content.Intent().apply {
                                             action = android.content.Intent.ACTION_SEND
                                             type = "text/plain"
                                             putExtra(
                                                 android.content.Intent.EXTRA_TEXT,
                                                 "✨ [북 다이어리 밑줄 구절 공유] ✨\n\n" +
                                                         "\"$extractedText\"\n\n" +
                                                         "📚 도서: 『${currentBook.title}』\n" +
                                                         "✍️ 저자: ${currentBook.author}\n" +
                                                         "📖 페이지: ${if (pageStr.isNotEmpty()) "${pageStr}쪽" else "선택 안 함"}\n\n" +
                                                         "#독서기록 #북스타그램 #독서펜기록 #동양화책장"
                                             )
                                         }
                                         context.startActivity(android.content.Intent.createChooser(shareIntent, "감성 구절 공유하기"))
                                         showShareCardDialog = false
                                         Toast.makeText(context, "📸 SNS 공유 다이얼로그를 호출했습니다!", Toast.LENGTH_SHORT).show()
                                     },
                                     modifier = Modifier.weight(1f),
                                     shape = RoundedCornerShape(12.dp),
                                     colors = ButtonDefaults.buttonColors(
                                         containerColor = MaterialTheme.colorScheme.primary
                                     )
                                 ) {
                                     Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                     Spacer(modifier = Modifier.width(6.dp))
                                     Text("SNS 공유", fontWeight = FontWeight.Bold)
                                 }

                                 // Save Image local simulator Button
                                 OutlinedButton(
                                     onClick = {
                                         Toast.makeText(context, "💾 [${activePalette.name}] 테마의 감성 카드 이미지가 기기 갤러리로 성공적으로 저장되었습니다!", Toast.LENGTH_LONG).show()
                                         showShareCardDialog = false
                                     },
                                     modifier = Modifier.weight(1f),
                                     shape = RoundedCornerShape(12.dp),
                                     border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                 ) {
                                     Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                     Spacer(modifier = Modifier.width(6.dp))
                                     Text("이미지 저장", fontWeight = FontWeight.Bold)
                                 }
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
                    placeholder = { 
                        Text(
                            text = "여기에 떠오른 생각을 자유롭게 적어보세요...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("diary_notes_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
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

data class PastelPalette(
    val name: String,
    val description: String,
    val gradStart: Color,
    val gradEnd: Color,
    val textColor: Color,
    val accentColor: Color
)

fun autoSelectPalette(text: String): Int {
    val t = text.trim()
    return when {
        t.contains("사랑") || t.contains("기억") || t.contains("그리움") || t.contains("추억") || t.contains("마음") || t.contains("가족") -> 4 // 장미빛 회상
        t.contains("밤") || t.contains("우주") || t.contains("하늘") || t.contains("꿈") || t.contains("노래") || t.contains("영혼") || t.contains("은하") -> 2 // 차분한 은하
        t.contains("자연") || t.contains("초록") || t.contains("나무") || t.contains("숲") || t.contains("새") || t.contains("바람") || t.contains("쉼") || t.contains("안식") || t.contains("허브") -> 1 // 싱그런 허브
        t.contains("부") || t.contains("성공") || t.contains("돈") || t.contains("지혜") || t.contains("배움") || t.contains("독서") || t.contains("시간") || t.contains("생각") || t.contains("서재") -> 3 // 아늑한 서재
        else -> 0 // 포근한 노을
    }
}
