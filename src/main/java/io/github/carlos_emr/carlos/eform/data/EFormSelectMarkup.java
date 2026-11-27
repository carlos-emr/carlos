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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Range;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import com.google.common.base.Ascii;

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
 * <p>What a select submits is also the value a database tag that names the field ({@code {name}}) binds
 * ({@link #submittedValues}): the option a browser shows as selected for the select's display size (a drop-down
 * with none marked shows its first enabled option), never a disabled one, and nothing for a select a save does not
 * submit (inert {@code template} content, a disabled select, or a select that belongs to another form).</p>
 *
 * <p>Anything that cannot be resolved with certainty (the select is not found, a start tag cannot be read)
 * changes nothing: the template's own default stays.</p>
 */
final class EFormSelectMarkup {

    /** Length of {@code "<option"}. */
    private static final int OPTION_OPEN = 7;
    private static final String VALUE_ATTRIBUTE = "value";
    private static final String MULTIPLE_ATTRIBUTE = "multiple";
    private static final String SELECTED_ATTRIBUTE = "selected";
    private static final String DISABLED_ATTRIBUTE = "disabled";
    private static final String TEMPLATE_TAG = "template";
    private static final String FORM_TAG = "form";
    /**
     * HTML's white space in a tag and in option text: tab, line feed, form feed, carriage return and space. A
     * no-break space or an em space is not white space to HTML: it is part of a name, a value or the text.
     */
    private static final String HTML_SPACE = " \t\n\f\r";

    /**
     * How many characters of the chosen option's start tag and what follows it an edit remembers from the parse, to
     * prove at the shifted position that it is the same option. The text after an option is not yet edited when
     * its select is (a pass fills its fields in document order), so it still reads as the parse read it.
     */
    private static final int OPTION_WINDOW = 80;

    private EFormSelectMarkup() {
    }

    /**
     * One parse of a form, from which every database-tagged select of one pass is edited. Edits are written
     * against this parse's own text and positions; the caller moves them by the difference between the form's
     * length now and its length here ({@link SelectEdit#shifted}), which is exact because a pass fills its fields
     * in document order, so everything it has changed so far lies before the select it is working on.
     */
    static final class ParsedForm {
        private final String source;
        private final Document document;

        private ParsedForm(String source, Document document) {
            this.source = source;
            this.document = document;
        }

        /** The length of the markup that was parsed. */
        int length() {
            return source.length();
        }
    }

    /** Parses {@code html} once, for {@link #edit(ParsedForm, int, String)} to read as often as it needs. */
    static ParsedForm parseForm(CharSequence html) {
        String source = html.toString();
        return new ParsedForm(source, parse(source));
    }

    /** Where {@code " selected"} goes and which {@code selected} attributes of the other options come out. */
    static final class SelectEdit {
        final int insertAt;
        /** Ascending, non-overlapping {@code [start, end)} ranges, none inside the matched option's tag. */
        final List<int[]> removals = new ArrayList<>();
        /** The parse's own text from the chosen option's {@code <option} on, and of each removal, to check against. */
        private String optionWindow = "";
        private final List<String> removedText = new ArrayList<>();

        private SelectEdit(int insertAt) {
            this.insertAt = insertAt;
        }

        /** The same edit with every position moved by {@code by} (to the form as it stands now). */
        SelectEdit shifted(int by) {
            SelectEdit moved = new SelectEdit(insertAt + by);
            moved.optionWindow = optionWindow;
            moved.removedText.addAll(removedText);
            for (int[] range : removals) moved.removals.add(new int[] {range[0] + by, range[1] + by});
            return moved;
        }

        /**
         * Whether this edit lands exactly where it was read: the form, at the insert, still holds the parse's own
         * text of the chosen option's tag and of what follows it, and each removal range holds the very text the
         * parse took out. A shifted edit that does not fit (a shift that is wrong by as little as one option) is
         * not applied, so the select keeps the template's own default and never shows another option as stored.
         */
        boolean fits(CharSequence html) {
            int from = insertAt - OPTION_OPEN;
            if (!holds(html, from, optionWindow)) return false;
            for (int r = 0; r < removals.size(); r++) {
                if (!holds(html, removals.get(r)[0], removedText.get(r))) return false;
            }
            return true;
        }

        private static boolean holds(CharSequence html, int from, String text) {
            return !text.isEmpty() && from >= 0 && from + text.length() <= html.length()
                    && html.subSequence(from, from + text.length()).toString().equals(text);
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
     * text as {@link #optionText} reads it). The whole value is compared, never a
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
    static SelectEdit edit(CharSequence html, int pointer, String wanted) {
        return wanted == null ? null : edit(parseForm(html), pointer, wanted);
    }

    /**
     * As {@link #edit(CharSequence, int, String)}, read from a form parsed once: {@code pointer} and the edit's
     * positions are those of the parsed text. A form of any number of database-tagged selects costs this one parse.
     */
    static SelectEdit edit(ParsedForm form, int pointer, String wanted) {
        if (wanted == null) return null;
        String source = form.source;
        Element select = selectHolding(form.document, pointer);
        if (select == null) return null;
        String inside = markupInside(select, source);
        if (inside == null || holdsStyleElement(inside)) return null;
        List<Element> options = ownedOptions(select);
        Element chosen = chosenOption(options, source, wanted);
        Integer start = chosen == null ? null : tagStart(chosen);
        if (start == null) return null;
        SelectEdit edit = new SelectEdit(start + OPTION_OPEN);
        edit.optionWindow = source.substring(start, Math.min(source.length(), start + OPTION_OPEN + OPTION_WINDOW));
        if (!wanted.isEmpty() && !select.hasAttr(MULTIPLE_ATTRIBUTE)) {
            collectOtherSelectedRemovals(options, chosen, source, edit);
        }
        return edit;
    }

    /** Collects the {@code selected} attribute ranges of every other option, or leaves none if a tag cannot be read. */
    private static void collectOtherSelectedRemovals(List<Element> options, Element chosen, String source,
            SelectEdit edit) {
        List<int[]> removals = new ArrayList<>();
        for (Element option : options) {
            if (option == chosen) continue;
            Integer otherStart = tagStart(option);
            TagAttributes tag = otherStart == null ? null : readTagAttributes(source, otherStart + OPTION_OPEN);
            if (tag == null) return; // a tag that cannot be read: only the insert, never a guess
            removals.addAll(tag.selectedRanges);
        }
        removals.sort((a, b) -> Integer.compare(a[0], b[0]));
        edit.removals.addAll(removals);
        for (int[] range : removals) edit.removedText.add(source.substring(range[0], range[1]));
    }

    /**
     * What a browser submits, with the form a save posts, for each select whose start tag begins at one of
     * {@code selectStarts}: the value a database tag that names the field binds. The whole form is parsed once for
     * all of them, and not at all when there is none. A select has no entry when it submits nothing (see
     * {@link #valueSubmittedBy}), when the parser does not read a select there (one inside a comment, say), or when
     * a save does not submit it (see {@link #submittedWith}).
     */
    static Map<Integer, String> submittedValues(CharSequence html, List<Integer> selectStarts) {
        Map<Integer, String> submitted = new HashMap<>();
        if (selectStarts.isEmpty()) return submitted;
        String source = html.toString();
        Document document = parse(source);
        Element savedForm = firstLiveForm(document);
        for (int start : selectStarts) {
            Element select = selectHolding(document, start + 1);
            String value = select == null ? null : valueSubmittedBy(select, source, document, savedForm);
            if (value != null) submitted.put(start, value);
        }
        return submitted;
    }

    /**
     * The value a browser submits for {@code select} with {@code savedForm}, or null when it submits none. A select
     * with {@code multiple} submits each selected option that is not disabled; its first one is the value. Any
     * other select shows its last selected option or, as a drop-down with none marked, its first option that is not
     * disabled, and submits it unless it is disabled; a list box with none marked submits nothing. The options of a
     * select that holds a style element are those a browser lists (see {@link #holdsStyleElement}).
     */
    private static String valueSubmittedBy(Element select, String source, Document document, Element savedForm) {
        String inside = markupInside(select, source);
        if (inside == null || !submittedWith(select, document, savedForm)) return null;
        List<Element> options = holdsStyleElement(inside) ? optionsOutsideSelect(inside) : ownedOptions(select);
        if (select.hasAttr(MULTIPLE_ATTRIBUTE)) {
            return firstSelectedEnabledValue(options, select);
        }
        Element shown = lastSelectedOption(options);
        if (shown == null) {
            shown = isDropDown(select) ? firstEnabledOption(options, select) : null;
        }
        return shown == null || isDisabled(shown, select) ? null : submittedValue(shown);
    }

    private static String firstSelectedEnabledValue(List<Element> options, Element select) {
        for (Element option : options) {
            if (option.hasAttr(SELECTED_ATTRIBUTE) && !isDisabled(option, select)) {
                return submittedValue(option);
            }
        }
        return null;
    }

    private static Element lastSelectedOption(List<Element> options) {
        Element shown = null;
        for (Element option : options) {
            if (option.hasAttr(SELECTED_ATTRIBUTE)) shown = option;
        }
        return shown;
    }

    private static Element firstEnabledOption(List<Element> options, Element select) {
        for (Element option : options) {
            if (!isDisabled(option, select)) return option;
        }
        return null;
    }

    /**
     * Whether a save submits {@code select} with {@code savedForm}, the page's first live form (the one
     * {@code EForm.setAction} makes the save form). Never a select in inert {@code template} content, nor a disabled
     * one (see {@link #isDisabledControl}), nor one whose {@code form} attribute names another element or nothing, nor
     * one inside another form. A select outside every form is read as before: a form written between
     * {@code <table>} and {@code <tr>} owns the controls of its rows without holding them. So is every select of a
     * page that has no form, apart from template content.
     */
    private static boolean submittedWith(Element select, Document document, Element savedForm) {
        if (ancestorNamed(select, TEMPLATE_TAG, null) != null) return false;
        if (savedForm == null) return true;
        if (isDisabledControl(select)) return false;
        if (select.hasAttr(FORM_TAG)) {
            return targetsForm(document, select.attr(FORM_TAG), savedForm);
        }
        Element form = ancestorNamed(select, FORM_TAG, null);
        return form == null || form == savedForm;
    }

    /** The first {@code <form>} outside inert {@code <template>} content, or null. */
    private static Element firstLiveForm(Document document) {
        for (Element form : document.getElementsByTag(FORM_TAG)) {
            if (ancestorNamed(form, TEMPLATE_TAG, null) == null) return form;
        }
        return null;
    }

    /**
     * Whether {@code id} resolves to {@code savedForm} in the live document (outside inert {@code <template>}
     * content), matching HTML's {@code form} attribute lookup.
     */
    private static boolean targetsForm(Document document, String id, Element savedForm) {
        if (id.isEmpty() || !id.equals(savedForm.id())) return false;
        return document.getElementsByAttribute("id").stream()
                .filter(element -> id.equals(element.id()) && ancestorNamed(element, TEMPLATE_TAG, null) == null)
                .findFirst()
                .orElse(null) == savedForm;
    }

    /** The nearest ancestor of {@code element} named {@code name} below {@code stop} (null: anywhere), or null. */
    private static Element ancestorNamed(Element element, String name, Element stop) {
        for (Element ancestor = element.parent(); ancestor != null && ancestor != stop; ancestor = ancestor.parent()) {
            if (ancestor.normalName().equals(name)) return ancestor;
        }
        return null;
    }

    /**
     * Whether a browser treats the control as disabled, and so submits nothing for it: its own {@code disabled}, or a
     * disabled fieldset around it, unless the control is inside that fieldset's first legend.
     */
    private static boolean isDisabledControl(Element control) {
        if (control.hasAttr(DISABLED_ATTRIBUTE)) return true;
        Element below = control;
        for (Element ancestor = control.parent(); ancestor != null; below = ancestor, ancestor = ancestor.parent()) {
            if (ancestor.normalName().equals("fieldset") && ancestor.hasAttr(DISABLED_ATTRIBUTE)
                    && below != firstLegend(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** The first legend child of {@code fieldset}, or null. */
    private static Element firstLegend(Element fieldset) {
        for (Element child : fieldset.children()) {
            if (child.normalName().equals("legend")) return child;
        }
        return null;
    }

    /** Whether a browser treats the option as disabled: its own {@code disabled}, or that of its option group. */
    private static boolean isDisabled(Element option, Element select) {
        Element group = ancestorNamed(option, "optgroup", select);
        return option.hasAttr(DISABLED_ATTRIBUTE) || group != null && group.hasAttr(DISABLED_ATTRIBUTE);
    }

    /**
     * Whether a browser shows the select as a drop-down, which always has an option selected: no {@code multiple},
     * and a {@code size} that is absent, unreadable or at most 1 when read as HTML reads a non-negative integer
     * (leading white space and a "+" skipped, then the digits up to the first other character), as Chromium does.
     */
    private static boolean isDropDown(Element select) {
        if (select.hasAttr(MULTIPLE_ATTRIBUTE)) return false;
        String size = select.attr("size");
        int p = skipHtmlSpace(size, 0);
        if (p < size.length() && size.charAt(p) == '+') p++;
        int rows = 0;
        while (p < size.length() && size.charAt(p) >= '0' && size.charAt(p) <= '9') {
            rows = Math.min(rows * 10 + size.charAt(p++) - '0', 2); // only "at most 1" matters
        }
        return rows <= 1;
    }

    /**
     * The value a browser submits for an option: its {@code value} attribute when it has one (a bare
     * {@code value} is an explicit empty string), otherwise its text (see {@link #optionText}).
     */
    static String submittedValue(Element option) {
        return option.hasAttr(VALUE_ATTRIBUTE) ? option.attr(VALUE_ATTRIBUTE) : optionText(option);
    }

    /**
     * An option's text as a browser submits it when the option has no value attribute: its text with character
     * references decoded, HTML white space stripped from both ends and every run of it collapsed to one space. Any
     * other space, such as a no-break space ({@code &nbsp;}) or an em space, is text and stays, so the value read
     * here is the value posted. A comment's text is left out (the text on both sides is joined), and so is the text
     * of a script and of template content.
     */
    static String optionText(Element option) {
        StringBuilder text = new StringBuilder();
        NodeTraversor.filter(new NodeFilter() {
            @Override
            public FilterResult head(Node node, int depth) {
                if (node instanceof TextNode textNode) text.append(textNode.getWholeText());
                return node instanceof Element element
                        && (element.normalName().equals("script") || element.normalName().equals(TEMPLATE_TAG))
                        ? FilterResult.SKIP_ENTIRELY : FilterResult.CONTINUE;
            }

            @Override
            public FilterResult tail(Node node, int depth) {
                return FilterResult.CONTINUE;
            }
        }, option);
        StringBuilder collapsed = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHtmlSpace(c)) {
                space = !collapsed.isEmpty();
                continue;
            }
            if (space) collapsed.append(' ');
            space = false;
            collapsed.append(c);
        }
        return collapsed.toString();
    }

    private static boolean isHtmlSpace(char c) {
        return HTML_SPACE.indexOf(c) >= 0;
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
     * The select's own markup, between its start tag and its end tag, or null when the parser's positions do not
     * give it.
     */
    private static String markupInside(Element select, String source) {
        Range open = select.sourceRange();
        Range close = select.endSourceRange();
        int from = open.isTracked() ? open.end().pos() : 0;
        int to = close.isTracked() ? close.start().pos() : source.length();
        return from < 0 || to > source.length() || from > to ? null : source.substring(from, to);
    }

    /**
     * Whether a select's own markup holds a live style element (outside inert {@code <template>} content). A live
     * style element inside a select is raw text to current browsers, but jsoup's parser (like older browsers) drops
     * the start tag and reads the style's text as markup, so an option-looking string in it would be taken for an
     * option: such a select is never edited (the template's own default stays), and its options are read outside a
     * select ({@link #optionsOutsideSelect}). Text that only looks like a style tag (in a comment, in an attribute
     * value such as an option group's label, in a script) or a style inside inert template content is not a live
     * style element of the select.
     */
    private static boolean holdsStyleElement(String markup) {
        if (!Ascii.toLowerCase(markup).contains("<style")) return false;
        for (Element style : Jsoup.parseBodyFragment(markup).getElementsByTag("style")) {
            if (ancestorNamed(style, TEMPLATE_TAG, null) == null) return true;
        }
        return false;
    }

    /**
     * The options of a select's own markup read outside a select, where the parser keeps a style element's text as
     * text, as a browser does inside a select; not those in template content.
     */
    private static List<Element> optionsOutsideSelect(String markup) {
        List<Element> options = new ArrayList<>();
        for (Element option : Jsoup.parseBodyFragment(markup).getElementsByTag("option")) {
            if (ancestorNamed(option, TEMPLATE_TAG, null) == null) options.add(option);
        }
        return options;
    }

    /** Options in tree order that this select owns: not those of a nested select, not those in a template. */
    private static List<Element> ownedOptions(Element select) {
        List<Element> owned = new ArrayList<>();
        for (Element option : select.getElementsByTag("option")) {
            if (isOwnedOption(option, select)) owned.add(option);
        }
        return owned;
    }

    private static boolean isOwnedOption(Element option, Element select) {
        for (Element ancestor = option.parent(); ancestor != null && ancestor != select; ancestor = ancestor.parent()) {
            String tag = ancestor.normalName();
            if (tag.equals("select") || tag.equals(TEMPLATE_TAG)) return false;
        }
        return true;
    }

    private static Element chosenOption(List<Element> options, String source, String wanted) {
        if (wanted.isEmpty()) {
            return firstOptionWithValueAttr(options);
        }
        Element match = firstOptionBySubmittedValue(options, wanted, false);
        if (match != null) return match;
        match = firstOptionBySubmittedValue(options, wanted, true);
        return match != null ? match : firstOptionByRawValue(options, source, wanted);
    }

    private static Element firstOptionWithValueAttr(List<Element> options) {
        for (Element option : options) {
            if (option.hasAttr(VALUE_ATTRIBUTE)) return option;
        }
        return null;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision")
    private static Element firstOptionBySubmittedValue(List<Element> options, String wanted, boolean ignoreCase) {
        for (Element option : options) {
            String value = submittedValue(option);
            if (ignoreCase ? value.equalsIgnoreCase(wanted) : value.equals(wanted)) return option;
        }
        return null;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an eForm option value with a database value; not a security or authorization decision")
    private static Element firstOptionByRawValue(List<Element> options, String source, String wanted) {
        for (Element option : options) {
            if (!option.hasAttr(VALUE_ATTRIBUTE)) continue;
            Integer start = tagStart(option);
            TagAttributes tag = start == null ? null : readTagAttributes(source, start + OPTION_OPEN);
            if (tag != null && wanted.equalsIgnoreCase(tag.value)) return option;
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

    /** One parsed attribute value from {@link #readAttributeValue}, with its end offset and whether it was unquoted. */
    private record ParsedAttrValue(String value, int end, boolean unquoted) {
    }

    /**
     * Reads the attributes of the start tag whose name ends at {@code from}, up to its own {@code >}, splitting
     * them the way the HTML tokenizer does: only HTML white space separates attributes (a no-break or an em space
     * belongs to the name or the unquoted value it is in), a name may begin with {@code =}, a quoted value may
     * hold {@code >}, an unquoted value ends at white space or {@code >}, and a {@code /} between attributes is
     * stepped over. It records the first {@code value} attribute as written (a bare one is empty), since a
     * browser keeps the first of repeated attributes, and the {@code [start, end)} range of every
     * {@code selected} attribute with the white space before it. Names are matched as HTML matches them, ASCII
     * letters in any case. The tag's start is known from the parser, so this only reads inside one tag; null for
     * an unterminated tag or an unclosed quote, where nothing can be trusted.
     */
    private static TagAttributes readTagAttributes(String text, int from) {
        int length = text.length();
        int p = from;
        TagAttributes tag = new TagAttributes();
        boolean prevWasUnquotedValue = false;
        while (true) {
            p = skipSpaceAndSlash(text, p);
            if (p >= length) return null;
            if (text.charAt(p) == '>') return tag;
            int nameStart = p++; // the first character is the name's, even "="
            while (p < length && !isHtmlSpace(text.charAt(p)) && "=>/".indexOf(text.charAt(p)) < 0) p++;
            String name = text.substring(nameStart, p);
            ParsedAttrValue parsed = readAttributeValue(text, p);
            if (parsed == null) return null;
            p = parsed.end();
            if (tag.value == null && Ascii.equalsIgnoreCase(name, VALUE_ATTRIBUTE)) {
                tag.value = parsed.value();
            }
            if (Ascii.equalsIgnoreCase(name, SELECTED_ATTRIBUTE)) {
                tag.selectedRanges.add(selectedRange(text, from, nameStart, p, prevWasUnquotedValue));
            } else {
                prevWasUnquotedValue = parsed.unquoted();
            }
        }
    }

    private static int skipHtmlSpace(String text, int p) {
        int length = text.length();
        while (p < length && isHtmlSpace(text.charAt(p))) p++;
        return p;
    }

    private static int skipSpaceAndSlash(String text, int p) {
        int length = text.length();
        while (p < length && (isHtmlSpace(text.charAt(p)) || text.charAt(p) == '/')) p++;
        return p;
    }

    /** Reads {@code =value} at {@code afterName}, or an empty bare value if absent; returns null on an unclosed quote. */
    private static ParsedAttrValue readAttributeValue(String text, int afterName) {
        int length = text.length();
        int q = skipHtmlSpace(text, afterName);
        if (q >= length || text.charAt(q) != '=') {
            return new ParsedAttrValue("", afterName, false);
        }
        int p = skipHtmlSpace(text, q + 1);
        if (p < length && (text.charAt(p) == '"' || text.charAt(p) == '\'')) {
            int close = text.indexOf(text.charAt(p), p + 1);
            if (close < 0) return null;
            return new ParsedAttrValue(text.substring(p + 1, close), close + 1, false);
        }
        int valueStart = p;
        while (p < length && !isHtmlSpace(text.charAt(p)) && text.charAt(p) != '>') p++;
        return new ParsedAttrValue(text.substring(valueStart, p), p, p > valueStart);
    }

    /**
     * Computes the {@code [start, end)} range to remove for a {@code selected} attribute. Preceding HTML white space
     * is removed too unless it must stay to keep remaining tag parts apart: when another attribute immediately
     * follows, or when {@code /} immediately follows after an unquoted attribute value (dropping the space there
     * would fold {@code /} into the preceding unquoted value, changing what the browser submits for that option).
     */
    private static int[] selectedRange(String text, int from, int nameStart, int end, boolean prevWasUnquotedValue) {
        int spaceStart = nameStart;
        boolean keepSpace = false;
        if (end < text.length()) {
            char next = text.charAt(end);
            keepSpace = !isHtmlSpace(next) && next != '>' && (next != '/' || prevWasUnquotedValue);
        }
        while (!keepSpace && spaceStart > from && isHtmlSpace(text.charAt(spaceStart - 1))) spaceStart--;
        return new int[] {spaceStart, end};
    }
}
