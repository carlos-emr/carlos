/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.EFormExportZip;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Real bounded ZIP refusal and action result controls for the eForm import page. */
@Tag("unit")
@Tag("eform")
class ManageEFormImportErrorsUnitTest extends CarlosUnitTestBase {
    @TempDir Path temporary;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        request.setMethod("POST");
        request.setParameter("method", "importEForm");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    @Test
    void shouldNotBindZippedForm_fromRequestParameters() throws NoSuchMethodException {
        var setter = ManageEForm2Action.class.getMethod("setZippedForm", java.io.File.class);
        assertThat(setter.isAnnotationPresent(org.apache.struts2.interceptor.parameter.StrutsParameter.class)).isFalse();
    }

    private ManageEForm2Action action(Path archive) {
        ManageEForm2Action action = spy(new ManageEForm2Action());
        action.setZippedForm(archive.toFile());
        // There is no Struts container in this unit test; use its shipped English bundle.
        doAnswer(call -> ResourceBundle.getBundle("oscarResources", Locale.ENGLISH)
                .getString(call.getArgument(0))).when(action).getText(anyString());
        return action;
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pt-BR", "pl"})
    void shouldProvideTheImportRefusal_inEverySupportedLanguage(String language) {
        Locale locale = Locale.forLanguageTag(language);
        String message = ResourceBundle.getBundle("oscarResources", locale).getString("eform.importReadFailed");
        assertThat(message).isNotBlank().contains("ZIP").doesNotContain("\uFFFD");
        assertThat(new java.text.MessageFormat(message, locale).format(new Object[0]))
                .isNotBlank().contains("ZIP").doesNotContain("{0}");
    }

    @Test
    void shouldReturnImportErrors_whenRealZipTextBudgetIsExceeded() throws Exception {
        Path archive = temporary.resolve("oversized.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("eform.properties"));
            byte[] block = new byte[8192];
            for (int i = 0; i < 2049; i++) zip.write(block);
            zip.closeEntry();
        }
        assertThat(Files.size(archive)).isLessThan(100_000L);
        Path images = Files.createDirectory(temporary.resolve("images"));
        try (var folders = mockStatic(ImageUpload2Action.class); var forms = mockStatic(EFormUtil.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(action(archive).execute()).isEqualTo("fail");
            assertThat((List<?>) request.getAttribute("importErrors")).hasSize(1);
            assertThat(request.getAttribute("status")).isNull();
            forms.verify(() -> EFormUtil.saveEForm(any(EForm.class)), never());
        }
        try (var staged = Files.list(images.resolve("extractFolder"))) {
            assertThat(staged).isEmpty();
        }
    }

    @Test
    void shouldKeepTheImportFormOpen_whenNoFileWasUploaded() throws Exception {
        try (var importer = mockConstruction(EFormExportZip.class)) {
            assertThat(new ManageEForm2Action().execute()).isEqualTo("fail");
            assertThat(importer.constructed()).isEmpty();
        }
        assertThat(request.getAttribute("input")).isEqualTo("import");
        assertThat(request.getAttribute("importErrors")).isEqualTo(List.of("No file was uploaded."));
        assertThat(request.getAttribute("status")).isNull();
    }

    @Test
    void shouldReturnSafeImportErrors_whenTheUploadedFileCannotBeRead() throws Exception {
        Path missing = temporary.resolve("private-server-file.zip");
        assertThat(action(missing).execute()).isEqualTo("fail");
        assertThat((List<?>) request.getAttribute("importErrors")).hasSize(1);
        assertThat(request.getAttribute("importErrors").toString())
                .doesNotContain(missing.toString()).contains("import");
        assertThat(request.getAttribute("status")).isNull();
        assertThat(request.getAttribute("input")).isEqualTo("import");
    }

    @Test
    void shouldLogSafeOperationalDiagnostics_whenImportIoFails() throws Exception {
        Path archive = Files.createFile(temporary.resolve("unreadable-entry.zip"));
        var failure = new java.io.IOException("FAKE-private-archive-name\nFAKE-forged-log-line");
        try (var capture = LogCapture.forLogger(ManageEForm2Action.class);
             var _ = mockConstruction(EFormExportZip.class, (mock, context) ->
                     when(mock.importForm(any())).thenThrow(failure))) {
            assertThat(action(archive).execute()).isEqualTo("fail");
            var warnings = capture.events().stream()
                    .filter(event -> event.getLevel() == org.apache.logging.log4j.Level.WARN).toList();
            assertThat(warnings).hasSize(1);
            assertThat(warnings.getFirst().getThrown()).isNull();
            assertThat(warnings.getFirst().getMessage().getFormattedMessage())
                    .contains("java.io.IOException")
                    .doesNotContain("FAKE-private-archive-name", "FAKE-forged-log-line");
            assertThat(request.getAttribute("input")).isEqualTo("import");
        }
    }

    @Test
    void shouldKeepWarnings_onTheImportPage() throws Exception {
        Path archive = Files.createFile(temporary.resolve("warnings.zip"));
        try (var _ = mockConstruction(EFormExportZip.class, (mock, context) ->
                when(mock.importForm(any())).thenReturn(List.of("Synthetic invalid form")))) {
            assertThat(action(archive).execute()).isEqualTo("fail");
            assertThat(request.getAttribute("importErrors")).isEqualTo(List.of("Synthetic invalid form"));
            assertThat(request.getAttribute("status")).isNull();
        }
    }

    @Test
    void shouldKeepSuccess_afterAnAcceptedImport() throws Exception {
        Path archive = Files.createFile(temporary.resolve("accepted.zip"));
        try (var _ = mockConstruction(EFormExportZip.class, (mock, context) ->
                when(mock.importForm(any())).thenReturn(List.of()))) {
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getAbsolutePath()).thenReturn(archive.toString());
            ManageEForm2Action action = new ManageEForm2Action();
            action.withUploadedFiles(List.of(upload));
            assertThat(action.execute()).isEqualTo("success");
            assertThat(request.getAttribute("status")).isEqualTo("success");
            assertThat(request.getAttribute("importErrors")).isNull();
        }
    }

    @Test
    void shouldDenyImport_beforeReadingAnUpload() {
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);
        ManageEForm2Action denied = action(temporary.resolve("missing.zip"));
        assertThatThrownBy(denied::execute)
                .isInstanceOf(SecurityException.class);
        assertThat(request.getAttribute("importErrors")).isNull();
    }
}
