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
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.eform.upload;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.eform.EFormImagePublicationLock;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class ImageUpload2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String INVALID_FILENAME_MESSAGE_KEY = "dms.error.invalidFilename";

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
        if (uploadValidationError != null) {
            addActionError(uploadValidationError);
            return ERROR;
        }

        try {
            if (imageFileName == null || imageFileName.isEmpty()) {
                MiscUtils.getLogger().warn("Image upload rejected: no filename provided");
                addActionError("Upload failed: no file was selected.");
                return ERROR;
            }

            // Sanitize the filename and track if it changed. Compare against the name the browser
            // actually sent (captured in withUploadedFiles before its own normalization), not
            // against the already-normalized field — normalization is idempotent, so comparing the
            // field with itself can never report a rename.
            String originalFileName =
                    uploadedOriginalFileName != null ? uploadedOriginalFileName : imageFileName;
            imageFileName = PathValidationUtils.validateFileName(imageFileName);
            boolean fileNameWasSanitized = !originalFileName.equals(imageFileName);

            // Ensure upload directory exists (throws IOException if creation fails)
            File imageFolder = getImageFolder();
            if (!imageFolder.exists()) {
                MiscUtils.getLogger().error("Image folder does not exist after creation attempt: {}", imageFolder.getAbsolutePath());
                addActionError("Upload failed: unable to prepare upload directory");
                return ERROR;
            }

            // Wait for any provisional ZIP publication/rollback before replacing this image.
            // Otherwise a successful in-place upload could keep the staged inode and be
            // removed by that import's later rollback.
            try (var _ = EFormImagePublicationLock.acquire(imageFolder)) {
                File destinationFile = PathValidationUtils.validateUpload(image, imageFileName, imageFolder);
                var destination = destinationFile.toPath();
                boolean linux = "Linux".equals(System.getProperty("os.name"));
                // Atomic rename checks the directory, so retain the former target-write refusal.
                // Do this under the shared publication lease, before allocating/copying staging.
                if (Files.exists(destination)) {
                    if (!Files.isWritable(destination)) {
                        throw new java.nio.file.AccessDeniedException(destination.toString(), null,
                                "Existing image is not writable");
                    }
                    if (linux && !Files.isReadable(destination)) {
                        addActionError("Replacing this image requires read and write access to preserve its permissions and attributes.");
                        return ERROR;
                    }
                }
                FileStore store = Files.getFileStore(imageFolder.toPath());
                boolean posix = store.supportsFileAttributeView(PosixFileAttributeView.class);
                // A private directory makes the staged file inaccessible from its creation.
                // Normal file creation inside it retains the process umask for a new image.
                var stagingDirectory = posix
                        ? Files.createTempDirectory(imageFolder.toPath(), ".carlos-image-",
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                        : Files.createTempDirectory(imageFolder.toPath(), ".carlos-image-");
                try (var cleanup = new StagingCleanup(stagingDirectory)) {
                    var staged = cleanup.staged;
                    try (InputStream fis = PathValidationUtils.openValidatedUploadInputStream(image);
                         OutputStream fos = Files.newOutputStream(staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                    }
                    preserveReplacementMetadata(destination, staged, store, posix, linux);
                    Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    cleanup.published = true;
                }
            }

            // Pass sanitization info to JSP for display in success message
            if (fileNameWasSanitized) {
                request.setAttribute("sanitizedFileName", imageFileName);
            }
            request.setAttribute("status", "uploaded");
            return SUCCESS;

        } catch (FileValidationException e) {
            MiscUtils.getLogger().warn("Rejected invalid image upload filename");
            addActionError(getInvalidFilenameMessage());
            return ERROR;
        } catch (SecurityException se) {
            MiscUtils.getLogger().warn("SecurityException during image upload: " + se.getMessage(), se);
            addActionError("Upload failed: invalid file or security policy violation");
            return ERROR;
        } catch (IOException e) {
            MiscUtils.getLogger().error("Image upload failed for file: {}", imageFileName, e);
            addActionError("Upload failed: an error occurred while saving the image. Please try again or contact support.");
            return ERROR;
        }
    }

    private static void preserveReplacementMetadata(Path existing, Path staged, FileStore store, boolean posix,
            boolean linux) throws IOException {
        if (Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Existing eForm image must be a regular file");
        }
        if (linux) {
            // GNU cp can copy named POSIX ACLs, which the Linux NIO provider does not expose.
            // Explicit preservation options cause copy failures to fail the upload.
            copyLinuxMetadata(existing, staged);
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("eForm image metadata staging must retain a regular file");
            }
            if (posix) {
                var original = Files.readAttributes(existing, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                var replacement = Files.readAttributes(staged, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                // GNU cp may skip ownership changes that the current user cannot make.
                if (!original.owner().equals(replacement.owner()) || !original.group().equals(replacement.group())
                        || !original.permissions().equals(replacement.permissions())) {
                    throw new IOException("Unable to retain existing eForm image ownership and permissions");
                }
            }
            return;
        }
        if (store.supportsFileAttributeView(UserDefinedFileAttributeView.class)) {
            var source = Files.getFileAttributeView(existing, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            var target = Files.getFileAttributeView(staged, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            for (String name : source.list()) {
                int size = source.size(name);
                var value = ByteBuffer.allocate(size);
                if (source.read(name, value) != size || target.write(name, value.flip()) != size) {
                    throw new IOException("Unable to retain an existing eForm image attribute");
                }
            }
        }
        if (posix) {
            var original = Files.readAttributes(existing, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            var replacement = Files.getFileAttributeView(staged, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            replacement.setPermissions(original.permissions());
            if (!original.group().equals(replacement.readAttributes().group())) replacement.setGroup(original.group());
        }
        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            var source = Files.getFileAttributeView(existing, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            var target = Files.getFileAttributeView(staged, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            target.setAcl(source.getAcl());
        }
        if (store.supportsFileAttributeView(FileOwnerAttributeView.class)) {
            var owner = Files.getOwner(existing, LinkOption.NOFOLLOW_LINKS);
            if (!owner.equals(Files.getOwner(staged, LinkOption.NOFOLLOW_LINKS))) Files.setOwner(staged, owner);
        }
    }

    private static void copyLinuxMetadata(Path existing, Path staged) throws IOException {
        // Both paths are contained in the image directory, and the staged path is private.
        Process copy = new ProcessBuilder("/usr/bin/cp", "--attributes-only", "--preserve=mode,ownership,xattr",
                "--no-dereference", "--", existing.toAbsolutePath().toString(), staged.toAbsolutePath().toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (!copy.waitFor(30, TimeUnit.SECONDS) || copy.exitValue() != 0) {
                throw new IOException("Unable to retain existing eForm image access metadata");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retaining existing eForm image access metadata", interrupted);
        } finally {
            if (copy.isAlive()) copy.destroyForcibly();
        }
    }

    /** Cleanup failure after atomic publication cannot make a completed upload retryable. */
    private static final class StagingCleanup implements java.io.Closeable {
        private final java.nio.file.Path directory;
        private final java.nio.file.Path staged;
        private boolean published;

        private StagingCleanup(java.nio.file.Path directory) {
            this.directory = directory;
            this.staged = directory.resolve("image");
        }

        @Override
        public void close() throws IOException {
            // Before publication, try-with-resources preserves the original copy failure
            // and suppresses cleanup failures; both deletion attempts still run.
            try (java.io.Closeable directoryCleanup = () -> Files.deleteIfExists(directory)) {
                Files.deleteIfExists(staged);
            } catch (IOException | SecurityException failure) {
                if (!published) throw failure;
                MiscUtils.getLogger().warn("Image upload succeeded but temporary staging cleanup failed", failure);
            }
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static File getImageFolder() throws IOException {
        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory() + "/");
        if (!imageFolder.exists() && !imageFolder.mkdirs())
            throw new IOException("Could not create directory " + imageFolder.getAbsolutePath() + " check permissions and ensure the correct EFORM_IMAGES_DIR property is set in the properties file");
        return imageFolder;
    }

    private String getInvalidFilenameMessage() {
        try {
            return ResourceBundle.getBundle("oscarResources", request.getLocale())
                    .getString(INVALID_FILENAME_MESSAGE_KEY);
        } catch (MissingResourceException e) {
            return "Invalid filename";
        }
    }

    private File image;
    private String uploadValidationError;

    /**
     * Receives uploaded files from the Struts 7.x {@code ActionFileUploadInterceptor}.
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.image = PathValidationUtils.validateUploadContent(uploaded.getContent());
            this.imageFileContentType = uploaded.getContentType();
            try {
                // Keep the browser-supplied name before any normalization. validateStrictFileName
                // ends in validateFileName, which already applies the character normalization, so
                // execute() cannot detect a rename by comparing against the value stored here —
                // its second validateFileName call is idempotent and the comparison was always
                // false, silently leaving the admin with a renamed file and a form that no longer
                // resolves it.
                this.uploadedOriginalFileName = uploaded.getOriginalName();
                this.imageFileName = PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
            } catch (FileValidationException e) {
                this.uploadValidationError = getInvalidFilenameMessage();
                this.imageFileName = null;
            }
        }
    }

    public File getImage() {
        return image;
    }

    public void setImage(File image) {
        this.image = image;
    }

    private String imageFileName;
    private String imageFileContentType;
    /**
     * The filename exactly as the browser supplied it, captured before normalization so a rename
     * can be reported. Null when the name arrived through {@link #setImageFileName} rather than the
     * upload interceptor, in which case that value is itself the pre-normalization original.
     */
    private String uploadedOriginalFileName;

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public void setImageFileContentType(String imageFileContentType) {
        this.imageFileContentType = imageFileContentType;
    }
}
