/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies persisted eForms retain original content and metadata through ZIP export. @since 2026.08 */
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
class EFormExportZipIntegrationTest extends CarlosTestBase {
    @Autowired private EFormDao dao;

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
}
