package dev.typinganimation.core.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Scholar API access. No Scholar or Minecraft classes are linked by the shared core. */
public final class ScholarAccess {
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<String, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    private ScholarAccess() { }

    /** Only Scholar-owned method names belong here; mapped Minecraft methods use typed calls in the adapter. */
    public static Object get(Object instance, String name) throws ReflectiveOperationException {
        if (instance == null) throw new ReflectiveOperationException("Missing Scholar API object: " + name);
        Map<String, Method> methods = METHODS.get(instance.getClass());
        Method method = methods.get(name);
        if (method == null) {
            method = instance.getClass().getMethod(name);
            methods.put(name, method);
        }
        try {
            return method.invoke(instance);
        } catch (InvocationTargetException failure) {
            throw new ReflectiveOperationException("Scholar getter failed: " + name, failure.getCause());
        }
    }

    public static int number(Object instance, String name) throws ReflectiveOperationException {
        return ((Number) get(instance, name)).intValue();
    }

    public static boolean flag(Object instance, String name) throws ReflectiveOperationException {
        return (Boolean) get(instance, name);
    }

    /** Scholar's native blink clock is a wall-clock timestamp; keep its existing focus and blink phase. */
    public static long lastActionTime(Object widget) throws ReflectiveOperationException {
        Map<String, Field> fields = FIELDS.get(widget.getClass());
        Field field = fields.get("lastActionTime");
        if (field == null) {
            for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    field = type.getDeclaredField("lastActionTime");
                    field.setAccessible(true);
                    fields.put("lastActionTime", field);
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException("Scholar lastActionTime");
        }
        return field.getLong(widget);
    }

    public static String plain(Object widget) throws ReflectiveOperationException {
        return (String) get(get(get(widget, "getEditor"), "getString"), "toStringWithoutFormatting");
    }

    /** Scholar counts Unicode code points, whereas the animation core indexes Java UTF-16 strings. */
    public static int utf16Index(String text, int codePointIndex) {
        if (text == null) throw new IllegalArgumentException("Missing book text");
        int count = text.codePointCount(0, text.length());
        if (codePointIndex < 0 || codePointIndex > count) throw new IndexOutOfBoundsException("Scholar character index");
        return text.offsetByCodePoints(0, codePointIndex);
    }
}
