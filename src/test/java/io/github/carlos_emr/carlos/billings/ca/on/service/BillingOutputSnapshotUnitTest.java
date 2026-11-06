/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.CarlosProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class BillingOutputSnapshotUnitTest {
    @TempDir Path directory;
    private Object oldHome;

    @BeforeEach void setup() {
        oldHome = CarlosProperties.getInstance().put("HOME_DIR", directory.toString());
    }
    @AfterEach void restoreProperty() {
        if (oldHome == null) CarlosProperties.getInstance().remove("HOME_DIR");
        else CarlosProperties.getInstance().put("HOME_DIR", oldHome);
    }
    @Test
    void shouldRestoreExactOriginal_afterReplacement() throws Exception {
        Path output = directory.resolve("preview.html");
        Files.writeString(output, "original patient preview\n");
        var snapshot = BillingOutputSnapshot.capture("preview.html");
        Files.writeString(output, "replacement");
        snapshot.restore();
        assertThat(output).hasContent("original patient preview\n");
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(output); }
    }
    @Test
    void shouldRemoveOnlyNewPreview_whenOriginalWasAbsent() throws Exception {
        Path unrelated = directory.resolve("unrelated.html");
        Files.writeString(unrelated, "unrelated");
        var snapshot = BillingOutputSnapshot.capture("new.html");
        Files.writeString(directory.resolve("new.html"), "replacement");
        snapshot.restore();
        assertThat(directory.resolve("new.html")).doesNotExist();
        assertThat(unrelated).hasContent("unrelated");
    }
    @Test
    void shouldRetainOriginalBackup_whenRestoreFails() throws Exception {
        Path output = directory.resolve("preview.html");
        Files.writeString(output, "original");
        var snapshot = BillingOutputSnapshot.capture("preview.html");
        Files.delete(output);
        Files.createDirectory(output);
        Files.writeString(output.resolve("blocker"), "do not delete");
        assertThatThrownBy(snapshot::restore).isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("reconcile");
        try (var files = Files.list(directory)) {
            var backups = files.filter(Files::isRegularFile).toList();
            assertThat(backups).hasSize(1);
            assertThat(backups.getFirst()).hasContent("original");
        }
        assertThat(output.resolve("blocker")).hasContent("do not delete");
    }
    @Test
    void shouldRejectDirectoryAndTraversal_beforeChangingFiles() throws Exception {
        Files.createDirectory(directory.resolve("directory.html"));
        assertThatThrownBy(() -> BillingOutputSnapshot.capture("directory.html"))
                .isInstanceOf(BillingFileWriteException.class);
        assertThatThrownBy(() -> BillingOutputSnapshot.capture("../outside.html"))
                .isInstanceOf(BillingFileWriteException.class);
        try (var files = Files.list(directory)) { assertThat(files.count()).isEqualTo(1); }
    }
    @Test
    void shouldDiscardOnlyBackup_afterSuccessfulPublication() throws Exception {
        Path output = directory.resolve("preview.html");
        Files.writeString(output, "old");
        var snapshot = BillingOutputSnapshot.capture("preview.html");
        Files.writeString(output, "committed");
        snapshot.discard();
        assertThat(output).hasContent("committed");
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(output); }
    }
}
