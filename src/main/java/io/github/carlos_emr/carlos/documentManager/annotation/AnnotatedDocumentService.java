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
package io.github.carlos_emr.carlos.documentManager.annotation;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Saves a provider's annotations as a <strong>new</strong> document in the chart.
 *
 * <p>The received document is a clinical record and is never modified. Composition runs
 * against a read-only copy, the result is filed as its own {@code document} row for the
 * same patient, and the source row and file are left exactly as they were. The new row's
 * {@code source} column records {@code annotated-copy-of:&lt;sourceDocNo&gt;} so the copy
 * can be traced back without a schema change.
 *
 * <p>Ordering matters here. Authorisation runs first, then the file is composed and
 * written, and only once bytes are safely on disk is the database row created. A crash
 * between the two leaves an orphaned file, which is harmless; the reverse order would
 * leave a row pointing at a file that does not exist, which surfaces to a clinician as a
 * missing document.
 *
 * <p>Because PDFBox parses attacker-controllable input — an inbound fax is whatever the
 * sender chose to transmit — composition is bounded by {@link #COMPOSE_TIMEOUT_SECONDS}
 * and the feature is offered only for documents within {@link #MAX_ANNOTATABLE_PAGES}
 * and {@link #MAX_ANNOTATABLE_BYTES}.
 *
 * @since 2026-09
 */
public class AnnotatedDocumentService {

    private static final Logger logger = MiscUtils.getLogger();

    /** Hard ceiling on one composition, after which the worker is cancelled. */
    public static final int COMPOSE_TIMEOUT_SECONDS = 60;

    /** Documents beyond this are viewable and faxable, but not annotatable. */
    public static final int MAX_ANNOTATABLE_PAGES = 200;

    /** Matches the multipart ceiling used elsewhere in the document module. */
    public static final long MAX_ANNOTATABLE_BYTES = 50L * 1024 * 1024;

    /** Retries before giving up on a unique destination name; a collision is already rare. */
    private static final int MAX_FILENAME_ATTEMPTS = 5;

    private static final String SOURCE_PREFIX = "annotated-copy-of:";
    private static final String ANNOTATED_SUFFIX = " (annotated)";
    private static final String DOCUMENT_DIR_LABEL = "DOCUMENT_DIR";
    private static final String SIGNATURE_PREFIX = "consult_sig_";

    /**
     * Shipped with the eForm assets. DejaVu covers the Latin Extended-A range the
     * French, Polish and Portuguese locales need; the PDF base-14 fonts do not.
     */
    private static final String FONT_RELATIVE_PATH =
            "library/eforms/dejavufonts/ttf/DejaVuSans.ttf";

    private final SecurityInfoManager securityInfoManager;
    private final AnnotatedDocumentComposer composer;
    private final String webappRoot;

    /**
     * @param webappRoot absolute path of the exploded web application, used to locate the
     *                   bundled DejaVu font. Supplied by the caller because this class is
     *                   deliberately free of any servlet dependency.
     */
    public AnnotatedDocumentService(SecurityInfoManager securityInfoManager,
                                    AnnotatedDocumentComposer composer,
                                    String webappRoot) {
        this.securityInfoManager = securityInfoManager;
        this.composer = composer;
        this.webappRoot = webappRoot;
    }

    /**
     * Composes and files the annotated copy.
     *
     * @param loggedInInfo the saving provider
     * @param sourceDocNo  the document being annotated
     * @param annotations  validated marks from {@link DocumentAnnotationParser}
     * @return the new document number
     * @throws SecurityException        if the provider lacks {@code _edoc} write, or access to
     *                                  the document's patient
     * @throws IllegalArgumentException if the document is missing, is not a PDF, or is beyond
     *                                  the annotatable limits
     * @throws IOException              if composition or the file write fails
     */
    public int save(LoggedInInfo loggedInInfo, int sourceDocNo,
                    List<DocumentAnnotationDto> annotations) throws IOException {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        EDoc source = EDocUtil.getDoc(String.valueOf(sourceDocNo));
        if (source == null || StringUtils.isBlank(source.getFileName())) {
            throw new IllegalArgumentException("The document could not be found.");
        }

        // The patient comes from the document's own module link, never from the request.
        // Trusting a submitted demographic would let a caller pair their own patient's
        // number with another patient's document.
        String demographicNo = StringUtils.trimToNull(source.getModuleId());
        if (demographicNo != null && !"0".equals(demographicNo)
                && !securityInfoManager.isAllowedAccessToPatientRecord(
                        loggedInInfo, Integer.parseInt(demographicNo))) {
            throw new SecurityException("Unauthorized access to patient record");
        }

        File documentDir = PathValidationUtils.resolveConfiguredDirectory(
                CarlosProperties.getInstance().getDocumentDirectory(), DOCUMENT_DIR_LABEL);
        File sourceFile = PathValidationUtils.validateExistingPath(
                new File(documentDir, source.getFileName()), documentDir);

        assertAnnotatable(source, sourceFile);

        Path readOnlyCopy = createPrivateWorkingCopy();
        byte[] composed;
        try {
            Files.copy(sourceFile.toPath(), readOnlyCopy,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            composed = composeBounded(readOnlyCopy, annotations,
                    signatureFor(loggedInInfo.getLoggedInProviderNo()), fontPath());
        } finally {
            Files.deleteIfExists(readOnlyCopy);
        }

        File target = createUniqueTarget(documentDir, composed);
        String newFileName = target.getName();

        int newDocNo;
        try {
            newDocNo = Integer.parseInt(EDocUtil.addDocumentSQL(buildCopy(source, newFileName,
                    loggedInInfo.getLoggedInProviderNo(), sourceDocNo)));
        } catch (RuntimeException e) {
            // The row is the thing that makes the file reachable; without it the bytes are
            // unreferenced PHI sitting in the document store.
            Files.deleteIfExists(target.toPath());
            throw e;
        }

        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENT,
                String.valueOf(newDocNo), null, demographicNo);
        logger.info("Annotated copy {} filed from document {} by provider {}",
                newDocNo, sourceDocNo, LogSafe.sanitize(loggedInInfo.getLoggedInProviderNo()));

        return newDocNo;
    }

    /**
     * Runs composition on its own thread with a hard deadline. A crafted PDF can drive a
     * parser into pathological work; without a ceiling that consumes a request thread
     * indefinitely.
     */
    private byte[] composeBounded(Path source, List<DocumentAnnotationDto> annotations,
                                  Path signature, Path font) throws IOException {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "annotated-document-composer");
            t.setDaemon(true);
            return t;
        })) {
            Callable<byte[]> task = () -> composer.compose(source, annotations, signature, font);
            Future<byte[]> future = executor.submit(task);
            try {
                return future.get(COMPOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IOException("Composing the annotated document took too long.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Composing the annotated document was interrupted.");
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException("The annotated document could not be composed.");
            }
        }
    }

    /**
     * Writes the composed bytes to a destination no other request can be holding.
     *
     * <p>A timestamp alone is not unique: two saves completing in the same millisecond would
     * pick the same name, and because a plain write truncates an existing file both document
     * rows would end up pointing at whichever request finished last. On a shared chart that
     * silently replaces one patient's document with another's. {@link StandardOpenOption#CREATE_NEW}
     * makes creation atomic, so a collision fails rather than overwrites, and the loop then
     * takes a fresh name.
     */
    private File createUniqueTarget(File documentDir, byte[] composed) throws IOException {
        for (int attempt = 0; attempt < MAX_FILENAME_ATTEMPTS; attempt++) {
            String candidate = PathValidationUtils.validateGeneratedFileName(
                    "document_" + System.currentTimeMillis() + "_"
                            + UUID.randomUUID().toString().replace("-", "") + "_annotated.pdf");
            File target = PathValidationUtils.validateGeneratedChildPath(candidate, documentDir);
            try {
                Files.write(target.toPath(), composed, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return target;
            } catch (FileAlreadyExistsException collision) {
                logger.warn("Annotated copy filename collided; retrying with a new name");
            }
        }
        throw new IOException("Could not allocate a filename for the annotated document.");
    }

    /**
     * Creates the working copy inside CARLOS' own temp root with owner-only permissions.
     *
     * <p>The default temp directory is shared, and this file holds a patient's document for the
     * duration of composition. Creating it with {@code rw-------} under the application temp
     * root keeps it out of reach of other accounts on the host, and the caller deletes it in a
     * finally block regardless of outcome.
     */
    private static Path createPrivateWorkingCopy() throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"),
                PathValidationUtils.APPLICATION_TEMP_ROOT_NAME);
        Files.createDirectories(root);
        try {
            return Files.createTempFile(root, "carlos-annotate-src-", ".pdf",
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException notPosix) {
            // Non-POSIX filesystem: fall back to a plain create inside the same private root.
            return Files.createTempFile(root, "carlos-annotate-src-", ".pdf");
        }
    }

    private void assertAnnotatable(EDoc source, File sourceFile) {
        if (!"application/pdf".equalsIgnoreCase(StringUtils.trimToEmpty(source.getContentType()))) {
            throw new IllegalArgumentException("Only PDF documents can be annotated.");
        }
        if (source.getNumberOfPages() > MAX_ANNOTATABLE_PAGES) {
            throw new IllegalArgumentException(
                    "Documents longer than " + MAX_ANNOTATABLE_PAGES + " pages cannot be annotated.");
        }
        if (sourceFile.length() > MAX_ANNOTATABLE_BYTES) {
            throw new IllegalArgumentException("This document is too large to annotate.");
        }
    }

    /**
     * Copies the filing attributes of the source so the annotated version lands in the same
     * place in the chart. The observation date is carried over deliberately: the clinical
     * date of the underlying report has not changed just because a provider marked it up.
     * Review state is left clear, so the copy does not inherit a sign-off it never received.
     */
    private EDoc buildCopy(EDoc source, String fileName, String providerNo, int sourceDocNo) {
        EDoc copy = new EDoc();
        copy.setFileName(fileName);
        copy.setDescription(StringUtils.trimToEmpty(source.getDescription()) + ANNOTATED_SUFFIX);
        copy.setType(source.getType());
        copy.setDocClass(source.getDocClass());
        copy.setDocSubClass(source.getDocSubClass());
        copy.setContentType("application/pdf");
        copy.setCreatorId(providerNo);
        copy.setResponsibleId(source.getResponsibleId());
        copy.setSource(SOURCE_PREFIX + sourceDocNo);
        copy.setSourceFacility(source.getSourceFacility());
        copy.setProgramId(source.getProgramId());
        copy.setStatus('A');
        copy.setDocPublic(source.getDocPublic());
        copy.setObservationDate(source.getObservationDate());
        copy.setNumberOfPages(source.getNumberOfPages());
        copy.setModule(source.getModule());
        copy.setModuleId(source.getModuleId());
        copy.setContentDateTime(new java.util.Date());
        copy.setDateTimeStampAsDate(new java.util.Date());
        return copy;
    }

    /**
     * @return the provider's stored stamp, or {@code null} when they have not set one.
     *         A null is only an error if the model actually contains a signature mark,
     *         which the composer decides.
     */
    private Path signatureFor(String providerNo) {
        try {
            File imageDir = PathValidationUtils.resolveConfiguredDirectory(
                    CarlosProperties.getInstance().getEformImageDirectory(), "EFORM_IMAGE_DIR");
            File stamp = PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validateGeneratedFileName(
                            SIGNATURE_PREFIX + providerNo + ".png"), imageDir);
            return stamp.isFile() ? stamp.toPath() : null;
        } catch (RuntimeException e) {
            logger.warn("Signature stamp directory could not be resolved for the annotation composer");
            return null;
        }
    }

    private Path fontPath() {
        if (StringUtils.isBlank(webappRoot)) {
            return null;
        }
        Path font = new File(webappRoot, FONT_RELATIVE_PATH).toPath();
        return Files.isReadable(font) ? font : null;
    }
}
