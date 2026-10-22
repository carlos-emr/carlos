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

import java.util.regex.Pattern;

/**
 * Decides which editor the eForm Library's Edit action opens for a stored eForm template.
 *
 * <p>The Visual eForm Editor does not edit a template's markup in place: it rebuilds the page from
 * its own widgets and regenerates every script on save. A form it produced round-trips safely; a
 * hand-authored form, a form imported from another EMR, or a form with custom JavaScript loses that
 * code on the first visual save. So the visual editor is the default only for forms it can be
 * shown to own, and every other form keeps the HTML source editor (issue #3166).</p>
 *
 * <ul>
 *   <li>{@link EditorKind#VISUAL} — saved by the visual editor. Either it carries the
 *       {@link #VISUAL_EDITOR_MARKER_NAME} meta marker the editor writes since #3166, or it has the
 *       page shell the editor has always generated ({@code #eform_container} wrapping
 *       {@code #inputForm}), which is what the editor reloads without conversion.</li>
 *   <li>{@link EditorKind#CONVERTIBLE} — not the visual editor's, but it has the background-image and
 *       page structure the editor's importer can convert (the eForm Generator's
 *       {@code <div id="page1">} with a {@code BGImage} image). Opening it visually is offered as an
 *       explicit choice, never the default, because scripts are not preserved.</li>
 *   <li>{@link EditorKind#HTML} — everything else, including blank markup. Only the HTML source
 *       editor can change it safely.</li>
 * </ul>
 *
 * <p>The checks mirror the visual editor's own loader in {@code visualEformEditor.jsp}
 * ({@code loadEformData} and {@code isVisualEditorAuthored}); keep the two in step. They are
 * attribute-level pattern checks, not an HTML parse, so they stay linear on the multi-megabyte
 * templates that embed images. A false positive is caught by the loader, which refuses to convert a
 * form without that structure and leaves the editor detached from the stored form.</p>
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
        /** Saved by the Visual eForm Editor; it is the default editor. */
        VISUAL,
        /** Has the structure the visual editor can convert; the HTML editor stays the default. */
        CONVERTIBLE,
        /** Only the HTML source editor can change it without losing markup or scripts. */
        HTML
    }

    // A real attribute named id, not the tail of data-id or an "id=" inside some other word.
    private static final String ID_ATTRIBUTE = "(?<![\\w-])id\\s*=\\s*";

    private static final Pattern VISUAL_MARKER = Pattern.compile(
            "<meta\\s+name\\s*=\\s*[\"']" + VISUAL_EDITOR_MARKER_NAME + "[\"']\\s+content\\s*=\\s*[\"']"
                    + VISUAL_EDITOR_MARKER_CONTENT + "[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VISUAL_CONTAINER = Pattern.compile(ID_ATTRIBUTE + "[\"']eform_container[\"']");
    private static final Pattern VISUAL_INPUT_FORM = Pattern.compile(ID_ATTRIBUTE + "[\"']inputForm[\"']");
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
        if (VISUAL_MARKER.matcher(formHtml).find()
                || (VISUAL_CONTAINER.matcher(formHtml).find() && VISUAL_INPUT_FORM.matcher(formHtml).find())) {
            return EditorKind.VISUAL;
        }
        if (BACKGROUND_IMAGE.matcher(formHtml).find() && FIRST_PAGE.matcher(formHtml).find()) {
            return EditorKind.CONVERTIBLE;
        }
        return EditorKind.HTML;
    }
}
