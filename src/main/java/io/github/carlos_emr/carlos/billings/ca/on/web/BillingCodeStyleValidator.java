/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.Locale;
import java.util.Set;

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
        boolean found = false;
        for (String declaration : text.split(";")) {
            if (declaration.isBlank()) continue;
            int colon = declaration.indexOf(':');
            if (colon < 1) return false;
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            boolean supported = switch (property) {
                case "color", "background-color" -> COLORS.contains(value)
                        || value.matches("#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})");
                case "font-size" -> value.matches("xx-small|x-small|small|medium|large|x-large|xx-large|xxx-large|smaller|larger")
                        || value.matches("(?:0|[0-9]{1,3}(?:\\.[0-9]{1,3})?(?:px|pt|em|rem|%))");
                case "font-style" -> value.matches("normal|italic|oblique");
                case "font-variant" -> value.matches("normal|small-caps");
                case "font-weight" -> value.matches("normal|bold|bolder|lighter|[1-9]00");
                case "text-decoration" -> value.matches("none|(?:underline|overline|line-through)(?:\\s+(?:underline|overline|line-through))*");
                default -> false;
            };
            if (!supported) return false;
            found = true;
        }
        return found;
    }
}
