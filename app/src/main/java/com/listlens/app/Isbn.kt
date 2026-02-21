package com.listlens.app

/**
 * ISBN utilities.
 *
 * Goals:
 * - Accept messy OCR / user input (spaces, hyphens, extra digits)
 * - Return a validated ISBN-13 when possible
 */
object Isbn {

  /**
   * Extracts a valid ISBN-13 from arbitrary input, if present.
   *
   * - Prefers existing ISBN-13 (EAN-13 starting 978/979)
   * - Falls back to ISBN-10 conversion
   */
  fun extractIsbn13(raw: String): String? {
    val digits = raw.filter { it.isDigit() || it == 'X' || it == 'x' }

    // 1) Try to find a valid EAN-13 window.
    val onlyDigits = digits.filter { it.isDigit() }
    if (onlyDigits.length >= 13) {
      for (i in 0..(onlyDigits.length - 13)) {
        val candidate = onlyDigits.substring(i, i + 13)
        if ((candidate.startsWith("978") || candidate.startsWith("979")) && isValidEan13(candidate)) {
          return candidate
        }
      }
    }

    // 2) Try ISBN-10 windows (allow X check digit)
    if (digits.length >= 10) {
      for (i in 0..(digits.length - 10)) {
        val candidate10 = digits.substring(i, i + 10)
        val converted = isbn10To13(candidate10)
        if (converted != null) return converted
      }
    }

    return null
  }

  fun isValidIsbn13(isbn13: String): Boolean =
    isbn13.length == 13 && (isbn13.startsWith("978") || isbn13.startsWith("979")) && isValidEan13(isbn13)

  private fun isbn10To13(isbn10: String): String? {
    if (isbn10.length != 10) return null

    val core9 = isbn10.substring(0, 9)
    if (!core9.all { it.isDigit() }) return null

    val check10Char = isbn10.last()
    val check10Value = when {
      check10Char.isDigit() -> check10Char.digitToInt()
      check10Char == 'X' || check10Char == 'x' -> 10
      else -> return null
    }

    if (check10Value != calcIsbn10Check(core9)) return null

    val base12 = "978$core9"
    val check13 = calcEan13CheckDigit(base12)
    return base12 + check13
  }

  private fun calcIsbn10Check(core9: String): Int {
    // Weighted sum 10..2
    val sum = core9.mapIndexed { index, c ->
      val weight = 10 - index
      weight * c.digitToInt()
    }.sum()

    val r = 11 - (sum % 11)
    return when (r) {
      10 -> 10
      11 -> 0
      else -> r
    }
  }

  private fun isValidEan13(digits: String): Boolean {
    if (digits.length != 13 || !digits.all { it.isDigit() }) return false
    val sum = digits.dropLast(1).mapIndexed { index, c ->
      val n = c.digitToInt()
      if (index % 2 == 0) n else n * 3
    }.sum()
    val check = (10 - (sum % 10)) % 10
    return check == digits.last().digitToInt()
  }

  private fun calcEan13CheckDigit(first12Digits: String): Int {
    require(first12Digits.length == 12)
    val sum = first12Digits.mapIndexed { index, c ->
      val n = c.digitToInt()
      if (index % 2 == 0) n else n * 3
    }.sum()
    return (10 - (sum % 10)) % 10
  }
}
