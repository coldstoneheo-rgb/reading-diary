package com.example.data

/**
 * 일기 저장 시 사진 처리(명세 PHRASE-RECALL-SPEC-2026-11 §3.4). «유지»와 «삭제»를 null 하나로 겸하지 않는다.
 * - [Unchanged]: 사진 행을 건드리지 않는다(텍스트만 고친 편집).
 * - [Set]: 목록 순서 = sortOrder. 목록에 없는 기존 행은 삭제(+ 파일 삭제), [PhotoSlot.New]는 행 추가.
 *   교체 = 그 자리의 Existing을 New로, 전부 지우기 = `Set(emptyList())`.
 * 데이터 계층 타입이라 data 패키지에 둔다(Repository가 ui 타입을 참조하지 않게).
 */
sealed interface PhotoEdit {
    data object Unchanged : PhotoEdit
    data class Set(val slots: List<PhotoSlot>) : PhotoEdit
}

sealed interface PhotoSlot {
    data class Existing(val photoId: Int) : PhotoSlot
    /** [com.example.data.photo.DiaryPhotoStore.save]가 돌려준 상대 경로. */
    data class New(val path: String) : PhotoSlot
}
