/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * Modifications by CARLOS Contributors, 2026.
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.eform;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.ImportZipInputStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility class for exporting eForms as ZIP archives.
 */
public class EFormExportZip {
    private static final Logger _log = MiscUtils.getLogger();

    // Backstops for egregious expansion, with room for large images and bulk form packages.
    private static final long MAX_IMPORT_ENTRY_BYTES = 256L << 20;
    private static final long MAX_IMPORT_TOTAL_BYTES = 1L << 30;
    private static final int MAX_IMPORT_ENTRIES = 10_000;
    // Parsed properties and retained HTML need a smaller heap budget than streamed assets.
    private static final long MAX_IMPORT_TEXT_ENTRY_BYTES = 16L << 20;
    private static final long MAX_IMPORT_TEXT_TOTAL_BYTES = 64L << 20;

    /**
     * Converts an eForm display title to one safe generated filename component.
     * Preserves interior spaces and Unicode; the original title remains in eform.properties.
     * Stored paths are validated separately; the caller allocates unique archive entry names.
     *
     * @param name display title used to generate an export name
     * @return a validated component of at most 251 UTF-8 bytes, reserving four bytes for .zip
     * @throws SecurityException when the generated component is invalid or empty
     */
    public static String exportNameComponent(String name) {
        if (name == null || name.trim().isEmpty()) {
            return PathValidationUtils.validatePathComponent(name, "eform export name");
        }
        String safeName = normalizeTrailingSpacesAndDots(
                name.replaceAll("[/\\\\:\\p{Cc}]", "_").replaceAll("^[ .~]+", "_"));
        // Windows device basenames are reserved even with an extension (including COM¹/LPT¹).
        // https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
        if (safeName.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(?:\\..*)?")) {
            safeName = "_" + safeName;
        }
        return PathValidationUtils.validatePathComponent(
                normalizeTrailingSpacesAndDots(boundedComponent(safeName, "", 251)), "eform export name");
    }

    /** Replaces a trailing run with one underscore without regex suffix backtracking. */
    private static String normalizeTrailingSpacesAndDots(String name) {
        int end = name.length();
        while (end > 0 && (name.charAt(end - 1) == ' ' || name.charAt(end - 1) == '.')) {
            end--;
        }
        return end == name.length() ? name : name.substring(0, end) + "_";
    }

    /** Bounds UTF-8 bytes without splitting a code point, retaining space for a required suffix. */
    private static String boundedComponent(String base, String suffix, int limit) {
        int remaining = limit - suffix.getBytes(StandardCharsets.UTF_8).length;
        int end = 0;
        while (end < base.length()) {
            int codePoint = base.codePointAt(end);
            int bytes = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes > remaining) break;
            remaining -= bytes;
            end += Character.charCount(codePoint);
        }
        return base.substring(0, end) + suffix;
    }

    public void exportForms(List<EForm> eForms, OutputStream os) throws IOException, Exception {
        ZipOutputStream zos = new ZipOutputStream(os);
        zos.setLevel(9);
        Set<String> folders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> htmlNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        htmlNames.add("eform.properties");
        Pattern eformImagePattern = Pattern.compile("\\$\\{oscar_image_path\\}.+?[\"|'|>|<]");
        // HTML and assets share the importer's basename namespace. Reserve asset names
        // before allocating any generated HTML name, including references in later forms.
        for (EForm form : eForms) {
            if (form.getFormHtml() == null) continue;
            Matcher assets = eformImagePattern.matcher(form.getFormHtml());
            while (assets.find()) {
                String reference = assets.group();
                String assetName = reference.substring("${oscar_image_path}".length(), reference.length() - 1);
                htmlNames.add(PathValidationUtils.validatePathComponent(assetName, "eform export image name"));
            }
        }

        for (EForm eForm : eForms) {
            if (eForm.getFormName() == null || eForm.getFormName().equals("")) {
                _log.error("Eform must have a name to export.  FID: {}", LogSafe.sanitize(eForm.getFid())); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                throw new Exception("EForm must have a name to export");
            }
            Properties properties = new Properties(); //put all form properties into here
            String formFolder = exportNameComponent(eForm.getFormName().replaceAll("\\s", "")
                    .replaceAll("[*?\"<>|]", "_"));
            String baseFolder = formFolder;
            int suffix = 2;
            while (!folders.add(formFolder)) formFolder = boundedComponent(baseFolder, "-" + suffix++, 255);
            String fileName = eForm.getFormFileName();
            _log.debug("before:>" + fileName + "<");
            if (fileName == null || fileName.equals("")) {
                fileName = boundedComponent(formFolder, ".html", 255);
            }
            _log.debug("after:>" + fileName + "<");

            // Stored file paths remain strict; only names generated from display titles are sanitized.
            fileName = PathValidationUtils.validatePathComponent(fileName, "eform export file name");
            // The legacy importer matches HTML by basename, not its containing ZIP folder.
            int fileSuffix = 2;
            if (fileName.getBytes(StandardCharsets.UTF_8).length > 255) fileName = "export-" + fileSuffix++ + ".html";
            while (!htmlNames.add(fileName)) fileName = "export-" + fileSuffix++ + ".html";
            String directoryName = formFolder + "/"; //formName with all spaces removed
            String html = eForm.getFormHtml();
            properties.setProperty("form.htmlFilename", fileName);
            if (eForm.getFormName() != null && !eForm.getFormName().equals(""))
                properties.setProperty("form.name", eForm.getFormName());
            if (eForm.getFormSubject() != null && !eForm.getFormSubject().equals(""))
                properties.setProperty("form.details", eForm.getFormSubject());
            if (eForm.getFormCreator() != null && !eForm.getFormCreator().equals(""))
                properties.setProperty("form.creator", eForm.getFormCreator());
            if (eForm.getFormDate() != null && !eForm.getFormDate().equals(""))
                properties.setProperty("form.date", eForm.getFormDate());
            if (eForm.isShowLatestFormOnly()) properties.setProperty("form.showLatestFormOnly", "true");
            if (eForm.isPatientIndependent()) properties.setProperty("form.patientIndependent", "true");

            //write properties file
            ZipEntry propertiesZipEntry = new ZipEntry(directoryName + "eform.properties");
            zos.putNextEntry(propertiesZipEntry);
            properties.store(zos, "");
            zos.closeEntry();

            //write html
            String htmlFilename = "";
            htmlFilename = directoryName + fileName;
            _log.debug("html file name " + htmlFilename);
            ZipEntry htmlZipEntry = new ZipEntry(htmlFilename);
            zos.putNextEntry(htmlZipEntry);
            byte[] bytes = html.getBytes("UTF-8");
            InputStream is = new ByteArrayInputStream(bytes);
            outputToInput(zos, is);
            zos.closeEntry();

            //get Images, must do html search for image name

            Matcher matcher = eformImagePattern.matcher(html);
            int start = 0;
            while (matcher.find(start)) {
                String match = matcher.group();
                MiscUtils.getLogger().debug(match);
                start = matcher.end();
                int length = "${oscar_image_path}".length();
                String imageFileName = match.substring(length, match.length() - 1);
                MiscUtils.getLogger().debug("Image Name: " + imageFileName);
                // Validate the image name as a single path component before it becomes a ZIP entry name.
                imageFileName = PathValidationUtils.validatePathComponent(imageFileName, "eform export image name");
                File imageFile = getImageFile(imageFileName);
                try (FileInputStream fis = new FileInputStream(imageFile)) {  //should error out if image not found, in this case, skip the image
                    ZipEntry imageZipEntry = new ZipEntry(directoryName + imageFileName);
                    zos.putNextEntry(imageZipEntry);
                    outputToInput(zos, fis);
                    zos.closeEntry();
                } catch (FileNotFoundException fnfe) {
                    continue;
                }

            }
        }

        zos.close();
    }


    public File getImageFile(String imageFileName) throws Exception {
        String home_dir = CarlosProperties.getInstance().getEformImageDirectory();

        try {
            File directory = PathValidationUtils.resolveConfiguredDirectory(home_dir, "eform image directory");
            if (!directory.exists()) {
                throw new Exception("Directory:  " + home_dir + " does not exist");
            }
            return PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validatePathComponent(imageFileName, "eform image file"), directory);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("Could not open file " + home_dir + imageFileName + " does " + home_dir + " exist ?", e);
        }
    }

    private void outputToInput(OutputStream os, InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }

    private void inputToOutput(InputStream inputStream, OutputStream outputStream) throws IOException {
        // Keep working memory fixed even for large ZIP entries. The caller owns
        // both streams; in particular, do not close the shared ZipInputStream.
        byte[] buffer = new byte[8192];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
    }

    /**
     * Imports an eForm archive after fully staging and validating its expansion.
     * Generous abuse safeguards allow 256 MiB per entry, 1 GiB expanded overall and 10,000
     * entries, including directories and skipped archiver metadata. Large image assets and
     * multi-form packages within these ceilings are accepted without a compression-ratio limit.
     * Properties and form HTML additionally allow 16 MiB per entry and 64 MiB combined,
     * checked before parsing properties or publishing any form or asset.
     * Counters use actual decompressed bytes, including when ZIP sizes are absent or false.
     * A limit failure closes the input and removes this import's private staging directory
     * before any form registration or public asset publication occurs.
     *
     * @param importInputStream compressed archive input, closed by this method
     * @return warnings for skipped forms or existing images
     * @throws IOException if extraction fails or a resource limit is exceeded
     * @throws Exception if validation or form registration fails
     */
    public List<String> importForm(InputStream importInputStream) throws IOException, Exception {
        return importForm(importInputStream, MAX_IMPORT_ENTRY_BYTES, MAX_IMPORT_TOTAL_BYTES, MAX_IMPORT_ENTRIES);
    }

    /** Applies explicit budgets so rejection and cleanup can be tested with small synthetic archives. */
    List<String> importForm(InputStream importInputStream, long maxEntryBytes, long maxTotalBytes,
            int maxEntries) throws Exception {
        ArrayList<String> errors = new ArrayList<String>();
        _log.info("Importing eforms");

        File imageTempFolderDir = null;
        long textBytes;
        Hashtable<String, EForm> eformTable = new Hashtable<String, EForm>(); //stores eforms constructed from eform.properties, no HTML
        Hashtable<String, EForm> eformTableFailed = new Hashtable<String, EForm>();  //stores eforms that are constructed from eform.properties that alredy exist and do not need to be imported
        Hashtable<String, File> tempFiles = new Hashtable<String, File>(); //references extracted files in the temp folder
        // Always remove the temp extraction folder, even if a malicious/invalid zip entry makes
        // validateZipEntryPath throw partway through the first run (which would otherwise leak the
        // extracted temp files and the open ZipInputStream).
        try {
            //first runthrough, get the properties files, construct eforms, cache files
            try (ZipInputStream zis = new ImportZipInputStream(importInputStream,
                    maxEntryBytes, maxTotalBytes, maxEntries)) {
                File imageDir = ImageUpload2Action.getImageFolder();
                File imageExtractDir = PathValidationUtils.validateGeneratedChildPath("extractFolder", imageDir);
                // Each import owns a unique child of the retained, shared staging parent.
                // Establish stream ownership before staging setup, which can itself fail.
                Files.createDirectories(imageExtractDir.toPath());
                imageTempFolderDir = Files.createTempDirectory(imageExtractDir.toPath(), "extract-").toFile();
                imageTempFolderDir = PathValidationUtils.validateExistingPath(imageTempFolderDir, imageExtractDir);
                textBytes = stageImportEntries(zis, imageTempFolderDir, eformTable, eformTableFailed, tempFiles, errors);
            }

            // Check every retained HTML entry before the publication loop can expose an
            // asset. Match by the properties filename, not by an attacker-chosen extension.
            for (Entry<String, File> tempFile : tempFiles.entrySet()) {
                if (eformTable.containsKey(tempFile.getKey())) {
                    File htmlFile = PathValidationUtils.validateExistingPath(tempFile.getValue(), imageTempFolderDir);
                    textBytes = countImportTextBytes(htmlFile, textBytes);
                }
            }

            publishImportFiles(imageTempFolderDir, eformTable, eformTableFailed, tempFiles, errors);
            _log.info("Registering: " + eformTable.values().size() + " eforms");
            //write constructed eforms
            for (EForm eform : eformTable.values()) {
                _log.info("New eform: {}", LogSafe.sanitize(eform.getFormName()));
                EFormUtil.saveEForm(eform);
            }
        } finally {
            deleteDirectory(imageTempFolderDir);
        }
        return errors;
    }

    /** Stages all entries, including counting skipped entries, before any publication. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs CRLF_INJECTION_LOGS: the logged zip entry name is already passed through LogSafe.sanitize(...), so CR/LF are neutralized.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "CRLF_INJECTION_LOGS"}, justification = "IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. CRLF_INJECTION_LOGS: logged zip entry name is already wrapped in LogSafe.sanitize(...).")
    private long stageImportEntries(ZipInputStream zis, File imageTempFolderDir,
            Map<String, EForm> eformTable, Map<String, EForm> eformTableFailed,
            Map<String, File> tempFiles, List<String> errors) throws Exception {
        long textBytes = 0;
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            // Validate before skipping: traversal names must fail even under metadata folders.
            File zipEntryFile = PathValidationUtils.validateZipEntryPath(ze, imageTempFolderDir);
            if (ze.isDirectory() || isArchiverMetadataEntry(ze.getName())) {
                // Folder entries and Finder metadata are not form content. The next
                // getNextEntry call still drains their payload through the byte budgets.
                if (_log.isDebugEnabled()) {
                    _log.debug("Skipping non-content eForm import zip entry: {}", LogSafe.sanitize(ze.getName()));
                }
                continue;
            }
            String zipEntryFileName = PathValidationUtils.validatePathComponent(zipEntryFile.getName(), "eform import zip entry");
            _log.info("Unzipping..." + LogSafe.sanitize(zipEntryFileName));
            if (zipEntryFileName.equalsIgnoreCase("eform.properties")) {
                textBytes = stageImportProperties(zis, imageTempFolderDir, textBytes, eformTable, eformTableFailed, errors);
            } else {
                File tempFile = PathValidationUtils.validateGeneratedChildPath(zipEntryFileName, imageTempFolderDir);
                tempFiles.put(zipEntryFileName, tempFile);
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    inputToOutput(zis, fos);
                }
                zis.closeEntry();
            }
        }
        return textBytes;
    }

    /** Checks properties before parsing and retains warnings and form-selection behavior. */
    private long stageImportProperties(ZipInputStream zis, File imageTempFolderDir, long textBytes,
            Map<String, EForm> eformTable, Map<String, EForm> eformTableFailed, List<String> errors) throws Exception {
        Properties properties = new Properties();
        // Properties.load can allocate a full expanded line before returning, so stage
        // and reject oversized text before giving it to the parser.
        File propertiesFile = Files.createTempFile(imageTempFolderDir.toPath(), "properties-", ".tmp").toFile();
        try {
            try (FileOutputStream output = new FileOutputStream(propertiesFile)) {
                inputToOutput(zis, output);
            }
            textBytes = countImportTextBytes(propertiesFile, textBytes);
            try (FileInputStream input = new FileInputStream(propertiesFile)) {
                properties.load(input);
            }
        } finally {
            Files.deleteIfExists(propertiesFile.toPath());
        }
        EForm newEForm = this.createEFormFromProperties(properties);
        if (newEForm.getFormName() == null || newEForm.getFormName().isEmpty()) {
            errors.add("Skipped form because it has no form name.");
            _log.info("Skipped form because it has no form name.");
            eformTableFailed.put(newEForm.getFormFileName(), newEForm);
            return textBytes;
        }
        if (newEForm.getFormFileName() == null || newEForm.getFormFileName().isEmpty()) {
            errors.add("Skipped form titled '" + newEForm.getFormName() + "' because it has no form filename.");
            _log.info("Skipped form titled '{}' because it has no form filename.", LogSafe.sanitize(newEForm.getFormName()));
            return textBytes;
        }
        if (EFormUtil.formExistsInDB(newEForm.getFormName())) {
            errors.add("Skipped form '" + newEForm.getFormName() + "', form already exists");
            _log.info("Skipped form '{}', form already exists", LogSafe.sanitize(newEForm.getFormName()));
            eformTableFailed.put(newEForm.getFormFileName(), newEForm);
            return textBytes;
        }
        eformTable.put(newEForm.getFormFileName(), newEForm);
        _log.debug("going in eform table >{}<", LogSafe.sanitize(newEForm.getFormFileName()));
        zis.closeEntry();
        return textBytes;
    }

    /** Loads retained HTML and publishes assets only after all resource preflight checks pass. */
    private void publishImportFiles(File imageTempFolderDir, Map<String, EForm> eformTable,
            Map<String, EForm> eformTableFailed, Map<String, File> tempFiles, List<String> errors) throws IOException {
        for (Entry<String, File> tempFile : tempFiles.entrySet()) {
            _log.info("looking at {}", LogSafe.sanitize(tempFile.getKey()));
            if (eformTable.containsKey(tempFile.getKey())) {
                File extractedTempFile = PathValidationUtils.validateExistingPath(tempFile.getValue(), imageTempFolderDir);
                try (FileInputStream fis = new FileInputStream(extractedTempFile);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    inputToOutput(fis, baos);
                    String html = new String(baos.toByteArray());
                    // Do not log raw uploaded HTML; log only its length.
                    _log.debug("Loaded eform HTML content ({} chars)", html.length());
                    eformTable.get(tempFile.getKey()).setFormHtml(html);
                }
            } else if (!eformTableFailed.containsKey(tempFile.getKey())) {
                publishImportAsset(tempFile, imageTempFolderDir, errors);
            }
        }
    }

    private void publishImportAsset(Entry<String, File> tempFile, File imageTempFolderDir,
            List<String> errors) throws IOException {
        File extractedTempFile = PathValidationUtils.validateExistingPath(tempFile.getValue(), imageTempFolderDir);
        File imageFile = PathValidationUtils.validateGeneratedChildPath(PathValidationUtils.validatePathComponent(tempFile.getKey(), "eform image file"), ImageUpload2Action.getImageFolder());
        try {
            // Publish the complete inode without replacing another import's image.
            // Cleanup unlinks only our private staging name, preserving the public link.
            Files.createLink(imageFile.toPath(), extractedTempFile.toPath());
            _log.info("Loaded eform file: {}", LogSafe.sanitize(tempFile.getKey()));
        } catch (FileAlreadyExistsException e) {
            errors.add("Image '" + tempFile.getKey() + "' already exists, skipping image, but the form may still be uploaded.  Please resolve.");
            _log.info("EForm Import: Image with name '{}' already exists, skipping image, but the form may still be uploaded.  Please resolve.", LogSafe.sanitize(tempFile.getKey()));
        } catch (UnsupportedOperationException e) {
            throw new IOException("The eForm image filesystem does not support safe atomic asset publication.", e);
        }
    }

    /** Counts actual staged text bytes before any parser or full-entry buffer sees them. */
    private long countImportTextBytes(File file, long totalBytes) throws IOException {
        long entryBytes = Files.size(file.toPath());
        if (entryBytes > MAX_IMPORT_TEXT_ENTRY_BYTES || entryBytes > MAX_IMPORT_TEXT_TOTAL_BYTES - totalBytes) {
            throw new IOException("ZIP import text byte limit exceeded");
        }
        return totalBytes + entryBytes;
    }

    /**
     * True for the metadata macOS writes into ZIP archives: the {@code __MACOSX/} tree that
     * Finder's "Compress" adds, AppleDouble {@code ._<name>} resource-fork companions (also written
     * beside the real file by command-line archivers on a Mac), and Finder's {@code .DS_Store}.
     * Many published eForm packages were zipped on a Mac and carry these entries.
     *
     * <p>Skipping them only ever writes LESS than before: the dot-named ones were never importable
     * (the hidden-component check refuses a leading dot), and nothing under {@code __MACOSX/} is eForm
     * content. Every other hidden name, {@code .htaccess} for example, is still refused and still
     * fails the import.</p>
     *
     * @param entryName raw ZIP entry name, already path-validated by the caller
     * @return {@code true} when the entry is archiver metadata rather than eForm content
     */
    static boolean isArchiverMetadataEntry(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/")) {
            return true;
        }
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return baseName.startsWith("._") || ".DS_Store".equals(baseName);
    }

    private void deleteDirectory(File directory) {
        // Null-safe: this runs from importForm's finally, so a cleanup failure must never throw and
        // mask the real exception. listFiles() returns null when the dir is missing/unreadable.
        if (directory == null) {
            return;
        }
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (File file : contents) {
                file.delete();
            }
        }
        directory.delete();
    }

    public EForm createEFormFromProperties(Properties properties) throws Exception {
        EForm eForm = new EForm();
        eForm.setFormName(properties.getProperty("form.name"));
        if (eForm.getFormName() == null)
            throw new Exception("Error, form.name property cannot be found in eform.properties");
        eForm.setFormSubject(properties.getProperty("form.details"));
        // Validate the imported html filename as a single path component before persisting it: a
        // path-like value from a malicious eform.properties would otherwise be stored and later
        // re-exported as an unsafe ZIP entry name.
        String htmlFilename = properties.getProperty("form.htmlFilename");
        if (htmlFilename != null && !htmlFilename.isEmpty()) {
            htmlFilename = PathValidationUtils.validatePathComponent(htmlFilename, "eform import html filename");
        }
        eForm.setFormFileName(htmlFilename);
        eForm.setFormCreator(properties.getProperty("form.creator"));
        eForm.setFormDate(properties.getProperty("form.date"));
        eForm.setShowLatestFormOnly(Boolean.valueOf(properties.getProperty("form.showLatestFormOnly")));
        eForm.setPatientIndependent(Boolean.valueOf(properties.getProperty("form.patientIndependent")));
        return eForm;
    }

}
