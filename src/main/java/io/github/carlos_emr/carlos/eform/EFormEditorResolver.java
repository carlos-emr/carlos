/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which editor the eForm Library's Edit action opens for a stored eForm template.
 *
 * <p>The Visual eForm Editor does not edit a template's markup in place: it rebuilds the page from
 * its own widgets and regenerates every script on save. A form it produced round-trips safely; a
 * hand-authored form, a form imported from another EMR, or a form with custom JavaScript loses that
 * code on the first visual save. So the visual editor is the default only for a form it can prove it
 * wrote and nobody has changed since, and every other form keeps the HTML source editor
 * (issue #3166).</p>
 *
 * <ul>
 *   <li>{@link EditorKind#VISUAL} — the template carries the marker the visual editor writes on every
 *       save, {@code <meta name='carlos-eform-editor' content='visual' data-fingerprint='…'>}, and the
 *       fingerprint still matches the rest of the markup. The fingerprint is what makes this
 *       deterministic: a form saved visually and then changed in the HTML source editor (a custom
 *       script added, say) no longer matches, so its own edits are not silently regenerated away by
 *       the next visual save.</li>
 *   <li>{@link EditorKind#CONVERTIBLE} — the visual editor can open it, but not safely by default:
 *       a marker whose fingerprint no longer matches, a form the editor saved before the marker
 *       existed ({@code <div id="eform_container">} opening with {@code <form id="inputForm">}), or
 *       the eForm Generator's page structure ({@code <div id="page1">} with a {@code BGImage} image).
 *       The library keeps the HTML editor as the pencil and offers the visual editor as an explicit,
 *       warned choice.</li>
 *   <li>{@link EditorKind#HTML} — everything else, including blank markup. Only the HTML source
 *       editor can change it safely.</li>
 * </ul>
 *
 * <p>The checks mirror the visual editor's own {@code isVisualEditorAuthored} and
 * {@code eformFingerprint} in {@code visualEformEditor.jsp}; keep the two in step
 * ({@code scripts/eform-visual-editor-routing.test.js} runs the JavaScript against the same cases).
 * They are linear scans, not an HTML parse, so they stay cheap on the multi-megabyte templates that
 * embed images.</p>
 *
 * @since 2026-10-22
 */
public final class EFormEditorResolver {

    /** Name of the {@code <meta>} element the visual editor writes into every template it saves. */
    public static final String VISUAL_EDITOR_MARKER_NAME = "carlos-eform-editor";

    /** Content of the visual editor's marker {@code <meta>} element. */
    public static final String VISUAL_EDITOR_MARKER_CONTENT = "visual";

    /** The editor a stored eForm template should be opened in. */
    public enum EditorKind {
        /** Saved by the Visual eForm Editor and unchanged since; it is the default editor. */
        VISUAL,
        /** The visual editor can open it, but would replace its scripts; the HTML editor stays the default. */
        CONVERTIBLE,
        /** Only the HTML source editor can change it without losing markup or scripts. */
        HTML
    }

    // Exactly the tag the editor writes; anything else (hand-copied, re-quoted) is not proof.
    private static final Pattern VISUAL_MARKER = Pattern.compile(
            "<meta name='" + VISUAL_EDITOR_MARKER_NAME + "' content='" + VISUAL_EDITOR_MARKER_CONTENT
                    + "' data-fingerprint='([0-9a-f]{8})'>");

    // A real attribute named id, not the tail of data-id or an "id=" inside some other word.
    private static final String ID_ATTRIBUTE = "(?<![\\w-])id\\s*=\\s*";

    // The page shell the editor has always generated: the container opens with its input form.
    private static final Pattern LEGACY_VISUAL_SHELL = Pattern.compile(
            "<div\\s+" + ID_ATTRIBUTE + "[\"']eform_container[\"'][^>]*>\\s*<form\\s+" + ID_ATTRIBUTE
                    + "[\"']inputForm[\"']");
    // The editor's importer looks for [id^='BGImage'] and #page1; jQuery id selectors are case-sensitive.
    private static final Pattern BACKGROUND_IMAGE = Pattern.compile(ID_ATTRIBUTE + "[\"']BGImage[^\"']*[\"']");
    private static final Pattern FIRST_PAGE = Pattern.compile(ID_ATTRIBUTE + "[\"']page1[\"']");

    private EFormEditorResolver() {
    }

    /**
     * Classifies a stored template.
     *
     * @param formHtml the template markup ({@code eform.form_html}); may be {@code null}
     * @return the editor to open it in; never {@code null}
     */
    public static EditorKind resolve(String formHtml) {
        if (formHtml == null || formHtml.isBlank()) {
            return EditorKind.HTML;
        }
        Matcher marker = VISUAL_MARKER.matcher(formHtml);
        if (marker.find()) {
            String unmarked = formHtml.substring(0, marker.start()) + formHtml.substring(marker.end());
            return fingerprint(unmarked).equals(marker.group(1)) ? EditorKind.VISUAL : EditorKind.CONVERTIBLE;
        }
        if (LEGACY_VISUAL_SHELL.matcher(formHtml).find()
                || (BACKGROUND_IMAGE.matcher(formHtml).find() && FIRST_PAGE.matcher(formHtml).find())) {
            return EditorKind.CONVERTIBLE;
        }
        return EditorKind.HTML;
    }

    /**
     * The fingerprint the visual editor stamps into its marker: 32-bit FNV-1a over the UTF-16 code
     * units of the markup without the marker, as eight lower-case hex digits. It detects change, not
     * tampering; it is not a security control, and the editor's JavaScript computes the same value.
     *
     * @param markup the template markup with the marker tag removed
     * @return eight lower-case hexadecimal digits
     */
    static String fingerprint(String markup) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < markup.length(); i++) {
            hash ^= markup.charAt(i);
            hash *= 0x01000193;
        }
        return String.format("%08x", hash);
    }
}
