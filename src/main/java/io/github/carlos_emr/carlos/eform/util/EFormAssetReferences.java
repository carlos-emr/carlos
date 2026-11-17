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
package io.github.carlos_emr.carlos.eform.util;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Ascii;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;

import io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Rewrites bare-filename asset references in stored eForm HTML onto CARLOS's existing eForm asset
 * reference process.
 *
 * <h3>The problem</h3>
 * <p>An eForm package is authored as a self-contained folder, so its HTML references the files
 * beside it by bare name:</p>
 *
 * <pre>{@code
 * <script src="jSignature.min.js"></script>
 * <body onload="onBodyLoad()"><script src="onBodyLoad_Oct2018.js"></script>
 * }</pre>
 *
 * <p>The ZIP importer flattens those files into the shared eForm image directory and stores the
 * HTML verbatim, so nothing rewrites the reference. A bare name resolves against the page's own
 * URL, and every eForm surface lives under {@code /<context>/eform/}, so the browser asks for
 * {@code /<context>/eform/jSignature.min.js}. No route serves that, Tomcat answers the 404 page,
 * and the browser refuses the response on MIME type ({@code text/html} is not executable). The
 * form's {@code onload} handler is then undefined and the page never finishes building.</p>
 *
 * <p>Measured against a 179-package corpus of real published eForms: 77 such requests across 31
 * distinct filenames.</p>
 *
 * <h3>Why rewrite rather than serve the bare path</h3>
 * <p>Serving {@code /<context>/eform/<name>} would be the smaller diff and the wrong shape. It
 * introduces a second, parallel way to read the eForm asset directory — a new path-traversal
 * surface — and, more importantly, it bypasses the render surface's capability model: the PDF
 * renderer authorizes exactly the asset names it finds in the composed HTML
 * ({@code EFormRenderTokenService.authorizeAssets}) and refuses anything else. An asset fetched
 * over a route the composer never rewrote is an asset outside that grant.</p>
 *
 * <p>Rewriting to the {@code ${oscar_image_path}} marker instead puts these references onto the
 * path everything downstream already understands. {@code EFormBase.setImagePath(contextPath)}
 * turns the marker into {@code /<context>/eform/displayImage?imagefile=<name>} and percent-encodes
 * the URL-hostile characters real packages put in filenames;
 * {@link EFormRenderPdfHtmlComposer} rewrites that onto the render servlet; and the render grant
 * picks the name up because it is now where the grant scanner looks. One insertion point, no new
 * route, no new way to name a file.</p>
 *
 * <h3>Scope — only what can actually be served</h3>
 * <p>A reference is rewritten only when a non-empty file of exactly that name exists in the eForm
 * asset directory. That is deliberate and it is what keeps this from being a blind string sweep:
 * a corpus form referencing {@code jquery-1.7.1.min.js} (82 references, shipped in no package) is
 * left exactly as authored, because pointing it at an asset route that would also 404 buys
 * nothing and only obscures where the reference really came from.</p>
 *
 * <p>Values containing a path separator, a scheme, a query, a fragment, or an existing
 * {@code ${...}} marker are never candidates, so traversal is structurally excluded before the
 * name is validated — and it is validated anyway, through the same
 * {@link PathValidationUtils#validatePathComponent} and {@link DisplayImage2Action#getImageFile}
 * lookup the asset route itself uses. Rewriting is idempotent: the rewritten value contains
 * {@code ${...}}, so a second pass skips it.</p>
 *
 * @since 2026-09-20
 */
public final class EFormAssetReferences {

    /**
     * The marker {@code EFormBase.setImagePath(contextPath)} substitutes with the eForm asset
     * route. Rewriting to the marker rather than to a finished URL is what keeps this class out of
     * the business of building and encoding that URL.
     */
    static final String IMAGE_PATH_MARKER = "${oscar_image_path}";

    private static final Set<String> SRC_ELEMENTS = Set.of(
            "script", "img", "iframe", "frame", "embed", "audio", "video", "source", "track");
    private static final Set<String> SCRIPT_ATTRIBUTES = Set.of(
            "onload", "onerror", "onclick", "onchange", "oninput", "onmouseover", "onmouseout",
            "onmouseenter", "onmouseleave", "onfocus", "onblur", "onkeydown", "onkeyup", "onkeypress",
            "onmousedown", "onmouseup", "ontouchstart", "ontouchend", "onpointerdown", "onpointerup",
            "onpageshow", "onsubmit", "onreset", "onresize", "onbeforeprint", "onafterprint");
    private static final Set<String> CONTROL_WORDS = Set.of("if", "while", "for", "with", "switch", "catch");
    private static final Set<String> BEFORE_EXPRESSION = Set.of(
            "return", "throw", "case", "delete", "void", "typeof", "new", "in", "of", "instanceof",
            "yield", "await", "else", "do", "break", "continue");

    /** Applied only at a JavaScript code position, never over strings, comments or HTML text. */
    private static final Pattern JS_SRC_REFERENCE = Pattern.compile(
            "\\.(src)(\\s*=\\s*)([\"'])([^\"'<>]{1,255})\\3");

    /**
     * A bare filename: a name, a dot, an extension, and nothing structural.
     *
     * <p>Spaces, parentheses, brackets and commas are admitted because real published packages ship
     * names like {@code scan (1).png} — a Windows duplicate-download artifact the ZIP importer
     * stores verbatim. They are legal in a filename and illegal unencoded in a request target, and
     * encoding them is precisely what {@code EFormBase.setImagePath} does after this rewrite, so
     * excluding them here would strand exactly the assets that need the marker most.</p>
     */
    private static final Pattern BARE_FILENAME = Pattern.compile(
            "[A-Za-z0-9._~, ()\\[\\]-]{1,200}\\.[A-Za-z0-9]{1,10}");

    /**
     * Upper bound on distinct candidate names resolved per call. A pathological document cannot
     * turn one page render into an unbounded number of filesystem lookups; the cap is far above
     * anything a real form reaches (the corpus maximum is well under 20).
     */
    private static final int MAX_RESOLVED_NAMES = 256;

    private EFormAssetReferences() {
    }

    /**
     * Rewrites bare-filename {@code src} references and {@code link[href]} assets onto the eForm
     * asset marker. Navigation hrefs remain as authored.
     *
     * @param html stored eForm HTML, before marker substitution; null or blank is returned as-is
     * @return the HTML with servable bare references rewritten, otherwise unchanged
     */
    public static String normalizeBareAssetReferences(String html) {
        return normalizeBareAssetReferences(html, EFormAssetReferences::isServableAsset);
    }

    /**
     * Seam for tests: the same rewrite against a supplied existence predicate, so the rule can be
     * exercised without an eForm asset directory on disk.
     *
     * @param assetExists answers whether a validated bare filename is servable by the asset route
     */
    static String normalizeBareAssetReferences(String html, Predicate<String> assetExists) {
        if (html == null || html.isEmpty() || !html.contains("=")) {
            return html;
        }

        Map<String, Boolean> resolved = new HashMap<>();
        Set<Integer> insertions = new TreeSet<>();
        // Use the parser only for source positions. Never serialize the document: that would
        // normalize unrelated hand-authored markup, quotes and whitespace.
        for (Element element : Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true)).getAllElements()) {
            String tag = element.normalName();
            if (SRC_ELEMENTS.contains(tag) || ("input".equals(tag) && Ascii.equalsIgnoreCase("image", element.attr("type")))) {
                collectAttribute(html, element, "src", insertions, resolved, assetExists);
            }
            if ("link".equals(tag) && isStylesheetRelation(element.attr("rel"))) {
                collectAttribute(html, element, "href", insertions, resolved, assetExists);
            }
            if ("script".equals(tag) && !element.hasAttr("src")
                    && (element.attr("type").isBlank()
                        || element.attr("type").matches("(?i)(?:text|application)/(?:x-)?(?:java|ecma)script"))) {
                for (DataNode data : element.dataNodes()) {
                    Range range = data.sourceRange();
                    if (range.isTracked()) {
                        collectScriptReferences(html, range.startPos(), range.endPos(), insertions, resolved, assetExists);
                    }
                }
            }
            for (org.jsoup.nodes.Attribute attribute : element.attributes()) {
                if (SCRIPT_ATTRIBUTES.contains(attribute.getKey())) {
                    Range range = element.attributes().sourceRange(attribute.getKey()).valueRange();
                    // Entity decoding changes offsets and can introduce quotes; preserve those
                    // handlers rather than treating encoded string contents as executable code.
                    if (range.isTracked() && html.substring(range.startPos(), range.endPos()).equals(attribute.getValue())) {
                        collectScriptReferences(html, range.startPos(), range.endPos(), insertions, resolved, assetExists);
                    }
                }
            }
        }
        if (insertions.isEmpty()) {
            return html;
        }
        StringBuilder rewritten = new StringBuilder(html.length() + insertions.size() * IMAGE_PATH_MARKER.length());
        int previous = 0;
        for (int position : insertions) {
            rewritten.append(html, previous, position).append(IMAGE_PATH_MARKER);
            previous = position;
        }
        return rewritten.append(html, previous, html.length()).toString();
    }

    private static void collectAttribute(String html, Element element, String attribute,
            Set<Integer> insertions, Map<String, Boolean> resolved, Predicate<String> assetExists) {
        Range range = element.attributes().sourceRange(attribute).valueRange();
        if (!range.isTracked() || range.startPos() < 1 || range.endPos() >= html.length()) {
            return;
        }
        char quote = html.charAt(range.startPos() - 1);
        // Keep the existing quoted-filename scope; insert into the original source, not a
        // decoded/normalized attribute value or text that merely resembles an attribute.
        if ((quote == '\'' || quote == '"') && html.charAt(range.endPos()) == quote) {
            String value = html.substring(range.startPos(), range.endPos());
            if (isCandidate(value) && isServable(value, resolved, assetExists)) {
                insertions.add(range.startPos());
            }
        }
    }

    private static boolean isStylesheetRelation(String relation) {
        for (String token : relation.split("[\\t\\n\\f\\r ]+")) {
            if (Ascii.equalsIgnoreCase("stylesheet", token)) {
                return true;
            }
        }
        return false;
    }

    private static void collectScriptReferences(String html, int start, int end, Set<Integer> insertions,
            Map<String, Boolean> resolved, Predicate<String> assetExists) {
        Deque<Boolean> controlParentheses = new ArrayDeque<>();
        String lastWord = "";
        boolean beforeExpression = true;
        boolean afterBrace = false;
        boolean propertyName = false;
        Matcher reference = JS_SRC_REFERENCE.matcher(html);
        for (int i = start; i < end;) {
            char c = html.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (html.startsWith("//", i) || html.startsWith("<!--", i) || html.startsWith("-->", i)) {
                while (i < end && html.charAt(i) != '\n' && html.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (html.startsWith("/*", i)) {
                int close = html.indexOf("*/", i + 2);
                i = close < 0 ? end : Math.min(end, close + 2);
                continue;
            }
            if (c == '`') {
                // Nested template interpolation needs a full parser. Preserve the remaining
                // script rather than risk interpreting template text as property assignments.
                return;
            }
            if (c == '\'' || c == '"') {
                i = skipQuotedScript(html, i, end, c);
                beforeExpression = false;
                afterBrace = false;
                lastWord = "";
                continue;
            }
            if (c == '/') {
                if (afterBrace) {
                    // A slash after '}' can begin a regexp or divide an object expression.
                    // Preserve this ambiguous remainder instead of guessing its lexical state.
                    return;
                }
                if (beforeExpression) {
                    i = skipScriptRegexp(html, i, end);
                    beforeExpression = false;
                } else {
                    i += i + 1 < end && html.charAt(i + 1) == '=' ? 2 : 1;
                    beforeExpression = true;
                }
                lastWord = "";
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int wordStart = i++;
                while (i < end && Character.isJavaIdentifierPart(html.charAt(i))) {
                    i++;
                }
                lastWord = propertyName ? "" : html.substring(wordStart, i);
                propertyName = false;
                beforeExpression = BEFORE_EXPRESSION.contains(lastWord);
                afterBrace = false;
                continue;
            }
            if ((c == '+' || c == '-') && i + 1 < end && html.charAt(i + 1) == c) {
                // Prefix operators still precede an expression; postfix operators still follow one.
                i += 2;
                lastWord = "";
                afterBrace = false;
                propertyName = false;
                continue;
            }
            propertyName = c == '.';
            if (c == '.') {
                reference.region(i, end);
                if (reference.lookingAt()) {
                    int following = reference.end();
                    while (following < end && Character.isWhitespace(html.charAt(following))) {
                        following++;
                    }
                    String value = reference.group(4);
                    if ((following == end || ";,)}]".indexOf(html.charAt(following)) >= 0)
                            && isCandidate(value) && isServable(value, resolved, assetExists)) {
                        insertions.add(reference.start(4));
                    }
                }
                beforeExpression = false;
            } else if (c == '(') {
                controlParentheses.push(CONTROL_WORDS.contains(lastWord));
                beforeExpression = true;
            } else if (c == ')') {
                beforeExpression = !controlParentheses.isEmpty() && controlParentheses.pop();
            } else if (c == ']' || Character.isDigit(c)) {
                beforeExpression = false;
            } else {
                beforeExpression = true;
            }
            afterBrace = c == '}';
            lastWord = "";
            i++;
        }
    }

    private static int skipQuotedScript(String html, int start, int end, char quote) {
        for (int i = start + 1; i < end; i++) {
            if (html.charAt(i) == '\\') {
                i++;
            } else if (html.charAt(i) == quote) {
                return i + 1;
            }
        }
        return end;
    }

    private static int skipScriptRegexp(String html, int start, int end) {
        boolean characterClass = false;
        for (int i = start + 1; i < end; i++) {
            char c = html.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                characterClass = true;
            } else if (c == ']') {
                characterClass = false;
            } else if (c == '/' && !characterClass) {
                return i + 1;
            } else if (c == '\n' || c == '\r') {
                return end; // An unterminated regexp makes the remainder unsafe to inspect.
            }
        }
        return end;
    }

    /**
     * Whether a reference value is shaped like a bare filename this class may rewrite.
     *
     * <p>Everything structural disqualifies it: a separator means the author already said where the
     * file lives, a scheme or protocol-relative prefix means it is not ours to serve, a query or
     * fragment means it is a route rather than a file, and a {@code ${...}} marker means it is
     * already on the reference path this class exists to put it on.</p>
     */
    private static boolean isCandidate(String value) {
        String trimmed = value.trim();
        if (trimmed.length() != value.length() || trimmed.isEmpty()) {
            // A value the author padded is not one this class should silently re-anchor.
            return false;
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0 || trimmed.indexOf(':') >= 0
                || trimmed.indexOf('?') >= 0 || trimmed.indexOf('#') >= 0
                || trimmed.indexOf('$') >= 0 || trimmed.indexOf('{') >= 0) {
            return false;
        }
        // Percent and plus need query-component encoding that setImagePath does not currently do.
        // Rewriting them would turn an exact ZIP filename into a different asset request.
        return BARE_FILENAME.matcher(trimmed).matches()
                && EFormAssetContentType.forFilename(trimmed).isPresent();
    }

    private static boolean isServable(String name, Map<String, Boolean> resolved,
            Predicate<String> assetExists) {
        Boolean cached = resolved.get(name);
        if (cached != null) {
            return cached;
        }
        if (resolved.size() >= MAX_RESOLVED_NAMES) {
            return false;
        }
        boolean servable = assetExists.test(name);
        resolved.put(name, servable);
        return servable;
    }

    /**
     * Whether the eForm asset route could actually serve this name.
     *
     * <p>Resolved through {@link DisplayImage2Action#getImageFile}, the same validated lookup the
     * route itself performs, so this cannot come out true for a name the route would refuse.
     * Emptiness counts as absence for the reason the render composer already treats it that way:
     * a zero-byte asset loads with a 200 and satisfies every gate while showing nothing.</p>
     */
    private static boolean isServableAsset(String name) {
        try {
            PathValidationUtils.validatePathComponent(name, "eform asset reference");
            File asset = DisplayImage2Action.getImageFile(name);
            return asset.isFile() && asset.length() > 0;
        } catch (Exception e) {
            // A name the asset route would refuse, or an unreadable/absent asset directory. Either
            // way this reference is not ours to rewrite; leaving it as authored is the safe answer,
            // and it is not worth a log line on every render of every form.
            return false;
        }
    }
}
