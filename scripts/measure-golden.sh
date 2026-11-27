#!/usr/bin/env bash
# 밑줄 검출 기준 측정 한 번에: 기준 271 · 사용자 사진(틀 없음) · 사용자 사진(사용자가 실제로 자른 틀) · 테스터 견본.
# 자료는 레포 밖에 있다(PUBLIC 레포 — 경로·본문을 레포에 두지 않는다). 첫 인자 = 자료 폴더들의 부모 폴더.
#   bash scripts/measure-golden.sh '<자료 부모 폴더>'          # 측정
#   bash scripts/measure-golden.sh '<자료 부모 폴더>' --check  # 폴더 · gradle만 확인(측정 0)
# 결과: <각 폴더>/_results/measure.md (첫 줄 = head SHA · 실행 시각). 종료코드 0 = 측정이 돌았다(수치 합격 판정은 하지 않는다).
set -euo pipefail
ROOT="${1:?자료 부모 폴더를 첫 인자로}"
SETS=(golden golden-user-20261127 golden-user-crop-20261127 golden-tester-20261127)
cd "$(dirname "$0")/.."
GRADLE=$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle 2>/dev/null | head -1 || true)
[ -n "$GRADLE" ] || { echo "gradle 9.4.1 배포판 없음"; exit 2; }
DIRS=""
for s in "${SETS[@]}"; do
  [ -d "$ROOT/$s" ] || { echo "자료 폴더 없음: $s"; exit 2; }
  DIRS="${DIRS:+$DIRS;}$ROOT/$s"
done
if [ "${2:-}" = "--check" ]; then echo "확인 끝: 자료 ${#SETS[@]}묶음 · gradle 있음"; exit 0; fi
RD_GOLDEN_DIR="$DIRS" "$GRADLE" :app:testDebugUnitTest --tests com.example.golden.GoldenMeasureTest --rerun -q
HEAD_SHA=$(git rev-parse --short HEAD)
for s in "${SETS[@]}"; do
  echo "== $s"
  first=$(head -1 "$ROOT/$s/_results/measure.md")
  echo "$first"
  case "$first" in *"$HEAD_SHA"*) ;; *) echo "결과 첫 줄의 SHA가 지금 HEAD($HEAD_SHA)와 다르다 — 측정이 돌지 않았다"; exit 3;; esac
  grep -E '= 지금 기본값|제외 없음\(지금 앱\)' "$ROOT/$s/_results/measure.md" | head -3 || true
done
