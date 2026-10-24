/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Compile every packaged JRXML with the actual report engine, including expression imports. */
@Tag("integration")
@Tag("report")
class JasperReportTemplatesIntegrationTest {
    @TestFactory
    List<DynamicTest> shouldCompilePackagedTemplates_withCurrentJasperReports() throws Exception {
        Path root = Path.of("src/main/resources");
        try (var files = Files.walk(root)) {
            var templates = files.filter(path -> path.toString().endsWith(".jrxml")).sorted().toList();
            assertThat(templates).isNotEmpty();
            return templates.stream().map(path -> DynamicTest.dynamicTest(root.relativize(path).toString(), () -> {
                try (var input = Files.newInputStream(path)) {
                    assertThat(JasperCompileManager.compileReport(input).getName()).isNotBlank();
                }
            })).toList();
        }
    }
}
