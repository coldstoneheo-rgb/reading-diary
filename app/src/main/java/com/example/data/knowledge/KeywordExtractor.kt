package com.example.data.knowledge

/**
 * 일기 문장에서 "함께 나온 단어" 후보를 뽑는 순수 함수(ADR-003 Q2, ADR-004 Q3).
 *
 * 형태소 분석기 없이 순수 Kotlin으로 처리한다. 정확도를 길이 휴리스틱이 아니라 **작은 사전 세 개**로 올린다:
 * 명사 꼬리([NOUN_TAILS]), 용언 어미([VERB_ENDINGS]), 조사([PARTICLES]·[ONE_CHAR_PARTICLES]).
 * 사전은 상수라 APK 영향이 사실상 없고 전부 JVM 테스트로 고정된다.
 *
 * 처리 순서(순서가 규칙의 일부다):
 * 1. 앞뒤 `_`를 떼고(마크다운 강조 `_성장_`) 소문자로. 2글자 미만·숫자만은 버린다.
 * 2. **2글자 이상 조사**를 긴 것부터 한 번만 뗀다. 뗀 뒤 2글자 이상 남을 때만.
 * 3. **용언 활용형**이면 어미를 떼고, 어간이 2글자 미만이면 **버린다**.
 *    `간다`·`말했다`는 단어가 아니므로 원형을 남기지 않는다. `필요하다`→`필요`는 남긴다.
 * 4. **1글자 조사**를 뗀다. 단 [NOUN_TAILS]로 끝나면 떼지 않는다(`고양이`·`연구결과`·`자본주의` 보호).
 * 5. 남은 것이 2글자 미만이거나 불용어면 버린다.
 *
 * 알려진 한계: 단음절 명사(`새`·`알`·`돈`)는 연결 단어가 되지 못한다. 사전에 없는 명사 꼬리는 여전히 잘릴 수 있다.
 * 그래서 결과는 화면에서 "키워드"가 아니라 **"함께 나온 단어"**로 부르고, 항상 원문과 함께 보여 오탐이 눈에 걸러지게 한다.
 */
object KeywordExtractor {

    private val TOKEN_SPLIT = Regex("[^가-힣a-zA-Z0-9_]+")
    private val DIGITS_ONLY = Regex("^[0-9_]+$")
    private val HASHTAG = Regex("#([가-힣a-zA-Z0-9_]{1,20})")

    /** 2글자 이상 조사. 긴 것부터 시도한다. */
    private val PARTICLES: List<String> = listOf(
        "에서는", "에게는", "으로는", "이라는", "에서의", "에게서",
        "에서", "으로", "에게", "까지", "부터", "처럼", "보다", "만큼", "조차", "마저", "라도", "이나",
        "에는", "에도", "라는", "라고", "이라", "으로써", "로써", "로서",
        "들이", "들을", "들은", "들의", "들과", "들도", "들에", "들"
    )

    /**
     * 용언 활용 어미. 긴 것부터 시도한다. 마지막 `"다"`는 서술격 조사까지 받는다(`인격체다`→`인격체`).
     * 어미를 뗀 어간이 2글자 미만이면 그 토큰은 단어가 아니다.
     */
    private val VERB_ENDINGS: List<String> = listOf(
        "하면서", "했지만", "하지만", "이었다", "였다고", "하였다", "해야만",
        "하다", "한다", "했다", "하는", "하고", "하며", "해서", "하게", "하지", "하면", "해도", "하여",
        "되다", "된다", "됐다", "되는", "되어", "되고", "되며",
        "이다", "이며", "이고", "였다", "이던",
        "는다", "었다", "았다", "겠다", "런다", "린다", "긴다", "킨다",
        "한", "다"
    )

    /** 1글자 조사. [NOUN_TAILS] 보호를 통과한 뒤에만 뗀다. */
    private val ONE_CHAR_PARTICLES: Set<Char> =
        setOf('은', '는', '이', '가', '을', '를', '의', '에', '와', '과', '도', '만', '로')

    /**
     * 1글자 조사처럼 보이는 글자로 끝나지만 실제로는 명사인 꼬리. 이 꼬리로 끝나면 마지막 글자를 떼지 않는다.
     * 합성어의 꼬리로 검사하므로 `연구결과`는 `결과` 하나로 보호된다.
     */
    private val NOUN_TAILS: Set<String> = setOf(
        // -과
        "결과", "효과", "성과", "경과", "사과", "치과", "내과", "외과", "학과", "안과", "예과", "이비인후과",
        // -의
        "회의", "주의", "정의", "의의", "강의", "문의", "논의", "협의", "편의", "예의", "성의", "창의", "동의", "이의", "합의", "발의", "건의",
        // -도
        "온도", "제도", "태도", "각도", "속도", "정도", "밀도", "강도", "용도", "고도", "농도", "위도", "경도", "빈도", "척도", "인도", "지도", "보도", "복도", "수도", "기도", "의도", "궤도", "선도", "노도",
        // -이
        "아이", "종이", "나이", "사이", "차이", "길이", "넓이", "높이", "깊이", "놀이", "먹이", "굽이", "벌이", "풀이",
        "고양이", "호랑이", "원숭이", "어린이", "목걸이", "귀걸이", "손잡이",
        // -가
        "국가", "작가", "화가", "평가", "물가", "대가", "원가", "단가", "정가", "추가", "참가", "증가", "시가", "저가", "고가",
        "성악가", "음악가", "소설가", "전문가", "예술가", "번역가",
        // -을
        "마을", "가을", "겨울", "서울", "거울", "저울",
        // -로
        "통로", "경로", "진로", "도로", "회로", "미로", "활로", "항로", "선로", "수로", "재료", "자료", "원료", "연료",
        // -만
        "불만", "비만", "자만", "교만", "낭만", "충만", "원만",
        // -다 (용언 활용형으로 오해되는 명사)
        "바다"
    )

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
        var word = token.trim('_').lowercase()
        if (word.length < 2) return null
        if (DIGITS_ONLY.matches(word)) return null

        // 2) 2글자 이상 조사
        var strippedParticle = false
        for (particle in PARTICLES) {
            if (word.length - particle.length >= 2 && word.endsWith(particle)) {
                word = word.dropLast(particle.length)
                strippedParticle = true
                break
            }
        }

        // 3) 용언 활용형 — 어간이 짧으면 단어가 아니다
        if (!strippedParticle && NOUN_TAILS.none { word.endsWith(it) }) {
            for (ending in VERB_ENDINGS) {
                if (word.length > ending.length && word.endsWith(ending)) {
                    val stem = word.dropLast(ending.length)
                    return if (stem.length >= 2 && stem !in STOPWORDS) stem else null
                }
            }
        }

        // 4) 1글자 조사 — 명사 꼬리는 보호
        if (word.length >= 3 && word.last() in ONE_CHAR_PARTICLES && NOUN_TAILS.none { word.endsWith(it) }) {
            word = word.dropLast(1)
        }

        if (word.length < 2) return null
        if (word in STOPWORDS || word in VERB_ENDINGS || word in PARTICLES) return null
        return word
    }
}
