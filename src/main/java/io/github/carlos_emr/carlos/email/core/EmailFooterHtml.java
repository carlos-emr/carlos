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
package io.github.carlos_emr.carlos.email.core;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * The email footer's formatting (issue #3981; formatting added by maintainer decision, 8 Oct 2026):
 * bold, italic, links and line breaks, and nothing else.
 *
 * <p>The footer travels as HTML. Whatever a browser posts is cleaned here against an allow-list
 * before it is stored or sent: no scripts, styles, event handlers, images or other tags, and links
 * only to {@code https:} or {@code mailto:} addresses. The editor's preview cleans the same way in
 * the browser (DOMPurify), but this server-side cleaning is the one that counts.</p>
 *
 * <p>Every email with a footer goes out in two versions: the formatted one built by
 * {@link #toHtmlDocument}, and a plain-text one whose footer comes from {@link #toPlainText}. The
 * footer's length limit, {@link EmailData#FOOTER_MAX_LENGTH}, counts the plain-text characters; the
 * HTML may not exceed {@link #MAX_HTML_LENGTH}.</p>
 *
 * @since 2026-10-08
 */
public final class EmailFooterHtml {

    /** Most characters a footer's cleaned HTML may take, its formatting included. */
    public static final int MAX_HTML_LENGTH = 10_000;

    private static final Set<String> BLOCK_TAGS = Set.of("p", "div");
    private static final String BR = "<br>";
    // What an editor leaves after the last line: a break, or an empty line (Chrome: <div><br></div>).
    private static final List<String> EMPTY_TAILS =
            List.of(BR, "<div><br></div>", "<p><br></p>", "<div></div>", "<p></p>");

    // Control or invisible formatting characters, a lone surrogate, or a character the parser had
    // to replace, in a link's address: such an address is dropped like one with a protocol that
    // isn't allowed, so the address a reader sees is the one the link goes to.
    private static final Pattern HIDDEN_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Cs}\\uFFFD]");

    // Built once and never changed: Safelist is mutable, so it must not be handed out.
    private static final Safelist ALLOWED = new Safelist() {
        @Override
        public boolean isSafeAttribute(String tagName, Element el, Attribute attr) {
            return super.isSafeAttribute(tagName, el, attr)
                    && !("href".equals(attr.getKey()) && HIDDEN_CHARACTERS.matcher(attr.getValue()).find());
        }
    }
            .addTags("b", "strong", "i", "em", "br", "p", "div", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "https", "mailto");

    private EmailFooterHtml() {
    }

    /**
     * Cleans a footer against the allow-list.
     *
     * @param html the footer as posted or stored, may be null
     * @return the cleaned footer HTML, or empty when it has no visible text
     */
    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document.OutputSettings output = new Document.OutputSettings()
                .prettyPrint(false)
                .escapeMode(Entities.EscapeMode.base);
        // An editor leaves a line break at the end; it would only add space below the footer.
        String cleaned = stripTrailingBreaks(Jsoup.clean(html, "", ALLOWED, output)).strip();
        return toPlainText(cleaned).isEmpty() ? "" : cleaned;
    }

    /**
     * The footer as the plain-text version of an email shows it: its text and line breaks, with each
     * link's address written out after its text when the two differ. Nothing is cleaned here; give it
     * cleaned HTML.
     *
     * @param html the footer HTML, may be null
     * @return the footer as plain text, without surrounding whitespace
     */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    text.append(textNode.text().replace('\u00A0', ' '));
                } else if (node instanceof Element element) {
                    if ("br".equals(element.normalName())) {
                        text.append('\n');
                    } else if (BLOCK_TAGS.contains(element.normalName())) {
                        startLine(text);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (!(node instanceof Element element)) {
                    return;
                }
                if ("a".equals(element.normalName())) {
                    String address = linkAddress(element.attr("href"));
                    if (!address.isEmpty() && !element.text().strip().equals(address)) {
                        text.append(" <").append(address).append('>');
                    }
                } else if (BLOCK_TAGS.contains(element.normalName())) {
                    startLine(text);
                }
            }
        }, Jsoup.parseBodyFragment(html).body());
        // Line by line rather than one pattern over the whole text: linear on any input.
        String lines = text.toString().lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"));
        return lines.replaceAll("\n{3,}", "\n\n").strip();
    }

    /**
     * Turns plain text, such as a footer an eForm sends, into footer HTML: escaped, with its line
     * breaks kept.
     *
     * @param text plain text, may be null
     * @return footer HTML, or empty when the text is blank
     */
    public static String fromPlainText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lines = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        return SafeEncode.forHtmlContent(lines).replace("\n", "<br>");
    }

    /**
     * @param html the footer HTML, may be null
     * @return how many characters the footer counts against {@link EmailData#FOOTER_MAX_LENGTH}
     */
    public static int visibleLength(String html) {
        return toPlainText(html).length();
    }

    /**
     * The formatted version of an email: the message as typed (its line breaks and spacing kept,
     * nothing in it read as formatting), then the clinic logo when there is one, then the footer.
     *
     * @param plainBody the message, or with encryption on the secure-message notice, as plain text
     * @param footerHtml the footer HTML; it is cleaned again here
     * @param logoContentId the logo's Content-ID when the email carries the logo inline, else null
     * @return a complete HTML document
     */
    // FindSecBugs POTENTIAL_XML_INJECTION: every value appended is escaped (SafeEncode, messageHtml) or cleaned against the allow-list (clean); the rest are literals.
    @SuppressFBWarnings(value = "POTENTIAL_XML_INJECTION", justification = "appended values are escaped with SafeEncode or cleaned against the footer allow-list; the rest are literals")
    public static String toHtmlDocument(String plainBody, String footerHtml, String logoContentId) {
        StringBuilder html = new StringBuilder(
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                        + "<body style=\"font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.4;\">");
        // pre-wrap keeps the message's spacing where a mail app honours it; the non-breaking spaces
        // keep it where one does not (Outlook's Word engine).
        html.append("<div style=\"white-space: pre-wrap;\">").append(keepSpacing(messageHtml(plainBody)))
                .append("</div><br>");
        if (logoContentId != null) {
            // Inline (cid:), never a web address: nothing to host, and no request reveals that
            // the patient opened the email.
            html.append("<img src=\"cid:").append(SafeEncode.forHtmlAttribute(logoContentId))
                    .append("\" alt=\"\" style=\"display: block; max-width: 600px; height: auto; margin-bottom: 8px;\">");
        }
        html.append("<div>").append(clean(footerHtml)).append("</div></body></html>");
        return html.toString();
    }

    // The message as the plain-text version sends it: only trailing whitespace dropped, so a first
    // line's indent stays (fromPlainText, for footers, strips both ends).
    private static String messageHtml(String plainBody) {
        if (plainBody == null) {
            return "";
        }
        String lines = plainBody.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        return SafeEncode.forHtmlContent(lines).replace("\n", BR);
    }

    // One backwards pass, no pattern: linear however many breaks, empty lines and spaces there are.
    private static String stripTrailingBreaks(String html) {
        int end = html.length();
        while (true) {
            while (end > 0 && Character.isWhitespace(html.charAt(end - 1))) {
                end--;
            }
            String tail = null;
            for (String candidate : EMPTY_TAILS) {
                if (end >= candidate.length() && html.startsWith(candidate, end - candidate.length())) {
                    tail = candidate;
                    break;
                }
            }
            if (tail == null) {
                return html.substring(0, end);
            }
            end -= tail.length();
        }
    }

    // A space after a space (or at the start of a line) becomes a non-breaking space, so runs of
    // spaces and indents survive in every mail app while single spaces still let lines wrap.
    // Tabs count as four spaces. Linear, no pattern.
    private static String keepSpacing(String escapedHtml) {
        StringBuilder out = new StringBuilder(escapedHtml.length());
        boolean afterSpace = true;
        for (int i = 0; i < escapedHtml.length(); i++) {
            char c = escapedHtml.charAt(i);
            if (c == ' ' || c == '\t') {
                int width = c == '\t' ? 4 : 1;
                for (int n = 0; n < width; n++) {
                    out.append(afterSpace ? "&nbsp;" : " ");
                    afterSpace = true;
                }
            } else {
                out.append(c);
                // Escaped text has no '>' of its own, so one here ends a <br>: a new line starts,
                // and its first space must not collapse either.
                afterSpace = c == '>';
            }
        }
        return out.toString();
    }

    private static void startLine(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }

    private static String linkAddress(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        return href.regionMatches(true, 0, "mailto:", 0, 7) ? href.substring(7) : href;
    }
}
