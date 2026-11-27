plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.woodpeckerai.readingdiary"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val kakaoRestApiKeyEnv = System.getenv("KAKAO_REST_API_KEY") ?: ""
    buildConfigField("String", "ENV_KAKAO_REST_API_KEY", "\"$kakaoRestApiKeyEnv\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 축소 · 난독화(2번째 빌드부터). mapping.txt는 번들에 자동 포함된다(콘솔에 안 보일 때만 app/build/outputs/mapping/release/에서 올린다).
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    // 측정 전용: release와 같은 R8 구성으로 **서명하지 않은** 번들을 만든다(키 · 비밀번호 없이 번들 크기와 R8 통과를 잰다).
    // 서명이 없어 Play에 올릴 수 없다. 올릴 번들은 사용자가 scripts/sign-release.sh로 만든다.
    create("releaseR8Check") {
      initWith(getByName("release"))
      signingConfig = null
      matchingFallbacks += listOf("release")
    }
    // 실행 점검 전용(추기 2 B-c): release와 같은 R8 구성(initWith release · 같은 proguardFiles)을 디버그 키로 서명해 에뮬레이터에 깐다.
    // 동작에 닿는 차이는 서명 키뿐이다(BuildConfig.BUILD_TYPE · 출력 폴더 이름만 다르고 읽는 코드 0 · debuggable = false 그대로 — R8을 디버그 모드로 돌리지 않는다). Play에 올리지 않는다.
    create("r8Debug") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debugConfig")
      matchingFallbacks += listOf("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  // 측정 빌드는 release와 같은 코드여야 한다(상자 덤프 빈 껍데기 = src/release).
  sourceSets {
    // AGP 9 내장 Kotlin은 java.srcDir로 더한 폴더를 Kotlin 컴파일에 넣지 않는다(11-27 사용자 측정: compileReleaseR8CheckKotlin이
    // debugtools를 못 찾음) — kotlin 소스 폴더로도 잇는다. release 변형은 기본 소스 세트 src/release를 그대로 쓰므로 이 줄과 무관하다.
    getByName("releaseR8Check").java.srcDir("src/release/java")
    getByName("releaseR8Check").kotlin.srcDir("src/release/java")
    getByName("r8Debug").java.srcDir("src/release/java")
    getByName("r8Debug").kotlin.srcDir("src/release/java")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Room 스키마 JSON 출력 위치(ADR-004 1단계 ③). 커밋해 두어야 훗날 Migration 작성·검증의 정본이 된다.
// 이 방식은 KSP 태스크의 '선언된 출력'이 아니라, 엔티티가 바뀌었는데 태스크가 UP-TO-DATE 로 스킵되면 정본이 낡을 수 있다.
// RoomSchemaExportTest.schemaDoesNotDriftWithinTheSameVersion 이 그 경우를 잡는다.
// 배선을 Gradle 에 맡기려면 Room Gradle 플러그인(androidx.room + room { schemaDirectory(...) })으로 바꾼다 — 플러그인 1개 추가 비용.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  // 유료 API 키는 앱 바이너리에 굽지 않는다(docs/adr/ADR-001). .env에 있어도 BuildConfig에 들어가지 않는다.
  ignoreList.add("GEMINI_API_KEY")
  // 네이버 책 검색 API는 종료됐다. .env에 옛 값이 남아 있어도 쓰지 않는 키를 APK에 굽지 않는다.
  ignoreList.add("NAVER_CLIENT_ID")
  ignoreList.add("NAVER_CLIENT_SECRET")
}

// 재도입 가능성이 있는 미사용 의존성만 주석으로 남기고, 그 외(특히 키 유출 위험이 있는 것)는 카탈로그까지 제거한다.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.security.crypto)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.play.services.mlkit.text.recognition.korean)
  implementation(libs.play.services.code.scanner)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  // 순수 JVM 단위 테스트에서 org.json을 쓰기 위해(android.jar 스텁은 "not mocked"로 throw)
  testImplementation(libs.json)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
