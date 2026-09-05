package com.example

import com.example.data.api.Isbn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {

  @Test
  fun validIsbn13_isAcceptedWithOrWithoutHyphens() {
    // 9788937460449: 실제 유효한 978 접두 ISBN-13 (체크섬 9)
    assertEquals("9788937460449", Isbn.fromBarcode("9788937460449"))
    assertEquals("9788937460449", Isbn.fromBarcode("978-89-374-6044-9"))
    assertEquals("9788937460449", Isbn.fromBarcode(" 978 89 374 6044 9 "))
  }

  @Test
  fun prefix979_isAccepted() {
    // 9791162243077: 979 접두, 체크섬 7
    assertEquals("9791162243077", Isbn.fromBarcode("9791162243077"))
  }

  @Test
  fun wrongChecksum_isRejected() {
    assertNull(Isbn.fromBarcode("9788937460440"))
    assertFalse(Isbn.checksumOk("9788937460440"))
    assertTrue(Isbn.checksumOk("9788937460449"))
  }

  @Test
  fun fromQuery_onlyTreatsPureNumericInputAsIsbn() {
    assertEquals("9788937460449", Isbn.fromQuery(" 978-89-374-6044-9 "))
    assertNull(Isbn.fromQuery("데미안 9788937460449"))   // 제목이 섞이면 제목 검색
    assertNull(Isbn.fromQuery("데미안"))
    assertNull(Isbn.fromQuery("9788937460440"))          // 체크섬 오류
  }

  @Test
  fun nonBookBarcodes_areRejected() {
    assertNull(Isbn.fromBarcode("9771234567003"))   // 977 = 잡지(ISSN) EAN
    assertNull(Isbn.fromBarcode("8801234567890"))   // 880 = 한국 상품 바코드
    assertNull(Isbn.fromBarcode("12345"))           // 5자리 부가기호
    assertNull(Isbn.fromBarcode("978893746044"))    // 12자리
    assertNull(Isbn.fromBarcode(""))
    assertNull(Isbn.fromBarcode(null))
    assertNull(Isbn.fromBarcode("978893746044X"))   // ISBN-10식 X는 13자리에 없음
  }
}
