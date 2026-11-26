/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Reduces administrator-authored rich text to formatting markup before it is printed as HTML.
 *
 * <p>Almost every value CARLOS prints is data, so it is <em>encoded</em> ({@link SafeEncode}) and shows
 * as literal text. A few are different on purpose: the clinic's help panel (Administration &gt; Resource
 * Base URL, "details" option) is composed in a rich-text editor and stored as HTML, and it has to render
 * as headings, lists and links. Encoding it would show the tags; printing it raw lets a stored
 * {@code <script>}, event-handler attribute or {@code javascript:} link run in every clinician's session.
 * This class is the third option: keep the markup an author can reasonably want and drop the rest.</p>
 *
 * <p>Kept: the document-formatting tags of jsoup's {@link Safelist#relaxed()} (paragraphs, headings, lists,
 * tables, emphasis, code, quotes, links, {@code http}/{@code https} images), plus the editor's
 * strike-through ({@code del}, {@code s}) and horizontal rule ({@code hr}). Links keep their {@code target}
 * and always carry {@code rel="noopener noreferrer"}; relative links (a page inside CARLOS) are kept as
 * written. Everything else goes: scripts, styles, frames, forms, SVG/MathML, {@code class}/{@code id}/
 * {@code style}, every {@code on*} handler and any URL scheme other than {@code http}, {@code https},
 * {@code ftp} and {@code mailto}. The scheme is read the way a browser reads it (tabs and line breaks
 * inside the URL are ignored), so an obfuscated {@code javascript:} link does not survive.</p>
 *
 * <p>Use this only where HTML is the intended content. For plain data use {@link SafeEncode} or
 * {@code <carlos:encode>}.</p>
 *
 * @since 2026-11-26
 */
public final class SafeHtml {

    /**
     * jsoup resolves a link against a base URI before it tests the scheme and discards a link it cannot
     * resolve, so a relative link needs some absolute base to be judged. It is only used for that test:
     * {@code preserveRelativeLinks(true)} keeps the author's own text, so this host is never emitted.
     */
    private static final String LINK_BASE_URI = "https://carlos.invalid/";

    private static final Safelist FORMATTED_TEXT = Safelist.relaxed()
            .addTags("del", "s", "hr")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .preserveRelativeLinks(true);

    /** The attributes of the safelist that hold a URL. */
    private static final String[] URL_ATTRIBUTES = {"href", "src", "cite"};

    /** The URL schemes a kept link or image may use; a URL with no scheme is relative and always allowed. */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "ftp", "mailto");

    /** What a browser reads as a URL scheme: a letter, then letters, digits, {@code +}, {@code -} or {@code .}, then a colon. */
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    /** Characters a browser ignores or drops while reading a URL: ASCII controls (tab, line breaks) and whitespace. */
    private static final Pattern IGNORED_BY_BROWSERS = Pattern.compile("[\\p{Cntrl}\\s]");

    private SafeHtml() {
    }

    /**
     * Returns {@code html} reduced to the formatting markup described on this class.
     *
     * @param html administrator-authored HTML; may be {@code null}
     * @return the cleaned HTML, safe to print as an element's content; {@code ""} for {@code null}; never
     *         {@code null}
     */
    public static String forFormattedText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Document cleaned = new Cleaner(FORMATTED_TEXT).clean(Jsoup.parseBodyFragment(html, LINK_BASE_URI));
        dropUnsafeUrls(cleaned);
        // prettyPrint off: keep the author's whitespace instead of reflowing it.
        cleaned.outputSettings().prettyPrint(false);
        return cleaned.body().html();
    }

    /**
     * Removes a URL the safelist let through but a browser would read as a script URL.
     *
     * <p>jsoup judges a relative link by its resolved form while {@code preserveRelativeLinks} keeps the
     * author's text, and what jsoup resolves is not always what a browser reads: a browser discards tabs and
     * line breaks anywhere in a URL, so {@code jav<TAB>ascript:} is a {@code javascript:} link to it. Each
     * kept URL is therefore re-read the way a browser reads it, and kept only if it is relative or uses one
     * of {@link #ALLOWED_SCHEMES}. A check made on the finished text does not depend on which jsoup release
     * produced it.</p>
     */
    private static void dropUnsafeUrls(Document cleaned) {
        for (Element element : cleaned.getAllElements()) {
            for (String attribute : URL_ATTRIBUTES) {
                if (element.hasAttr(attribute) && !isSafeUrl(element.attr(attribute))) {
                    element.removeAttr(attribute);
                }
            }
        }
    }

    private static boolean isSafeUrl(String url) {
        // Over-strip on purpose: dropping every whitespace and control character can only make a value look
        // more like a scheme, never less, so an unusual value is removed rather than let through.
        String read = IGNORED_BY_BROWSERS.matcher(url).replaceAll("");
        Matcher scheme = SCHEME.matcher(read);
        return !scheme.find() || ALLOWED_SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT));
    }
}
