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
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.UploadContext;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HttpServletRequestWrapper that allows for multiple reads of multipart/form-data.
 *
 * <p>Also parses multipart text form fields so that {@link #getParameter(String)} works
 * at the filter level. Without this, Tomcat 11 returns {@code null} for
 * {@code getParameter()} on multipart requests processed outside a
 * {@code @MultipartConfig}-annotated servlet — which breaks CSRFGuard token extraction.</p>
 *
 * <p>Overrides {@link #getParts()} and {@link #getPart(String)} so that servlet-based
 * upload endpoints (which call {@code request.getParts()} directly) receive properly
 * parsed parts from the cached body. Without this override, {@code getParts()} delegates
 * to the original Tomcat {@code Request} which tries to read the already-consumed
 * stream and returns an empty collection or fails.</p>
 */
public class MultiReadHttpServletRequest extends HttpServletRequestWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiReadHttpServletRequest.class);

    private ExposedByteArrayOutputStream cachedBytes;

    /** Maximum request body size (500 MB) to prevent memory exhaustion. Matches struts.multipart.maxSize. */
    static final long MAX_BODY_SIZE = 500L * 1024 * 1024;

    /** Lazily parsed multipart text form field parameters. {@code null} means not yet parsed. */
    private Map<String, List<String>> multipartParams;

    /** Lazily parsed multipart parts for {@link #getParts()}. {@code null} means not yet parsed. */
    private Collection<Part> cachedParts;

    private static final Pattern FIELD_NAME_PATTERN =
            Pattern.compile("\\bname=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static final byte[] HEADER_BODY_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

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
     * Returns the value of a request parameter. For multipart/form-data requests, this
     * parses text form fields from the cached body since Tomcat 11 does not make them
     * available via {@code getParameter()} at the filter level.
     *
     * @param name the parameter name
     * @return the first value, or {@code null} if not found
     */
    @Override
    public String getParameter(String name) {
        // Check multipart-parsed params first (these are invisible to the wrapped request)
        Map<String, List<String>> parsed = getParsedMultipartParams();
        List<String> values = parsed.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, List<String>> parsed = getParsedMultipartParams();
        if (parsed.isEmpty()) {
            return super.getParameterMap();
        }
        // Multipart params first (consistent with getParameter() priority), then wrapped request
        Map<String, String[]> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
            merged.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        for (Map.Entry<String, String[]> entry : super.getParameterMap().entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(merged);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        Map<String, List<String>> parsed = getParsedMultipartParams();
        if (parsed.isEmpty()) {
            return super.getParameterNames();
        }
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> origNames = super.getParameterNames();
        while (origNames.hasMoreElements()) {
            names.add(origNames.nextElement());
        }
        names.addAll(parsed.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public String[] getParameterValues(String name) {
        Map<String, List<String>> parsed = getParsedMultipartParams();
        List<String> values = parsed.get(name);
        if (values != null) {
            return values.toArray(new String[0]);
        }
        return super.getParameterValues(name);
    }

    // ------------------------------------------------------------------
    // getParts() / getPart() — multipart Part parsing from cached body
    // ------------------------------------------------------------------

    /**
     * Returns all parts of the multipart request, parsed from the cached body.
     *
     * <p>Uses Tomcat's bundled {@code FileUpload} parser (the same parser Tomcat uses
     * internally for {@code @MultipartConfig} servlets) to parse parts from the cached
     * bytes, then wraps each {@code FileItem} in a {@link FileItemPart} adapter.</p>
     *
     * @return an unmodifiable collection of all parts
     * @throws IOException if caching or parsing the request body fails
     * @throws ServletException if the multipart parsing fails
     */
    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        if (cachedParts != null) {
            return cachedParts;
        }

        String contentType = getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return super.getParts();
        }

        if (cachedBytes == null) {
            try {
                cacheInputStream();
            } catch (IOException e) {
                LOGGER.error("Failed to cache multipart input stream for getParts() "
                        + "(URI: {}). File upload will fail for this request.",
                        getRequestURI(), e);
                throw e;
            }
        }

        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(1024 * 1024); // 1 MB — matches web.xml file-size-threshold

            FileUpload upload = new FileUpload();
            upload.setFileItemFactory(factory);
            // Uses MAX_BODY_SIZE (500 MB, matching struts.multipart.maxSize) rather than
            // web.xml's 50 MB, since this wrapper serves all multipart requests including
            // Struts-routed uploads with higher limits.
            upload.setFileSizeMax(MAX_BODY_SIZE);
            upload.setSizeMax(MAX_BODY_SIZE);

            // Use the internal buffer directly (no copy). The buffer is effectively
            // immutable after cacheInputStream() completes — no further writes occur.
            final byte[] buf = cachedBytes.getBuffer();
            final int count = cachedBytes.getCount();

            UploadContext ctx = new UploadContext() {
                @Override
                public String getCharacterEncoding() {
                    return MultiReadHttpServletRequest.this.getCharacterEncoding();
                }

                @Override
                public String getContentType() {
                    return MultiReadHttpServletRequest.this.getContentType();
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(buf, 0, count);
                }

                @Override
                public long contentLength() {
                    return count;
                }
            };

            List<FileItem> fileItems = upload.parseRequest(ctx);
            List<Part> parts = new ArrayList<>(fileItems.size());
            for (FileItem item : fileItems) {
                parts.add(new FileItemPart(item));
            }
            cachedParts = Collections.unmodifiableList(parts);
        } catch (FileUploadException e) {
            LOGGER.error("Failed to parse multipart parts from cached bytes (URI: {}, bodySize: {} bytes)",
                    getRequestURI(), cachedBytes.size(), e);
            throw new ServletException("Failed to parse multipart request from cached bytes", e);
        }

        return cachedParts;
    }

    /**
     * Returns the named part of the multipart request.
     *
     * @param name the part name (form field name)
     * @return the part, or {@code null} if not found
     * @throws IOException if caching or parsing the request body fails
     * @throws ServletException if the multipart parsing fails
     */
    @Override
    public Part getPart(String name) throws IOException, ServletException {
        for (Part part : getParts()) {
            if (Objects.equals(part.getName(), name)) {
                return part;
            }
        }
        return null;
    }

    /**
     * Lazily parses multipart text form fields from the cached body.
     * Triggers input stream caching if not already done (CSRFGuard calls
     * {@code getParameter()} before any stream reads).
     *
     * @return parsed parameters (never {@code null}; empty map for non-multipart requests)
     */
    private Map<String, List<String>> getParsedMultipartParams() {
        if (multipartParams != null) {
            return multipartParams;
        }

        String contentType = getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            multipartParams = Collections.emptyMap();
            return multipartParams;
        }

        // Ensure the input stream has been cached -- getParameter() may be called before
        // getInputStream(), e.g. by CSRFGuard's CsrfValidator at the filter level.
        if (cachedBytes == null) {
            try {
                cacheInputStream();
            } catch (IOException e) {
                LOGGER.error("Failed to cache multipart input stream for parameter extraction "
                        + "(URI: {}). CSRF token extraction will fail for this request.",
                        getRequestURI(), e);
                multipartParams = Collections.emptyMap();
                return multipartParams;
            }
        }

        try {
            multipartParams = parseMultipartFormFields(cachedBytes.getBuffer(), cachedBytes.getCount(), contentType);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse multipart form fields", e);
            multipartParams = Collections.emptyMap();
        }
        return multipartParams;
    }

    private void cacheInputStream() throws IOException {
        cachedBytes = new ExposedByteArrayOutputStream();
        try {
            ServletInputStream input = super.getInputStream();
            long copied = IOUtils.copyLarge(input, cachedBytes, 0, MAX_BODY_SIZE);
            if (copied >= MAX_BODY_SIZE && input.read() != -1) {
                cachedBytes = null;
                throw new IOException("Request body exceeds maximum allowed size of "
                        + (MAX_BODY_SIZE / (1024 * 1024)) + " MB");
            }
            cachedBytes.freeze();
        } catch (IOException e) {
            cachedBytes = null;
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Multipart form field parser
    // ------------------------------------------------------------------

    /**
     * Parses text form fields (fields without a {@code filename} attribute) from a
     * multipart/form-data body. File upload parts are skipped.
     *
     * <p>This exists because Tomcat 11 does not expose multipart form fields via
     * {@code getParameter()} at the filter level (requires {@code @MultipartConfig}
     * on the target servlet). CSRFGuard calls {@code getParameter(tokenName)} to
     * validate tokens, so the CSRF token embedded in the multipart body must be
     * extractable.</p>
     *
     * @param body        the raw request body bytes (may be larger than {@code bodyLength})
     * @param bodyLength  the number of valid bytes in {@code body}
     * @param contentType the Content-Type header value (must start with "multipart/")
     * @return a map of field name to list of values (text fields only)
     */
    static Map<String, List<String>> parseMultipartFormFields(byte[] body, int bodyLength, String contentType) {
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> params = new LinkedHashMap<>();
        byte[] boundaryMarker = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        int pos = indexOf(body, bodyLength, boundaryMarker, 0);
        if (pos < 0) {
            return Collections.emptyMap();
        }

        while (pos >= 0) {
            pos += boundaryMarker.length;
            if (pos + 2 > bodyLength) {
                break;
            }

            // Final boundary ends with "--"
            if (body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }

            // Skip CRLF after boundary marker
            if (pos + 1 < bodyLength && body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            }

            // Find the blank line separating headers from body
            int headerEnd = indexOf(body, bodyLength, HEADER_BODY_SEPARATOR, pos);
            if (headerEnd < 0) {
                break;
            }

            String headers = new String(body, pos, headerEnd - pos, StandardCharsets.ISO_8859_1);
            int bodyStart = headerEnd + HEADER_BODY_SEPARATOR.length;

            // Find the next boundary
            int nextBoundary = indexOf(body, bodyLength, boundaryMarker, bodyStart);
            if (nextBoundary < 0) {
                break;
            }

            // Part body ends before the CRLF that precedes the next boundary
            int bodyEnd = nextBoundary - 2;
            if (bodyEnd < bodyStart) {
                bodyEnd = bodyStart;
            }

            // Only extract text fields (parts WITHOUT a filename attribute)
            String name = extractFieldName(headers);
            if (name != null && !hasFilename(headers)) {
                String value = new String(body, bodyStart, bodyEnd - bodyStart, StandardCharsets.UTF_8);
                params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }

            pos = nextBoundary;
        }

        return params;
    }

    /**
     * Extracts the boundary parameter from a multipart Content-Type header.
     *
     * @param contentType e.g. {@code "multipart/form-data; boundary=----WebKit..."}
     * @return the boundary string, or {@code null} if not found
     */
    private static String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String boundary = trimmed.substring("boundary=".length()).trim();
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    /**
     * Extracts the {@code name} attribute from a Content-Disposition header.
     *
     * @param headers the raw part headers
     * @return the field name, or {@code null} if not found
     */
    private static String extractFieldName(String headers) {
        Matcher matcher = FIELD_NAME_PATTERN.matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Checks whether the part headers contain a {@code filename} attribute,
     * indicating a file upload rather than a text form field.
     */
    private static boolean hasFilename(String headers) {
        return headers.toLowerCase(Locale.ROOT).contains("filename=");
    }

    /**
     * Finds the first occurrence of {@code target} in {@code source} starting from {@code fromIndex}.
     *
     * @param source       the byte array to search in
     * @param sourceLength the number of valid bytes in {@code source}
     * @param target       the byte sequence to search for
     * @param fromIndex    the index to start searching from
     * @return the index of the first match, or {@code -1} if not found
     */
    private static int indexOf(byte[] source, int sourceLength, byte[] target, int fromIndex) {
        int searchLimit = sourceLength - target.length;
        for (int i = fromIndex; i <= searchLimit; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A {@link ByteArrayOutputStream} subclass that exposes its internal buffer and byte
     * count without copying. This allows zero-copy reads after the stream has been fully
     * written.
     *
     * <p>The buffer is shared — callers must not modify it. After {@link #freeze()} is
     * called, all mutating methods ({@code write}, {@code reset}) throw
     * {@link IllegalStateException}, enforcing the immutability contract at runtime.</p>
     *
     * <p>{@code cacheInputStream()} calls {@code freeze()} once writing is complete.
     * After that point, the buffer and count are stable and safe for concurrent readers.</p>
     */
    static class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

        private boolean frozen;

        /** Freezes this stream, preventing any further writes or resets. */
        void freeze() {
            this.frozen = true;
        }

        /** Returns the internal byte buffer (may be larger than {@link #getCount()}). */
        byte[] getBuffer() {
            return buf;
        }

        /** Returns the number of valid bytes in {@link #getBuffer()}. */
        int getCount() {
            return count;
        }

        @Override
        public synchronized void write(int b) {
            if (frozen) {
                throw new IllegalStateException("Buffer is frozen after cacheInputStream()");
            }
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (frozen) {
                throw new IllegalStateException("Buffer is frozen after cacheInputStream()");
            }
            super.write(b, off, len);
        }

        @Override
        public void reset() {
            if (frozen) {
                throw new IllegalStateException("Buffer is frozen after cacheInputStream()");
            }
            super.reset();
        }
    }

    /* An inputstream which reads the cached request body */
    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        public CachedServletInputStream(ExposedByteArrayOutputStream cachedBytes) {
            // Use the internal buffer directly (no copy). The offset+length form of
            // ByteArrayInputStream avoids allocating a trimmed copy of the buffer.
            input = new ByteArrayInputStream(cachedBytes.getBuffer(), 0, cachedBytes.getCount());
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

    /**
     * Adapts a Tomcat {@link FileItem} to the {@link Part} interface.
     *
     * <p>Used by {@link #getParts()} to wrap multipart parts parsed from the cached
     * request body. Methods delegate to the underlying {@code FileItem}, with minor
     * adaptations for API signature differences and null safety.</p>
     *
     * <p><strong>Naming inversion warning:</strong> {@code FileItem.getName()} returns
     * the submitted filename, while {@code Part.getName()} returns the form field name.
     * This adapter maps them correctly but the crossed naming is a maintenance hazard.</p>
     */
    private static class FileItemPart implements Part {

        private final FileItem fileItem;

        FileItemPart(FileItem fileItem) {
            this.fileItem = Objects.requireNonNull(fileItem, "fileItem must not be null");
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return fileItem.getInputStream();
        }

        @Override
        public String getContentType() {
            return fileItem.getContentType();
        }

        /** Returns the form field name ({@code FileItem} calls this {@code getFieldName()}). */
        @Override
        public String getName() {
            return fileItem.getFieldName();
        }

        /** Returns the original client filename ({@code FileItem} calls this {@code getName()}). */
        @Override
        public String getSubmittedFileName() {
            return fileItem.getName();
        }

        @Override
        public long getSize() {
            return fileItem.getSize();
        }

        /**
         * Not supported. Callers should use {@link #getInputStream()} with
         * {@code PathValidationUtils.validatePath()} instead.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public void write(String fileName) throws IOException {
            throw new UnsupportedOperationException(
                    "FileItemPart.write() is not supported. "
                    + "Use Part.getInputStream() with PathValidationUtils.validatePath() instead.");
        }

        @Override
        public void delete() throws IOException {
            try {
                fileItem.delete();
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        @Override
        public String getHeader(String name) {
            if (fileItem.getHeaders() == null) {
                return null;
            }
            return fileItem.getHeaders().getHeader(name);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            if (fileItem.getHeaders() == null) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            Iterator<String> it = fileItem.getHeaders().getHeaders(name);
            while (it.hasNext()) {
                result.add(it.next());
            }
            return result;
        }

        @Override
        public Collection<String> getHeaderNames() {
            if (fileItem.getHeaders() == null) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            Iterator<String> it = fileItem.getHeaders().getHeaderNames();
            while (it.hasNext()) {
                result.add(it.next());
            }
            return result;
        }
    }
}
