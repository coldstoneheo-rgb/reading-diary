package com.example.data.api

/**
 * 바코드 스캔 결과를 ISBN-13으로 검증하는 순수 함수(ADR-003 실행 순서 8).
 * 책 뒷면의 EAN-13 바코드는 978/979로 시작하는 13자리 숫자이고 마지막 자리가 체크섬이다.
 * 부가기호(5자리 두 번째 바코드)나 잡지 바코드(977)는 거른다.
 */
object Isbn {
    private val NON_DIGIT = Regex("[^0-9]")

    /** 하이픈·공백을 걷어낸 13자리 ISBN. 아니면 null. */
    fun fromBarcode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.replace(NON_DIGIT, "")
        if (digits.length != 13) return null
        if (!(digits.startsWith("978") || digits.startsWith("979"))) return null
        return if (checksumOk(digits)) digits else null
    }

    private val ISBN_QUERY = Regex("^[0-9\\- ]+$")

    /** 사용자가 친 검색어가 '전부' 숫자·하이픈·공백이고 유효한 ISBN-13일 때만 ISBN으로 본다. "데미안 9788937460449" 같은 혼합 입력은 제목 검색으로 둔다. */
    fun fromQuery(query: String): String? =
        query.trim().takeIf { ISBN_QUERY.matches(it) }?.let { fromBarcode(it) }

    /** ISBN-13(EAN-13) 체크섬: 홀수 자리×1 + 짝수 자리×3 의 합이 10의 배수. */
    internal fun checksumOk(digits: String): Boolean {
        if (digits.length != 13 || digits.any { !it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 13) {
            val d = digits[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        return sum % 10 == 0
    }
}
