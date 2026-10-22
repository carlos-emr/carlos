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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source regressions for making the Visual eForm Editor the eForm Library's default editor
 * (issue #3166). The behaviour itself is driven in a browser by
 * {@code scripts/eform-visual-editor-default-edit-playwright-checks.js}; these pin the invariants a
 * refactor could silently drop.
 *
 * @since 2026-10-22
 */
@DisplayName("Visual eForm Editor default edit regressions")
@Tag("unit")
@Tag("eform")
class VisualEFormEditorDefaultEditRegressionTest {

    private static final Path EFORM_JSP_DIR = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "eform");
    private static final Path VISUAL_EDITOR_JSP = EFORM_JSP_DIR.resolve("visualEformEditor.jsp");
    private static final Path LIBRARY_JSP = EFORM_JSP_DIR.resolve("efmformmanager.jsp");
    private static final Path FOOTER_JSPF = EFORM_JSP_DIR.resolve("efmFooter.jspf");
    private static final Path LEFT_NAV_JSPF =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "administration", "leftNav.jspf");
    private static final Path STRUTS_EFORM =
            Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts-eform.xml");

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("should stamp the same marker the library routes on, over the finished markup")
    void shouldShareMarker_betweenEditorAndResolver() throws Exception {
        String jsp = read(VISUAL_EDITOR_JSP);

        assertThat(jsp)
                .contains("var VISUAL_EDITOR_MARKER_NAME = \"" + EFormEditorResolver.VISUAL_EDITOR_MARKER_NAME + "\";")
                .contains("var VISUAL_EDITOR_MARKER_CONTENT = \""
                        + EFormEditorResolver.VISUAL_EDITOR_MARKER_CONTENT + "\";")
                .contains("+ \"' data-fingerprint='\" + fingerprint + \"'>\";");
        // The fingerprint must cover the markup exactly as stored, so it is stamped after the last rewrite.
        int normalize = jsp.indexOf("source = source.replace(/>\\s*</g, \">\\n<\");");
        int stamp = jsp.indexOf("source = stampVisualEditorMarker(source);");
        assertThat(normalize).isPositive();
        assertThat(stamp).isGreaterThan(normalize);
    }

    @Test
    @DisplayName("should attach the editor to a stored form only after it loaded onto the canvas")
    void shouldAttachFormId_onlyAfterSuccessfulLoad() throws Exception {
        String jsp = read(VISUAL_EDITOR_JSP);
        String loader = jsp.substring(jsp.indexOf("function loadEformById(id)"),
                jsp.indexOf("function showEditingBanner("));

        assertThat(loader).contains("loaded = (loadEformData(formHtml) === true);");
        assertThat(loader.indexOf("if (!loaded) { return; }"))
                .as("the id must only be attached after the load succeeded")
                .isPositive()
                .isLessThan(loader.indexOf("setEformId(data.body.id);"));
        assertThat(jsp)
                .as("a refused conversion must report false, not undefined")
                .contains("as it lacks any recognisable background images.\"); return false;")
                .contains("as it lacks a recognisable page structure.\"); return false;")
                .as("the in-page Load button goes through the same guarded loader")
                .contains("if (selectedId > 0) { loadEformById(selectedId); }")
                .as("an older load answering late must not replace a newer one")
                .contains("var seq = ++eformLoadSeq;")
                .contains("if (seq !== eformLoadSeq) { return; // superseded by a later load")
                .as("saving while a stored form is still loading would create a copy of it")
                .contains("function saveToOscarEforms(include_fax) { if (eformLoadPending) {");
    }

    @Test
    @DisplayName("should pre-load only the id the gate validated")
    void shouldPreloadValidatedFid_fromGateAttribute() throws Exception {
        String jsp = read(VISUAL_EDITOR_JSP);

        assertThat(jsp)
                .contains("request.getAttribute(\"visualEditorInitialFid\")")
                .contains("var INITIAL_EFORM_FID = <%= visualEditorInitialFid %>;")
                .contains("if (INITIAL_EFORM_FID > 0) { loadEformById(INITIAL_EFORM_FID); }")
                .doesNotContain("request.getParameter(\"fid\")");
        assertThat(read(STRUTS_EFORM)).contains(
                "<action name=\"eform/visualEformEditor\" "
                        + "class=\"io.github.carlos_emr.carlos.eform.gate.ViewVisualEFormEditor2Action\"/>");
    }

    @Test
    @DisplayName("should keep the HTML source editor reachable from every library row")
    void shouldKeepHtmlEditorLink_onEveryLibraryRow() throws Exception {
        String jsp = read(LIBRARY_JSP);

        assertThat(jsp)
                .contains("EFormUtil.listEFormsWithEditor(orderBy, EFormUtil.CURRENT)")
                .as("without _admin.eform write the visual editor is unreachable, so every row stays HTML")
                .contains("EFormEditorResolver.EditorKind editorKind = tableIsEFormAdmin "
                        + "? (EFormEditorResolver.EditorKind) curForm.get(\"editorKind\") "
                        + ": EFormEditorResolver.EditorKind.HTML;");
        // One HTML-editor link in the visual branch (Advanced) and one in the other branch (the pencil).
        assertThat(jsp.split("/eform/efmformmanageredit\\?fid=", -1)).hasSize(3);
        assertThat(jsp.split("/eform/visualEformEditor\\?fid=", -1)).hasSize(3);
    }

    @Test
    @DisplayName("should frame the visual editor inside the Administration panel instead of injecting it")
    void shouldFrameVisualEditor_insideAdministrationPanel() throws Exception {
        assertThat(read(FOOTER_JSPF))
                .contains("$(document).on(\"click\", '#dynamic-content a.eformVisualEditorLink', function(e) {")
                .contains("window.carlosOpenInAdminFrame(href, $(this).attr(\"title\"));");
        assertThat(read(LEFT_NAV_JSPF))
                .contains("function carlosOpenInAdminFrame(source, iframeTitle, submitForm) {")
                .contains("carlosOpenInAdminFrame($(this).attr('rel'), iframeTitle, $(this).data('submit-form'));");
    }
}
