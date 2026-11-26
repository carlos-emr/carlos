/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The constructors of {@link EForm}, each of which sets up the same per-form working state (the field
 * readings and the submitted-value map the select prefill uses) because none delegates to another. Synthetic
 * values only.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EForm constructors")
class EFormConstructorsUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        registerMock(EFormValueDao.class, mock(EFormValueDao.class));
    }

    private static HashMap<String, Object> storedForm() {
        HashMap<String, Object> loaded = new HashMap<>();
        loaded.put("formName", "FAKE form");
        loaded.put("formHtml", "<form></form>");
        loaded.put("formSubject", "FAKE subject");
        return loaded;
    }

    @Test
    void shouldLoadTheStoredForm_whenBuiltFromAFormAndPatient() {
        try (var util = mockStatic(EFormUtil.class)) {
            util.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(storedForm());

            EForm form = new EForm("1", "7");

            assertThat(form.getFid()).isEqualTo("1");
            assertThat(form.getDemographicNo()).isEqualTo("7");
            assertThat(form.getProviderNo()).isNull();
            assertThat(form.getFormName()).isEqualTo("FAKE form");
            assertThat(form.getFormSubject()).isEqualTo("FAKE subject");
        }
    }

    @Test
    void shouldKeepTheProvider_whenBuiltFromAFormPatientAndProvider() {
        try (var util = mockStatic(EFormUtil.class)) {
            util.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(storedForm());

            EForm form = new EForm("1", "7", "99");

            assertThat(form.getFid()).isEqualTo("1");
            assertThat(form.getDemographicNo()).isEqualTo("7");
            assertThat(form.getProviderNo()).isEqualTo("99");
            assertThat(form.getFormName()).isEqualTo("FAKE form");
        }
    }

    @Test
    void shouldDescribeAMissingForm_whenBuiltFromABlankSavedFormId() {
        EForm form = new EForm("");

        assertThat(form.getFormName()).isEmpty();
        assertThat(form.getFormSubject()).isEmpty();
        assertThat(form.getFormHtml()).isEqualTo("No Such Form in Database");
        assertThat(form.getFid()).isNull();
        assertThat(form.getDemographicNo()).isNull();
        assertThat(form.getProviderNo()).isNull();
    }
}
