/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportSourceSchemaValidator;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual owning note DAO reads, strict schema/receipts and snapshot isolation; every fixture row is explicitly owned and removed. */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportNoteSourceMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private CaseManagementNoteDAO notes;
    private CdsExportNoteAccessDao access;
    private CaseManagementNoteExtDAO extensions;
    private CaseManagementIssueDAO patientIssues;
    private IssueDAO catalogue;
    private io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao providers;
    private TransactionTemplate read;
    private final int id =
            490_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final String marker = "OwnedNoteSource" + id;
    private final String body = "Clinical 😀 & literal text\r\n".repeat(50000);
    private boolean ownsRows;

    @BeforeEach
    void setup() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!"true".equals(properties.getProperty("ownedDisposableDatabase"))
                || !properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned fixture database only");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser"));
        source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(source);
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(PersistenceManagedTypes.of());
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.show_sql",
                                    "false"));
                    return factory;
                });
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean(
                "transactionManager",
                JpaTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.register(
                CaseManagementNoteDAOImpl.class,
                CaseManagementNoteExtDAOImpl.class,
                CdsExportNoteAccessDao.class,
                CaseManagementIssueDAOImpl.class,
                IssueDAOImpl.class,
                io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class);
        context.refresh();
        notes = context.getBean(CaseManagementNoteDAO.class);
        access = context.getBean(CdsExportNoteAccessDao.class);
        extensions = context.getBean(CaseManagementNoteExtDAO.class);
        patientIssues = context.getBean(CaseManagementIssueDAO.class);
        catalogue = context.getBean(IssueDAO.class);
        providers = context.getBean(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(30);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.executeWithoutResult(
                status -> {
                    for (int patient : List.of(id, id + 1)) {
                        assertThat(
                                        sql.queryForObject(
                                                "select count(*) from demographic where demographic_no=?",
                                                Integer.class,
                                                patient))
                                .isZero();
                        assertThat(
                                        sql.queryForObject(
                                                "select count(*) from casemgmt_note where note_id=?",
                                                Integer.class,
                                                patient))
                                .isZero();
                    }
                    assertThat(
                                    sql.queryForObject(
                                            "select count(*) from issue where issue_id=?",
                                            Integer.class,
                                            id + 10))
                            .isZero();
                    assertThat(
                                    sql.queryForObject(
                                            "select count(*) from casemgmt_issue where demographic_no in (?,?)",
                                            Integer.class,
                                            id,
                                            id + 1))
                            .isZero();
                    sql.update(
                            "insert into issue(issue_id,code,description,role,sortOrderId,update_date) values(?,?,'Synthetic CDS note link','doctor',0,CURRENT_TIMESTAMP)",
                            id + 10,
                            "OwnedLink" + id);
                    for (String provider : List.of("986641", "986642", "986643")) {
                        assertThat(
                                        sql.queryForObject(
                                                "select count(*) from provider where provider_no=?",
                                                Integer.class,
                                                provider))
                                .isZero();
                        sql.update(
                                "insert into provider(provider_no,first_name,last_name,provider_type,status,ohip_no,lastUpdateDate) values(?,'Synthetic',?,'doctor','0',?,CURRENT_TIMESTAMP)",
                                provider,
                                marker,
                                provider);
                    }
                    for (int patient : List.of(id, id + 1)) {
                        sql.update(
                                "insert into demographic(demographic_no,first_name,last_name,patient_status,lastUpdateDate) values(?,'Synthetic',?,'IN',CURRENT_TIMESTAMP)",
                                patient,
                                marker);
                        sql.update(
                                "insert into casemgmt_note(note_id,demographic_no,note,history,program_no,reporter_caisi_role,update_date) values(?,?,?,'','17','1',CURRENT_TIMESTAMP)",
                                patient,
                                patient,
                                patient == id ? body : "Other owned patient");
                        sql.update(
                                "insert into casemgmt_note_ext(id,note_id,key_val,value,date_value) values(?,?,'OwnedLargeNoteExt',?,'2020-02-29')",
                                patient,
                                patient,
                                patient == id ? body : "Other owned extension");
                        sql.update(
                                "insert into casemgmt_issue(id,demographic_no,issue_id,type,update_date) values(?,?,?, ?,CURRENT_TIMESTAMP)",
                                patient,
                                patient,
                                id + 10,
                                marker);
                        sql.update(
                                "insert into casemgmt_issue_notes(id,note_id) values(?,?)",
                                patient,
                                patient);
                    }
                    sql.update(
                            "update casemgmt_note set provider_no='986641',signing_provider_no='986642' where note_id=? and demographic_no=?",
                            id,
                            id);
                    sql.update(
                            "update casemgmt_note set provider_no='986643',signing_provider_no='986643' where note_id=? and demographic_no=?",
                            id + 1,
                            id + 1);
                });
        ownsRows = true;
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (ownsRows) {
                sql.update(
                        "delete l from casemgmt_issue_notes l join casemgmt_note n on n.note_id=l.note_id join demographic d on d.demographic_no=n.demographic_no where n.note_id in (?,?) and d.last_name=?",
                        id,
                        id + 1,
                        marker);
                sql.update(
                        "delete from casemgmt_issue where demographic_no in (?,?) and type=?",
                        id,
                        id + 1,
                        marker);
                sql.update(
                        "delete from issue where issue_id=? and code=?", id + 10, "OwnedLink" + id);
                for (int patient : List.of(id, id + 1)) {
                    sql.update(
                            "delete e from casemgmt_note_ext e join casemgmt_note n on n.note_id=e.note_id join demographic d on d.demographic_no=n.demographic_no where e.id=? and e.key_val='OwnedLargeNoteExt' and d.last_name=?",
                            patient,
                            marker);
                    sql.update(
                            "delete n from casemgmt_note n join demographic d on d.demographic_no=n.demographic_no where n.note_id=? and n.demographic_no=? and d.first_name='Synthetic' and d.last_name=?",
                            patient,
                            patient,
                            marker);
                    sql.update(
                            "delete from demographic where demographic_no=? and first_name='Synthetic' and last_name=?",
                            patient,
                            marker);
                }
                sql.update(
                        "delete from provider where provider_no in ('986641','986642','986643') and first_name='Synthetic' and last_name=?",
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    @Test
    void shouldStreamOnlyTheOwnedNote_andKeepEveryLargeUtf8Byte() throws Exception {
        byte[] expected = body.getBytes(StandardCharsets.UTF_8);
        read.executeWithoutResult(
                status -> {
                    assertThat(notes.countCdsExportRows(id)).isEqualTo(1);
                    var page = notes.findCdsExportRowsAfter(id, null, 32);
                    assertThat(page).hasSize(1);
                    var row = page.getFirst();
                    assertThat(row.id()).isEqualTo(id);
                    assertThat(row.cells().keySet())
                            .isEqualTo(
                                    Set.copyOf(
                                            CdsExportPatientTableReadSupport.Plan.CASE_NOTES
                                                    .columns()));
                    var cell = row.cells().get("note");
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(expected.length);
                    var digest = io.github.carlos_emr.carlos.utility.CdsImportKeys.digest();
                    long offset = 0;
                    while (offset < expected.length) {
                        byte[] chunk =
                                notes.readCdsExportValueChunk(
                                        id,
                                        id,
                                        "note",
                                        offset,
                                        (int) Math.min(65536, expected.length - offset));
                        assertThat(chunk).isNotEmpty();
                        digest.update(chunk);
                        offset += chunk.length;
                    }
                    assertThat(HexFormat.of().formatHex(digest.digest())).isEqualTo(cell.sha256());
                    assertThat(notes.findCdsExportRowsAfter(id, (long) id, 32)).isEmpty();
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                notes.readCdsExportValueChunk(
                                                        id, id + 1, "note", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldMatchThePinnedPhysicalSchema_andActualOrderedReceiptMetadata() {
        read.executeWithoutResult(
                status -> {
                    var schema = notes.readCdsExportSourceSchema();
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            schema,
                                                            Set.copyOf(
                                                                    CdsExportPatientTableReadSupport
                                                                            .Plan.CASE_NOTES
                                                                            .columns())))
                            .doesNotThrowAnyException();
                    var receipt = notes.readCdsExportReceiptColumns();
                    assertThat(receipt).hasSize(25);
                    assertThat(receipt.stream().map(column -> column.name()).toList())
                            .containsExactlyInAnyOrderElementsOf(
                                    CdsExportPatientTableReadSupport.Plan.CASE_NOTES.columns());
                });
    }

    @Test
    void shouldReadExactOrderedCompositeLinkMetadata_underTheOwningSnapshot() {
        read.executeWithoutResult(
                status -> {
                    var columns = notes.readCdsExportIssueLinkReceiptColumns();
                    assertThat(columns)
                            .extracting(column -> column.name())
                            .containsExactly("id", "note_id");
                    assertThat(columns)
                            .allSatisfy(
                                    column -> {
                                        assertThat(column.type()).isEqualTo("INT");
                                        assertThat(column.precision()).isEqualTo(10);
                                        assertThat(column.scale()).isZero();
                                    });
                    assertThat(
                                    new io.github.carlos_emr.carlos.demographic.cdsexport.service
                                                    .CdsExportNoteIssueLinkSourceReader(notes)
                                            .receiptColumns())
                            .isEqualTo(columns);
                });
        assertThatThrownBy(() -> notes.readCdsExportIssueLinkReceiptColumns())
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void shouldPreserveEveryOwnedNoteExtension_andStreamItsCompleteUtf8Value() {
        read.executeWithoutResult(
                status -> {
                    assertThat(extensions.countCdsExportRows(id)).isEqualTo(1);
                    var page = extensions.findCdsExportRowsAfter(id, null, 32);
                    assertThat(page).hasSize(1);
                    var row = page.getFirst();
                    assertThat(row.id()).isEqualTo(id);
                    assertThat(row.cells().get("value").inlineBytes()).isNull();
                    assertThat(
                                    new String(
                                            row.cells().get("date_value").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("2020-02-29");
                    var digest = io.github.carlos_emr.carlos.utility.CdsImportKeys.digest();
                    long offset = 0;
                    while (offset < row.cells().get("value").bytes()) {
                        byte[] chunk =
                                extensions.readCdsExportValueChunk(
                                        id,
                                        id,
                                        "value",
                                        offset,
                                        (int)
                                                Math.min(
                                                        65536,
                                                        row.cells().get("value").bytes() - offset));
                        assertThat(chunk).isNotEmpty();
                        digest.update(chunk);
                        offset += chunk.length;
                    }
                    assertThat(HexFormat.of().formatHex(digest.digest()))
                            .isEqualTo(row.cells().get("value").sha256());
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            extensions.readCdsExportSourceSchema(),
                                                            Set.copyOf(
                                                                    CdsExportPatientTableReadSupport
                                                                            .Plan.CASE_NOTE_EXT
                                                                            .columns())))
                            .doesNotThrowAnyException();
                    assertThat(extensions.readCdsExportReceiptColumns()).hasSize(5);
                    assertThat(extensions.findCdsExportRowsAfter(id, (long) id, 32)).isEmpty();
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                extensions.readCdsExportValueChunk(
                                                        id, id + 1, "value", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCaptureExactNoteAuthorsAndSigners_withTheirPublicFieldsOnly() {
        read.executeWithoutResult(
                status -> {
                    var history =
                            io
                                    .github
                                    .carlos_emr
                                    .carlos
                                    .demographic
                                    .cdsexport
                                    .service
                                    .CdsExportNoteSourceReader
                                    .CATEGORIES
                                    .stream()
                                    .map(
                                            io.github.carlos_emr.carlos.demographic.cds.contract
                                                            .CdsCategory
                                                    ::wireName)
                                    .collect(java.util.stream.Collectors.toSet());
                    assertThat(providers.countCdsExportPublicProviders(id, history)).isEqualTo(2);
                    var first = providers.findCdsExportPublicProvidersAfter(id, history, null, 1);
                    assertThat(first).hasSize(1);
                    assertThat(first.getFirst().id()).isEqualTo("986641");
                    var after =
                            io.github.carlos_emr.carlos.demographic.cdsexport.service
                                    .CdsExportPublicProviderSourceReader.key(first.getFirst().id());
                    var second = providers.findCdsExportPublicProvidersAfter(id, history, after, 1);
                    assertThat(second).hasSize(1);
                    assertThat(second.getFirst().id()).isEqualTo("986642");
                    assertThat(first.getFirst().cells())
                            .containsOnlyKeys(
                                    "first_name",
                                    "last_name",
                                    "ohip_no",
                                    "practitionerNo",
                                    "practitionerNoType",
                                    "provider_no");
                    assertThat(
                                    providers.findCdsExportPublicProvidersAfter(
                                            id,
                                            history,
                                            io.github.carlos_emr.carlos.demographic.cdsexport
                                                    .service.CdsExportPublicProviderSourceReader
                                                    .key("986642"),
                                            32))
                            .isEmpty();
                });
    }

    @Test
    void shouldExcludeNoteProviderAssociations_fromOtherCategorySelections() {
        read.executeWithoutResult(
                status -> {
                    assertThat(providers.countCdsExportPublicProviders(id, Set.of("Demographics")))
                            .isZero();
                    assertThat(
                                    providers.countCdsExportPublicProviders(
                                            id, Set.of("MedicationsAndTreatments")))
                            .isZero();
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                providers.countCdsExportPublicProviders(
                                                        id, Set.of("ClinicalNotes"))))
                .hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_SOURCE_INVALID");
    }

    @Test
    void shouldCaptureEveryPatientIssueField_andOnlyItsReferencedCatalogueRows() {
        read.executeWithoutResult(
                status -> {
                    assertThat(patientIssues.countCdsExportRows(id)).isEqualTo(1);
                    assertThat(catalogue.countCdsExportRows(id)).isEqualTo(1);
                    var issues = patientIssues.findCdsExportRowsAfter(id, null, 32);
                    assertThat(issues).hasSize(1);
                    assertThat(issues.getFirst().id()).isEqualTo(id);
                    assertThat(issues.getFirst().cells()).hasSize(10);
                    assertThat(
                                    new String(
                                            issues.getFirst().cells().get("type").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo(marker);
                    var definitions = catalogue.findCdsExportRowsAfter(id, null, 32);
                    assertThat(definitions).hasSize(1);
                    assertThat(definitions.getFirst().id()).isEqualTo(id + 10);
                    assertThat(definitions.getFirst().cells()).hasSize(8);
                    assertThat(
                                    new String(
                                            definitions
                                                    .getFirst()
                                                    .cells()
                                                    .get("description")
                                                    .inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("Synthetic CDS note link");
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            patientIssues
                                                                    .readCdsExportSourceSchema(),
                                                            Set.copyOf(
                                                                    CdsExportPatientTableReadSupport
                                                                            .Plan.CASE_ISSUES
                                                                            .columns())))
                            .doesNotThrowAnyException();
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            catalogue.readCdsExportSourceSchema(),
                                                            Set.copyOf(
                                                                    CdsExportPatientTableReadSupport
                                                                            .Plan.ISSUE_CATALOGUE
                                                                            .columns())))
                            .doesNotThrowAnyException();
                    assertThat(patientIssues.readCdsExportReceiptColumns()).hasSize(10);
                    assertThat(catalogue.readCdsExportReceiptColumns()).hasSize(8);
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                patientIssues.readCdsExportValueChunk(
                                                        id, id + 1, "type", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCaptureUnlinkedAndResolvedIssues_withoutAnActivityFilter() {
        sql.update("delete from casemgmt_issue_notes where id=? and note_id=?", id, id);
        sql.update(
                "update casemgmt_issue set resolved=1,acute=1,certain=1,major=1 where id=? and demographic_no=?",
                id,
                id);
        read.executeWithoutResult(
                status -> {
                    assertThat(patientIssues.countCdsExportRows(id)).isEqualTo(1);
                    assertThat(catalogue.countCdsExportRows(id)).isEqualTo(1);
                    var row = patientIssues.findCdsExportRowsAfter(id, null, 32).getFirst();
                    for (String flag : List.of("resolved", "acute", "certain", "major"))
                        assertThat(
                                        new String(
                                                row.cells().get(flag).inlineBytes(),
                                                StandardCharsets.UTF_8))
                                .isEqualTo("1");
                });
    }

    @Test
    void shouldCaptureExactPhysicalIssueLinks_withoutIncludingAnotherPatient() {
        read.executeWithoutResult(
                status -> {
                    assertThat(notes.countCdsExportIssueLinks(id)).isEqualTo(1);
                    var page = notes.findCdsExportIssueLinksAfter(id, null, 32);
                    assertThat(page).hasSize(1);
                    assertThat(page.getFirst())
                            .isEqualTo(
                                    new io.github.carlos_emr.carlos.casemgmt.dto
                                            .CdsExportNoteIssueLinkDto(id, id));
                    assertThat(notes.findCdsExportIssueLinksAfter(id, page.getFirst(), 32))
                            .isEmpty();
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            notes.readCdsExportIssueLinkSchema(),
                                                            Set.of("id", "note_id")))
                            .doesNotThrowAnyException();
                });
    }

    @Test
    void shouldRefuseDuplicatePhysicalLinks_withoutCollapsingRows() {
        sql.update("insert into casemgmt_issue_notes(id,note_id) values(?,?)", id, id);
        assertThatThrownBy(() -> read.execute(status -> notes.countCdsExportIssueLinks(id)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status -> notes.findCdsExportIssueLinksAfter(id, null, 32)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldRefuseCrossPatientLinks_fromEitherPatientDirection() {
        sql.update("update casemgmt_issue_notes set id=? where note_id=?", id, id + 1);
        for (int patient : List.of(id, id + 1)) {
            assertThatThrownBy(
                            () -> read.execute(status -> notes.countCdsExportIssueLinks(patient)))
                    .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
            assertThatThrownBy(
                            () ->
                                    read.execute(
                                            status ->
                                                    notes.findCdsExportIssueLinksAfter(
                                                            patient, null, 32)))
                    .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        }
    }

    @Test
    void shouldPageTheCompleteCompositeIdentity_withoutSkippingSharedNotes() {
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.executeWithoutResult(
                status -> {
                    for (int offset = 100; offset < 135; offset++) {
                        assertThat(
                                        sql.queryForObject(
                                                "select count(*) from casemgmt_issue where id=?",
                                                Integer.class,
                                                id + offset))
                                .isZero();
                        sql.update(
                                "insert into casemgmt_issue(id,demographic_no,issue_id,type,update_date) values(?,?,?,?,CURRENT_TIMESTAMP)",
                                id + offset,
                                id,
                                id + 10,
                                marker);
                        sql.update(
                                "insert into casemgmt_issue_notes(id,note_id) values(?,?)",
                                id + offset,
                                id);
                    }
                });
        read.executeWithoutResult(
                status -> {
                    assertThat(notes.countCdsExportIssueLinks(id)).isEqualTo(36);
                    var first = notes.findCdsExportIssueLinksAfter(id, null, 32);
                    assertThat(first).hasSize(32);
                    var second = notes.findCdsExportIssueLinksAfter(id, first.getLast(), 32);
                    assertThat(second).hasSize(4);
                    var all = new java.util.ArrayList<>(first);
                    all.addAll(second);
                    assertThat(
                                    all.stream()
                                            .map(
                                                    io.github.carlos_emr.carlos.casemgmt.dto
                                                                    .CdsExportNoteIssueLinkDto
                                                            ::noteId)
                                            .distinct())
                            .containsExactly((long) id);
                    assertThat(
                                    all.stream()
                                            .map(
                                                    io.github.carlos_emr.carlos.casemgmt.dto
                                                                    .CdsExportNoteIssueLinkDto
                                                            ::patientIssueId))
                            .isSorted()
                            .doesNotHaveDuplicates();
                    assertThat(notes.findCdsExportIssueLinksAfter(id, second.getLast(), 32))
                            .isEmpty();
                    assertThatCode(
                                    () -> {
                                        var reader =
                                                new io.github.carlos_emr.carlos.demographic
                                                        .cdsexport.service
                                                        .CdsExportNoteIssueLinkSourceReader(notes);
                                        var values = new java.io.ByteArrayOutputStream();
                                        var index = new java.io.ByteArrayOutputStream();
                                        new io.github.carlos_emr.carlos.demographic.cdsexport
                                                        .service.CdsExportNativeCaptureComposer()
                                                .compose(
                                                        values,
                                                        index,
                                                        Set.of("casemgmt_issue_notes"),
                                                        1000,
                                                        1L << 20,
                                                        () -> {},
                                                        writer -> {
                                                            writer.beginTable(
                                                                    reader.table(),
                                                                    reader.columns(),
                                                                    reader.count(id));
                                                            reader.capture(id, writer);
                                                            writer.endTable();
                                                        });
                                        var verified =
                                                new io.github.carlos_emr.carlos.demographic
                                                                .cdsexport.service
                                                                .CdsExportNativeCaptureReader()
                                                        .verify(
                                                                new java.io.ByteArrayInputStream(
                                                                        values.toByteArray()),
                                                                new java.io.ByteArrayInputStream(
                                                                        index.toByteArray()),
                                                                Map.of(
                                                                        "casemgmt_issue_notes",
                                                                        new io.github.carlos_emr
                                                                                .carlos.demographic
                                                                                .cdsexport.service
                                                                                .CdsExportNativeCaptureReader
                                                                                .Table(
                                                                                Set.of(
                                                                                        "id",
                                                                                        "note_id"),
                                                                                36)),
                                                                1000,
                                                                1L << 20,
                                                                () -> {});
                                        assertThat(verified.fields()).isEqualTo(72);
                                        assertThat(verified.rows())
                                                .containsEntry("casemgmt_issue_notes", 36L);
                                    })
                            .doesNotThrowAnyException();
                });
    }

    @Test
    void shouldRequireReadonlyTransactions_forAssociationMetadataAndValues() {
        assertThatThrownBy(() -> notes.countCdsExportIssueLinks(id))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> notes.findCdsExportIssueLinksAfter(id, null, 32))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> write.execute(status -> notes.countCdsExportIssueLinks(id)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldKeepOneRealSnapshot_whenTheNoteScopeChangesOnAnotherConnection() {
        read.executeWithoutResult(
                status -> {
                    assertThat(access.readScopes(id, 8192).getFirst().program()).isEqualTo("17");
                    try (var other = source.getConnection();
                            var update =
                                    other.prepareStatement(
                                            "update casemgmt_note set program_no='18' where note_id=? and demographic_no=?")) {
                        update.setInt(1, id);
                        update.setInt(2, id);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failure) {
                        throw new IllegalStateException("Owned concurrent fixture update failed");
                    }
                    assertThat(access.readScopes(id, 8192).getFirst().program()).isEqualTo("17");
                    var page = notes.findCdsExportRowsAfter(id, null, 32);
                    assertThat(
                                    new String(
                                            page.getFirst().cells().get("program_no").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("17");
                });
        String current = read.execute(status -> access.readScopes(id, 8192).getFirst().program());
        assertThat(current).isEqualTo("18");
    }

    @Test
    void shouldRefuseNonReadonlyReads_andNeverSkipALockedNoteDuringScopeDiscovery() {
        assertThatThrownBy(() -> notes.countCdsExportRows(id))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> write.execute(status -> notes.countCdsExportRows(id)))
                .isInstanceOf(IllegalStateException.class);
        sql.update(
                "update casemgmt_note set locked='1' where note_id=? and demographic_no=?", id, id);
        assertThatThrownBy(() -> read.execute(status -> access.readScopes(id, 8192)))
                .hasMessage("CDS_EXPORT_NOTE_AUTHORIZATION_DENIED");
    }
}
