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
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeFilter.FilterResult;

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
 * options outside the rendered select, including inside inert templates, are ignored. Each option's value is
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
 * unchanged Save keeps it cleared. A cleared {@code multiple} select without a blank option reopens
 * with no selection. Otherwise, when no option carries the saved value, the template is printed
 * unchanged. The field stores one value, so at most one option is marked. The saved value is only
 * compared, never written into the HTML, a pattern or a replacement string.</p>
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
        return template != null && renderedSelect(template) != null;
    }

    private static Element renderedSelect(String template) {
        // Use the parsed control and original source positions so markup outside it stays untouched.
        return Parser.htmlParser().setTrackPosition(true).parseInput(template, "").select("select")
                .stream().filter(select -> select.closest("template") == null).findFirst().orElse(null);
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
     *         nothing was ever saved or no option carries the saved value; a cleared multiple
     *         select without a blank option has no selected option
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
        Element select = renderedSelect(selectHtml);
        if (select == null) {
            return selectHtml;
        }
        String wanted = StringUtils.trimToEmpty(saved);
        List<OptionTag> options = options(selectHtml, select);
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
        if (match == null && !(select.hasAttr("multiple") && wanted.isEmpty())) {
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
        if (match != null) {
            edits.add(new Edit(match.insertAt(), match.insertAt(), " selected"));
        }
        edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));

        StringBuilder out = new StringBuilder(selectHtml.length() + 16);
        int copied = 0;
        for (Edit edit : edits) {
            out.append(selectHtml, copied, edit.start()).append(edit.text());
            copied = edit.end();
        }
        return out.append(selectHtml, copied, selectHtml.length()).toString();
    }

    /** The rendered options and their attribute ranges in the original template. */
    private static List<OptionTag> options(String html, Element select) {
        List<OptionTag> options = new ArrayList<>();
        for (Element option : select.select("option")) {
            if (option.closest("template") != null) {
                continue;
            }
            int lt = option.sourceRange().start().pos();
            List<Attribute> attributes = new ArrayList<>();
            int close = readAttributes(html, lt + 1 + "option".length(), attributes);
            if (close < 0) {
                continue;
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
                options.add(new OptionTag(insertAt, optionText(option), selected));
            }
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
            // HTML attribute names fold only the ASCII letters.
            StringBuilder name = new StringBuilder(nameEnd - nameStart);
            for (int i = nameStart; i < nameEnd; i++) {
                char letter = html.charAt(i);
                name.append(letter >= 'A' && letter <= 'Z' ? (char) (letter + 'a' - 'A') : letter);
            }
            attributes.add(new Attribute(name.toString(), nameStart, p, valueStart, valueEnd));
        }
        return -1;
    }

    /** Collects the text a browser posts for an option with no value attribute. */
    private static String optionText(Element option) {
        StringBuilder text = new StringBuilder();
        option.filter((node, depth) -> {
            if (node.nameIs("template")) {
                return FilterResult.SKIP_ENTIRELY;
            }
            if (node instanceof TextNode content) {
                text.append(content.getWholeText());
            }
            return FilterResult.CONTINUE;
        });
        // HTML collapses its ASCII whitespace. A no-break space remains part of the value.
        return StringUtils.strip(text.toString().replaceAll("[\\t\\n\\f\\r ]+", " "), " ");
    }
}
