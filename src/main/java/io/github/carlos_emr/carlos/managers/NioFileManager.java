/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.CarlosProperties;

/**
 * the NioFileManager handles all file input and output of all OscarDocument files
 * by providing several convenience utilities.
 * <p>
 * One goal is to eliminate the use of "CarlosProperties.getInstance().getProperty("DOCUMENT_DIR")"
 * in every single page of OSCAR code.
 */
public interface NioFileManager {
    public static final String DOCUMENT_DIRECTORY = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

    public Path hasCacheVersion2(LoggedInInfo loggedInInfo, String filename, Integer pageNum);

    public Path getDocumentCacheDirectory(LoggedInInfo loggedInInfo);

    /**
     * First checks to see if a cache version is already available.  If one is not available then a
     * new cached version is created.
     * <p>
     * Returns a file path to the cached version of the given PDF
     */
    public Path createCacheVersion2(LoggedInInfo loggedInInfo, String sourceDirectory, String filename, Integer pageNum);

    /**
     * Remove the given file from the cache directory.
     * This is highly recommended function for temporary document preview images.
     *
     * @param loggedInInfo
     * @param fileName
     */
    public boolean removeCacheVersion(LoggedInInfo loggedInInfo, final String fileName);

    /**
     * Remove every cached page image produced by {@link #createCacheVersion2} for one source PDF.
     * Page images are named {@code <boundedFilename>_<sourceKey>_<page>.png}, so callers that only hold
     * the PDF's path (e.g. the fax-preview flush) cannot clear them with the single-file
     * {@link #removeCacheVersion}; this rebuilds the same source-scoped prefix and removes all pages.
     *
     * @param loggedInInfo caller identity, logged at DEBUG only; unlike {@link #removeCacheVersion}
     *                     this method applies no privilege gate of its own — the caller's
     *                     {@code _fax} READ check is the authorization boundary
     * @param sourceDirectory the directory the PDF was previewed from (its parent directory)
     * @param filename        the PDF filename
     * @return the number of cache page images removed
     * @throws IOException when the cache directory cannot be enumerated, or when one or more
     *         matching page images could not be removed after a best-effort pass over all of
     *         them — cached preview pages are PHI-bearing, so a caller must not report a
     *         successful flush while any remain on disk
     * @throws IllegalArgumentException when {@code sourceDirectory} is not an allowed preview
     *         source (not a real, existing CARLOS-owned temp subtree or the document root) and so
     *         cannot be keyed. The source-scoped page prefix is then underivable, so the flush
     *         cannot verify the PHI preview pages are gone; the caller must treat this as an
     *         uncleared cache, not as "nothing to remove"
     */
    public int removeCacheVersions(LoggedInInfo loggedInInfo, String sourceDirectory, String filename) throws IOException;

    /**
     * Save a file to the temporary directory from ByteArrayOutputStream
     *
     * @throws IOException
     */
    public Path saveTempFile(final String fileName, ByteArrayOutputStream os, String fileType) throws IOException;

    public Path saveTempFile(final String fileName, ByteArrayOutputStream os) throws IOException;

    /**
     * Delete a temp file. Do this often.
     *
     * @param fileName
     */
    public boolean deleteTempFile(final String fileName);

    /**
     * retrieve given filename from Oscar's document directory path as defined in
     * Oscar properties.
     * Filename string in File out
     */
    public File getOscarDocument(String fileName);

    /**
     * Path NIO object in Path out.
     * The incoming path could have been derived from a temporary file.
     */
    public Path getOscarDocument(Path fileNamePath);

    /**
     * Atomically promotes a regular application-owned temporary file into document storage.
     * The source remains intact so callers can retry safely.
     */
    Path promoteApplicationTempFile(Path source) throws FilePromotionException;

    /**
     * True if given filename exists in OscarDocument directory.
     * False if file not found.
     */
    public boolean isOscarDocument(String fileName);

    public Path createTempFile(final String fileName, ByteArrayOutputStream os) throws IOException;

}
 
