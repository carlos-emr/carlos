/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.test.hl7;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.DefaultXMLParser;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * Builds synthetic HL7 v2 XML messages that ask the parser for something outside the message, and
 * watches whether it went and got it.
 *
 * <p>The messages declare a {@code DOCTYPE} that names a disposable local file holding
 * {@link #MARKER}, or a URL on a loopback HTTP server started here. A parser that follows either
 * leaves evidence: the marker text in the parsed content, or a request counted by
 * {@link #requestCount()}. Nothing leaves the machine, no public service is contacted, and the
 * message and file contain no patient data.
 *
 * <p>Close it after use; it stops the server and deletes the marker file.
 */
public final class HostileHl7Xml implements AutoCloseable {

    /** Text only the marker file contains. */
    public static final String MARKER = "SYNTHETIC-HL7-XXE-MARKER-7f3a91";

    /** Family name the benign message carries; the hostile ones replace it with an entity. */
    public static final String FAMILY_NAME = "DOE";

    private static final String PIPE_ADT_A01 = "MSH|^~\\&|SENDER|FACILITY|CARLOS|CLINIC|20260101120000||ADT^A01|MSG0001|P|2.3\r"
            + "EVN|A01|20260101120000\r"
            + "PID|1||FAKE-1^^^TEST^MR||" + FAMILY_NAME + "^JOHN||19800101|M\r"
            + "PV1|1|I\r";

    private final Path markerFile;
    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private HostileHl7Xml(Path markerFile, HttpServer server) {
        this.markerFile = markerFile;
        this.server = server;
    }

    /**
     * Writes the marker file and starts the loopback observer on a free port.
     *
     * @return a running fixture
     * @throws IOException if the file or the server cannot be created
     */
    public static HostileHl7Xml start() throws IOException {
        Path marker = Files.createTempFile("hl7-xxe-marker", ".txt");
        Files.writeString(marker, MARKER);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        HostileHl7Xml fixture = new HostileHl7Xml(marker, server);
        server.createContext("/", exchange -> {
            fixture.requests.add(exchange.getRequestURI().getPath());
            byte[] body = "<!ELEMENT any ANY>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return fixture;
    }

    /** @return how many requests the loopback observer has received */
    public int requestCount() {
        return requests.size();
    }

    /** @return the paths requested from the loopback observer, in order */
    public List<String> requestedPaths() {
        return List.copyOf(requests);
    }

    /** @return the URL of the loopback observer, without a trailing slash */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** @return a well-formed HL7 v2 XML {@code ADT^A01}, as HAPI itself encodes it, with no DOCTYPE */
    public String validXml() throws HL7Exception {
        return new DefaultXMLParser().encode(new PipeParser().parse(PIPE_ADT_A01));
    }

    /** @return the same message as pipe-delimited text */
    public String validPipe() {
        return PIPE_ADT_A01;
    }

    /** @return XML whose family name is an external general entity reading the marker file */
    public String externalFileEntity() throws HL7Exception {
        return withEntityInFamilyName(
                "<!DOCTYPE ADT_A01 [<!ENTITY xxe SYSTEM \"" + markerFile.toUri() + "\">]>");
    }

    /** @return XML whose family name is an external general entity fetched over HTTP */
    public String externalHttpEntity() throws HL7Exception {
        return withEntityInFamilyName(
                "<!DOCTYPE ADT_A01 [<!ENTITY xxe SYSTEM \"" + baseUrl() + "/general-entity\">]>");
    }

    /** @return XML that declares an external parameter entity and expands it inside the DTD */
    public String externalParameterEntity() throws HL7Exception {
        return withEntityInFamilyName(
                "<!DOCTYPE ADT_A01 [<!ENTITY % remote SYSTEM \"" + baseUrl() + "/parameter-entity.dtd\"> %remote;]>");
    }

    /** @return XML whose DOCTYPE points at an external DTD subset */
    public String externalDtd() throws HL7Exception {
        return withEntityInFamilyName("<!DOCTYPE ADT_A01 SYSTEM \"" + baseUrl() + "/external.dtd\">");
    }

    /** @return XML whose DOCTYPE reads the marker file through a parameter entity inside the DTD */
    public String externalFileParameterEntity() throws HL7Exception {
        return withEntityInFamilyName(
                "<!DOCTYPE ADT_A01 [<!ENTITY % file SYSTEM \"" + markerFile.toUri() + "\"> %file;]>");
    }

    /**
     * @param levels how many times each entity multiplies the previous one by ten
     * @return XML whose family name is an internal entity that expands to ten to the power of
     *         {@code levels} copies of its base text: harmless at 2, a denial of service at 9
     */
    public String internalEntityExpansion(int levels) throws HL7Exception {
        StringBuilder doctype = new StringBuilder("<!DOCTYPE ADT_A01 [<!ENTITY lol0 \"0123456789\">");
        for (int level = 1; level <= levels; level++) {
            doctype.append("<!ENTITY lol").append(level).append(" \"");
            for (int copy = 0; copy < 10; copy++) {
                doctype.append("&lol").append(level - 1).append(';');
            }
            doctype.append("\">");
        }
        doctype.append("]>");
        return inject(doctype.toString(), "<XPN.1>" + FAMILY_NAME + "</XPN.1>", "<XPN.1>&lol" + levels + ";</XPN.1>");
    }

    /** @return XML with a {@code DOCTYPE} that declares only a harmless internal entity */
    public String harmlessInternalEntity() throws HL7Exception {
        return inject("<!DOCTYPE ADT_A01 [<!ENTITY note \"plain text\">]>",
                "<XPN.1>" + FAMILY_NAME + "</XPN.1>", "<XPN.1>&note;</XPN.1>");
    }

    private String withEntityInFamilyName(String doctype) throws HL7Exception {
        return inject(doctype, "<XPN.1>" + FAMILY_NAME + "</XPN.1>", "<XPN.1>&xxe;</XPN.1>");
    }

    private String inject(String doctype, String from, String to) throws HL7Exception {
        String xml = validXml();
        String withDoctype = xml.replaceFirst("<ADT_A01", java.util.regex.Matcher.quoteReplacement(doctype + "\n<ADT_A01"));
        if (withDoctype.equals(xml) || !withDoctype.contains(from)) {
            throw new IllegalStateException("fixture could not place the entity in the HL7 XML");
        }
        return withDoctype.replace(from, to);
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        Files.deleteIfExists(markerFile);
    }
}
