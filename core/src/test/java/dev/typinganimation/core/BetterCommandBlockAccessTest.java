package dev.typinganimation.core;

import dev.typinganimation.core.compat.BetterCommandBlockAccess;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BetterCommandBlockAccessTest {
    @Test void formattedColourChunksMapAroundVirtualIndentationAndHorizontalScrolling() {
        String command="say prefix execute as @e[nbt={Name:'A\uD83D\uDE03',Tags:['one','two']}] run say end";
        int begin=command.indexOf("@e[");
        String actual=command.substring(begin,command.indexOf(" run"));
        for (int indent:new int[]{0,2,8}) {
            String rendered=" ".repeat(indent)+actual;
            for (int scroll:new int[]{0,1,3,9}) {
                int clipped=Math.min(scroll,rendered.length());
                String visible=rendered.substring(clipped);
                for (int split=0;split<=visible.length();split++) {
                    for (int[] part:new int[][]{{0,split},{split,visible.length()}}) {
                        String segment=visible.substring(part[0],part[1]);
                        int prefix=BetterCommandBlockAccess.virtualPrefix(indent,clipped,part[0],segment.length());
                        int start=BetterCommandBlockAccess.sourceIndex(begin,indent,clipped,part[0]+prefix);
                        if (prefix<segment.length()) {
                            assertTrue(start>=begin);
                            assertEquals(segment.substring(prefix),command.substring(start,start+segment.length()-prefix));
                        }
                    }
                }
            }
        }
    }

    @Test void optionalOwnedFieldAndAccessorNamesSupportBothPublishedApiFamilies() throws Exception {
        ModernWidget modern=new ModernWidget();LegacyWidget legacy=new LegacyWidget();
        assertEquals(List.of("native line"),BetterCommandBlockAccess.field(modern,"lines"));
        assertEquals(4,BetterCommandBlockAccess.number(modern,"horizontalOffset"));
        assertTrue(BetterCommandBlockAccess.flag(modern,"hasCommandSuggestor"));
        assertEquals("font-modern",BetterCommandBlockAccess.accessor(modern,"getFont","getTextRenderer"));
        assertEquals("font-legacy",BetterCommandBlockAccess.accessor(legacy,"getFont","getTextRenderer"));
        assertEquals("font-modern",BetterCommandBlockAccess.accessor(modern,"getFont","getTextRenderer"));
        assertThrows(NoSuchFieldException.class,()->BetterCommandBlockAccess.field(modern,"removedFutureLayout"));
        assertThrows(NoSuchMethodException.class,()->BetterCommandBlockAccess.accessor(modern,"removedMethod","removedAlias"));
    }
    @Test void accessorInvocationFailureIsReportedWithoutChangingTheOwner() {
        BrokenWidget broken=new BrokenWidget();
        ReflectiveOperationException failure=assertThrows(ReflectiveOperationException.class,
                ()->BetterCommandBlockAccess.accessor(broken,"getFont","getTextRenderer"));
        assertInstanceOf(IllegalStateException.class,failure.getCause());
    }

    public static class Layout {
        private final List<String> lines=List.of("native line");
        private final int horizontalOffset=4;
        private final boolean hasCommandSuggestor=true;
    }
    public static class ModernWidget extends Layout { private final Object accessor=new ModernApi(); }
    public static class LegacyWidget extends Layout { private final Object accessor=new LegacyApi(); }
    public static class BrokenWidget extends Layout { private final Object accessor=new BrokenApi(); }
    public static class ModernApi { public Object getFont() { return "font-modern"; } }
    public static class LegacyApi { public Object getTextRenderer() { return "font-legacy"; } }
    public static class BrokenApi { public Object getFont() { throw new IllegalStateException("future API"); } }
}
