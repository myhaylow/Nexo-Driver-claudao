package br.com.nexo.driver.ocr.mlkit

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextLayoutReconstructorTest {

    @Test
    fun `joins line fragments on the same visual row from left to right`() {
        val rows = OcrTextLayoutReconstructor.reconstruct(
            listOf(
                OcrLayoutLine("112 corridas", top = 103, left = 176, originalIndex = 3),
                OcrLayoutLine("R$8,50", top = 180, left = 32, originalIndex = 4),
                OcrLayoutLine("4,96", top = 100, left = 32, originalIndex = 2),
                OcrLayoutLine("Pgto. no app", top = 48, left = 32, originalIndex = 1),
            ),
        )

        assertEquals(listOf("Pgto. no app", "4,96 112 corridas", "R$8,50"), rows)
    }

    @Test
    fun `does not merge neighbouring rows outside the Y tolerance`() {
        val rows = OcrTextLayoutReconstructor.reconstruct(
            listOf(
                OcrLayoutLine("primeira", top = 100, left = 20, originalIndex = 0),
                OcrLayoutLine(
                    "segunda",
                    top = 100 + OcrTextLayoutReconstructor.ROW_Y_TOLERANCE_PIXELS + 1,
                    left = 20,
                    originalIndex = 1,
                ),
            ),
        )

        assertEquals(listOf("primeira", "segunda"), rows)
    }

    @Test
    fun `uses original order as a deterministic tie breaker`() {
        val rows = OcrTextLayoutReconstructor.reconstruct(
            listOf(
                OcrLayoutLine("direita", top = 120, left = 80, originalIndex = 3),
                OcrLayoutLine("primeiro", top = 120, left = 20, originalIndex = 1),
                OcrLayoutLine("segundo", top = 120, left = 20, originalIndex = 2),
            ),
        )

        assertEquals(listOf("primeiro segundo direita"), rows)
    }
}
