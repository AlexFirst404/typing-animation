package dev.typinganimation.core.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Access to the optional editor's own fields; mapped Minecraft members use typed calls. */
public final class CommandBlockOverhaulAccess {
    public static final String WIDGET = "me.cyanhana.commandblockuioverhaul.ui.ModMultiLineEditBox";
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<String, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    private CommandBlockOverhaulAccess() { }

    public static Object field(Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) throw new ReflectiveOperationException("Missing command editor");
        Map<String, Field> fields = FIELDS.get(owner.getClass());
        Field field = fields.get(name);
        if (field == null) {
            Class<?> type = owner.getClass();
            while (type != null && !WIDGET.equals(type.getName())) type = type.getSuperclass();
            if (type == null) throw new ReflectiveOperationException("Not a CommandBlockUIOverhaul editor");
            field = type.getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name, field);
        }
        return field.get(owner);
    }

    public static int number(Object owner, String name) throws ReflectiveOperationException {
        return ((Number) field(owner, name)).intValue();
    }

    public static Object accessor(Object owner, String name) throws ReflectiveOperationException {
        Object target = field(owner, "accessor");
        Map<String, Method> methods = METHODS.get(target.getClass());
        Method method = methods.get(name);
        if (method == null) { method = target.getClass().getMethod(name); methods.put(name, method); }
        return method.invoke(target);
    }

    /** Display wrapping must preserve every original UTF-16 unit, including spaces and surrogate pairs. */
    public static int[] lineStarts(String value, List<String> lines) {
        int[] starts = new int[lines.size()];
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || !value.startsWith(line, offset))
                throw new IllegalArgumentException("Command editor layout changed the underlying text");
            starts[i] = offset;
            offset += line.length();
            if (offset > 0 && offset < value.length() && Character.isHighSurrogate(value.charAt(offset-1))
                    && Character.isLowSurrogate(value.charAt(offset)))
                throw new IllegalArgumentException("Command editor split a surrogate pair");
        }
        if (offset != value.length()) throw new IllegalArgumentException("Incomplete command editor layout");
        return starts;
    }
}
