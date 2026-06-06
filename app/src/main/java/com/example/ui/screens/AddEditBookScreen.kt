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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    // Search dialog states
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultBook>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isNaverActive by remember { mutableStateOf(false) }

    LaunchedEffect(showSearchDialog) {
        if (showSearchDialog) {
            var configClientId = try {
                SecureKeyManager.getNaverClientId(context)
            } catch (e: Exception) {
                ""
            }
            if (configClientId.isBlank()) {
                configClientId = BuildConfig.NAVER_CLIENT_ID
            }

            var configClientSecret = try {
                SecureKeyManager.getNaverClientSecret(context)
            } catch (e: Exception) {
                ""
            }
            if (configClientSecret.isBlank()) {
                configClientSecret = BuildConfig.NAVER_CLIENT_SECRET
            }
            
            isNaverActive = configClientId.isNotBlank() && 
                    configClientSecret.isNotBlank() && 
                    configClientId != "MY_NAVER_CLIENT_ID" && 
                    configClientId != "NAVER_CLIENT_ID" &&
                    configClientId != "NAVER_CLIENT_ID_PLACEHOLDER" &&
                    configClientSecret != "MY_NAVER_CLIENT_SECRET" && 
                    configClientSecret != "NAVER_CLIENT_SECRET" &&
                    configClientSecret != "NAVER_CLIENT_SECRET_PLACEHOLDER"
        }
    }

    // Sync if editing
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
            }
        } else {
            // Pick first bookcase as default
            if (bookcases.isNotEmpty() && selectedBookcaseId == 0) {
                selectedBookcaseId = bookcases.first().id
            }
        }
    }

    // Function to perform optimized Korean books search using Naver Search API with Google Books fallback
    fun performBookSearch(query: String) {
        if (query.trim().isEmpty()) return
        isSearching = true
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                var configClientId = try {
                    SecureKeyManager.getNaverClientId(context)
                } catch (e: Exception) {
                    ""
                }
                if (configClientId.isBlank()) {
                    configClientId = BuildConfig.NAVER_CLIENT_ID
                }

                var configClientSecret = try {
                    SecureKeyManager.getNaverClientSecret(context)
                } catch (e: Exception) {
                    ""
                }
                if (configClientSecret.isBlank()) {
                    configClientSecret = BuildConfig.NAVER_CLIENT_SECRET
                }
                
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
                        val escapedQuery = URLEncoder.encode(query, "UTF-8")
                        val url = "https://openapi.naver.com/v1/search/book.json?query=$escapedQuery&display=10"
                        
                        val request = Request.Builder()
                            .url(url)
                            .addHeader("X-Naver-Client-Id", configClientId)
                            .addHeader("X-Naver-Client-Secret", configClientSecret)
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
                                    
                                    // Remove HTML tags (e.g. <b>...</b>)
                                    var cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "")
                                    var cleanAuthor = rawAuthor.replace(Regex("<[^>]*>"), "")

                                    // Remove redundant bracketed or parenthesized suffixes like (반양장본), [개정판]
                                    cleanTitle = cleanTitle
                                        .replace(Regex("\\s*[\\(\\[](반양장본|양장본|개정판|제\\d+판|Paperback|Hardcover|소설|단행본|Korean Edition|번역본)[\\)\\]]"), "")
                                        .trim()

                                    // Filter/Clean up author info
                                    cleanAuthor = cleanAuthor
                                        .replace(Regex("^(저자|지은이|글|그림|옮김)\\s*:\\s*"), "")
                                        .replace(Regex("\\s+(저|지음|글|그림|역)$"), "")
                                        .replace("^", ", ")
                                        .replace("|", ", ")
                                        .trim()

                                    val cover = item.optString("image", "")
                                    val isbn = item.optString("isbn", "")
                                    
                                    // Default page count to avoid doing sequential blocking HTTP request waterfalls (no longer querying Google Books for each item, making the search instantaneous)
                                    val pageCount = 250
                                    
                                    list.add(SearchResultBook(cleanTitle, cleanAuthor, pageCount, cover))
                                }
                            }
                            if (list.isNotEmpty()) {
                                return@withContext list
                            }
                        }
                    } catch (e: Exception) {
                        // Naver failed, automatic fallback to Google Books Search
                    }
                }
                
                // Fallback to Google book search API if Naver keys are missing or failed
                try {
                    val client = OkHttpClient()
                    val escapedQuery = URLEncoder.encode(query, "UTF-8")
                    // Removed &langRestrict=ko&country=KR parameters to avoid empty results from geographic restriction on cloud servers
                    val url = "https://www.googleapis.com/books/v1/volumes?q=$escapedQuery&maxResults=10"
                    val request = Request.Builder().url(url).build()
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
                        list
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            searchResults = results
            isSearching = false
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("온라인 도서 검색", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(390.dp)) {
                    // API Integration Status Indicator
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        color = (if (isNaverActive) Color(0xFF03C75A) else Color(0xFFFFB300)).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = (if (isNaverActive) Color(0xFF03C75A) else Color(0xFFFFB300)).copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isNaverActive) "🟢 네이버 검색 연동 활성화" else "🟡 구글 도서 검색 사용 중",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNaverActive) Color(0xFF028A3E) else Color(0xFFB37400)
                                ),
                                modifier = Modifier.testTag("search_api_status_indicator")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("search_book_input"),
                        placeholder = { Text("도서 제목 또는 저자 입력...") },
                        trailingIcon = {
                            IconButton(onClick = { performBookSearch(searchQuery) }) {
                                Icon(Icons.Default.Search, contentDescription = "검색")
                            }
                        },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "검색어 또는 결과를 탐색하세요.\n(데미안, 사피엔스, 돈의 속성 등)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchResults) { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            title = result.title
                                            author = result.author
                                            totalPagesStr = result.pageCount.toString()
                                            coverUrl = result.coverUrl
                                            showSearchDialog = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(result.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(width = 45.dp, height = 65.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            result.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            result.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            "${result.pageCount} 쪽 분량",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (bookId == null) "새 도서 등록" else "도서 정보 수정", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (bookId == null) {
                            viewModel.navigateTo(Screen.Dashboard)
                        } else {
                            viewModel.navigateTo(Screen.BookDetail(bookId))
                        }
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
            
            // Search Option Chip
            Button(
                onClick = { showSearchDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("online_search_trigger_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("온라인 도서 검색하여 자동 완성", fontWeight = FontWeight.Bold)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (coverUrl.isNotEmpty()) "위의 '온라인 도서 검색' 기능을 사용하여 연동한 최신 도서 표지 이미지가 적용되어 있습니다."
                               else "도서를 직접 입력하여 등록하시는 경우, 선택된 책장 분류에 맞춰 분위기 있는 명화 디자인 전용 책 표지가 기본적으로 자동 지정됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 16.sp
                    )
                }
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("도서 제목 *") },
                placeholder = { Text("읽고 계신 책 이름을 적어주세요") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("book_title_input"),
                shape = RoundedCornerShape(8.dp)
            )

            // Author Field
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("저자 / 작가 *") },
                placeholder = { Text("헤르만 헤세") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("book_author_input"),
                shape = RoundedCornerShape(8.dp)
            )

            // Total Pages & Current Page
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = totalPagesStr,
                    onValueChange = { totalPagesStr = it },
                    label = { Text("전체 페이지 수 *") },
                    placeholder = { Text("300") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("book_total_pages_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = currentPageStr,
                    onValueChange = { currentPageStr = it },
                    label = { Text("현재 읽은 페이지") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("book_current_page_input"),
                    shape = RoundedCornerShape(8.dp)
                )
            }

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
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("bookcase_selector"),
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
                        "READING" -> "읽고 있는 책 (" + currentPageStr + "쪽)"
                        "TO_READ" -> "다음에 읽을 책"
                        "COMPLETED" -> "다 읽은 책 (" + totalPagesStr + "쪽 완독)"
                        else -> "미정"
                    }
                    OutlinedTextField(
                        value = statusLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("현재 독서 상태") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("status_selector"),
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
                            text = { Text("다 읽은 책") },
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

            Spacer(modifier = Modifier.weight(1f))

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
                    if (currentPage < 0 || currentPage > totalPages) {
                        Toast.makeText(context, "현재 페이지는 0에서 전체 페이지 수 사이여야 합니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    viewModel.saveBook(
                        id = bookId,
                        title = title,
                        author = author,
                        totalPages = totalPages,
                        currentPage = currentPage,
                        coverUrl = activeCoverUrl,
                        bookcaseId = selectedBookcaseId,
                        status = selectedStatus
                    ) {
                        Toast.makeText(context, if (bookId == null) "새 책이 활성화 되었습니다." else "저장이 완료 되었습니다.", Toast.LENGTH_SHORT).show()
                        if (bookId == null) {
                            viewModel.navigateTo(Screen.Dashboard)
                        } else {
                            viewModel.navigateTo(Screen.BookDetail(bookId))
                        }
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

data class SearchResultBook(
    val title: String,
    val author: String,
    val pageCount: Int,
    val coverUrl: String
)
