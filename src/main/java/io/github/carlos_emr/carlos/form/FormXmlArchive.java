/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import javax.xml.transform.TransformerException;
import org.w3c.dom.Document;

/**
 * The optional XML copy ({@code save_as_xml}) of a committed form version, and the edit time that
 * identifies it.
 *
 * <p>The XML import ({@code FormXmlImportService}) takes a version's identity from its archive
 * name, {@code <table>_<patient>_<yyyyMMddHHmmss>.xml}. It stores the name's edit time as the
 * restored row's {@code formEdited}, and skips an archive whose patient and edit time already
 * exist. Every committed version must therefore have an archive of its own, under a name no other
 * version of that questionnaire shares (issue #5693 review):</p>
 * <ul>
 *   <li>{@link #nextEditTime} never gives a new version the same second as the patient's latest
 *       version of the questionnaire, so two versions saved in one second or in one transaction
 *       keep distinct identities;</li>
 *   <li>{@link #write} names the archive from the version's stored edit time, not the commit
 *       clock, and reserves the name atomically, so an existing archive is never replaced.</li>
 * </ul>
 *
 * @since 2026-11-26
 */
final class FormXmlArchive {
    /** A latest version further ahead of the clock than this is treated as bad data, not followed. */
    static final long MAX_AHEAD_MILLIS = 60_000L;
    /** How many seconds later an archive name may move when concurrent saves stored the same second. */
    static final int MAX_LATER_SECONDS = 60;

    private static final String STAMP = "yyyyMMddHHmmss";

    private FormXmlArchive() {
    }

    /**
     * The edit time of a new version: the current second, or the second after the patient's latest
     * version of the same questionnaire when that is the current second or later. Whole seconds,
     * the precision of the {@code timestamp} column on every server.
     *
     * @param latest the latest stored edit time of the patient's versions of the questionnaire,
     *        visible to the saving transaction, or {@code null} when there is none
     * @return the edit time to store
     */
    static Date nextEditTime(Date latest) {
        return nextEditTime(latest, System.currentTimeMillis());
    }

    /**
     * @param latest the latest stored edit time, or {@code null}
     * @param now the current time in milliseconds
     * @return the edit time to store
     */
    static Date nextEditTime(Date latest, long now) {
        long second = wholeSecond(now);
        if (latest != null) {
            long latestSecond = wholeSecond(latest.getTime());
            // A latest version more than a minute ahead of the clock is bad data; following it
            // would date every later version after it.
            if (latestSecond >= second && latestSecond - second <= MAX_AHEAD_MILLIS) {
                second = latestSecond + 1000L;
            }
        }
        return new Date(second);
    }

    /**
     * Writes a committed version's XML copy under the configured {@code form_record_path}, named for
     * its stored edit time.
     *
     * <p>The name is reserved with an atomic create, so an archive that already exists is never
     * replaced. Two concurrent saves can still store the same second (each saw the other's version
     * only after commit); the later archive then takes the next free second, and a restore from it
     * dates that version that much later. Nothing about the patient or form is logged.</p>
     *
     * @param table the form's physical table, which begins the archive name
     * @param patient the patient's demographic number
     * @param edited the version's stored edit time
     * @param document the committed row as the legacy {@code <Results><Row>} document
     * @return the archive written
     * @throws FileAlreadyExistsException if every name up to {@link #MAX_LATER_SECONDS} later is taken
     * @throws IOException if the archive directory cannot be written
     * @throws TransformerException if the document cannot be written as XML
     */
    static Path write(String table, int patient, Date edited, Document document)
            throws IOException, TransformerException {
        File directory = Path.of(CarlosProperties.getInstance().getProperty("form_record_path", "/root")).toFile();
        long second = wholeSecond(edited.getTime());
        for (int later = 0; later <= MAX_LATER_SECONDS; later++) {
            String stamp = UtilDateUtilities.DateToString(new Date(second + later * 1000L), STAMP);
            File file = PathValidationUtils.validatePath(table + "_" + patient + "_" + stamp + ".xml", directory);
            try {
                Files.createFile(file.toPath());
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
            if (later > 0) {
                MiscUtils.getLogger().warn("The XML copy of a committed form version is named {} second(s) after its"
                        + " edit time because another version's copy has that name", later);
            }
            // JDBCUtil writes into the reserved file and deletes it if the write fails.
            JDBCUtil.saveAsXML(document, file.getPath());
            return file.toPath();
        }
        throw new FileAlreadyExistsException("No free XML archive name for the committed form version");
    }

    private static long wholeSecond(long millis) {
        return millis - Math.floorMod(millis, 1000L);
    }
}
