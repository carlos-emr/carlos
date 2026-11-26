/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.CarlosProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("billing")
class BillingOutputFilesUnitTest {
    @TempDir Path directory;
    private Object previousHome;

    @BeforeEach void configure() {
        previousHome = CarlosProperties.getInstance().put("HOME_DIR", directory.toString());
    }

    @AfterEach void restore() {
        if (previousHome == null) CarlosProperties.getInstance().remove("HOME_DIR");
        else CarlosProperties.getInstance().put("HOME_DIR", previousHome);
    }

    @Test
    void shouldKeepBothCompleteOutputsWithoutPrivateLinks_whenTheTransactionCommits() throws Exception {
        try (var files = new BillingOutputFiles.NewFiles()) {
            files.write("claim.001", "claim", StandardCharsets.US_ASCII);
            files.write("preview.html", "évidence", StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(directory.resolve("claim.001"))).isEqualTo("claim" + System.lineSeparator());
        assertThat(Files.readString(directory.resolve("preview.html"))).isEqualTo("évidence" + System.lineSeparator());
        try (var paths = Files.list(directory)) {
            assertThat(paths.map(Path::getFileName).map(Path::toString)).containsExactlyInAnyOrder("claim.001", "preview.html");
        }
    }

    @Test
    void shouldKeepAnIndependentReplacementAndRemoveOnlyOurOtherOutput_onRollback() throws Exception {
        Path claim = directory.resolve("claim.002");
        Path preview = directory.resolve("preview.html");
        try (var files = new BillingOutputFiles.NewFiles()) {
            files.write("claim.002", "ours", StandardCharsets.US_ASCII);
            files.write("preview.html", "ours", StandardCharsets.UTF_8);
            Path independent = Files.writeString(directory.resolve("independent"), "preserved");
            Files.move(independent, claim, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            var result = files.rollback(new IllegalStateException("confirmed rollback"));
            assertThat(result.independentReplacement()).isTrue();
            assertThat(result.complete()).isTrue();
        }
        assertThat(claim).hasContent("preserved");
        assertThat(preview).doesNotExist();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldPreserveRacingReplacements_whenTheOutputChangesDuringWithdrawal(boolean anotherFileArrives) throws Exception {
        Path target = directory.resolve("claim.004");
        Path independent = Files.writeString(directory.resolve("incoming"), "independent bytes");
        var cause = new IllegalStateException("confirmed rollback");
        try (var files = new BillingOutputFiles.NewFiles()) {
            files.write("claim.004", "generated", StandardCharsets.US_ASCII);
            try (var io = org.mockito.Mockito.mockStatic(Files.class, call -> {
                if (call.getMethod().getName().equals("move") && target.equals(call.getArgument(0))) {
                    Files.move(independent, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else if (anotherFileArrives && call.getMethod().getName().equals("createLink")
                        && target.equals(call.getArgument(0))) {
                    Files.writeString(target, "newest bytes", java.nio.file.StandardOpenOption.CREATE_NEW);
                }
                return call.callRealMethod();
            })) {
                var result = files.rollback(cause);
                assertThat(result.independentReplacement()).isTrue();
                assertThat(result.complete()).isEqualTo(!anotherFileArrives);
                assertThat(cause.getSuppressed()).hasSize(anotherFileArrives ? 1 : 0);
            }
        }
        assertThat(target).hasContent(anotherFileArrives ? "newest bytes" : "independent bytes");
        try (var paths = Files.list(directory)) {
            var retained = paths.filter(path -> path.getFileName().toString().startsWith(".ohip-rollback-")).toList();
            if (anotherFileArrives) assertThat(retained).singleElement().satisfies(path -> assertThat(path).hasContent("independent bytes"));
            else assertThat(retained).isEmpty();
        }
    }

    @Test
    void shouldKeepOrdinaryRecoveryAvailable_whenAnOwnedOutputAlreadyDisappeared() throws Exception {
        try (var files = new BillingOutputFiles.NewFiles()) {
            files.write("missing.001", "ours", StandardCharsets.US_ASCII);
            Files.delete(directory.resolve("missing.001"));
            var result = files.rollback(new IllegalStateException("confirmed rollback"));
            assertThat(result.independentReplacement()).isFalse();
            assertThat(result.complete()).isTrue();
        }
    }

    @Test
    void shouldRefuseAnExistingLinkAndPreserveItsTarget_whenNoOutputWasPublished() throws Exception {
        Path prior = Files.writeString(directory.resolve("prior"), "archive");
        Path target = Files.createSymbolicLink(directory.resolve("claim.003"), prior);
        try (var files = new BillingOutputFiles.NewFiles()) {
            assertThatThrownBy(() -> files.write("claim.003", "new", StandardCharsets.US_ASCII))
                    .isInstanceOf(BillingFileWriteException.class).hasCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            files.rollback(new IllegalStateException("confirmed rollback"));
        }
        assertThat(Files.isSymbolicLink(target)).isTrue();
        assertThat(prior).hasContent("archive");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void shouldUseTheLinuxNoReplaceRenameAndPinFileIdentity_whenStorageHasNoHardLinks() throws Exception {
        var previous = Files.writeString(directory.resolve("prior.001"), "prior claim");
        var independent = Files.writeString(directory.resolve("incoming"), "independent replacement");
        try (var filesystem = withoutHardLinks(); var files = new BillingOutputFiles.NewFiles()) {
            assertThatThrownBy(() -> files.write("prior.001", "new", StandardCharsets.US_ASCII))
                    .isInstanceOf(BillingFileWriteException.class).hasCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            files.write("new.001", "complete", StandardCharsets.US_ASCII);
            files.write("new.html", "preview", StandardCharsets.UTF_8);
            assertThat(directory.resolve("new.001")).hasContent("complete");
            Files.move(independent, directory.resolve("new.html"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            files.rollback(new IllegalStateException("confirmed rollback"));
        }
        assertThat(previous).hasContent("prior claim");
        assertThat(directory.resolve("new.001")).doesNotExist();
        assertThat(directory.resolve("new.html")).hasContent("independent replacement");
    }

    @Test
    void shouldReportTheStorageCapabilityWithoutPublishing_whenNeitherPrimitiveIsAvailable() throws Exception {
        try (var filesystem = withoutHardLinks();
             var kernel = org.mockito.Mockito.mockStatic(LinuxNoReplaceMove.class, call -> {
                 throw new java.nio.file.AtomicMoveNotSupportedException("private", "private", "unavailable");
             });
             var files = new BillingOutputFiles.NewFiles()) {
            assertThatThrownBy(() -> files.write("new.001", "fixture", StandardCharsets.US_ASCII))
                    .isInstanceOfSatisfying(BillingFileWriteException.class, error ->
                            assertThat(error.reason()).isEqualTo(BillingFileWriteException.Reason.NEW_OUTPUT_UNSUPPORTED));
        }
        try (var paths = Files.list(directory)) { assertThat(paths).isEmpty(); }
    }

    private static org.mockito.MockedStatic<Files> withoutHardLinks() {
        return org.mockito.Mockito.mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("createLink")) throw new UnsupportedOperationException("hard links unavailable");
            return call.callRealMethod();
        });
    }
}
