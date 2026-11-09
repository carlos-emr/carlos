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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.commn.model.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real owning prescription/precision DAO snapshots, including archived/hidden rows and foreign-parent isolation. */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportMedicationCaptureMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int patient =
            550_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(40_000_000);
    private static final io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory ALLERGY =
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                    .ALLERGIES_AND_ADVERSE_REACTIONS;
    private static final io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
            MEDICATION =
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                            .MEDICATIONS_AND_TREATMENTS;
    private final String provider =
            "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    private boolean providerInserted;

    @BeforeEach
    void prepareOwnedRows() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException("Owned CDS database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser"));
        source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
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
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    Drug.class.getName(),
                                    PartialDate.class.getName(),
                                    Provider.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(
                DrugDaoImpl.class,
                PartialDateDaoImpl.class,
                io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        for (int n = 0; n < 35; n++)
            sql.update(
                    "insert into drugs(drugid,demographic_no,provider_no,GCN_SEQNO,archived,prn,position,lastUpdateDate,dispenseInternal,customName,hide_from_drug_profile,takemin,takemax,comment) values(?,?,'',0,?,0,0,CURRENT_TIMESTAMP,0,?,1,NULL,NULL,?)",
                    patient + n,
                    patient,
                    n % 2,
                    "Synthetic café 😀 " + n,
                    n == 0 ? null : n == 1 ? "" : "Synthetic note");
        sql.update(
                "insert into drugs(drugid,demographic_no,provider_no,GCN_SEQNO,archived,prn,position,lastUpdateDate,dispenseInternal) values(?,?,'',0,0,0,0,CURRENT_TIMESTAMP,0)",
                patient + 40,
                patient + 1);
        sql.update(
                "insert into allergies(allergyid,demographic_no,DESCRIPTION,TYPECODE,archived,position,lastUpdateDate) values(?,?,'Synthetic allergy',0,0,0,CURRENT_TIMESTAMP)",
                patient,
                patient);
        sql.update(
                "insert into allergies(allergyid,demographic_no,DESCRIPTION,TYPECODE,archived,position,lastUpdateDate) values(?,?,'Foreign synthetic allergy',0,0,0,CURRENT_TIMESTAMP)",
                patient + 40,
                patient + 1);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from provider where provider_no=?",
                                Integer.class,
                                provider))
                .isZero();
        sql.update(
                "insert into provider(provider_no,first_name,last_name,ohip_no,comments,dob,status,lastUpdateDate) values(?,'Synthetic','Prescriber','001234','PRIVATE_PROVIDER_SENTINEL','1900-01-01','0',CURRENT_TIMESTAMP)",
                provider);
        providerInserted = true;
        sql.update(
                "update drugs set provider_no=? where drugid=? and demographic_no=?",
                provider,
                patient + 34,
                patient);
        precision(0, 1, patient, 1, "YYYY");
        precision(1, 2, patient, 3, "YYYY-MM");
        precision(2, 2, patient, 5, "YYYY");
        precision(3, 2, patient, 5, "UNREVIEWED");
        precision(4, 3, patient, 4, "YYYY");
        precision(5, 1, patient + 40, 1, "YYYY");
        precision(6, 2, patient + 40, 3, "YYYY");
    }

    @AfterEach
    void cleanOnlyOwnedRows() throws Exception {
        try {
            if (sql != null) {
                sql.update(
                        "delete from partial_date where id between ? and ?", patient, patient + 6);
                sql.update(
                        "delete from allergies where allergyid in (?,?) and demographic_no in (?,?)",
                        patient,
                        patient + 40,
                        patient,
                        patient + 1);
                sql.update(
                        "delete from drugs where demographic_no in (?,?) and drugid between ? and ?",
                        patient,
                        patient + 1,
                        patient,
                        patient + 40);
                if (providerInserted)
                    sql.update(
                            "delete from provider where provider_no=? and first_name='Synthetic' and last_name='Prescriber'",
                            provider);
            }
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (source != null) source.close();
            }
        }
    }

    @Test
    void
            shouldReconcileAnActuallyPersistedImporterProjection_andPreserveItsSourceMeaningAfterANativeNoteEdit()
                    throws Exception {
        var drugs = context.getBean(DrugDaoImpl.class);
        var persister =
                new io.github.carlos_emr.carlos.demographic.cdsimport.service
                        .CdsMedicationPersister(
                        drugs, context.getBean(PartialDateDaoImpl.class), null);
        var method =
                persister
                        .getClass()
                        .getDeclaredMethod(
                                "candidate",
                                int.class,
                                io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                        .CdsMedicationDto.class,
                                io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                        .CdsProviderMatchDto.class);
        method.setAccessible(true);
        var preparedSource = CdsExportMedicationFieldReconcilerUnitTest.source();
        var providerMatch =
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto(
                        null,
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto
                                .Outcome.UNMATCHED_IDENTIFIER);
        var candidate = (Drug) method.invoke(persister, patient, preparedSource, providerMatch);
        Object targetDao = org.springframework.test.util.AopTestUtils.getTargetObject(drugs);
        var em =
                (jakarta.persistence.EntityManager)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                targetDao, "entityManager");
        var receipts =
                new io.github.carlos_emr.carlos.demographic.cdsimport.service
                        .CdsTargetReceiptService();
        org.springframework.test.util.ReflectionTestUtils.setField(receipts, "entityManager", em);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.setTimeout(30);
        Integer inserted = null;
        try {
            var original =
                    write.execute(
                            status -> {
                                drugs.persist(candidate);
                                drugs.flush();
                                return receipts.capture(
                                        Map.of("drugs", candidate.getId().toString()));
                            });
            inserted = candidate.getId();
            assertThat(original).isNotNull();
            sql.update(
                    "update drugs set comment=? where drugid=? and demographic_no=?",
                    "Edited actual native provider note",
                    inserted,
                    patient);
            var currentReceipt =
                    write.execute(
                            status ->
                                    receipts.capture(
                                            Map.of("drugs", candidate.getId().toString())));
            var old =
                    io
                            .github
                            .carlos_emr
                            .carlos
                            .demographic
                            .cds
                            .contract
                            .CdsNativeBaselineCodec
                            .decode(
                                    original.nativeBaseline(),
                                    original.targetReferences(),
                                    original.targetDigest())
                            .rows()
                            .stream()
                            .filter(row -> row.table().equals("drugs"))
                            .findFirst()
                            .orElseThrow();
            var now =
                    io
                            .github
                            .carlos_emr
                            .carlos
                            .demographic
                            .cds
                            .contract
                            .CdsNativeBaselineCodec
                            .decode(
                                    currentReceipt.nativeBaseline(),
                                    currentReceipt.targetReferences(),
                                    currentReceipt.targetDigest())
                            .rows()
                            .stream()
                            .filter(row -> row.table().equals("drugs"))
                            .findFirst()
                            .orElseThrow();
            var captured =
                    read.execute(
                            status ->
                                    drugs.findCdsExportRowsAfter(
                                                    patient, candidate.getId().longValue() - 1, 1)
                                            .getFirst());
            assertThat(captured.id()).isEqualTo(candidate.getId().longValue());
            var text = new LinkedHashMap<String, String>();
            var numbers =
                    new LinkedHashMap<
                            String,
                            io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric>();
            captured.cells()
                    .forEach(
                            (column, cell) -> {
                                text.put(
                                        column,
                                        cell.sqlNull()
                                                ? null
                                                : new String(
                                                        java.util.Objects.requireNonNull(
                                                                cell.inlineBytes()),
                                                        StandardCharsets.UTF_8));
                                if (cell.exactNumeric() != null)
                                    numbers.put(column, cell.exactNumeric());
                            });
            var row =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportCapturedTextRowDto(
                            "drugs",
                            CdsExportPatientTableSourceReader.key(captured.id()),
                            text,
                            numbers);
            var base =
                    CdsExportMedicationFieldReconcilerUnitTest.fixture(
                            CdsExportMedicationFieldReconcilerUnitTest.baseline(),
                            CdsExportMedicationFieldReconcilerUnitTest.numbers(),
                            true);
            String address = CdsExportMedicationFieldReconcilerUnitTest.ADDRESS,
                    unitId =
                            io.github.carlos_emr.carlos.utility.CdsImportKeys.hash(
                                    CdsExportMedicationFieldReconcilerUnitTest.RUN,
                                    CdsExportMedicationFieldReconcilerUnitTest.UNIT);
            var tables = new LinkedHashMap<>(base.index().tables());
            var unit = new LinkedHashMap<>(tables.get("cds_import_unit").get(unitId).fields());
            unit.put("target_references", original.targetReferences());
            unit.put("target_digest", original.targetDigest());
            unit.put("native_baseline", original.nativeBaseline());
            tables.put(
                    "cds_import_unit",
                    Map.of(
                            unitId,
                            new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportCapturedTextRowDto("cds_import_unit", unitId, unit)));
            var reference =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportRetainedIndexDto.Reference(
                            "drugs", inserted.toString(), old.digest());
            var index =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportRetainedIndexDto(
                            base.index().sourceManifestSha256(),
                            3,
                            4096,
                            tables,
                            Set.of(address),
                            Map.of(address, List.of(reference)),
                            Map.of());
            var difference =
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison
                            .compare(old, now);
            var checked =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportRetainedReceiptVerificationDto(
                            index.sourceManifestSha256(),
                            Map.of(
                                    address,
                                    List.of(
                                            new io.github.carlos_emr.carlos.demographic.cdsexport
                                                    .dto.CdsExportRetainedReceiptVerificationDto
                                                    .CheckedReference(
                                                    reference,
                                                    now.digest(),
                                                    io.github.carlos_emr.carlos.demographic
                                                            .cdsexport.dto
                                                            .CdsExportRetainedReceiptVerificationDto
                                                            .Reason.CHANGED_ROW,
                                                    difference))),
                            Set.of(),
                            Set.of(address));
            var result =
                    new CdsExportMedicationFieldReconciler()
                            .reconcile(
                                    patient,
                                    index,
                                    address,
                                    checked,
                                    row,
                                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportNativeRowReceiptDto(
                                            "drugs",
                                            inserted.toString(),
                                            row.recordKey(),
                                            now.digest(),
                                            now),
                                    List.of(),
                                    Map.of());
            assertThat(
                            result.mapping().fields().values().stream()
                                    .filter(
                                            value ->
                                                    value.outcome()
                                                            == io.github.carlos_emr.carlos
                                                                    .demographic.cdsexport.dto
                                                                    .CdsExportFieldAssessmentDto
                                                                    .Outcome.REQUIRES_REVIEW)
                                    .toList())
                    .isEmpty();
            assertThat(result.mapping().nativeRowRepresentable()).isTrue();
            assertThat(result.mapping().value().fields())
                    .containsEntry(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportMedicationDto.Field.Notes,
                            "Edited actual native provider note");
            assertThat(result.mapping().value().strengthAmount()).isEqualTo("5");
            assertThat(result.mapping().value().prescriber().lastName()).isEqualTo("Physician");
        } finally {
            Integer cleanupId = inserted == null ? candidate.getId() : inserted;
            if (cleanupId != null)
                sql.update(
                        "delete from drugs where drugid=? and demographic_no=?",
                        cleanupId,
                        patient);
        }
    }

    @Test
    void
            shouldCountAndPageAllCurrentArchivedAndHiddenPrescriptionRows_withEveryReviewedPhysicalColumn() {
        var drugs = context.getBean(DrugDaoImpl.class);
        read.executeWithoutResult(
                status -> {
                    try {
                        new CdsExportSourceSchemaValidator()
                                .verify(
                                        drugs.readCdsExportSourceSchema(),
                                        Set.copyOf(
                                                CdsExportPatientTableReadSupport.Plan.DRUGS
                                                        .columns()));
                    } catch (java.io.IOException invalid) {
                        throw new java.io.UncheckedIOException(invalid);
                    }
                    assertThat(drugs.readCdsExportReceiptColumns()).hasSize(64);
                    assertThat(drugs.countCdsExportRows(patient)).isEqualTo(35);
                    var first = drugs.findCdsExportRowsAfter(patient, null, 32);
                    var last = drugs.findCdsExportRowsAfter(patient, first.getLast().id(), 32);
                    assertThat(first).hasSize(32);
                    assertThat(last).hasSize(3);
                    assertThat(first.getFirst().cells()).hasSize(64);
                    assertThat(first.getFirst().cells().get("comment").sqlNull()).isTrue();
                    assertThat(first.get(1).cells().get("comment").sqlNull()).isFalse();
                    assertThat(first.get(1).cells().get("comment").bytes()).isZero();
                    assertThat(
                                    new String(
                                            first.getFirst()
                                                    .cells()
                                                    .get("customName")
                                                    .inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("Synthetic café 😀 0");
                    assertThat(
                                    new String(
                                            first.get(1).cells().get("archived").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("1");
                    assertThat(
                                    new String(
                                            first.getFirst()
                                                    .cells()
                                                    .get("hide_from_drug_profile")
                                                    .inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("1");
                    assertThat(drugs.countCdsExportRows(patient + 1)).isEqualTo(1);
                });
    }

    @Test
    void shouldCaptureTheStoredDoseBits_insteadOfTheRoundedSqlDisplay_andKeepItsSnapshot() {
        sql.update(
                "update drugs set takemin=0.1,takemax=0.10000000894069672 where drugid=?", patient);
        var drugs = context.getBean(DrugDaoImpl.class);
        read.executeWithoutResult(
                status -> {
                    var before = drugs.findCdsExportRowsAfter(patient, null, 1).getFirst();
                    var low = before.cells().get("takemin");
                    var high = before.cells().get("takemax");
                    assertThat(low.inlineBytes()).isEqualTo(high.inlineBytes());
                    assertThat(low.sha256()).isEqualTo(high.sha256());
                    assertThat(low.exactNumeric().bits()).isEqualTo("3dcccccd");
                    assertThat(high.exactNumeric().bits()).isEqualTo("3dccccce");
                    try (var writer = source.getConnection();
                            var update =
                                    writer.prepareStatement(
                                            "update drugs set takemin=0.2 where drugid=?")) {
                        update.setInt(1, patient);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failed) {
                        throw new IllegalStateException(failed);
                    }
                    assertThat(
                                    drugs.findCdsExportRowsAfter(patient, null, 1)
                                            .getFirst()
                                            .cells()
                                            .get("takemin")
                                            .exactNumeric())
                            .isEqualTo(low.exactNumeric());
                });
        read.executeWithoutResult(
                status ->
                        assertThat(
                                        drugs.findCdsExportRowsAfter(patient, null, 1)
                                                .getFirst()
                                                .cells()
                                                .get("takemin")
                                                .exactNumeric()
                                                .bits())
                                .isEqualTo("3e4ccccd"));
    }

    @Test
    void
            shouldCapturePrecisionOnlyForSelectedPatientOwnedDomains_withoutLosingDuplicateOrUnknownRows() {
        var dates = context.getBean(PartialDateDaoImpl.class);
        read.executeWithoutResult(
                status -> {
                    assertThat(dates.countCdsExportScopedRows(patient, Set.of(ALLERGY)))
                            .isEqualTo(1);
                    assertThat(dates.countCdsExportScopedRows(patient, Set.of(MEDICATION)))
                            .isEqualTo(3);
                    assertThat(dates.countCdsExportScopedRows(patient, Set.of(ALLERGY, MEDICATION)))
                            .isEqualTo(4);
                    assertThat(
                                    dates.findCdsExportScopedRowsAfter(
                                            patient, Set.of(MEDICATION), null, 32))
                            .hasSize(3);
                    assertThat(
                                    dates.countCdsExportScopedRows(
                                            patient + 1, Set.of(ALLERGY, MEDICATION)))
                            .isEqualTo(2);
                    assertThat(
                                    dates
                                            .findCdsExportScopedRowsAfter(
                                                    patient, Set.of(MEDICATION), null, 32)
                                            .stream()
                                            .map(
                                                    row ->
                                                            new String(
                                                                    row.cells()
                                                                            .get("format")
                                                                            .inlineBytes(),
                                                                    StandardCharsets.UTF_8)))
                            .contains("UNREVIEWED");
                });
    }

    @Test
    void shouldKeepTheOriginalLargeValueAndRowHashesInOneSnapshot_acrossAConcurrentNativeEdit() {
        String expected = "Frozen café 😀\r\n".repeat(10000);
        sql.update("update drugs set special_instruction=? where drugid=?", expected, patient);
        var drugs = context.getBean(DrugDaoImpl.class);
        read.executeWithoutResult(
                status -> {
                    var row = drugs.findCdsExportRowsAfter(patient, null, 1).getFirst();
                    var field = row.cells().get("special_instruction");
                    assertThat(field.inlineBytes()).isNull();
                    try (var writer = source.getConnection();
                            var update =
                                    writer.prepareStatement(
                                            "update drugs set special_instruction='Later native edit' where drugid=?")) {
                        update.setInt(1, patient);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failed) {
                        throw new IllegalStateException(failed);
                    }
                    try (var input =
                            new CdsExportRawValueInputStream(
                                    drugs, patient, row.id(), "special_instruction", field)) {
                        assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                                .isEqualTo(expected);
                    } catch (java.io.IOException invalid) {
                        throw new java.io.UncheckedIOException(invalid);
                    }
                });
        assertThat(
                        sql.queryForObject(
                                "select special_instruction from drugs where drugid=?",
                                String.class,
                                patient))
                .isEqualTo("Later native edit");
    }

    @Test
    void shouldRefuseCrossDomainAndForeignPatientChunks_andRequireTheCallerSnapshot() {
        var drugs = context.getBean(DrugDaoImpl.class);
        var dates = context.getBean(PartialDateDaoImpl.class);
        assertThatThrownBy(() -> drugs.countCdsExportRows(patient))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        for (Runnable refused :
                List.<Runnable>of(
                        () -> drugs.readCdsExportValueChunk(patient, patient + 40, "comment", 0, 1),
                        () ->
                                dates.readCdsExportScopedChunk(
                                        patient, Set.of(ALLERGY), patient + 1, "format", 0, 1),
                        () ->
                                dates.readCdsExportScopedChunk(
                                        patient, Set.of(MEDICATION), patient, "format", 0, 1),
                        () ->
                                dates.countCdsExportScopedRows(
                                        patient,
                                        Set.of(
                                                io.github.carlos_emr.carlos.demographic.cds.contract
                                                        .CdsCategory.DEMOGRAPHICS)))) {
            assertThatThrownBy(() -> read.executeWithoutResult(status -> refused.run()))
                    .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class)
                    .hasMessageContaining("CDS_EXPORT_");
        }
    }

    @Test
    void
            shouldCaptureInactiveMedicationPrescribersOnlyForTheSelectedDomain_withoutPrivateProviderValues() {
        var providers =
                context.getBean(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class);
        read.executeWithoutResult(
                status -> {
                    assertThat(
                                    providers.countCdsExportPublicProviders(
                                            patient, Set.of("MedicationsAndTreatments")))
                            .isEqualTo(1);
                    assertThat(
                                    providers.countCdsExportPublicProviders(
                                            patient, Set.of("AllergiesAndAdverseReactions")))
                            .isZero();
                    assertThat(
                                    providers.countCdsExportPublicProviders(
                                            patient + 1, Set.of("MedicationsAndTreatments")))
                            .isZero();
                    var rows =
                            providers.findCdsExportPublicProvidersAfter(
                                    patient, Set.of("MedicationsAndTreatments"), null, 32);
                    assertThat(rows).hasSize(1);
                    assertThat(rows.getFirst().id()).isEqualTo(provider);
                    assertThat(rows.getFirst().cells().keySet())
                            .isEqualTo(
                                    io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto
                                            .COLUMNS);
                    assertThat(rows.getFirst().cells().keySet())
                            .doesNotContain("comments", "dob", "billing_no");
                    assertThat(
                                    new String(
                                            rows.getFirst().cells().get("ohip_no").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("001234");
                });
    }

    private void precision(int n, int owner, int parent, int field, String format) {
        sql.update(
                "insert into partial_date(id,table_name,table_id,field_name,format) values(?,?,?,?,?)",
                patient + n,
                owner,
                parent,
                field,
                format);
    }
}
