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
package io.github.carlos_emr.carlos.billings.ca.on.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Enforces the BDD test-name convention on the Ontario billing migration tests. */
@DisplayName("Ontario billing test naming architecture")
@Tag("unit")
@Tag("billing")
class BillingTestNamingArchitectureTest {

    private static final int MINIMUM_SCOPED_TEST_FILES = 100;
    private static final Pattern TEST_ANNOTATION = Pattern.compile(
            "@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b");
    private static final Pattern METHOD = Pattern.compile(
            "\\b(?:public|protected|private)?\\s*(?:static\\s+)?"
                    + "(?:void|[\\w<>?,\\s\\[\\]]+)\\s+(\\w+)\\s*\\(");
    private static final Pattern BDD_NAME =
            Pattern.compile("should[A-Z][A-Za-z0-9]*_[a-z][A-Za-z0-9]*");
    private static final Set<String> SHARED_BILLING_TESTS = Set.of(
            "BillingONCHeader1DaoIntegrationTest.java",
            "BillingONEAReportDaoIntegrationTest.java",
            "BillingONExtDaoIntegrationTest.java",
            "BillingONItemDaoIntegrationTest.java",
            "BillingONPaymentDaoImplUnitTest.java",
            "BillingONPaymentDaoIntegrationTest.java",
            "BillingOnItemPaymentDaoImplUnitTest.java",
            "BillingOnTransactionDaoImplUnitTest.java",
            "CtlBillingServiceDaoIntegrationTest.java",
            "DiagnosticCodeDaoIntegrationTest.java",
            "OscarAppointmentDaoQueryIntegrationTest.java",
            "BillingItemsNotLoadedExceptionUnitTest.java",
            "BillingONCHeader1UnitTest.java",
            "BillingONItemEqualsContractUnitTest.java",
            "BillingONItemUnitTest.java",
            "BillingONPaymentContractUnitTest.java");

    @Test
    void shouldKeepBillingTestNamesBddFormatted_forOntarioMigrationScope() throws IOException {
        List<String> violations = new ArrayList<>();
        int scannedFiles = 0;
        scannedFiles += collectViolations(Path.of("src/test/java/io/github/carlos_emr/carlos/billing/CA"), violations);
        scannedFiles += collectViolations(Path.of("src/test/java/io/github/carlos_emr/carlos/billings/ca"), violations);
        scannedFiles += collectViolations(Path.of("src/test/java/io/github/carlos_emr/carlos/commn/dao"), violations);
        scannedFiles += collectViolations(Path.of("src/test/java/io/github/carlos_emr/carlos/commn/model"), violations);

        assertThat(scannedFiles)
                .as("Scoped billing test files scanned")
                .isGreaterThanOrEqualTo(MINIMUM_SCOPED_TEST_FILES);

        assertThat(violations)
                .as("Test method names must be should<Action>_<prepositionOrContext><Condition>")
                .isEmpty();
    }

    @Test
    void shouldReportBddViolation_whenCommentSeparatesTestAnnotationFromMethod(@TempDir Path tempDir)
            throws IOException {
        Path path = tempDir.resolve("CommentedMethodUnitTest.java");
        Files.write(path, List.of(
                "class CommentedMethodUnitTest {",
                "    @" + "Test",
                "    // This comment must not clear the pending test-method state.",
                "    void bad" + "Name() {",
                "    }",
                "}"));
        List<String> violations = new ArrayList<>();

        collectFileViolations(path, violations);

        assertThat(violations).containsExactly(path + ":4 badName");
    }

    private static int collectViolations(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            List<Path> scopedTests = paths.filter(BillingTestNamingArchitectureTest::isJavaTestInScope).toList();
            for (Path path : scopedTests) {
                collectFileViolations(path, violations);
            }
            return scopedTests.size();
        }
    }

    private static boolean isJavaTestInScope(Path path) {
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".java")) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/commn/dao/") || normalized.contains("/commn/model/")) {
            return SHARED_BILLING_TESTS.contains(path.getFileName().toString());
        }
        return true;
    }

    private static void collectFileViolations(Path path, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(path);
        boolean pendingTestMethod = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (TEST_ANNOTATION.matcher(line).find()) {
                pendingTestMethod = true;
                continue;
            }
            String stripped = line.stripLeading();
            if (!pendingTestMethod || line.isBlank() || stripped.startsWith("@") || isCommentLine(stripped)) {
                continue;
            }
            pendingTestMethod = false;
            Matcher matcher = METHOD.matcher(line);
            if (matcher.find() && !BDD_NAME.matcher(matcher.group(1)).matches()) {
                violations.add(path + ":" + (i + 1) + " " + matcher.group(1));
            }
        }
    }

    private static boolean isCommentLine(String stripped) {
        return stripped.startsWith("//")
                || stripped.startsWith("/*")
                || stripped.startsWith("*")
                || stripped.startsWith("*/");
    }
}
