/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A lone {@code stream.read(buffer);} statement throws away how many bytes arrived: the stream may
 * return fewer than the buffer holds, so the data is silently truncated or carries a zero-filled
 * tail. Document storage, the document reader, MCEDT uploads and the consultation fax logo all did
 * this (#3027). Read whole files with {@code readAllBytes()} or loop on the returned count.
 */
@Tag("unit")
@DisplayName("Ignored InputStream.read(byte[]) return values")
class IgnoredStreamReadRegressionTest {

    /**
     * A statement that is only {@code <receiver>.read(<buffer>);}, where the receiver is named like
     * an input stream. Reads into an array with an offset, and reads whose count is assigned or
     * tested, are not matched; neither are domain {@code read(...)} methods such as the dashboard's
     * indicator-template handler, whose receivers are not stream-named.
     */
    private static final Pattern IGNORED_STREAM_READ = Pattern.compile(
            "^\\s*((?i:fis|fin|is|in|input|bis|dis|zis|gis|\\w*stream))\\.read\\(\\w+\\);\\s*$");

    @Test
    @DisplayName("should find no read(byte[]) statement whose byte count is ignored")
    void shouldContainNoIgnoredStreamRead_inMainSources() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        assertThat(sourceRoot).as("run from the project root").isDirectory();

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (IGNORED_STREAM_READ.matcher(lines.get(i)).matches()) {
                        offenders.add(sourceRoot.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("read the whole stream with readAllBytes(), or loop on read()'s returned count")
                .isEmpty();
    }

    @Test
    @DisplayName("should recognise the pattern the guard exists for, and ignore safe reads")
    void shouldMatchOnlyIgnoredStreamReads_forGuardPattern() {
        assertThat(matches("            fis.read(fdata);")).isTrue();
        assertThat(matches("        fileInputStream.read(faxLogImage);")).isTrue();
        assertThat(matches("    is.read(b);")).isTrue();

        assertThat(matches("        int n = fis.read(buffer);")).isFalse();
        assertThat(matches("        while ((n = in.read(buffer)) != -1) {")).isFalse();
        assertThat(matches("        fis.read(buffer, 0, length);")).isFalse();
        assertThat(matches("        templateHandler.read(bytearray);")).isFalse();
    }

    @Test
    @DisplayName("should read a whole classpath resource, whatever available() reports")
    void shouldReadWholeResource_viaMiscUtils() throws IOException {
        String missing = "/no-such-resource-for-read-test.txt";
        byte[] expected;
        try (InputStream in = getClass().getResourceAsStream("/log4j2.xml")) {
            assertThat(in).as("test classpath resource").isNotNull();
            expected = in.readAllBytes();
        }

        assertThat(MiscUtils.readFileAsByteArray("/log4j2.xml")).isEqualTo(expected);
        assertThatThrownBy(() -> MiscUtils.readFileAsByteArray(missing))
                .isInstanceOf(FileNotFoundException.class);
    }

    private static boolean matches(String line) {
        Matcher matcher = IGNORED_STREAM_READ.matcher(line);
        return matcher.matches();
    }
}
