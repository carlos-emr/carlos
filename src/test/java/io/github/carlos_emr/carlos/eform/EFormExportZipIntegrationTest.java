/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Verifies persisted eForms retain original content and metadata through ZIP export. @since 2026.08 */
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
class EFormExportZipIntegrationTest extends CarlosTestBase {
    @Autowired private EFormDao dao;
    @Autowired private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Well Baby 0/6 months|WellBaby0_6months", "Ça \"va\" Łódź|Ça_va_Łódź"})
    void shouldExportOriginalDatabaseContent_whenDisplayNameRequiresEncoding(String title, String folder) throws Exception {
        EForm stored = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(stored);
        stored.setFormName(title);
        stored.setFileName("fixture.html");
        stored.setFormHtml("<html>Zoë Ł &amp; bébé\r\n</html>");
        dao.persist(stored);
        hibernateTemplate.flush();
        hibernateTemplate.clear();
        var loaded = new io.github.carlos_emr.carlos.eform.data.EForm(stored.getId().toString(), "1");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new EFormExportZip().exportForms(List.of(loaded), bytes);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo(folder + "/eform.properties");
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(zip.readAllBytes()));
            assertThat(properties.getProperty("form.name")).isEqualTo(title);
            assertThat(properties.getProperty("form.htmlFilename")).isEqualTo("fixture.html");
            assertThat(zip.getNextEntry().getName()).isEqualTo(folder + "/fixture.html");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(stored.getFormHtml());
            assertThat(zip.getNextEntry()).isNull();
        }
        hibernateTemplate.clear();
        assertThat(dao.find(stored.getId()).getFormName()).isEqualTo(title);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollBackFirstRegistrationAndItsImage_whenTheSecondRegistrationFails(@TempDir Path images)
            throws Exception {
        var entries = new LinkedHashMap<String, String>();
        entries.put("one/eform.properties", "form.name=Rollback first ZIP form\nform.htmlFilename=one.html\n");
        entries.put("one/one.html", "<img src='diagram.png'>");
        entries.put("two/eform.properties", "form.name=Rollback second ZIP form\nform.htmlFilename=two.html\n");
        entries.put("two/two.html", "<img src='preexisting.png'>");
        entries.put("diagram.png", "PROVISIONAL");
        entries.put("preexisting.png", "ARCHIVE COPY");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        Path existing = Files.writeString(images.resolve("preexisting.png"), "EXISTING");
        Path provisional = images.resolve("diagram.png");
        List<String> attemptedNames = new ArrayList<>();
        EFormDao faultingDao = mock(EFormDao.class, delegatesTo(dao));
        doAnswer(call -> {
            EForm form = call.getArgument(0);
            attemptedNames.add(form.getFormName());
            if (attemptedNames.size() == 2) {
                assertThat(dao.findMaxIdForActiveForm(attemptedNames.getFirst())).isNotNull();
                assertThat(Files.readString(provisional)).isEqualTo("PROVISIONAL");
                throw new IllegalStateException("second registration failed");
            }
            dao.persist(form);
            return null;
        }).when(faultingDao).persist(any());
        try (var folders = mockStatic(ImageUpload2Action.class); var beans = mockStatic(SpringUtils.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            beans.when(() -> SpringUtils.getBean(EFormDao.class)).thenReturn(faultingDao);
            beans.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactionManager);

            assertThatThrownBy(() -> new EFormExportZip().importForm(new ByteArrayInputStream(bytes.toByteArray())))
                    .isInstanceOf(IllegalStateException.class).hasMessage("second registration failed");
        }
        assertThat(attemptedNames).containsExactlyInAnyOrder("Rollback first ZIP form", "Rollback second ZIP form");
        for (String name : attemptedNames) {
            verify(faultingDao, times(2)).findMaxIdForActiveForm(name);
        }
        verify(faultingDao, never()).findByName(any());
        assertThat(attemptedNames).allSatisfy(name -> assertThat(dao.findMaxIdForActiveForm(name)).isNull());
        assertThat(provisional).doesNotExist();
        assertThat(Files.readString(existing)).isEqualTo("EXISTING");
    }
}
