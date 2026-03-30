package com.trustai.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class InputSanitizer {

    private InputSanitizer() {}

    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw
            .replaceAll("[\\u0000-\\u001F&&[^\\n\\r\\t]]", "")
            .replace("<script", "&lt;script")
            .replace("</script>", "&lt;/script&gt;")
            .trim();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static void sanitizeObject(Object body) {
        sanitizeObject(body, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings("unchecked")
    private static void sanitizeObject(Object body, Set<Object> visited) {
        if (body == null) {
            return;
        }
        Class<?> bodyClass = body.getClass();
        if (isTerminalType(bodyClass)) {
            return;
        }
        if (body instanceof String str) {
            // String is immutable, so caller must sanitize before assigning.
            return;
        }
        if (visited.contains(body)) {
            return;
        }
        visited.add(body);
        if (body instanceof Map<?, ?> map) {
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String strVal) {
                    entry.setValue(sanitize(strVal));
                } else {
                    sanitizeObject(value, visited);
                }
            }
            return;
        }
        if (body instanceof Collection<?> collection) {
            for (Object item : collection) {
                sanitizeObject(item, visited);
            }
            return;
        }

        Class<?> cls = bodyClass;
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(body);
                    if (value instanceof String strVal) {
                        field.set(body, sanitize(strVal));
                    } else {
                        sanitizeObject(value, visited);
                    }
                } catch (IllegalAccessException ignored) {
                    // Ignore inaccessible fields to avoid blocking requests.
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean isTerminalType(Class<?> cls) {
        if (cls.isPrimitive()
            || cls.isEnum()
            || Number.class.isAssignableFrom(cls)
            || Boolean.class == cls
            || Character.class == cls
            || Class.class == cls) {
            return true;
        }
        String className = cls.getName();
        return className.startsWith("java.time.")
            || className.startsWith("java.math.")
            || className.startsWith("java.net.")
            || className.startsWith("java.nio.");
    }
}
