/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Properties;
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
    private String previousEnabled;

    @BeforeEach
    void useTemporaryArchive() {
        Properties configuration = CarlosProperties.getInstance();
        previousPath = configuration.getProperty("form_record_path");
        previousEnabled = configuration.getProperty("save_as_xml");
        configuration.setProperty("form_record_path", archive.toString());
        configuration.setProperty("save_as_xml", "true");
    }

    @AfterEach
    void restoreArchivePath() {
        restore("form_record_path", previousPath);
        restore("save_as_xml", previousEnabled);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, value);
        }
    }

    @Test
    void shouldUseTheCurrentWholeSecond_whenNoVersionIsAtOrAfterIt() {
        assertThat(nextEditTime()).isEqualTo(new Date(SECOND));
        assertThat(nextEditTime(new Date(SECOND - 1))).isEqualTo(new Date(SECOND));
        assertThat(FormXmlArchive.currentSecond().getTime() % 1000L).isZero();
    }

    @Test
    void shouldFollowTheLatestVersion_whenItHasTheCurrentSecondOrALaterOne() {
        // Two saves in one second, or in one transaction.
        assertThat(nextEditTime(new Date(SECOND + 999))).isEqualTo(new Date(SECOND + 1000));
        // A version a few seconds ahead (several saves in one second, or the clock stepped back).
        assertThat(nextEditTime(new Date(SECOND + 30_000))).isEqualTo(new Date(SECOND + 31_000));
        assertThat(nextEditTime(new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS)))
                .isEqualTo(new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS + 1000));
    }

    @Test
    void shouldIgnoreALatestVersion_moreThanAMinuteAheadOfTheClock() {
        Date outlier = new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS + 1000);
        assertThat(nextEditTime(outlier)).isEqualTo(new Date(SECOND));
        assertThat(nextEditTime(outlier, new Date(SECOND))).isEqualTo(new Date(SECOND + 1000));
        assertThat(nextEditTime(outlier, new Date(SECOND + FormXmlArchive.MAX_AHEAD_MILLIS)))
                .isEqualTo(new Date(outlier.getTime() + 1000));
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

        assertThatThrownBy(() -> FormXmlArchive.write("formSF36", 42, new Date(NOW), document(8)))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(taken)).isEqualTo("the other version");
    }

    @Test
    void shouldChooseAnUnusedEditTime_whenAnOlderArchiveExistsWithoutItsRow() throws Exception {
        FormXmlArchive.write("formSF36", 42, new Date(SECOND), document(7));

        Date edited = nextEditTime();
        assertThat(edited).isEqualTo(new Date(SECOND + 1000));
        Path written = FormXmlArchive.write("formSF36", 42, edited, document(8));
        assertThat(written.getFileName()).hasToString("formSF36_42_" + stamp(edited.getTime()) + ".xml");
        assertThat(Files.readString(written)).contains("<ID>8</ID>");
    }

    @Test
    void shouldChooseAnUnusedEditTime_whenArchivesFillMoreThanAMinute() throws Exception {
        for (long second = SECOND; second <= SECOND + 61_000L; second += 1000L) {
            Files.createFile(archive.resolve("formSF36_42_" + stamp(second) + ".xml"));
        }

        Date edited = nextEditTime();
        assertThat(edited).isEqualTo(new Date(SECOND + 62_000L));
        assertThat(FormXmlArchive.write("formSF36", 42, edited, document(9))).exists();
    }

    private static Date nextEditTime(Date... existing) {
        return FormXmlArchive.nextEditTime("formSF36", 42, new Date(SECOND), List.of(existing));
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
