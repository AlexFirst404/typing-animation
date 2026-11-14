package dev.typinganimation.core.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads Better Command Block UI's own layout API without linking its optional classes. */
public final class BetterCommandBlockAccess {
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<String, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private BetterCommandBlockAccess() { }

    public static Object field(Object owner, String name) throws ReflectiveOperationException {
        Map<String, Field> cache = FIELDS.get(owner.getClass());
        Field field = cache.get(name);
        if (field == null) {
            Class<?> type = owner.getClass();
            while (type != null) {
                try { field = type.getDeclaredField(name); break; }
                catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            cache.put(name, field);
        }
        return field.get(owner);
    }

    public static Object accessor(Object owner, String modern, String legacy) throws ReflectiveOperationException {
        Object accessor = field(owner, "accessor");
        Map<String, Method> cache = METHODS.get(accessor.getClass());
        Method method = cache.get(modern);
        if (method == null) {
            try { method = accessor.getClass().getMethod(modern); }
            catch (NoSuchMethodException missing) { method = accessor.getClass().getMethod(legacy); }
            cache.put(modern, method);
        }
        try { return method.invoke(accessor); }
        catch (InvocationTargetException failure) {
            throw new ReflectiveOperationException("Better Command Block UI accessor failed: " + modern, failure.getCause());
        }
    }

    public static int number(Object owner, String field) throws ReflectiveOperationException {
        return ((Number) field(owner, field)).intValue();
    }
    public static boolean flag(Object owner, String field) throws ReflectiveOperationException {
        return (Boolean) field(owner, field);
    }

    /** Virtual indentation consumes screen columns, but never consumes command-string indices. */
    public static int sourceIndex(int textOffset, int indentation, int horizontalScroll, int segmentOffset) {
        return textOffset + horizontalScroll - indentation + segmentOffset;
    }
    public static int virtualPrefix(int indentation, int horizontalScroll, int segmentOffset, int length) {
        return Math.max(0, Math.min(length, indentation - horizontalScroll - segmentOffset));
    }
}
