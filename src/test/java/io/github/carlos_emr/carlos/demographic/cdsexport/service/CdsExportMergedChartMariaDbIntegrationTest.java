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
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.cli.CdsExportCliSpringConfiguration;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliSpringConfiguration;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAbandonService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportJobService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import javax.sql.DataSource;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Charts linked by Merge Records, through the command-line export and import service graphs,
 * against the full Flyway schema in an owned MariaDB database, with real protected staging,
 * policy, recipient envelope, document files, privileges and audit.
 *
 * <p>A head chart, a chart merged into it and a chart merged into that one each hold a stored
 * document, an appointment and an allergy; an older, deleted merge names a fourth chart outside
 * the group. Exporting every category for the merged chart alone writes one patient file, the
 * head's, holding every chart's records, one {@code CARLOS.MergedChart.v1} record per merged
 * chart and one {@code CARLOS.MergeLinks.v1}; the ReadMe says where the selected chart went. The
 * receiving CARLOS (the same database once the sending charts are gone) refuses that file for
 * review when a merged-chart record has been altered, and otherwise imports every chart's records
 * onto one new chart and keeps both records as source. A group one of whose charts the provider
 * may not open is held as a whole while the rest of the job goes on. A stopped job's kept group
 * file is adopted while the group is unchanged and built again once a Merge Records privilege
 * row of the group goes.
 *
 * <p>The first case also proves the capture of a group in one snapshot against every category's
 * real readers: one schema pass for the whole transaction, and each chart's prevention snapshot
 * ended before the next chart's begins.
 */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportMergedChartMariaDbIntegrationTest {
    private static final String OPERATOR = "merged-fixture";
    private static final String PROVIDER = "989873";
    private static final String NAMESPACE = "merged-fixture";
    /**
     * The altered file arrives from its own source: the receiving CARLOS treats a second, different
     * file for a patient one source already sent as a revision needing review.
     */
    private static final String ALTERED = "merged-altered";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** The charts: the group's head, its two merged charts, then charts outside the group. */
    private static final int HEAD = 0, MEMBER = 1, CHAINED = 2, OUTSIDE = 3, OTHER = 4;

    private final List<Integer> patients = new ArrayList<>();
    private final List<Integer> imported = new ArrayList<>();
    private final List<Integer> fixtureIssues = new ArrayList<>();
    private final List<String> importRuns = new ArrayList<>();
    private final String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private Path base, documents, keys;
    private Properties jdbc;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private CdsExportRecipientFixture recipient;
    private String run;
    private final List<String> exportRuns = new ArrayList<>();

    @BeforeEach
    void ownedDatabaseStorageAndPolicy() throws Exception {
        jdbc = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            jdbc.load(input);
        }
        String url = jdbc.getProperty("jdbcUrl", "");
        assertThat(url).matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?");
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        // Protected staging refuses an ancestor another user owns or could write to; the native
        // tier supplies its own private root, elsewhere the process home is used.
        base =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "cds-export-merged-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        Files.setPosixFilePermissions(base, PosixFilePermissions.fromString("rwx------"));
        var store = directory(base.resolve("store"));
        keys = directory(base.resolve("keys"));
        var policy = directory(base.resolve("policy"));
        documents = directory(base.resolve("documents"));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        protectedFile(keys.resolve("primary.key"), key);
        recipient = new CdsExportRecipientFixture(Instant.now());
        policy(policy);
        var settings = database(url);
        settings.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        settings.setProperty("cds.export.enabled", "true");
        settings.setProperty("cds.export.operator." + OPERATOR + ".providers", PROVIDER);
        // The JVM's own zone, never changed (see CdsExportResumeMariaDbIntegrationTest).
        settings.setProperty("cds.export.clinicalZone", TimeZone.getDefault().toZoneId().getId());
        settings.setProperty("cds.export.store.active", "primary");
        settings.setProperty("cds.export.store.primary.root", store.toString());
        settings.setProperty("cds.export.masterKey.active", "primary");
        settings.setProperty("cds.export.masterKeyRoot", keys.toString());
        settings.setProperty("cds.export.minimumFreeBytes", "0");
        settings.setProperty("cds.export.policyRoot", policy.toString());
        settings.setProperty("DOCUMENT_DIR", documents.toString());
        // An imported chart's witnesses are sealed with the import's key, and its retained
        // source is read from the import's staging root (see importer()).
        settings.setProperty("cds.nativeBaseline.keyRoot", keys.toString());
        settings.setProperty("cds.nativeBaseline.activeKey", "primary");
        settings.setProperty(
                "cds.import.stagingRoot", directory(base.resolve("import-runs")).toString());
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("cds-export-cli");
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new PropertiesPropertySource("owned-merged-fixture", settings));
        context.registerBean("cdsExportProperties", Properties.class, () -> settings);
        context.register(CdsExportCliSpringConfiguration.class);
        context.refresh();
        sql = new JdbcTemplate(context.getBean(DataSource.class));
        assertThat(sql.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("cds_export_test");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from provider where provider_no=?",
                                Long.class,
                                PROVIDER))
                .isZero();
    }

    @AfterEach
    void eraseOwnedRowsAndFiles() throws Exception {
        try {
            if (sql != null) {
                // A run a failed assertion left stopped would keep the clinic in maintenance.
                for (String importRun : importRuns)
                    if (List.of("INCOMPLETE", "RECONCILIATION_REQUIRED")
                            .contains(runState(importRun)))
                        try {
                            release(importRun);
                        } catch (Exception failed) {
                            throw new IOException("Import run left in maintenance", failed);
                        }
                for (String exportRun : exportRuns) {
                    sql.update("delete from cds_export_held_patient where run_id=?", exportRun);
                    sql.update("delete from cds_export_event where run_id=?", exportRun);
                    sql.update("delete from cds_export_attempt where run_id=?", exportRun);
                    sql.update("delete from cds_export_run where id=?", exportRun);
                }
                eraseImports();
                var charts = new ArrayList<>(patients);
                charts.addAll(imported);
                for (int patient : charts) {
                    sql.update(
                            "delete from demographic_merged where demographic_no=? or merged_to=?",
                            patient,
                            patient);
                    sql.update(
                            "delete from secObjPrivilege where objectName in (?,?)",
                            "_eChart$" + patient,
                            "_demographic$" + patient);
                    eraseChart(patient);
                }
                for (int issue : fixtureIssues) sql.update("delete from issue where issue_id=?", issue);
                sql.update("delete from secObjPrivilege where roleUserGroup=?", PROVIDER);
                sql.update("delete from provider where provider_no=?", PROVIDER);
            }
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (base != null && Files.exists(base))
                    try (var files = Files.walk(base)) {
                        for (var path : files.sorted(Comparator.reverseOrder()).toList())
                            Files.deleteIfExists(path);
                    }
            }
        }
    }

    @Test
    void shouldImportEveryMergedChartsRecordsOntoOneChart_whenAMergedGroupMakesTheRoundTrip()
            throws Exception {
        seed(4);
        for (int chart : List.of(HEAD, MEMBER, CHAINED)) {
            records(chart);
            historyRecords(chart);
        }
        // The chart merged into the head, a chart merged into that one, an older merge naming a
        // chart outside the group that was undone, and the Merge Records page's privilege rows.
        link(MEMBER, HEAD, 0);
        link(CHAINED, MEMBER, 0);
        link(OUTSIDE, MEMBER, 1);
        mergePrivilege(MEMBER);
        mergePrivilege(CHAINED);

        // Every category, for the merged chart alone.
        byte[] zip =
                export(List.of(patient(MEMBER)), EnumSet.allOf(CdsCategory.class), true);
        var entries = entries(zip);
        var xmlFiles = entries.keySet().stream().filter(name -> name.endsWith(".xml")).toList();
        assertThat(xmlFiles).hasSize(1);
        String xml = new String(entries.get(xmlFiles.getFirst()), StandardCharsets.UTF_8);
        // One patient (the head's Demographics), every chart's records in their sections.
        assertThat(count(xml, "Demographics")).isEqualTo(1);
        assertThat(xml).contains("FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + HEAD);
        assertThat(count(xml, "Appointments")).isEqualTo(3);
        assertThat(count(xml, "AllergiesAndAdverseReactions")).isEqualTo(3);
        assertThat(count(xml, "Reports")).isEqualTo(3);
        assertThat(count(xml, "FamilyHistory")).isEqualTo(3);
        assertThat(categories(xml, "CARLOS.NativeIssues.v1")).isEqualTo(3);
        assertThat(
                        entries.keySet().stream()
                                .filter(name -> name.contains("/Reports/"))
                                .toList())
                .hasSize(3);
        // One record per merged chart, then the file's links.
        assertThat(categories(xml, CdsMergedChartContract.CHART_RECORD)).isEqualTo(2);
        assertThat(categories(xml, CdsMergedChartContract.LINKS_RECORD)).isEqualTo(1);
        // Every chart of the file is audited as exported; the chart outside it is not.
        assertThat(
                        new TreeSet<>(
                                sql.queryForList(
                                        "select demographic_no from log where action='EXPORT'"
                                                + " and data like ?",
                                        Integer.class,
                                        "CDS_EXPORT_PACKAGE_PREPARED run=" + run + " %")))
                .containsExactlyInAnyOrder(patient(HEAD), patient(MEMBER), patient(CHAINED));
        String readMe = text(entries, "ReadMe.txt");
        assertThat(readMe)
                .contains(line(CdsExportMergedChartReadMe.CHARTS_SELECTED, "1"))
                .contains(line(CdsExportMergedChartReadMe.PATIENT_FILES, "1"))
                .contains(
                        line(
                                CdsExportMergedChartReadMe.SELECTED_ELSEWHERE,
                                patient(MEMBER) + " -> " + patient(HEAD)))
                .contains(
                        line(
                                CdsExportMergedChartReadMe.RECORDS,
                                CdsExportMergedChartReadMe.WRITTEN))
                .contains(
                        line(
                                CdsExportMergedChartReadMe.LINKS,
                                "3 (active 2, deleted 1, outside this file 1)"))
                .contains(line(CdsExportMergedChartReadMe.SHAPE, "CHAIN"));
        for (int chart : List.of(MEMBER, CHAINED))
            assertThat(readMe)
                    .containsPattern(
                            Pattern.quote(CdsExportMergedChartReadMe.MERGED_CHART + ": \"")
                                    + patient(chart)
                                    + ": [^\"]*Appointments 1[^\"]*\"");
        assertThat(text(entries, "ExportEventLog.csv"))
                .contains(
                        CdsExportMergedChartReadMe.EVENT
                                + " "
                                + patient(MEMBER)
                                + " -> "
                                + patient(HEAD))
                .contains(
                        CdsExportMergedChartReadMe.EVENT
                                + " "
                                + patient(CHAINED)
                                + " -> "
                                + patient(HEAD));

        // The receiving CARLOS: the sending charts are not there.
        for (int chart : List.of(HEAD, MEMBER, CHAINED, OUTSIDE)) unlink(chart);
        for (int chart : List.of(HEAD, MEMBER, CHAINED, OUTSIDE)) eraseChart(patient(chart));
        var source = directory(base.resolve("import-source"));
        // A merged-chart record whose document no longer has the digest it states.
        var digest =
                Pattern.compile(
                                "(documentSha256(?:\"|&quot;):(?:\"|&quot;))([0-9a-f]{64})")
                        .matcher(xml);
        assertThat(digest.find()).isTrue();
        String altered =
                xml.substring(0, digest.start(2))
                        + "0".repeat(64)
                        + xml.substring(digest.end(2));
        // The receiver imports the decrypted package as it arrived, its attachments included.
        var alteredEntries = new TreeMap<>(entries);
        alteredEntries.put(xmlFiles.getFirst(), altered.getBytes(StandardCharsets.UTF_8));
        var refusedRun =
                importFile(
                        protectedFile(source.resolve("altered.zip"), zip(alteredEntries)),
                        ALTERED);
        assertThat(patientState(refusedRun)).isEqualTo("REFUSED");
        assertThat(target(refusedRun)).isNull();
        assertThat(diagnostics(refusedRun)).contains("CDS_MERGED_CHART_CARRIER_INVALID");
        // Nothing of the patient was written; the run stops for review and keeps the clinic in
        // maintenance until its operator abandons it.
        assertThat(runState(refusedRun)).isEqualTo("INCOMPLETE");
        release(refusedRun);
        assertThat(runState(refusedRun)).isEqualTo("ABANDONED");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographic where last_name like ?",
                                Long.class,
                                "FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-%"))
                .isZero();

        var importRun = importFile(protectedFile(source.resolve("package.zip"), zip), NAMESPACE);
        Integer chart = target(importRun);
        assertThat(chart).isNotNull();
        imported.add(chart);
        assertThat(diagnostics(importRun))
                .contains("CDS_MERGED_CHARTS_IMPORTED")
                .doesNotContain("CDS_MERGED_CHART_CARRIER_INVALID");
        assertThat(
                        sql.queryForObject(
                                "select safe_summary from cds_import_diagnostic where run_id=? and"
                                        + " code='CDS_MERGED_CHARTS_IMPORTED'",
                                String.class,
                                importRun))
                .isEqualTo("2 merged charts from the sending CARLOS imported into this chart");
        // Every chart's records are on the one imported chart.
        assertThat(
                        sql.queryForObject(
                                "select last_name from demographic where demographic_no=?",
                                String.class,
                                chart))
                .isEqualTo("FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + HEAD);
        assertThat(rows("appointment", chart)).isEqualTo(3);
        assertThat(rows("allergies", chart)).isEqualTo(3);
        assertThat(rows("casemgmt_note", chart)).isEqualTo(3);
        assertThat(sql.queryForList("select encounter_type from casemgmt_note where demographic_no=?",
                String.class, chart)).containsOnly("Exact  merged encounter");
        assertThat(sql.queryForList("select e.value from casemgmt_note_ext e join casemgmt_note n on n.note_id=e.note_id "
                + "where n.demographic_no=? and e.key_val='Treatment'", String.class, chart))
                .containsExactlyInAnyOrder("First 0", "Second 0", "First 1", "Second 1", "First 2", "Second 2");
        assertThat(sql.queryForObject("select count(*) from casemgmt_note_ext e join casemgmt_note n on n.note_id=e.note_id "
                + "where n.demographic_no=? and e.key_val='Start Date' and e.value='YYYY' and e.date_value='2001-07-23'",
                Long.class, chart)).isEqualTo(3);
        assertThat(sql.queryForObject("select count(*) from casemgmt_issue p join issue i on i.issue_id=p.issue_id "
                + "where p.demographic_no=? and i.code=? and p.acute=1 and p.major=1 and p.type='general  ' "
                + "and p.update_date='2025-01-02 03:04:05'", Long.class, chart, "CDSM" + token)).isEqualTo(3);
        assertThat(
                        new TreeSet<>(
                                sql.queryForList(
                                        "select reason from appointment where demographic_no=?",
                                        String.class,
                                        chart)))
                .containsExactly(
                        "FAKE merged visit " + HEAD,
                        "FAKE merged visit " + MEMBER,
                        "FAKE merged visit " + CHAINED);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from ctl_document where module='demographic' and"
                                        + " module_id=?",
                                Long.class,
                                chart))
                .isEqualTo(3L);
        // No Merge Records link is restored; both records are kept as source.
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographic_merged where demographic_no=? or"
                                        + " merged_to=?",
                                Long.class,
                                chart,
                                chart))
                .isZero();
        assertThat(
                        sql.queryForList(
                                "select original_value from cds_import_field where run_id=? and"
                                        + " demographic_no=? and source_path like"
                                        + " '%/NewCategory[%]/CategoryName%'",
                                String.class,
                                importRun,
                                chart))
                .filteredOn(name -> name.startsWith("CARLOS.Merge"))
                .containsExactlyInAnyOrder(
                        CdsMergedChartContract.CHART_RECORD,
                        CdsMergedChartContract.CHART_RECORD,
                        CdsMergedChartContract.LINKS_RECORD);

        // Exported again from the receiving CARLOS, the chart is one chart: every record is
        // there, and the records the sender wrote about its own charts are not written again.
        var again = entries(export(List.of(chart), EnumSet.allOf(CdsCategory.class), true));
        var againFiles = again.keySet().stream().filter(name -> name.endsWith(".xml")).toList();
        assertThat(againFiles).hasSize(1);
        String reexported = new String(again.get(againFiles.getFirst()), StandardCharsets.UTF_8);
        assertThat(count(reexported, "Appointments")).isEqualTo(3);
        assertThat(count(reexported, "AllergiesAndAdverseReactions")).isEqualTo(3);
        assertThat(count(reexported, "Reports")).isEqualTo(3);
        assertThat(count(reexported, "FamilyHistory")).isEqualTo(3);
        assertThat(categories(reexported, "CARLOS.NativeIssues.v1")).isEqualTo(3);
        assertThat(categories(reexported, "CARLOS.NativeIssues.Source.v1")).isEqualTo(3);
        assertThat(categories(reexported, CdsMergedChartContract.CHART_RECORD)).isZero();
        assertThat(categories(reexported, CdsMergedChartContract.LINKS_RECORD)).isZero();
        assertThat(text(again, "ReadMe.txt"))
                .contains(
                        line(
                                CdsExportSupportingFilesComposer.NOT_EXPORTED,
                                CdsExportRetainedMergedChartRecords.NOT_EXPORTED_LABEL + ": 3"))
                .doesNotContain(CdsExportMergedChartReadMe.CHARTS_SELECTED);
    }

    @Test
    void shouldHoldTheWholeGroup_whenOneOfItsChartsIsRefused() throws Exception {
        seed(5);
        for (int chart : List.of(HEAD, MEMBER, OTHER)) records(chart);
        link(MEMBER, HEAD, 0);
        mergePrivilege(MEMBER);
        // The provider may open the head, but not the chart merged into it.
        sql.update(
                "insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)"
                        + " values(?,?,'|o|',0,'0')",
                PROVIDER,
                "_demographic$" + patient(MEMBER));

        byte[] zip =
                export(
                        List.of(patient(MEMBER), patient(OTHER)),
                        EnumSet.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.APPOINTMENTS,
                                CdsCategory.NEW_CATEGORY),
                        false);
        // The group is held under the head's number; the other chart is exported as usual.
        assertThat(
                        sql.queryForList(
                                "select concat(demographic_no,' ',safe_code) from"
                                        + " cds_export_held_patient where run_id=?",
                                String.class,
                                run))
                .containsExactly(patient(HEAD) + " CDS_EXPORT_MERGED_CHART_ACCESS");
        var entries = entries(zip);
        var xmlFiles = entries.keySet().stream().filter(name -> name.endsWith(".xml")).toList();
        assertThat(xmlFiles).hasSize(1);
        String xml = new String(entries.get(xmlFiles.getFirst()), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + OTHER)
                .doesNotContain("FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + HEAD)
                .doesNotContain("FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + MEMBER);
        assertThat(
                        new TreeSet<>(
                                sql.queryForList(
                                        "select demographic_no from log where action='EXPORT'"
                                                + " and data like ?",
                                        Integer.class,
                                        "CDS_EXPORT_PACKAGE_PREPARED run=" + run + " %")))
                .containsExactly(patient(OTHER));
        assertThat(text(entries, "ReadMe.txt"))
                .contains(line("Held patient", Integer.toString(patient(HEAD))))
                .contains(line("Held patient reason", "CDS_EXPORT_MERGED_CHART_ACCESS"))
                .contains(
                        line(
                                CdsExportMergedChartReadMe.HELD_REFUSED,
                                Integer.toString(patient(MEMBER))))
                .contains(
                        line(
                                CdsExportMergedChartReadMe.HELD_SELECTED_AS,
                                Integer.toString(patient(MEMBER))))
                .contains(line(CdsExportMergedChartReadMe.CHARTS_SELECTED, "2"));
    }

    @Test
    void shouldAdoptAKeptMergedFileOnlyWhileItsGroupIsUnchanged_whenAStoppedExportIsResumed()
            throws Exception {
        seed(5);
        for (int chart : List.of(HEAD, MEMBER, OTHER)) records(chart);
        link(MEMBER, HEAD, 0);
        mergePrivilege(MEMBER);
        // The other chart's file is writable by its group, which the document storage policy
        // does not trust: every attempt stops there, after the group's file is finished and kept.
        Files.setPosixFilePermissions(
                documents.resolve(filename(OTHER)), PosixFilePermissions.fromString("rw-rw----"));
        var facade = context.getBean(CdsExportService.class);
        try (var spring = bridge()) {
            run =
                    facade.acceptDelegated(
                                    OPERATOR,
                                    command(
                                            List.of(patient(MEMBER), patient(OTHER)),
                                            EnumSet.of(
                                                    CdsCategory.DEMOGRAPHICS,
                                                    CdsCategory.REPORTS,
                                                    CdsCategory.APPOINTMENTS,
                                                    CdsCategory.NEW_CATEGORY)))
                            .id();
            exportRuns.add(run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(1)).containsExactly(1L, 0L);

            // Nothing changed: the group's kept file is adopted.
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(2)).containsExactly(1L, 1L);

            // The Merge Records privilege row of the merged chart goes: the group is the same,
            // but what its file carries is not, so the file is built again.
            sql.update(
                    "delete from secObjPrivilege where roleUserGroup='_all' and objectName=?",
                    "_eChart$" + patient(MEMBER));
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(3)).containsExactly(1L, 0L);

            // Unmerge changes the file's membership; selecting the former member now writes
            // that chart alone. Re-merging and then reversing the head must each rebuild too.
            sql.update("update demographic_merged set deleted=1 where demographic_no=? and deleted=0", patient(MEMBER));
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(4)).containsExactly(1L, 0L);
            link(MEMBER, HEAD, 0);
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(5)).containsExactly(1L, 0L);
            sql.update("update demographic_merged set deleted=1 where demographic_no=? and deleted=0", patient(MEMBER));
            link(HEAD, MEMBER, 0);
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(facade.executeDelegated(OPERATOR, PROVIDER, run));
            assertThat(keptAndAdopted(6)).containsExactly(1L, 0L);

            // The storage is fixed: the last attempt adopts the rebuilt file and publishes.
            Files.setPosixFilePermissions(
                    documents.resolve(filename(OTHER)), PosixFilePermissions.fromString("rw-------"));
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            var last = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertThat(last.state()).isEqualTo(CdsExportState.READY);
            assertThat(keptAndAdopted(7).get(1)).isEqualTo(1L);
            var entries = entries(download(facade));
            assertThat(entries.keySet().stream().filter(name -> name.endsWith(".xml")))
                    .hasSize(2);
            assertThat(text(entries, "ReadMe.txt"))
                    .contains(
                            line(
                                    CdsExportMergedChartReadMe.LINKS,
                                    "3 (active 1, deleted 2, outside this file 0)"));
        }
    }

    /** Runs the job through the command-line service graph and returns its decrypted package. */
    private byte[] export(List<Integer> selected, Set<CdsCategory> categories, boolean complete)
            throws Exception {
        var facade = context.getBean(CdsExportService.class);
        try (var spring = bridge()) {
            run = facade.acceptDelegated(OPERATOR, command(selected, categories)).id();
            exportRuns.add(run);
            CdsExportJobDto job = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertThat(job.state()).isEqualTo(CdsExportState.READY);
            assertThat(job.heldBack()).isEqualTo(!complete);
            return download(facade);
        }
    }

    /**
     * The privilege reader resolves its DAO through the published Spring bridge; every bean it
     * asks for comes from this context, looked up as the bridge looks it up.
     */
    private org.mockito.MockedStatic<SpringUtils> bridge() {
        return mockStatic(
                SpringUtils.class,
                invocation -> {
                    var arguments = invocation.getArguments();
                    if (invocation.getMethod().getName().equals("getBean")
                            && arguments.length == 1) {
                        if (arguments[0] instanceof Class<?> type) return bean(type);
                        if (arguments[0] instanceof String name) return context.getBean(name);
                    }
                    if (invocation.getMethod().getName().equals("getBeanFactory"))
                        return context.getBeanFactory();
                    return invocation.callRealMethod();
                });
    }

    private CdsExportCommand command(List<Integer> selected, Set<CdsCategory> categories) {
        return new CdsExportCommand(
                // One submission per job: a second job of the test is another submission.
                "merged-" + token + "-" + exportRuns.size(),
                PROVIDER,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                selected,
                categories,
                "receiver",
                "approved");
    }

    /** The published package, decrypted as its recipient would. */
    private byte[] download(CdsExportService facade) throws Exception {
        var actor = facade.downloadActorDelegated(OPERATOR, PROVIDER, run);
        var output = new ByteArrayOutputStream();
        try (var download =
                context.getBean(CdsExportArtifactService.class).open(actor, run, "package")) {
            download.writeTo(output);
            download.complete();
        }
        var parsed = new CMSAuthEnvelopedData(output.toByteArray());
        var info = parsed.getRecipientInfos().getRecipients().iterator().next();
        return info.getContent(
                new JceKeyTransAuthEnvelopedRecipient(recipient.keys.getPrivate())
                        .setProvider(new BouncyCastleProvider()));
    }

    /** How many finished patient files attempt {@code epoch} kept, and how many it adopted. */
    private List<Long> keptAndAdopted(long epoch) {
        return sql.queryForObject(
                "select checkpoint_charts, adopted_charts from cds_export_attempt where run_id=?"
                        + " and epoch=?",
                (row, number) -> List.of(row.getLong(1), row.getLong(2)),
                run,
                epoch);
    }

    private static void assertStopped(CdsExportJobDto job) {
        // The storage policy refuses the untrusted file: the job stops, resumable.
        assertThat(job.state()).isIn(CdsExportState.REVIEW_REQUIRED, CdsExportState.FAILED);
    }

    /** Work done inside the command-line import service graph. */
    @FunctionalInterface
    private interface ImporterWork<T> {
        T run(AnnotationConfigApplicationContext importer) throws Exception;
    }

    /**
     * Runs {@code work} in the command-line import service graph of the receiving CARLOS, with
     * the same database and the import's own settings, as the import command would.
     */
    private <T> T importer(ImporterWork<T> work) throws Exception {
        var settings = new Properties();
        try (InputStream defaults =
                CdsCliSpringConfiguration.class.getResourceAsStream("/jdbc-defaults.properties")) {
            if (defaults != null) settings.load(defaults);
        }
        settings.putAll(database(jdbc.getProperty("jdbcUrl")));
        settings.setProperty("db_driver", "org.mariadb.jdbc.Driver");
        settings.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        settings.setProperty("db_default_use_information_schema", "false");
        settings.setProperty("carlos.flyway.onBoot", "validate");
        for (String flag : List.of("enabled", "maintenanceGuardEnabled", "maintenanceMode"))
            settings.setProperty("cds.import." + flag, "true");
        settings.setProperty("cds.import.clinicalZone", TimeZone.getDefault().toZoneId().getId());
        settings.setProperty(
                "cds.import.allowedRoot", directory(base.resolve("import-source")).toString());
        settings.setProperty(
                "cds.import.stagingRoot", directory(base.resolve("import-runs")).toString());
        settings.setProperty(
                "cds.import.uploadRoot", directory(base.resolve("import-uploads")).toString());
        settings.setProperty("cds.nativeBaseline.keyRoot", keys.toString());
        settings.setProperty("cds.nativeBaseline.activeKey", "primary");
        settings.setProperty("DOCUMENT_DIR", documents.toString());
        settings.setProperty("cds.import.sourceNamespaces", NAMESPACE + "," + ALTERED);
        for (String namespace : List.of(NAMESPACE, ALTERED))
            settings.setProperty("cds.import.source." + namespace + ".profiles", "omd-5.2");
        settings.setProperty("cds.import.operator." + OPERATOR + ".providers", PROVIDER);
        var carlos = CarlosProperties.getInstance();
        String documentDirectory = carlos.getProperty("DOCUMENT_DIR");
        BeanFactory previous = SpringUtils.getBeanFactory();
        try (var importer = new AnnotationConfigApplicationContext()) {
            importer.getEnvironment().setActiveProfiles("cds-import-cli");
            importer.getEnvironment()
                    .getPropertySources()
                    .addFirst(new PropertiesPropertySource("owned-merged-import", settings));
            importer.registerBean("cdsImportProperties", Properties.class, () -> settings);
            importer.register(CdsCliSpringConfiguration.class);
            importer.refresh();
            // The import's native helpers read the published bridge and the document store, as
            // in the command-line runtime's own JVM; both are put back afterwards.
            publish(importer);
            carlos.setProperty("DOCUMENT_DIR", documents.toString());
            return work.run(importer);
        } finally {
            publish(previous);
            if (documentDirectory == null) carlos.remove("DOCUMENT_DIR");
            else carlos.setProperty("DOCUMENT_DIR", documentDirectory);
        }
    }

    /**
     * Publishes {@code factory} on the Spring bridge. The bridge keeps the first factory it is
     * given, as a one-shot command-line JVM publishes exactly one; a test JVM that runs several
     * contexts in turn replaces it and puts the earlier one back.
     */
    private static void publish(BeanFactory factory) throws ReflectiveOperationException {
        var field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, factory);
    }

    /** Imports one package as the receiving CARLOS would, and returns the run. */
    private String importFile(Path file, String namespace) throws Exception {
        return importer(
                importer -> {
                    var command =
                            new CdsImportCommand(
                                    file,
                                    "merged-" + UUID.randomUUID(),
                                    PROVIDER,
                                    null,
                                    null,
                                    namespace,
                                    "omd-5.2",
                                    false,
                                    false,
                                    true,
                                    0,
                                    null,
                                    false);
                    var actor =
                            importer.getBean(CdsImportAccessService.class)
                                    .delegatedActor(OPERATOR, command);
                    var service = importer.getBean(CdsImportService.class);
                    String id = service.accept(actor, command).id();
                    importRuns.add(id);
                    service.runAccepted(actor, id);
                    return id;
                });
    }

    /**
     * The operator's supported way out of a run that ended with a patient refused for review:
     * cancel it and abandon it, which releases the clinic.
     */
    private void release(String importRun) throws Exception {
        importer(
                importer -> {
                    var command =
                            importer.getBean(CdsImportJobService.class).load(importRun).command();
                    var actor =
                            importer.getBean(CdsImportAccessService.class)
                                    .delegatedActor(OPERATOR, command);
                    var service = importer.getBean(CdsImportService.class);
                    service.cancel(actor, importRun);
                    service.authorizeAbandon(actor, importRun);
                    importer.getBean(CdsImportAbandonService.class)
                            .abandon(
                                    importRun,
                                    actor,
                                    importRun,
                                    "FAKE merged-chart fixture: the refused file was reviewed");
                    return null;
                });
    }

    private Properties database(String url) {
        var settings = new Properties();
        int slash = url.indexOf('/', url.indexOf("://") + 3);
        settings.setProperty("db_uri", url.substring(0, slash + 1));
        settings.setProperty("db_name", url.substring(slash + 1));
        settings.setProperty("db_username", jdbc.getProperty("jdbcUser"));
        settings.setProperty("db_password", jdbc.getProperty("jdbcPassword"));
        settings.setProperty("billregion", "ON");
        settings.setProperty("cds.export.worker.enabled", "false");
        settings.setProperty("cds.import.worker.enabled", "false");
        return settings;
    }

    private String runState(String importRun) {
        return sql.queryForObject(
                "select state from cds_import_run where id=?", String.class, importRun);
    }

    private String patientState(String importRun) {
        return sql.queryForObject(
                "select state from cds_import_patient where run_id=?", String.class, importRun);
    }

    private Integer target(String importRun) {
        return sql.queryForObject(
                "select target_demographic_no from cds_import_patient where run_id=?",
                Integer.class,
                importRun);
    }

    private List<String> diagnostics(String importRun) {
        return sql.queryForList(
                "select code from cds_import_diagnostic where run_id=?", String.class, importRun);
    }

    private long rows(String table, int chart) {
        return sql.queryForObject(
                "select count(*) from " + table + " where demographic_no=?", Long.class, chart);
    }

    /** The bean SpringUtils would return: by its conventional name, else the first of its type. */
    private Object bean(Class<?> type) {
        String name =
                Character.toLowerCase(type.getSimpleName().charAt(0))
                        + type.getSimpleName().substring(1);
        if (context.containsBean(name)) return context.getBean(name);
        String[] names = context.getBeanNamesForType(type);
        if (names.length == 0)
            throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(type);
        return context.getBean(names[0]);
    }

    /** Every entry of the package, by name. */
    private static Map<String, byte[]> entries(byte[] zip) throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null)
                if (!entry.isDirectory()) result.put(entry.getName(), input.readAllBytes());
        }
        return result;
    }

    /** A package of these entries, in name order. */
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    /** The one entry whose name ends with {@code suffix}, as text. */
    private static String text(Map<String, byte[]> entries, String suffix) {
        var matches =
                entries.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith(suffix))
                        .toList();
        assertThat(matches).hasSize(1);
        return new String(matches.getFirst().getValue(), StandardCharsets.UTF_8);
    }

    /** A ReadMe field as the supporting-file composer writes it. */
    private static String line(String label, String value) throws IOException {
        return label + ": " + JSON.writeValueAsString(value) + "\n";
    }

    /** How many elements of this name the patient file holds. */
    private static long count(String xml, String element) {
        return Pattern.compile("<(?:[A-Za-z0-9]+:)?" + element + "[ >]").matcher(xml).results().count();
    }

    /** How many NewCategory records of this name the patient file holds. */
    private static long categories(String xml, String name) {
        return Pattern.compile(
                        "<(?:[A-Za-z0-9]+:)?CategoryName>" + Pattern.quote(name) + "</")
                .matcher(xml)
                .results()
                .count();
    }

    private void seed(int charts) throws IOException {
        sql.update(
                "insert into provider(provider_no,first_name,last_name,provider_type,specialty,sex,"
                        + "status,lastUpdateDate) values(?,'Merged','FAKE-Fixture','doctor','','U',"
                        + "'1',CURRENT_TIMESTAMP)",
                PROVIDER);
        // Every category's read right, the import's write right and the export right itself.
        var objects =
                List.of(
                        "_rx",
                        "_allergy",
                        "_prevention",
                        "_lab",
                        "_lab_sensitive",
                        "_edoc",
                        "_eform",
                        "_appointment",
                        "_measurement",
                        "_casemgmt.notes",
                        "_eChart",
                        "_dxresearch",
                        "_con",
                        "_form");
        for (String object : objects)
            sql.update(
                    "insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority)"
                            + " values(?,?,'|r|',10)",
                    PROVIDER,
                    object);
        sql.update(
                "insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority) values"
                        + "(?,'_demographic','|x|',10),(?,'_cds_export','|w|',10)",
                PROVIDER,
                PROVIDER);
        int first = 430_000_000 + ThreadLocalRandom.current().nextInt(1_000_000) * 10;
        for (int index = 0; index < charts; index++) {
            int patient = first + index;
            patients.add(patient);
            sql.update(
                    "insert into demographic(demographic_no,last_name,first_name,year_of_birth,"
                            + "month_of_birth,date_of_birth,sex,patient_status,address,city,province,"
                            + "postal,phone,provider_no,lastUpdateDate) values(?,?,'Synthetic','1971',"
                            + "'03','04','F','AC','1 FAKE Merged Road','Ottawa','ON','K1A0B1',"
                            + "'613-555-0100',?,'2025-01-02 12:00:00')",
                    patient,
                    "FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + index,
                    PROVIDER);
        }
    }

    /** A stored document, an appointment and an allergy of one chart. */
    private void records(int index) throws IOException {
        int patient = patient(index);
        sql.update(
                "insert into document(document_no,doctype,docdesc,docfilename,doccreator,"
                        + "responsible,source,program_id,updatedatetime,status,contenttype,"
                        + "contentdatetime,public1,observationdate,number_of_pages,"
                        + "restrictToProgram,abnormal) values(?,'lab',?,?,?,?,'FAKE Merged Lab',"
                        + "NULL,'2025-02-03 12:00:00','A','application/pdf',"
                        + "'2025-02-03 12:00:00',0,'2025-02-03',1,0,0)",
                patient,
                "FAKE merged report " + index,
                filename(index),
                PROVIDER,
                PROVIDER);
        sql.update(
                "insert into ctl_document(module,module_id,document_no,status)"
                        + " values('demographic',?,?,'A')",
                patient,
                patient);
        var file = documents.resolve(filename(index));
        Files.write(
                file,
                ("%PDF-1.4\n% FAKE merged fixture report " + index + "\n%%EOF\n")
                        .getBytes(StandardCharsets.US_ASCII));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        sql.update(
                "insert into appointment(provider_no,appointment_date,start_time,end_time,name,"
                        + "demographic_no,program_id,notes,reason,location,resources,type,style,"
                        + "billing,status,createdatetime,updatedatetime,creator,lastupdateuser,"
                        + "remarks,urgency) values(?,?,'09:00:00','09:14:00',?,?,0,'',?,'','','',"
                        + "'','','t','2025-02-01 10:00:00','2025-02-01 10:00:00',?,?,'','')",
                PROVIDER,
                "2025-02-1" + index,
                "FAKE-MERGED-" + token.toUpperCase(Locale.ROOT) + "-" + index,
                patient,
                "FAKE merged visit " + index,
                PROVIDER,
                PROVIDER);
        sql.update(
                "insert into allergies(demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,"
                        + "archived,start_date,age_of_onset,severity_of_reaction,onset_of_reaction,"
                        + "position,lastUpdateDate,providerNo,nonDrug) values(?,'2025-01-03',?,0,"
                        + "'FAKE rash',0,'2024-12-01','0','1','1',0,'2025-01-03 12:00:00',?,0)",
                patient,
                "FAKE merged allergen " + index,
                PROVIDER);
    }

    /** Native CPP extensions and unlinked markers survive every member-to-head boundary. */
    private void historyRecords(int index) {
        int patient = patient(index);
        int family = definition("FamHistory");
        int marker = definition("CDSM" + token);
        sql.update("insert into casemgmt_note(note_id,update_date,observation_date,demographic_no,provider_no,"
                + "note,signed,signing_provider_no,program_no,reporter_caisi_role,reporter_program_team,history,"
                + "locked,position,uuid,encounter_type) values(?,'2025-01-02 03:04:05','2025-01-02 03:04:05',"
                + "?,?,?,1,?,'','1','0',?,'0',0,?,'Exact  merged encounter')",
                patient, patient, PROVIDER, "FAKE merged CPP " + index, PROVIDER,
                "FAKE merged CPP " + index, UUID.randomUUID().toString());
        String issue = "insert into casemgmt_issue(demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date) "
                + "values(?,?,1,0,1,0,NULL,'general  ','2025-01-02 03:04:05')";
        sql.update(issue, patient, family);
        int link = sql.queryForObject("select id from casemgmt_issue where demographic_no=? and issue_id=?",
                Integer.class, patient, family);
        sql.update("insert into casemgmt_issue_notes(id,note_id) values(?,?)", link, patient);
        sql.update(issue, patient, marker);
        sql.update("insert into casemgmt_note_ext(note_id,key_val,value,date_value) values"
                + "(?,'Treatment',?,NULL),(?,'Treatment',?,NULL),(?,'Start Date','YYYY','2001-07-23')",
                patient, "First " + index, patient, "Second " + index, patient);
    }

    private int definition(String code) {
        var found = sql.queryForList("select issue_id from issue where code=? and type='system'", Integer.class, code);
        if (!found.isEmpty()) {
            assertThat(found).hasSize(1);
            return found.getFirst();
        }
        sql.update("insert into issue(code,description,role,type,sortOrderId) values(?,?,'all','system',0)", code, "FAKE " + code);
        int id = sql.queryForObject("select issue_id from issue where code=? and type='system'", Integer.class, code);
        fixtureIssues.add(id);
        return id;
    }

    /** A Merge Records link, as the Merge Records page writes it. */
    private void link(int merged, int into, int deleted) {
        sql.update(
                "insert into demographic_merged(demographic_no,merged_to,deleted,lastUpdateUser,"
                        + "lastUpdateDate) values(?,?,?,?,'2025-03-01')",
                patient(merged),
                patient(into),
                deleted,
                PROVIDER);
    }

    /** The privilege row the Merge Records page adds for a merged chart. */
    private void mergePrivilege(int merged) {
        sql.update(
                "insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)"
                        + " values('_all',?,'|or|',0,'0')",
                "_eChart$" + patient(merged));
    }

    private void unlink(int index) {
        int patient = patient(index);
        sql.update(
                "delete from demographic_merged where demographic_no=? or merged_to=?",
                patient,
                patient);
        sql.update(
                "delete from secObjPrivilege where objectName in (?,?)",
                "_eChart$" + patient,
                "_demographic$" + patient);
    }

    /** A chart's rows this test or its imports wrote. */
    private void eraseChart(int patient) {
        sql.update("delete from log where demographic_no=?", patient);
        sql.update("delete from casemgmt_note_ext where note_id in (select note_id from casemgmt_note where demographic_no=?)", patient);
        sql.update("delete from casemgmt_issue_notes where id in (select id from casemgmt_issue where demographic_no=?)", patient);
        sql.update("delete from casemgmt_issue where demographic_no=?", patient);
        sql.update("delete from casemgmt_note where demographic_no=?", patient);
        sql.update(
                "delete from document where document_no in (select document_no from ctl_document"
                        + " where module='demographic' and module_id=?)",
                patient);
        sql.update("delete from ctl_document where module='demographic' and module_id=?", patient);
        sql.update("delete from appointment where demographic_no=?", patient);
        sql.update("delete from allergies where demographic_no=?", patient);
        sql.update("delete from admission where client_id=?", patient);
        sql.update("delete from demographicExt where demographic_no=?", patient);
        sql.update("delete from demographiccust where demographic_no=?", patient);
        sql.update("delete from demographic where demographic_no=?", patient);
    }

    /**
     * Every ledger row of this test's import runs. The import's own tables refer to each other,
     * so they are removed on one plain connection with foreign keys unchecked; the application's
     * guarded connections refuse that statement, as they should.
     */
    private void eraseImports() throws java.sql.SQLException {
        if (importRuns.isEmpty()) return;
        try (var connection =
                java.sql.DriverManager.getConnection(
                        jdbc.getProperty("jdbcUrl"),
                        jdbc.getProperty("jdbcUser"),
                        jdbc.getProperty("jdbcPassword"))) {
            var tables = new ArrayList<String>();
            try (var query =
                            connection.prepareStatement(
                                    "select table_name from information_schema.columns where"
                                            + " table_schema=database() and table_name like"
                                            + " 'cds\\_import\\_%' and column_name='run_id'");
                    var rows = query.executeQuery()) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
            }
            try {
                for (String table : tables) {
                    assertThat(table).matches("[a-z_]+");
                    for (String importRun : importRuns)
                        try (var delete =
                                connection.prepareStatement(
                                        "delete from " + table + " where run_id=?")) {
                            delete.setString(1, importRun);
                            delete.executeUpdate();
                        }
                }
                for (String importRun : importRuns)
                    try (var delete =
                            connection.prepareStatement("delete from cds_import_run where id=?")) {
                        delete.setString(1, importRun);
                        delete.executeUpdate();
                    }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
        }
    }

    private int patient(int index) {
        return patients.get(index);
    }

    private String filename(int index) {
        return "FAKE-merged-" + token + "-" + index + ".pdf";
    }

    /** A registered recipient, sender and audit plan, each pinned by the policy manifest. */
    private void policy(Path root) throws Exception {
        directory(root.resolve("recipients"));
        directory(root.resolve("certificates"));
        directory(root.resolve("audit"));
        var sender =
                json(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "namespace", "owned-merged-fixture",
                                "physicianGroup", "FAKE Merged Clinic",
                                "groupContactInformation", "fixture@example.test"));
        var receiver =
                json(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "id", "receiver",
                                "certificateId", "receiver-key",
                                "certificateSha256", recipient.fingerprint,
                                "profiles", List.of("omd-5.2")));
        byte[] certificate = recipient.certificate.getEncoded();
        var audit =
                json(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "id", "approved",
                                "description", "FAKE recipient has approved audit access",
                                "recipientIds", List.of("receiver"),
                                "providerNos", List.of(PROVIDER),
                                "profiles", List.of("omd-5.2")));
        protectedFile(root.resolve("sender.json"), sender);
        protectedFile(root.resolve("recipients/receiver.json"), receiver);
        protectedFile(root.resolve("certificates/receiver-key.der"), certificate);
        protectedFile(root.resolve("audit/approved.json"), audit);
        protectedFile(
                root.resolve("policy.json"),
                json(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "senderSha256", sha256(sender),
                                "recipients", Map.of("receiver", sha256(receiver)),
                                "certificates", Map.of("receiver-key", sha256(certificate)),
                                "auditPlans", Map.of("approved", sha256(audit)))));
    }

    private static byte[] json(Object value) throws IOException {
        return JSON.writeValueAsBytes(value);
    }

    private static Path directory(Path path) throws IOException {
        Files.createDirectories(path);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }

    private static Path protectedFile(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
