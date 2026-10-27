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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsCliProtocolUnitTest {
    @TempDir Path directory;
    private static final String[] IMPORT = {"--operation", "import", "--provider", "999998", "--input", "/allowed/é.xml",
            "--source-namespace", "synthetic-source", "--source-profile", "pcon-2024-02"};
    private static final String NONCE = "5613b36a-4909-48bd-86f1-bd07b31c11f1";
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

    @Test
    void shouldExcludeDedicatedCliConfiguration_whenOrdinaryWebProfileDiscoversIt() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(CdsCliSpringConfiguration.class);context.refresh();
            assertThat(context.containsBean("dataSource")).isFalse();
            assertThat(context.getBeansOfType(CdsCliSpringConfiguration.class)).isEmpty();
            assertThat(context.getBeansOfType(io.github.carlos_emr.carlos.daos.security.SecuserroleDao.class)).isEmpty();
        }
    }

    @Test
    void shouldBindUtf8ArgumentsByLength_whenMatchingBothLauncherContracts() {
        String[] arguments = {"--operation", "import", "--provider", "999998", "--input", "/allowed/é.xml"};
        assertThat(HexFormat.of().formatHex(CdsDelegationVerifier.digest(arguments)))
                .isEqualTo("e593853869e82354927eeaa16bcafea034f26ced04aaad2ceeb6678e2a3f60a7");
        assertThat(CdsDelegationVerifier.digest(new String[]{"ab", "c"}))
                .isNotEqualTo(CdsDelegationVerifier.digest(new String[]{"a", "bc"}));
    }

    @Test
    void shouldPreserveFrameworkProviderKeys_whenProviderIdentifierIsAlphanumeric() {
        String[] arguments=IMPORT.clone();arguments[3]="T099";
        assertThat(CdsCliArguments.parse(arguments).command(NONCE).providerNo()).isEqualTo("T099");
    }

    @Test
    void shouldKeepBootstrapEvidencePrivateAndMessageFree_whenFrameworkInitializationFails() throws Exception {
        Properties properties=new Properties();properties.setProperty("cds.import.stagingRoot",directory.toString());
        String sentinel="SENSITIVE_PATIENT_AND_PASSWORD_SENTINEL";
        String location=CdsCliLogging.bootstrapFailure(properties,new IllegalStateException(sentinel,
                new java.sql.SQLException(sentinel,"42000",1234)));
        assertThat(location).isNotNull();
        assertThat(Files.readString(Path.of(location))).contains("IllegalStateException","sqlState","42000")
                .doesNotContain(sentinel);
        assertThat(Files.getPosixFilePermissions(Path.of(location)))
                .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void shouldValidateArgumentCombinations_whenConstructingSharedCommand() {
        var command = CdsCliArguments.parse(IMPORT).command(NONCE);
        assertThat(command.matchProviderNames()).isTrue(); assertThat(command.mergeExisting()).isFalse();
        assertThat(command.input()).isEqualTo(Path.of("/allowed/é.xml"));
        assertThat(command.sourceProfile()).isEqualTo("pcon-2024-02");
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--provider", "999998"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--synthetic"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--timeshift", "1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--program", "0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--facility", "2147483648"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--mystery", "sensitive-value"))).hasMessage("CDS_ARGUMENTS_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "resume", "cancel", "report"})
    void shouldReuseRecordedOptions_whenSelectingExistingRun(String operation) {
        String[] arguments = {"--operation", operation, "--provider", "999998", "--run", NONCE};
        assertThat(CdsCliArguments.parse(arguments).runId()).isEqualTo(NONCE);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(arguments, "--timeshift", "0"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowOnlyStatusLookup_whenRecoveringSubmissionWithoutRunHandle() {
        String[] args = {"--operation", "status", "--provider", "T099", "--submission-key", NONCE};
        var parsed = CdsCliArguments.parse(args);
        assertThat(parsed.runId()).isNull(); assertThat(parsed.submissionKey()).isEqualTo(NONCE);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(args, "--run", NONCE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsCliArguments.parse(concat(IMPORT, "--submission-key", NONCE)))
                .isInstanceOf(IllegalArgumentException.class);
        for (String operation : List.of("resume", "cancel", "report")) {
            assertThatThrownBy(() -> CdsCliArguments.parse(new String[]{"--operation", operation,
                    "--provider", "T099", "--submission-key", NONCE})).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldReadResolvedHandleWithoutAcceptingOrExecuting_whenSubmissionLookupSucceeds() {
        FakeRuntime runtime = new FakeRuntime(); runtime.resolvedRun = "recovered-run"; runtime.clean = false;
        String[] args = {"--operation", "status", "--provider", "999998", "--submission-key", NONCE};
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(args), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                out, err, (config, selected, delegation) -> runtime);
        assertThat(code).isZero();
        assertThat(runtime.calls).containsExactly("observe:status", "summary", "close");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("recovered-run", "\"cleanComplete\":false")
                .doesNotContain("ACCEPTED", "SUBMISSION");
    }

    @Test
    void shouldRetainPrintedSubmissionKey_whenAcceptanceAcknowledgementIsLost() {
        FakeRuntime runtime = new FakeRuntime(); runtime.acceptFailure = true;
        assertThat(invoke("import", runtime)).isEqualTo(2);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("SUBMISSION", NONCE).doesNotContain("ACCEPTED");
        assertThat(runtime.calls).containsExactly("accept", "close");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).doesNotContain("sensitive-path");
    }

    @Test
    void shouldPreserveRecoverableSubmissionWithoutExecuting_whenAcceptedConsoleWriteBreaks() {
        PrintStream broken = new PrintStream(stdout, true, StandardCharsets.UTF_8) {
            @Override public void println(String value) {
                if (value.contains("ACCEPTED")) setError(); else super.println(value);
            }
        };
        FakeRuntime runtime = new FakeRuntime();
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(IMPORT), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                broken, err, (config, selected, delegation) -> runtime);
        assertThat(code).isEqualTo(2);
        assertThat(runtime.calls).containsExactly("accept", "close");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("SUBMISSION", NONCE).doesNotContain("ACCEPTED");
    }

    @Test
    void shouldRemainUncertainWithoutClaimingNonAcceptance_whenSubmissionLookupCannotResolve() {
        String[] args = {"--operation", "status", "--provider", "999998", "--submission-key", NONCE};
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(args), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                out, err, (config, selected, delegation) -> { throw new CdsImportException("SUBMISSION_OUTCOME_NOT_YET_KNOWN"); });
        assertThat(code).isEqualTo(4);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("outcome is not yet known", "retain original source")
                .doesNotContain("no import was accepted");
    }

    @Test
    void shouldUseActualPropertiesSyntax_whenDerivingAuthoritativeHeap() throws Exception {
        var config = CdsCliConfiguration.parse(Path.of("/unused"),
                "cds.import.cliHeapMiB:7\\\n  68\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(config.heapMiB()).isEqualTo(768);
        assertThat(CdsCliConfiguration.parse(Path.of("/unused"), new byte[0]).heapMiB()).isEqualTo(512);
        assertThatThrownBy(() -> CdsCliConfiguration.parse(Path.of("/unused"), "cds.import.cliHeapMiB=127".getBytes()))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CdsCliConfiguration.parse(Path.of("/unused"), "cds.import.cliHeapMiB=16385".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectUntrustedConfigurationBeforeBoot_whenCapabilityProbeCannotUseProductionProperties() {
        int code = CdsImportCli.run(new String[]{"--capabilities"}, Map.of("CARLOS_CDS_PROPERTIES", "/tmp/forged.properties"),
                out, err, (config, arguments, delegation) -> { throw new AssertionError("must never boot"); });
        assertThat(code).isEqualTo(2); assertThat(stdout.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(stderr.toString(StandardCharsets.UTF_8)).doesNotContain("forged.properties");
    }

    @Test
    void shouldAcceptFreshBoundProof_whenProviderOperationNonceAndExactArgumentsMatch() throws Exception {
        var proof = CdsDelegationVerifier.verifyDocument(document(), NONCE + ".json", CdsCliArguments.parse(IMPORT), IMPORT, Instant.ofEpochSecond(1000));
        assertThat(proof.operator()).isEqualTo("synthetic-operator"); assertThat(proof.operatorUid()).isEqualTo(999998);
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider", "operation", "operator", "operatorUid", "argvSha256", "nonce", "expiresEpochSeconds", "issuedEpochSeconds", "protocol"})
    void shouldRefuseForgedProofFields_whenTrustedDocumentDoesNotBindRequest(String field) throws Exception {
        Map<String, Object> value = values();
        Object changed = switch (field) {
            case "protocol" -> 4294967297L;
            case "operatorUid" -> -1;
            case "expiresEpochSeconds" -> 999;
            case "issuedEpochSeconds" -> 1600;
            case "provider" -> "000001";
            case "operation" -> "report";
            case "operator" -> "forged\noperator";
            case "argvSha256" -> "0".repeat(64);
            default -> UUID.randomUUID().toString();
        };
        value.put(field, changed);
        byte[] forged = new ObjectMapper().writeValueAsBytes(value);
        var arguments = CdsCliArguments.parse(IMPORT);
        assertThatThrownBy(() -> CdsDelegationVerifier.verifyDocument(forged,
                NONCE + ".json", arguments, IMPORT, Instant.ofEpochSecond(1000))).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectAmbiguousJsonAndExpiredProof_whenDuplicateTrailingOrOverlongControlInputArrives() throws Exception {
        String document = new String(document(), StandardCharsets.UTF_8);
        var arguments = CdsCliArguments.parse(IMPORT);
        for (String malformed : List.of(document + " {}", document.replace("\"protocol\":1", "\"protocol\":1,\"protocol\":1"), " ".repeat(65537))) {
            assertThatThrownBy(() -> CdsDelegationVerifier.verifyDocument(malformed.getBytes(StandardCharsets.UTF_8),
                    NONCE + ".json", arguments, IMPORT, Instant.ofEpochSecond(1000))).isInstanceOf(IOException.class);
        }
        byte[] expired = document();
        assertThatThrownBy(() -> CdsDelegationVerifier.verifyDocument(expired, NONCE + ".json",
                arguments, IMPORT, Instant.ofEpochSecond(1400))).isInstanceOf(IOException.class);
    }

    @Test
    void shouldEmitAcceptedHandleBeforeExecution_whenForegroundImportStarts() {
        FakeRuntime runtime = new FakeRuntime();
        assertThat(invoke("import", runtime)).isZero();
        assertThat(runtime.calls).containsExactly("accept", "observe:import", "execute:false", "summary", "close");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("ACCEPTED", NONCE, "cleanComplete");
    }

    @Test
    void shouldReturnIncomplete_whenClinicalCompleteButReportOrCleanupIsIncomplete() {
        FakeRuntime runtime = new FakeRuntime(); runtime.clean = false;
        assertThat(invoke("import", runtime)).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "report"})
    void shouldSeparateQuerySuccessFromImportOutcome_whenExistingRunIsIncomplete(String operation) {
        FakeRuntime runtime = new FakeRuntime(); runtime.clean = false;
        assertThat(invoke(operation, runtime)).isZero();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"cleanComplete\":false");
    }

    @Test
    void shouldRetainFailureExit_whenRuntimeRetirementAlsoFails() {
        FakeRuntime runtime = new FakeRuntime(); runtime.clean = false; runtime.closeFailure = true;
        assertThat(invoke("import", runtime)).isEqualTo(4);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("CDS_CLEANUP").doesNotContain("sensitive-path");
    }

    @Test
    void shouldReportDeliveryFailure_whenConsoleIsBrokenAfterClinicalCommit() {
        var failed=new java.util.concurrent.atomic.AtomicBoolean();
        PrintStream broken = new PrintStream(new OutputStream() {
            @Override public void write(int value) throws IOException { if(failed.get()) throw new IOException("sensitive-path"); }
        });
        FakeRuntime runtime = new FakeRuntime(); runtime.expectAcceptedHandle = false;
        runtime.onExecution=() -> failed.set(true);
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(IMPORT), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                broken, err, (config, arguments, delegation) -> runtime);
        assertThat(code).isEqualTo(2); assertThat(runtime.calls).contains("execute:false", "close");
    }

    @Test
    void shouldRefuseBeforeOpeningRuntime_whenInitialConsoleDeliveryFails() {
        PrintStream broken=new PrintStream(new OutputStream(){@Override public void write(int value) throws IOException{throw new IOException("private");}});
        int code=CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"),new Properties(),512),
                CdsCliArguments.parse(IMPORT),new CdsDelegationVerifier.Delegation("synthetic-operator",999998,NONCE),
                broken,err,(config,arguments,delegation) -> {throw new AssertionError("No acceptance or bootstrap after broken initial delivery");});
        assertThat(code).isEqualTo(2);
    }

    @Test
    void shouldKeepAcceptedHandleAndSafeFailure_whenSharedEngineRefusesExecution() {
        FakeRuntime runtime = new FakeRuntime(); runtime.executionFailure = true;
        assertThat(invoke("import", runtime)).isEqualTo(4);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains(NONCE);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("CDS_INCOMPLETE").doesNotContain("sensitive-path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMPORT_BUSY", "IMPORT_ALREADY_RUNNING", "IMPORT_RULESET_OR_OPTIONS_INCOMPATIBLE",
            "MAINTENANCE_RELEASE_REQUIRES_RECONCILIATION", "CDS_INPUT_DISK_RESERVE", "CDS_SOURCE_SNAPSHOT_FAILED"})
    void shouldExposeFiniteSafeReason_whenSharedEngineRefusesControl(String reason) {
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(new String[]{"--operation", "resume", "--provider", "999998", "--run", NONCE}),
                new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE), out, err,
                (config, selected, delegation) -> { throw new CdsImportException(reason, true, new IOException("sensitive-path")); });
        assertThat(code).isEqualTo(4);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("safeCode=" + reason, "outcomeUnknown=true", "submission outcome is not yet known")
                .doesNotContain("sensitive-path");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).doesNotContain("ACCEPTED", "RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CDS_INPUT_DISK_RESERVE", "CDS_SOURCE_SNAPSHOT_FAILED"})
    void shouldRetainSubmissionNonceWithoutClaimingAcceptance_whenInputStorageFails(String reason) {
        FakeRuntime runtime = new FakeRuntime();
        runtime.inputFailureCode = reason;
        assertThat(invoke("import", runtime)).isEqualTo(4);
        assertThat(runtime.calls).containsExactly("accept", "close");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("SUBMISSION", NONCE).doesNotContain("ACCEPTED", "RESULT");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("safeCode=" + reason, "outcomeUnknown=false")
                .doesNotContain("sensitive-path", "patient-value", "NOT_ACCEPTED", "rolled back");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"private-patient-path", "PATIENT_ALICE_SMITH", "IMPORT_BUSY\nprivate-patient-path",
            "CDS_INPUT_DISK_RESERVE_PRIVATE", "CDS_SOURCE_SNAPSHOT_FAILED\nprivate-patient-path",
            "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"})
    void shouldProtectUnknownAndAdversarialCodes_whenRefusalReasonIsNotPublic(String reason) {
        int code = CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(IMPORT), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                out, err, (config, selected, delegation) -> { throw new CdsImportException(reason); });
        assertThat(code).isEqualTo(4);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("safeCode=DETAILS_PROTECTED", "outcomeUnknown=false")
                .doesNotContain("private-patient-path", "PATIENT_ALICE_SMITH", "safeCode=IMPORT_BUSY",
                        "safeCode=CDS_INPUT_DISK_RESERVE", "safeCode=CDS_SOURCE_SNAPSHOT_FAILED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReportTypedDurableResumeIntent_whenReadingCurrentStatus(boolean requested) {
        FakeRuntime runtime = new FakeRuntime(); runtime.resumeRequested = requested; runtime.maintenanceReleasePending = requested; runtime.clean = !requested;
        assertThat(invoke("status", runtime)).isZero();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"resumeRequested\":" + requested,
                "\"maintenanceReleasePending\":" + requested, "\"cleanComplete\":" + !requested);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE_SOURCE_VALUE", "released", "RELEASED\nPRIVATE_SOURCE_VALUE"})
    void shouldOmitInvalidControlSummaryValues_whenSummaryContainsFreeformOrWrongType(String value) {
        FakeRuntime runtime = new FakeRuntime(); runtime.resumeRequested = value;
        runtime.maintenanceReleasePending = value; runtime.maintenanceReleaseState = value;
        assertThat(invoke("status", runtime)).isZero();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).doesNotContain("resumeRequested", "maintenanceReleasePending",
                "maintenanceReleaseState", "PRIVATE_SOURCE_VALUE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_APPLICABLE", "RELEASE_PENDING", "RELEASE_DEFERRED", "REQUIRES_RECONCILIATION", "RELEASED"})
    void shouldReportFiniteMaintenanceReleaseState_whenReadingCurrentStatus(String state) {
        FakeRuntime runtime = new FakeRuntime(); runtime.maintenanceReleaseState = state;
        assertThat(invoke("status", runtime)).isZero();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"maintenanceReleaseState\":\"" + state + "\"");
    }

    private int invoke(String operation, FakeRuntime runtime) {
        String[] arguments = "import".equals(operation) ? IMPORT
                : new String[]{"--operation", operation, "--provider", "999998", "--run", NONCE};
        return CdsImportCli.runAuthorized(new CdsCliConfiguration(Path.of("/unused"), new Properties(), 512),
                CdsCliArguments.parse(arguments), new CdsDelegationVerifier.Delegation("synthetic-operator", 999998, NONCE),
                out, err, (config, selected, delegation) -> runtime);
    }

    private static Map<String, Object> values() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("protocol", 1); value.put("operator", "synthetic-operator"); value.put("operatorUid", 999998);
        value.put("provider", "999998"); value.put("operation", "import"); value.put("nonce", NONCE);
        value.put("issuedEpochSeconds", 900); value.put("expiresEpochSeconds", 1200);
        value.put("argvSha256", HexFormat.of().formatHex(CdsDelegationVerifier.digest(IMPORT))); return value;
    }
    private static byte[] document() throws IOException { return new ObjectMapper().writeValueAsBytes(values()); }
    private static String[] concat(String[] first, String... last) {
        List<String> all = new ArrayList<>(List.of(first)); all.addAll(List.of(last)); return all.toArray(String[]::new);
    }

    private final class FakeRuntime implements CdsCliRuntime {
        private final List<String> calls = new ArrayList<>();
        private boolean clean = true, closeFailure, executionFailure, acceptFailure, expectAcceptedHandle = true;
        private String resolvedRun, inputFailureCode;
        private Object resumeRequested, maintenanceReleasePending, maintenanceReleaseState;
        private Runnable onExecution=() -> { };
        @Override public String resolveRunId(String requested) { return resolvedRun == null ? requested : resolvedRun; }
        @Override public String accept() throws IOException {
            calls.add("accept");
            if (inputFailureCode != null) throw new CdsInputException(inputFailureCode, "sensitive-path", "patient-value");
            if (acceptFailure) throw new IOException("sensitive-path");
            return NONCE;
        }
        @Override public void observe(String run, String operation) { calls.add("observe:" + operation); }
        @Override public void execute(String run, boolean resume) {
            calls.add("execute:" + resume);
            if (expectAcceptedHandle) assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("ACCEPTED");
            if (executionFailure) throw new CdsImportException("sensitive-path");
            onExecution.run();
        }
        @Override public void cancel(String run) { calls.add("cancel"); }
        @Override public Map<String, Object> summary(String run) {
            calls.add("summary");
            Map<String, Object> summary = new LinkedHashMap<>(Map.of("runId", run, "state", "COMPLETE", "cleanComplete", clean,
                    "clinicalComplete", true, "operationTerminal", true));
            if (resumeRequested != null) summary.put("resumeRequested", resumeRequested);
            if (maintenanceReleasePending != null) summary.put("maintenanceReleasePending", maintenanceReleasePending);
            if (maintenanceReleaseState != null) summary.put("maintenanceReleaseState", maintenanceReleaseState);
            return summary;
        }
        @Override public Map<String, Object> reports(String run) { calls.add("reports"); return Map.of("reports", List.of()); }
        @Override public void close() throws IOException {
            calls.add("close"); if (closeFailure) throw new IOException("sensitive-path");
        }
    }
}
