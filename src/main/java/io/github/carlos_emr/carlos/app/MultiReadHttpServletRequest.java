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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpServletRequestWrapper that allows for multiple reads of multipart/form-data
 * and exposes multipart form fields via {@link #getParameter(String)} so that
 * CSRFGuard can extract the CSRF token from multipart POST requests.
 */
public class MultiReadHttpServletRequest extends HttpServletRequestWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiReadHttpServletRequest.class);

    private ByteArrayOutputStream cachedBytes;

    /** Parsed multipart form field parameters (lazy-initialized, null until parsed). */
    private Map<String, String[]> multipartParams;

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
     * Parses multipart form fields from the cached request body so that
     * {@link #getParameter(String)} can return values embedded in the multipart stream.
     * File upload items are skipped — only simple form fields are extracted.
     */
    private void parseMultipartParams() {
        if (multipartParams != null) {
            return;
        }
        multipartParams = new HashMap<>();
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(8192);
            ServletFileUpload upload = new ServletFileUpload(factory);
            List<FileItem> items = upload.parseRequest(this);
            Map<String, List<String>> temp = new HashMap<>();
            for (FileItem item : items) {
                if (item.isFormField()) {
                    String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
                    temp.computeIfAbsent(item.getFieldName(), k -> new ArrayList<>())
                            .add(item.getString(encoding));
                }
            }
            for (Map.Entry<String, List<String>> entry : temp.entrySet()) {
                multipartParams.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
        } catch (FileUploadException | IOException e) {
            LOGGER.warn("Failed to parse multipart form fields for CSRF token extraction — "
                    + "getParameter() will fall back to query string only", e);
            multipartParams = new HashMap<>();
        }
    }

    @Override
    public String getParameter(String name) {
        // Try the original (query string) first
        String value = super.getParameter(name);
        if (value != null) {
            return value;
        }
        // Fall back to multipart form fields
        parseMultipartParams();
        String[] values = multipartParams.get(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> combined = new HashMap<>(super.getParameterMap());
        parseMultipartParams();
        for (Map.Entry<String, String[]> entry : multipartParams.entrySet()) {
            combined.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(combined);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values != null) {
            return values;
        }
        parseMultipartParams();
        return multipartParams.get(name);
    }

    /* An inputstream which reads the cached request body */
    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        public CachedServletInputStream(ByteArrayOutputStream cachedBytes) {
            /* create a new input stream from the cached request body */
            input = new ByteArrayInputStream(cachedBytes.toByteArray());
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {

        }
    }
}