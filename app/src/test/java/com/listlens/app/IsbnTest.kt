package com.listlens.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnTest {

  @Test
  fun extractIsbn13_accepts_isbn13_plain() {
    assertEquals("9780143127741", Isbn.extractIsbn13("9780143127741"))
  }

  @Test
  fun extractIsbn13_accepts_isbn13_with_noise() {
    assertEquals("9780143127741", Isbn.extractIsbn13("ISBN 978-0-143-12774-1"))
  }

  @Test
  fun extractIsbn13_converts_isbn10() {
    // 0143127748 -> 9780143127741
    assertEquals("9780143127741", Isbn.extractIsbn13("0143127748"))
  }

  @Test
  fun extractIsbn13_returns_null_for_invalid() {
    assertNull(Isbn.extractIsbn13("1234567890"))
    assertNull(Isbn.extractIsbn13("hello world"))
  }
}
