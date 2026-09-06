package com.example.data.knowledge

/**
 * 일기 문장에서 "함께 나온 단어" 후보를 뽑는 순수 함수(ADR-003 Q2, ADR-004 Q3).
 *
 * 형태소 분석기 없이 순수 Kotlin으로 처리한다. 사전은 상수라 APK 영향이 사실상 없고 전부 JVM 테스트로 고정된다.
 *
 * 처리 순서(순서가 규칙의 일부다):
 * 1. 앞뒤 `_`를 떼고(마크다운 강조 `_성장_`) 소문자로. 2글자 미만·숫자만은 버린다.
 * 2. **2글자 이상 조사**를 긴 것부터 한 번만 뗀다. 뗀 뒤 2글자 이상 남을 때만.
 * 3. **용언 활용형** 처리([stripVerbEnding]).
 * 4. **1글자 조사**를 뗀다(3글자 이상일 때만). 뗀 뒤 활용형이 드러날 수 있으므로 3번을 한 번 더 돌린다.
 *    `필요하다는` → `필요하다` → `필요`.
 * 5. 남은 것이 2글자 미만이거나 불용어면 버린다.
 *
 * ## 사전을 좁게 쓰는 이유
 * `결과`·`각도`·`정도`처럼 조사로 끝나 보이는 2글자 명사를 `endsWith`로 보호하면 **`생각도`·`정도로`·`여성의`가
 * 통째로 살아남아** 같은 낱말이 조사에 따라 갈라진다. 그 형태가 합성어(`연구결과`)보다 훨씬 흔하므로,
 * 보호는 [PROTECTED_NOUNS] **완전 일치**와 `~주의` 하나로만 한다.
 *
 * ## 알려진 한계(테스트로 고정되어 있다)
 * - 조사로 끝나 보이는 합성어는 잘린다: `연구결과`→`연구결`, `적정온도`→`적정온`, `국무회의`→`국무회`.
 * - 단음절 명사(`새`·`알`·`돈`)는 연결 단어가 되지 못한다.
 * - `다`로 끝나면 활용형으로 본다. 그래서 `인격체다`도 버려지고, **[PROTECTED_NOUNS]에 없는 `-다` 명사는
 *   잘리는 게 아니라 검색에서 통째로 사라진다**(사용자가 원인을 알 수 없는 유일한 실패라 사전을 우선 채웠다).
 * 그래서 결과는 화면에서 "키워드"가 아니라 **"함께 나온 단어"**로 부르고, 항상 원문과 함께 보여 오탐이 눈에 걸러지게 한다.
 */
object KeywordExtractor {

    private val TOKEN_SPLIT = Regex("[^가-힣a-zA-Z0-9_]+")
    private val DIGITS_ONLY = Regex("^[0-9_]+$")
    private val HASHTAG = Regex("#([가-힣a-zA-Z0-9_]{1,20})")

    /** 조사. 긴 것부터 시도한다(마지막 `들`만 1글자다 — `학생들`의 복수 접미사). */
    private val PARTICLES: List<String> = listOf(
        "에서는", "에게는", "으로는", "이라는", "에게서", "으로써", "으로서",
        "에서", "으로", "에게", "까지", "부터", "처럼", "보다", "만큼", "조차", "마저", "라도", "로써", "로서",
        "에는", "에도", "라는", "라고", "들이", "들을", "들은", "들의", "들과", "들도", "들에", "들"
    )

    /** 2글자 이상 용언 어미. 뗀 뒤 2글자 이상 남을 때만 뗀다. */
    private val VERB_ENDINGS: List<String> = listOf(
        "하면서", "했지만", "하지만", "이었다", "였다고", "하였다",
        "하다", "한다", "했다", "하는", "하고", "하며", "해서", "하게", "하지", "하면", "해도", "하여",
        "되다", "된다", "됐다", "되는", "되어", "되고", "되며", "되지",
        "이다", "이며", "이고", "였다", "이던", "이라",
        "는다", "었다", "았다", "겠다"
    )

    /**
     * 1글자 용언 어미. 2글자 어간을 남길 수 있을 때만 뗀다(`제한`·`권한`·`인하`는 보존).
     * `하`는 **3글자 토큰에서만** 뗀다(`생각하`→`생각`, `체제하`→`체제`). 4글자 이상에 적용하면 `가격인하`→`가격인`이 된다.
     */
    private val VERB_ENDINGS_ONE: List<String> = listOf("한", "하")
    private val ONLY_ON_THREE_CHARS: Set<String> = setOf("하")

    /** 1글자 조사. */
    private val ONE_CHAR_PARTICLES: Set<Char> =
        setOf('은', '는', '이', '가', '을', '를', '의', '에', '와', '과', '도', '만', '로')

    /**
     * 조사·어미로 끝나 보이지만 명사인 낱말. **완전 일치**로만 보호한다(`endsWith`가 아니다 — 위 KDoc 참조).
     * 2글자 낱말은 1글자 조사 규칙(3글자 이상)에 애초에 걸리지 않으므로, 여기 넣을 2글자는 `다`로 끝나 용언으로 오해되는 것뿐이다.
     */
    private val PROTECTED_NOUNS: Set<String> = setOf(
        // -다 로 끝나는 명사(용언 활용형으로 오해되어 **잘리는 게 아니라 통째로 사라진다**. 지명·고유명사가 특히 위험하다)
        "바다", "붓다", "판다", "소다", "사이다", "우간다", "아젠다",
        "캐나다", "르완다", "플로리다", "네바다", "버뮤다", "그라나다", "베다", "아난다",
        // -의 로 끝나는 3글자 명사(`~주의` 보호는 4글자 이상만 받으므로 여기서 따로 챙긴다)
        "부주의",
        // -이 로 끝나는 3글자 이상 명사
        "고양이", "호랑이", "원숭이", "어린이", "목걸이", "귀걸이", "손잡이", "미닫이", "여닫이",
        // -가 로 끝나는 3글자 이상 명사(사람)
        "전문가", "소설가", "음악가", "성악가", "예술가", "번역가", "정치가", "사업가", "자본가", "평론가", "건축가"
    )

    private val STOPWORDS: Set<String> = setOf(
        "그것", "이것", "저것", "우리", "사람", "때문", "하나", "그리고", "그러나", "하지만", "그래서", "또는",
        "자기", "무엇", "어떤", "모든", "위해", "대한", "통해",  // "있다"·"없다"류는 -다 규칙이 먼저 버린다
        "그런", "이런", "저런", "그렇게", "이렇게", "저렇게", "바로", "그냥", "정말", "매우", "너무", "아주", "다시", "또한", "역시",
        "이제", "지금", "오늘", "내가", "나는", "나의", "너의", "당신", "여기", "저기", "거기", "그때", "언제", "어디", "누구",
        "어떻게", "얼마나", "않는", "않고", "않다", "아니", "있는", "없는", "하는", "되는", "보다", "부터", "까지", "처럼",
        "the", "and", "for", "that", "this", "with", "from", "are", "was", "were", "not", "but", "you", "your"
    )

    /** 본문에서 단어 후보를 뽑는다. 등장 순서를 유지한 중복 없는 목록. */
    fun extract(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        for (raw in text.split(TOKEN_SPLIT)) {
            val stem = stem(raw.trim()) ?: continue
            seen.add(stem)
        }
        return seen.toList()
    }

    /** `notes` 안의 `#태그`. 등장 순서 유지, 중복 제거. 대소문자는 원문 그대로 둔다. */
    fun tags(notes: String): List<String> =
        HASHTAG.findAll(notes).map { it.groupValues[1] }.distinct().toList()

    /** 토큰 하나를 정규화한다. 버릴 토큰이면 null. */
    internal fun stem(token: String): String? {
        var word = token.trim('_').lowercase()
        if (word.length < 2) return null
        if (DIGITS_ONLY.matches(word)) return null

        // 2) 2글자 이상 조사
        for (particle in PARTICLES) {
            if (word.length - particle.length >= 2 && word.endsWith(particle)) {
                word = word.dropLast(particle.length)
                break
            }
        }

        // 3) 용언 활용형
        word = stripVerbEnding(word) ?: return null

        // 4) 1글자 조사 → 드러난 활용형을 한 번 더
        if (word.length >= 3 && word.last() in ONE_CHAR_PARTICLES && !isProtectedNoun(word)) {
            word = word.dropLast(1)
            word = stripVerbEnding(word) ?: return null
        }

        if (word.length < 2) return null
        if (word in STOPWORDS || word in PARTICLES || word in VERB_ENDINGS) return null
        return word
    }

    /**
     * `~주의`(-ism) 보호는 **4글자 이상에만** 준다. 3글자에 주면 `위주의`(위주+의)·`민주의`가 통째로 살아남아
     * `위주`와 갈라진다 — [PROTECTED_NOUNS]를 완전 일치로 좁힌 이유와 같은 실패다. 3글자 예외는 사전에 넣는다.
     */
    private fun isProtectedNoun(word: String): Boolean =
        word in PROTECTED_NOUNS || (word.length >= 4 && word.endsWith("주의"))

    /**
     * 용언 어미를 뗀다. 용언이 아니면 [word]를 그대로 돌려주고, **활용형인데 어간을 못 건지면 null**(토큰 폐기)이다.
     * `다`로 끝나는데 아는 어미가 없으면 폐기한다 — `간다`·`나온다`·`떠났다`의 어간을 억지로 남기면 그것이 가짜 연결을 만든다.
     */
    private fun stripVerbEnding(word: String): String? {
        if (isProtectedNoun(word)) return word
        for (ending in VERB_ENDINGS) {
            if (word.length - ending.length >= 2 && word.endsWith(ending)) {
                return word.dropLast(ending.length)
            }
        }
        if (word.endsWith("다")) return null
        for (ending in VERB_ENDINGS_ONE) {
            if (ending in ONLY_ON_THREE_CHARS && word.length != 3) continue
            if (word.length - ending.length >= 2 && word.endsWith(ending)) {
                return word.dropLast(ending.length)
            }
        }
        return word
    }
}
