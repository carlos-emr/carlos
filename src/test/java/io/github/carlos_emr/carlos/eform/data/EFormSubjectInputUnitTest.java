/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
@Tag("fast")
class EFormSubjectInputUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    void shouldPreserveStoredSubject_whenTemplateHasNoControl() {
        EForm form = new EForm();
        form.setFormHtml("<html><body><form id='letter'></form></body></html>");
        String subject = "Follow-up & \"results\" <script>alert(1)</script>";
        form.setFormSubject(subject);
        form.ensureSubjectInput();
        form.ensureSubjectInput();
        var document = Jsoup.parse(form.getFormHtml());
        assertThat(document.select("form [name=subject]")).hasSize(1);
        assertThat(document.selectFirst("form [name=subject]").val()).isEqualTo(subject);
        assertThat(document.select("script")).isEmpty();
    }

    @Test
    void shouldPreserveTemplateControl_whenValueIsIntentionallyEmpty() {
        EForm form = new EForm();
        form.setFormHtml("<form><input name='subject' value=''></form>");
        form.setFormSubject("Old subject");
        form.ensureSubjectInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("[name=subject]");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEmpty();
        assertThat(fields.first().attr("type")).isEmpty();
    }

    @Test
    void shouldInitializeNewForm_withoutUndefinedSubject() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureSubjectInput();
        assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("[name=subject]").val()).isEmpty();
    }
}
