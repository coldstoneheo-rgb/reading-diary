package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.data.Bookcase
import com.example.ui.viewmodel.ReadingViewModel
import com.example.data.SecureKeyManager
import com.example.data.api.BookSearchParsers
import com.example.data.api.SearchResultBook
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBookScreen(
    viewModel: ReadingViewModel,
    bookId: Int? = null,
    startWithSearch: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val bookcases by viewModel.bookcases.collectAsState()
    val books by viewModel.books.collectAsState()

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var totalPagesStr by remember { mutableStateOf("") }
    var currentPageStr by remember { mutableStateOf("0") }
    var coverUrl by remember { mutableStateOf("") }
    var selectedBookcaseId by remember { mutableStateOf(0) }
    var selectedStatus by remember { mutableStateOf("TO_READ") } // "READING", "TO_READ", "COMPLETED"

    val activeCoverUrl = if (coverUrl.isNotBlank()) coverUrl else {
        val bookcaseName = bookcases.find { it.id == selectedBookcaseId }?.name ?: ""
        when {
            bookcaseName.contains("소설") -> "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400"
            bookcaseName.contains("재테크") -> "https://images.unsplash.com/photo-1592492159418-09f31333cca8?auto=format&fit=crop&q=80&w=400"
            bookcaseName.contains("자기계발") -> "https://images.unsplash.com/photo-1610116306796-6fea9f4fae38?auto=format&fit=crop&q=80&w=400"
            bookcaseName.contains("인문") -> "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=400"
            else -> {
                val defaultCovers = listOf(
                    "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&q=80&w=400",
                    "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=400",
                    "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=400",
                    "https://images.unsplash.com/photo-1592492159418-09f31333cca8?auto=format&fit=crop&q=80&w=400",
                    "https://images.unsplash.com/photo-1610116306796-6fea9f4fae38?auto=format&fit=crop&q=80&w=400"
                )
                val index = Math.abs(title.hashCode()) % defaultCovers.size
                defaultCovers[index]
            }
        }
    }

    var bookcaseExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // Inline search states
    var isSearchActive by remember { mutableStateOf(bookId == null && startWithSearch) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultBook>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    /** 네이버 키가 있으면 네이버로, 없으면 공용 Google 도서로 검색한다. 안내 카드가 이 값으로 원인을 구분한다. */
    var searchedWithNaver by remember { mutableStateOf<Boolean?>(null) }

    // Sync state if editing
    LaunchedEffect(bookId, books, bookcases) {
        if (bookId != null) {
            val book = books.find { it.id == bookId }
            book?.let {
                title = it.title
                author = it.author
                totalPagesStr = it.totalPages.toString()
                currentPageStr = it.currentPage.toString()
                coverUrl = it.coverUrl
                selectedBookcaseId = it.bookcaseId
                selectedStatus = it.status
                isSearchActive = false
            }
        } else {
            // Default first bookcase
            if (bookcases.isNotEmpty() && selectedBookcaseId == 0) {
                selectedBookcaseId = bookcases.first().id
            }
        }
    }

    // Live auto-debounced search on searchQuery changes
    LaunchedEffect(searchQuery) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.length >= 2) {
            delay(400) // 400ms debounce
            isSearching = true
            val naverCredentials = withContext(Dispatchers.IO) { resolveNaverCredentials(context) }
            val responseResult = withContext(Dispatchers.IO) {
                if (naverCredentials != null) {
                    val (configClientId, configClientSecret) = naverCredentials
                    try {
                        val client = OkHttpClient()
                        val escapedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
                        val url = "https://openapi.naver.com/v1/search/book.json?query=$escapedQuery&display=10"
                        
                        val request = Request.Builder()
                            .url(url)
                            .addHeader("X-Naver-Client-Id", configClientId)
                            .addHeader("X-Naver-Client-Secret", configClientSecret)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()
                            
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            Pair<List<SearchResultBook>, String?>(BookSearchParsers.parseNaverBooks(body), null)
                        } else {
                            val errMsg = when (response.code) {
                                401 -> "네이버 검색 API 인증에 실패했습니다. (HTTP 401 Unauthorized - 입력하신 Naver Client ID 또는 Client Secret Key 값을 다시 한 번 학인해 주세요.)"
                                403 -> "네이버 검색 API 호출 권한이 없습니다. (HTTP 403 Forbidden - 네이버 개발자 센터 내 애플리케이션 서비스 API 설정에 '검색(도서)' 서비스가 활성화되어 있는지 확인해 주세요.)"
                                429 -> "네이버 검색 API 일일 호출 제한 한도를 초과했습니다. (HTTP 429 Too Many Requests - 일일 기본 25,000건 무료 한도가 초과되었습니다.)"
                                else -> "네이버 API 호출에 실패했습니다. (HTTP 오류 코드: ${response.code})"
                            }
                            Pair<List<SearchResultBook>, String?>(emptyList(), errMsg)
                        }
                    } catch (e: Exception) {
                        Pair<List<SearchResultBook>, String?>(emptyList(), "네이버 연결 네트워크 도중 오류가 발생했습니다: ${e.message}")
                    }
                } else {
                    try {
                        val client = OkHttpClient()
                        val escapedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
                        val url = "https://www.googleapis.com/books/v1/volumes?q=$escapedQuery&maxResults=10"
                        val request = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            Pair<List<SearchResultBook>, String?>(BookSearchParsers.parseGoogleBooks(body), null)
                        } else {
                            val googleErrDetail = if (response.code == 429) {
                                "공용 Google 도서 검색의 호출 한도를 넘었습니다 (HTTP 429). 잠시 후 다시 시도하거나 직접 입력해 등록해 주세요."
                            } else {
                                "Google Books API Error (HTTP ${response.code})."
                            }
                            Pair<List<SearchResultBook>, String?>(emptyList(), googleErrDetail)
                        }
                    } catch (e: Exception) {
                        Pair<List<SearchResultBook>, String?>(emptyList(), "네트워크에 연결할 수 없거나 요청 처리 중 오류가 발생했습니다: ${e.localizedMessage}")
                    }
                }
            }
            searchedWithNaver = naverCredentials != null
            searchResults = responseResult.first
            searchError = responseResult.second
            isSearching = false
        } else {
            searchResults = emptyList()
            searchError = null
            isSearching = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (bookId != null) "도서 정보 수정" else if (isSearchActive) "도서 검색하여 추가기" else "새 도서 정보 입력",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (isSearchActive) {
            // Inline Search UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "📚 실시간 도서 데이터베이스 검색",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Sleek Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_book_input"),
                    placeholder = { 
                        Text(
                            text = "도서 제목 또는 저자", 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        ) 
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Skip online search and go to direct manual input button
                TextButton(
                    onClick = { isSearchActive = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("검색 없이 직접 입력해서 등록하기 ➔", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }

                // Results list or Loading/Empty States
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Case A: Query too short - Show guide
                            if (searchQuery.trim().length < 2) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "책 제목이나 지은이의 두 글자 이상 입력해 주세요\n실시간으로 온라인 데이터베이스에서 도서를 찾아옵니다.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            lineHeight = 18.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                            // Case B: There is an error (e.g. Quota exceed) or search results empty
                            else if (searchError != null || searchResults.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "서버 검색 제한 알림",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (searchError != null) "도서 검색에 실패했습니다" else "검색 결과가 없습니다",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = searchGuidanceText(
                                                    query = searchQuery,
                                                    error = searchError,
                                                    searchedWithNaver = searchedWithNaver
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedButton(
                                                onClick = { isSearchActive = false },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("search_manual_entry_button"),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                            ) {
                                                Text("직접 입력해서 등록", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }
                            }
                            // Case C: Success display list
                            else {
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                title = result.title
                                                author = result.author
                                                totalPagesStr = result.pageCount.toString()
                                                coverUrl = result.coverUrl
                                                isSearchActive = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Book Image Cover with round edge and subtle border
                                        Box(
                                            modifier = Modifier
                                                .size(width = 54.dp, height = 78.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        ) {
                                            if (result.coverUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(result.coverUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "SearchResult Cover",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Book,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Right: Details with Highlighted characters in Title
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = getHighlightedText(
                                                    text = result.title,
                                                    query = searchQuery.trim(),
                                                    highlightColor = MaterialTheme.colorScheme.primary
                                                ),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "저자 / 작가: ${result.author}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${result.pageCount} 쪽 분량",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Chevron navigation-like arrow
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "선택",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Confirmation form & direct input UI (no current page input!)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If books was prefilled from online search, provide banner & undo/reset search action
                if (bookId == null && coverUrl.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "온라인 도서 자동 연동 완료",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = { 
                                title = ""
                                author = ""
                                totalPagesStr = ""
                                coverUrl = ""
                                isSearchActive = true 
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "다른 도서 검색 ➔", 
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Cover Preview & Info Description Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 115.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeCoverUrl.startsWith("http")) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(activeCoverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Cover preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "도서 대표 표지",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (coverUrl.isNotEmpty()) "선택하신 도서의 공식 이미지가 연동되었습니다."
                                   else "도서를 직접 입력해 등록하시는 경우, 책장 분류에 맞는 분위기 있는 명화 디자인 도서 커버가 자동 매칭됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Title Input Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("도서 제목 *") },
                    placeholder = { Text("도서 이름을 적어주세요") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_title_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Author Input Field
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("저자 / 작가 *") },
                    placeholder = { Text("지은이를 입력해주세요") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_author_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Total Page Count (No matching "현재 페이지 입력", as requested deleted!)
                OutlinedTextField(
                    value = totalPagesStr,
                    onValueChange = { totalPagesStr = it },
                    label = { Text("전체 페이지 수 *") },
                    placeholder = { Text("전체 쪽 수 입력 (예 : 300)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_total_pages_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Dropdown bookcases selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = bookcaseExpanded,
                        onExpandedChange = { bookcaseExpanded = !bookcaseExpanded }
                    ) {
                        val activeBookcaseName = bookcases.find { it.id == selectedBookcaseId }?.name ?: "기본 책장"
                        OutlinedTextField(
                            value = activeBookcaseName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("책장 분류 선택") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookcaseExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("bookcase_selector"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = bookcaseExpanded,
                            onDismissRequest = { bookcaseExpanded = false }
                        ) {
                            bookcases.forEach { bookcase ->
                                DropdownMenuItem(
                                    text = { Text(bookcase.name) },
                                    onClick = {
                                        selectedBookcaseId = bookcase.id
                                        bookcaseExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Dropdown statuses selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded }
                    ) {
                        val statusLabel = when (selectedStatus) {
                            "READING" -> "읽고 있는 책 (상세 화면에서 조절)"
                            "TO_READ" -> "다음에 읽을 책"
                            "COMPLETED" -> "다 읽은 책 (완독)"
                            else -> "미정"
                        }
                        OutlinedTextField(
                            value = statusLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("현재 독서 상태") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("status_selector"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("읽고 있는 책") },
                                onClick = {
                                    selectedStatus = "READING"
                                    statusExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("다음에 읽을 책") },
                                onClick = {
                                    selectedStatus = "TO_READ"
                                    currentPageStr = "0"
                                    statusExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("다 읽은 책 (완독)") },
                                onClick = {
                                    selectedStatus = "COMPLETED"
                                    if (totalPagesStr.isNotEmpty()) {
                                        currentPageStr = totalPagesStr
                                    }
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Confirm Submit Button
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "도서 제목을 반드시 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (author.isBlank()) {
                            Toast.makeText(context, "도서 저자를 반드시 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val totalPages = totalPagesStr.toIntOrNull() ?: 0
                        if (totalPages <= 0) {
                            Toast.makeText(context, "올바른 전체 페이지 수를 기재해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val currentPage = currentPageStr.toIntOrNull() ?: 0

                        viewModel.saveBook(
                            id = bookId,
                            title = title,
                            author = author,
                            totalPages = totalPages,
                            currentPage = if (selectedStatus == "COMPLETED") totalPages else currentPage,
                            coverUrl = activeCoverUrl,
                            bookcaseId = selectedBookcaseId,
                            status = selectedStatus
                        ) {
                            Toast.makeText(context, if (bookId == null) "새 책이 활성화 되었습니다." else "저장이 완료 되었습니다.", Toast.LENGTH_SHORT).show()
                            viewModel.navigateBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_book_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bookId == null) "등록 완료" else "수정사항 저장 완료",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// Highlights matching substring of a search result dynamically
@Composable
fun getHighlightedText(text: String, query: String, highlightColor: Color): AnnotatedString {
    return buildAnnotatedString {
        if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
            append(text)
        } else {
            var startIndex = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            while (startIndex < text.length) {
                val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    append(text.substring(startIndex))
                    break
                } else {
                    if (matchIndex > startIndex) {
                        append(text.substring(startIndex, matchIndex))
                    }
                    withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                        append(text.substring(matchIndex, matchIndex + query.length))
                    }
                    startIndex = matchIndex + query.length
                }
            }
        }
    }
}




/**
 * 네이버 검색 키. 출처 우선순위(CLAUDE.md): ① 기기 암호화 저장소 → ② 빌드 셸 환경변수 → ③ .env.
 * 없으면 null — 호출자는 공용 Google 도서 검색으로 간다. 값은 로그에 남기지 않는다. 판정은 [pickNaverCredentials]가 한다.
 */
internal fun resolveNaverCredentials(context: android.content.Context): Pair<String, String>? {
    val id = runCatching { SecureKeyManager.getNaverClientId(context) }.getOrDefault("")
        .ifBlank { BuildConfig.ENV_NAVER_CLIENT_ID }
        .ifBlank { BuildConfig.NAVER_CLIENT_ID }
    val secret = runCatching { SecureKeyManager.getNaverClientSecret(context) }.getOrDefault("")
        .ifBlank { BuildConfig.ENV_NAVER_CLIENT_SECRET }
        .ifBlank { BuildConfig.NAVER_CLIENT_SECRET }
    return pickNaverCredentials(id, secret)
}

private val NAVER_PLACEHOLDERS = setOf(
    "MY_NAVER_CLIENT_ID", "NAVER_CLIENT_ID", "NAVER_CLIENT_ID_PLACEHOLDER",
    "MY_NAVER_CLIENT_SECRET", "NAVER_CLIENT_SECRET", "NAVER_CLIENT_SECRET_PLACEHOLDER"
)

/**
 * 순수 함수: 원시 문자열 둘을 정리해 (ID, Secret)으로 만든다. 둘 다 있어야 하고 공백·자리표시자(양쪽 목록 합집합)는 없는 것으로 친다.
 * 예전 버전이 ID/Secret을 바꿔 저장한 기기 보정: 네이버 Client ID(20자)가 Secret(10자)보다 길다.
 */
internal fun pickNaverCredentials(idRaw: String, secretRaw: String): Pair<String, String>? {
    fun clean(v: String) = v.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    var id = clean(idRaw)
    var secret = clean(secretRaw)
    if (id.isBlank() || secret.isBlank() || id in NAVER_PLACEHOLDERS || secret in NAVER_PLACEHOLDERS) return null
    if (id.length < secret.length) { val t = id; id = secret; secret = t }
    return id to secret
}

/**
 * 검색 실패·결과 없음 안내. 원인을 사실대로 말하고, 존재하지 않는 키 입력 화면으로 보내지 않는다(ADR-003 실행 7, 오너 결정: 네이버 키 UI는 만들지 않음).
 * @param searchedWithNaver null이면 아직 검색 전(빈 결과 문구만 쓴다).
 */
internal fun searchGuidanceText(query: String, error: String?, searchedWithNaver: Boolean?): String {
    if (error == null) {
        return "검색어 '${query.trim()}'에 맞는 책을 찾지 못했습니다.\n다른 단어로 다시 검색하거나 직접 입력해 등록해 주세요."
    }
    val route = when (searchedWithNaver) {
        true -> "네이버 도서 검색"
        false -> "공용 Google 도서 검색(이 앱에는 네이버 검색 키가 등록되어 있지 않습니다)"
        null -> "온라인 검색"
    }
    return "$route 중 오류가 났습니다.\n$error\n\n잠시 후 다시 시도하거나 직접 입력해 등록해 주세요."
}
