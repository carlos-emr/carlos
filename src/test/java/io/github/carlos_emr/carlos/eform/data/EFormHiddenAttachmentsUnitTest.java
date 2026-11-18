/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EFormHiddenAttachmentsUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepAttachedForms_whenAnEditDateIsMissing() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        var form = new EForm();
        form.setFormHtml("<form></form>");
        var undated = new PatientForm("BPMH", 21, 101, null, null);
        var dated = new PatientForm("BPMH", 22, 101, null, Timestamp.valueOf("2026-03-17 09:30:00"));

        form.addHiddenAttachments(List.of(), List.of(), List.of(), List.of(), List.of(undated, dated));

        var document = Jsoup.parse(form.getFormHtml());
        var undatedEntry = document.selectFirst("#entry_formNo21[data-formdate]");
        assertThat(undatedEntry).isNotNull();
        assertThat(undatedEntry.attr("data-formdate")).isEmpty();
        assertThat(document.selectFirst("#entry_formNo22").attr("data-formdate")).isEqualTo("17-03-2026 09:30:00");
        assertThat(document.select("[name=formNo]").eachAttr("value")).containsExactly("21", "22");
    }
}
