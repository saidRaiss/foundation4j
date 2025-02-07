package dev.soffa.foundation.commons;


import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class TextUtil {

    private TextUtil() {
    }

    public static String snakeCase(String input) {
        if (TextUtil.isEmpty(input)) {
            return input;
        }
        String value = input.trim().replaceAll("\\s+", "_");
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : value.toCharArray()) {
            char nc = Character.toLowerCase(c);
            if (Character.isSpaceChar(c)) {
                stringBuilder.append('_');
                continue;
            }
            if (stringBuilder.length() > 0 && Character.isUpperCase(c)) {
                stringBuilder.append('_');
            }
            stringBuilder.append(nc);
        }
        return stringBuilder.toString().replaceAll("_+", "_");
    }

    public static String prefix(String value, boolean test, String ifTrue, String ifFalse) {
        if (test) {
            return prefix(value, ifTrue);
        } else {
            return prefix(value, ifFalse);
        }
    }

    public static String prefix(String value, String... prefixes) {
        String prefix = Arrays.stream(prefixes)
            .filter(TextUtil::isNotEmpty)
            .map(TextUtil::cleanDivider)
            .collect(Collectors.joining("_"));

        if (TextUtil.isEmpty(value)) {
            return prefix;
        }
        if (TextUtil.isNotEmpty(prefix)) {
            prefix += "_";
        }
        return prefix + cleanDivider(value);
    }

    private static String cleanDivider(String input) {
        if (TextUtil.isEmpty(input)) {
            return input;
        }
        return input.replaceAll("^[-_]|[-_]$", "");
    }

    public static boolean isEmpty(String value) {
        return value == null || value.matches("\\s*");
    }

    public static boolean isNotEmpty(String... value) {
        for (String s : value) {
            if (TextUtil.isEmpty(s)) {
                return false;
            }
        }
        return true;
    }

    public static String trimToEmpty(String input) {
        if (input == null) {
            return "";
        }
        return input.trim();
    }

    public static String trimToNull(String input) {
        if (isEmpty(input)) {
            return null;
        }
        return input.trim();
    }

    public static String format(final String pattern, Object... args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        if (pattern.contains("{}") && !pattern.contains("%")) {
            return MessageFormat.format(pattern, args);
        }
        return String.format(pattern, args);
    }

}
