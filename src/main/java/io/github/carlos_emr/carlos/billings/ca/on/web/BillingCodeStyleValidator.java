/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import com.google.common.base.Ascii;
import java.util.Set;
import java.util.HashSet;

/** Validates the presentation declarations supported by the billing style editor without rewriting them. */
final class BillingCodeStyleValidator {
    private static final Set<String> COLORS = Set.of((
            "aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood "
            + "cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgrey "
            + "darkgreen darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue "
            + "darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite "
            + "forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray grey green greenyellow honeydew hotpink indianred indigo ivory "
            + "khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgrey "
            + "lightgreen lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime "
            + "limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue "
            + "mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive "
            + "olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum "
            + "powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue "
            + "slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke "
            + "yellow yellowgreen transparent currentcolor").split(" "));

    private BillingCodeStyleValidator() { }

    /** Accepts complete declarations only; callers retain the original text on both success and error. */
    static boolean isSupported(String text) {
        if (text == null || text.isBlank() || text.length() > 4096) return false;
        // CSS keywords here are ASCII. Do not accept Unicode confusables or control
        // characters that trim/case conversion could hide while the original text is stored.
        if (text.chars().anyMatch(ch -> ch > 0x7e || (ch < 0x20
                && ch != '\t' && ch != '\r' && ch != '\n' && ch != '\f'))) return false;
        boolean found = false;
        for (String declaration : text.split(";")) {
            if (declaration.isBlank()) continue;
            int colon = declaration.indexOf(':');
            if (colon < 1) return false;
            String property = Ascii.toLowerCase(declaration.substring(0, colon).trim());
            String value = Ascii.toLowerCase(declaration.substring(colon + 1).trim());
            boolean supported = switch (property) {
                case "color", "background-color" -> COLORS.contains(value)
                        || value.matches("#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})");
                case "font-size" -> value.matches("xx-small|x-small|small|medium|large|x-large|xx-large|xxx-large|smaller|larger")
                        || value.matches("(?:0|\\d{1,3}(?:\\.\\d{1,3})?(?:px|pt|em|rem|%))");
                case "font-style" -> value.matches("normal|italic|oblique");
                case "font-variant" -> value.matches("normal|small-caps");
                case "font-weight" -> value.matches("normal|bold|bolder|lighter|[1-9]00");
                case "text-decoration" -> isTextDecoration(value);
                default -> false;
            };
            if (!supported) return false;
            found = true;
        }
        return found;
    }

    /** CSS allows each line-decoration keyword only once, or the single keyword none. */
    private static boolean isTextDecoration(String value) {
        if (value.equals("none")) return true;
        Set<String> seen = new HashSet<>();
        for (String word : value.split("\\s+")) {
            if (!Set.of("underline", "overline", "line-through").contains(word) || !seen.add(word)) return false;
        }
        return !seen.isEmpty();
    }
}
