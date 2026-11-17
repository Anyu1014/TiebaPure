package dev.anyu.tiebapure
import org.junit.Assert.*
import org.junit.Test
class OriginalImageLayoutTest {
    @Test fun respectsBoundsAndAvailableWidth() {
        assertEquals(1f / 3f, originalImageWidthFraction(10f / 21f), 0.001f)
        assertEquals(0.7f, originalImageWidthFraction(1f), 0.001f)
        assertEquals(1f, originalImageWidthFraction(21f / 10f), 0.001f)
        assertEquals(1f / 3f, originalImageWidthFraction(0.01f), 0.001f)
        assertEquals(1f, originalImageWidthFraction(100f), 0.001f)
    }
}
