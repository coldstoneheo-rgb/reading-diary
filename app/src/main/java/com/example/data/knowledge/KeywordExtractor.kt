package com.example.data.knowledge

/**
 * 일기 문장에서 "함께 나온 단어" 후보를 뽑는 순수 함수(ADR-003 Q2).
 *
 * 형태소 분석기 없이 순수 Kotlin으로 처리하므로 정확도는 "그럴듯한 단어" 수준이다. 그래서 결과는 화면에서
 * "키워드"가 아니라 "함께 나온 단어"로 부르고, 항상 원문(구절 또는 메모)과 함께 보여 오탐이 사용자 눈에 걸러지게 한다.
 *
 * 규칙:
 * - 한글·영문·숫자·`_` 이외의 문자로 토큰을 나눈다(`#자존감_회복` 태그와 같은 단위).
 * - 조사·어미 접미를 긴 것부터 **한 번만** 떼되, 뗀 뒤 2글자 이상 남을 때만 뗀다.
 *   1글자 접미(은/는/이/가/을/를/의/에/와/과/도/만/로)는 과절단을 줄이기 위해 더 보수적으로 뗀다:
 *   토큰이 4글자 이상이거나, 3글자여도 명사 끝소리로 흔한 "이/가"가 아닐 때만("고양이"·"호랑이" 보호). "~주의"(자본주의)는 떼지 않는다.
 * - 뗀 뒤에도 접미 목록에 그대로 있는 토큰("했다", "하는")은 단어가 아니므로 버린다.
 * - 2글자 미만, 숫자만, 불용어는 버린다. 영문은 소문자로 통일한다.
 * - 같은 문장 안의 중복은 하나로 합친다(등장 순서 유지).
 * - `notes`의 `#태그`는 [tags]로 따로 뽑는다. 사용자가 직접 붙인 것이므로 접미 제거·불용어 필터를 거치지 않는다.
 *
 * 알려진 한계(ADR-003 회의록): 단음절 명사("새", "알", "돈")는 연결 단어가 되지 못하고, 활용형에 따라 같은 낱말이 다른 형태로 남을 수 있다.
 */
object KeywordExtractor {

    private val TOKEN_SPLIT = Regex("[^가-힣a-zA-Z0-9_]+")
    private val DIGITS_ONLY = Regex("^[0-9_]+$")
    private val HASHTAG = Regex("#([가-힣a-zA-Z0-9_]{1,20})")

    /** 2글자 이상 접미. 긴 것부터 시도한다. 목록 순서가 곧 우선순위다. */
    private val SUFFIXES: List<String> = listOf(
        "에서는", "에게는", "으로는", "이라는", "하면서", "했지만", "하지만", "이었다", "였다고",
        "에서", "으로", "에게", "까지", "부터", "처럼", "보다", "이다", "이며", "이고", "하고", "한다", "했다", "하다", "되다", "된다", "됐다",
        "하는", "되는", "하며", "해서", "하게", "하지", "하면", "이라", "에는", "라는", "라고", "들이", "들을", "들은", "들의", "들과", "들도",
        "들", "한"
    )

    /** 1글자 조사. 3글자 토큰에서는 "이/가"를 떼지 않는다(고양이, 호랑이, 어린이). */
    private val ONE_CHAR_PARTICLES: Set<Char> = setOf('은', '는', '이', '가', '을', '를', '의', '에', '와', '과', '도', '만', '로')
    private val NOUN_LIKE_ENDINGS: Set<Char> = setOf('이', '가')

    private val STOPWORDS: Set<String> = setOf(
        "그것", "이것", "저것", "우리", "사람", "때문", "하나", "그리고", "그러나", "하지만", "그래서", "또는",
        "자기", "무엇", "어떤", "모든", "있다", "없다", "한다", "했다", "된다", "되다", "같다", "위해", "대한", "통해",
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
        if (token.length < 2) return null
        if (DIGITS_ONLY.matches(token)) return null
        var word = token.lowercase()
        var stripped = false
        for (suffix in SUFFIXES) {
            if (word.length - suffix.length >= 2 && word.endsWith(suffix)) {
                word = word.removeSuffix(suffix)
                stripped = true
                break
            }
        }
        if (!stripped) {
            val last = word.last()
            val canStrip = last in ONE_CHAR_PARTICLES &&
                word.length >= 3 &&
                !word.endsWith("주의") &&
                (word.length >= 4 || last !in NOUN_LIKE_ENDINGS)
            if (canStrip) word = word.dropLast(1)
        }
        if (word.length < 2) return null
        if (word in STOPWORDS || word in SUFFIXES) return null
        return word
    }
}
