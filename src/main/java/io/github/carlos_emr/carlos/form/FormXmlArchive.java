/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *   <li>Saves of the same patient's forms allocate an unused second while holding a database lock.
 *       An earlier archive can reserve a second even when its database row has been deleted;</li>
 *   <li>{@link #write} names the archive from the version's stored edit time, not the commit
 *       clock, and reserves the name atomically, so an existing archive is never replaced.</li>
 * </ul>
 *
 * @since 2026-11-26
 */
final class FormXmlArchive {
    /** A latest version further ahead of the clock than this is treated as bad data, not followed. */
    static final long MAX_AHEAD_MILLIS = 60_000L;
    private static final String STAMP = "yyyyMMddHHmmss";

    private FormXmlArchive() {
    }

    static Date currentSecond() {
        return new Date(wholeSecond(System.currentTimeMillis()));
    }

    /**
     * Chooses a whole second after the recent versions while avoiding any occupied seconds.
     * Bad future timestamps do not force the new version to follow them.
     */
    static Date nextEditTime(String table, int patient, Date now, List<Date> storedEdits) {
        long current = now.getTime();
        long candidate = current;
        Set<Long> occupied = new HashSet<>();
        for (Date edited : storedEdits) {
            long second = wholeSecond(edited.getTime());
            occupied.add(second);
            if (second >= candidate && second - current <= MAX_AHEAD_MILLIS) {
                candidate = second + 1000L;
            }
        }
        while (occupied.contains(candidate) || archiveExists(table, patient, candidate)) {
            candidate += 1000L;
        }
        return new Date(candidate);
    }

    static boolean isEnabled() {
        return Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("save_as_xml", "false"));
    }

    private static boolean archiveExists(String table, int patient, long second) {
        if (!isEnabled()) return false;
        try {
            return Files.exists(archivePath(table, patient, second));
        } catch (IllegalArgumentException | SecurityException _) {
            // An unusable archive path is reported by the post-commit write as usual.
            return false;
        }
    }

    /**
     * Writes a committed version's XML copy under the configured {@code form_record_path}, named for
     * its stored edit time.
     *
     * <p>The name is reserved with an atomic create. If the file already exists, the write fails
     * instead of publishing a copy that would restore under the wrong edit time.</p>
     *
     * @param table the form's physical table, which begins the archive name
     * @param patient the patient's demographic number
     * @param edited the version's stored edit time
     * @param document the committed row as the legacy {@code <Results><Row>} document
     * @return the archive written
     * @throws FileAlreadyExistsException if the version's archive name is already taken
     * @throws IOException if the archive directory cannot be written
     * @throws TransformerException if the document cannot be written as XML
     */
    static Path write(String table, int patient, Date edited, Document document)
            throws IOException, TransformerException {
        Path path = archivePath(table, patient, wholeSecond(edited.getTime()));
        Files.createFile(path);
        // JDBCUtil writes into the reserved file and deletes it if the write fails.
        JDBCUtil.saveAsXML(document, path.toString());
        return path;
    }

    private static Path archivePath(String table, int patient, long second) {
        File directory = Path.of(CarlosProperties.getInstance().getProperty("form_record_path", "/root")).toFile();
        String stamp = UtilDateUtilities.DateToString(new Date(second), STAMP);
        return PathValidationUtils.validatePath(table + "_" + patient + "_" + stamp + ".xml", directory).toPath();
    }

    private static long wholeSecond(long millis) {
        return millis - Math.floorMod(millis, 1000L);
    }
}
