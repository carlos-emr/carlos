/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile every packaged Jasper template with the actual report engine, including expression imports.
 *
 * <p>Templates named {@code .xml} (labels, day sheets) are included: they kept JasperReports 6
 * root attributes after the 7.x upgrade, failed to load at all, and went unnoticed while this test
 * only looked at {@code .jrxml} files (issue #5046).
 */
@Tag("integration")
@Tag("report")
class JasperReportTemplatesIntegrationTest {
    @TestFactory
    List<DynamicTest> shouldCompilePackagedTemplates_withCurrentJasperReports() throws Exception {
        Path root = Path.of("src/main/resources");
        try (var files = Files.walk(root)) {
            var templates = files.filter(JasperReportTemplatesIntegrationTest::isJasperTemplate).sorted().toList();
            assertThat(templates).hasSizeGreaterThanOrEqualTo(45);
            return templates.stream().map(path -> DynamicTest.dynamicTest(root.relativize(path).toString(), () -> {
                try (var input = Files.newInputStream(path)) {
                    assertThat(JasperCompileManager.compileReport(input).getName()).isNotBlank();
                }
            })).toList();
        }
    }

    private static boolean isJasperTemplate(Path path) {
        String name = path.toString();
        if (name.endsWith(".jrxml")) {
            return true;
        }
        try {
            return name.endsWith(".xml") && Files.readString(path, StandardCharsets.UTF_8).contains("<jasperReport");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
