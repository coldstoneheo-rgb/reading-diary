#!/usr/bin/env bash
# R8을 켠 릴리스 번들의 크기와 R8 통과를 잰다(서명 0 · `:app:bundleReleaseR8Check`만). 올릴 번들이 아니다.
#
#   bash C:/Users/colds/reading-diary/.claude/worktrees/r8-release/scripts/measure-r8.sh           # 재기
#   bash C:/Users/colds/reading-diary/.claude/worktrees/r8-release/scripts/measure-r8.sh --check   # 빌드 없이 전제만 점검
#   (#90 머지 뒤에는 C:/Users/colds/reading-diary/scripts/measure-r8.sh)
#
# - 작업 폴더는 이 스크립트가 든 레포(또는 워크트리)의 루트로 스스로 잡는다. 어디서 실행해도 된다.
# - 비밀번호 · 키 파일을 쓰지 않는다. 셸의 KAKAO_REST_API_KEY는 이 실행 안에서만 지운다(번들에 셸 값이 박히지 않게).
# - 끝에 찍는 것: BUILD 결과 · .aab 바이트 수 · 상한(10,400,000바이트) 아래인지 · missing_rules.txt 유무.
set -uo pipefail
stop() { echo "멈춤: $*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || stop "스크립트 폴더를 찾지 못함"
cd "$ROOT" || stop "레포 루트로 가지 못함: $ROOT"
[ -f settings.gradle.kts ] && [ -f app/build.gradle.kts ] || stop "레포 루트가 아님(settings.gradle.kts · app/build.gradle.kts 없음): $ROOT"
grep -q 'create("releaseR8Check")' app/build.gradle.kts || stop "이 트리에 releaseR8Check 빌드 타입이 없음(#90 가지 또는 #90이 든 main에서 실행)"

LIMIT=10400000
AAB="app/build/outputs/bundle/releaseR8Check/app-releaseR8Check.aab"
MISSING="app/build/outputs/mapping/releaseR8Check/missing_rules.txt"

# JDK: JAVA_HOME → Android Studio 동봉 JBR
JBR_DEFAULT="/c/Program Files/Android/Android Studio/jbr"
if [ -z "${JAVA_HOME:-}" ]; then [ -d "$JBR_DEFAULT" ] && export JAVA_HOME="$JBR_DEFAULT"; fi
[ -n "${JAVA_HOME:-}" ] || stop "JDK 없음: JAVA_HOME이 비었고 $JBR_DEFAULT 도 없음"
command -v cygpath >/dev/null && JAVA_HOME="$(cygpath -u "$JAVA_HOME")" && export JAVA_HOME
[ -x "$JAVA_HOME/bin/java" ] || [ -x "$JAVA_HOME/bin/java.exe" ] || stop "JDK 없음: $JAVA_HOME/bin/java"

# Android SDK: local.properties의 sdk.dir → ANDROID_HOME → 기본 설치 위치
SDK_DEFAULT="$HOME/AppData/Local/Android/Sdk"
if [ -f local.properties ] && grep -q '^sdk.dir=' local.properties; then
  SDK_FROM="local.properties"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
  SDK_FROM="ANDROID_HOME"
elif [ -d "$SDK_DEFAULT" ]; then
  export ANDROID_HOME="$SDK_DEFAULT"; SDK_FROM="기본 위치($SDK_DEFAULT)"
else
  stop "Android SDK 없음: local.properties의 sdk.dir · ANDROID_HOME · $SDK_DEFAULT 모두 없음"
fi

# gradle(레포에 래퍼 없음 · PATH에 없음): GRADLE → ~/.gradle 배포판
GRADLE="${GRADLE:-$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle 2>/dev/null | head -1)}"
[ -n "$GRADLE" ] && [ -x "$GRADLE" ] || stop "gradle 9.4.1 없음: ~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle (GRADLE로 지정 가능)"

# .env(카카오 책 검색 키)는 크기 · R8 통과에 영향이 없다. 없으면 알리고 계속한다(키 없는 번들 = 검색만 빈 키).
if [ -f .env ]; then ENV_NOTE=".env 있음"; else ENV_NOTE=".env 없음(카카오 키 없이 빌드 — 크기 · R8 측정에는 영향 없음)"; fi

echo "레포 루트 : $ROOT"
echo "가지·HEAD : $(git branch --show-current 2>/dev/null) $(git rev-parse --short HEAD 2>/dev/null)"
echo "JDK       : $JAVA_HOME"
echo "SDK       : $SDK_FROM"
echo "gradle    : $GRADLE"
echo "키 파일   : $ENV_NOTE"

if [ "${1:-}" = "--check" ]; then echo "점검 통과: 전제가 모두 있음(빌드는 하지 않았다)"; exit 0; fi

unset KAKAO_REST_API_KEY
rm -f "$AAB" "$MISSING"
"$GRADLE" :app:bundleReleaseR8Check --no-configuration-cache
CODE=$?

echo "----- 결과 -----"
if [ "$CODE" -eq 0 ]; then echo "BUILD: 성공"; else echo "BUILD: 실패(종료코드 $CODE)"; fi
if [ -f "$MISSING" ]; then echo "missing_rules.txt: 있음 → $ROOT/$MISSING (내용을 LC에 전달)"; else echo "missing_rules.txt: 없음"; fi
if [ -f "$AAB" ]; then
  BYTES=$(wc -c < "$AAB" | tr -d ' ')
  if [ "$BYTES" -le "$LIMIT" ]; then V="상한 $LIMIT 이하"; else V="상한 $LIMIT 초과"; fi
  echo "aab: $BYTES 바이트 · $V · $AAB"
else
  echo "aab: 없음"
fi
exit "$CODE"
