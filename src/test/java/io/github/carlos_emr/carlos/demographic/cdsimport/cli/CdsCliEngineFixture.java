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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicitly disposable installed-engine fixture; never called by production import code. */
public final class CdsCliEngineFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CONFIG = Path.of("/etc/carlos-emr/carlos.properties");
    private static final String PROVIDER = "999998";

    public static void main(String[] args) {
        try {
            if (args.length < 3 || !Files.isRegularFile(Path.of("/.dockerenv"))
                    || ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).intValue() != 0) {
                throw new IllegalArgumentException("Disposable root container required");
            }
            Path credentials = Path.of(args[1]).toAbsolutePath();
            if (!Files.isRegularFile(credentials, LinkOption.NOFOLLOW_LINKS)
                    || (((int) Files.getAttribute(credentials, "unix:mode", LinkOption.NOFOLLOW_LINKS)) & 0077) != 0) {
                throw new IllegalArgumentException("Protected synthetic JDBC configuration required");
            }
            Properties jdbc = new Properties();
            try (InputStream input = Files.newInputStream(credentials)) { jdbc.load(input); }
            String url = jdbc.getProperty("jdbcUrl", "").replace("jdbc:mariadb:", "jdbc:mysql:");
            if (!url.matches("jdbc:mysql://[^/]+/(?:carlos_cds_test|cds_full_test|cds_cli_test)(?:\\?.*)?")) {
                throw new IllegalArgumentException("Explicit disposable database required");
            }
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"))) {
                String actual;
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT DATABASE()")) {
                    if (!result.next()) throw new IllegalStateException("Database unavailable"); actual = result.getString(1);
                }
                if (!List.of("carlos_cds_test", "cds_full_test", "cds_cli_test").contains(actual)) throw new IllegalStateException("Non-test database");
                Object result = switch (args[0]) {
                    case "prepare" -> prepare(connection, jdbc, url, Path.of(args[2]));
                    case "inspect" -> inspect(connection, args[2]);
                    case "control" -> {
                        if (args.length != 4) throw new IllegalArgumentException("Control required");
                        yield control(connection, Path.of(args[2]), args[3]);
                    }
                    default -> throw new IllegalArgumentException("Unknown fixture operation");
                };
                System.out.println(JSON.writeValueAsString(result));
            }
        } catch (Exception failure) {
            String location = "unknown";
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (frame.getClassName().equals(CdsCliEngineFixture.class.getName())) {
                    location = frame.getMethodName() + ":" + frame.getLineNumber(); break;
                }
            }
            String sql = failure instanceof java.sql.SQLException database
                    ? ":sqlState=" + database.getSQLState() + ":vendorCode=" + database.getErrorCode() : "";
            // Operational fixture evidence only: never print exception messages, SQL or patient/config values.
            System.err.println("CDS_SYNTHETIC_FIXTURE_FAILED:" + failure.getClass().getSimpleName() + ":" + location + sql);
            System.exit(1);
        }
    }

    private static Map<String, Object> prepare(Connection connection, Properties jdbc, String url, Path root) throws Exception {
        if (!root.isAbsolute() || Files.exists(root, LinkOption.NOFOLLOW_LINKS) || Files.exists(CONFIG, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Existing fixture or production configuration is never replaced");
        }
        int facility = baseline(connection);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String namespace = "cli-e2e-" + token, alpha = "A" + token.substring(0, 5);
        List<String> students = List.of("S" + token.substring(0, 4) + "1", "S" + token.substring(0, 4) + "2", "S" + token.substring(0, 4) + "3");
        int course = 1_000_000_000 + new java.security.SecureRandom().nextInt(400_000_000);
        List<Integer> programs = List.of(course, course + 1, course + 2);
        connection.setAutoCommit(false);
        try {
            seedProvider(connection, alpha, "1");
            update(connection, "INSERT INTO secUserRole(provider_no,role_name,activeyn,lastUpdateDate) VALUES (?,'doctor',1,NOW())", alpha);
            for (int i = 0; i < students.size(); i++) seedProvider(connection, students.get(i), i == 2 ? "0" : "1");
            update(connection, "INSERT INTO secRole(role_name,description) SELECT 'student','Synthetic student' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM secRole WHERE role_name='student')");
            String educatorRole = "CDS E2E " + token;
            update(connection, "INSERT INTO secRole(role_name,description) VALUES (?,'Owned synthetic educator')", educatorRole);
            long studentRole = count(connection, "SELECT role_no FROM secRole WHERE role_name='student'");
            long educator = count(connection, "SELECT role_no FROM secRole WHERE role_name=?", educatorRole);
            seedProgram(connection, programs.get(0), facility, "CDS E2E " + token);
            seedProgram(connection, programs.get(1), facility, "program" + students.get(0));
            seedProgram(connection, programs.get(2), facility, "program" + students.get(1));
            update(connection, "INSERT INTO program_provider(program_id,provider_no,role_id) VALUES (?,?,?)", course, PROVIDER, educator);
            for (String student : students) update(connection, "INSERT INTO program_provider(program_id,provider_no,role_id) VALUES (?,?,?)", course, student, studentRole);
            if (count(connection, "SELECT COUNT(*) FROM provider_facility WHERE provider_no=? AND facility_id=?", PROVIDER, facility) == 0) {
                update(connection, "INSERT INTO provider_facility(provider_no,facility_id) VALUES (?,?)", PROVIDER, facility);
            }
            connection.commit();
        } catch (Exception failure) { connection.rollback(); throw failure; }
        finally { connection.setAutoCommit(true); }

        directory(root, false); Path source = root.resolve("source"); directory(source, false);
        Path state = root.resolve("state"); directory(state, true);
        directory(state.resolve("runs"), true); directory(state.resolve("uploads"), true);
        directory(root.resolve("documents"), true);
        String baseline = resource("/demographic/cds-import-summary.xml");
        String full = resource("/demographic/cds-import-all-categories.xml");
        String minimal = baseline.substring(0, baseline.indexOf("</Demographics>") + "</Demographics>".length()) + "</PatientRecord></OmdCds>";
        Map<String, String> sources = new LinkedHashMap<>();
        for (String label : List.of("ordinary", "dryrun", "course", "allcategory", "allcategoryAlpha", "cancel")) {
            String text = switch (label) { case "allcategory", "allcategoryAlpha" -> full; case "course" -> baseline; default -> minimal; };
            text = distinct(text, token, label);
            if (label.equals("cancel")) {
                StringBuilder notes = new StringBuilder();
                for (int i = 0; i < 256; i++) notes.append("<ClinicalNotes><MyClinicalNotesContent>Synthetic cancellation occurrence ").append(i).append("</MyClinicalNotesContent></ClinicalNotes>");
                text = text.replace("</PatientRecord>", notes + "</PatientRecord>");
            }
            Path path = source.resolve(label + ".xml"); writeSource(path, text); sources.put(label, path.toString());
        }
        Path batch = source.resolve("batch"); directory(batch, false);
        List<Path> batchFiles = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Path path = batch.resolve("patient" + i + ".xml"); writeSource(path, distinct(minimal, token, "batch" + i)); batchFiles.add(path);
        }
        sources.put("directory", batch.toString());
        Path archive = source.resolve("patients.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW))) {
            for (int i = 0; i < batchFiles.size(); i++) {
                output.putNextEntry(new ZipEntry(batchFiles.get(i).getFileName().toString()));
                output.write(distinct(minimal, token, "archive" + (i + 1)).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        rootReadable(archive, false); sources.put("archive", archive.toString());
        Path metadataFile = root.resolve("fixture.json");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("protocol", 1); metadata.put("fixtureRoot", root.toString()); metadata.put("provider", PROVIDER);
        metadata.put("alphanumericProvider", alpha); metadata.put("namespace", namespace); metadata.put("profile", "pcon-2024-02");
        metadata.put("courseId", course); metadata.put("facilityId", facility); metadata.put("students", students); metadata.put("programs", programs);
        metadata.put("metadataFile", metadataFile.toString()); metadata.put("sources", sources);
        Files.writeString(metadataFile, JSON.writeValueAsString(metadata), StandardOpenOption.CREATE_NEW);
        Files.setPosixFilePermissions(metadataFile, PosixFilePermissions.fromString("rw-------"));
        Properties config = new Properties(); int slash = url.indexOf('/', "jdbc:mysql://".length());
        config.setProperty("db_uri", url.substring(0, slash + 1)); config.setProperty("db_name", url.substring(slash + 1));
        config.setProperty("db_driver", "com.mysql.cj.jdbc.Driver"); config.setProperty("db_username", jdbc.getProperty("jdbcUser")); config.setProperty("db_password", jdbc.getProperty("jdbcPassword"));
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect"); config.setProperty("billregion", "ON");
        config.setProperty("db_default_use_information_schema", "false");
        for (String key : List.of("enabled", "maintenanceGuardEnabled", "maintenanceMode")) config.setProperty("cds.import." + key, "true");
        config.setProperty("carlos.flyway.onBoot", "validate"); config.setProperty("cds.import.clinicalZone", "America/Toronto"); config.setProperty("cds.import.cliHeapMiB", "512");
        config.setProperty("cds.import.allowedRoot", source.toString()); config.setProperty("cds.import.stagingRoot", state.resolve("runs").toString()); config.setProperty("cds.import.uploadRoot", state.resolve("uploads").toString()); config.setProperty("DOCUMENT_DIR", root.resolve("documents").toString());
        config.setProperty("cds.import.sourceNamespaces", namespace); config.setProperty("cds.import.source." + namespace + ".profiles", "pcon-2024-02"); config.setProperty("cds.import.source." + namespace + ".synthetic", "true");
        config.setProperty("cds.import.operator.root.providers", PROVIDER + "," + alpha); config.setProperty("OSCAR_LEARNING", "yes"); config.setProperty("cds.import.course." + course + ".educators", PROVIDER);
        config.setProperty("cds.test.fixtureMetadata", metadataFile.toString());
        if (!Files.exists(CONFIG.getParent(), LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(CONFIG.getParent());
        rootReadable(CONFIG.getParent(), true);
        try (var output = Files.newOutputStream(CONFIG, StandardOpenOption.CREATE_NEW)) { config.store(output, "Disposable CDS installed-engine fixture"); }
        rootReadable(CONFIG, false);
        return metadata;
    }

    private static Object inspect(Connection connection, String run) throws Exception {
        if (run.equals("fixture")) {
            Map<String, Object> totals = new LinkedHashMap<>();
            totals.put("totalNativePatients", scalar(connection, "SELECT COUNT(*) FROM demographic"));
            totals.put("totalNativeDrugs", scalar(connection, "SELECT COUNT(*) FROM drugs"));
            totals.put("totalNativeAllergies", scalar(connection, "SELECT COUNT(*) FROM allergies"));
            totals.put("totalNativePreventions", scalar(connection, "SELECT COUNT(*) FROM preventions"));
            totals.put("totalNativeAppointments", scalar(connection, "SELECT COUNT(*) FROM appointment"));
            totals.put("totalNativeMeasurements", scalar(connection, "SELECT COUNT(*) FROM measurements"));
            totals.put("totalNativeDocuments", scalar(connection, "SELECT COUNT(*) FROM document"));
            totals.put("totalNativeNotes", scalar(connection, "SELECT COUNT(*) FROM casemgmt_note"));
            totals.put("totalNativeContacts", scalar(connection, "SELECT COUNT(*) FROM Contact"));
            totals.put("totalNativeContactLinks", scalar(connection, "SELECT COUNT(*) FROM DemographicContact"));
            totals.put("totalNativePharmacyLinks", scalar(connection, "SELECT COUNT(*) FROM demographicPharmacy"));
            totals.put("totalNativeLaboratoryResults", scalar(connection, "SELECT COUNT(*) FROM cds_laboratory_result"));
            totals.put("totalNativeHL7Messages", scalar(connection, "SELECT COUNT(*) FROM hl7TextMessage"));
            totals.put("totalNativeHL7Info", scalar(connection, "SELECT COUNT(*) FROM hl7TextInfo"));
            totals.put("totalNativeLabRouting", scalar(connection, "SELECT COUNT(*) FROM patientLabRouting"));
            totals.put("totalNativeAdmissions", scalar(connection, "SELECT COUNT(*) FROM admission"));
            return totals;
        }
        UUID.fromString(run);
        long targets = count(connection, "SELECT COUNT(DISTINCT d.demographic_no) FROM cds_import_patient p JOIN demographic d ON d.demographic_no=p.target_demographic_no WHERE p.run_id=?", run);
        long holds = count(connection, "SELECT COUNT(*) FROM cds_import_patient WHERE run_id=? AND held=TRUE", run);
        long checkpoints = count(connection, "SELECT COUNT(*) FROM cds_import_unit u JOIN cds_import_checkpoint c ON c.id=u.unit_key WHERE u.run_id=?", run);
        long preventionCount = count(connection, "SELECT COUNT(*) FROM preventions p WHERE EXISTS(SELECT 1 FROM cds_import_patient i WHERE i.run_id=? AND i.target_demographic_no=p.demographic_no)", run);
        long creatorMismatch = count(connection, "SELECT COUNT(*) FROM preventions p JOIN cds_import_run r ON r.id=? WHERE EXISTS(SELECT 1 FROM cds_import_patient i WHERE i.run_id=r.id AND i.target_demographic_no=p.demographic_no) AND (p.creator IS NULL OR CAST(p.creator AS CHAR)<>r.provider_no)", run);
        long shifted = count(connection, "SELECT COUNT(*) FROM cds_import_patient p JOIN demographic d ON d.demographic_no=p.target_demographic_no WHERE p.run_id=? AND d.year_of_birth='1971' AND CAST(d.month_of_birth AS UNSIGNED)=3 AND CAST(d.date_of_birth AS UNSIGNED)=11", run);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nativeTargets", targets); result.put("holds", holds); result.put("checkpoints", checkpoints);
        result.put("expectedUnits", count(connection, "SELECT expected_units FROM cds_import_run WHERE id=?", run));
        result.put("cancelRequested", count(connection, "SELECT cancel_requested FROM cds_import_run WHERE id=?", run) == 1);
        result.put("sourcefields", count(connection, "SELECT COUNT(*) FROM cds_import_field WHERE run_id=?", run));
        result.put("admissions", count(connection, "SELECT COUNT(*) FROM admission a WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=a.client_id)", run));
        result.put("preventionCreatorMatchesProvider", preventionCount > 0 && creatorMismatch == 0);
        result.put("shiftedBirthDates", shifted);
        result.put("shiftedMedicationDates", count(connection, "SELECT COUNT(*) FROM drugs d WHERE d.rx_date='2026-01-22' AND d.written_date='2026-01-22' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographic_no)", run));
        result.put("shiftedAllergyDates", count(connection, "SELECT COUNT(*) FROM allergies a WHERE DATE(a.start_date)='2020-05-13' AND DATE(a.entry_date)='2020-05-14' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=a.demographic_no)", run));
        result.put("shiftedNoteDates", count(connection, "SELECT COUNT(*) FROM casemgmt_note n WHERE DATE(n.observation_date)='2026-02-10' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND CAST(p.target_demographic_no AS CHAR)=n.demographic_no)", run));
        result.put("nativeDrugs", count(connection, "SELECT COUNT(*) FROM drugs d WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographic_no)", run));
        result.put("nativeAllergies", count(connection, "SELECT COUNT(*) FROM allergies a WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=a.demographic_no)", run));
        result.put("nativePreventions", preventionCount);
        result.put("nativeYearPreventions", count(connection, "SELECT COUNT(*) FROM preventions p WHERE p.prevention_date IS NOT NULL AND p.refused='0' AND p.never='0' AND p.deleted='0' AND p.provider_no='' AND EXISTS(SELECT 1 FROM cds_import_patient i WHERE i.run_id=? AND i.target_demographic_no=p.demographic_no) AND (SELECT COUNT(*) FROM partial_date t WHERE t.table_name=4 AND t.table_id=p.id AND t.field_name=7)=1 AND EXISTS(SELECT 1 FROM partial_date t WHERE t.table_name=4 AND t.table_id=p.id AND t.field_name=7 AND t.format='YYYY') AND EXISTS(SELECT 1 FROM preventionsExt e WHERE e.prevention_id=p.id AND e.keyval='cdsDatePrecision' AND e.val='YEAR') AND EXISTS(SELECT 1 FROM preventionsExt e WHERE e.prevention_id=p.id AND e.keyval='cdsDateOriginal' AND e.val='2020')", run));
        result.put("nativeAppointments", count(connection, "SELECT COUNT(*) FROM appointment a WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=a.demographic_no)", run));
        result.put("nativeMeasurements", count(connection, "SELECT COUNT(*) FROM measurements m WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=m.demographicNo)", run));
        result.put("nativeDocuments", count(connection, "SELECT COUNT(DISTINCT d.document_no) FROM document d JOIN ctl_document c ON c.document_no=d.document_no AND c.module='demographic' AND c.status='A' WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=c.module_id)", run));
        result.put("nativeUnclockedReports", count(connection, "SELECT COUNT(DISTINCT d.document_no) FROM document d JOIN ctl_document c ON c.document_no=d.document_no AND c.module='demographic' AND c.status='A' WHERE d.cds_content_precision IN ('MONTH','UNKNOWN') AND d.contentdatetime IS NULL AND d.observationdate IS NULL AND d.reviewdatetime IS NULL AND COALESCE(d.reviewer,'')='' AND d.numberofpages=0 AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=c.module_id)", run));
        result.put("nativeMonthReports", count(connection, "SELECT COUNT(DISTINCT d.document_no) FROM document d JOIN ctl_document c ON c.document_no=d.document_no AND c.module='demographic' AND c.status='A' WHERE d.cds_content_precision='MONTH' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=c.module_id)", run));
        result.put("nativeUnknownReports", count(connection, "SELECT COUNT(DISTINCT d.document_no) FROM document d JOIN ctl_document c ON c.document_no=d.document_no AND c.module='demographic' AND c.status='A' WHERE d.cds_content_precision='UNKNOWN' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=c.module_id)", run));
        result.put("nativeProfessionalContacts", count(connection, "SELECT COUNT(DISTINCT c.id) FROM Contact c JOIN DemographicContact d ON d.contactId=CAST(c.id AS CHAR) AND d.type=2 WHERE c.type='ProfessionalContact' AND d.category='professional' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographicNo)", run));
        result.put("nativeFamilyPhysicians", count(connection, "SELECT COUNT(*) FROM Contact c JOIN DemographicContact d ON d.contactId=CAST(c.id AS CHAR) AND d.type=2 WHERE c.type='ProfessionalContact' AND d.category='professional' AND d.role='Family Doctor' AND c.firstName='Family' AND c.lastName='Source clinician' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographicNo)", run));
        result.put("nativeReferringPhysicians", count(connection, "SELECT COUNT(*) FROM Contact c JOIN DemographicContact d ON d.contactId=CAST(c.id AS CHAR) AND d.type=2 WHERE c.type='ProfessionalContact' AND d.category='professional' AND d.role='Referring Doctor' AND c.firstName='Referral' AND c.lastName='Source clinician' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographicNo)", run));
        result.put("nativeDemographicContacts", count(connection, "SELECT COUNT(*) FROM DemographicContact d WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographicNo)", run));
        result.put("nativeUnknownContactConsents", count(connection, "SELECT COUNT(*) FROM DemographicContact d WHERE d.consentToContact IS NULL AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographicNo)", run));
        result.put("nativePreferredPharmacies", count(connection, "SELECT COUNT(*) FROM pharmacyInfo f JOIN demographicPharmacy d ON d.pharmacyID=f.recordID WHERE d.preferredOrder=1 AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographic_no)", run));
        result.put("nativeUnknownActivePharmacyConsents", count(connection, "SELECT COUNT(*) FROM pharmacyInfo f JOIN demographicPharmacy d ON d.pharmacyID=f.recordID WHERE d.preferredOrder=1 AND d.status='1' AND f.status='1' AND d.consentToContact IS NULL AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=d.demographic_no)", run));
        result.put("nativeNotes", count(connection, "SELECT COUNT(*) FROM casemgmt_note n WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=n.demographic_no)", run));
        result.put("nativeNotesWithoutObservationClock", count(connection, "SELECT COUNT(*) FROM casemgmt_note n WHERE n.observation_date IS NULL AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=n.demographic_no)", run));
        result.put("nativeUnsignedAuthorFreeNotes", count(connection, "SELECT COUNT(*) FROM casemgmt_note n WHERE n.signed=0 AND COALESCE(n.provider_no,'')='' AND COALESCE(n.signing_provider_no,'')='' AND EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id=? AND p.target_demographic_no=n.demographic_no)", run));
        result.put("nativeLaboratoryResults", count(connection, "SELECT COUNT(*) FROM cds_laboratory_result WHERE runId=?", run));
        result.put("nativeLaboratoryProjection", count(connection, "SELECT COUNT(*) FROM cds_laboratory_result l JOIN hl7TextMessage m ON m.lab_id=l.nativeLabNo AND m.type='CARLOS-CDS' JOIN hl7TextInfo i ON i.lab_no=m.lab_id AND i.result_status='A' AND i.final_result_count=1 JOIN patientLabRouting p ON p.lab_no=m.lab_id AND p.lab_type='HL7' AND p.demographic_no=l.demographicNo WHERE l.runId=? AND l.abnormalFlag='H' AND l.resultStatus='Final' AND l.collectionPrecision='OFFSET_DATE_TIME' AND l.collectionDate='2025-03-04T10:20:30.125-05:00' AND LOCATE('|H|',FROM_BASE64(m.message))>0 AND LOCATE('|Final|',FROM_BASE64(m.message))>0 AND LOCATE('20250304102030.125-0500',FROM_BASE64(m.message))>0 AND LOCATE('Lab comment ',FROM_BASE64(m.message))>0", run));
        result.put("nativeLaboratoryPrivateNoteIsolated", count(connection, "SELECT COUNT(*) FROM cds_laboratory_result l JOIN hl7TextMessage m ON m.lab_id=l.nativeLabNo WHERE l.runId=? AND l.physiciansNotes='PRIVATE SYNTHETIC PHYSICIAN NOTE' AND LOCATE('PRIVATE SYNTHETIC PHYSICIAN NOTE',FROM_BASE64(m.message))=0", run));
        return result;
    }

    private static Object control(Connection connection, Path path, String operation) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 65536) throw new IllegalStateException("Owned fixture metadata required");
        Properties config = new Properties(); try (var input = Files.newInputStream(CONFIG)) { config.load(input); }
        if (!path.toAbsolutePath().toString().equals(config.getProperty("cds.test.fixtureMetadata"))) throw new IllegalStateException("Foreign fixture refused");
        JsonNode metadata = JSON.readTree(Files.readString(path));
        String namespace = metadata.path("namespace").asText(); if (!namespace.matches("cli-e2e-[a-f0-9]{8}")) throw new IllegalStateException("Foreign fixture refused");
        List<String> students = new ArrayList<>(); metadata.path("students").forEach(value -> students.add(value.asText()));
        String token = namespace.substring(8);
        List<String> expectedStudents = List.of("S" + token.substring(0, 4) + "1",
                "S" + token.substring(0, 4) + "2", "S" + token.substring(0, 4) + "3");
        if (!students.equals(expectedStudents)) throw new IllegalStateException("Invalid student fixture");
        int course = metadata.path("courseId").asInt(); if (course < 1_000_000_000 || course > 1_400_000_000) throw new IllegalStateException("Invalid course fixture");
        if (count(connection, "SELECT COUNT(*) FROM program WHERE id=? AND name=? AND description='Owned synthetic CDS fixture'", course, "CDS E2E " + token) != 1) throw new IllegalStateException("Owned course unavailable");
        for (String student : students) {
            if (count(connection, "SELECT COUNT(*) FROM provider p JOIN program_provider pp ON pp.provider_no=p.provider_no JOIN secRole r ON r.role_no=pp.role_id WHERE p.provider_no=? AND p.last_name='CDS SYNTHETIC FIXTURE' AND p.first_name='Synthetic' AND pp.program_id=? AND r.role_name='student'", student, course) != 1) {
                throw new IllegalStateException("Owned student membership unavailable");
            }
        }
        for (int i = 0; i < 2; i++) {
            if (count(connection, "SELECT COUNT(*) FROM program WHERE id=? AND name=? AND description='Owned synthetic CDS fixture'", course + i + 1, "program" + students.get(i)) != 1) {
                throw new IllegalStateException("Owned personal program unavailable");
            }
        }
        connection.setAutoCommit(false);
        try {
            switch (operation) {
                case "reset" -> {
                    for (int i = 0; i < students.size(); i++) update(connection, "UPDATE provider SET status=? WHERE provider_no=?", i == 2 ? "0" : "1", students.get(i));
                    for (int id = course; id <= course + 2; id++) update(connection, "UPDATE program SET programStatus='active' WHERE id=?", id);
                }
                case "missing-personal-program" -> update(connection, "UPDATE provider SET status='1' WHERE provider_no=?", students.get(2));
                case "inactive-personal-program" -> update(connection, "UPDATE program SET programStatus='inactive' WHERE id=?", course + 2);
                case "inactive-students" -> { for (String student : students) update(connection, "UPDATE provider SET status='0' WHERE provider_no=?", student); }
                case "inactive-course" -> update(connection, "UPDATE program SET programStatus='inactive' WHERE id=?", course);
                default -> throw new IllegalArgumentException("Unknown fixture control");
            }
            connection.commit(); return Map.of("control", operation, "totalNativePatients", scalar(connection, "SELECT COUNT(*) FROM demographic"));
        } catch (Exception failure) { connection.rollback(); throw failure; }
        finally { connection.setAutoCommit(true); }
    }

    private static int baseline(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try {
            if (count(connection, "SELECT COUNT(*) FROM secRole WHERE role_name='doctor'") != 1) {
                throw new IllegalStateException("Actual migrated doctor role required");
            }
            if (count(connection, "SELECT COUNT(*) FROM provider WHERE provider_no=?", PROVIDER) == 0) {
                seedProvider(connection, PROVIDER, "1");
                update(connection, "INSERT INTO secUserRole(provider_no,role_name,activeyn,lastUpdateDate) VALUES (?,'doctor',1,NOW())", PROVIDER);
            }
            if (count(connection, "SELECT COUNT(*) FROM provider WHERE provider_no=? AND provider_type='doctor' AND status='1' AND ((BINARY(last_name)=BINARY('CDS SYNTHETIC FIXTURE') AND BINARY(first_name)=BINARY('Synthetic')) OR (BINARY(last_name)=BINARY('carlosdoc') AND BINARY(first_name)=BINARY('doctor')))", PROVIDER) != 1
                    || count(connection, "SELECT COUNT(*) FROM secUserRole WHERE provider_no=? AND role_name='doctor' AND activeyn=1", PROVIDER) != 1) {
                throw new IllegalStateException("Existing non-fixture provider is never replaced");
            }
            int facility = Math.toIntExact(scalar(connection, "SELECT COALESCE(MIN(id),0) FROM Facility WHERE disabled=0"));
            if (facility == 0) {
                update(connection, "INSERT INTO Facility(name,hic,disabled,orgId,sectorId,enableHealthNumberRegistry,enableDigitalSignatures,enableOcanForms,enableCbiForm,enableAnonymous,enablePhoneEncounter,lastUpdated,enableEncounterTime,enableEncounterTransportationTime,rxInteractionWarningLevel,displayAllVacancies) VALUES('CDS synthetic CLI facility',0,0,0,0,1,0,0,0,0,0,NOW(),0,0,0,0)");
                facility = Math.toIntExact(scalar(connection, "SELECT LAST_INSERT_ID()"));
            }
            if (facility < 1) throw new IllegalStateException("Active synthetic facility required");
            connection.commit();
            return facility;
        } catch (Exception failure) { connection.rollback(); throw failure; }
        finally { connection.setAutoCommit(true); }
    }

    private static void seedProvider(Connection connection, String provider, String status) throws Exception {
        update(connection, "INSERT INTO provider(provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateDate) VALUES (?,'CDS SYNTHETIC FIXTURE','Synthetic','doctor','','U',?,NOW())", provider, status);
    }
    private static void seedProgram(Connection connection, int id, int facility, String name) throws Exception {
        update(connection, "INSERT INTO program(id,facilityId,name,description,type,maxAllowed,programStatus,transgender,firstNation,bedProgramAffiliated,alcohol,physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax) VALUES (?,?,?,'Owned synthetic CDS fixture','service',100,'active',0,0,0,0,0,0,0,'no',0,200)", id, facility, name);
    }
    private static long count(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (var result = statement.executeQuery()) { if (!result.next()) throw new IllegalStateException("Aggregate unavailable"); return result.getLong(1); }
        }
    }
    private static long scalar(Connection connection, String sql) throws Exception { return count(connection, sql); }
    private static void update(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]); statement.executeUpdate(); }
    }
    private static String resource(String name) throws Exception {
        try (var input = CdsCliEngineFixture.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Committed corpus unavailable");
            byte[] bytes = input.readNBytes(65536);
            if (input.read() != -1) throw new IllegalStateException("Committed corpus exceeds fixture bound");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private static String distinct(String source, String token, String label) {
        String suffix = switch (label) { case "allcategory" -> "all"; case "allcategoryAlpha" -> "alpha"; default -> label; };
        String identity = "E" + token + suffix;
        if (identity.length() > 20) throw new IllegalArgumentException("Fixture identity exceeds source contract");
        return source.replace("E-PW0000000000004241", identity).replace("FAKE-PW0000000000004241", "SYNTHETIC " + identity)
                .replace("E-CDS-CORPUS-202608", identity).replace("FAKE-CDS-CORPUS-202608", "SYNTHETIC " + identity);
    }
    private static void directory(Path path, boolean serviceOwned) throws Exception {
        Files.createDirectory(path); var lookup = path.getFileSystem().getUserPrincipalLookupService();
        if (serviceOwned) Files.setOwner(path, lookup.lookupPrincipalByName("carlos"));
        Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(lookup.lookupPrincipalByGroupName("carlos"));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(serviceOwned ? "rwx------" : "rwxr-x---"));
    }
    private static void rootReadable(Path path, boolean directory) throws Exception {
        var lookup = path.getFileSystem().getUserPrincipalLookupService();
        Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(lookup.lookupPrincipalByGroupName("carlos"));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwxr-x---" : "rw-r-----"));
    }
    private static void writeSource(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardOpenOption.CREATE_NEW); rootReadable(path, false);
    }
}
