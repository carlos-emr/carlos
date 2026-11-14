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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

/**
 * The real {@code drugReason} read plan against the pinned physical schema, with reasons written
 * exactly as CARLOS writes them: persisted through {@link DrugReasonDao#addNewDrugReason} as
 * {@code RxReason2Action} and {@code RxWriteScript2Action} do, and archived through a merge as
 * {@code RxReason2Action.archiveReason} does. Covers the patient scope (archived rows kept, other
 * charts excluded), large comments, the ownership refusals, and the real captured values through
 * the medication mappers into a schema-valid ProblemCode and reasons residual.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportMedicationReasonReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private TransactionTemplate write;
    private DrugReasonDao reasons;
    private DrugDao drugs;
    private final int patient =
            560_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final int other = patient + 1;
    private final int drug =
            1_600_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(400_000_000);
    private final String comments = "Started  for HbA1c 8.1 😀 & reviewed\r\n".repeat(600);
    private final List<Integer> written = new ArrayList<>();
    private boolean owned;

    @BeforeEach
    void setup() throws Exception {
        var settings = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            settings.load(input);
        }
        if (!"true".equals(settings.getProperty("ownedDisposableDatabase"))
                || !settings.getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned disposable medication database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(settings.getProperty("jdbcUrl"));
        source.setUsername(settings.getProperty("jdbcUser"));
        source.setPassword(settings.getProperty("jdbcPassword"));
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
                                    Drug.class.getName(), DrugReason.class.getName()));
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
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.register(DrugReasonDaoImpl.class, DrugDaoImpl.class);
        context.refresh();
        reasons = context.getBean(DrugReasonDao.class);
        drugs = context.getBean(DrugDao.class);
        var manager = context.getBean(PlatformTransactionManager.class);
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        write = new TransactionTemplate(manager);
        write.setTimeout(30);
        owned = true;
        // Two prescriptions of this chart, as the Rx page saves an ordinary active one, and one
        // of another chart.
        String insert =
                "insert into drugs(drugid,demographic_no,provider_no,GCN_SEQNO,archived,prn,"
                        + "position,lastUpdateDate,dispenseInternal,customName,rx_date,written_date,"
                        + "end_date,rxStatus,past_med,start_date_unknown,hide_from_drug_profile)"
                        + " values(?,?,'',0,0,0,0,CURRENT_TIMESTAMP,0,?,'2026-01-02','2026-01-02',"
                        + "'2026-04-02','Active',0,0,0)";
        sql.update(insert, drug, patient, "Synthetic metformin");
        sql.update(insert, drug + 1, patient, "Synthetic ramipril");
        sql.update(insert, drug + 2, other, "Synthetic other chart drug");
        // The Rx reason page: a primary ICD-9 indication with comments, and a second one.
        written.add(reason(drug, patient, "icd9", "250", comments, true));
        written.add(reason(drug, patient, "icd10", "E11.9", "", false));
        // The Rx write page: always unflagged with empty comments; later archived with a reason.
        int archived = reason(drug + 1, patient, "icd9", "401", "", false);
        written.add(archived);
        write.executeWithoutResult(
                status -> {
                    var row = reasons.find(archived);
                    row.setArchivedFlag(true);
                    row.setArchivedReason("Entered on the wrong drug");
                    reasons.merge(row);
                });
        written.add(reason(drug + 2, other, "icd9", "250", "", true));
    }

    /** Persists one reason through the owning DAO exactly as the Rx pages do. */
    private int reason(
            int drugId, int demographic, String system, String code, String note, boolean primary) {
        return write.execute(
                status -> {
                    var row = new DrugReason();
                    row.setDrugId(drugId);
                    row.setProviderNo("999998");
                    row.setDemographicNo(demographic);
                    row.setCodingSystem(system);
                    row.setCode(code);
                    row.setComments(note);
                    row.setPrimaryReasonFlag(primary);
                    row.setArchivedFlag(false);
                    row.setDateCoded(new Date());
                    assertThat(reasons.addNewDrugReason(row)).isTrue();
                    return row.getId();
                });
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from drugReason where drugId between ? and ? and demographicNo in (?,?)",
                        drug - 1,
                        drug + 3,
                        patient,
                        other);
                sql.update(
                        "delete from drugs where drugid between ? and ? and demographic_no in (?,?)",
                        drug,
                        drug + 2,
                        patient,
                        other);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    @Test
    void shouldReadEveryReasonOfThePatientsPrescriptions_includingArchivedOnesOnly() {
        read.executeWithoutResult(
                status -> {
                    assertThat(reasons.countCdsExportRows(patient)).isEqualTo(3);
                    assertThat(reasons.findCdsExportRowsAfter(patient, null, 32))
                            .extracting(CdsExportRawRowDto::id)
                            .containsExactly(
                                    (long) written.get(0),
                                    (long) written.get(1),
                                    (long) written.get(2));
                    var archived =
                            reasons.findCdsExportRowsAfter(patient, (long) written.get(1), 32)
                                    .getFirst()
                                    .cells();
                    assertThat(text(archived.get("archivedFlag"))).isEqualTo("1");
                    assertThat(text(archived.get("archivedReason")))
                            .isEqualTo("Entered on the wrong drug");
                    assertThat(text(archived.get("primaryReasonFlag"))).isEqualTo("0");
                    assertThat(text(archived.get("dateCoded"))).matches("\\d{4}-\\d{2}-\\d{2}");
                    assertThat(reasons.countCdsExportRows(other)).isEqualTo(1);
                });
    }

    @Test
    void shouldStreamTheCompleteComments_underTheReasonsOwnPatient() {
        read.executeWithoutResult(
                status -> {
                    var cell =
                            reasons.findCdsExportRowsAfter(patient, null, 32)
                                    .getFirst()
                                    .cells()
                                    .get("comments");
                    byte[] data = comments.getBytes(StandardCharsets.UTF_8);
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(data.length);
                    assertThat(
                                    reasons.readCdsExportValueChunk(
                                            patient, written.get(0), "comments", 0, 64))
                            .isEqualTo(Arrays.copyOf(data, 64));
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                reasons.readCdsExportValueChunk(
                                                        patient, written.get(3), "comments", 0, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldHoldBothCharts_whenAReasonsPatientDisagreesWithItsDrugsOwner() {
        // CARLOS never writes this; a reason filed on this chart's drug under the other patient
        // would show in both charts, so both are held instead of either silently losing it.
        reason(drug, other, "icd9", "4019", "", false);
        for (int chart : List.of(patient, other))
            assertThatThrownBy(
                            () ->
                                    read.execute(
                                            status -> reasons.countCdsExportRows(chart)))
                    .isInstanceOf(CdsExportReviewException.class)
                    .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldHoldTheNamedPatient_whenAReasonsDrugNoLongerExists() {
        reason(drug - 1, patient, "icd9", "4019", "", false);
        assertThatThrownBy(() -> read.execute(status -> reasons.findCdsExportRowsAfter(patient, null, 32)))
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        Long remaining = read.execute(status -> reasons.countCdsExportRows(other));
        assertThat(remaining).isEqualTo(1L);
    }

    @Test
    void shouldMatchAllPinnedPhysicalColumns_ofTheReasonCapture() {
        read.executeWithoutResult(
                status -> {
                    var reader = new CdsExportMedicationReasonSourceReader(reasons);
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(
                                                            reader.table(),
                                                            reader.schema(),
                                                            reader.columns()))
                            .doesNotThrowAnyException();
                    assertThat(reader.columns())
                            .isEqualTo(Set.copyOf(CdsExportPatientTableReadSupport.Plan.DRUG_REASONS.columns()));
                });
    }

    /**
     * The real stored values (tinyint flags, a DATE, NULL and empty text) captured as the export
     * captures them, read back and mapped: the primary ICD-9 reason is ProblemCode, both shown
     * reasons travel in the residual, the archived one is accounted and nothing is left unmapped.
     */
    @Test
    void shouldExportTheRealCapturedReasons_asProblemCodeAndASchemaValidResidual() throws Exception {
        var readers =
                List.<CdsExportNativeSourceReader>of(
                        new CdsExportMedicationReasonSourceReader(reasons),
                        new CdsExportMedicationSourceReader(drugs));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        var expected = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        read.executeWithoutResult(
                status -> {
                    try {
                        var tables = new TreeSet<String>();
                        for (var reader : readers) tables.add(reader.table());
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        index,
                                        tables,
                                        100_000,
                                        64L << 20,
                                        () -> {},
                                        writer -> {
                                            for (var reader : readers) {
                                                long count = reader.count(patient);
                                                writer.beginTable(
                                                        reader.table(), reader.columns(), count);
                                                reader.capture(patient, writer);
                                                writer.endTable();
                                                scopes.put(
                                                        reader.table(),
                                                        new CdsExportCapturedTableDto(
                                                                reader.columns(),
                                                                count,
                                                                "a".repeat(64)));
                                                expected.put(
                                                        reader.table(),
                                                        new CdsExportNativeCaptureReader.Table(
                                                                reader.columns(), count));
                                            }
                                        });
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        String manifest = "b".repeat(64);
        var capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(scopes);
        var captures = mock(CdsExportNativeCaptureService.class);
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            expected,
                                            100_000,
                                            64L << 20,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        });
        var lease = lease(manifest);
        var cohort = mock(CdsExportCohortService.Patient.class);
        when(cohort.patient()).thenReturn(patient);
        var drugRows = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var reasonRows = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        new CdsExportCapturedTextRowsReader(captures)
                .forEachTable(
                        lease,
                        cohort,
                        scopes.keySet(),
                        scopes.keySet(),
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", manifest, 1),
                        100_000,
                        64L << 20,
                        1 << 20,
                        mock(CdsExportRunControl.class),
                        row -> {
                            if (row.table().equals("drugs")) drugRows.put(row.recordKey(), row);
                            else
                                reasonRows
                                        .computeIfAbsent(
                                                row.fields().get("drugId"),
                                                ignored -> new ArrayList<>())
                                        .add(row);
                        });
        assertThat(drugRows).hasSize(2);
        var first = drugRows.get(CdsExportPatientTableSourceReader.key(drug));
        var mapped =
                new CdsExportNativeMedicationMapper()
                        .map(
                                patient,
                                first,
                                List.of(),
                                Map.of(),
                                null,
                                reasonRows.get(Integer.toString(drug)));
        assertThat(mapped.value().fields())
                .containsEntry(CdsExportMedicationDto.Field.ProblemCode, "250");
        assertThat(mapped.reasons()).contains("\"code\":\"E11.9\"", "\\u0020\\u0020for");
        var assessed = new ArrayList<CdsExportCapturedTextRowDto>(reasonRows.get(Integer.toString(drug)));
        assessed.add(first);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(assessed, mapped.fields(), 1000, () -> {});
        String xml =
                new String(
                        CdsExportNativeHistoryMapperUnitTest.render(
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                content -> new CdsExportNativeMedicationMapper().write(mapped, content)),
                        StandardCharsets.UTF_8);
        assertThat(xml).contains("<ProblemCode>250</ProblemCode>", "CARLOS.Medication.Reasons.v1");
        // The second prescription's only reason is archived: accounted, no ProblemCode, no residual.
        var second = drugRows.get(CdsExportPatientTableSourceReader.key(drug + 1L));
        var archived =
                new CdsExportMedicationReasonMapper()
                        .map(patient, second, reasonRows.get(Integer.toString(drug + 1)));
        assertThat(archived.problemCode()).isNull();
        assertThat(archived.residual()).isNull();
        assertThat(archived.fields(false).values())
                .allMatch(field -> field.outcome() == CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
    }

    private CdsExportLeaseDto lease(String manifest) {
        var command =
                new CdsExportCommand(
                        "reason-capture",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(patient),
                        EnumSet.of(
                                CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS),
                        "receiver",
                        "audit");
        return new CdsExportLeaseDto(
                UUID.randomUUID().toString(),
                1,
                manifest,
                new CdsExportJobDocumentDto(
                        2,
                        new CdsExportActorDto("agent", "999998", null, null),
                        command,
                        manifest,
                        "sender",
                        manifest,
                        manifest,
                        new CdsExportStorageBindingDto("primary", "primary", manifest)));
    }

    private static String text(io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto cell) {
        return cell.sqlNull() ? null : new String(cell.inlineBytes(), StandardCharsets.UTF_8);
    }
}
