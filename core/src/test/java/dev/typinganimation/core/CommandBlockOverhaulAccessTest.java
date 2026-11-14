package dev.typinganimation.core;

import dev.typinganimation.core.compat.CommandBlockOverhaulAccess;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandBlockOverhaulAccessTest {
    @Test void wrappedCommandsRetainSpacesEmptyRowsAndUtf16Positions() {
        assertArrayEquals(new int[]{0, 4, 4, 7}, CommandBlockOverhaulAccess.lineStarts(
                "say \uD83D\uDE03 {x:1}", List.of("say ", "", "\uD83D\uDE03 ", "{x:1}")));
        assertArrayEquals(new int[]{0}, CommandBlockOverhaulAccess.lineStarts("", List.of("")));
    }

    @Test void changedOrIncompleteLayoutsFallBackInsteadOfAnimatingWrongCharacters() {
        assertThrows(IllegalArgumentException.class, () -> CommandBlockOverhaulAccess.lineStarts("a b", List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> CommandBlockOverhaulAccess.lineStarts("abc", List.of("ab")));
        assertThrows(IllegalArgumentException.class, () -> CommandBlockOverhaulAccess.lineStarts("\uD83D\uDE03", List.of("\uD83D", "\uDE03")));
    }
}
