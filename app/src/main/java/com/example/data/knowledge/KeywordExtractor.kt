package com.example.data.knowledge

/**
 * 일기 문장에서 "함께 나온 단어" 후보를 뽑는 순수 함수(ADR-003 Q2).
 *
 * 형태소 분석기 없이 순수 Kotlin으로 처리하므로 정확도는 "그럴듯한 단어" 수준이다. 그래서 결과는 화면에서
 * "키워드"가 아니라 "함께 나온 단어"로 부르고, 항상 원문 구절과 함께 보여 오탐이 사용자 눈에 걸러지게 한다.
 *
 * 규칙:
 * - 한글·영문·숫자 이외의 문자로 토큰을 나눈다.
 * - 조사·어미 접미를 긴 것부터 **한 번만** 떼되, 뗀 뒤 2글자 이상 남을 때만 뗀다("종이"→"종" 같은 과절단 방지).
 * - 2글자 미만, 숫자만, 불용어는 버린다. 영문은 소문자로 통일한다.
 * - 같은 문장 안의 중복은 하나로 합친다(등장 순서 유지).
 * - `notes`의 `#태그`는 [tags]로 따로 뽑는다. 사용자가 직접 붙인 것이므로 접미 제거·불용어 필터를 거치지 않는다.
 */
object KeywordExtractor {

    private val TOKEN_SPLIT = Regex("[^가-힣a-zA-Z0-9]+")
    private val DIGITS_ONLY = Regex("^[0-9]+$")
    private val HASHTAG = Regex("#([가-힣a-zA-Z0-9_]{1,20})")

    /** 긴 것부터 시도한다. 목록 순서가 곧 우선순위다. */
    private val SUFFIXES: List<String> = listOf(
        "에서는", "에게는", "으로는", "이라는", "하면서", "했지만", "하지만",
        "에서", "으로", "에게", "까지", "부터", "처럼", "보다", "이다", "이며", "이고", "하고", "한다", "했다",
        "하는", "하며", "해서", "이라", "에는", "라는", "라고", "들이", "들을", "들은", "들의",
        "은", "는", "이", "가", "을", "를", "의", "에", "와", "과", "도", "만", "로", "다", "들"
    )

    private val STOPWORDS: Set<String> = setOf(
        "그것", "이것", "저것", "우리", "사람", "때문", "하나", "그리고", "그러나", "하지만", "그래서", "또는",
        "자기", "무엇", "어떤", "모든", "있다", "없다", "한다", "되다", "같다", "위해", "대한", "통해",
        "그런", "이런", "저런", "바로", "그냥", "정말", "매우", "너무", "아주", "다시", "또한", "역시",
        "이제", "지금", "오늘", "내가", "나는", "나의", "너의", "당신", "여기", "저기", "거기",
        "않는", "않고", "않다", "아니", "그때", "언제", "어디", "누구", "왜", "어떻게", "얼마나",
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
        for (suffix in SUFFIXES) {
            if (word.length - suffix.length >= 2 && word.endsWith(suffix)) {
                word = word.removeSuffix(suffix)
                break
            }
        }
        if (word.length < 2) return null
        if (word in STOPWORDS) return null
        return word
    }
}
