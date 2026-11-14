/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Exact owned MVC scope; unrelated release actions do not require a develop refactor. */
final class CdsTransferActionTestSources {
    private CdsTransferActionTestSources() {}

    static Stream<Path> actions() throws IOException {
        var base = Path.of("src/main/java/io/github/carlos_emr/carlos/demographic");
        var files = new java.util.ArrayList<Path>();
        for (String direction : List.of("cdsimport", "cdsexport")) {
            try (var paths = Files.list(base.resolve(direction).resolve("web"))) {
                files.addAll(paths.filter(path -> path.getFileName().toString().endsWith("2Action.java"))
                        .filter(Files::isRegularFile).toList());
            }
        }
        if (files.isEmpty()) throw new IllegalStateException("CDS action inventory is empty");
        return files.stream().sorted();
    }
}
