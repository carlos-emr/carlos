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

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportEformRenderReadDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportEformRenderService.Reason;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService.RenderedEformPdf;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * On the actual MariaDB schema: the eForms rendered for a chart are exactly the ones the chart
 * lists, each render is captured only when the snapshot holds the very row it was made from, and
 * the data summaries export the listed eForms while the removed and patient-independent ones are
 * accounted and withheld, in agreement with the captured render outcomes.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportEformRenderMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private static final byte[] PDF = "%PDF-1.7\nsynthetic\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir Path directory;

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private CdsExportEformRenderReadDao eforms;
    private CdsExportDataSummaryReadDao summaries;
    private final int patient =
            590_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);

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
            throw new IllegalArgumentException("Owned disposable care database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(settings.getProperty("jdbcUrl"));
        source.setUsername(settings.getProperty("jdbcUser"));
        source.setPassword(settings.getProperty("jdbcPassword"));
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
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.register(CdsExportEformRenderReadDao.class, CdsExportDataSummaryReadDao.class);
        context.refresh();
        eforms = context.getBean(CdsExportEformRenderReadDao.class);
        summaries = context.getBean(CdsExportDataSummaryReadDao.class);
        read =
                new TransactionTemplate(
                        context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        // A listed eForm, one deleted or replaced by an edit, and a patient-independent one, as
        // AddEForm2Action and the eForm list write them.
        for (int i = 0; i < 3; i++)
            sql.update(
                    "insert into eform_data(fdid,fid,form_name,subject,demographic_no,status,"
                            + "form_date,form_time,form_provider,form_data,patient_independent,"
                            + "showLatestFormOnly,roleType) values(?,1,'Synthetic eForm','Subject',"
                            + "?,?,'2024-05-06','13:00:00','999998','<html>v1</html>',?,0,'')",
                    patient + i,
                    patient,
                    i == 1 ? 0 : 1,
                    i == 2 ? 1 : 0);
        sql.update(
                "insert into eform_values(id,fdid,fid,demographic_no,var_name,var_value)"
                        + " values(?,?,1,?,'weight','70')",
                patient,
                patient + 1,
                patient);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (sql != null) {
                sql.update("delete from eform_values where id=?", patient);
                sql.update("delete from eform_data where demographic_no=?", patient);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    private final CdsExportRunControl control =
            new CdsExportRunControl() {
                @Override
                public void checkpoint() {}

                @Override
                public void captureCheckpoint() {}

                @Override
                public void authorizePatient(int chart) {}

                @Override
                public void authorizeCategory(
                        int chart, CdsCategory category, boolean sensitiveLaboratory) {}

                @Override
                public LoggedInInfo authorizeRendering(int chart) {
                    return new LoggedInInfo();
                }
            };

    private CdsExportEformRenderingSourceReader reader() {
        var settings = new Properties();
        settings.setProperty("cds.export.eformRenderIntervalMillis", "0");
        return new CdsExportEformRenderingSourceReader(
                eforms,
                new CdsExportEformRenderService(
                        (who, fdid) -> {
                            try {
                                Path file = Files.createTempFile(directory, "eform-browser-render-", ".pdf");
                                Files.write(file, PDF);
                                return new RenderedEformPdf(file);
                            } catch (IOException failed) {
                                throw new java.io.UncheckedIOException(failed);
                            }
                        },
                        settings,
                        System::nanoTime));
    }

    /** The prepared reader captured in a repeatable-read snapshot and read back. */
    private Map<String, CdsExportEformRenderings.Rendering> capture(CdsExportNativeSourceReader reader) {
        return read.execute(
                status -> {
                    try {
                        long rows = reader.count(patient);
                        var values = new ByteArrayOutputStream();
                        var index = new ByteArrayOutputStream();
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        index,
                                        Set.of(reader.table()),
                                        1_000_000,
                                        1L << 30,
                                        () -> {},
                                        writer -> {
                                            writer.beginTable(reader.table(), reader.columns(), rows);
                                            reader.capture(patient, writer, () -> {});
                                            writer.endTable();
                                        });
                        var result = new TreeMap<String, CdsExportEformRenderings.Rendering>();
                        var sink = new CdsExportEformRenderings.Fields(1000, result);
                        new CdsExportNativeCaptureReader()
                                .scan(
                                        new ByteArrayInputStream(values.toByteArray()),
                                        new ByteArrayInputStream(index.toByteArray()),
                                        Map.of(
                                                reader.table(),
                                                new CdsExportNativeCaptureReader.Table(
                                                        reader.columns(), rows)),
                                        1_000_000,
                                        1L << 30,
                                        () -> {},
                                        sink);
                        sink.finish();
                        return result;
                    } catch (IOException failed) {
                        throw new java.io.UncheckedIOException(failed);
                    }
                });
    }

    @Test
    void shouldRenderOnlyTheListedEform_withItsAuthorAndADigestOfTheRenderedRow() {
        var current = eforms.current(patient);
        assertThat(current).extracting(CdsExportEformRenderReadDao.Current::fdid).containsExactly(patient);
        assertThat(current.getFirst().authorFirstName()).isEqualTo("doctor");
        assertThat(current.getFirst().authorLastName()).isEqualTo("carlosdoc");
        Long listed = read.execute(status -> eforms.count(patient));
        assertThat(listed).isEqualTo(1L);

        sql.update("update eform_data set form_data='<html>v2</html>' where fdid=?", patient);

        assertThat(eforms.current(patient).getFirst().digest())
                .isNotEqualTo(current.getFirst().digest());
    }

    @Test
    void shouldCaptureTheRender_onlyWhileTheSnapshotHoldsTheRowItWasMadeFrom() throws Exception {
        var spool = new CdsExportEformRenderServiceUnitTest.Spool();
        var reader = reader();

        var captured = capture(reader.forCapture(patient, control, spool));

        assertThat(captured).containsOnlyKeys(Integer.toString(patient));
        assertThat(captured.get(Integer.toString(patient)).rendered()).isTrue();
        assertThat(captured.get(Integer.toString(patient)).content().bytes()).isEqualTo(PDF.length);

        // Edited between the render and the snapshot: the stale render is never exported.
        var prepared = reader.forCapture(patient, control, spool);
        sql.update("update eform_data set subject='Edited' where fdid=?", patient);

        assertThat(capture(prepared).get(Integer.toString(patient)).reason()).isEqualTo(Reason.CHANGED);
    }

    @Test
    void shouldExportTheListedEformAndWithholdTheOthers_inAgreementWithTheRenderOutcomes()
            throws Exception {
        var readers = new TreeMap<String, CdsExportNativeSourceReader>();
        for (var reader : CdsExportDataSummarySourceReader.readers(summaries))
            if (Set.of("eform_data", "eform_values", "EFormDocs").contains(reader.table()))
                readers.put(reader.table(), reader);
        var rows =
                read.execute(
                        status -> {
                            try {
                                var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
                                for (var reader : readers.values())
                                    plan.put(
                                            reader.table(),
                                            new CdsExportNativeCaptureReader.Table(
                                                    reader.columns(), reader.count(patient)));
                                var values = new ByteArrayOutputStream();
                                var index = new ByteArrayOutputStream();
                                new CdsExportNativeCaptureComposer()
                                        .compose(
                                                values,
                                                index,
                                                readers.keySet(),
                                                1_000_000,
                                                1L << 30,
                                                () -> {},
                                                writer -> {
                                                    for (var reader : readers.values()) {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                plan.get(reader.table()).rows());
                                                        reader.capture(patient, writer, () -> {});
                                                        writer.endTable();
                                                    }
                                                });
                                var collector =
                                        new CdsExportDataSummaryRowsReader.Collector(readers.keySet());
                                new CdsExportNativeCaptureReader()
                                        .scan(
                                                new ByteArrayInputStream(values.toByteArray()),
                                                new ByteArrayInputStream(index.toByteArray()),
                                                plan,
                                                1_000_000,
                                                1L << 30,
                                                () -> {},
                                                collector);
                                collector.finish();
                                return collector.rows();
                            } catch (IOException failed) {
                                throw new java.io.UncheckedIOException(failed);
                            }
                        });
        var exported = new java.util.ArrayList<CdsExportDataSummaryPlanner.Group>();
        var withheld = new TreeMap<String, CdsExportDataSummaryOccurrenceService.Withheld>();
        for (var group : CdsExportDataSummaryPlanner.plan(rows, ZoneId.of("America/Toronto"))) {
            var why = CdsExportDataSummaryOccurrenceService.withheld(group);
            if (why == null) exported.add(group);
            else {
                withheld.put(group.id(), why);
                new CdsExportNativeFieldAssessmentValidator()
                        .requireRepresentable(
                                group.rows().stream().map(CdsExportDataSummaryPlanner.Row::assessed).toList(),
                                CdsExportDataSummaryOccurrenceService.withheldAssessments(
                                        new CdsExportDataSummaryOccurrenceService.Hidden(group, why)),
                                1000,
                                () -> {});
            }
        }

        assertThat(exported)
                .extracting(CdsExportDataSummaryPlanner.Group::id)
                .containsExactly(Integer.toString(patient));
        assertThat(withheld)
                .containsEntry(
                        Integer.toString(patient + 1),
                        CdsExportDataSummaryOccurrenceService.Withheld.REMOVED_EFORM)
                .containsEntry(
                        Integer.toString(patient + 2),
                        CdsExportDataSummaryOccurrenceService.Withheld.PATIENT_INDEPENDENT_EFORM);
        // The removed eForm's values row goes with it, not into an exported record.
        assertThat(exported.getFirst().rows()).hasSize(1);
        CdsExportDataSummaryOccurrenceService.requireRenderingScope(
                exported,
                Set.of("eform_data", CdsExportEformRenderingSourceReader.TABLE),
                capture(reader().forCapture(patient, control, new CdsExportEformRenderServiceUnitTest.Spool())));
    }
}
