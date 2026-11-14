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
import io.github.carlos_emr.carlos.demographic.cdsexport.cli.CdsExportCliSpringConfiguration;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipInputStream;
import javax.sql.DataSource;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A stopped export resumed through the command-line service graph, against the full Flyway
 * schema in an owned MariaDB database, with real protected staging, policy, recipient envelope,
 * document files, privileges and audit.
 *
 * <p>Six synthetic charts, each with one stored document file, are exported with Demographics and
 * Reports. The first attempt finishes four charts, holds one whose file is missing, and stops at
 * the last, whose file the document storage policy does not trust. Between attempts one chart's
 * demographic row changes, another's document file changes and the held chart's file appears. The
 * resumed attempt adopts the two unchanged charts, builds the other three again and stops at the
 * same untrusted file. Cancelling the stopped job erases what it kept; the next attempt builds
 * every chart fresh, and its charts are byte for byte those the resumed attempt held. The last
 * attempt adopts them and publishes, and the decrypted package carries exactly those bytes.
 *
 * <p>Chart XML and attachment names are keyed by the job, so only attempts of one job can be
 * compared byte for byte; the package's ReadMe and event log carry the attempt's own time and are
 * not compared.
 */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportResumeMariaDbIntegrationTest {
    private static final String OPERATOR = "resume-fixture";
    private static final String PROVIDER = "989871";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** The six charts, in cohort order. */
    private static final int UNCHANGED = 0, CHANGED_ROW = 1, CHANGED_FILE = 2, HELD = 3,
            UNCHANGED_LATER = 4, UNTRUSTED = 5;

    private final List<Integer> patients = new ArrayList<>();
    private final String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private Path base, documents, storeRoot;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private CdsExportRecipientFixture recipient;
    private TimeZone previousZone;
    private String run;

    @BeforeEach
    void ownedDatabaseStorageAndPolicy() throws Exception {
        var jdbc = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            jdbc.load(input);
        }
        String url = jdbc.getProperty("jdbcUrl", "");
        assertThat(url).matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?");
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        // Protected staging refuses an ancestor another user could write to, which a shared
        // temporary directory is: everything lives below the build directory.
        base = Path.of("target", "cds-export-resume-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(base);
        Files.setPosixFilePermissions(base, PosixFilePermissions.fromString("rwx------"));
        storeRoot = directory(base.resolve("store"));
        var keys = directory(base.resolve("keys"));
        var policy = directory(base.resolve("policy"));
        documents = directory(base.resolve("documents"));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        protectedFile(keys.resolve("primary.key"), key);
        recipient = new CdsExportRecipientFixture(Instant.now());
        policy(policy);
        var settings = new Properties();
        int slash = url.indexOf('/', url.indexOf("://") + 3);
        settings.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        settings.setProperty("db_uri", url.substring(0, slash + 1));
        settings.setProperty("db_name", url.substring(slash + 1));
        settings.setProperty("db_username", jdbc.getProperty("jdbcUser"));
        settings.setProperty("db_password", jdbc.getProperty("jdbcPassword"));
        settings.setProperty("billregion", "ON");
        settings.setProperty("cds.export.enabled", "true");
        settings.setProperty("cds.export.worker.enabled", "false");
        settings.setProperty("cds.import.worker.enabled", "false");
        settings.setProperty("cds.export.operator." + OPERATOR + ".providers", PROVIDER);
        settings.setProperty("cds.export.clinicalZone", "America/Toronto");
        settings.setProperty("cds.export.store.active", "primary");
        settings.setProperty("cds.export.store.primary.root", storeRoot.toString());
        settings.setProperty("cds.export.masterKey.active", "primary");
        settings.setProperty("cds.export.masterKeyRoot", keys.toString());
        settings.setProperty("cds.export.minimumFreeBytes", "0");
        settings.setProperty("cds.export.policyRoot", policy.toString());
        settings.setProperty("DOCUMENT_DIR", documents.toString());
        // The command-line runtime runs in the clinical zone.
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("cds-export-cli");
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new PropertiesPropertySource("owned-resume-fixture", settings));
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
    void eraseOwnedRowsAndFiles() throws IOException {
        try {
            if (sql != null) {
                if (run != null) {
                    sql.update("delete from cds_export_held_patient where run_id=?", run);
                    sql.update("delete from cds_export_event where run_id=?", run);
                    sql.update("delete from cds_export_attempt where run_id=?", run);
                    sql.update("delete from cds_export_run where id=?", run);
                }
                for (int patient : patients) {
                    sql.update("delete from log where demographic_no=?", patient);
                    sql.update("delete from ctl_document where document_no=?", patient);
                    sql.update("delete from document where document_no=?", patient);
                    sql.update("delete from demographic where demographic_no=?", patient);
                }
                sql.update("delete from secObjPrivilege where roleUserGroup=?", PROVIDER);
                sql.update("delete from provider where provider_no=?", PROVIDER);
            }
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (previousZone != null) TimeZone.setDefault(previousZone);
                if (base != null && Files.exists(base))
                    try (var files = Files.walk(base)) {
                        for (var path : files.sorted(Comparator.reverseOrder()).toList())
                            Files.deleteIfExists(path);
                    }
            }
        }
    }

    @Test
    void shouldAdoptOnlyUnchangedCharts_whenAStoppedExportIsResumed() throws Exception {
        seed();
        var facade = context.getBean(CdsExportService.class);
        var retention = context.getBean(CdsExportRetentionService.class);
        // The privilege reader resolves its DAO through the published Spring bridge; every bean
        // it asks for comes from this context, looked up as the bridge looks it up. Its queries
        // and decisions are real.
        try (var spring =
                mockStatic(
                        SpringUtils.class,
                        invocation -> {
                            var arguments = invocation.getArguments();
                            if (invocation.getMethod().getName().equals("getBean")
                                    && arguments.length == 1) {
                                if (arguments[0] instanceof Class<?> type) return bean(type);
                                if (arguments[0] instanceof String name)
                                    return context.getBean(name);
                            }
                            if (invocation.getMethod().getName().equals("getBeanFactory"))
                                return context.getBeanFactory();
                            return invocation.callRealMethod();
                        })) {
            var command =
                    new CdsExportCommand(
                            "resume-" + token,
                            PROVIDER,
                            null,
                            CdsLogicalProfile.ONTARIO_MD_5_2,
                            CdsExportCommand.Selection.SELECTED_PATIENTS,
                            List.of(),
                            patients,
                            EnumSet.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                            "receiver",
                            "approved");
            run = facade.acceptDelegated(OPERATOR, command).id();

            // Attempt 1 finishes four charts, holds the chart whose file is missing and stops at
            // the untrusted file. What it finished is kept for seven days.
            var first = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(first);
            assertKept(1, 4);
            var kept1 = kept(1);
            assertThat(kept1.charts().keySet())
                    .containsExactly(
                            patient(UNCHANGED),
                            patient(CHANGED_ROW),
                            patient(CHANGED_FILE),
                            patient(UNCHANGED_LATER));
            assertThat(kept1.held())
                    .containsExactly(
                            Map.entry(patient(HELD), "CDS_EXPORT_NATIVE_REPORT_FILE_MISSING"));
            assertThat(events("CHARTS_KEPT")).contains("CDS_EXPORT_CHARTS_KEPT_4");
            // The sweep erases everything else the stopped attempt staged; the charts remain.
            retention.sweep();
            assertThat(attempt(1).get("state")).isEqualTo("KEPT");
            assertThat(kept(1)).isEqualTo(kept1);

            // One chart's row changes, another's document file changes, and the held chart's
            // file appears. The untrusted file stays untrusted.
            sql.update(
                    "update demographic set address='2 FAKE Resume Road',"
                            + " lastUpdateDate='2025-03-04 12:00:00' where demographic_no=?",
                    patient(CHANGED_ROW));
            writeDocument(CHANGED_FILE, 2, "rw-------");
            writeDocument(HELD, 1, "rw-------");

            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            var second = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(second);
            assertKept(2, 5);
            assertThat(attempt(2).get("adopted_charts")).isEqualTo(2L);
            // Attempt 2 decided every chart attempt 1 kept again, so attempt 1 is released.
            assertThat(expired(1)).isTrue();
            assertThat(events("CHECKPOINT_SUPERSEDED")).hasSize(1);
            assertThat(adoptedAudit(2, 1))
                    .containsExactlyInAnyOrder(patient(UNCHANGED), patient(UNCHANGED_LATER));
            var kept2 = kept(2);
            assertThat(kept2.charts().keySet())
                    .containsExactly(
                            patient(UNCHANGED),
                            patient(CHANGED_ROW),
                            patient(CHANGED_FILE),
                            patient(HELD),
                            patient(UNCHANGED_LATER));
            assertThat(kept2.held()).isEmpty();
            // Adopted: the very bytes attempt 1 built.
            for (int unchanged : List.of(UNCHANGED, UNCHANGED_LATER))
                assertThat(kept2.charts().get(patient(unchanged)))
                        .isEqualTo(kept1.charts().get(patient(unchanged)));
            // Built again from the changed row, and from the changed file.
            var row = kept2.charts().get(patient(CHANGED_ROW));
            assertThat(row.xmlSha256())
                    .isNotEqualTo(kept1.charts().get(patient(CHANGED_ROW)).xmlSha256());
            assertThat(row.xml()).contains("2 FAKE Resume Road");
            assertThat(kept1.charts().get(patient(CHANGED_ROW)).xml())
                    .doesNotContain("2 FAKE Resume Road");
            assertThat(kept2.charts().get(patient(CHANGED_FILE)).attachments().values())
                    .containsExactly(sha256(document(CHANGED_FILE, 2)));
            assertThat(kept1.charts().get(patient(CHANGED_FILE)).attachments().values())
                    .containsExactly(sha256(document(CHANGED_FILE, 1)));
            // Held before: evaluated again and built, never adopted.
            assertThat(kept2.charts().get(patient(HELD)).attachments().values())
                    .containsExactly(sha256(document(HELD, 1)));

            // Cancelling the stopped job erases what it kept at once.
            facade.cancelDelegated(OPERATOR, PROVIDER, run);
            assertThat(expired(2)).isTrue();
            retention.sweep();
            assertThat(events("CHARTS_ERASED"))
                    .contains("CDS_EXPORT_CHARTS_ERASED_4", "CDS_EXPORT_CHARTS_ERASED_5");
            assertThat(attempt(1).get("state")).isEqualTo("PURGED");
            assertThat(attempt(2).get("state")).isEqualTo("PURGED");
            for (long epoch : List.of(1L, 2L))
                assertThat(
                                !Files.exists(storeRoot.resolve(run).resolve(Long.toString(epoch)))
                                        || isEmpty(
                                                storeRoot
                                                        .resolve(run)
                                                        .resolve(Long.toString(epoch))))
                        .isTrue();

            // Nothing is kept now, so the next attempt builds every chart fresh: a fresh attempt
            // of the same job makes exactly the charts the resumed attempt held.
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            var third = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertStopped(third);
            assertKept(3, 5);
            assertThat(attempt(3).get("adopted_charts")).isEqualTo(0L);
            assertThat(adoptedAudit(3, 1)).isEmpty();
            assertThat(adoptedAudit(3, 2)).isEmpty();
            var kept3 = kept(3);
            assertThat(kept3).isEqualTo(kept2);

            // The storage is fixed: the last attempt adopts all five and publishes.
            Files.setPosixFilePermissions(
                    documents.resolve(filename(UNTRUSTED)),
                    PosixFilePermissions.fromString("rw-------"));
            facade.resumeDelegated(OPERATOR, PROVIDER, run);
            var last = facade.executeDelegated(OPERATOR, PROVIDER, run);
            assertThat(last.state()).isEqualTo(CdsExportState.READY);
            assertThat(last.totalPatients()).isEqualTo(6L);
            assertThat(last.heldBack()).isFalse();
            assertThat(attempt(4).get("adopted_charts")).isEqualTo(5L);
            assertThat(events("CHARTS_ADOPTED")).containsExactly("CDS_EXPORT_CHARTS_ADOPTED_5");
            assertThat(adoptedAudit(4, 3))
                    .containsExactlyInAnyOrder(
                            patient(UNCHANGED),
                            patient(CHANGED_ROW),
                            patient(CHANGED_FILE),
                            patient(HELD),
                            patient(UNCHANGED_LATER));
            // Publication releases what attempt 3 kept.
            assertThat(expired(3)).isTrue();

            var zip = decrypt(download(facade));
            var entries = entries(zip);
            for (var chart : kept3.charts().values()) {
                assertThat(entry(entries, chart.relative())).isEqualTo(chart.xmlSha256());
                String folder = chart.relative().substring(0, chart.relative().lastIndexOf('/') + 1);
                for (var attachment : chart.attachments().entrySet())
                    assertThat(entry(entries, folder + attachment.getKey()))
                            .isEqualTo(attachment.getValue());
            }
            assertThat(entries.keySet().stream().filter(name -> name.endsWith(".xml")))
                    .hasSize(6);
            assertThat(
                            entries.keySet().stream()
                                    .filter(name -> name.contains("/Reports/"))
                                    .toList())
                    .hasSize(6);
            assertThat(entries.keySet())
                    .anyMatch(name -> name.endsWith("ReadMe.txt"))
                    .anyMatch(name -> name.endsWith("ExportEventLog.csv"));
        }
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

    /** One finished chart as an attempt kept it: its archive path, XML and attachments. */
    private record KeptChart(
            String relative, String xmlSha256, String xml, Map<String, String> attachments) {}

    /** What one attempt kept: its finished charts and held patients, in cohort order. */
    private record Kept(Map<Integer, KeptChart> charts, Map<Integer, String> held) {}

    private Kept kept(long epoch) throws Exception {
        var row = attempt(epoch);
        var pointer =
                new CdsExportChartCheckpointDto(
                        (String) row.get("checkpoint_member"),
                        (String) row.get("checkpoint_digest"),
                        ((Number) row.get("checkpoint_bytes")).longValue(),
                        ((Number) row.get("checkpoint_charts")).longValue());
        var stores = context.getBean(CdsExportStagingFactory.class);
        var binding = context.getBean(CdsExportLedgerService.class).document(run).storage();
        var store = stores.openExisting(run, epoch, binding, () -> {});
        var chain = CdsExportChartCheckpointReader.open(store, run, epoch, pointer, () -> {});
        var charts = new LinkedHashMap<Integer, KeptChart>();
        var held = new LinkedHashMap<Integer, String>();
        try (var cursor = chain.cursor()) {
            CdsExportChartCheckpointReader.Entry entry;
            while ((entry = cursor.next()) != null) {
                if (!entry.chartEntry()) {
                    held.put(entry.patient(), entry.code());
                    continue;
                }
                var chart =
                        CdsExportClinicalPackageService.checkpointChart(
                                entry.chart(), entry.patient());
                byte[] xml = read(store, chart.xml());
                var attachments = new TreeMap<String, String>();
                for (var attachment : chart.attachments())
                    attachments.put(
                            attachment.relativePath(), sha256(read(store, attachment.file())));
                charts.put(
                        entry.patient(),
                        new KeptChart(
                                chart.relative(),
                                sha256(xml),
                                new String(xml, StandardCharsets.UTF_8),
                                Map.copyOf(attachments)));
            }
        }
        return new Kept(charts, held);
    }

    private static byte[] read(
            CdsExportStagingService store,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto member)
            throws IOException {
        try (InputStream input = store.open(member)) {
            return input.readAllBytes();
        }
    }

    private Map<String, Object> attempt(long epoch) {
        return sql.queryForMap(
                "select state, checkpoint_member, checkpoint_digest, checkpoint_bytes,"
                        + " checkpoint_charts, adopted_charts, expires_at, updated_at"
                        + " from cds_export_attempt where run_id=? and epoch=?",
                run,
                epoch);
    }

    private boolean expired(long epoch) {
        return Boolean.TRUE.equals(
                sql.queryForObject(
                        "select expires_at <= CURRENT_TIMESTAMP(6) from cds_export_attempt"
                                + " where run_id=? and epoch=?",
                        Boolean.class,
                        run,
                        epoch));
    }

    /** The attempt kept {@code charts} finished charts until seven days after it stopped. */
    private void assertKept(long epoch, long charts) {
        var row = attempt(epoch);
        assertThat(row.get("state")).isIn("KEEPING", "KEPT");
        assertThat(((Number) row.get("checkpoint_charts")).longValue()).isEqualTo(charts);
        long kept =
                sql.queryForObject(
                        "select timestampdiff(SECOND, updated_at, expires_at) from"
                                + " cds_export_attempt where run_id=? and epoch=?",
                        Long.class,
                        run,
                        epoch);
        assertThat(Duration.ofSeconds(kept))
                .isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7).plusMinutes(1));
    }

    private static void assertStopped(CdsExportJobDto job) {
        // The storage policy refuses the untrusted file: the job stops for review, resumable.
        assertThat(job.state()).isIn(CdsExportState.REVIEW_REQUIRED, CdsExportState.FAILED);
        assertThat(job.state()).isNotEqualTo(CdsExportState.READY);
    }

    private List<String> events(String type) {
        return sql.queryForList(
                "select coalesce(safe_code,'') from cds_export_event where run_id=? and"
                        + " event_type=? order by id",
                String.class,
                run,
                type);
    }

    /** The charts audited as adopted by attempt {@code epoch} from attempt {@code source}. */
    private Set<Integer> adoptedAudit(long epoch, long source) {
        return new TreeSet<>(
                sql.queryForList(
                        "select demographic_no from log where data like ?",
                        Integer.class,
                        "CDS_EXPORT_CHART_ADOPTED run="
                                + run
                                + " epoch="
                                + epoch
                                + " sourceEpoch="
                                + source
                                + " %"));
    }

    private byte[] download(CdsExportService facade) throws IOException {
        var actor = facade.downloadActorDelegated(OPERATOR, PROVIDER, run);
        var output = new ByteArrayOutputStream();
        try (var download =
                context.getBean(CdsExportArtifactService.class).open(actor, run, "package")) {
            download.writeTo(output);
            download.complete();
        }
        return output.toByteArray();
    }

    private byte[] decrypt(byte[] envelope) throws Exception {
        var parsed = new CMSAuthEnvelopedData(envelope);
        assertThat(parsed.getEncryptionAlgOID()).isEqualTo(CMSAlgorithm.AES256_GCM.getId());
        var info = parsed.getRecipientInfos().getRecipients().iterator().next();
        return info.getContent(
                new JceKeyTransAuthEnvelopedRecipient(recipient.keys.getPrivate())
                        .setProvider(new BouncyCastleProvider()));
    }

    /** Every entry of the package, by name, as the SHA-256 of its bytes. */
    private static Map<String, String> entries(byte[] zip) throws Exception {
        var result = new TreeMap<String, String>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null)
                if (!entry.isDirectory()) result.put(entry.getName(), sha256(input.readAllBytes()));
        }
        return result;
    }

    /** The digest of the one entry whose name ends with {@code relative}. */
    private static String entry(Map<String, String> entries, String relative) {
        var matches =
                entries.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getKey().equals(relative)
                                                || entry.getKey().endsWith("/" + relative))
                        .toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst().getValue();
    }

    private void seed() throws IOException {
        sql.update(
                "insert into provider(provider_no,first_name,last_name,provider_type,specialty,sex,"
                        + "status,lastUpdateDate) values(?,'Resume','FAKE-Fixture','doctor','','U',"
                        + "'1',CURRENT_TIMESTAMP)",
                PROVIDER);
        // Demographics and Reports: the chart, its documents and eForms, the preferred pharmacy
        // read with the demographics, and the export right itself.
        sql.update(
                "insert into secObjPrivilege(roleUserGroup,objectName,privilege,priority) values"
                        + "(?,'_demographic','|r|',10),(?,'_edoc','|r|',10),(?,'_eform','|r|',10),"
                        + "(?,'_rx','|r|',10),(?,'_cds_export','|w|',10)",
                PROVIDER,
                PROVIDER,
                PROVIDER,
                PROVIDER,
                PROVIDER);
        int first = 420_000_000 + ThreadLocalRandom.current().nextInt(1_000_000) * 10;
        for (int index = 0; index < 6; index++) {
            int patient = first + index;
            patients.add(patient);
            sql.update(
                    "insert into demographic(demographic_no,last_name,first_name,year_of_birth,"
                            + "month_of_birth,date_of_birth,sex,patient_status,address,city,province,"
                            + "postal,phone,provider_no,lastUpdateDate) values(?,?,'Synthetic','1971',"
                            + "'03','04','F','AC','1 FAKE Resume Road','Ottawa','ON','K1A0B1',"
                            + "'613-555-0100',?,'2025-01-02 12:00:00')",
                    patient,
                    "FAKE-RESUME-" + token.toUpperCase(java.util.Locale.ROOT) + "-" + index,
                    PROVIDER);
            sql.update(
                    "insert into document(document_no,doctype,docdesc,docfilename,doccreator,"
                            + "responsible,source,program_id,updatedatetime,status,contenttype,"
                            + "contentdatetime,public1,observationdate,number_of_pages,"
                            + "restrictToProgram,abnormal) values(?,'lab',?,?,?,?,'FAKE Resume Lab',"
                            + "NULL,'2025-02-03 12:00:00','A','application/pdf',"
                            + "'2025-02-03 12:00:00',0,'2025-02-03',1,0,0)",
                    patient,
                    "FAKE resume report " + index,
                    filename(index),
                    PROVIDER,
                    PROVIDER);
            sql.update(
                    "insert into ctl_document(module,module_id,document_no,status)"
                            + " values('demographic',?,?,'A')",
                    patient,
                    patient);
            // The held chart's file is missing until after the first attempt; the last chart's
            // file is writable by its group, which the document storage policy does not trust.
            if (index == HELD) continue;
            writeDocument(index, 1, index == UNTRUSTED ? "rw-rw----" : "rw-------");
        }
    }

    private int patient(int index) {
        return patients.get(index);
    }

    private String filename(int index) {
        return "FAKE-resume-" + token + "-" + index + ".pdf";
    }

    /** A small synthetic PDF: the export reads its signature, never its pages. */
    private byte[] document(int index, int version) {
        return ("%PDF-1.4\n% FAKE resume fixture report "
                        + index
                        + " version "
                        + version
                        + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private void writeDocument(int index, int version, String permissions) throws IOException {
        var file = documents.resolve(filename(index));
        Files.write(file, document(index, version));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
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
                                "namespace", "owned-resume-fixture",
                                "physicianGroup", "FAKE Resume Clinic",
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

    private static void protectedFile(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.findAny().isEmpty();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
