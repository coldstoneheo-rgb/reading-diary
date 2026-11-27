# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
# 2번째 빌드(R8 첫 켬): 테스터 크래시의 retrace에 줄 번호가 남게 한다.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ML Kit는 시작할 때 등록기(…Registrar)를 이름으로 찾아 인자 없는 생성자로 만든다(ComponentDiscovery · 리플렉션).
# R8 full mode는 그 생성자를 지운다 → «Could not instantiate …Registrar» 뒤 글자 인식은 오류, 바코드 스캐너는 조용히 안 열린다
# (2026-11-27 r8Debug 에뮬레이터 실측 · 디버그 빌드는 정상). 등록기 클래스와 생성자만 남긴다.
# ML Kit · Play 서비스 의존성을 올리면 이름 규칙 밖의 등록기가 생길 수 있다 — 올릴 때마다 r8Debug 실행 점검(인식 · 바코드)을 다시 한다.
-keep class com.google.mlkit.**Registrar { <init>(); }
