package io.github.carlos_emr.carlos.sms.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("support")
class SmsLoggingRedactionArchitectureTest {
    private static final Path SMS_SOURCE_ROOT = Path.of("src/main/java/io/github/carlos_emr/carlos/sms");

    @Test
    @DisplayName("SMS code avoids direct logging surfaces")
    void shouldAvoidDirectLoggingSurfacesInSmsPackage() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(SMS_SOURCE_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, violations));
        }

        assertThat(violations)
                .as("SMS code should avoid direct logging; use redacted/audited helpers for sensitive values")
                .isEmpty();
    }

    private static void collectViolations(Path path, List<String> violations) {
        String source = readString(path);
        addViolationIfPresent(path, source, violations, "logger.", "direct logger call");
        addViolationIfPresent(path, source, violations, "System.out", "standard output logging");
        addViolationIfPresent(path, source, violations, "System.err", "standard error logging");
        addViolationIfPresent(path, source, violations, "printStackTrace(", "direct stack trace logging");
        addViolationIfPresent(path, source, violations, "LogAction", "shared audit logger bypass");
    }

    private static void addViolationIfPresent(
            Path path,
            String source,
            List<String> violations,
            String pattern,
            String reason
    ) {
        if (source.contains(pattern)) {
            violations.add(path + ": " + reason + " contains `" + pattern + "`");
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
