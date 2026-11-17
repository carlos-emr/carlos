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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.parser.Parser;

/**
 * Selects a patient's saved value in a configured custom-field {@code <select>} on the Master Record
 * edit form.
 *
 * <p>The select is the administrator's HTML template ({@code demographicExtForm}), printed as it is. The
 * saved value is the text the browser posted, so it is the option's <em>decoded</em> value: an option
 * written {@code value="A &amp; B"} posts {@code A & B}. Matching the saved text against the template's
 * source, as the form used to, missed every option whose value holds an entity, so the browser selected
 * the first option and the next unchanged Save overwrote the patient's value (#6271 review).</p>
 *
 * <p>Here each option's value is decoded as a browser decodes an attribute, and the first option whose
 * decoded value equals the saved text is marked {@code selected}. Every other option's {@code selected}
 * is taken off, because a single select keeps the <em>last</em> selected option, so a template default
 * after the saved option would otherwise win. Nothing else in the template changes. The saved value is
 * only compared, never written into the HTML, a pattern or a replacement string, so quotes, entities
 * and regular-expression characters in it change nothing.</p>
 *
 * <p>A saved value no option carries leaves the template unchanged, as before.</p>
 *
 * @since 2026-11-17
 */
public final class DemographicCustomFieldSelect {

    /** An option start tag; quoted attribute values may hold {@code >}. */
    private static final Pattern OPTION_TAG =
            Pattern.compile("<option\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", Pattern.CASE_INSENSITIVE);
    /** The value attribute of an option tag: double-quoted, single-quoted or bare. */
    private static final Pattern VALUE_ATTRIBUTE =
            Pattern.compile("\\svalue\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>\"']+))", Pattern.CASE_INSENSITIVE);
    /**
     * A quoted attribute value (group 1, kept as it is) or a {@code selected} attribute, with or without
     * a value (removed): matching quoted values first keeps the word inside one, such as a label, intact.
     */
    private static final Pattern SELECTED_ATTRIBUTE = Pattern.compile(
            "(\"[^\"]*\"|'[^']*')|\\sselected(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>\"']+))?(?=[\\s/>]|$)",
            Pattern.CASE_INSENSITIVE);

    private DemographicCustomFieldSelect() {
    }

    /**
     * Marks the option whose decoded value is the saved text as the selected one.
     *
     * @param selectHtml the configured select template; {@code null} prints nothing
     * @param saved the patient's saved value; {@code null} and surrounding blanks are read as the form
     *              always read them ({@link StringUtils#trimToEmpty})
     * @return the template with that option selected and no other, or the template unchanged when no
     *         option carries the saved value
     */
    public static String markSaved(String selectHtml, String saved) {
        if (selectHtml == null) {
            return "";
        }
        String wanted = StringUtils.trimToEmpty(saved);
        Matcher option = OPTION_TAG.matcher(selectHtml);
        int matchStart = -1;
        int matchEnd = -1;
        int valueEnd = -1;
        while (option.find()) {
            Matcher value = VALUE_ATTRIBUTE.matcher(option.group());
            if (value.find() && decodedValue(value).equals(wanted)) {
                matchStart = option.start();
                matchEnd = option.end();
                valueEnd = option.start() + value.end();
                break;
            }
        }
        if (matchStart < 0) {
            return selectHtml;
        }

        StringBuilder out = new StringBuilder(selectHtml.length() + 16);
        int copied = 0;
        option.reset();
        while (option.find()) {
            out.append(selectHtml, copied, option.start());
            if (option.start() == matchStart) {
                // The saved option: its own selected attribute (if any) off, then one after its value.
                String before = selectHtml.substring(matchStart, valueEnd);
                String after = selectHtml.substring(valueEnd, matchEnd);
                out.append(withoutSelected(before)).append(" selected").append(withoutSelected(after));
            } else {
                out.append(withoutSelected(option.group()));
            }
            copied = option.end();
        }
        out.append(selectHtml, copied, selectHtml.length());
        return out.toString();
    }

    /** The option value as the browser posts it: entities decoded, as in an attribute. */
    private static String decodedValue(Matcher value) {
        String raw = value.group(1) != null ? value.group(1)
                : value.group(2) != null ? value.group(2) : value.group(3);
        return Parser.unescapeEntities(raw, true);
    }

    private static String withoutSelected(String tagPart) {
        Matcher attribute = SELECTED_ATTRIBUTE.matcher(tagPart);
        StringBuilder out = new StringBuilder(tagPart.length());
        int copied = 0;
        while (attribute.find()) {
            if (attribute.group(1) != null) {
                continue;
            }
            out.append(tagPart, copied, attribute.start());
            copied = attribute.end();
        }
        return out.append(tagPart, copied, tagPart.length()).toString();
    }
}
