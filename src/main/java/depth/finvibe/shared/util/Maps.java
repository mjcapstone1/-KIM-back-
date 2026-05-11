package depth.finvibe.shared.util;

import depth.finvibe.shared.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Maps {
    private Maps() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected map but was " + (value == null ? "null" : value.getClass().getName()));
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected list but was " + (value == null ? "null" : value.getClass().getName()));
        }
        return (List<Object>) list;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listOfMaps(Object value) {
        List<Object> raw = list(value);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : raw) {
            rows.add((Map<String, Object>) item);
        }
        return rows;
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String str(Map<String, Object> map, String key, String defaultValue) {
        String value = str(map, key);
        return value == null ? defaultValue : value;
    }

    public static int intVal(Map<String, Object> map, String key) {
        return intVal(map.get(key), 0);
    }

    public static int intVal(Map<String, Object> map, String key, int defaultValue) {
        return intVal(map.get(key), defaultValue);
    }

    public static int intVal(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(value).replace(",", "").trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long longVal(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = String.valueOf(value).replace(",", "").trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return Math.round(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double doubleVal(Map<String, Object> map, String key) {
        return doubleVal(map.get(key), 0.0);
    }

    public static double doubleVal(Map<String, Object> map, String key, double defaultValue) {
        return doubleVal(map.get(key), defaultValue);
    }

    public static double doubleVal(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            String text = String.valueOf(value).replace(",", "").replace("%", "").trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean boolVal(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }


public static Map<String, Object> of(Object... items) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < items.length; i += 2) {
        map.put(String.valueOf(items[i]), items[i + 1]);
    }
    return map;
}

    public static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    public static List<Object> array() {
        return new ArrayList<>();
    }

    public static Object copy(Object value) {
        return Json.deepCopy(value);
    }

    public static String resourceAsString(String name) {
        try (InputStream in = Maps.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
