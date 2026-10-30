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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Puts the bundled font files on disk, verified, and publishes them as a {@link BundledFontSet}.
 *
 * <p>Flying Saucer registers a font from a file path, and DejaVu Sans ships inside the
 * {@code jasperreports-fonts} jar, so the four bundled fonts (DejaVu Sans regular and bold, WenQuanYi
 * Micro Hei and Symbola, the set {@code PdfFonts} uses) are written to a directory under
 * {@code java.io.tmpdir} on first use.</p>
 *
 * <ul>
 *   <li><b>One directory per font bundle.</b> It is named {@code carlos-pdf-fonts-} plus the first 12
 *       hex digits of the SHA-256 over the four fonts' bytes, so a restart reuses it (a directory per
 *       start leaked 14 MB each time). It is not under {@code carlos-temp}, which
 *       {@code ApplicationTempPurgeJob} ages out. Directories of other bundle versions are left alone,
 *       because another JVM sharing the temporary directory may be using one.</li>
 *   <li><b>Owner only.</b> The directory is created owner-only where POSIX permissions exist, and
 *       the files are written owner-only; that is the tamper control. A directory that already
 *       exists but is a symbolic link, belongs to another user or is writable by others is not
 *       used: this JVM uses a fresh private directory instead and logs a WARN, so another local user
 *       cannot block PDF generation.</li>
 *   <li><b>Verified.</b> On first use each file's SHA-256 is compared with the class-path bytes, and
 *       a file that is missing or different is written to a temporary name and moved into place
 *       atomically, under one lock.</li>
 *   <li><b>Checked before every render.</b> {@link #fontSet()} checks each file's existence and size
 *       (four {@code stat} calls); on a mismatch the verification and rewrite run again under the
 *       lock, so a file deleted or truncated after first use is restored by the next render. A file
 *       altered without changing its size is caught at the next first use, that is, at restart.</li>
 * </ul>
 *
 * <p>The verified state is published as one immutable {@link BundledFontSet} in a {@code volatile}
 * field, so a render always sees one complete, consistent set. The constructor does no I/O.</p>
 *
 * <p>Logs carry counts and a font's role, never a path.</p>
 *
 * @since 2026-10-29
 */
@Service
class BundledFontFileService {

    private static final Logger logger = MiscUtils.getLogger();

    /** The start of the shared directory's name, and of the private one used when it cannot be trusted. */
    static final String DIRECTORY_PREFIX = "carlos-pdf-fonts-";

    /** Hex digits of the bundle hash kept in the directory name: 48 bits, for one directory per bundle version. */
    private static final int HASH_HEX_DIGITS = 12;

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    /**
     * Held while font files are verified or written. It is static so that two beans in one class
     * loader (two Spring contexts) never write one directory at once; separate JVMs are protected by
     * the atomic move of complete files.
     */
    private static final ReentrantLock EXTRACTION_LOCK = new ReentrantLock();

    /**
     * The class-path fonts, in the order that is hashed into the directory name. Changing the order,
     * or a font, changes the directory name on purpose.
     */
    private static final List<Resource> RESOURCES = List.of(
            new Resource(Face.SANS_REGULAR, "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf", true),
            new Resource(Face.SANS_BOLD, "net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf", true),
            new Resource(Face.CJK, "fonts/wqy/wqy-microhei.ttc", false),
            new Resource(Face.SYMBOL, "fonts/symbola/Symbola_hint.ttf", false));

    private final Path tempRoot;
    private final ClassLoader resources;

    /** The last complete, verified state; replaced as a whole. */
    private volatile Verified verified;

    /** The user this process runs as, learned from a file it creates; guarded by {@link #EXTRACTION_LOCK}. */
    private UserPrincipal processUser;

    /** Production wiring: the JVM's temporary directory and this class's class loader. */
    @Autowired
    BundledFontFileService() {
        this(Path.of(System.getProperty("java.io.tmpdir")), BundledFontFileService.class.getClassLoader());
    }

    /**
     * For tests: a chosen temporary directory and the loader the fonts are read from.
     *
     * @param tempRoot the directory the font directory is created in
     * @param resources the class loader the bundled fonts are read from
     */
    BundledFontFileService(Path tempRoot, ClassLoader resources) {
        this.tempRoot = Objects.requireNonNull(tempRoot, "tempRoot");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /**
     * Returns the verified bundled fonts, writing them to disk first if they are not there. Call it
     * before every render: it is a few {@code stat} calls when nothing changed.
     *
     * @return the font set; the same instance until a file is found missing or resized
     * @throws PDFGenerationException if a DejaVu Sans font is missing from the application or the
     *         files cannot be written; the message names no path, and the next call tries again
     */
    BundledFontSet fontSet() throws PDFGenerationException {
        Verified current = verified;
        if (current != null && current.isIntact()) {
            return current.set();
        }
        EXTRACTION_LOCK.lock();
        try {
            current = verified;
            if (current != null && current.isIntact()) {
                return current.set();
            }
            Verified rebuilt = extract(current);
            verified = rebuilt;
            return rebuilt.set();
        } finally {
            EXTRACTION_LOCK.unlock();
        }
    }

    // ---- extraction (under the lock) --------------------------------------------------------

    private Verified extract(Verified previous) throws PDFGenerationException {
        // The class path is read before the disk is touched, so a missing DejaVu Sans creates nothing.
        List<ClassPathFont> bundle = readBundle();
        try {
            Path directory = selectDirectory(bundleHash(bundle), previous == null ? null : previous.directory());
            List<FontFile> files = new ArrayList<>();
            int created = 0;
            int replaced = 0;
            for (ClassPathFont font : bundle) {
                Path file = directory.resolve(font.resource.fileName());
                switch (place(file, font)) {
                    case CREATED -> created++;
                    case REPLACED -> replaced++;
                    case VERIFIED -> { }
                }
                files.add(new FontFile(font.resource.face(), file, font.bytes.length));
            }
            BundledFontSet set = BundledFontSet.load(
                    fileOf(files, Face.SANS_REGULAR), fileOf(files, Face.SANS_BOLD),
                    fileOf(files, Face.CJK), fileOf(files, Face.SYMBOL));
            if (replaced > 0) {
                logger.warn("{} bundled font file(s) differed from the application's and were rewritten", replaced);
            }
            if (created > 0) {
                if (previous == null) {
                    logger.info("{} bundled font file(s) written", created);
                } else {
                    logger.warn("{} bundled font file(s) had gone missing and were written again", created);
                }
            }
            return new Verified(set, directory, files);
        } catch (IOException e) {
            throw new PDFGenerationException("The bundled fonts could not be written to the temporary directory", e);
        }
    }

    /** Reads the class-path fonts; a DejaVu Sans face that cannot be read fails, a fallback is left out. */
    private List<ClassPathFont> readBundle() throws PDFGenerationException {
        List<ClassPathFont> bundle = new ArrayList<>();
        for (Resource resource : RESOURCES) {
            byte[] bytes = null;
            try (InputStream in = resources.getResourceAsStream(resource.name())) {
                if (in != null) {
                    bytes = in.readAllBytes();
                }
            } catch (IOException e) {
                bytes = null;
            }
            if (bytes == null) {
                if (resource.required()) {
                    throw new PDFGenerationException("A bundled font is missing from the application");
                }
                logger.warn("The optional bundled font {} is missing from the application; the characters it would"
                        + " draw print as U+FFFD", resource.face());
                continue;
            }
            bundle.add(new ClassPathFont(resource, bytes, sha256(bytes)));
        }
        return bundle;
    }

    /** The directory name suffix: the first 12 hex digits of the SHA-256 over the fonts' bytes, in order. */
    private static String bundleHash(List<ClassPathFont> bundle) {
        MessageDigest digest = newDigest();
        for (ClassPathFont font : bundle) {
            digest.update(font.bytes);
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, HASH_HEX_DIGITS);
    }

    /**
     * The directory the files go in: the one already used, the shared one for this bundle, or a
     * private one when the shared name is taken by something this process cannot trust.
     */
    private Path selectDirectory(String hash, Path previous) throws IOException {
        if (previous != null && isTrusted(previous)) {
            return previous;
        }
        Path shared = tempRoot.resolve(DIRECTORY_PREFIX + hash);
        if (!Files.exists(shared, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (supportsPosix(shared)) {
                    Files.createDirectory(shared, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
                } else {
                    Files.createDirectory(shared);
                }
            } catch (FileAlreadyExistsException createdByAnotherJvm) {
                // Judged below, like any directory that was there before.
            }
        }
        if (isTrusted(shared)) {
            return shared;
        }
        logger.warn("The shared bundled-font directory is a link, belongs to another user or is writable by"
                + " others; this JVM uses a private directory");
        return Files.createTempDirectory(tempRoot, DIRECTORY_PREFIX);
    }

    /**
     * Whether a directory is a real directory (not a link) owned by this process's user. Where POSIX
     * permissions exist, a directory of ours that others can reach is tightened to owner-only.
     */
    private boolean isTrusted(Path directory) throws IOException {
        // A symbolic link is not a directory when links are not followed.
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!supportsPosix(directory)) {
            return true;
        }
        if (!Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS).equals(processUser())) {
            return false;
        }
        if (!OWNER_ONLY.equals(Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS))) {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
        }
        return true;
    }

    /** The user this process runs as: the owner of a file it creates, whatever the user database says. */
    private UserPrincipal processUser() throws IOException {
        if (processUser == null) {
            Path probe = Files.createTempFile(tempRoot, DIRECTORY_PREFIX + "owner-", ".tmp");
            try {
                processUser = Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
            } finally {
                Files.deleteIfExists(probe);
            }
        }
        return processUser;
    }

    private static boolean supportsPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** Makes the file hold exactly the class-path bytes. */
    private static Placement place(Path file, ClassPathFont font) throws IOException {
        BasicFileAttributes attributes = null;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            // written below
        }
        if (attributes != null && attributes.isRegularFile() && attributes.size() == font.bytes.length
                && MessageDigest.isEqual(sha256(file), font.sha256)) {
            return Placement.VERIFIED;
        }
        write(file, font.bytes);
        return attributes == null ? Placement.CREATED : Placement.REPLACED;
    }

    /** Writes to a temporary name in the same directory, then moves the complete file into place. */
    private static void write(Path file, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path fileOf(List<FontFile> files, Face face) {
        return files.stream().filter(f -> f.face() == face).map(FontFile::file).findFirst().orElse(null);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        return newDigest().digest(bytes);
    }

    private static byte[] sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    // ---- value types ------------------------------------------------------------------------

    /** What {@link #place} had to do. */
    private enum Placement {
        VERIFIED, CREATED, REPLACED
    }

    /** A bundled font on the class path. */
    private record Resource(Face face, String name, boolean required) {
        String fileName() {
            return name.substring(name.lastIndexOf('/') + 1);
        }
    }

    /** A bundled font read from the class path. */
    private static final class ClassPathFont {
        final Resource resource;
        final byte[] bytes;
        final byte[] sha256;

        ClassPathFont(Resource resource, byte[] bytes, byte[] sha256) {
            this.resource = resource;
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    /** A font file in the directory, with the size it must keep. */
    private record FontFile(Face face, Path file, long size) {
        boolean isIntact() {
            try {
                BasicFileAttributes attributes =
                        Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return attributes.isRegularFile() && attributes.size() == size;
            } catch (IOException e) {
                return false;
            }
        }
    }

    /** One complete verified state. */
    private record Verified(BundledFontSet set, Path directory, List<FontFile> files) {
        Verified {
            files = List.copyOf(files);
        }

        boolean isIntact() {
            for (FontFile file : files) {
                if (!file.isIntact()) {
                    return false;
                }
            }
            return true;
        }
    }
}
