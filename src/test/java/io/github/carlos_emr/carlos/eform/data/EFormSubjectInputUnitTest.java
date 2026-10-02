/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins how a saved eForm's subject reaches templates without their own subject control
 * (issue #4027): the persisted subject on reopen, and never the catalog description on a
 * new instance or the admin preview.
 */
@Tag("unit")
@Tag("fast")
class EFormSubjectInputUnitTest extends CarlosUnitTestBase {
    private static final Path ADD_JSP = Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformadd_data.jsp");
    private static final Path SHOW_JSP = Path.of("src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp");

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    void shouldPreserveStoredSubject_whenTemplateHasNoControl() {
        EForm form = new EForm();
        form.setFormHtml("<html><body><form id='letter'></form></body></html>");
        String subject = "Follow-up & \"results\" <script>alert(1)</script>";
        form.ensureSubjectInput(subject);
        form.ensureSubjectInput(subject);
        var document = Jsoup.parse(form.getFormHtml());
        assertThat(document.select("form [name=subject]")).hasSize(1);
        assertThat(document.selectFirst("form [name=subject]").val()).isEqualTo(subject);
        assertThat(document.select("script")).isEmpty();
    }

    @Test
    void shouldEscapeSubjectExactlyOnce_inSerializedHtml() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureSubjectInput("A & B");
        String html = form.getFormHtml();
        assertThat(html).contains("value=\"A &amp; B\"").doesNotContain("&amp;amp;");
    }

    @Test
    void shouldPreserveTemplateControl_whenValueIsIntentionallyEmpty() {
        EForm form = new EForm();
        form.setFormHtml("<form><input name='subject' value=''></form>");
        form.ensureSubjectInput("Old subject");
        var fields = Jsoup.parse(form.getFormHtml()).select("[name=subject]");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEmpty();
        assertThat(fields.first().attr("type")).isEmpty();
    }

    @Test
    void shouldInitializeEmptySubject_whenSubjectIsNull() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureSubjectInput(null);
        var field = Jsoup.parse(form.getFormHtml()).selectFirst("[name=subject]");
        assertThat(field).isNotNull();
        assertThat(field.val()).isEmpty();
    }

    @Test
    void shouldIgnoreCatalogDescription_whenCallerSuppliesEmptySubject() {
        // A catalog-loaded EForm carries the template's description as its subject.
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.setFormSubject("Rich Text Letter Generator v2026.3.0");
        form.ensureSubjectInput("");
        assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("[name=subject]").val()).isEmpty();
    }

    @Test
    void shouldStartNewFormsEmpty_insteadOfCatalogDescription() throws IOException {
        String jsp = Files.readString(ADD_JSP, StandardCharsets.UTF_8);
        assertThat(jsp).contains("thisEForm.ensureSubjectInput(\"\");")
                .doesNotContain("ensureSubjectInput(thisEForm.getFormSubject())");
    }

    @Test
    void shouldSupplyPersistedSubject_onlyForSavedInstances() throws IOException {
        String jsp = Files.readString(SHOW_JSP, StandardCharsets.UTF_8);
        // fid == null is the saved-instance (fdid) branch; the fid branch is the catalog preview.
        assertThat(jsp).contains("eForm.ensureSubjectInput(fid == null ? eForm.getFormSubject() : \"\");");
    }

    @Test
    void shouldPreserveAuthoredNewFormFlag_whenTemplateUsesCaseSensitiveInitialization() {
        EForm form = new EForm();
        form.setFormHtml("<form><input type='hidden' id='newForm' name='newForm' value='True'></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("#newForm");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEqualTo("True");
        assertThat(fields.first().attr("name")).isEqualTo("newForm");
        assertThat(fields.first().parent().tagName()).isEqualTo("form");
    }

    @Test
    void shouldSupplyNewFormFlag_whenTemplateHasNone() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("#newForm");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEqualTo("true");
        assertThat(fields.first().attr("type")).isEqualTo("hidden");
        assertThat(fields.first().attr("name")).isEqualTo("newForm");
        assertThat(fields.first().parent().tagName()).isEqualTo("form");
    }
}
