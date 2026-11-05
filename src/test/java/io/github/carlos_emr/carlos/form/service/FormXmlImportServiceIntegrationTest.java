/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real service proxy commits/rollbacks; also runs against the opt-in physical MariaDB harness. */
@Tag("integration")
@Tag("dao")
@Tag("encounter")
@ContextConfiguration(locations = "classpath:test-form-xml-import-context.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FormXmlImportServiceIntegrationTest extends CarlosTestBase {
    private final String table = "formIssue5547_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired private FormXmlImportService service;
    @Autowired private EncounterFormDao forms;
    @Autowired private EFormValueDao apValues;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("dataSource") private DataSource dataSource;
    @TempDir Path temporary;
    private JdbcTemplate jdbc;
    private EncounterForm registration;

    @BeforeEach
    void createOwnedTable() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE " + table + " (ID INT AUTO_INCREMENT PRIMARY KEY, demographic_no INT NOT NULL,"
                + " formCreated DATE DEFAULT '2026-01-02', formEdited DATETIME(6), notes VARCHAR(255), rawHistory VARCHAR(255))");
        registration = new EncounterForm();
        registration.setFormName(table);
        registration.setFormTable(table);
        registration.setFormValue("../form/SetupForm.do?formName=Issue5547");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> forms.persist(registration));
    }

    @AfterEach
    void removeOwnedTable() {
        if (jdbc != null) jdbc.execute("DROP TABLE IF EXISTS " + table);
        if (registration != null && registration.getId() != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> forms.remove(registration.getId()));
        }
    }

    private String name(String timestamp) { return table + "_101_" + timestamp + ".xml"; }
    private ByteArrayInputStream input(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }
    private String body(String notes) {
        return "<Results><Row><ID>99999</ID><demographic_no>202</demographic_no><formEdited>wrong</formEdited>"
                + "<notes>" + notes + "</notes></Row></Results>";
    }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    private ZipFile archive(Map<String, String> entries) throws Exception {
        Path file = Files.createTempFile(temporary, "form-import-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ZipFile(file.toFile());
    }

    @Test
    void shouldCommitGeneratedIdAndTrustedTarget_whenArchiveContainsConflictingBodyIdentity() throws Exception {
        Long id = service.importEntry(input(body("  O&apos;Brien &amp; FAKE  ")), name("2026-10-31T12:34:56"));
        assertThat(id).isPositive().isNotEqualTo(99999L);
        assertThat(count()).isEqualTo(1);
        var row = forms.findPatientRecord(table, id.intValue(), 101);
        assertThat(row).containsEntry("notes", "  O'Brien & FAKE  ").containsEntry("rawHistory", null)
                .containsEntry("formCreated", "2026-01-02");
        assertThat(forms.findPatientRecord(table, id.intValue(), 202)).isNull();
        assertThat(service.importEntry(input(body("replacement")), name("2026-10-31T12:34:56"))).isNull();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void shouldRollbackEarlierWrite_whenLaterEntryHasAnUnknownPhysicalColumn() throws Exception {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        entries.put(name("2026-10-31T12:34:56"), body("first"));
        entries.put(name("2026-10-31T12:34:57"), "<Results><Row><unknownColumn>bad</unknownColumn></Row></Results>");
        try (ZipFile archive = archive(entries)) {
            assertThatThrownBy(() -> service.importArchive(archive)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(count()).isZero();
    }

    @Test
    void shouldRollbackEarlierWrite_whenLaterEntryFailsCheckedValidation() throws Exception {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        entries.put(name("2026-10-31T12:34:56"), body("first"));
        entries.put("bad.xml", "<Results/>");
        try (ZipFile archive = archive(entries)) {
            assertThatThrownBy(() -> service.importArchive(archive))
                    .isInstanceOf(io.github.carlos_emr.carlos.util.JDBCUtil.XmlImportException.class);
        }
        assertThat(count()).isZero();
    }

    @Test
    void shouldJoinAndRollbackCallerTransaction_whenFailureFollowsReadAndSecondWrite() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try {
                service.importEntry(input(body("first")), name("2026-10-31T12:34:56"));
                assertThat(forms.findPatientHistory(table, "101", false)).hasSize(1);
                service.importEntry(input(body("second")), name("2026-10-31T12:34:57"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            throw new IllegalStateException("synthetic failure after two writes");
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after two writes");
        assertThat(count()).isZero();
    }

    @Test
    void shouldRetainPhysicalDateStringsAndNulls_whenApReadsNativeColumns() throws Exception {
        Long id = service.importEntry(input(body("  FAKE  ")), name("2026-10-31T12:34:56"));
        var row = apValues.findDatabaseApValues(new ParameterizedSql("SELECT * FROM " + table + " WHERE ID=?", java.util.List.of(id))).get(0);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE ID=?")) {
            statement.setLong(1, id);
            try (var physical = statement.executeQuery()) {
                assertThat(physical.next()).isTrue();
                var metadata = physical.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    assertThat(row.raw(metadata.getColumnName(i))).isEqualTo(physical.getString(i));
                }
            }
        }
        assertThat(row.text("NOTES")).isEqualTo("FAKE");
        assertThat(row.raw("rawHistory")).isNull();
    }
}
