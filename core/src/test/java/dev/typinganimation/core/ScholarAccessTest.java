package dev.typinganimation.core;

import dev.typinganimation.core.compat.ScholarAccess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScholarAccessTest {
    @Test void mapsCodePointsToUtf16WithoutSplittingSupplementaryCharacters() {
        String text = "A\uD83D\uDE00Б\n\uD834\uDD1EZ";
        assertArrayEquals(new int[]{0, 1, 3, 4, 5, 7, 8},
                java.util.stream.IntStream.rangeClosed(0, 6).map(i -> ScholarAccess.utf16Index(text, i)).toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> ScholarAccess.utf16Index(text, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> ScholarAccess.utf16Index(text, 7));
    }

    @Test void optionalApiAccessUsesScholarGettersAndInheritedBlinkField() throws Exception {
        FakeWidget widget = new FakeWidget();
        assertEquals("A\uD83D\uDE00Б\n", ScholarAccess.plain(widget));
        assertEquals(123L, ScholarAccess.lastActionTime(widget));
        assertTrue(ScholarAccess.flag(widget, "isSelecting"));
        assertEquals(7, ScholarAccess.number(widget, "getCursorPos"));
        assertThrows(NoSuchMethodException.class, () -> ScholarAccess.get(widget, "removedFutureApi"));
    }

    public static class BaseWidget { protected long lastActionTime = 123L; }
    public static class FakeWidget extends BaseWidget {
        public FakeWidget getEditor() { return this; }
        public FakeWidget getString() { return this; }
        public String toStringWithoutFormatting() { return "A\uD83D\uDE00Б\n"; }
        public boolean isSelecting() { return true; }
        public int getCursorPos() { return 7; }
    }
}
