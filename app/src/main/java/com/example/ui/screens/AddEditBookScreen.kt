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
import com.example.ui.viewmodel.Screen
import com.example.data.SecureKeyManager
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
    var isNaverActive by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Determine Naver search API credentials configuration
    LaunchedEffect(Unit) {
        val configClientId = try {
            SecureKeyManager.getNaverClientId(context)
        } catch (e: Exception) {
            ""
        }.ifBlank { BuildConfig.NAVER_CLIENT_ID }

        val configClientSecret = try {
            SecureKeyManager.getNaverClientSecret(context)
        } catch (e: Exception) {
            ""
        }.ifBlank { BuildConfig.NAVER_CLIENT_SECRET }

        isNaverActive = configClientId.isNotBlank() && 
                configClientSecret.isNotBlank() && 
                configClientId != "MY_NAVER_CLIENT_ID" && 
                configClientId != "NAVER_CLIENT_ID" &&
                configClientId != "NAVER_CLIENT_ID_PLACEHOLDER" &&
                configClientSecret != "MY_NAVER_CLIENT_SECRET" && 
                configClientSecret != "NAVER_CLIENT_SECRET" &&
                configClientSecret != "NAVER_CLIENT_SECRET_PLACEHOLDER"
    }

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
            val responseResult = withContext(Dispatchers.IO) {
                var configClientId = try {
                    SecureKeyManager.getNaverClientId(context)
                } catch (e: Exception) {
                    ""
                }.ifBlank { BuildConfig.NAVER_CLIENT_ID }

                var configClientSecret = try {
                    SecureKeyManager.getNaverClientSecret(context)
                } catch (e: Exception) {
                    ""
                }.ifBlank { BuildConfig.NAVER_CLIENT_SECRET }

                val hasNaverKeys = configClientId.isNotBlank() && 
                        configClientSecret.isNotBlank() && 
                        configClientId != "MY_NAVER_CLIENT_ID" && 
                        configClientId != "NAVER_CLIENT_ID" &&
                        configClientId != "NAVER_CLIENT_ID_PLACEHOLDER" &&
                        configClientSecret != "MY_NAVER_CLIENT_SECRET" && 
                        configClientSecret != "NAVER_CLIENT_SECRET" &&
                        configClientSecret != "NAVER_CLIENT_SECRET_PLACEHOLDER"

                if (hasNaverKeys) {
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
                            val items = JSONObject(body).optJSONArray("items")
                            val list = mutableListOf<SearchResultBook>()
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val rawTitle = item.optString("title", "알 수 없는 제목")
                                    val rawAuthor = item.optString("author", "지은이 미상")
                                    
                                    var cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "")
                                    var cleanAuthor = rawAuthor.replace(Regex("<[^>]*>"), "")

                                    cleanTitle = cleanTitle
                                        .replace(Regex("\\s*[\\(\\[](반양장본|양장본|개정판|제\\d+판|Paperback|Hardcover|소설|단행본|Korean Edition|번역본)[\\)\\]]"), "")
                                        .trim()

                                    cleanAuthor = cleanAuthor
                                        .replace(Regex("^(저자|지은이|글|그림|옮김)\\s*:\\s*"), "")
                                        .replace(Regex("\\s+(저|지음|글|그림|역)$"), "")
                                        .replace("^", ", ")
                                        .replace("|", ", ")
                                        .trim()

                                    val cover = item.optString("image", "")
                                    list.add(SearchResultBook(cleanTitle, cleanAuthor, 250, cover))
                                }
                            }
                            Pair<List<SearchResultBook>, String?>(list, null)
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
                            val items = JSONObject(body).optJSONArray("items")
                            val list = mutableListOf<SearchResultBook>()
                            if (items != null) {
                                        for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val volumeInfo = item.optJSONObject("volumeInfo") ?: continue
                                    val titleStr = volumeInfo.optString("title", "알 수 없는 제목")
                                    val authorsArray = volumeInfo.optJSONArray("authors")
                                    val authorStr = if (authorsArray != null && authorsArray.length() > 0) {
                                        authorsArray.getString(0)
                                    } else "지은이 미상"
                                    val pageCount = volumeInfo.optInt("pageCount", 250)
                                    val imageLinks = volumeInfo.optJSONObject("imageLinks")
                                    val thumb = imageLinks?.optString("thumbnail")?.replace("http://", "https://") 
                                        ?: imageLinks?.optString("smallThumbnail")?.replace("http://", "https://") ?: ""
                                    list.add(SearchResultBook(titleStr, authorStr, pageCount, thumb))
                                }
                            }
                            Pair<List<SearchResultBook>, String?>(list, null)
                        } else {
                            val googleErrDetail = if (response.code == 429) {
                                "공용 구글 도서 API 호출 한도가 초과되었습니다 (HTTP 429). 지속적인 고성능 도서 검색 서비스를 원하시면 본인의 고유 '네이버 검색 API Key'를 [설정] 화면에 등록하여 사용하시기 바랍니다."
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
                // API Status Indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = (if (isNaverActive) Color(0xFF03C75A) else Color(0xFFFFB300)).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = (if (isNaverActive) Color(0xFF03C75A) else Color(0xFFFFB300)).copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isNaverActive) "🟢 네이버 도서 검색 API 활성화" else "🟡 구글 글로벌 도서 검색 연동 중",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isNaverActive) Color(0xFF028A3E) else Color(0xFFB37400)
                            )
                        )
                    }
                }

                // Sleek Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_book_input"),
                    placeholder = { Text("도서 제목 또는 저자의 일부를 입력해주세요") },
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
                            // Case A: Query too short - Show guide and sample books
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
                                item {
                                    PopularSampleBooksList(
                                        onBookSelected = { bTitle, bAuthor, bPages, bCover ->
                                            title = bTitle
                                            author = bAuthor
                                            totalPagesStr = bPages.toString()
                                            coverUrl = bCover
                                            isSearchActive = false
                                        }
                                    )
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
                                                    text = "도서 검색 연결 안내",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (searchError != null) {
                                                    "공용 구글 API 호출 한도가 초과되었습니다.\n오류 정보: $searchError\n\n지속적인 사용을 원하시면 본인의 네이버 ID/Secret 키를 [설정]에 등록해 주세요. 현재는 아래 추천 도서를 클릭하여 바로 추가하시거나, 우측 상단 '직접 입력'을 통해 등록하실 수 있습니다."
                                                } else {
                                                    "검색어 '$searchQuery'에 해당하는 도서를 찾지 못했습니다.\n\n정확한 단어로 다시 검색하시거나, 아래의 인기 추천 도서를 원클릭 추가해 보세요. 직접 본인의 Naver API Key를 설정에 추가하시면 고정밀 네이버 도서 검색도 가능합니다."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { isSearchActive = false },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                                ) {
                                                    Text("직접 수동 등록", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                                Button(
                                                    onClick = { viewModel.navigateTo(Screen.Settings) },
                                                    modifier = Modifier.weight(1.2f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    Text("⚙️ API 설정하기", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    PopularSampleBooksList(
                                        onBookSelected = { bTitle, bAuthor, bPages, bCover ->
                                            title = bTitle
                                            author = bAuthor
                                            totalPagesStr = bPages.toString()
                                            coverUrl = bCover
                                            isSearchActive = false
                                        }
                                    )
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

data class SearchResultBook(
    val title: String,
    val author: String,
    val pageCount: Int,
    val coverUrl: String
)

data class PopularSampleBook(
    val title: String,
    val author: String,
    val pageCount: Int,
    val coverUrl: String,
    val description: String
)

@Composable
fun PopularSampleBooksList(
    onBookSelected: (title: String, author: String, pageCount: Int, coverUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val samplePopularBooks = listOf(
        PopularSampleBook(
            "사피엔스 (Sapiens)",
            "유발 하라리",
            630,
            "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&q=80&w=200",
            "인류 문명의 역사적 진화를 설명하는 고전적 베스트셀러"
        ),
        PopularSampleBook(
            "돈의 속성",
            "김승호",
            390,
            "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&q=80&w=200",
            "최상위 부자가 이야기하는 지혜와 도덕적 자산 관리"
        ),
        PopularSampleBook(
            "불편한 편의점",
            "김호연",
            268,
            "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&q=80&w=200",
            "청파동 편의점 점원과 손님 사이의 다정하고 따뜻한 위로"
        ),
        PopularSampleBook(
            "세이노의 가르침",
            "세이노",
            736,
            "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?auto=format&fit=crop&q=80&w=200",
            "피가 되고 살이 되는 실용적 지식과 자립에 대한 조언"
        ),
        PopularSampleBook(
            "아몬드",
            "손원평",
            264,
            "https://images.unsplash.com/photo-1476275466078-4007374efbbe?auto=format&fit=crop&q=80&w=200",
            "타인의 감정을 공감하지 못하는 소년의 성장과 희망"
        )
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "📚 추천 샘플 도서 (클릭 시 원클릭 정보 자동 입력)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        samplePopularBooks.forEach { book ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBookSelected(book.title, book.author, book.pageCount, book.coverUrl) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 66.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(book.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${book.author} | 전체 ${book.pageCount}쪽",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = book.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "선택 추가",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

