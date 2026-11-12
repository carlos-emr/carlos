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

    /** What the visual editor generates, before it stamps its marker in front of the title. */
    private static final String GENERATED = "<!DOCTYPE html>\n<html>\n<head>\n"
            + "<META http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n"
            + "<title>Visual</title>\n</head>\n<body onload='focusAll();'>\n"
            + "<div id='eform_container' style='max-width: 750px'>\n<form id=\"inputForm\" action=\"get\">\n"
            + "<div id=\"page_1\" class=\"page_container\"><div class=\"input_elements\"></div></div>\n"
            + "</form>\n</div>\n</body>\n</html>";

    /** The eForm Generator's page structure: a positioned page div with a background image. */
    private static final String GENERATOR_FORM = "<html><head><title>Generated</title>"
            + "<script>function custom(){ /* clinic code */ }</script></head><body>\n"
            + "<div id=\"page1\" style=\"position:relative;\" >\n"
            + "<img id=\"BGImage1\" src=\"${oscar_image_path}form.png\" style=\"position: relative;\">\n"
            + "<input name=\"patient\" type=\"text\" oscarDB=\"patient_name\"></div></body></html>";

    /** Stamps the marker the way the editor's stampVisualEditorMarker() does. */
    private static String stamped(String generated) {
        String tag = "<meta name='carlos-eform-editor' content='visual' data-fingerprint='"
                + EFormEditorResolver.fingerprint(generated) + "'>";
        int at = generated.indexOf("<title>");
        return generated.substring(0, at) + tag + generated.substring(at);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("should keep the HTML editor for missing or blank markup")
    void shouldReturnHtml_forMissingOrBlankMarkup(String formHtml) {
        assertThat(EFormEditorResolver.resolve(formHtml)).isEqualTo(EditorKind.HTML);
    }

    @Test
    @DisplayName("should open the visual editor for a form it saved and nobody changed since")
    void shouldReturnVisual_whenMarkerFingerprintMatches() {
        assertThat(EFormEditorResolver.resolve(stamped(GENERATED))).isEqualTo(EditorKind.VISUAL);
    }

    @Test
    @DisplayName("should demote a visual form changed afterwards in the HTML editor to explicit conversion")
    void shouldReturnConvertible_whenFormChangedAfterVisualSave() {
        String edited = stamped(GENERATED).replace("</head>", "<script>function clinicCustom(){}</script></head>");

        assertThat(EFormEditorResolver.resolve(edited)).isEqualTo(EditorKind.CONVERTIBLE);
    }

    @Test
    @DisplayName("should treat even a whitespace-only change outside the editor as unverified")
    void shouldReturnConvertible_whenLineEndingsChanged() {
        String edited = stamped(GENERATED).replace("\n", "\r\n");

        assertThat(EFormEditorResolver.resolve(edited)).isEqualTo(EditorKind.CONVERTIBLE);
    }

    @Test
    @DisplayName("should not accept a re-quoted copy of the marker as proof")
    void shouldNotTreatRequotedMarker_asProof() {
        String requoted = stamped(GENERATED).replace("<meta name='carlos-eform-editor' content='visual' data-fingerprint='",
                "<meta name=\"carlos-eform-editor\" content=\"visual\" data-fingerprint='");

        // Still the editor's page shell, so it can be converted, but it is not proven unchanged.
        assertThat(EFormEditorResolver.resolve(requoted)).isEqualTo(EditorKind.CONVERTIBLE);
    }

    @Test
    @DisplayName("should not route a hand-written form to the visual editor because it quotes the marker text")
    void shouldNotReturnVisual_whenMarkerTextIsQuotedInScript() {
        String formHtml = "<html><head><title>Letter</title><script>var note = "
                + "\"<meta name='carlos-eform-editor' content='visual' data-fingerprint='00000000'>\";</script>"
                + "</head><body><form method='post'><textarea name='body'></textarea></form></body></html>";

        assertThat(EFormEditorResolver.resolve(formHtml)).isNotEqualTo(EditorKind.VISUAL);
    }

    @Test
    @DisplayName("should offer, not default to, the visual editor for a form it saved before the marker existed")
    void shouldReturnConvertible_forLegacyVisualEditorShell() {
        assertThat(EFormEditorResolver.resolve(GENERATED)).isEqualTo(EditorKind.CONVERTIBLE);
    }

    @Test
    @DisplayName("should not treat the shell's ids on unrelated elements as the visual editor's shell")
    void shouldReturnHtml_whenShellIdsAreOnUnrelatedElements() {
        String formHtml = "<html><body><div id='eform_container'><p>Intro</p></div>"
                + "<script>function go(){}</script><form id='inputForm' onsubmit='go()'></form></body></html>";

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
    @DisplayName("should compute the 32-bit FNV-1a fingerprint the editor's JavaScript computes")
    void shouldMatchFnv1aReferenceValues_forFingerprint() {
        assertThat(EFormEditorResolver.fingerprint("")).isEqualTo("811c9dc5");
        assertThat(EFormEditorResolver.fingerprint("a")).isEqualTo("e40c292c");
        assertThat(EFormEditorResolver.fingerprint("foobar")).isEqualTo("bf9cf968");
        // Over UTF-16 code units, so a surrogate pair hashes as two units, exactly as charCodeAt does.
        assertThat(EFormEditorResolver.fingerprint("\u00e9\u20ac\ud83d\ude00")).isEqualTo("0c5e230d");
    }

    @Test
    @DisplayName("should expose the marker the visual editor writes")
    void shouldExposeMarkerConstants_forVisualEditor() {
        assertThat(EFormEditorResolver.VISUAL_EDITOR_MARKER_NAME).isEqualTo("carlos-eform-editor");
        assertThat(EFormEditorResolver.VISUAL_EDITOR_MARKER_CONTENT).isEqualTo("visual");
    }
}
