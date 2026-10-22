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

import io.github.carlos_emr.carlos.eform.EFormEditorResolver.EditorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EFormEditorResolver}, which decides whether the eForm Library's Edit action
 * opens a template in the Visual eForm Editor or keeps the HTML source editor (issue #3166).
 *
 * @since 2026-10-22
 */
@DisplayName("EFormEditorResolver")
@Tag("unit")
@Tag("eform")
class EFormEditorResolverUnitTest {

    /** The head the visual editor writes since #3166, as generate_eform_source_html builds it. */
    private static final String MARKED_VISUAL_FORM = "<!DOCTYPE html><html><head>\n"
            + "<META http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n"
            + "<meta name='carlos-eform-editor' content='visual'>\n"
            + "<title>Marked</title></head><body><div id='eform_container' style='max-width: 750px'>\n"
            + "<form id=\"inputForm\" action=\"get\"></form></div></body></html>";

    /** What the visual editor saved before the marker existed. */
    private static final String LEGACY_VISUAL_FORM = "<!DOCTYPE html><html><head><title>Legacy</title></head>"
            + "<body onload='focusAll();'><div id='eform_container' style='max-width: 750px'>\n"
            + "<form id=\"inputForm\" action=\"get\"><div id=\"page_1\" class=\"page_container\"></div></form>"
            + "</div></body></html>";

    /** The eForm Generator's page structure: a positioned page div with a background image. */
    private static final String GENERATOR_FORM = "<html><head><title>Generated</title>"
            + "<script>function custom(){ /* clinic code */ }</script></head><body>\n"
            + "<div id=\"page1\" style=\"position:relative;\" >\n"
            + "<img id=\"BGImage1\" src=\"${oscar_image_path}form.png\" style=\"position: relative;\">\n"
            + "<input name=\"patient\" type=\"text\" oscarDB=\"patient_name\"></div></body></html>";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("should keep the HTML editor for missing or blank markup")
    void shouldReturnHtml_forMissingOrBlankMarkup(String formHtml) {
        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should open the visual editor for a template carrying its marker")
    void shouldReturnVisual_whenMarkerPresent() {
        assertThat(EFormEditorResolver.resolve(MARKED_VISUAL_FORM)).isEqualTo(EditorKind.VISUAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<meta name=\"carlos-eform-editor\" content=\"visual\">",
            "<META NAME='CARLOS-EFORM-EDITOR' CONTENT='VISUAL'>",
            "<meta  name = 'carlos-eform-editor'   content = 'visual' />"
    })
    @DisplayName("should recognise the marker regardless of quoting, case and spacing")
    void shouldReturnVisual_forMarkerSpellingVariants(String marker) {
        String formHtml = "<html><head>" + marker + "<title>t</title></head><body></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.VISUAL);
    }

    @Test
    @DisplayName("should open the visual editor for a form it saved before the marker existed")
    void shouldReturnVisual_forLegacyVisualEditorShell() {
        assertThat(EFormEditorResolver.resolve(LEGACY_VISUAL_FORM)).isEqualTo(EditorKind.VISUAL);
    }

    @Test
    @DisplayName("should not treat a marker naming another editor as the visual editor's")
    void shouldReturnHtml_whenMarkerNamesAnotherEditor() {
        String formHtml = "<html><head><meta name='carlos-eform-editor' content='html'></head>"
                + "<body><p>Hand written</p></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should not treat a container without the visual editor's input form as visual")
    void shouldReturnHtml_whenContainerHasNoInputForm() {
        String formHtml = "<html><body><div id='eform_container'><form id='myForm'></form></div></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should offer conversion, not default to it, for an eForm Generator page structure")
    void shouldReturnConvertible_forGeneratorPageStructure() {
        assertThat(EFormEditorResolver.resolve(GENERATOR_FORM)).isEqualTo(EditorKind.CONVERTIBLE);
    }

    @Test
    @DisplayName("should keep the HTML editor when the background image is missing")
    void shouldReturnHtml_whenBackgroundImageMissing() {
        String formHtml = "<html><body><div id=\"page1\" style=\"position:relative;\"><input name=\"a\"></div>"
                + "</body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should keep the HTML editor when the page structure is missing")
    void shouldReturnHtml_whenPageStructureMissing() {
        String formHtml = "<html><body><img id=\"BGImage\" src=\"x.png\"><input name=\"a\"></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should ignore data-id attributes that only look like the structure ids")
    void shouldReturnHtml_forLookalikeDataIdAttributes() {
        String formHtml = "<html><body><div data-id=\"page1\"><img data-id=\"BGImage1\" src=\"x.png\"></div>"
                + "<div data-id='eform_container'><form data-id='inputForm'></form></div></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should keep a typical hand-authored form such as a letter template in the HTML editor")
    void shouldReturnHtml_forHandAuthoredForm() {
        String formHtml = "<html><head><title>Letter</title><script src='../js/jquery.js'></script>"
                + "<script>function init(){ document.getElementById('body').focus(); }</script></head>"
                + "<body onload='init()'><form method='post' action=''><textarea id='body'></textarea>"
                + "</form></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should expose the marker the visual editor writes")
    void shouldExposeMarkerConstants_forVisualEditor() {
        assertThat(EFormEditorResolver.VISUAL_EDITOR_MARKER_NAME).isEqualTo("carlos-eform-editor");
        assertThat(EFormEditorResolver.VISUAL_EDITOR_MARKER_CONTENT).isEqualTo("visual");
    }
}
