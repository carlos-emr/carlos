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
package io.github.carlos_emr.carlos.form.util;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc.DocumentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * @author denniswarren
 * <p>
 * Use this class to transport the output of an Oscar form from any
 * servlet into the ConvertToEdoc Utility.
 */
public class FormTransportContainer {

    private CapturingResponseWrapper responseWrapper;

    private final static DocumentType documentType = DocumentType.form;
    private final String HTML;
    private final String contextPath;
    private String realPath;
    private final LoggedInInfo loggedInInfo;
    private String subject;
    private String providerNo;
    private String demographicNo;
    private String formName;
    private static final String FORM_FORWARD_PATH = "/form/forwardshortcutname";

//	private final Map<String, String[]> modifiableParameters;
//	private Map<String, String[]> allParameters = new TreeMap<>();

    public FormTransportContainer(HttpServletResponse response,
                                  HttpServletRequest request, final String formPath) throws ServletException, IOException {

        responseWrapper = new CapturingResponseWrapper(response);

        String forwardPath = formPath != null ? formPath : FORM_FORWARD_PATH;
        ActionContext actionContext = ActionContext.getContext();
        HttpServletResponse previousResponse = actionContext == null ? null : ServletActionContext.getResponse();
        if (actionContext != null) {
            ServletActionContext.setResponse(responseWrapper);
        }
        try {
            request.getRequestDispatcher(forwardPath).forward(request, responseWrapper);
        } finally {
            if (actionContext != null) {
                ServletActionContext.setResponse(previousResponse);
            }
        }
        if (responseWrapper.isUnrenderableStatus()) {
            String failureMessage = "Form rendering failed with HTTP status " + responseWrapper.getStatus();
            String nestedError = responseWrapper.getErrorMessage();
            if (nestedError != null && !nestedError.isBlank()) {
                failureMessage += ": " + nestedError;
            }
            throw new ServletException(failureMessage);
        }

        this.HTML = responseWrapper.toString();
        this.loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        this.contextPath = request.getContextPath();

    }

    public FormTransportContainer(HttpServletResponse response,
                                  HttpServletRequest request) throws ServletException, IOException {
        this(response, request, null);
    }

    public final String getContextPath() {
        return this.contextPath;
    }

    public final String getRealPath() {
        return realPath;
    }

    public void setRealPath(String realPath) {
        this.realPath = realPath;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHTML() {
        return this.HTML;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    private static final class CapturingResponseWrapper extends HttpServletResponseWrapper {
        private final StringWriter stringWriter = new StringWriter();
        private final PrintWriter writer = new PrintWriter(stringWriter);
        private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        private final ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                if (writeListener == null) {
                    throw new IllegalArgumentException("writeListener must not be null");
                }
                throw new IllegalStateException(
                        "FormTransportContainer captures forwarded form output synchronously and does not support asynchronous writes");
            }

            @Override
            public void write(int value) {
                outputBytes.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                outputBytes.write(bytes, offset, length);
            }
        };
        private final String initialContentType;
        private final String initialCharacterEncoding;
        private final Locale initialLocale;
        private final int initialBufferSize;
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private String characterEncoding;
        private Locale locale;
        private int bufferSize;
        private Supplier<Map<String, String>> trailerFields;
        private String errorMessage;
        private boolean writerAccessed;
        private boolean outputStreamAccessed;

        CapturingResponseWrapper(HttpServletResponse response) {
            super(response);
            initialContentType = response.getContentType();
            initialCharacterEncoding = response.getCharacterEncoding();
            initialLocale = response.getLocale();
            initialBufferSize = response.getBufferSize();
            resetMetadata();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStreamAccessed) {
                throw new IllegalStateException("getOutputStream() has already been called for this response");
            }
            writerAccessed = true;
            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writerAccessed) {
                throw new IllegalStateException("getWriter() has already been called for this response");
            }
            outputStreamAccessed = true;
            return outputStream;
        }

        @Override
        public void sendError(int statusCode) {
            this.status = statusCode;
            this.errorMessage = null;
        }

        @Override
        public void sendError(int statusCode, String message) {
            this.status = statusCode;
            this.errorMessage = message;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public void sendRedirect(String location) {
            this.status = HttpServletResponse.SC_FOUND;
        }

        @Override
        public void sendRedirect(String location, boolean clearBuffer) {
            this.status = HttpServletResponse.SC_FOUND;
        }

        @Override
        public void sendRedirect(String location, int statusCode) {
            this.status = statusCode;
        }

        @Override
        public void sendRedirect(String location, int statusCode, boolean clearBuffer) {
            this.status = statusCode;
        }

        @Override
        public void setStatus(int statusCode) {
            this.status = statusCode;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void flushBuffer() {
            writer.flush();
        }

        @Override
        public void resetBuffer() {
            stringWriter.getBuffer().setLength(0);
            outputBytes.reset();
        }

        @Override
        public void reset() {
            resetBuffer();
            status = HttpServletResponse.SC_OK;
            errorMessage = null;
            writerAccessed = false;
            outputStreamAccessed = false;
            resetMetadata();
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void addCookie(Cookie cookie) {
            // Intentionally ignored; nested cookies must not leak to the caller response.
        }

        @Override
        public void setHeader(String name, String value) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void addHeader(String name, String value) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void setDateHeader(String name, long date) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void addDateHeader(String name, long date) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void setIntHeader(String name, int value) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void addIntHeader(String name, int value) {
            // Intentionally ignored; nested headers must not leak to the caller response.
        }

        @Override
        public void setContentLength(int length) {
            // Intentionally ignored; nested content length must not leak to the caller.
        }

        @Override
        public void setContentLengthLong(long length) {
            // Intentionally ignored; nested content length must not leak to the caller.
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
            String charsetFromContentType = extractCharset(contentType);
            if (charsetFromContentType != null) {
                this.characterEncoding = charsetFromContentType;
            }
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void setCharacterEncoding(String characterEncoding) {
            this.characterEncoding = characterEncoding;
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public void setLocale(Locale locale) {
            this.locale = locale;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        @Override
        public int getBufferSize() {
            return bufferSize;
        }

        @Override
        public void setTrailerFields(Supplier<Map<String, String>> supplier) {
            // Captured locally; nested trailer fields must not leak to the caller response.
            this.trailerFields = supplier;
        }

        @Override
        public Supplier<Map<String, String>> getTrailerFields() {
            return trailerFields;
        }

        boolean isUnrenderableStatus() {
            return status >= HttpServletResponse.SC_MULTIPLE_CHOICES;
        }

        @Override
        public String toString() {
            writer.flush();
            return stringWriter.toString() + decodeOutputBytes();
        }

        private Charset resolveCharacterEncoding() {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                return StandardCharsets.UTF_8;
            }
        }

        private String decodeOutputBytes() {
            if (outputBytes.size() == 0) {
                return "";
            }
            return new String(outputBytes.toByteArray(), resolveCharacterEncoding());
        }

        private static String extractCharset(String contentType) {
            if (contentType == null) {
                return null;
            }
            for (String parameter : contentType.split(";")) {
                String trimmed = parameter.trim();
                if (trimmed.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
                    String charset = trimmed.substring("charset=".length()).trim();
                    if (charset.length() >= 2 && charset.charAt(0) == '"'
                            && charset.charAt(charset.length() - 1) == '"') {
                        charset = charset.substring(1, charset.length() - 1).trim();
                    }
                    return charset.isEmpty() ? null : charset;
                }
            }
            return null;
        }

        private void resetMetadata() {
            contentType = initialContentType;
            characterEncoding = initialCharacterEncoding;
            locale = initialLocale;
            bufferSize = initialBufferSize;
        }
    }

}
