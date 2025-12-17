//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.webserv;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.logging.Logger;

public class WebServiceSessionInvalidatingFilter implements Filter {
    private static final Logger logger = Logger.getLogger(WebServiceSessionInvalidatingFilter.class.getName());
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // nothing
    }

    // Custom wrapper with cached body
    private static class CachedRequestWrapper extends HttpServletRequestWrapper {
        private byte[] cachedBody;
        private boolean bodyRead = false;

        public CachedRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            // Don't read the body in constructor - only when actually needed
        }

        private void cacheBodyIfNeeded() throws IOException {
            if (!bodyRead) {
                try (InputStream is = super.getInputStream()) {
                    cachedBody = is.readAllBytes();
                } catch (Exception e) {
                    cachedBody = new byte[0];
                }
                bodyRead = true;
            }
        }

        @Override
        public BufferedReader getReader() throws IOException {
            cacheBodyIfNeeded();
            String body = new String(cachedBody, StandardCharsets.UTF_8);
            return new BufferedReader(new StringReader(body));
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            cacheBodyIfNeeded();
            
            return new ServletInputStream() {
                private ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
                
                @Override 
                public int read() throws IOException { 
                    return bais.read(); 
                }
                
                @Override 
                public int read(byte[] b) throws IOException {
                    return bais.read(b);
                }
                
                @Override 
                public int read(byte[] b, int off, int len) throws IOException {
                    return bais.read(b, off, len);
                }
                
                @Override 
                public boolean isFinished() { 
                    return bais.available() == 0; 
                }
                
                @Override 
                public boolean isReady() { 
                    return true; 
                }
                
                @Override 
                public void setReadListener(ReadListener listener) {
                    // For async processing - not typically needed for synchronous requests
                }
            };
        }

        public String getCachedBody() throws IOException {
            cacheBodyIfNeeded();
            return new String(cachedBody, StandardCharsets.UTF_8);
        }
    }

    // Custom response wrapper to capture response body
    private static class CachedResponseWrapper extends HttpServletResponseWrapper {
        private ByteArrayOutputStream cachedBody;
        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private boolean outputStreamUsed = false;
        private boolean writerUsed = false;

        public CachedResponseWrapper(HttpServletResponse response) {
            super(response);
            this.cachedBody = new ByteArrayOutputStream();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writerUsed) {
                throw new IllegalStateException("getWriter() has already been called on this response.");
            }
            
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        cachedBody.write(b);
                        CachedResponseWrapper.super.getResponse().getOutputStream().write(b);
                    }
                    
                    @Override
                    public void write(byte[] b) throws IOException {
                        cachedBody.write(b);
                        CachedResponseWrapper.super.getResponse().getOutputStream().write(b);
                    }
                    
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        cachedBody.write(b, off, len);
                        CachedResponseWrapper.super.getResponse().getOutputStream().write(b, off, len);
                    }
                    
                    @Override
                    public boolean isReady() {
                        return true;
                    }
                    
                    @Override
                    public void setWriteListener(WriteListener listener) {
                        // For async processing
                    }
                };
            }
            outputStreamUsed = true;
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStreamUsed) {
                throw new IllegalStateException("getOutputStream() has already been called on this response.");
            }
            
            if (writer == null) {
                String encoding = getCharacterEncoding();
                if (encoding == null) {
                    encoding = StandardCharsets.UTF_8.name();
                }
                
                writer = new PrintWriter(new OutputStreamWriter(new TeeOutputStream(
                    cachedBody, super.getResponse().getOutputStream()), encoding));
            }
            writerUsed = true;
            return writer;
        }
        
        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            if (outputStream != null) {
                outputStream.flush();
            }
            super.flushBuffer();
        }

        public String getCachedBody() {
            try {
                if (writer != null) {
                    writer.flush();
                }
                String encoding = getCharacterEncoding();
                if (encoding == null) {
                    encoding = StandardCharsets.UTF_8.name();
                }
                return cachedBody.toString(encoding);
            } catch (Exception e) {
                return cachedBody.toString();
            }
        }
    }
    
    // Helper class to write to two output streams simultaneously
    private static class TeeOutputStream extends OutputStream {
        private OutputStream out1;
        private OutputStream out2;
        
        public TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }
        
        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }
        
        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
        
        @Override
        public void close() throws IOException {
            out1.close();
            out2.close();
        }
    }

    @Override
    public void doFilter(ServletRequest tmpRequest, ServletResponse tmpResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) tmpRequest;
        HttpServletResponse response = (HttpServletResponse) tmpResponse;
        
        CachedRequestWrapper wrappedRequest = new CachedRequestWrapper(request);
        CachedResponseWrapper wrappedResponse = new CachedResponseWrapper(response);

        // Check if this is a demographics request
        String requestPath = wrappedRequest.getRequestURI();
        boolean isDemographicsRequest = requestPath != null && requestPath.contains("/demographics");
        
        // Log incoming request
        long startTime = System.currentTimeMillis();
        System.out.println("\n=== [" + new java.util.Date() + "] Incoming Request ===");
        System.out.println("Method: " + wrappedRequest.getMethod());
        System.out.println("Request URL: " + wrappedRequest.getRequestURL());
        System.out.println("Query String: " + wrappedRequest.getQueryString());
        System.out.println("Content-Type: " + wrappedRequest.getContentType());
        System.out.println("Content-Length: " + wrappedRequest.getContentLength());
        System.out.println("Remote Address: " + wrappedRequest.getRemoteAddr());
        System.out.println("User Agent: " + wrappedRequest.getHeader("User-Agent"));
        
        // Log request headers (optional - can be verbose)
        System.out.println("\n--- Request Headers ---");
        Enumeration<String> headerNames = wrappedRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            System.out.println(headerName + ": " + wrappedRequest.getHeader(headerName));
        }
        
        // Log request parameters
        if (wrappedRequest.getParameterMap().size() > 0) {
            System.out.println("\n--- Request Parameters ---");
            wrappedRequest.getParameterMap().forEach((key, values) -> {
                System.out.println(key + ": " + String.join(", ", values));
            });
        }
        
        // Log request body
        if (wrappedRequest.getContentLength() > 0 || 
            (wrappedRequest.getContentType() != null && !wrappedRequest.getContentType().isEmpty())) {
            
            try {
                String body = wrappedRequest.getCachedBody();
                if (body != null && !body.trim().isEmpty()) {
                    System.out.println("\n--- Request Body ---");
                    if (wrappedRequest.getContentType() != null && 
                        wrappedRequest.getContentType().contains("application/json")) {
                        System.out.println("JSON Body:");
                        System.out.println(formatJson(body));
                    } else {
                        System.out.println("Body:");
                        System.out.println(body);
                    }
                } else {
                    System.out.println("\n--- Request Body: (empty) ---");
                }
            } catch (Exception e) {
                System.out.println("\n--- Could not read request body: " + e.getMessage() + " ---");
            }
        }

        try {
            // Process the request
            chain.doFilter(wrappedRequest, wrappedResponse);
            
            // Ensure response is flushed
            wrappedResponse.flushBuffer();
            
        } finally {
            // Log outgoing response
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("\n=== [" + new java.util.Date() + "] Outgoing Response ===");
            System.out.println("Status: " + wrappedResponse.getStatus());
            System.out.println("Content-Type: " + wrappedResponse.getContentType());
            
           
            // Only log detailed response info for demographics requests
            //if (isDemographicsRequest) {
                // Log response headers
                System.out.println("\n--- Response Headers ---");
                Collection<String> responseHeaderNames = wrappedResponse.getHeaderNames();
                for (String headerName : responseHeaderNames) {
                    Collection<String> headerValues = wrappedResponse.getHeaders(headerName);
                    System.out.println(headerName + ": " + String.join(", ", headerValues));
                }
                
                // Log response body
                try {
                    String responseBody = wrappedResponse.getCachedBody();
                    if (responseBody != null && !responseBody.trim().isEmpty()) {
                        System.out.println("\n--- Response Body (Date/Time fields only) ---");
                        if (wrappedResponse.getContentType() != null && 
                            wrappedResponse.getContentType().contains("application/json")) {
                            System.out.println("JSON Response (filtered):");
                            printDateTimeFields(responseBody);
                        } else {
                            System.out.println("Response (non-JSON):");
                            System.out.println(responseBody);
                        }
                    } else {
                        System.out.println("\n--- Response Body: (empty) ---");
                    }
                } catch (Exception e) {
                    System.out.println("\n--- Could not read response body: " + e.getMessage() + " ---");
                }
            // } else {
            //     System.out.println("\n--- Response details omitted (not a demographics request) ---");
            // }
            
            System.out.println("=== Request completed in " + duration + "ms ===\n");
        }
    }

    // Method to print only date/time related fields from JSON
    private void printDateTimeFields(String jsonBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonBody);

            boolean found = printDateTimeFieldsRecursive(root, "");
            if (!found) {
                System.out.println("No date/time fields found in response.");
            }
        } catch (Exception e) {
            System.out.println("Error parsing JSON for date/time fields: " + e.getMessage());
        }
    }

    private boolean printDateTimeFieldsRecursive(JsonNode node, String path) {
        boolean foundAny = false;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey().toLowerCase();
                JsonNode value = entry.getValue();

                if (key.contains("date") || key.contains("time") || key.contains("dob")) {
                    System.out.print(path + entry.getKey() + ": " + value.toString() + " ,");
                    foundAny = true;
                }
                // Recurse into nested nodes
                foundAny |= printDateTimeFieldsRecursive(value, path + entry.getKey() + ".");
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                foundAny |= printDateTimeFieldsRecursive(node.get(i), path + "[" + i + "].");
            }
        }

        return foundAny;
    }
    
    // Fallback method using simple string matching
    private void printDateTimeFieldsSimple(String jsonBody) {
        boolean foundDateTimeFields = false;
        String[] lines = jsonBody.split("\n");
        
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            if ((lowerLine.contains("date") || lowerLine.contains("time")) && lowerLine.contains(":")) {
                System.out.println(line.trim());
                foundDateTimeFields = true;
            }
        }
        
        if (!foundDateTimeFields) {
            System.out.println("No date/time fields found in response.");
        }
    }
    
    // Simple JSON formatter (basic indentation)
    private String formatJson(String json) {
        try {
            StringBuilder formatted = new StringBuilder();
            int indent = 0;
            boolean inString = false;
            char prev = 0;
            
            for (char c : json.toCharArray()) {
                if (c == '"' && prev != '\\') {
                    inString = !inString;
                }
                
                if (!inString) {
                    if (c == '{' || c == '[') {
                        formatted.append(c).append('\n');
                        indent += 2;
                        addIndent(formatted, indent);
                    } else if (c == '}' || c == ']') {
                        formatted.append('\n');
                        indent -= 2;
                        addIndent(formatted, indent);
                        formatted.append(c);
                    } else if (c == ',') {
                        formatted.append(c).append('\n');
                        addIndent(formatted, indent);
                    } else if (c == ':') {
                        formatted.append(c).append(' ');
                    } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                        formatted.append(c);
                    } else if (inString) {
                        formatted.append(c);
                    }
                } else {
                    formatted.append(c);
                }
                prev = c;
            }
            return formatted.toString();
        } catch (Exception e) {
            return json; // Return original if formatting fails
        }
    }
    
    private void addIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }

    @Override
    public void destroy() {
        // nothing
    }
}