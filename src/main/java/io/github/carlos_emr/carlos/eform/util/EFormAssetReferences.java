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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * {@code ${}, so a second pass skips it.</p>
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

    /**
     * Matches an HTML {@code src} attribute and captures its quoted value.
     *
     * <p>Deliberately not an HTML parse. Re-serializing two decades of hand-authored clinic markup
     * through a parser to change one attribute risks changing everything else on the page, and
     * these forms are exactly the input least able to survive normalization. The whitespace before
     * {@code src} excludes {@code data-src} and {@code x-src}, which can carry non-resource data.</p>
     */
    private static final Pattern HTML_SRC_REFERENCE = Pattern.compile(
            "<[A-Za-z][^<>]{0,4096}?\\s+(src)(\\s*=\\s*)([\"'])([^\"'<>]{1,255})\\3",
            Pattern.CASE_INSENSITIVE);

    /** A dotted JavaScript property assignment, not a standalone variable called {@code src}. */
    private static final Pattern JS_SRC_REFERENCE = Pattern.compile(
            "\\.(src)(\\s*=\\s*)([\"'])([^\"'<>]{1,255})\\3",
            Pattern.CASE_INSENSITIVE);

    /**
     * Only a link element's href loads an asset. An anchor href is navigation, even if a file of
     * the same name happens to exist in the shared asset directory.
     */
    private static final Pattern LINK_HREF_REFERENCE = Pattern.compile(
            "<link\\b[^<>]{0,4096}?\\s+(href)(\\s*=\\s*)([\"'])([^\"'<>]{1,255})\\3",
            Pattern.CASE_INSENSITIVE);

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
        return rewriteReferences(rewriteReferences(rewriteReferences(html, HTML_SRC_REFERENCE,
                resolved, assetExists), JS_SRC_REFERENCE, resolved, assetExists),
                LINK_HREF_REFERENCE, resolved, assetExists);
    }

    private static String rewriteReferences(String html, Pattern reference, Map<String, Boolean> resolved,
            Predicate<String> assetExists) {
        Matcher matcher = reference.matcher(html);
        StringBuilder rewritten = new StringBuilder(html.length());
        boolean changed = false;

        while (matcher.find()) {
            String value = matcher.group(4);
            String replacement = matcher.group();
            if (isCandidate(value) && isServable(value, resolved, assetExists)) {
                replacement = matcher.group().substring(0, matcher.start(4) - matcher.start())
                        + IMAGE_PATH_MARKER + value + matcher.group(3);
                changed = true;
            }
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        if (!changed) {
            return html;
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
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
