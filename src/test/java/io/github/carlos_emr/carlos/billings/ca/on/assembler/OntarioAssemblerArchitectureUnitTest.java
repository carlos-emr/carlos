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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Architecture guard coverage for package and dependency boundaries in Ontario billing assemblers. */
@DisplayName("Ontario assembler architecture")
@Tag("unit")
@Tag("billing")
class OntarioAssemblerArchitectureUnitTest {

    private static final Path ASSEMBLER_DIR =
            Path.of("src/main/java/io/github/carlos_emr/carlos/billings/ca/on/assembler");
    private static final Pattern FORBIDDEN_WRITE = Pattern.compile(
            ".*(\\.merge\\s*\\(|\\.persist\\s*\\(|\\w+Dao\\.remove\\s*\\(|parseAndSaveRAPremiums\\s*\\().*");

    @Test
    void shouldKeepViewModelAssemblersFreeOfDaoWrites() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ASSEMBLER_DIR)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (FORBIDDEN_WRITE.matcher(line).matches()) {
                        violations.add(file + ":" + (i + 1) + ": " + line.trim());
                    }
                }
            }
        }

        assertThat(violations)
                .as("ViewModelAssemblers must be read-only; move DAO writes into service/persister classes")
                .isEmpty();
    }
}
