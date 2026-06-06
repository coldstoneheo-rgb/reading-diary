package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecureKeyManager
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ReadingViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var geminiKey by remember { mutableStateOf("") }
    var naverId by remember { mutableStateOf("") }
    var naverSecret by remember { mutableStateOf("") }

    var geminiVisible by remember { mutableStateOf(false) }
    var naverSecretVisible by remember { mutableStateOf(false) }

    // Read stored keys when loading settings
    LaunchedEffect(Unit) {
        geminiKey = SecureKeyManager.getGeminiApiKey(context)
        naverId = SecureKeyManager.getNaverClientId(context)
        naverSecret = SecureKeyManager.getNaverClientSecret(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시스템 및 API 설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        modifier = Modifier.testTag("settings_back_button")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header Card / Description
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "보안 및 개인정보",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AES-256 기기 로컬 암호화",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "입력하신 API 인증 키 정보는 외부 서버로 유출되지 않으며, 안드로이드 Jetpack 라이브러리를 통해 기기 내부에 암호화 저장소에 보관됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // SECTION 1: Gemini API Configuration
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini",
                            tint = Color(0xFF1A73E8)
                        )
                        Text(
                            text = "Gemini AI 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "독서 다이어리 내에서 필기한 밑줄 사진을 업로드할 때, 문장을 명확하게 해독 및 텍스트 자동 발췌(OCR)하기 위해 사용됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Gemini API Key Acquirement Guide
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💡 Gemini API Key 발급 방법",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "1. Google AI Studio (aistudio.google.com) 사이트에 접속해 구글 계정으로 로그인합니다.\n" +
                                        "2. 좌측 상단 또는 화면 중앙의 'Get API key' 버튼을 누릅니다.\n" +
                                        "3. 'Create API key'를 클릭하여 새 키를 발급받은 후 복사하여 아래에 붙여넣어 주세요.\n" +
                                        "※ 개인 API 키를 등록하면 높은 속도와 개인만의 독립된 한도로 더욱 원활하게 이용할 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AI Studio에서 발급받은 API Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gemini_key_input"),
                        visualTransformation = if (geminiVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { geminiVisible = !geminiVisible }) {
                                Icon(
                                    imageVector = if (geminiVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (geminiVisible) "비밀번호 보이기" else "비밀번호 숨기기"
                                )
                            }
                        }
                    )
                }
            }

            // SECTION 2: Naver Books API Configuration
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
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Naver Books",
                            tint = Color(0xFF03C75A)
                        )
                        Text(
                            text = "네이버 도서 검색 API 설정 (선택 사항)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "이 설정은 선택 사항(Optional)입니다. 기본적으로 앱 내부에 개발자용 API 키가 기본 내장되어 있어, 일반 사용자가 번거롭게 네이버 오픈 API 사이트에 가입하여 고유 키를 발급받으실 필요가 전혀 없습니다.\n\n다만 개발자용 기본 API가 일일 호출 제한 한도에 다다랐거나, 이용자 본인의 독립된 네이버 API 한도를 전용으로 운용하고자 할 경우에만 정보를 입력해 주시면 됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = naverId,
                        onValueChange = { naverId = it },
                        label = { Text("Naver Client ID") },
                        placeholder = { Text("네이버 개발자 센터에서 획득한 Client ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("naver_id_input")
                    )

                    OutlinedTextField(
                        value = naverSecret,
                        onValueChange = { naverSecret = it },
                        label = { Text("Naver Client Secret") },
                        placeholder = { Text("네이버 개발자 센터에서 획득한 Client Secret") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("naver_secret_input"),
                        visualTransformation = if (naverSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { naverSecretVisible = !naverSecretVisible }) {
                                Icon(
                                    imageVector = if (naverSecretVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (naverSecretVisible) "비밀번호 보이기" else "비밀번호 숨기기"
                                )
                            }
                        }
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete / Clear local keys
                OutlinedButton(
                    onClick = {
                        SecureKeyManager.saveGeminiApiKey(context, "")
                        SecureKeyManager.saveNaverClientId(context, "")
                        SecureKeyManager.saveNaverClientSecret(context, "")
                        geminiKey = ""
                        naverId = ""
                        naverSecret = ""
                        Toast.makeText(context, "모든 개인 API Key정보가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).testTag("settings_reset_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "초기화")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("전체 지우기", fontWeight = FontWeight.Bold)
                }

                // Save locally encrypted keys
                Button(
                    onClick = {
                        SecureKeyManager.saveGeminiApiKey(context, geminiKey.trim())
                        SecureKeyManager.saveNaverClientId(context, naverId.trim())
                        SecureKeyManager.saveNaverClientSecret(context, naverSecret.trim())
                        Toast.makeText(context, "API Key 정보가 암호화되어 안전하게 저장되었습니다!", Toast.LENGTH_SHORT).show()
                        viewModel.navigateTo(Screen.Dashboard)
                    },
                    modifier = Modifier.weight(1f).testTag("settings_save_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "저장")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("설정 저장", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
