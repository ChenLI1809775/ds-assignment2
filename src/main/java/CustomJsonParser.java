import java.lang.reflect.Method;
import java.util.*;

/**
 * Custom JSON Parser
 * A simple implementation of JSON parser that can parse JSON strings into Java objects
 * and serialize Java objects into JSON strings.
 */
public class CustomJsonParser {

    // JSON string to be parsed
    private String json;
    // Current parsing position
    private int pos;

    /**
     * Parse a JSON string into a Java object
     *
     * @param jsonString The JSON string to parse
     * @return The parsed Java object (Map, List, String, Number, Boolean, or null)
     * @throws JsonParsingException if the JSON string is invalid
     */
    public Object parse(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new JsonParsingException("Empty JSON string");
        }

        this.json = jsonString.trim();
        this.pos = 0;

        Object result = parseValue();

        // Check if there's unexpected content after parsing
        skipWhitespace();
        if (pos < json.length()) {
            throw new JsonParsingException("Unexpected character at end of JSON: " + json.substring(pos));
        }

        return result;
    }

    /**
     * Parse a JSON string into a Java object of specified type
     *
     * @param jsonString The JSON string to parse
     * @param clazz      The target class to deserialize into
     * @param <T>        The type of the target object
     * @return The parsed Java object
     * @throws JsonParsingException if the JSON string is invalid
     */
    public <T> T parse(String jsonString, Class<T> clazz) {
        Object parsed = parse(jsonString);

        if (clazz == null) {
            throw new JsonParsingException("Target class cannot be null");
        }

        // If target is a basic type or collection, try direct cast
        if (parsed instanceof Map && isPojoClass(clazz)) {
            return deserializeToPojo((Map<String, Object>) parsed, clazz);
        }

        return clazz.cast(parsed);
    }

    /**
     * Parse a JSON string into a Java object of specified parameterized type
     *
     * @param jsonString  The JSON string to parse
     *                    * @param clazz      The target class to deserialize into
     * @param genericType The generic type of list elements
     * @param <T>         The type of the target object
     * @return The parsed Java object
     * @throws JsonParsingException if the JSON string is invalid
     */
    public <T> T parse(String jsonString, Class<T> clazz, Class<?> genericType) {
        Object parsed = parse(jsonString);

        if (clazz == null) {
            throw new JsonParsingException("Target class cannot be null");
        }

        // Handle List types
        if (List.class.isAssignableFrom(clazz) && parsed instanceof List) {
            List<Object> list = (List<Object>) parsed;
            List<Object> result = new ArrayList<>();

            for (Object item : list) {
                if (item instanceof Map && isPojoClass(genericType)) {
                    result.add(deserializeToPojo((Map<String, Object>) item, genericType));
                } else {
                    result.add(item);
                }
            }

            return (T) result;
        }

        // Handle Map types or POJOs
        if (parsed instanceof Map && isPojoClass(clazz)) {
            return deserializeToPojo((Map<String, Object>) parsed, clazz);
        }

        return clazz.cast(parsed);
    }

    /**
     * Serialize a Java object into a pretty formatted JSON string
     *
     * @param obj The object to serialize
     * @return The pretty formatted JSON string representation
     */
    public String stringifyPretty(Object obj) {
        return stringifyPretty(obj, 0);
    }

    /**
     * Serialize a Java object into a pretty formatted JSON string with specified indent
     *
     * @param obj    The object to serialize
     * @param indent The initial indentation level
     * @return The pretty formatted JSON string representation
     */
    private String stringifyPretty(Object obj, int indent) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof Map) {
            return stringifyMapPretty((Map<?, ?>) obj, indent);
        } else if (obj instanceof List) {
            return stringifyListPretty((List<?>) obj, indent);
        } else if (obj instanceof Collection) {
            return stringifyListPretty(new ArrayList<>((Collection<?>) obj), indent);
        } else {
            return stringifyObjectPretty(obj, indent);
        }
    }

    /**
     * Create indentation spaces based on level
     *
     * @param indent The indentation level
     * @return The indentation string
     */
    private String createIndent(int indent) {
        return "  ".repeat(indent); // 2 spaces per indent level
    }

    /**
     * Serialize a Map into a pretty formatted JSON object string
     *
     * @param map    The Map to serialize
     * @param indent The current indentation level
     * @return The pretty formatted JSON object string
     */
    private String stringifyMapPretty(Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append(createIndent(indent + 1))
                    .append("\"").append(escapeString(entry.getKey().toString())).append("\": ");

            Object value = entry.getValue();
            if (value instanceof Map || value instanceof List) {
                sb.append(stringifyPretty(value, indent + 1));
            } else {
                sb.append(stringifyPretty(value, 0));
            }
            first = false;
        }

        sb.append("\n").append(createIndent(indent)).append("}");
        return sb.toString();
    }

    /**
     * Serialize a List into a pretty formatted JSON array string
     *
     * @param list   The List to serialize
     * @param indent The current indentation level
     * @return The pretty formatted JSON array string
     */
    private String stringifyListPretty(List<?> list, int indent) {
        if (list.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append(createIndent(indent + 1));

            if (item instanceof Map || item instanceof List) {
                sb.append(stringifyPretty(item, indent + 1));
            } else {
                sb.append(stringifyPretty(item, 0));
            }
            first = false;
        }

        sb.append("\n").append(createIndent(indent)).append("]");
        return sb.toString();
    }

    /**
     * Serialize an object using its getter methods in pretty format
     *
     * @param obj    The object to serialize
     * @param indent The indentation level
     * @return The pretty formatted JSON object string
     */
    private String stringifyObjectPretty(Object obj, int indent) {
        Class<?> clazz = obj.getClass();
        Method[] methods = clazz.getMethods();

        // Map to store getter methods and their corresponding field names
        Map<String, Method> getters = new HashMap<>();

        // Find all getter methods
        for (Method method : methods) {
            if (method.getName().startsWith("get") &&
                    method.getParameterCount() == 0 &&
                    !method.getName().equals("getClass")) {
                String fieldName = method.getName().substring(3);
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                getters.put(fieldName, method);
            } else if (method.getName().startsWith("is") &&
                    method.getParameterCount() == 0 &&
                    method.getReturnType() == boolean.class) {
                String fieldName = method.getName().substring(2);
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                getters.put(fieldName, method);
            }
        }

        if (getters.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        boolean first = true;
        for (Map.Entry<String, Method> entry : getters.entrySet()) {
            String fieldName = entry.getKey();
            Method getter = entry.getValue();

            try {
                Object value = getter.invoke(obj);
                if (!first) {
                    sb.append(",\n");
                }
                sb.append(createIndent(indent + 1))
                        .append("\"").append(escapeString(fieldName)).append("\": ");

                if (value instanceof Map || value instanceof List) {
                    sb.append(stringifyPretty(value, indent + 1));
                } else {
                    sb.append(stringifyPretty(value, 0));
                }
                first = false;
            } catch (Exception e) {
                // Skip fields that cause exceptions
                continue;
            }
        }

        sb.append("\n").append(createIndent(indent)).append("}");
        return sb.toString();
    }

    /**
     * Check if a class is a POJO (Plain Old Java Object)
     *
     * @param clazz The class to check
     * @return true if it's a POJO, false otherwise
     */
    private boolean isPojoClass(Class<?> clazz) {
        return clazz != String.class &&
                clazz != Number.class &&
                clazz != Boolean.class &&
                !Map.class.isAssignableFrom(clazz) &&
                !List.class.isAssignableFrom(clazz) &&
                !Collection.class.isAssignableFrom(clazz);
    }

    /**
     * Deserialize a Map into a POJO using setter methods
     *
     * @param map   The map containing field values
     * @param clazz The target class
     * @param <T>   The type of the target object
     * @return The deserialized object
     */
    private <T> T deserializeToPojo(Map<String, Object> map, Class<T> clazz) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            Method[] methods = clazz.getMethods();

            // Create a map of setter methods
            Map<String, Method> setters = new HashMap<>();
            for (Method method : methods) {
                if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                    String fieldName = method.getName().substring(3);
                    fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                    setters.put(fieldName, method);
                }
            }

            // Set each field using its setter
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                Method setter = setters.get(fieldName);

                if (setter != null) {
                    Class<?> paramType = setter.getParameterTypes()[0];
                    try {
                        Object convertedValue = convertValue(value, paramType);
                        setter.invoke(obj, convertedValue);
                    } catch (Exception e) {
                        // Skip fields that cause exceptions
                        System.err.println("Warning: Failed to set field " + fieldName + ": " + e.getMessage());
                    }
                }
            }

            return obj;
        } catch (Exception e) {
            throw new JsonParsingException("Failed to instantiate or populate object: " + e.getMessage());
        }
    }

    /**
     * Convert a value to the target type
     *
     * @param value      The value to convert
     * @param targetType The target type
     * @return The converted value
     */
    /**
     * Convert a value to the target type
     *
     * @param value      The value to convert
     * @param targetType The target type
     * @return The converted value
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        // Handle number conversions
        if (value instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            } else if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            } else if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            } else if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            } else if (targetType == short.class || targetType == Short.class) {
                return num.shortValue();
            } else if (targetType == byte.class || targetType == Byte.class) {
                return num.byteValue();
            }
        }

        // Handle string to number conversions
        if (value instanceof String str) {
            try {
                if (targetType == int.class || targetType == Integer.class) {
                    return Integer.parseInt(str);
                } else if (targetType == long.class || targetType == Long.class) {
                    return Long.parseLong(str);
                } else if (targetType == double.class || targetType == Double.class) {
                    return Double.parseDouble(str);
                } else if (targetType == float.class || targetType == Float.class) {
                    return Float.parseFloat(str);
                }
            } catch (NumberFormatException e) {
                // Fall through to return the original value
            }
        }

        // Handle nested object conversion
        if (value instanceof Map && isPojoClass(targetType)) {
            return deserializeToPojo((Map<String, Object>) value, targetType);
        }

        // For other types, return as is (may cause ClassCastException later)
        return value;
    }


    /**
     * Parse a JSON value (object, array, string, number, boolean, or null)
     *
     * @return The parsed value
     */
    private Object parseValue() {
        skipWhitespace();

        if (pos >= json.length()) {
            throw new JsonParsingException("Unexpected end of JSON");
        }

        char currentChar = json.charAt(pos);

        switch (currentChar) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                return parseTrue();
            case 'f':
                return parseFalse();
            case 'n':
                return parseNull();
            default:
                // Number or negative number
                if (Character.isDigit(currentChar) || currentChar == '-') {
                    return parseNumber();
                } else {
                    throw new JsonParsingException("Unexpected character: " + currentChar);
                }
        }
    }

    /**
     * Parse a JSON object
     *
     * @return A Map representing the JSON object
     */
    private Map<String, Object> parseObject() {
        if (json.charAt(pos) != '{') {
            throw new JsonParsingException("Expected '{' at position " + pos);
        }
        pos++; // Skip '{'

        Map<String, Object> object = new HashMap<>();
        skipWhitespace();

        // Handle empty object
        if (pos < json.length() && json.charAt(pos) == '}') {
            pos++; // Skip '}'
            return object;
        }

        while (pos < json.length()) {
            skipWhitespace();

            // Parse key
            String key = parseString();
            skipWhitespace();

            // Check for ':'
            if (pos >= json.length() || json.charAt(pos) != ':') {
                throw new JsonParsingException("Expected ':' after key");
            }
            pos++; // Skip ':'
            skipWhitespace();

            // Parse value
            Object value = parseValue();
            object.put(key, value);

            skipWhitespace();

            // Check for ',' or '}'
            if (pos >= json.length()) {
                throw new JsonParsingException("Unexpected end of JSON object");
            }

            char ch = json.charAt(pos);
            if (ch == '}') {
                pos++; // Skip '}'
                break;
            } else if (ch == ',') {
                pos++; // Skip ','
                continue;
            } else {
                throw new JsonParsingException("Expected ',' or '}' in object");
            }
        }

        return object;
    }

    /**
     * Parse a JSON array
     *
     * @return A List representing the JSON array
     */
    private List<Object> parseArray() {
        if (json.charAt(pos) != '[') {
            throw new JsonParsingException("Expected '[' at position " + pos);
        }
        pos++; // Skip '['

        List<Object> array = new ArrayList<>();
        skipWhitespace();

        // Handle empty array
        if (pos < json.length() && json.charAt(pos) == ']') {
            pos++; // Skip ']'
            return array;
        }

        while (pos < json.length()) {
            skipWhitespace();

            // Parse value
            Object value = parseValue();
            array.add(value);

            skipWhitespace();

            // Check for ',' or ']'
            if (pos >= json.length()) {
                throw new JsonParsingException("Unexpected end of JSON array");
            }

            char ch = json.charAt(pos);
            if (ch == ']') {
                pos++; // Skip ']'
                break;
            } else if (ch == ',') {
                pos++; // Skip ','
                continue;
            } else {
                throw new JsonParsingException("Expected ',' or ']' in array");
            }
        }

        return array;
    }

    /**
     * Parse a JSON string
     *
     * @return The parsed string
     */
    private String parseString() {
        if (json.charAt(pos) != '"') {
            throw new JsonParsingException("Expected '\"' at position " + pos);
        }
        pos++; // Skip opening '"'

        StringBuilder sb = new StringBuilder();
        while (pos < json.length() && json.charAt(pos) != '"') {
            char ch = json.charAt(pos);
            if (ch == '\\') {
                pos++; // Skip escape character
                if (pos >= json.length()) {
                    throw new JsonParsingException("Unexpected end in string escape sequence");
                }
                ch = json.charAt(pos);
                switch (ch) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        // Handle Unicode escape
                        if (pos + 4 >= json.length()) {
                            throw new JsonParsingException("Invalid Unicode escape sequence");
                        }
                        String hex = json.substring(pos + 1, pos + 5);
                        try {
                            int codePoint = Integer.parseInt(hex, 16);
                            sb.append((char) codePoint);
                            pos += 4; // Skip 4 hex characters
                        } catch (NumberFormatException e) {
                            throw new JsonParsingException("Invalid Unicode escape sequence: " + hex);
                        }
                        break;
                    default:
                        throw new JsonParsingException("Invalid escape sequence: \\" + ch);
                }
            } else {
                sb.append(ch);
            }
            pos++;
        }

        if (pos >= json.length()) {
            throw new JsonParsingException("Unterminated string");
        }

        pos++; // Skip closing '"'
        return sb.toString();
    }

    /**
     * Parse a JSON number
     *
     * @return A Number (Integer, Long, or Double) representing the parsed number
     */
    private Number parseNumber() {
        int start = pos;

        // Handle negative sign
        if (json.charAt(pos) == '-') {
            pos++;
        }

        // Parse integer part
        while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
            pos++;
        }

        // Parse fractional part
        if (pos < json.length() && json.charAt(pos) == '.') {
            pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }

        // Parse exponent part
        if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
            pos++;
            if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }

        String numberStr = json.substring(start, pos);

        try {
            // Determine if it's an integer or floating point
            if (numberStr.contains(".") || numberStr.contains("e") || numberStr.contains("E")) {
                return Double.parseDouble(numberStr);
            } else {
                long value = Long.parseLong(numberStr);
                // Return Integer if within range, otherwise Long
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return (int) value;
                } else {
                    return value;
                }
            }
        } catch (NumberFormatException e) {
            throw new JsonParsingException("Invalid number: " + numberStr);
        }
    }

    /**
     * Parse the JSON true value
     *
     * @return Boolean.TRUE
     */
    private Boolean parseTrue() {
        if (pos + 3 >= json.length() || !json.substring(pos, pos + 4).equals("true")) {
            throw new JsonParsingException("Expected 'true'");
        }
        pos += 4;
        return Boolean.TRUE;
    }

    /**
     * Parse the JSON false value
     *
     * @return Boolean.FALSE
     */
    private Boolean parseFalse() {
        if (pos + 4 >= json.length() || !json.substring(pos, pos + 5).equals("false")) {
            throw new JsonParsingException("Expected 'false'");
        }
        pos += 5;
        return Boolean.FALSE;
    }

    /**
     * Parse the JSON null value
     *
     * @return null
     */
    private Object parseNull() {
        if (pos + 3 >= json.length() || !json.substring(pos, pos + 4).equals("null")) {
            throw new JsonParsingException("Expected 'null'");
        }
        pos += 4;
        return null;
    }

    /**
     * Skip whitespace characters
     */
    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    /**
     * Serialize a Java object into a JSON string using getter methods
     *
     * @param obj The object to serialize
     * @return The JSON string representation
     */
    public String stringify(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof Map) {
            return stringifyMap((Map<?, ?>) obj);
        } else if (obj instanceof List) {
            return stringifyList((List<?>) obj);
        } else if (obj instanceof Collection) {
            return stringifyList(new ArrayList<>((Collection<?>) obj));
        } else {
            // Use reflection to serialize object based on getter methods
            return stringifyObject(obj);
        }
    }

    /**
     * Serialize an object using its getter methods
     *
     * @param obj The object to serialize
     * @return The JSON object string
     */
    private String stringifyObject(Object obj) {
        Class<?> clazz = obj.getClass();
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        Method[] methods = clazz.getMethods();

        // Map to store getter methods and their corresponding field names
        Map<String, Method> getters = new HashMap<>();

        // Find all getter methods
        for (Method method : methods) {
            if (method.getName().startsWith("get") &&
                    method.getParameterCount() == 0 &&
                    !method.getName().equals("getClass")) {
                String fieldName = method.getName().substring(3);
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                getters.put(fieldName, method);
            } else if (method.getName().startsWith("is") &&
                    method.getParameterCount() == 0 &&
                    method.getReturnType() == boolean.class) {
                String fieldName = method.getName().substring(2);
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                getters.put(fieldName, method);
            }
        }

        // Serialize each field using its getter
        for (Map.Entry<String, Method> entry : getters.entrySet()) {
            String fieldName = entry.getKey();
            Method getter = entry.getValue();

            try {
                Object value = getter.invoke(obj);
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(escapeString(fieldName)).append("\":");
                sb.append(stringify(value));
                first = false;
            } catch (Exception e) {
                // Skip fields that cause exceptions
                continue;
            }
        }

        sb.append("}");
        return sb.toString();
    }


    /**
     * Serialize a Map into a JSON object string
     *
     * @param map The Map to serialize
     * @return The JSON object string
     */
    private String stringifyMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeString(entry.getKey().toString())).append("\":");
            sb.append(stringify(entry.getValue()));
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Serialize a List into a JSON array string
     *
     * @param list The List to serialize
     * @return The JSON array string
     */
    private String stringifyList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(",");
            }
            sb.append(stringify(item));
            first = false;
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Escape special characters in a string
     *
     * @param str The string to escape
     * @return The escaped string
     */
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Custom exception for JSON parsing errors
     */
    public static class JsonParsingException extends RuntimeException {
        public JsonParsingException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        CustomJsonParser parser = new CustomJsonParser();

        // Example JSON string
        String jsonString = """
                [
                  {
                    "id": "IDS60901",
                    "name": "Adelaide (West Terrace / ngayirdapira)",
                    "state": "SA",
                    "time_zone": "CST",
                    "lat": -34.9,
                    "lon": 138.6,
                    "local_date_time": "15/04:00pm",
                    "local_date_time_full": "20230715160000",
                    "air_temp": 13.3,
                    "apparent_t": 9.5,
                    "cloud": "Partly cloudy",
                    "dewpt": 5.7,
                    "press": 1023.9,
                    "rel_hum": 60,
                    "wind_dir": "S",
                    "wind_spd_kmh": 15,
                    "wind_spd_kt": 8
                  }
                ]""";

        try {
            // Parse JSON
            Object parsed = parser.parse(jsonString);
            // Serialize back to JSON
            String serialized = parser.stringify(parsed);
            System.out.println(serialized);
        } catch (CustomJsonParser.JsonParsingException e) {
            System.err.println("JSON parsing error: " + e.getMessage());
        }
    }
}
