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
package io.github.carlos_emr.carlos.prescript.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Minimal XML-RPC 1.0 client using Java 21's built-in {@link HttpClient}.
 *
 * <p>Replaces the removed {@code xmlrpc:xmlrpc:1.2-b1} dependency. Supports the full
 * XML-RPC type set and returns {@link Vector} for arrays and {@link Hashtable} for
 * structs to match the legacy library's type mapping that callers depend on.</p>
 *
 * <p>Thread safety: the shared {@link HttpClient} is thread-safe. Each call creates
 * its own {@link DocumentBuilderFactory} since that class is not thread-safe.</p>
 *
 * @since 2026-02-26
 */
public class SimpleXmlRpcClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Date-time formatters for XML-RPC {@code dateTime.iso8601} parsing.
     * Tried in order: XML-RPC spec format first, then standard ISO 8601.
     * {@link DateTimeFormatter} is thread-safe (immutable).
     */
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    private final String serverUrl;

    /**
     * Creates a client for the given XML-RPC endpoint.
     *
     * @param serverUrl String the full URL of the XML-RPC server
     */
    public SimpleXmlRpcClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * Executes an XML-RPC method call.
     *
     * @param methodName String the remote method name
     * @param params     Vector of parameters to pass (String, Integer, Boolean, Double, Vector, Hashtable)
     * @return Object the deserialized response value, or null if the server returns an empty params section
     * @throws XmlRpcFaultException if the server returns an XML-RPC fault
     * @throws Exception            if a network or parsing error occurs
     */
    public Object execute(String methodName, Vector params) throws Exception {
        String requestXml = buildRequest(methodName, params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "text/xml")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("DrugRef XML-RPC server returned HTTP " + response.statusCode()
                    + " for '" + methodName + "' at " + serverUrl);
        }
        return parseResponse(response.body());
    }

    /**
     * Builds the XML-RPC request envelope.
     *
     * @param methodName String the remote method name, expected to be a simple ASCII identifier per the XML-RPC specification
     * @param params     Vector of typed parameter values to serialize
     * @return String the complete XML request body
     */
    private String buildRequest(String methodName, Vector params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<methodCall><methodName>").append(escapeXml(methodName)).append("</methodName>");
        sb.append("<params>");
        for (int i = 0; i < params.size(); i++) {
            sb.append("<param>");
            serializeValue(sb, params.get(i));
            sb.append("</param>");
        }
        sb.append("</params></methodCall>");
        return sb.toString();
    }

    /**
     * Recursively serializes a Java value into an XML-RPC {@code <value>} element.
     * Maps Java types to XML-RPC types: String, Integer, Boolean, Double, Vector (array),
     * and Hashtable (struct). Null and unrecognized types are serialized as empty/toString strings.
     */
    @SuppressWarnings("unchecked")
    private void serializeValue(StringBuilder sb, Object value) {
        sb.append("<value>");
        if (value == null) {
            sb.append("<string></string>");
        } else if (value instanceof String s) {
            sb.append("<string>").append(escapeXml(s)).append("</string>");
        } else if (value instanceof Integer i) {
            sb.append("<int>").append(i).append("</int>");
        } else if (value instanceof Boolean b) {
            sb.append("<boolean>").append(b ? "1" : "0").append("</boolean>");
        } else if (value instanceof Double d) {
            sb.append("<double>").append(d).append("</double>");
        } else if (value instanceof Vector v) {
            sb.append("<array><data>");
            for (int i = 0; i < v.size(); i++) {
                serializeValue(sb, v.get(i));
            }
            sb.append("</data></array>");
        } else if (value instanceof Hashtable ht) {
            sb.append("<struct>");
            var keys = ht.keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                sb.append("<member><name>").append(escapeXml(key.toString())).append("</name>");
                serializeValue(sb, ht.get(key));
                sb.append("</member>");
            }
            sb.append("</struct>");
        } else {
            sb.append("<string>").append(escapeXml(value.toString())).append("</string>");
        }
        sb.append("</value>");
    }

    /** Escapes the five XML special characters for safe embedding in XML text content. */
    private String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Parses the XML-RPC response, extracting the return value or throwing on fault.
     * XXE protection features are enabled on every parse.
     *
     * @param responseXml String the raw XML body of the HTTP response from the XML-RPC server
     * @return Object the deserialized response value, or null if the response contains no params
     * @throws XmlRpcFaultException if the response contains a {@code <fault>} element
     * @throws Exception            if the XML cannot be parsed
     */
    private Object parseResponse(String responseXml) throws Exception {
        // Per-call factory — DocumentBuilderFactory is not thread-safe (see class-level doc)
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(responseXml)));

        Element root = doc.getDocumentElement();

        NodeList faultList = root.getElementsByTagName("fault");
        if (faultList.getLength() > 0) {
            Element faultValue = getFirstChildElement(faultList.item(0));
            @SuppressWarnings("unchecked")
            Hashtable<String, Object> fault = (Hashtable<String, Object>) parseValue(faultValue);
            int code = fault.get("faultCode") instanceof Integer i ? i : 0;
            String msg = fault.get("faultString") instanceof String s ? s : "Unknown fault";
            throw new XmlRpcFaultException(code, msg);
        }

        NodeList paramList = root.getElementsByTagName("param");
        if (paramList.getLength() > 0) {
            Element valueElem = getFirstChildElement(paramList.item(0));
            return parseValue(valueElem);
        }

        return null;
    }

    /**
     * Deserializes a single XML-RPC {@code <value>} node into its Java equivalent.
     * Handles all XML-RPC 1.0 types: string, int/i4, boolean, double, dateTime.iso8601,
     * base64, array, and struct. A bare {@code <value>text</value>} without a type tag
     * is treated as a string per the XML-RPC specification.
     *
     * <p>{@code dateTime.iso8601} values are parsed to {@link java.util.Date} to match
     * the legacy {@code xmlrpc:xmlrpc:1.2-b1} library behavior that callers depend on
     * (e.g., {@code DrugrefUtil.convertLocalDS} casts these fields to {@link Date}).</p>
     *
     * @throws XmlRpcFaultException if a numeric or date value cannot be parsed
     * @throws Exception            if nested array or struct parsing fails
     */
    private Object parseValue(Node valueNode) throws Exception {
        Element child = getFirstChildElement(valueNode);
        if (child == null) {
            // bare <value>text</value> — treat as string per XML-RPC spec
            return valueNode.getTextContent();
        }

        String tag = child.getTagName();
        String text = child.getTextContent();

        return switch (tag) {
            case "string" -> text;
            case "int", "i4" -> {
                try {
                    yield Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new XmlRpcFaultException(0, "Invalid <" + tag + "> value: '" + text + "'");
                }
            }
            case "boolean" -> "1".equals(text);
            case "double" -> {
                try {
                    yield Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new XmlRpcFaultException(0, "Invalid <double> value: '" + text + "'");
                }
            }
            case "dateTime.iso8601" -> parseDateTimeIso8601(text);
            case "base64" -> Base64.getDecoder().decode(text);
            case "array" -> parseArray(child);
            case "struct" -> parseStruct(child);
            default -> text;
        };
    }

    /**
     * Parses an XML-RPC {@code dateTime.iso8601} value into a {@link java.util.Date}.
     * Matches the legacy {@code xmlrpc:xmlrpc:1.2-b1} library behavior so callers
     * that cast the result to {@link Date} (e.g., {@code DrugrefUtil.convertLocalDS})
     * continue to work correctly.
     *
     * <p>Tries XML-RPC spec format ({@code yyyyMMddTHH:mm:ss}) first, then standard
     * ISO 8601 ({@code yyyy-MM-dd'T'HH:mm:ss}). {@link DateTimeFormatter} instances
     * are thread-safe (reused from the class-level constant).</p>
     *
     * @param text String the raw {@code dateTime.iso8601} text from the server response
     * @return Date the parsed date value in the system default time zone
     * @throws XmlRpcFaultException if the value cannot be parsed by any known format
     * @since 2026-02-27
     */
    private static Date parseDateTimeIso8601(String text) throws XmlRpcFaultException {
        for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new XmlRpcFaultException(0, "Cannot parse dateTime.iso8601 value: '" + text + "'");
    }

    /** Parses an XML-RPC {@code <array>} element into a {@link Vector}. */
    private Vector<Object> parseArray(Element arrayElem) throws Exception {
        Vector<Object> vec = new Vector<>();
        NodeList dataNodes = arrayElem.getElementsByTagName("data");
        if (dataNodes.getLength() > 0) {
            Node data = dataNodes.item(0);
            for (Node n = data.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE && "value".equals(n.getNodeName())) {
                    vec.add(parseValue(n));
                }
            }
        }
        return vec;
    }

    /** Parses an XML-RPC {@code <struct>} element into a {@link Hashtable}. */
    private Hashtable<String, Object> parseStruct(Element structElem) throws Exception {
        Hashtable<String, Object> ht = new Hashtable<>();
        for (Node n = structElem.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "member".equals(n.getNodeName())) {
                String name = null;
                Object value = null;
                for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNodeType() != Node.ELEMENT_NODE) continue;
                    if ("name".equals(c.getNodeName())) {
                        name = c.getTextContent();
                    } else if ("value".equals(c.getNodeName())) {
                        value = parseValue(c);
                    }
                }
                if (name != null && value != null) {
                    ht.put(name, value);
                }
            }
        }
        return ht;
    }

    /** Returns the first child {@link Element} of the given node, or null if none exist. */
    private Element getFirstChildElement(Node node) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) n;
            }
        }
        return null;
    }
}
