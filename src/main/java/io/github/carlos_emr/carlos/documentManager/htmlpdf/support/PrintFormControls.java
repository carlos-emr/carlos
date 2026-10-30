/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Replaces form controls with static text and marks for printing (step c of the render pipeline).
 *
 * <p>Flying Saucer draws form controls as interactive PDF form fields, which ConcatPDF and the
 * Letter placement cannot carry (the fields must be flattened) and which look nothing like the page
 * a browser prints. A printed record needs the values: this step turns each control into a
 * {@code <span>} or {@code <div>} that holds what the control shows, so the text is ordinary text.
 * Right-to-left shaping and the fallback-font step therefore apply to it, and it can be selected and
 * searched in the PDF.</p>
 *
 * <table>
 *   <caption>What each control becomes</caption>
 *   <tr><th>Original</th><th>Replacement</th></tr>
 *   <tr><td>{@code <input>} of type text, email, tel, number, date, time, datetime-local, month, week,
 *       url, search, color, an unknown type, or no type</td>
 *       <td>{@code <span>} (kind {@code text}) with the {@code value}, CR and LF removed</td></tr>
 *   <tr><td>{@code type="checkbox"}</td><td>{@code <span>} (kind {@code checkbox}): U+2612 if
 *       {@code checked} is present, else U+2610</td></tr>
 *   <tr><td>{@code type="radio"}</td><td>{@code <span>} (kind {@code radio}): U+25C9 if {@code checked}
 *       is present, else U+25CB</td></tr>
 *   <tr><td>{@code type="password"}</td><td>{@code <span>} (kind {@code password}): eight U+2022 when
 *       the value is not empty, else nothing; the length is never revealed</td></tr>
 *   <tr><td>{@code type} hidden, submit, button, reset, image, file; {@code <button>}</td>
 *       <td>removed</td></tr>
 *   <tr><td>{@code <select>}</td><td>{@code <span>} (kind {@code select}): the selected option, or all
 *       selected options joined by ", " for {@code multiple}; none selected gives the first option
 *       (single) or nothing (multiple); options inside {@code <optgroup>} count</td></tr>
 *   <tr><td>{@code <textarea>}</td><td>{@code <div>} (kind {@code textarea}), newlines kept</td></tr>
 *   <tr><td>{@code <progress>}</td><td>{@code <span>} (kind {@code progress}) holding a bar: the track is
 *       filled to {@code value/max}, with {@code max} defaulting to 1; the track alone when there is no
 *       value</td></tr>
 *   <tr><td>{@code <meter>}</td><td>{@code <span>} (kind {@code meter}) holding a bar filled to
 *       {@code (value - min) / (max - min)}, in the colour a browser gives it</td></tr>
 * </table>
 *
 * <p>The bar of a {@code <progress>} and of a {@code <meter>} is two nested spans, the track and the part
 * of it that is filled ({@code carlos-pdf-gauge-fill}, with a percentage width in its style), so that
 * the page shows what a browser shows. The {@code PrintStylesheet} draws the track 10em wide for a
 * progress and 5em for a meter, as browsers do, and an inline {@code width} or {@code height} on the
 * control, which the replacement keeps, sizes it. A meter's fill is {@code carlos-pdf-gauge-optimum},
 * {@code carlos-pdf-gauge-suboptimal} or {@code carlos-pdf-gauge-poor} by where the value lies among the
 * {@code low}, {@code high} and {@code optimum} points, by the rule Chromium applies. An attribute that
 * is not a finite number is taken as absent, so {@code value="abc"} gives an indeterminate progress, and
 * the content of the element (the text a browser without support would show) is not printed.</p>
 *
 * <p>Each replacement copies the original's {@code id}, {@code class}, {@code style}, {@code title},
 * {@code dir} and {@code lang}, adds the classes {@code carlos-pdf-control carlos-pdf-<kind>} and
 * {@code data-input-type="<type>"}, and takes the original's place, so document order is kept. No
 * other attribute is copied: a control's {@code name} and {@code value}, and everything of a hidden
 * field, stay out of the printed document.</p>
 *
 * <p>{@code checked} and {@code selected} follow the HTML rule for boolean attributes: present means
 * on, whatever the value ({@code checked="false"} is checked, as in a browser). Attribute and type
 * names are compared case-insensitively, so legacy upper-case markup prints the same as lower-case.</p>
 *
 * <p>Sizing: a text-like input gets {@code min-width: size * 0.55em} (default size 20) and a textarea
 * {@code min-width: cols * 0.55em; min-height: rows * 1.2em} (defaults 20 and 2), unless its own
 * inline style sets the width or height. The replacement grows to fit its content, so a value is
 * never clipped.</p>
 *
 * @since 2026-10-29
 */
public final class PrintFormControls {

    /** U+2612 BALLOT BOX WITH X: a checked checkbox. */
    static final String CHECKED_BOX = new String(Character.toChars(0x2612));
    /** U+2610 BALLOT BOX: an unchecked checkbox. */
    static final String UNCHECKED_BOX = new String(Character.toChars(0x2610));
    /** U+25C9 FISHEYE: a selected radio button. */
    static final String SELECTED_RADIO = new String(Character.toChars(0x25C9));
    /** U+25CB WHITE CIRCLE: an unselected radio button. */
    static final String UNSELECTED_RADIO = new String(Character.toChars(0x25CB));
    /** U+2022 BULLET, eight of which stand for a password of any length. */
    static final String PASSWORD_MASK = new String(Character.toChars(0x2022)).repeat(8);

    /** The part of a progress or meter track that is filled; a meter's fill also carries one of the three colour classes. */
    static final String GAUGE_FILL = "carlos-pdf-gauge-fill";
    static final String GAUGE_OPTIMUM = "carlos-pdf-gauge-optimum";
    static final String GAUGE_SUBOPTIMAL = "carlos-pdf-gauge-suboptimal";
    static final String GAUGE_POOR = "carlos-pdf-gauge-poor";

    /** The attributes of the original that the replacement keeps. */
    private static final List<String> COPIED_ATTRIBUTES = List.of("id", "class", "style", "title", "dir", "lang");

    /** HTML default {@code size} of a text input. */
    private static final int DEFAULT_SIZE = 20;
    /** HTML default {@code cols} and {@code rows} of a textarea. */
    private static final int DEFAULT_COLUMNS = 20;
    private static final int DEFAULT_ROWS = 2;

    /**
     * Upper bounds on the minimum sizes. A {@code size}, {@code cols} or {@code rows} beyond them is
     * never meaningful on a printed page, and an unbounded minimum width would widen the whole layout
     * (and shrink the zoom) for one broken attribute.
     */
    static final int MAX_SIZE = 200;
    static final int MAX_ROWS = 100;

    /** Average character width, in em, of the face used for printing (a text input of size N is N * 0.55em wide). */
    private static final BigDecimal EM_PER_COLUMN = new BigDecimal("0.55");
    private static final BigDecimal EM_PER_ROW = new BigDecimal("1.2");

    private PrintFormControls() {
    }

    /**
     * Whether the text is a decimal number, as {@code <progress>} and {@code <meter>} attributes must be: an optional
     * sign, digits with an optional fraction or a fraction alone, and an optional exponent. A scan, not
     * a regular expression, so that no attribute value can make it backtrack.
     */
    static boolean isDecimalNumber(String text) {
        int length = text.length();
        int i = 0;
        if (i < length && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        int whole = i;
        while (i < length && isDigit(text.charAt(i))) {
            i++;
        }
        boolean hasWhole = i > whole;
        if (i < length && text.charAt(i) == '.') {
            i++;
            int fraction = i;
            while (i < length && isDigit(text.charAt(i))) {
                i++;
            }
            if (!hasWhole && i == fraction) {
                return false;
            }
        } else if (!hasWhole) {
            return false;
        }
        if (i < length && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < length && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            int exponent = i;
            while (i < length && isDigit(text.charAt(i))) {
                i++;
            }
            if (i == exponent) {
                return false;
            }
        }
        return i == length;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Replaces every form control of the document, in document order.
     *
     * @param document the document to change
     */
    public static void replaceControls(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element control : document.select("input, button, select, textarea, progress, meter")) {
            switch (control.normalName()) {
                case "input" -> replaceInput(control);
                case "button" -> control.remove();
                case "select" -> replace(control, "span", "select", "select", selectedOptionsText(control), null);
                case "textarea" -> replaceTextarea(control);
                case "progress" -> replaceProgress(control);
                case "meter" -> replaceMeter(control);
                default -> { }
            }
        }
    }

    // ---- input ------------------------------------------------------------------------------

    // FindSecBugs IMPROPER_UNICODE: case-fold of an HTML input type (ASCII keywords); selects a rendering, not a security decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of an HTML input type keyword; selects a rendering, not a security decision")
    private static void replaceInput(Element input) {
        String type = input.attr("type").toLowerCase(Locale.ROOT);
        switch (type) {
            case "hidden", "submit", "button", "reset", "image", "file" -> input.remove();
            case "checkbox" -> replace(input, "span", "checkbox", "checkbox",
                    input.hasAttr("checked") ? CHECKED_BOX : UNCHECKED_BOX, null);
            case "radio" -> replace(input, "span", "radio", "radio",
                    input.hasAttr("checked") ? SELECTED_RADIO : UNSELECTED_RADIO, null);
            case "password" -> replace(input, "span", "password", "password",
                    input.attr("value").isEmpty() ? "" : PASSWORD_MASK, minimumWidthOf(input));
            case "email", "tel", "number", "date", "time", "datetime-local", "month", "week", "url", "search", "color" ->
                    replace(input, "span", "text", type, withoutLineBreaks(input.attr("value")), minimumWidthOf(input));
            default -> replace(input, "span", "text", "text", withoutLineBreaks(input.attr("value")),
                    minimumWidthOf(input));
        }
    }

    private static String withoutLineBreaks(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    /** {@code min-width: size * 0.55em}, or {@code null} when the input's own style sets a width. */
    private static String minimumWidthOf(Element input) {
        if (InlineStyle.setsWidth(input.attr("style"))) {
            return null;
        }
        return "min-width: " + em(EM_PER_COLUMN, positive(input.attr("size"), DEFAULT_SIZE, MAX_SIZE));
    }

    // ---- select -----------------------------------------------------------------------------

    private static String selectedOptionsText(Element select) {
        List<Element> options = select.select("option");
        List<Element> selected = new ArrayList<>();
        for (Element option : options) {
            if (option.hasAttr("selected")) {
                selected.add(option);
            }
        }
        if (select.hasAttr("multiple")) {
            List<String> labels = new ArrayList<>();
            for (Element option : selected) {
                labels.add(labelOf(option));
            }
            return String.join(", ", labels);
        }
        if (!selected.isEmpty()) {
            // A browser keeps the last selected option of a single-selection list.
            return labelOf(selected.get(selected.size() - 1));
        }
        return options.isEmpty() ? "" : labelOf(options.get(0));
    }

    /** What a browser shows for an option: its {@code label} if it has a non-empty one, else its text. */
    private static String labelOf(Element option) {
        String label = option.attr("label");
        return label.isBlank() ? option.text() : label;
    }

    // ---- textarea ---------------------------------------------------------------------------

    private static void replaceTextarea(Element textarea) {
        String text = textarea.wholeText().replace("\r\n", "\n").replace('\r', '\n');
        String style = textarea.attr("style");
        List<String> minimums = new ArrayList<>();
        if (!InlineStyle.setsWidth(style)) {
            minimums.add("min-width: " + em(EM_PER_COLUMN, positive(textarea.attr("cols"), DEFAULT_COLUMNS, MAX_SIZE)));
        }
        if (!InlineStyle.setsHeight(style)) {
            minimums.add("min-height: " + em(EM_PER_ROW, positive(textarea.attr("rows"), DEFAULT_ROWS, MAX_ROWS)));
        }
        replace(textarea, "div", "textarea", "textarea", text, minimums.isEmpty() ? null : String.join("; ", minimums));
    }

    // ---- progress and meter -----------------------------------------------------------------

    /** A bar with no value, or with a value that is not a number, is an indeterminate progress: the track alone. */
    private static void replaceProgress(Element progress) {
        OptionalDouble value = number(progress, "value");
        if (value.isEmpty()) {
            replaceGauge(progress, "progress", null, null);
            return;
        }
        double max = number(progress, "max").orElse(1);
        // value / max is never NaN: both are finite and max is positive; it may be infinite, which clamps to 1.
        replaceGauge(progress, "progress", clamp(value.getAsDouble() / (max > 0 ? max : 1), 0, 1), null);
    }

    /**
     * The HTML defaults and corrections for a meter ({@code min} 0, {@code max} 1 and not below {@code min}, the value
     * and the points held within the range, {@code high} not below {@code low}, {@code optimum} in the middle).
     */
    private static void replaceMeter(Element meter) {
        double min = number(meter, "min").orElse(0);
        double max = Math.max(min, number(meter, "max").orElse(1));
        double value = clamp(number(meter, "value").orElse(0), min, max);
        double low = clamp(number(meter, "low").orElse(min), min, max);
        double high = clamp(number(meter, "high").orElse(max), low, max);
        double optimum = clamp(number(meter, "optimum").orElse(min + (max - min) / 2), min, max);
        double span = max - min;
        // A range wider than a double can hold, or none at all, has no position to show (as in Chromium).
        double fraction = span > 0 && Double.isFinite(span) ? (value - min) / span : 0;
        replaceGauge(meter, "meter", fraction, meterLevel(value, low, high, optimum));
    }

    /**
     * Which part of a meter's range its value lies in, by the rule Chromium applies: the optimum lies under
     * {@code low}, over {@code high}, or between them, and the value is as good as it is near that region.
     */
    private static String meterLevel(double value, double low, double high, double optimum) {
        if (optimum < low) {
            if (value <= low) {
                return GAUGE_OPTIMUM;
            }
            return value <= high ? GAUGE_SUBOPTIMAL : GAUGE_POOR;
        }
        if (high < optimum) {
            if (high <= value) {
                return GAUGE_OPTIMUM;
            }
            return low <= value ? GAUGE_SUBOPTIMAL : GAUGE_POOR;
        }
        return low <= value && value <= high ? GAUGE_OPTIMUM : GAUGE_SUBOPTIMAL;
    }

    /** A numeric attribute; absent, not a number and too large for a double all read as absent. */
    private static OptionalDouble number(Element element, String attribute) {
        String text = element.attr(attribute).strip();
        if (!isDecimalNumber(text)) {
            return OptionalDouble.empty();
        }
        double number = Double.parseDouble(text);
        return Double.isFinite(number) ? OptionalDouble.of(number) : OptionalDouble.empty();
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * The track of a bar, with its fill when there is a position to show.
     *
     * @param fraction how much of the track is filled, 0 to 1, or {@code null} for the track alone
     * @param level the colour class of the fill, or {@code null} for the default one
     */
    private static void replaceGauge(Element control, String kind, Double fraction, String level) {
        Element replacement = replacementFor(control, "span", kind, kind, null);
        if (fraction != null) {
            Element fill = replacement.appendElement("span").addClass(GAUGE_FILL);
            if (level != null) {
                fill.addClass(level);
            }
            fill.attr("style", "width: " + percent(fraction));
        }
        control.replaceWith(replacement);
    }

    /** A percentage to a tenth of a percent, written with a decimal point whatever the locale and never in exponent form. */
    private static String percent(double fraction) {
        return BigDecimal.valueOf(fraction * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + "%";
    }

    // ---- replacement ------------------------------------------------------------------------

    /**
     * Builds the replacement and puts it where the control was.
     *
     * @param kind the {@code carlos-pdf-<kind>} class
     * @param inputType the {@code data-input-type} value
     * @param text what the control shows, as a text node
     * @param minimumSize a declaration (or several) to add to the style, or {@code null}
     */
    private static void replace(Element control, String tag, String kind, String inputType, String text,
            String minimumSize) {
        Element replacement = replacementFor(control, tag, kind, inputType, minimumSize);
        if (!text.isEmpty()) {
            replacement.appendText(text);
        }
        control.replaceWith(replacement);
    }

    /** The replacement element, with the original's copied attributes, its classes and its minimum size, not yet placed. */
    private static Element replacementFor(Element control, String tag, String kind, String inputType,
            String minimumSize) {
        Element replacement = new Element(tag);
        for (String name : COPIED_ATTRIBUTES) {
            if (control.hasAttr(name)) {
                replacement.attr(name, control.attr(name));
            }
        }
        replacement.addClass("carlos-pdf-control").addClass("carlos-pdf-" + kind);
        replacement.attr("data-input-type", inputType);
        if (minimumSize != null) {
            replacement.attr("style", InlineStyle.append(replacement.attr("style"), minimumSize));
        }
        return replacement;
    }

    /** A positive integer attribute, or the default when it is absent, zero or not a number; never above the cap. */
    private static int positive(String value, int fallback, int cap) {
        long number = HtmlAttributeNumbers.leadingNumber(value);
        if (number <= 0) {
            return fallback;
        }
        return (int) Math.min(number, cap);
    }

    private static String em(BigDecimal perUnit, int units) {
        return perUnit.multiply(BigDecimal.valueOf(units)).stripTrailingZeros().toPlainString() + "em";
    }
}
