#!/usr/bin/env bash
# 서명된 릴리스 번들(AAB)을 만들고, 번들 서명이 업로드 키와 같은지 대조한다. **사용자가 직접 실행한다**(에이전트는 실행하지 않는다).
#
#   bash scripts/sign-release.sh                      # 번들 만들기 + 대조
#   bash scripts/sign-release.sh --verify-only <aab>  # 이미 있는 번들의 서명만 대조
#
# - 비밀번호는 화면 입력으로만 받는다(인자 · 파일 · 셸 기록에 남지 않는다). gradle 한 번에만 넘기고 끝나면 지운다.
# - 업로드 키 파일: KEYSTORE_PATH(없으면 레포 루트 my-upload-key.jks) · 별칭: KEY_ALIAS(없으면 upload).
# - gradle: GRADLE(없으면 ~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle).
# - JDK: JAVA_HOME(없으면 Android Studio 동봉 JBR "C:/Program Files/Android/Android Studio/jbr"). keytool도 이 JDK의 것을 쓴다.
# - 셸에 KAKAO_REST_API_KEY가 export돼 있으면 멈춘다 — 그대로 빌드하면 셸 값이 .env보다 앞서 앱에 박힌다(app/build.gradle.kts의 ENV_KAKAO).
# - 구성 캐시를 끄고 빌드한다 — 켜 두면 서명 비밀번호가 .gradle/configuration-cache에 남는다.
# - main 가지 · 고친 파일 없음 · HEAD = origin/main 일 때만 빌드한다(워커가 가지를 오가는 트리에서 실행하므로).
#   고정 커밋으로 빌드하려면 EXPECT_HEAD=<전체 SHA>를 같이 준다.
# - 끝에 찍히는 지문 두 줄은 PR · REPORT · 메시지에 붙이지 않는다(«일치» 여부만 알린다).
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "${1:-}" != "--verify-only" ]; then
  [ "$(git branch --show-current)" = main ] || { echo "중단: main 가지가 아니다($(git branch --show-current))" >&2; exit 1; }
  [ -z "$(git status --porcelain)" ] || { echo "중단: 고친 파일이 있다(git status로 확인)" >&2; exit 1; }
  HEAD_SHA="$(git rev-parse HEAD)"
  WANT="${EXPECT_HEAD:-$(git rev-parse origin/main)}"
  [ "$HEAD_SHA" = "$WANT" ] || { echo "중단: HEAD $HEAD_SHA 가 기대 커밋 $WANT 와 다르다(git pull 또는 EXPECT_HEAD 확인)" >&2; exit 1; }
  echo "빌드 커밋: $HEAD_SHA (main · 깨끗한 트리)"
fi

KEYSTORE="${KEYSTORE_PATH:-$PWD/my-upload-key.jks}"
# 상대 경로면 레포 루트 기준 절대 경로로(gradle의 file()은 app/ 기준이라 같은 파일을 보게 한다).
case "$KEYSTORE" in /*|[A-Za-z]:*) ;; *) KEYSTORE="$PWD/$KEYSTORE" ;; esac
ALIAS="${KEY_ALIAS:-upload}"
AAB_DEFAULT="app/build/outputs/bundle/release/app-release.aab"

fail() { echo "중단: $*" >&2; exit 1; }

JBR_DEFAULT="/c/Program Files/Android/Android Studio/jbr"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$JBR_DEFAULT" ]; then export JAVA_HOME="$JBR_DEFAULT"; fi
[ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME이 없고 Android Studio JBR도 찾지 못했다(JAVA_HOME으로 지정)"
# Windows 꼴(C:\...)이면 POSIX 꼴로 — 그대로 PATH에 넣으면 «:»에서 갈라진다.
command -v cygpath >/dev/null && JAVA_HOME="$(cygpath -u "$JAVA_HOME")" && export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

[ -f "$KEYSTORE" ] || fail "업로드 키 파일이 없다: $KEYSTORE (KEYSTORE_PATH로 지정)"
command -v keytool >/dev/null || fail "keytool이 PATH에 없다(JDK bin을 PATH에 넣는다)"

IFS= read -rsp "키 저장소 비밀번호: " STORE_PASSWORD; echo
[ -n "$STORE_PASSWORD" ] || fail "비밀번호가 비었다"
export STORE_PASSWORD
trap 'unset STORE_PASSWORD KEY_PASSWORD' EXIT

# 업로드 키 인증서의 SHA-256 지문(비밀번호는 환경 변수 이름으로만 넘긴다 — 명령줄에 값이 보이지 않는다).
key_sha256() {
  keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env STORE_PASSWORD 2>/dev/null |
    sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1
}
# 번들 서명 인증서의 SHA-256 지문.
aab_sha256() {
  keytool -printcert -jarfile "$1" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1
}

verify() {
  local aab="$1"
  [ -f "$aab" ] || fail "번들이 없다: $aab"
  local k a
  k="$(key_sha256 || true)"; [ -n "$k" ] || fail "업로드 키 지문을 읽지 못했다(비밀번호 · 별칭 $ALIAS 확인)"
  a="$(aab_sha256 "$aab" || true)"; [ -n "$a" ] || fail "번들에 서명이 없다: $aab"
  echo "업로드 키 SHA-256 : $k"
  echo "번들 서명 SHA-256 : $a"
  [ "$k" = "$a" ] || fail "지문이 다르다 — 이 번들을 올리지 않는다"
  echo "일치 · $(wc -c < "$aab" | tr -d ' ') 바이트 · $aab"
}

if [ "${1:-}" = "--verify-only" ]; then
  verify "${2:-$AAB_DEFAULT}"
  exit 0
fi

[ -z "${KAKAO_REST_API_KEY:-}" ] || fail "셸에 KAKAO_REST_API_KEY가 export돼 있다 — unset KAKAO_REST_API_KEY 하고 다시"

# 빌드 전에 비밀번호 · 별칭부터 확인한다(틀리면 R8 빌드를 기다리지 않고 멈춘다).
[ -n "$(key_sha256 || true)" ] || fail "업로드 키를 열지 못했다(비밀번호 · 별칭 $ALIAS 확인)"

IFS= read -rsp "키 비밀번호(키 저장소와 같으면 Enter): " KEY_PASSWORD; echo
[ -n "$KEY_PASSWORD" ] || KEY_PASSWORD="$STORE_PASSWORD"
export KEY_PASSWORD

GRADLE="${GRADLE:-$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle 2>/dev/null | head -1)}"
[ -n "$GRADLE" ] || fail "gradle을 찾지 못했다(GRADLE로 지정)"

KEYSTORE_PATH="$KEYSTORE" "$GRADLE" :app:bundleRelease --no-daemon --no-configuration-cache
unset KEY_PASSWORD
verify "$AAB_DEFAULT"
echo "R8 가독화 파일은 번들 안에 함께 들어간다(Play가 자동으로 읽는다). 콘솔에 안 보이면 올린다: app/build/outputs/mapping/release/mapping.txt"
