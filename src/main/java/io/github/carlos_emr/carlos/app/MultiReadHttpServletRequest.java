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
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.app;

import org.apache.commons.io.IOUtils;


import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * {@link HttpServletRequestWrapper} that caches the request body to allow multiple reads
 * of multipart/form-data and other request content.
 *
 * <p>Used by {@link CarlosCsrfGuardFilter} to allow CSRFGuard token extraction and
 * downstream servlet file upload processing to both read the request input stream.
 * Without this wrapper, the first read would consume the stream, making it unavailable
 * for subsequent reads.
 *
 * <p>Enforces a maximum body size of {@link #MAX_BODY_SIZE} (500 MB) to prevent
 * memory exhaustion from oversized uploads.
 *
 * @since 2026-03-17
 */
public class MultiReadHttpServletRequest extends HttpServletRequestWrapper {
    private ByteArrayOutputStream cachedBytes;

    /** Maximum request body size (500 MB) to prevent memory exhaustion. Matches struts.multipart.maxSize. */
    static final long MAX_BODY_SIZE = 500L * 1024 * 1024;

    /**
     * Wraps the given request to allow multiple reads of the body.
     *
     * @param request the original HTTP servlet request
     */
    public MultiReadHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    /**
     * Returns a new {@link ServletInputStream} over the cached request body, allowing
     * the body to be read multiple times.
     *
     * @return a {@link ServletInputStream} backed by the cached request body
     * @throws IOException if caching the request body fails or the body exceeds {@link #MAX_BODY_SIZE}
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cachedBytes == null) {
            cacheInputStream();
        }

        return new CachedServletInputStream(cachedBytes);
    }

    /**
     * Returns a {@link BufferedReader} over the cached request body using the request's
     * declared character encoding, or {@code ISO-8859-1} if no encoding is specified.
     *
     * @return a {@link BufferedReader} for reading the cached request body
     * @throws IOException if caching the request body fails or the body exceeds {@link #MAX_BODY_SIZE}
     */
    @Override
    public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            encoding = "ISO-8859-1";
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
    }

    /**
     * Reads and caches the entire request input stream into {@code cachedBytes}.
     *
     * @throws IOException if the stream cannot be read or the body exceeds {@link #MAX_BODY_SIZE}
     */
    private void cacheInputStream() throws IOException {
        cachedBytes = new ByteArrayOutputStream();
        try {
            ServletInputStream input = super.getInputStream();
            long copied = IOUtils.copyLarge(input, cachedBytes, 0, MAX_BODY_SIZE);
            if (copied >= MAX_BODY_SIZE && input.read() != -1) {
                cachedBytes = null;
                throw new IOException("Request body exceeds maximum allowed size of "
                        + (MAX_BODY_SIZE / (1024 * 1024)) + " MB");
            }
        } catch (IOException e) {
            cachedBytes = null;
            throw e;
        }
    }

    /**
     * {@link ServletInputStream} implementation that reads from a cached byte array,
     * allowing the request body to be re-read after initial consumption.
     */
    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        /**
         * Creates a new input stream backed by the given cached bytes.
         *
         * @param cachedBytes ByteArrayOutputStream the cached request body
         */
        public CachedServletInputStream(ByteArrayOutputStream cachedBytes) {
            /* create a new input stream from the cached request body */
            input = new ByteArrayInputStream(cachedBytes.toByteArray());
        }

        /**
         * Reads the next byte from the cached request body.
         *
         * @return int the next byte of data, or {@code -1} if the end of the stream is reached
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read() throws IOException {
            return input.read();
        }

        /**
         * Returns whether all cached bytes have been read.
         *
         * @return boolean {@code true} if no more bytes are available
         */
        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        /**
         * Returns whether data is available for reading without blocking.
         * Always returns {@code true} since the data is cached in memory.
         *
         * @return boolean always {@code true}
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * Sets a read listener for async I/O. Not implemented because this
         * stream is backed by an in-memory byte array and never blocks.
         *
         * @param readListener ReadListener the listener to register (ignored)
         */
        @Override
        public void setReadListener(ReadListener readListener) {

        }
    }
}