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
package io.github.carlos_emr.carlos.eform.data;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Chooses and reads the option of a {@code <select>} in an eForm template, with an HTML parser rather than
 * string searches.
 *
 * <p>An eForm select filled from a database tag ({@code oscarDB=...}) must show, and submit, the option a
 * browser would submit as exactly the database output. Where an option is, what it submits and which select
 * owns it are the HTML parser's answers, not a text search's: an end tag written {@code </select >}, a
 * {@code </select>} or an option-looking string inside a comment, a {@code script}, a {@code style}, an
 * attribute or an {@code optgroup} label, an option inside inert {@code template} content, text on both sides
 * of a comment in an option, and a bare {@code value} attribute are all read as a browser reads them. The
 * parser reports where each tag starts, so the edit is still a minimal change to the template's own text:
 * {@code " selected"} put in after {@code <option}, and the {@code selected} attribute taken out of the other
 * options of the same single select. Nothing else in the markup is touched.</p>
 *
 * <p>Anything that cannot be resolved with certainty (the select is not found, a start tag cannot be read)
 * changes nothing: the template's own default stays.</p>
 */
final class EFormSelectMarkup {

    /** Length of {@code "<option"}. */
    private static final int OPTION_OPEN = 7;
    private static final String VALUE_ATTRIBUTE = "value";

    private EFormSelectMarkup() {
    }

    /** Where {@code " selected"} goes and which {@code selected} attributes of the other options come out. */
    static final class SelectEdit {
        final int insertAt;
        /** Ascending, non-overlapping {@code [start, end)} ranges, none inside the matched option's tag. */
        final List<int[]> removals = new ArrayList<>();

        private SelectEdit(int insertAt) {
            this.insertAt = insertAt;
        }

        /** Applies the removals and the insert, from the end of the markup backwards so positions stay valid. */
        void applyTo(StringBuilder html) {
            boolean inserted = false;
            for (int r = removals.size() - 1; r >= 0; r--) {
                int[] range = removals.get(r);
                if (!inserted && range[0] < insertAt) {
                    html.insert(insertAt, " selected");
                    inserted = true;
                }
                html.delete(range[0], range[1]);
            }
            if (!inserted) html.insert(insertAt, " selected");
        }
    }

    /**
     * The edit that makes the select whose start tag holds {@code pointer} show the option a browser would
     * submit as exactly {@code wanted}, or null when the select is not found or no option matches (the template
     * is then left as it is).
     *
     * <p>Candidates are the options the select owns (its own, and those in its option groups; never an option in
     * a nested select or in {@code template} content). The wanted text is compared with each option's submitted
     * value (its {@code value} attribute, which may be a bare attribute and then submits an empty string, or its
     * text with character references decoded and white space collapsed). The whole value is compared, never a
     * prefix, and nothing is built into a pattern from it. An <em>exact</em> submitted value anywhere in the
     * select wins over every compatibility match; only when no option submits exactly the wanted text is it
     * compared without regard to case, and then against the option's raw attribute spelling (a template value
     * that literally holds {@code Smith &amp; Sons} matches that same text in the database, as it did before
     * character references were decoded). An EMPTY {@code wanted} marks the first option that has a
     * {@code value} attribute, whatever it holds (kept for compatibility with the earlier text search for
     * {@code " value="}; not a design).</p>
     *
     * <p>In a single select the {@code selected} attribute is taken out of every OTHER option of that select
     * (a single select shows the last {@code selected} option, so a template default after the stored value's
     * option would hide it); a {@code multiple} select and an empty {@code wanted} keep the others as they are.</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision")
    static SelectEdit edit(CharSequence html, int pointer, String wanted) {
        if (wanted == null) return null;
        String source = html.toString();
        Element select = selectHolding(parse(source), pointer);
        if (select == null || hasStyleInside(select, source)) return null;
        List<Element> options = ownedOptions(select);
        Element chosen = chosenOption(options, source, wanted);
        if (chosen == null) return null;
        Integer start = tagStart(chosen);
        if (start == null) return null;
        SelectEdit edit = new SelectEdit(start + OPTION_OPEN);
        boolean collect = !wanted.isEmpty() && !select.hasAttr("multiple");
        if (!collect) return edit;
        List<int[]> removals = new ArrayList<>();
        for (Element option : options) {
            if (option == chosen) continue;
            Integer otherStart = tagStart(option);
            TagAttributes tag = otherStart == null ? null : readTagAttributes(source, otherStart + OPTION_OPEN);
            if (tag == null) return edit; // a tag that cannot be read: only the insert, never a guess
            removals.addAll(tag.selectedRanges);
        }
        removals.sort((a, b) -> Integer.compare(a[0], b[0]));
        edit.removals.addAll(removals);
        return edit;
    }

    /**
     * The value a browser submits for the option of the select whose start tag holds {@code pointer} that is
     * shown as selected (the last {@code selected} option of a single select, the first of one with
     * {@code multiple}), or null when none is marked selected or the select is not found. Used to carry the
     * submitted value to the database tags that depend on the field.
     */
    static String selectedValue(CharSequence html, int pointer) {
        String source = html.toString();
        Element select = selectHolding(parse(source), pointer);
        if (select == null || hasStyleInside(select, source)) return null;
        Element shown = null;
        for (Element option : ownedOptions(select)) {
            if (!option.hasAttr("selected")) continue;
            shown = option;
            if (select.hasAttr("multiple")) break;
        }
        return shown == null ? null : submittedValue(shown);
    }

    /**
     * The value a browser submits for an option: its {@code value} attribute when it has one (a bare
     * {@code value} is an explicit empty string), otherwise its text with character references decoded and
     * white space stripped and collapsed. The parser leaves a comment's text out and joins the text on both
     * sides of it.
     */
    static String submittedValue(Element option) {
        return option.hasAttr(VALUE_ATTRIBUTE) ? option.attr(VALUE_ATTRIBUTE) : option.text();
    }

    private static Document parse(String html) {
        return Parser.htmlParser().setTrackPosition(true).parseInput(html, "");
    }

    /** The select whose start tag contains {@code pointer}, or null. */
    private static Element selectHolding(Document document, int pointer) {
        for (Element select : document.getElementsByTag("select")) {
            Range range = select.sourceRange();
            if (range.isTracked() && range.start().pos() <= pointer && pointer <= range.end().pos()) return select;
        }
        return null;
    }

    /**
     * Whether the select's own markup holds a {@code <style} start. A style element inside a select is
     * raw text to current browsers, but jsoup's parser (like older browsers) drops the start tag and reads
     * the style's text as markup, so an option-looking string in it would be taken for an option. The two
     * parsers cannot be told to agree here, so such a select is left exactly as the template has it.
     */
    private static boolean hasStyleInside(Element select, String source) {
        Range open = select.sourceRange();
        Range close = select.endSourceRange();
        int from = open.isTracked() ? open.end().pos() : 0;
        int to = close.isTracked() ? close.start().pos() : source.length();
        if (from < 0 || to > source.length() || from > to) return true;
        return source.substring(from, to).toLowerCase(java.util.Locale.ROOT).contains("<style");
    }

    /** Options in tree order that this select owns: not those of a nested select, not those in a template. */
    private static List<Element> ownedOptions(Element select) {
        List<Element> owned = new ArrayList<>();
        for (Element option : select.getElementsByTag("option")) {
            boolean mine = true;
            for (Element ancestor = option.parent(); ancestor != null && ancestor != select; ancestor = ancestor.parent()) {
                String tag = ancestor.normalName();
                if (tag.equals("select") || tag.equals("template")) {
                    mine = false;
                    break;
                }
            }
            if (mine) owned.add(option);
        }
        return owned;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision")
    private static Element chosenOption(List<Element> options, String source, String wanted) {
        if (wanted.isEmpty()) {
            for (Element option : options) {
                if (option.hasAttr(VALUE_ATTRIBUTE)) return option;
            }
            return null;
        }
        for (Element option : options) {
            if (submittedValue(option).equals(wanted)) return option;
        }
        for (Element option : options) {
            if (submittedValue(option).equalsIgnoreCase(wanted)) return option;
        }
        for (Element option : options) {
            if (!option.hasAttr(VALUE_ATTRIBUTE)) continue;
            Integer start = tagStart(option);
            TagAttributes tag = start == null ? null : readTagAttributes(source, start + OPTION_OPEN);
            if (tag != null && tag.value != null && tag.value.equalsIgnoreCase(wanted)) return option;
        }
        return null;
    }

    /** The offset of the {@code <} of an element's start tag, or null when the parser did not track it. */
    private static Integer tagStart(Element element) {
        Range range = element.sourceRange();
        return range.isTracked() ? range.start().pos() : null;
    }

    /** One start tag's attributes as the edit needs them (see {@link #readTagAttributes}). */
    private static final class TagAttributes {
        String value;
        final List<int[]> selectedRanges = new ArrayList<>();
    }

    /**
     * Reads the attributes of the start tag whose name ends at {@code p}, up to its own {@code >} (quoted
     * values with {@code >} inside them, single-quoted and bare values, attributes without a value and a stray
     * {@code =} are stepped over): the first {@code value} attribute that has a value, as written, and the
     * {@code [start, end)} range of every {@code selected} attribute with the white space before it. The tag's
     * start is known from the parser, so this only reads inside one tag; null for an unterminated tag or an
     * unclosed quote, where nothing can be trusted.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive match of HTML attribute names (value, selected) in eForm markup, as a browser matches them; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of HTML attribute names in eForm markup; not a security or authorization decision")
    private static TagAttributes readTagAttributes(String text, int from) {
        int length = text.length();
        int p = from;
        TagAttributes tag = new TagAttributes();
        while (true) {
            while (p < length && (Character.isWhitespace(text.charAt(p)) || text.charAt(p) == '/')) p++;
            if (p >= length) return null;
            if (text.charAt(p) == '>') return tag;
            int nameStart = p;
            while (p < length && !Character.isWhitespace(text.charAt(p)) && "=>/".indexOf(text.charAt(p)) < 0) p++;
            if (p == nameStart) { // a stray "=": step over it
                p++;
                continue;
            }
            String name = text.substring(nameStart, p);
            int attributeEnd = p;
            int q = p;
            while (q < length && Character.isWhitespace(text.charAt(q))) q++;
            if (q < length && text.charAt(q) == '=') {
                p = q + 1;
                while (p < length && Character.isWhitespace(text.charAt(p))) p++;
                int valueStart;
                int valueEnd;
                if (p < length && (text.charAt(p) == '"' || text.charAt(p) == '\'')) {
                    valueStart = p + 1;
                    valueEnd = text.indexOf(text.charAt(p), valueStart);
                    if (valueEnd < 0) return null;
                    p = valueEnd + 1;
                } else {
                    valueStart = p;
                    while (p < length && !Character.isWhitespace(text.charAt(p)) && text.charAt(p) != '>') p++;
                    valueEnd = p;
                }
                attributeEnd = p;
                if (tag.value == null && name.equalsIgnoreCase(VALUE_ATTRIBUTE)) {
                    tag.value = text.substring(valueStart, valueEnd);
                }
            }
            if (name.equalsIgnoreCase("selected")) {
                int spaceStart = nameStart;
                while (spaceStart > from && Character.isWhitespace(text.charAt(spaceStart - 1))) spaceStart--;
                tag.selectedRanges.add(new int[] {spaceStart, attributeEnd});
            }
        }
    }
}
