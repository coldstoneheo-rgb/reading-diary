package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-005 §1: Play 첫 업로드 전에 정한 applicationId. 업로드 뒤에는 바꿀 수 없으므로 되돌림을 막는다.
 * namespace·코드 패키지(`com.example`)는 그대로다 — BuildConfig 클래스 위치가 그 증거.
 */
class ApplicationIdTest {

  @Test
  fun applicationId_isTheReservedPlayId() {
    assertEquals("com.woodpeckerai.readingdiary", BuildConfig.APPLICATION_ID)
  }
}
