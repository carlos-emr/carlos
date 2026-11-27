/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A committed form version's XML copy is named for its own edit time and never replaces another
 * version's copy, and a new version's edit time never repeats the patient's latest one (#5693).
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Form XML archive names")
class FormXmlArchiveUnitTest {
    private static final long NOW = 1_795_683_061_123L;
    private static final long SECOND = NOW - 123L;

    @TempDir Path archive;
    private String previousPath;

    @BeforeEach
    void useTemporaryArchive() {
        Properties configuration = CarlosProperties.getInstance();
        previousPath = configuration.getProperty("form_record_path");
        configuration.setProperty("form_record_path", archive.toString());
    }

    @AfterEach
    void restoreArchivePath() {
        Properties configuration = CarlosProperties.getInstance();
        if (previousPath == null) {
            configuration.remove("form_record_path");
        } else {
            configuration.setProperty("form_record_path", previousPath);
        }
    }

    @Test
    void shouldUseTheCurrentWholeSecond_whenNoVersionIsAtOrAfterIt() {
        assertThat(FormXmlArchive.nextEditTime(null, NOW)).isEqualTo(new Date(SECOND));
        assertThat(FormXmlArchive.nextEditTime(new Date(SECOND - 1), NOW)).isEqualTo(new Date(SECOND));
        assertThat(FormXmlArchive.nextEditTime(null).getTime() % 1000L).isZero();
    }

    @Test
    void shouldFollowTheLatestVersion_whenItHasTheCurrentSecondOrALaterOne() {
        // Two saves in one second, or in one transaction.
        assertThat(FormXmlArchive.nextEditTime(new Date(SECOND + 999), NOW)).isEqualTo(new Date(SECOND + 1000));
        // A version a few seconds ahead (several saves in one second, or the clock stepped back).
        assertThat(FormXmlArchive.nextEditTime(new Date(SECOND + 30_000), NOW)).isEqualTo(new Date(SECOND + 31_000));
        assertThat(FormXmlArchive.nextEditTime(new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS), NOW))
                .isEqualTo(new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS + 1000));
    }

    @Test
    void shouldIgnoreALatestVersion_moreThanAMinuteAheadOfTheClock() {
        assertThat(FormXmlArchive.nextEditTime(new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS + 1000), NOW))
                .isEqualTo(new Date(SECOND));
    }

    @Test
    void shouldNameTheCopyForTheVersionsEditTime_withTheImportContract() throws Exception {
        Path written = FormXmlArchive.write("formSF36", 42, new Date(NOW), document(7));

        assertThat(written.getParent()).isEqualTo(archive);
        assertThat(written.getFileName()).hasToString("formSF36_42_" + stamp(SECOND) + ".xml");
        assertThat(Files.readString(written)).contains("<Results>", "<Row>", "<ID>7</ID>");
    }

    @Test
    void shouldNeverReplaceAnotherVersionsCopy_whenItsNameIsTaken() throws Exception {
        Path taken = Files.writeString(archive.resolve("formSF36_42_" + stamp(SECOND) + ".xml"), "the other version");

        Path written;
        try (LogCapture logs = LogCapture.forLogger(FormXmlArchive.class.getName())) {
            written = FormXmlArchive.write("formSF36", 42, new Date(NOW), document(8));
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("1 second(s) after its edit time").doesNotContain("42", "formSF36");
            });
        }

        assertThat(Files.readString(taken)).isEqualTo("the other version");
        assertThat(written.getFileName()).hasToString("formSF36_42_" + stamp(SECOND + 1000) + ".xml");
        assertThat(Files.readString(written)).contains("<ID>8</ID>");
    }

    @Test
    void shouldRefuseAndWriteNothing_whenEveryLaterNameIsTaken() throws Exception {
        for (int later = 0; later <= FormXmlArchive.MAX_LATER_SECONDS; later++) {
            Files.writeString(archive.resolve("formSF36_42_" + stamp(SECOND + later * 1000L) + ".xml"), "taken");
        }

        assertThatThrownBy(() -> FormXmlArchive.write("formSF36", 42, new Date(NOW), document(9)))
                .isInstanceOf(FileAlreadyExistsException.class);
        try (var files = Files.list(archive)) {
            assertThat(files).hasSize(FormXmlArchive.MAX_LATER_SECONDS + 1)
                    .allSatisfy(file -> assertThat(Files.readString(file)).isEqualTo("taken"));
        }
    }

    private static String stamp(long millis) {
        return UtilDateUtilities.DateToString(new Date(millis), "yyyyMMddHHmmss");
    }

    private static Document document(int id) throws Exception {
        Document document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().newDocument();
        Element results = document.createElement("Results");
        Element row = document.createElement("Row");
        Element column = document.createElement("ID");
        column.setTextContent(Integer.toString(id));
        row.appendChild(column);
        results.appendChild(row);
        document.appendChild(results);
        return document;
    }
}
