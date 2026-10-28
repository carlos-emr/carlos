/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.logging.log4j.Level;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Valid temp PDFs must not log a failed document-directory probe before succeeding. */
@Tag("unit")
@Tag("document")
@Tag("security")
@Isolated("EDocUtil path roots and temp cache are JVM-wide")
@DisplayName("EDocUtil document and temp path logging")
class EDocUtilTempPathLogUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path root;

    private final Map<String, String> previousSystemProperties = new HashMap<>();
    private String previousDocumentDir;
    private Field cachedAllowedTempDirectories;
    private Object previousAllowedTempDirectories;
    private Path documents;
    private Path managedTemp;

    @BeforeEach
    void configureIsolatedRoots() throws Exception {
        documents = Files.createDirectories(root.resolve("documents"));
        Path sharedTemp = Files.createDirectories(root.resolve("temp"));
        managedTemp = Files.createDirectories(sharedTemp.resolve("carlos-eform-browser-pdf-temp"));
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documents.toString());
        for (String property : new String[]{"java.io.tmpdir", "catalina.base", "catalina.home"}) {
            previousSystemProperties.put(property, System.getProperty(property));
            System.setProperty(property, property.equals("java.io.tmpdir") ? sharedTemp.toString()
                    : Files.createDirectories(root.resolve(property.replace('.', '-'))).toString());
        }
        // The production cache is initialized from system properties. Restore its exact previous
        // value afterward so this real-path fixture cannot change another test's temp boundary.
        cachedAllowedTempDirectories = PathValidationUtils.class.getDeclaredField("allowedTempDirectories");
        cachedAllowedTempDirectories.setAccessible(true);
        previousAllowedTempDirectories = cachedAllowedTempDirectories.get(null);
        cachedAllowedTempDirectories.set(null, null);
    }

    @AfterEach
    void restoreRoots() throws Exception {
        if (previousDocumentDir == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        previousSystemProperties.forEach((property, value) -> {
            if (value == null) System.clearProperty(property);
            else System.setProperty(property, value);
        });
        cachedAllowedTempDirectories.set(null, previousAllowedTempDirectories);
    }

    @Test
    void shouldReadManagedPdfAndCountPages_withoutFalseSecurityErrors() throws Exception {
        Path pdf = managedTemp.resolve("owned-render.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        try (LogCapture paths = LogCapture.forLogger(PathValidationUtils.class);
             LogCapture documentsLog = LogCapture.forLogger(EDocUtil.class)) {
            assertThat(EDocUtil.resolvePath(pdf.toString())).isEqualTo(pdf.toRealPath().toString());
            assertThat(EDocUtil.getPDFPageCount(pdf.toString())).isEqualTo(2);
            assertThat(EDocUtil.readContent(pdf.toString())).isEqualTo(Files.readAllBytes(pdf));
            assertThat(EDocUtil.getFile(pdf.toString())).isEqualTo(Files.readAllBytes(pdf));
            assertNoErrors(paths, documentsLog);
        }
    }

    @Test
    void shouldReadRelativeAndAbsoluteDocumentPaths_withoutErrors() throws Exception {
        Path document = Files.writeString(documents.resolve("owned.txt"), "owned document bytes");
        try (LogCapture paths = LogCapture.forLogger(PathValidationUtils.class);
             LogCapture documentsLog = LogCapture.forLogger(EDocUtil.class)) {
            assertThat(EDocUtil.resolvePath("owned.txt")).isEqualTo(document.toRealPath().toString());
            assertThat(EDocUtil.readContent(document.toString())).isEqualTo(Files.readAllBytes(document));
            assertNoErrors(paths, documentsLog);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"inside", "outside"})
    void shouldResolveAllowedTempSymlinkToCanonicalFile_withoutErrors(String location) throws Exception {
        Path document = Files.writeString(managedTemp.resolve("owned.txt"), "owned temp bytes");
        Path link = Files.createSymbolicLink((location.equals("inside") ? managedTemp : root)
                .resolve("inside-link.txt"), document);
        try (LogCapture paths = LogCapture.forLogger(PathValidationUtils.class);
             LogCapture documentsLog = LogCapture.forLogger(EDocUtil.class)) {
            assertThat(EDocUtil.resolvePath(link.toString())).isEqualTo(document.toRealPath().toString());
            assertThat(EDocUtil.readContent(link.toString())).isEqualTo(Files.readAllBytes(document));
            assertNoErrors(paths, documentsLog);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "temp-symlink", "document-symlink", "document-prefix"})
    void shouldRejectForbiddenPathsAndSymlinkEscapes_andKeepSecurityErrors(String kind) throws Exception {
        Path forbidden = Files.writeString(Files.createDirectories(root.resolve("documents-other"))
                .resolve("forbidden.txt"), "foreign bytes must not be read");
        Path requested = switch (kind) {
            case "temp-symlink" -> Files.createSymbolicLink(managedTemp.resolve("outside-link.txt"), forbidden);
            case "document-symlink" -> Files.createSymbolicLink(documents.resolve("outside-link.txt"), forbidden);
            case "document-prefix" -> documents.resolve("../documents-other/forbidden.txt");
            default -> forbidden;
        };
        try (LogCapture paths = LogCapture.forLogger(PathValidationUtils.class)) {
            assertThatThrownBy(() -> EDocUtil.resolvePath(requested.toString())).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> EDocUtil.readContent(requested.toString()))
                    .isInstanceOf(IOException.class).hasCauseInstanceOf(SecurityException.class);
            assertThat(EDocUtil.getFile(requested.toString())).isNull();
            assertThat(paths.events()).anyMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
    }

    @Test
    void shouldKeepActualPdfReadFailuresVisible() throws Exception {
        Path invalidPdf = Files.writeString(managedTemp.resolve("invalid.pdf"), "not a PDF");
        try (LogCapture documentsLog = LogCapture.forLogger(EDocUtil.class)) {
            assertThat(EDocUtil.getPDFPageCount(invalidPdf.toString())).isZero();
            assertThat(documentsLog.events()).anyMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR)
                    && event.getThrown() instanceof IOException);
        }
    }

    private static void assertNoErrors(LogCapture... captures) {
        for (LogCapture capture : captures) {
            assertThat(capture.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
    }
}
