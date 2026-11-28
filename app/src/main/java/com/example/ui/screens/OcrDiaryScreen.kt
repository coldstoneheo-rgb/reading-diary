package com.example.ui.screens

import android.os.Build
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.WindowManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.data.ocr.BitmapDecoding
import com.example.data.ocr.CropGeometry
import com.example.data.photo.CameraTempFiles
import com.example.data.photo.RecallPhoto
import com.example.ui.viewmodel.OcrState
import com.example.ui.viewmodel.PhotoDrafts
import com.example.ui.viewmodel.PhotoInputSlot
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** «추출 된 문장» 편집창에 보이는 줄 수: 하한(빈 칸 · 짧은 글) · 상한(긴 글은 칸 안 스크롤). HO-2026-1127 조건 5 — 전에는 4줄. */
internal const val EXTRACTED_TEXT_MIN_LINES = 6
internal const val EXTRACTED_TEXT_MAX_LINES = 12

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OcrDiaryScreen(
    viewModel: ReadingViewModel,
    bookId: Int,
    diaryId: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val loadedBooks by viewModel.loadedBooks.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val ocrState by viewModel.ocrState.collectAsState()
    val preciseState by viewModel.preciseOcrState.collectAsState()
    val preciseAvailable by viewModel.preciseAnalysisAvailable.collectAsState()
    var showPreciseConfirm by remember { mutableStateOf(false) }

    val currentBook = loadedBooks?.find { it.id == bookId }
    val editingDiary = remember(diaryId, diaries) { diaries.find { it.id == diaryId } }

    // 카메라 앱을 다녀오거나 회전하면 Activity가 다시 만들어진다. 입력 중인 글과 사진 상태를 잃지 않게 저장 가능한 상태로 둔다.
    // 화면 위치도 함께 복원되므로(MainActivity) 같은 책·같은 기록의 폼에만 이어진다. inputs는 복원 때 검증되지 않아 저장 키에 넣는다.
    // 토큰은 진입마다 새로 만들고 복원 때만 이어지므로, 번들에 남은 옛 입력이 나중에 연 폼으로 들어오지 않는다.
    val formKey = remember(bookId, diaryId) { "ocr_form/${viewModel.diaryFormToken.value}/$bookId/$diaryId" }
    var pageStr by rememberSaveable(key = "$formKey/page") { mutableStateOf("") }
    var extractedText by rememberSaveable(key = "$formKey/text") { mutableStateOf("") }
    // 마지막 인식의 틀 안 전체 글. 밑줄 어절이 있으면 편집창은 그 어절만으로 시작하고, 전체 글은 접힌 «원문 전체»로 내려간다.
    var ocrFullText by rememberSaveable(key = "$formKey/ocrFull") { mutableStateOf("") }
    var showOcrFullText by rememberSaveable(key = "$formKey/ocrFullShown") { mutableStateOf(false) }
    var notes by rememberSaveable(key = "$formKey/notes") { mutableStateOf("") }
    var editDetailsLoaded by rememberSaveable(key = "$formKey/editLoaded") { mutableStateOf(false) }
    // 밑줄 미리 선택(새 인식마다 새 값). 칩 상태는 새 값이 올 때만 새로 만든다(사용자가 고친 칩은 같은 값 동안 유지).
    // 재생성(회전 등) 때는 resetOcrState가 값을 비우므로 패널도 사라진다 — rememberSaveable 복원은 지금 경로에서 쓰이지 않는 대비다.
    val preselect by viewModel.underlinePreselect.collectAsState()
    val preselectChosen = rememberSaveable(
        preselect,
        key = "$formKey/preselectChosen",
        saver = Saver<SnapshotStateList<Boolean>, BooleanArray>(save = { it.toBooleanArray() }, restore = { it.toMutableList().toMutableStateList() })
    ) { (preselect?.map { it.selected } ?: emptyList()).toMutableStateList() }

    // Date & Time picker state: default to current or diary's historic time
    var selectedTimestamp by rememberSaveable(key = "$formKey/time") { mutableStateOf(System.currentTimeMillis()) }

    val dateFormatter = remember { SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA) }
    val timeFormatter = remember { SimpleDateFormat("a h:mm", Locale.KOREA) }

    // User Selection flow variables
    var selectedMethod by rememberSaveable(key = "$formKey/method") { mutableStateOf<String?>(null) } // "CAMERA" or "GALLERY"
    var activeImageUrl by rememberSaveable(key = "$formKey/image") { mutableStateOf<String?>(null) }
    var isImageTaken by rememberSaveable(key = "$formKey/imageTaken") { mutableStateOf(false) }

    // Transformation States
    var imageRotation by rememberSaveable(key = "$formKey/rotation") { mutableStateOf(0f) }
    var isFlipped by rememberSaveable(key = "$formKey/flipped") { mutableStateOf(false) }
    var isCroppedReady by rememberSaveable(key = "$formKey/cropReady") { mutableStateOf(false) }
    var cropLeft by rememberSaveable(key = "$formKey/cropLeft") { mutableStateOf(CropGeometry.DEFAULT_LEFT) }
    var cropRight by rememberSaveable(key = "$formKey/cropRight") { mutableStateOf(CropGeometry.DEFAULT_RIGHT) }
    var cropTop by rememberSaveable(key = "$formKey/cropTop") { mutableStateOf(CropGeometry.DEFAULT_TOP) }
    var cropBottom by rememberSaveable(key = "$formKey/cropBottom") { mutableStateOf(CropGeometry.DEFAULT_BOTTOM) }

    // Camera/Gallery integration state
    var cameraTempUri by rememberSaveable(key = "$formKey/cameraUri") { mutableStateOf<Uri?>(null) }
    var cameraStartedAt by rememberSaveable(key = "$formKey/cameraStartedAt") { mutableStateOf(0L) }

    fun clearShownPhoto() {
        viewModel.clearUnderlinePreselect() // 앞 사진의 미리 선택 칩이 새 사진 · 빈 화면에 남지 않게
        viewModel.clearOcrError() // 앞 사진의 실패 안내가 새 사진 아래에 남지 않게
        ocrFullText = "" // 앞 사진의 «원문 전체»가 새 사진 · 빈 화면에 남지 않게(편집창 글은 전처럼 남는다)
        showOcrFullText = false
        activeImageUrl = null
        isImageTaken = false
        isCroppedReady = false
        imageRotation = 0f
        isFlipped = false
        selectedMethod = null
        cropLeft = CropGeometry.DEFAULT_LEFT
        cropRight = CropGeometry.DEFAULT_RIGHT
        cropTop = CropGeometry.DEFAULT_TOP
        cropBottom = CropGeometry.DEFAULT_BOTTOM
    }

    /** 새 사진을 띄운다. 앞 사진의 회전·반전·자르기 틀은 새 사진에 맞지 않으므로 기본값(틀 = 사진 전체)으로 되돌린다. */
    fun showNewPhoto(uri: Uri, method: String) {
        clearShownPhoto()
        activeImageUrl = uri.toString()
        isImageTaken = true
        selectedMethod = method
    }

    fun createCameraTempUri(): Uri {
        cameraStartedAt = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val tempFile = java.io.File.createTempFile(
            CameraTempFiles.PREFIX,
            CameraTempFiles.SUFFIX,
            storageDir
        )
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (uri != null && (success || CameraTempFiles.hasNewPhoto(context.cacheDir, uri.lastPathSegment, cameraStartedAt))) {
            showNewPhoto(uri, "CAMERA")
            Toast.makeText(context, "📸 실물 페이지 캡처 성공! 잘라내기 영역을 설정하세요.", Toast.LENGTH_SHORT).show()
        } else if (!success) {
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
            showNewPhoto(it, "GALLERY")
            Toast.makeText(context, "🖼️ 사진 선택 성공! 자르기 단계를 거쳐 이미지 상태를 완성해 주세요.", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "사진 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 편집 모드의 보관 사진. 불러오기 전에 저장되면 빈 목록이 «전부 지우기»로 읽히므로 photosLoaded로 구분한다.
    val diarySaving by viewModel.diarySaving.collectAsState()
    // 저장을 누른 새 기록의 작성 시각. 저장 중 앱이 내려가 복원되면 이 값으로 초안이 이미 저장됐는지 확인한다.
    // 누른 뒤 시각을 고쳐도 판정이 빗나가지 않게 따로 둔다. 편집은 다시 저장해도 중복이 없어 쓰지 않는다.
    var submittedCreatedAt by rememberSaveable(key = "$formKey/submittedAt") { mutableStateOf<Long?>(null) }
    val currentScreen by viewModel.currentScreenState.collectAsState()
    LaunchedEffect(submittedCreatedAt, diarySaving, currentScreen) {
        val createdAt = submittedCreatedAt ?: return@LaunchedEffect
        if (diarySaving) return@LaunchedEffect
        val here = Screen.AddDiary(bookId, diaryId)
        if (viewModel.currentScreenState.value != here) return@LaunchedEffect
        val alreadySaved = try {
            viewModel.isNewDiaryAlreadySaved(bookId, createdAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        withContext(Dispatchers.Main.immediate) {
            if (alreadySaved == null) {
                // 못 가렸다. 버튼을 계속 막으면 초안을 저장할 길이 없어지므로 풀어 주되, 중복 가능성을 알린다.
                submittedCreatedAt = null
                Toast.makeText(context, "저장 여부를 확인하지 못했습니다. 이미 저장됐다면 다시 저장할 때 기록이 2건이 될 수 있습니다.", Toast.LENGTH_LONG).show()
            } else if (!alreadySaved) {
                submittedCreatedAt = null
            } else if (viewModel.currentScreenState.value == here) {
                Toast.makeText(context, "이미 저장된 기록입니다.", Toast.LENGTH_SHORT).show()
                viewModel.navigateBack()
            }
        }
    }
    var photosLoaded by remember(diaryId) { mutableStateOf(diaryId == null) }
    var loadedPhotos by remember(diaryId) { mutableStateOf<List<RecallPhoto>>(emptyList()) }
    var photoDrafts by rememberSaveable(key = "$formKey/drafts", stateSaver = PhotoDraftsSaver) { mutableStateOf<List<PhotoInputSlot>>(emptyList()) }
    // 초안이 출발한 DB 사진 id. null = 아직 불러오지 않음. 저장 중 앱이 내려가면 번들에는 커밋 전 초안이 남으므로, 복원 때 이 값으로 커밋 여부를 가린다.
    var draftBaseIds by rememberSaveable(key = "$formKey/draftBaseIds") { mutableStateOf<ArrayList<Int>?>(null) }
    var photoReplaceIndex by rememberSaveable(key = "$formKey/replaceIndex") { mutableStateOf<Int?>(null) }
    var photoCameraUri by rememberSaveable(key = "$formKey/photoCameraUri") { mutableStateOf<String?>(null) }

    LaunchedEffect(diaryId) {
        if (diaryId == null) return@LaunchedEffect
        loadedPhotos = try {
            viewModel.loadPhotos(diaryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // photosLoaded를 올리지 않아 저장이 막힌다. 커밋 여부를 못 가린 채 저장하면 커밋된 사진이 지워질 수 있다.
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(context, "사진 정보를 불러오지 못했습니다. 화면을 다시 열어 주세요.", Toast.LENGTH_LONG).show()
            }
            return@LaunchedEffect
        }
        val currentIds = loadedPhotos.map { it.photoId }
        val baseIds = draftBaseIds
        // DB가 기준과 다르면 이 초안은 이미 커밋됐다. 그대로 다시 저장하면 원본을 못 읽을 때 커밋된 사진이 지워지므로 커밋 결과를 새 기준으로 삼는다.
        if (baseIds != currentIds) {
            photoDrafts = currentIds.map { PhotoInputSlot.Existing(it) }
            draftBaseIds = ArrayList(currentIds)
            if (baseIds != null) clearShownPhoto() // 화면의 사진은 커밋 때 함께 보관됐다. 남겨 두면 다시 저장될 때 또 들어간다
        }
        photosLoaded = true
    }

    fun applyPickedPhoto(uri: Uri) {
        photoDrafts = photoReplaceIndex?.let { PhotoDrafts.replaceAt(photoDrafts, it, uri) } ?: PhotoDrafts.add(photoDrafts, uri)
        photoReplaceIndex = null
    }

    val photoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = photoCameraUri?.let(Uri::parse)
        if (uri != null && (success || CameraTempFiles.hasNewPhoto(context.cacheDir, uri.lastPathSegment, cameraStartedAt))) {
            applyPickedPhoto(uri)
        }
    }
    fun launchPhotoCamera() {
        try {
            val uri = createCameraTempUri()
            photoCameraUri = uri.toString()
            photoCameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "카메라 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
    val photoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPhotoCamera()
        else Toast.makeText(context, "사진 촬영을 위해 카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }
    val photoGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { applyPickedPhoto(it) }
    }

    // 저장하지 않은 입력이 있으면 뒤로 갈 때 한 번 묻는다. 이 폼이 저장을 시작했거나 이미 떠나는 중(전환 애니메이션)이면 묻지 않는다.
    // diarySaving은 앱 전체 상태라 쓰지 않는다 — 앞 기록을 저장하는 동안 쓰던 새 기록도 지켜야 한다.
    var submittedHere by remember { mutableStateOf(false) }
    val isCurrent = currentScreen == Screen.AddDiary(bookId, diaryId)
    val hasUnsavedInput = isCurrent && !submittedHere && submittedCreatedAt == null && if (diaryId == null) {
        extractedText.isNotBlank() || notes.isNotBlank() || activeImageUrl != null || photoDrafts.isNotEmpty()
    } else {
        val original = editingDiary
        editDetailsLoaded && original != null && (
            extractedText != original.selectedText || notes != original.notes || pageStr != original.page.toString() ||
                selectedTimestamp != original.createdAt || activeImageUrl != null ||
                (photosLoaded && photoDrafts != loadedPhotos.map { PhotoInputSlot.Existing(it.photoId) })
            )
    }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val leave = {
        viewModel.resetOcrState()
        viewModel.navigateBack()
    }
    BackHandler(enabled = hasUnsavedInput) { showDiscardConfirm = true }

    // 키보드가 뜨면 상단 바(저장)는 그 자리에 두고 내용만 줄인다. 창의 기본(adjustUnspecified)은 스크롤 뷰를 못 찾는 Compose 창을
    // adjustPan으로 다뤄 화면 전체를 밀어 올렸다(상단 바가 화면 밖으로). 이 화면에 있는 동안만 adjustResize로 두고 떠날 때 되돌린다.
    // edge-to-edge 창은 크기가 바뀌지 않고 IME 인셋만 받으므로, 아래 Scaffold가 그 인셋을 내용 여백으로 쓴다.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val previous = window?.attributes?.softInputMode
        fun apply(mode: Int) {
            // setSoftInputMode(0)은 «테마 값 따르기»로만 바뀌고 값이 남는다 — 속성을 직접 돌려놓는다.
            val lp = window?.attributes ?: return
            lp.softInputMode = mode
            window.attributes = lp
        }
        if (previous != null) apply(previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv() or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (previous != null) apply(previous) }
    }

    // 편집창 글을 한 번에 바꾸는 동작(줄바꿈 해제 · 전체 지우기 · 원문 전체로 바꾸기)은 되돌리기 스낵바를 띄운다. 편집창 글만 되돌린다.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    fun replaceTextWithUndo(newText: String, message: String) {
        val before = extractedText
        if (newText == before) return
        extractedText = newText
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message, actionLabel = "되돌리기", duration = SnackbarDuration.Long)
            // 그 사이 사용자가 글을 또 고쳤으면 되돌리지 않는다(새로 친 글을 덮지 않게).
            if (result == SnackbarResult.ActionPerformed && extractedText == newText) extractedText = before
        }
    }
    // «자르기 처음대로»: 틀을 사진 전체로 돌리고 되돌리기 스낵바를 띄운다. 그 사이 사진이 바뀌었거나 틀을 또 움직였으면 되돌리지 않는다.
    fun resetCropWithUndo() {
        val before = listOf(cropLeft, cropTop, cropRight, cropBottom)
        val defaults = listOf(CropGeometry.DEFAULT_LEFT, CropGeometry.DEFAULT_TOP, CropGeometry.DEFAULT_RIGHT, CropGeometry.DEFAULT_BOTTOM)
        if (before == defaults) return
        val photo = activeImageUrl
        val rotation0 = imageRotation
        val flipped0 = isFlipped
        cropLeft = CropGeometry.DEFAULT_LEFT
        cropTop = CropGeometry.DEFAULT_TOP
        cropRight = CropGeometry.DEFAULT_RIGHT
        cropBottom = CropGeometry.DEFAULT_BOTTOM
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar("자르기 틀을 처음대로 돌렸어요", actionLabel = "되돌리기", duration = SnackbarDuration.Long)
            val untouched = listOf(cropLeft, cropTop, cropRight, cropBottom) == defaults && activeImageUrl == photo && !isCroppedReady &&
                imageRotation == rotation0 && isFlipped == flipped0
            if (result == SnackbarResult.ActionPerformed && untouched) {
                cropLeft = before[0]; cropTop = before[1]; cropRight = before[2]; cropBottom = before[3]
            }
        }
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text("지금까지 적은 내용과 고른 사진이 사라집니다.") },
            confirmButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false; leave() },
                    modifier = Modifier.testTag("discard_diary_confirm")
                ) { Text("나가기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("계속 쓰기") } }
        )
    }

    // Load initial edit details. 처음 한 번만 채운다(복원된 입력이나 편집 중 글을 DB 값으로 덮지 않게).
    LaunchedEffect(editingDiary) {
        editingDiary?.let {
            if (!editDetailsLoaded) {
                pageStr = it.page.toString()
                extractedText = it.selectedText
                notes = it.notes
                selectedTimestamp = it.createdAt
                editDetailsLoaded = true
            }
        }
    }

    // 화면 진입 시 이전 화면(다른 책/일기)의 OCR 결과가 남아 편집창을 덮어쓰지 않도록 초기화하고, 이탈 시에도 비운다
    DisposableEffect(Unit) {
        viewModel.resetOcrState()
        // 정밀 분석(유료·외부 전송)은 소유자(책/일기)가 바뀔 때만 버린다. 회전 등 재생성에서는 요청과 결과가 살아남는다.
        viewModel.enterPreciseScope("$bookId/$diaryId")
        onDispose {
            viewModel.resetOcrState()
            // 촬영 임시 파일은 화면을 떠날 때 지운다(ADR-002 후속 발견). 시스템이 재생성하려고 Activity를 부술 때는 지우지 않는다 —
            // 카메라가 그 파일에 쓰는 중이거나, 재생성을 견디는 정밀 분석 작업이 읽고 있을 수 있다. 보관 사진 저장이 진행 중이면 ViewModel이 끝난 뒤로 미룬다.
            val activity = context as? ComponentActivity
            val recreating = activity != null && !activity.isFinishing && activity.lifecycle.currentState == Lifecycle.State.DESTROYED
            if (!recreating) {
                viewModel.releaseCameraTempFiles(context.cacheDir)
            }
        }
    }

    // 정밀 분석 실패는 토스트로만 알리고 편집창은 건드리지 않는다
    LaunchedEffect(preciseState) {
        (preciseState as? OcrState.Error)?.let {
            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            viewModel.resetPreciseOcrState()
        }
    }

    // OCR 결과 반영. 실패 메시지는 편집창에 넣지 않는다(일기로 저장되면 안 되므로) — ADR-002 Q2
    LaunchedEffect(ocrState) {
        when (val state = ocrState) {
            is OcrState.Success -> {
                extractedText = state.text
                ocrFullText = state.text
                showOcrFullText = false
                Toast.makeText(context, "인식된 문장을 원문과 대조해 주세요.", Toast.LENGTH_SHORT).show()
            }
            // 실패는 «틀 안 글자 읽기» 버튼 아래에 남긴다(OcrErrorText). 토스트는 두 줄에서 잘려 다음에 할 일이 보이지 않았다.
            else -> Unit
        }
    }

    // 밑줄 어절이 오면 편집창을 그 어절만으로 바꾼다 — 편집창이 아직 인식 결과 그대로일 때만(사용자가 고친 글 · 기존 기록의 글은 덮지 않는다).
    // 미리 선택이 0개면 아무것도 하지 않는다(전체 글이 그대로 남는다).
    LaunchedEffect(preselect) {
        val words = preselect ?: return@LaunchedEffect
        if (words.none { it.selected } || ocrFullText.isEmpty() || extractedText != ocrFullText) return@LaunchedEffect
        extractedText = preselectedText(words, words.map { it.selected })
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
            if (loadedBooks == null) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("해당 책을 일치시킬 수 없습니다.")
                    TextButton(onClick = { viewModel.navigateBack() }) { Text("돌아가기") }
                }
            }
        }
        return
    }

    /** 저장(상단 바 «저장»). 빠진 값은 토스트로 알리고 멈춘다. */
    fun submitDiary() {
        val pageNum = pageStr.toIntOrNull() ?: 0
        if (pageNum <= 0) {
            Toast.makeText(context, "기록할 쪽수를 먼저 기재해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (pageNum > currentBook.totalPages) {
            Toast.makeText(context, "입력하신 페이지가 이 책의 전체 쪽수(${currentBook.totalPages}쪽)보다 많을 수는 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (extractedText.isBlank()) {
            Toast.makeText(context, "사진에서 글자를 읽어 오거나 구절을 직접 적어 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!photosLoaded) {
            Toast.makeText(context, "사진 정보를 불러오는 중입니다. 잠시 뒤 다시 눌러 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 화면에 남겨 둔 사진은 체크 없이 함께 보관한다(ADR-006 Q-a). 빼려면 «사진 전체 삭제».
        val shownPhoto = activeImageUrl?.takeIf { !it.startsWith("http") }
        val photoFits = PhotoDrafts.canAdd(photoDrafts)
        val slots = if (shownPhoto != null && photoFits) {
            photoDrafts + PhotoInputSlot.New(Uri.parse(shownPhoto), imageRotation, isFlipped)
        } else {
            photoDrafts
        }

        // 저장 완료는 사용자가 이미 다른 화면으로 갔을 수 있으므로, 저장을 시작한 이 화면일 때만 닫는다.
        val startedOn = viewModel.currentScreenState.value
        submittedHere = true
        if (diaryId == null) submittedCreatedAt = selectedTimestamp

        // Save diary with selected timestamp (which defaults to System.currentTimeMillis() or selected/historical date-time value)
        viewModel.saveDiaryWithPhotos(
            bookId = bookId,
            page = pageNum,
            selectedText = extractedText,
            notes = notes,
            id = diaryId,
            createdAt = selectedTimestamp,
            photos = PhotoDrafts.toInput(slots, loadedPhotos.map { it.photoId }),
            onFailure = {
                submittedHere = false
                // 이미 화면을 떠났으면 입력이 남아 있지 않다.
                val message = if (viewModel.currentScreenState.value === startedOn) {
                    "기록을 저장하지 못했습니다. 입력한 내용은 그대로 있으니 다시 시도해 주세요."
                } else {
                    "기록을 저장하지 못했습니다. 다시 작성해 주세요."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        ) { failedPhotos ->
            if (pageNum > currentBook.currentPage) {
                viewModel.updateBookProgress(currentBook.id, pageNum)
            }
            val saved = if (diaryId != null) "다이어리 기록이 안전하게 수정되었습니다." else "새 다이어리 기록이 안전하게 저장되었습니다."
            val notice = when {
                failedPhotos > 0 -> "글은 저장했지만 사진 ${failedPhotos}장을 저장하지 못했습니다."
                shownPhoto != null && !photoFits -> "글은 저장했습니다. 사진은 기록당 ${PhotoDrafts.MAX_PER_DIARY}장까지라 방금 사진은 보관하지 않았습니다."
                else -> saved
            }
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            if (viewModel.navigateBackIfCurrent(startedOn)) viewModel.resetOcrState()
        }
    
    }

    fun launchCamera() {
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
    }

    fun launchGallery() {
        try {
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(context, "갤러리 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 키보드 인셋을 내용 여백에 더한다(위 adjustResize와 짝). 상단 바는 그대로, 내용 칸만 키보드 위까지로 줄어든다.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (diaryId != null) "기록 고치기" else "기록하기",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("diary_screen_title")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedInput) showDiscardConfirm = true else leave() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconAction("사진 촬영", Icons.Default.PhotoCamera, onClick = { launchCamera() }, modifier = Modifier.testTag("diary_camera_button"))
                    IconAction("사진 선택", Icons.Default.Collections, onClick = { launchGallery() }, modifier = Modifier.testTag("diary_gallery_button"))
                    // 저장 = 글자 버튼(LC 판정 · REDESIGN §7). 저장할 글이 없으면 비활성 꼴. 복원 판정이 끝나기 전에 또 누르면 같은 작성 시각의 기록이 2건이 된다.
                    TextButton(
                        onClick = { submitDiary() },
                        enabled = !diarySaving && submittedCreatedAt == null && extractedText.isNotBlank(),
                        modifier = Modifier.testTag("save_diary_submit_button")
                    ) {
                        // 화면의 주 동작이라 TextButton 기본(labelLarge 14sp)보다 크게 — 16sp(LC 게이트 관찰 · R2)
                        Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                .testTag("diary_form_scroll")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            // 다른 묶음 사이 16dp · 같은 묶음 안 8dp(REDESIGN §3)
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 날짜 · 시각 · 쪽: «이름표 + 값» 한 줄씩(48dp 이상), 줄 사이 1dp 구분선
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                EntryRow(label = "날짜", onClick = { showDatePicker() }, onClickLabel = "날짜 고르기", modifier = Modifier.testTag("diary_date_row")) {
                    EntryValue(dateFormatter.format(Date(selectedTimestamp)), Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EntryDivider()
                EntryRow(label = "시각", onClick = { showTimePicker() }, onClickLabel = "시각 고르기", modifier = Modifier.testTag("diary_time_row")) {
                    EntryValue(timeFormatter.format(Date(selectedTimestamp)), Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EntryDivider()
                EntryRow(label = "쪽", modifier = Modifier.testTag("diary_page_row")) {
                    val valueStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    BasicTextField(
                        value = pageStr,
                        onValueChange = { pageStr = it },
                        textStyle = valueStyle,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diary_page_input"),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (pageStr.isEmpty()) {
                                    Text("필수 · 예: 150", style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            // 사진 자리: 사진이 없으면 한 줄 안내(촬영 · 선택은 상단 바 아이콘). 사진이 있으면 아래 자르기 카드가 이 자리다.
            if (activeImageUrl == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("photo_slot"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "위 아이콘으로 책 페이지를 찍거나 골라 주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 자르기 단계(REDESIGN §2-3 · R2): 사진 상자 = 카드 폭 · 사진 비 유지(최대 = 바를 뺀 높이의 58%) · 사진 위 도구 줄(아이콘만) ·
            // 접힌 «틀 조정»(접혀도 지금 틀은 사진 위에 늘 보인다, 보정 3) · 한 줄 동작(자르기 확정 → 틀 안 글자 읽기).
            if (isImageTaken && activeImageUrl != null) {
                var showCropAdjust by rememberSaveable(key = "$formKey/cropAdjustOpen") { mutableStateOf(false) }
                var showPhotoClearConfirm by remember { mutableStateOf(false) }
                if (showPhotoClearConfirm) {
                    AlertDialog(
                        onDismissRequest = { showPhotoClearConfirm = false },
                        title = { Text("사진을 지울까요?") },
                        text = { Text("회전 · 반전 · 자르기 틀도 처음으로 돌아갑니다. 편집창에 적은 글은 그대로 남아요.") },
                        confirmButton = {
                            TextButton(
                                // 지운 뒤 같은 사진을 다시 골라도 옛 틀이 되돌아오지 않게 «처음대로» 되돌리기 스낵바를 닫는다.
                                onClick = { showPhotoClearConfirm = false; snackbarHostState.currentSnackbarData?.dismiss(); clearShownPhoto() },
                                modifier = Modifier.testTag("photo_clear_confirm")
                            ) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPhotoClearConfirm = false }, modifier = Modifier.testTag("photo_clear_cancel")) { Text("취소") }
                        }
                    )
                }
                val frameIsDefault = cropLeft == CropGeometry.DEFAULT_LEFT && cropTop == CropGeometry.DEFAULT_TOP &&
                    cropRight == CropGeometry.DEFAULT_RIGHT && cropBottom == CropGeometry.DEFAULT_BOTTOM
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("camera_crop_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    // 자르기 틀은 상자가 아니라 사진이 실제로 그려진 사각형 기준(CropGeometry). 크기를 모르면 상자 전체.
                    val orientedImageSize by produceState<Pair<Int, Int>?>(initialValue = null, activeImageUrl) {
                        value = null // 사진이 바뀌면 새 크기를 읽기 전까지 이전 사진 비율로 그리지 않는다
                        value = activeImageUrl?.let { url ->
                            withContext(Dispatchers.IO) { BitmapDecoding.orientedSize(context, Uri.parse(url)) }
                        }?.takeIf { it.first > 0 && it.second > 0 }
                    }
                    // 사진 상자 최대 높이 = 쓸 수 있는 높이의 58%(60%면 411×914dp 폰에서 버튼 아래 4dp가 바에 가렸다). API 35+에서 screenHeightDp는 상태 표시줄 · 내비게이션 바를
                    // 포함하므로 그만큼 빼야 세로로 긴 사진에서도 «자르기 확정»이 첫 화면 안에 든다(1080×2400에서 아래가 잘리던 결함).
                    val systemBarsDp = if (Build.VERSION.SDK_INT >= 35) {
                        val bars = WindowInsets.systemBars
                        with(LocalDensity.current) { (bars.getTop(this) + bars.getBottom(this)).toDp() }
                    } else 0.dp
                    val maxPhotoHeight = (LocalConfiguration.current.screenHeightDp.dp - systemBarsDp) * 0.58f
                    Column(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
                        // 사진 위 도구 줄: 아이콘만(48dp · 낭독 이름 = 툴팁). 검은 사진 상자 위라 흰 그림.
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("crop_tool_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconAction("사진 지우기", Icons.Default.DeleteOutline, onClick = { showPhotoClearConfirm = true }, modifier = Modifier.testTag("photo_clear_button"))
                                Spacer(modifier = Modifier.weight(1f))
                                IconAction(
                                    "90도 회전",
                                    Icons.Default.RotateRight,
                                    onClick = { imageRotation = (imageRotation + 90f) % 360f },
                                    modifier = Modifier.testTag("crop_rotate_button")
                                )
                                IconAction(
                                    "좌우 반전",
                                    Icons.Default.Flip,
                                    checked = isFlipped,
                                    onClick = { isFlipped = !isFlipped },
                                    modifier = Modifier
                                        .background(if (isFlipped) Color.White.copy(alpha = 0.24f) else Color.Transparent, CircleShape)
                                        .testTag("crop_flip_button")
                                )
                                IconAction(
                                    "자르기 처음대로",
                                    Icons.Default.RestartAlt,
                                    enabled = !isCroppedReady && !frameIsDefault,
                                    onClick = { resetCropWithUndo() },
                                    modifier = Modifier.testTag("crop_reset_button")
                                )
                            }
                        }
                        // 사진 상자: 폭 = 카드 폭, 높이 = 사진 비(회전 반영) · 최대 = 바를 뺀 높이의 58%(maxPhotoHeight). 크기를 모르면 3:4(책 쪽).
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val heightPerWidth = orientedImageSize?.let { (w, h) ->
                                if (CropGeometry.isQuarterTurn(imageRotation)) w.toFloat() / h else h.toFloat() / w
                            } ?: (4f / 3f)
                            val photoHeight = minOf(maxWidth * heightPerWidth, maxPhotoHeight)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(photoHeight)
                                    .testTag("crop_photo_box")
                            ) {
                                // 사진 배치와 틀을 dp로 한 번만 계산한다(그림·틀이 같은 값을 쓴다).
                                val overlay = CropGeometry.overlay(
                                    maxWidth.value, maxHeight.value, orientedImageSize, imageRotation,
                                    cropLeft, cropTop, cropRight, cropBottom
                                )
                                val shownImage = overlay.image
                                AsyncImage(
                                    model = activeImageUrl,
                                    contentDescription = "고른 책 페이지 사진",
                                    modifier = (if (orientedImageSize == null) Modifier.fillMaxSize() else {
                                        // 회전 전 크기로 잡고 돌려서, 돌린 뒤 모양이 shownImage와 정확히 겹치게 한다.
                                        val quarter = CropGeometry.isQuarterTurn(imageRotation)
                                        Modifier
                                            .align(Alignment.Center)
                                            .requiredSize(
                                                width = (if (quarter) shownImage.height else shownImage.width).dp,
                                                height = (if (quarter) shownImage.width else shownImage.height).dp
                                            )
                                    })
                                        .rotate(imageRotation)
                                        .graphicsLayer(
                                            scaleX = if (isFlipped) -1f else 1f,
                                            scaleY = 1f
                                        ),
                                    contentScale = ContentScale.Fit,
                                    alpha = 0.9f
                                )

                                // 지금 틀: «틀 조정»이 접혀 있어도 늘 그린다. 확정 전 = 노란 점선 + 바깥 어둡게, 확정 뒤 = 초록 테두리.
                                val pct = { v: Float -> (v * 100).toInt() }
                                val frameDescription = "자르기 틀 왼쪽 ${pct(cropLeft)}% · 위 ${pct(cropTop)}% · 오른쪽 ${pct(cropRight)}% · 아래 ${pct(cropBottom)}%" +
                                    if (isCroppedReady) " · 고정됨" else ""
                                Canvas(modifier = Modifier.fillMaxSize().testTag("crop_frame").semantics { contentDescription = frameDescription }) {
                                    val leftPx = overlay.crop.left.dp.toPx()
                                    val topPx = overlay.crop.top.dp.toPx()
                                    val rightPx = overlay.crop.right.dp.toPx()
                                    val bottomPx = overlay.crop.bottom.dp.toPx()

                                    val stroke = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                    )

                                    if (!isCroppedReady) {
                                        val dim = Color.Black.copy(alpha = 0.65f)
                                        drawRect(color = dim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, topPx))
                                        drawRect(color = dim, topLeft = Offset(0f, bottomPx), size = androidx.compose.ui.geometry.Size(size.width, size.height - bottomPx))
                                        drawRect(color = dim, topLeft = Offset(0f, topPx), size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx))
                                        drawRect(color = dim, topLeft = Offset(rightPx, topPx), size = androidx.compose.ui.geometry.Size(size.width - rightPx, bottomPx - topPx))

                                        drawRect(
                                            color = Color.Yellow,
                                            topLeft = Offset(leftPx, topPx),
                                            size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                            style = stroke
                                        )

                                        // 모서리 표시(끌기는 아직 없다 — REDESIGN §7-2)
                                        val handleSize = 8.dp.toPx()
                                        val t = 2.dp.toPx()
                                        val o = 1.dp.toPx()
                                        drawRect(color = Color.Yellow, topLeft = Offset(leftPx - o, topPx - o), size = androidx.compose.ui.geometry.Size(handleSize, t))
                                        drawRect(color = Color.Yellow, topLeft = Offset(leftPx - o, topPx - o), size = androidx.compose.ui.geometry.Size(t, handleSize))
                                        drawRect(color = Color.Yellow, topLeft = Offset(rightPx - handleSize + o, topPx - o), size = androidx.compose.ui.geometry.Size(handleSize, t))
                                        drawRect(color = Color.Yellow, topLeft = Offset(rightPx - o, topPx - o), size = androidx.compose.ui.geometry.Size(t, handleSize))
                                        drawRect(color = Color.Yellow, topLeft = Offset(leftPx - o, bottomPx - o), size = androidx.compose.ui.geometry.Size(handleSize, t))
                                        drawRect(color = Color.Yellow, topLeft = Offset(leftPx - o, bottomPx - handleSize + o), size = androidx.compose.ui.geometry.Size(t, handleSize))
                                        drawRect(color = Color.Yellow, topLeft = Offset(rightPx - handleSize + o, bottomPx - o), size = androidx.compose.ui.geometry.Size(handleSize, t))
                                        drawRect(color = Color.Yellow, topLeft = Offset(rightPx - o, bottomPx - handleSize + o), size = androidx.compose.ui.geometry.Size(t, handleSize))
                                    } else {
                                        drawRect(
                                            color = Color.Green,
                                            topLeft = Offset(leftPx, topPx),
                                            size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                            style = stroke
                                        )
                                    }
                                }

                                if (isCroppedReady) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(Color.Green, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("틀 고정됨", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            // 한 줄 동작: 확정 전 [틀 조정 ▾][자르기 확정] · 확정 뒤 [틀 다시 조정][틀 안 글자 읽기]. 글꼴이 크면 줄을 바꾼다.
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isCroppedReady) {
                                    TextButton(
                                        onClick = { showCropAdjust = !showCropAdjust },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("crop_adjust_toggle")
                                            .semantics { stateDescription = if (showCropAdjust) "펼침" else "접힘" }
                                    ) {
                                        Text("틀 조정")
                                        Icon(if (showCropAdjust) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.clearOcrError()
                                            isCroppedReady = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("crop_confirm_button")
                                    ) {
                                        Text("자르기 확정", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    TextButton(
                                        onClick = { isCroppedReady = false },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("crop_readjust_button")
                                    ) {
                                        Text("틀 다시 조정")
                                    }
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
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("ocr_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("틀 안 글자 읽기", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 틀 조정(접힘이 기본 · 기본 틀 = 사진 전체). 확정 뒤에는 틀이 고정되어 닫는다.
                        if (!isCroppedReady && showCropAdjust) {
                            Column(modifier = Modifier.fillMaxWidth().testTag("crop_adjust_panel")) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CropEdgeSlider("좌측 끝", cropLeft, 0f..0.8f, { cropLeft = it.coerceAtMost(cropRight - 0.1f) }, Modifier.weight(1f))
                                    CropEdgeSlider("우측 끝", cropRight, 0.2f..1f, { cropRight = it.coerceAtLeast(cropLeft + 0.1f) }, Modifier.weight(1f))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CropEdgeSlider("상단 끝", cropTop, 0f..0.8f, { cropTop = it.coerceAtMost(cropBottom - 0.1f) }, Modifier.weight(1f))
                                    CropEdgeSlider("하단 끝", cropBottom, 0.2f..1f, { cropBottom = it.coerceAtLeast(cropTop + 0.1f) }, Modifier.weight(1f))
                                }
                            }
                        }

                        // 인식 실패 안내는 버튼 줄과 따로 그린다(인식 중에는 버튼 줄이 숨어도 다음 실패가 보이게).
                        (ocrState as? OcrState.Error)?.let { OcrErrorText(it.message) }
                    }
                }
            }

            // Stage 3: 밑줄 문장 편집창. 머리 오른쪽에 아이콘 동작(줄바꿈 해제 · 전체 지우기 · 더 보기).
            Column(modifier = Modifier.fillMaxWidth()) {
                var showEditorMenu by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "밑줄 문장",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconAction(
                        "줄바꿈 해제",
                        Icons.Default.WrapText,
                        enabled = extractedText.isNotEmpty(),
                        onClick = { replaceTextWithUndo(extractedText.replace("\n", " ").replace("\\s+".toRegex(), " ").trim(), "줄바꿈을 풀었어요") },
                        modifier = Modifier.testTag("unwrap_lines_button")
                    )
                    IconAction(
                        "전체 지우기",
                        Icons.Default.Clear,
                        enabled = extractedText.isNotEmpty(),
                        onClick = { replaceTextWithUndo("", "글을 모두 지웠어요") },
                        modifier = Modifier.testTag("clear_text_button")
                    )
                    Box {
                        IconAction("더 보기", Icons.Default.MoreVert, onClick = { showEditorMenu = true }, modifier = Modifier.testTag("editor_more_button"))
                        DropdownMenu(expanded = showEditorMenu, onDismissRequest = { showEditorMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("여백 자동 보정") },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                enabled = extractedText.isNotEmpty(),
                                onClick = {
                                    showEditorMenu = false
                                    extractedText = extractedText.trim()
                                    Toast.makeText(context, "여백 정돈이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("trim_text_button")
                            )
                            // 글로 공유: 공유 시트에 보낼 글을 그대로 넘긴다(이미지는 만들지 않는다).
                            DropdownMenuItem(
                                text = { Text("글로 공유하기") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                enabled = extractedText.isNotBlank(),
                                onClick = {
                                    showEditorMenu = false
                                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, diaryShareText(extractedText, currentBook.title, currentBook.author, pageStr))
                                    }
                                    context.startActivity(android.content.Intent.createChooser(share, "글로 공유하기"))
                                },
                                modifier = Modifier.testTag("share_text_button")
                            )
                        }
                    }
                }

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
                                    text = "읽어 온 글자가 이곳에 보이고, 밑줄 친 부분만 남게 고칠 수 있습니다.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            },
                            // 글 길이에 따라 6줄에서 12줄까지 늘어난다(전에는 115dp 고정 · 4줄). 12줄을 넘으면 칸 안에서 스크롤한다.
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diary_extracted_text_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            minLines = EXTRACTED_TEXT_MIN_LINES,
                            maxLines = EXTRACTED_TEXT_MAX_LINES
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "글자 수: ${extractedText.length}자 | 문장 수: ${if (extractedText.isEmpty()) 0 else extractedText.split(".", "?", "!").filter { it.isNotBlank() }.size}개",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
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
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                                    color = if (extractedText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // 밑줄 미리 선택: 고른 어절이 없으면 아무것도 그리지 않는다(기존 화면 그대로). 첫 값은 위 LaunchedEffect(preselect)가 편집창에 넣고, 칩을 고친 뒤에는 버튼을 눌렀을 때만 바뀐다.
                UnderlinePreselectPanel(
                    words = preselect,
                    chosen = preselectChosen,
                    onToggle = { i -> preselectChosen[i] = !preselectChosen[i] },
                    onApply = { preselect?.let { extractedText = preselectedText(it, preselectChosen) } },
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 원문 전체(접힘): 편집창 글이 마지막 인식의 전체 글과 다를 때(밑줄 어절로 시작 · 사용자가 고침). 펼쳐 읽거나 편집창을 전체 글로 되돌린다.
                // 미리 선택 값(ViewModel)에 묶지 않는다 — 프로세스 재생성 · 사진 회전으로 값이 비어도 원문은 남는다(ocrFullText는 rememberSaveable).
                if (ocrFullText.isNotEmpty() && extractedText != ocrFullText) {
                    OcrFullTextSection(
                        fullText = ocrFullText,
                        expanded = showOcrFullText,
                        onToggle = { showOcrFullText = !showOcrFullText },
                        onUseFullText = { replaceTextWithUndo(ocrFullText, "편집창을 원문 전체로 바꿨어요") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 정밀 분석(선택): 키 등록 + 사진 전송 동의가 있을 때만 노출. 사진마다 확인 다이얼로그를 거친다(ADR-002 Q3)
                val preciseTarget = activeImageUrl?.takeIf { !it.startsWith("http") }
                if (preciseAvailable && preciseTarget != null && isCroppedReady) {
                    OutlinedButton(
                        onClick = { showPreciseConfirm = true },
                        enabled = preciseState !is OcrState.Processing && ocrState !is OcrState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("precise_analysis_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (preciseState is OcrState.Processing) "Google Gemini 분석 중…" else "정밀 분석 (사진을 Google Gemini로 전송)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showPreciseConfirm && preciseTarget != null) {
                    AlertDialog(
                        onDismissRequest = { showPreciseConfirm = false },
                        title = { Text("사진을 Google Gemini로 보낼까요?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("선택한 영역의 페이지 사진이 본인 키로 Google Gemini에 전송됩니다. 요금은 키 소유자에게 청구되며, 결과는 확인 후에만 편집창에 반영됩니다.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPreciseConfirm = false
                                    viewModel.processPreciseAnalysis(
                                        imageUri = Uri.parse(preciseTarget),
                                        rotationDegrees = imageRotation,
                                        flipped = isFlipped,
                                        cropLeft = cropLeft,
                                        cropTop = cropTop,
                                        cropRight = cropRight,
                                        cropBottom = cropBottom
                                    )
                                },
                                modifier = Modifier.testTag("precise_analysis_confirm")
                            ) { Text("전송하고 분석") }
                        },
                        dismissButton = { TextButton(onClick = { showPreciseConfirm = false }) { Text("취소") } }
                    )
                }

                (preciseState as? OcrState.Success)?.let { result ->
                    AlertDialog(
                        onDismissRequest = { viewModel.resetPreciseOcrState() },
                        title = { Text("정밀 분석 결과", fontWeight = FontWeight.Bold) },
                        text = { Text(result.text) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    extractedText = result.text
                                    ocrFullText = "" // 정밀 결과로 바꾸면 온디바이스 원문을 «원문 전체»로 남기지 않는다(덜 정확한 판독이 원문처럼 보이지 않게)
                                    viewModel.clearOcrError()
                                    viewModel.resetPreciseOcrState()
                                },
                                modifier = Modifier.testTag("precise_analysis_apply")
                            ) { Text("이 결과로 바꾸기") }
                        },
                        dismissButton = { TextButton(onClick = { viewModel.resetPreciseOcrState() }) { Text("버리기") } }
                    )
                }
            }

             if (diaryId != null && photosLoaded) {
                DiaryPhotoEditSection(
                    models = photoDrafts.map { slot ->
                        when (slot) {
                            is PhotoInputSlot.Existing -> loadedPhotos.firstOrNull { it.photoId == slot.photoId }?.file
                            is PhotoInputSlot.New -> slot.source
                            is PhotoInputSlot.Replace -> slot.source
                        }
                    },
                    onPick = { replaceIndex, useCamera ->
                        photoReplaceIndex = replaceIndex
                        if (!useCamera) {
                            try {
                                photoGalleryLauncher.launch("image/*")
                            } catch (e: Exception) {
                                Toast.makeText(context, "갤러리 실행 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchPhotoCamera()
                        } else {
                            photoPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    onRemove = { index -> photoDrafts = PhotoDrafts.removeAt(photoDrafts, index) }
                )
            }

            // 메모(구역 제목은 앱 말투로 짧게 — «밑줄 문장»과 짝)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "메모",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "이 밑줄을 보고 떠올랐던 본인의 생각, 감정, 일상을 기록해두세요.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        }
    }
}

private val PhotoDraftsSaver = Saver<List<PhotoInputSlot>, ArrayList<String>>(
    save = { PhotoDrafts.encode(it) },
    restore = { PhotoDrafts.decode(it) }
)

/** 공유 시트로 보내는 글 전부. */
internal fun diaryShareText(text: String, title: String, author: String, page: String): String =
    "\"$text\"\n\n" + listOf("『$title』", author.trim(), if (page.isBlank()) "" else "${page}쪽").filter { it.isNotBlank() }.joinToString(" · ")

/** 글자 인식 실패 안내(모델 준비 중 · 인터넷 필요 등). 다시 누르면 새 인식이 시작되며 사라진다. */
@Composable
internal fun OcrErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), // 12sp 이하 0(REDESIGN §6)
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("ocr_error")
    )
}

/** 틀 안 전체 글(접힘). 밑줄 어절만으로 시작한 편집창을 원문과 대조하거나 전체 글로 되돌린다. */
@Composable
private fun OcrFullTextSection(fullText: String, expanded: Boolean, onToggle: () -> Unit, onUseFullText: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        TextButton(onClick = onToggle, modifier = Modifier.testTag("ocr_full_text_toggle")) {
            Text(if (expanded) "원문 전체 접기" else "원문 전체 보기 (${fullText.length}자)", style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) {
            Text(
                text = fullText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("ocr_full_text")
            )
            TextButton(onClick = onUseFullText, modifier = Modifier.testTag("ocr_full_text_apply")) {
                Text("편집창을 원문 전체로 바꾸기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 아이콘만 쓰는 동작(REDESIGN §4). 낭독 이름(contentDescription)과 길게 누르면 뜨는 툴팁이 같은 [label]이다.
 * 누르는 대상은 IconButton 기본 48dp. [checked]를 주면 켜고 끄는 버튼(IconToggleButton)이 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean? = null,
    iconSize: Dp = 24.dp,
    tint: Color? = null
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        val drawIcon: @Composable () -> Unit = {
            Icon(imageVector = icon, contentDescription = label, tint = tint ?: LocalContentColor.current, modifier = Modifier.size(iconSize))
        }
        if (checked == null) {
            IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp)) { drawIcon() }
        } else {
            // 켜고 끄는 동작(좌우 반전): 낭독기가 켜짐/꺼짐을 읽는다.
            // 켜짐 글자색 기본값(primary)은 검은 도구 줄 위에서 보이지 않는다 — 꺼짐과 같은 둘레 글자색을 쓰고 켜짐은 배경 원으로 보인다.
            IconToggleButton(
                checked = checked, onCheckedChange = { onClick() }, enabled = enabled, modifier = modifier.size(48.dp),
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current)
            ) { drawIcon() }
        }
    }
}

/** «이름표 + 값» 한 줄(REDESIGN §3): 48dp 이상(글꼴이 커지면 함께 커진다) · 이름표 14sp 보조색 · 폭 56dp 이상. */
@Composable
private fun EntryRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 56.dp).padding(end = 8.dp)
        )
        content()
    }
}

@Composable
private fun EntryValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

@Composable
private fun EntryDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** «틀 조정» 안의 끝 하나(이름표 13sp · 누르는 높이 48dp 이상). */
@Composable
private fun CropEdgeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(modifier = modifier) {
        Text("$label: ${(value * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.heightIn(min = 48.dp))
    }
}
