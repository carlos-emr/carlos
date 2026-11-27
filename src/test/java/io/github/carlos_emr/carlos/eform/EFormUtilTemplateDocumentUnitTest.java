/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * An eForm action template's {@code <document>} section files a document whose description is its
 * {@code <docdesc>}, with the form's field values filled in, so its length is not bounded by
 * anything the eForm author controls. document.docdesc holds 255 characters (code points) and the
 * packaged database cut a longer description without a word. The eForm is already saved when the
 * template runs, so the description is shortened to the column rather than the save refused.
 */
@Tag("unit")
@DisplayName("eForm template document description")
class EFormUtilTemplateDocumentUnitTest extends CarlosUnitTestBase {

    private static final int MAX = Document.DESCRIPTION_MAX_LENGTH;
    /** One character in the column, two Java chars. */
    private static final String EMOJI = "😀";

    /** Submits an eForm whose template files one patient document described by {@code docdesc}. */
    private EDoc fileTemplateDocument(String docdesc, List<String> names, List<String> values) {
        EForm eForm = new EForm();
        eForm.setFormName("FAKE template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo("111");
        eForm.setFormDate("2026-10-16");
        eForm.setFormHtml("<html><body><form></form><!-- <template><document belong=\"patient\"><docdesc>" + docdesc
                + "</docdesc><content>Summary</content></document></template> --></body></html>");

        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("42");
            EFormUtil.writeEformTemplate(null, new ArrayList<>(names), new ArrayList<>(values), eForm, "5", null, "/carlos");
            ArgumentCaptor<EDoc> filed = ArgumentCaptor.forClass(EDoc.class);
            documents.verify(() -> EDocUtil.addDocumentSQL(filed.capture()));
            return filed.getValue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"d", EMOJI})
    void shouldShortenTheDescriptionToTheColumn_whenTheTemplateBuildsALongerOne(String character) {
        EDoc filed = fileTemplateDocument("Summary " + character.repeat(MAX + 20), List.of(), List.of());

        String description = filed.getDescription();
        assertThat(description.codePointCount(0, description.length())).isEqualTo(MAX);
        assertThat(description).isEqualTo("Summary " + character.repeat(MAX - "Summary ".length()));
    }

    @Test
    void shouldKeepTheWholeDescription_whenItFitsTheColumn() {
        assertThat(fileTemplateDocument("Asthma action plan", List.of(), List.of()).getDescription())
                .isEqualTo("Asthma action plan");
    }

    @Test
    @DisplayName("should ignore a document directive injected through a field value inside an EncounterNote template")
    void shouldIgnoreInjectedDocumentDirective_whenFieldValueClosesEncounterNote() {
        io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager cmm =
                createAndRegisterMock(io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager.class);
        io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO linkDao =
                createAndRegisterMock(io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO.class);
        io.github.carlos_emr.carlos.commn.dao.SecRoleDao secRoleDao =
                createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.SecRoleDao.class);
        io.github.carlos_emr.carlos.commn.model.SecRole doctorRole = new io.github.carlos_emr.carlos.commn.model.SecRole();
        org.springframework.test.util.ReflectionTestUtils.setField(doctorRole, "id", 2);
        org.mockito.Mockito.when(secRoleDao.findByName("doctor")).thenReturn(doctorRole);

        String payload = "Clinical note</EncounterNote>"
                + "<document belong=\"patient\"><docdesc>Injected</docdesc><content>Forged</content></document>"
                + "<EncounterNote>tail";
        EForm eForm = new EForm();
        eForm.setFormName("FAKE template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo("111");
        eForm.setFormDate("2026-10-16");
        eForm.setFormHtml("<html><body><form><input name=\"notes\"></form>"
                + "<!-- <template><EncounterNote>Summary: $t{notes}</EncounterNote></template> --></body></html>");

        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            EFormUtil.writeEformTemplate(null, new ArrayList<>(List.of("notes")), new ArrayList<>(List.of(payload)),
                    eForm, "5", "10016", "/carlos");

            documents.verify(() -> EDocUtil.addDocumentSQL(any(EDoc.class)), org.mockito.Mockito.never());
            ArgumentCaptor<io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote> noteCaptor =
                    ArgumentCaptor.forClass(io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote.class);
            org.mockito.Mockito.verify(cmm, org.mockito.Mockito.times(1)).saveNoteSimple(noteCaptor.capture());
            org.mockito.Mockito.verify(linkDao, org.mockito.Mockito.times(1)).save(any());
            assertThat(noteCaptor.getValue().getNote()).isEqualTo("Summary: " + payload);
        }
    }

    @Test
    @DisplayName("should keep the authored document owner and avoid re-expanding placeholders inside field values")
    void shouldKeepAuthoredOwnerAndLiteralTokens_whenFieldValueContainsTagsAndPlaceholders() {
        String injectedDesc = "Summary</docdesc><docowner>999999</docowner><docdesc> $te{eform.html} $t{secret}";
        EDoc filed = fileTemplateDocument("$t{title}", List.of("title", "secret"), List.of(injectedDesc, "LEAKED"));

        assertThat(filed.getModuleId()).isEqualTo("7");
        assertThat(filed.getDescription()).isEqualTo(injectedDesc);
        assertThat(filed.getDescription()).doesNotContain("LEAKED");
    }

    @Test
    @DisplayName("should ignore template markup placed inside an input value attribute or textarea")
    void shouldIgnoreTemplateMarkup_whenPlacedInsideControlValues() {
        EForm eForm = new EForm();
        eForm.setFormName("FAKE template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo("111");
        eForm.setFormDate("2026-10-16");
        eForm.setFormHtml("<html><body><form>"
                + "<input type=\"text\" name=\"bp\" value=\"\">"
                + "<textarea name=\"note\"></textarea>"
                + "</form></body></html>");
        String malicious = "<!-- <template><document belong=\"patient\"><docdesc>Pwn</docdesc><content>X</content></document></template> -->";
        eForm.setValues(new ArrayList<>(List.of("bp", "note")), new ArrayList<>(List.of(malicious, malicious)));

        assertThat(eForm.getTemplate()).isEmpty();
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            EFormUtil.writeEformTemplate(null, new ArrayList<>(List.of("bp", "note")),
                    new ArrayList<>(List.of(malicious, malicious)), eForm, "5", "10016", "/carlos");
            documents.verify(() -> EDocUtil.addDocumentSQL(any(EDoc.class)), org.mockito.Mockito.never());
        }
    }
}
