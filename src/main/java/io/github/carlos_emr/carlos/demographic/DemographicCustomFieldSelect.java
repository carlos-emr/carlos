/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

/**
 * Selects a patient's saved value in a configured custom-field {@code <select>} on the Master Record
 * edit form.
 *
 * <p>The select is the administrator's HTML template ({@code demographicExtForm}), printed as it is. The
 * saved value is the text the browser posted, so it is the option's <em>decoded</em> value: an option
 * written {@code value="A &amp; B"} posts {@code A & B}, and an option with no value attribute posts its
 * text. Matching the saved text against the template's source, as the form used to, missed every option
 * whose value holds an entity, so the browser selected the first option and the next unchanged Save
 * overwrote the patient's value (#6271 review).</p>
 *
 * <p>The option tags are read as a browser reads them: attributes in any order and case, quoted with
 * {@code "} or {@code '} or bare, a {@code >} inside a quoted value, line breaks inside the tag; text in a
 * quoted attribute (a {@code title} that says {@code value=...}) is never taken for an attribute, and
 * options inside comments, {@code <script>} or {@code <style>} are not options. Each option's value is
 * decoded as a browser decodes it; without a value attribute it is the option's text, decoded, with
 * whitespace collapsed. The saved text is compared case-sensitively with the exact decoded value,
 * with a whitespace-trimmed fallback for older stored values.</p>
 *
 * <p>The first option whose value equals the saved text is marked {@code selected}, and every other
 * option's {@code selected} is taken off, because a single select keeps the <em>last</em> selected option,
 * so a template default after the saved option would otherwise win. Nothing else in the template changes.
 * When nothing was ever saved ({@code null}: a new patient, or a field added since), the template is
 * printed unchanged, so its own default stands. A value that was saved and then cleared (blank) selects
 * the template's blank option ({@code value=""}) if it has one, so the field reopens cleared and an
 * unchanged Save keeps it cleared; without a blank option the template is printed unchanged. When no
 * option carries the saved value, the template is printed unchanged too. A {@code multiple} select is treated the same
 * way: the field stores one value, so one option is marked. The saved value is only compared, never
 * written into the HTML, a pattern or a replacement string.</p>
 *
 * @since 2026-11-17
 */
public final class DemographicCustomFieldSelect {

    /** One attribute of an option tag: its name and the range it occupies, value included. */
    private record Attribute(String name, int start, int end, int valueStart, int valueEnd) {
    }

    /** One option start tag: where {@code " selected"} goes, its value, and its own selected attributes. */
    private record OptionTag(int insertAt, String value, List<Attribute> selected) {
    }

    /** One change to the template: the range {@code start..end} is replaced by {@code text}. */
    private record Edit(int start, int end, String text) {
    }

    private DemographicCustomFieldSelect() {
    }

    /** Whether the configured template renders a select element. */
    public static boolean containsSelect(String template) {
        return template != null && Jsoup.parseBodyFragment(template).selectFirst("select") != null;
    }

    /** Configured select field names, using the same keys as the Master Record edit. */
    public static Set<String> configuredKeys(String names, String templates) {
        if (names == null || templates == null) {
            return Set.of();
        }
        String[] fields = names.split("\\|", -1);
        String[] forms = templates.split("\\|", -1);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < Math.min(fields.length, forms.length); i++) {
            if (containsSelect(forms[i])) {
                keys.add(fields[i].replace(' ', '_'));
            }
        }
        return keys;
    }

    /**
     * Marks the option whose value is the saved text as the selected one.
     *
     * @param selectHtml the configured select template; {@code null} prints nothing
     * @param saved the patient's saved value: {@code null} when none was ever saved, blank when it was cleared
     * @return the template with that option selected and no other, or the template unchanged when
     *         nothing was ever saved or no option carries the saved value
     */
    public static String markSaved(String selectHtml, String saved) {
        if (selectHtml == null) {
            return "";
        }
        if (saved == null) {
            // Never saved: the template's own default (a later "selected" option, say) must stand. A cleared
            // value is "" (the key is stored), and selects the blank option below.
            return selectHtml;
        }
        String wanted = StringUtils.trimToEmpty(saved);
        List<OptionTag> options = options(selectHtml);
        OptionTag match = null;
        for (OptionTag option : options) {
            if (option.value().equals(saved)) {
                match = option;
                break;
            }
            if (match == null && option.value().trim().equals(wanted)) {
                match = option;
            }
        }
        if (match == null) {
            return selectHtml;
        }

        List<Edit> edits = new ArrayList<>();
        for (OptionTag option : options) {
            for (Attribute selected : option.selected()) {
                // With the whitespace before it, which only ever separates it from what comes before.
                int start = selected.start();
                while (start > 0 && Character.isWhitespace(selectHtml.charAt(start - 1))) {
                    start--;
                }
                edits.add(new Edit(start, selected.end(), ""));
            }
        }
        edits.add(new Edit(match.insertAt(), match.insertAt(), " selected"));
        edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));

        StringBuilder out = new StringBuilder(selectHtml.length() + 16);
        int copied = 0;
        for (Edit edit : edits) {
            out.append(selectHtml, copied, edit.start()).append(edit.text());
            copied = edit.end();
        }
        return out.append(selectHtml, copied, selectHtml.length()).toString();
    }

    /** Every option start tag of the template, in order; reading stops at an unclosed quote or tag. */
    private static List<OptionTag> options(String html) {
        List<OptionTag> options = new ArrayList<>();
        int n = html.length();
        int i = 0;
        while (i < n) {
            int lt = html.indexOf('<', i);
            if (lt < 0) {
                break;
            }
            int skipped = skipNonMarkup(html, lt);
            if (skipped < 0) {
                break;
            }
            if (skipped > lt) {
                i = skipped;
                continue;
            }
            if (!startsTag(html, lt, "option")) {
                i = afterTagOrText(html, lt);
                if (i < 0) {
                    break;
                }
                continue;
            }
            List<Attribute> attributes = new ArrayList<>();
            int close = readAttributes(html, lt + 1 + "option".length(), attributes);
            if (close < 0) {
                break;
            }
            Attribute value = null;
            List<Attribute> selected = new ArrayList<>();
            for (Attribute attribute : attributes) {
                if (value == null && "value".equals(attribute.name())) {
                    value = attribute; // a repeated value attribute: the first wins, as in a browser
                } else if ("selected".equals(attribute.name())) {
                    selected.add(attribute);
                }
            }
            if (value != null) {
                String raw = value.valueStart() < 0 ? "" : html.substring(value.valueStart(), value.valueEnd());
                options.add(new OptionTag(value.end(), Parser.unescapeEntities(raw, true), selected));
            } else {
                int insertAt = html.charAt(close - 1) == '/' ? close - 1 : close;
                options.add(new OptionTag(insertAt, optionText(html, close + 1), selected));
            }
            i = close + 1;
        }
        return options;
    }

    /**
     * Reads the attributes of a start tag from {@code p} up to its closing {@code >}.
     *
     * @return the index of the closing {@code >}, or -1 for an unclosed quote or tag
     */
    private static int readAttributes(String html, int p, List<Attribute> attributes) {
        int n = html.length();
        while (p < n) {
            char c = html.charAt(p);
            if (c == '>') {
                return p;
            }
            if (Character.isWhitespace(c) || c == '/') {
                p++;
                continue;
            }
            int nameStart = p;
            while (p < n && !Character.isWhitespace(html.charAt(p)) && "=>/".indexOf(html.charAt(p)) < 0) {
                p++;
            }
            if (p == nameStart) {
                p++; // a stray "=": move on
                continue;
            }
            int nameEnd = p;
            int q = p;
            while (q < n && Character.isWhitespace(html.charAt(q))) {
                q++;
            }
            int valueStart = -1;
            int valueEnd = -1;
            if (q < n && html.charAt(q) == '=') {
                q++;
                while (q < n && Character.isWhitespace(html.charAt(q))) {
                    q++;
                }
                if (q < n && (html.charAt(q) == '"' || html.charAt(q) == '\'')) {
                    int closeQuote = html.indexOf(html.charAt(q), q + 1);
                    if (closeQuote < 0) {
                        return -1;
                    }
                    valueStart = q + 1;
                    valueEnd = closeQuote;
                    p = closeQuote + 1;
                } else {
                    valueStart = q;
                    while (q < n && !Character.isWhitespace(html.charAt(q)) && html.charAt(q) != '>') {
                        q++;
                    }
                    valueEnd = q;
                    p = q;
                }
            }
            if (attributes != null) {
                String name = html.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);
                attributes.add(new Attribute(name, nameStart, p, valueStart, valueEnd));
            }
        }
        return -1;
    }

    /**
     * The value a browser posts for an option without a value attribute: its text up to the next
     * option, optgroup or end of the select, tags and comments left out, decoded, with ASCII whitespace
     * collapsed and trimmed.
     */
    private static String optionText(String html, int from) {
        StringBuilder text = new StringBuilder();
        int n = html.length();
        int i = from;
        while (i < n) {
            int lt = html.indexOf('<', i);
            if (lt < 0) {
                text.append(html, i, n);
                break;
            }
            text.append(html, i, lt);
            if (startsTag(html, lt, "option") || startsTag(html, lt, "/option") || startsTag(html, lt, "optgroup")
                    || startsTag(html, lt, "/optgroup") || startsTag(html, lt, "/select")) {
                break;
            }
            int skipped = skipNonMarkup(html, lt);
            if (skipped < 0) {
                break;
            }
            if (skipped > lt) {
                i = skipped;
                continue;
            }
            i = afterTagOrText(html, lt);
            if (i < 0) {
                break;
            }
            if (i == lt + 1) {
                text.append('<');
            }
        }
        String decoded = Parser.unescapeEntities(text.toString(), false);
        // HTML's own whitespace (tab, line feed, form feed, carriage return, space); a no-break space stays.
        return StringUtils.strip(decoded.replaceAll("[\\t\\n\\f\\r ]+", " "), " ");
    }

    /** Advances over a whole tag, or one literal less-than sign in text. */
    private static int afterTagOrText(String html, int lt) {
        int name = lt + 1;
        if (name < html.length() && html.charAt(name) == '/') {
            name++;
        }
        if (name >= html.length() || !isAsciiLetter(html.charAt(name))) {
            return lt + 1;
        }
        int close = readAttributes(html, name, null);
        return close < 0 ? -1 : close + 1;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Skips a comment, a {@code <!...>} or {@code <?...>} declaration, or an element whose body is text,
     * not markup ({@code script}, {@code style}, {@code textarea}, {@code title}, {@code noscript}),
     * starting at {@code lt}.
     *
     * @return the index after it, {@code lt} when it is none of these, or -1 when it is not closed
     */
    private static int skipNonMarkup(String html, int lt) {
        if (html.startsWith("<!-->", lt)) {
            return lt + 5; // an empty comment, closed at once
        }
        if (html.startsWith("<!--->", lt)) {
            return lt + 6;
        }
        if (html.startsWith("<!--", lt)) {
            int end = html.indexOf("-->", lt + 4);
            return end < 0 ? -1 : end + 3;
        }
        if (html.startsWith("<!", lt) || html.startsWith("<?", lt)) {
            int end = html.indexOf('>', lt + 2);
            return end < 0 ? -1 : end + 1;
        }
        for (String rawText : new String[]{"script", "style", "textarea", "title", "noscript"}) {
            if (startsTag(html, lt, rawText)) {
                int end = indexOfIgnoreCase(html, "</" + rawText, lt + 1);
                int closeTag = end < 0 ? -1 : html.indexOf('>', end);
                return closeTag < 0 ? -1 : closeTag + 1;
            }
        }
        return lt;
    }

    /** Whether a tag named {@code name} (any case) starts at {@code lt}. */
    private static boolean startsTag(String html, int lt, String name) {
        int after = lt + 1 + name.length();
        if (!html.regionMatches(true, lt + 1, name, 0, name.length()) || after > html.length()) {
            return false;
        }
        if (after == html.length()) {
            return false;
        }
        char next = html.charAt(after);
        return Character.isWhitespace(next) || next == '>' || next == '/';
    }

    private static int indexOfIgnoreCase(String html, String part, int from) {
        for (int at = from; at + part.length() <= html.length(); at++) {
            if (html.regionMatches(true, at, part, 0, part.length())) {
                return at;
            }
        }
        return -1;
    }
}
