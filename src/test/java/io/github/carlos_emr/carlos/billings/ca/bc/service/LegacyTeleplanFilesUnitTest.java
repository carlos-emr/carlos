/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Recovery contracts for interrupted legacy exports. @since 2026-11-09 */
@Tag("unit")
class LegacyTeleplanFilesUnitTest {
    @TempDir Path directory;
    private static final String CLAIM = "HK81234.001";
    private static final String HTML = "HK91234_001.htm";

    @Test
    void shouldDiscardOwnedArtifacts_whenDatabaseConfirmsRollback() throws Exception {
        var files = new LegacyTeleplanFiles(directory);
        Path journal = files.stage(Map.of(CLAIM, "synthetic claim", HTML, "synthetic report"), Map.of(CLAIM, 42, HTML, 42));
        files.publish(journal);
        files.recover((id, filename) -> false);
        assertThat(directory.resolve(CLAIM)).doesNotExist();
        assertThat(directory.resolve(HTML)).doesNotExist();
        assertThat(journal).doesNotExist();
    }

    @Test
    void shouldRestoreCompleteArtifacts_whenCommitSucceededButAcknowledgementWasLost() throws Exception {
        var files = new LegacyTeleplanFiles(directory);
        Path journal = files.stage(Map.of(CLAIM, "synthetic claim", HTML, "synthetic report"), Map.of(CLAIM, 42, HTML, 42));
        files.publish(journal);
        Files.delete(directory.resolve(HTML));
        files.recover((id, filename) -> id == 42);
        assertThat(directory.resolve(CLAIM)).hasContent("synthetic claim" + System.lineSeparator());
        assertThat(directory.resolve(HTML)).hasContent("synthetic report" + System.lineSeparator());
        assertThat(journal).doesNotExist();
    }

    @Test
    void shouldPreserveChangedArtifactsAndJournal_whenRecoveryCannotProveOwnership() throws Exception {
        var files = new LegacyTeleplanFiles(directory);
        Path journal = files.stage(Map.of(CLAIM, "synthetic claim"), Map.of(CLAIM, 42));
        files.publish(journal);
        // Replace, rather than modifying the hard-linked staging copy.
        Files.delete(directory.resolve(CLAIM));
        Files.writeString(directory.resolve(CLAIM), "operator replacement");
        assertThatThrownBy(() -> files.recover((id, filename) -> false)).isInstanceOf(java.io.IOException.class);
        assertThat(directory.resolve(CLAIM)).hasContent("operator replacement");
        assertThat(journal).isDirectory();
    }

    @Test
    void shouldRefuseMixedCommitEvidence_withoutRemovingAnyFile() throws Exception {
        var files = new LegacyTeleplanFiles(directory);
        Path journal = files.stage(Map.of(CLAIM, "synthetic claim", HTML, "synthetic report"), Map.of(CLAIM, 42, HTML, 42));
        files.publish(journal);
        assertThatThrownBy(() -> files.recover((id, filename) -> CLAIM.equals(filename))).isInstanceOf(java.io.IOException.class);
        assertThat(directory.resolve(CLAIM)).exists();
        assertThat(directory.resolve(HTML)).exists();
        assertThat(journal).exists();
    }

    @Test
    void shouldRefuseExistingOrEscapingFilenames_withoutOverwritingThem() throws Exception {
        Files.writeString(directory.resolve(CLAIM), "existing content");
        var files = new LegacyTeleplanFiles(directory);
        assertThatThrownBy(() -> files.stage(Map.of(CLAIM, "replacement"), Map.of(CLAIM, 42))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> files.stage(Map.of("../escape", "replacement"), Map.of("../escape", 42))).isInstanceOf(java.io.IOException.class);
        files.recover((id, filename) -> false);
        assertThat(directory.resolve(CLAIM)).hasContent("existing content");
    }
}
