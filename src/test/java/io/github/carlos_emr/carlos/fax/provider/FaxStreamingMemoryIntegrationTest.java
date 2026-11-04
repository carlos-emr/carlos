/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
@Tag("fax")
class FaxStreamingMemoryIntegrationTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(240)
    void shouldTransferLargeFaxes_withHeapSmallerThanDocuments() throws Exception {
        Path log = directory.resolve("memory-profile.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx48m", "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true",
                "-Djava.io.tmpdir=" + directory, "-cp", System.getProperty("java.class.path"),
                FaxStreamingMemoryProbe.class.getName(), directory.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(220, TimeUnit.SECONDS)).as("bounded memory probe completed").isTrue();
            String output = Files.readString(log);
            System.out.print(output);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output.lines().filter(line -> line.startsWith("STREAMING_HEAP")).count()).isEqualTo(8);
            assertThat(output).contains("srfax inbound sizeMiB=256", "srfax outbound sizeMiB=256",
                    "middleware inbound sizeMiB=256", "middleware outbound sizeMiB=256", "maxHeapMiB=48");
        } finally {
            process.destroyForcibly();
        }
    }
}
