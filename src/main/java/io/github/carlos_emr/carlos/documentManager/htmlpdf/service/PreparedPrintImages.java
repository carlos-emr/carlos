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

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The files the image step wrote for one attempt (converted and cleaned images), in a private
 * temporary directory that exists only for that attempt. Closing deletes the directory, whatever
 * happened after it was made; use it in a {@code try}-with-resources around layout.
 *
 * <p>The directory is created owner-only by the platform and holds nothing the attempt did not
 * write. An attempt that converts no image has no directory: {@link #none()}.</p>
 *
 * @since 2026-10-29
 */
final class PreparedPrintImages implements AutoCloseable {

    private static final Logger logger = MiscUtils.getLogger();

    private static final PreparedPrintImages NONE = new PreparedPrintImages(null);

    private final Path directory;

    private PreparedPrintImages(Path directory) {
        this.directory = directory;
    }

    /**
     * No files were written.
     *
     * @return an instance without a directory, whose {@link #close()} does nothing
     */
    static PreparedPrintImages none() {
        return NONE;
    }

    /**
     * Takes ownership of a directory.
     *
     * @param directory the attempt's image directory, or {@code null} if no file was written
     * @return the holder; closing it deletes the directory ({@link #none()} for {@code null})
     */
    static PreparedPrintImages of(Path directory) {
        return directory == null ? NONE : new PreparedPrintImages(directory);
    }

    /**
     * The directory the files are in, to be added to the renderer's allowed directories.
     *
     * @return the directory, or {@code null} if no file was written
     */
    Path directory() {
        return directory;
    }

    /** Deletes the directory and everything in it. Safe to call again; a failure is logged without the path and ignored. */
    @Override
    public void close() {
        deleteRecursively(directory);
    }

    /** Deletes a directory tree without following links; a failure is logged by class name only. */
    static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (NoSuchFileException alreadyGone) {
            // Nothing left to delete.
        } catch (IOException | RuntimeException e) {
            logger.warn("The temporary image directory of a render could not be deleted ({})", e.getClass().getSimpleName());
        }
    }
}
