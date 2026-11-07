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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsScalarParser;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaTypeCursor;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsXmlReaders;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReportReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPathComposer;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlValidationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportHeaderComposer;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.xml.validation.ValidatorHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Validates each completed UTF-8 patient XML against the pinned wire schema and bounded
 * export rules. Large text, decimal and Base64 values use the shared schema-proven lexical
 * parser. The caller owns the input and any reference index: partial callbacks are not a
 * successful validation or a publishable manifest. Source completeness is a separate gate.
 */
public final class CdsExportXmlValidator {
    private static final int MAX_MARKUP = 65_536;
    private static final int MAX_NAMES = 2048;
    private static final int MAX_NAME = 256;
    private static final int MAX_NAMESPACES = 64;
    private static final int MAX_URI = 1024;
    private static final int MAX_ATTRIBUTES = 128;

    public CdsExportXmlValidationDto validate(InputStream input, String senderPatientId,
            Set<CdsCategory> selectedCategories, String applicationVersion, CdsLogicalProfile profile,
            CdsExportXmlLimitsDto limits, Consumer<CdsExportReportReferenceDto> references,
            Runnable checkpoint) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(limits);
        Objects.requireNonNull(references); Objects.requireNonNull(checkpoint);
        Set<CdsCategory> selected = Set.copyOf(selectedCategories);
        if (!selected.contains(CdsCategory.DEMOGRAPHICS) || senderPatientId == null
                || !senderPatientId.matches("[A-Za-z0-9][A-Za-z0-9-]{0,19}")) {
            throw new IllegalArgumentException("CDS_EXPORT_XML_REQUEST_INVALID");
        }
        String header = new CdsExportHeaderComposer().comment(applicationVersion, profile);
        CountingInput counted = new CountingInput(input, limits.maxBytes(), checkpoint);
        PushbackInputStream bytes = new PushbackInputStream(counted, 512);
        byte[] prefix = bytes.readNBytes(512);
        verifyDeclaration(prefix);
        bytes.unread(prefix);
        Pipeline pipeline = new Pipeline(senderPatientId, selected, header, limits, references, checkpoint);
        try {
            XMLReader reader = CdsXmlReaders.reader(MAX_NAME, MAX_URI, limits.maxDepth(),
                    MAX_ATTRIBUTES, MAX_NAMESPACES);
            reader.setContentHandler(pipeline); reader.setErrorHandler(pipeline);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", pipeline);
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            reader.parse(new InputSource(CdsXmlReaders.markupReader(new InputStreamReader(bytes, decoder),
                    MAX_MARKUP, IOException::new)));
        } catch (SAXException failure) {
            if (failure.getException() instanceof IOException io) throw io;
            throw new IOException("CDS_EXPORT_XML_SCHEMA_INVALID");
        } catch (javax.xml.parsers.ParserConfigurationException failure) {
            throw new IOException("CDS_EXPORT_XML_VALIDATOR_UNAVAILABLE");
        }
        pipeline.check();
        if (!pipeline.headerSeen || pipeline.patients != 1
                || !senderPatientId.equals(pipeline.senderId)
                || pipeline.counts.get(CdsCategory.DEMOGRAPHICS) != 1L) {
            throw new IOException("CDS_EXPORT_XML_ENVELOPE_INVALID");
        }
        return new CdsExportXmlValidationDto(pipeline.senderId, pipeline.legalFirstName, pipeline.legalLastName,
                pipeline.dateOfBirth, pipeline.counts, pipeline.textReports,
                pipeline.binaryReports, pipeline.referenceCount, HexFormat.of().formatHex(counted.digest.digest()),
                counted.count);
    }

    /**
     * Checks actual parsed legal name, patient ID and calendar birth date against the exact
     * DP01.11 path. Physician folder identity must separately come from captured roster/provider
     * evidence; an optional source PrimaryPhysician is not proof of present roster ownership.
     * This method never normalizes a different name or shifts a birth date through UTC.
     */
    public void verifyFileBinding(CdsExportXmlValidationDto actual, CdsExportPatientFileDto expected,
            String exactRelativePath) throws IOException {
        Objects.requireNonNull(actual); Objects.requireNonNull(expected);
        var path = new CdsExportPathComposer().compose(expected);
        var birth = javax.xml.datatype.DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(actual.dateOfBirth().strip());
        if (!Objects.equals(actual.senderPatientId(), expected.senderPatientId())
                || !Objects.equals(actual.legalFirstName(), expected.patientFirstName())
                || !Objects.equals(actual.legalLastName(), expected.patientLastName())
                || !birth.getEonAndYear().equals(java.math.BigInteger.valueOf(expected.dateOfBirth().getYear()))
                || birth.getMonth() != expected.dateOfBirth().getMonthValue() || birth.getDay() != expected.dateOfBirth().getDayOfMonth()
                || !(path.physicianFolder() + "/" + path.patientXmlFile()).equals(exactRelativePath)) {
            fail("CDS_EXPORT_XML_FILENAME_IDENTITY_MISMATCH");
        }
    }

    private static void verifyDeclaration(byte[] prefix) throws IOException {
        String start = new String(prefix, StandardCharsets.US_ASCII);
        if (prefix.length >= 3 && (prefix[0] & 255) == 239 && (prefix[1] & 255) == 187
                && (prefix[2] & 255) == 191) start = start.substring(3);
        int end = start.indexOf("?>");
        if (end < 0 || !start.substring(0, end + 2).matches(
                "<\\?xml\\s+version=['\"]1\\.0['\"]\\s+encoding=['\"]UTF-8['\"](?:\\s+standalone=['\"](?:yes|no)['\"])?\\s*\\?>")) {
            throw new IOException("CDS_EXPORT_XML_UTF8_DECLARATION_REQUIRED");
        }
    }

    private static final class Pipeline extends DefaultHandler2 {
        private final String expectedId;
        private final Set<CdsCategory> selected;
        private final String expectedHeader;
        private final CdsExportXmlLimitsDto limits;
        private final Consumer<CdsExportReportReferenceDto> references;
        private final Runnable checkpoint;
        private final ValidatorHandler validator = CdsWireSchema.newValidator();
        private final CdsSchemaTypeCursor types = CdsWireSchema.newTypeCursor();
        private final Deque<Frame> stack = new ArrayDeque<>();
        private final Set<String> names = new HashSet<>();
        private final Set<String> prefixes = new HashSet<>();
        private final Set<String> uris = new HashSet<>();
        private final EnumMap<CdsCategory, Long> counts = new EnumMap<>(CdsCategory.class);
        private final long started = System.nanoTime();
        private long elements;
        private long identityValues;
        private long identityCharacters;
        private long textReports;
        private long binaryReports;
        private long referenceCount;
        private int patients;
        private boolean headerSeen;
        private String senderId, legalFirstName, legalLastName, dateOfBirth;
        private Report report;

        Pipeline(String expectedId, Set<CdsCategory> selected, String header, CdsExportXmlLimitsDto limits,
                Consumer<CdsExportReportReferenceDto> references, Runnable checkpoint) {
            this.expectedId = expectedId; this.selected = selected; expectedHeader = header;
            this.limits = limits; this.references = references; this.checkpoint = checkpoint;
            for (CdsCategory category : selected) counts.put(category, 0L);
            validator.setErrorHandler(this);
        }

        @Override public void startDocument() throws SAXException { validator.startDocument(); }
        @Override public void endDocument() throws SAXException { validator.endDocument(); }
        @Override public void comment(char[] chars, int offset, int length) {
            if (patients == 0 && new String(chars, offset, length).equals(expectedHeader)) headerSeen = true;
        }
        @Override public void processingInstruction(String target, String data) throws SAXException {
            try { check(); name(target); } catch (IOException failure) { throw new SAXException(failure); }
        }
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {
            try {
                check();
                if (prefix.length() > MAX_NAME || uri.length() > MAX_URI) fail("CDS_EXPORT_XML_NAMESPACE_LIMIT");
                prefixes.add(prefix); uris.add(uri);
                if (prefixes.size() > MAX_NAMESPACES || uris.size() > MAX_NAMESPACES) fail("CDS_EXPORT_XML_NAMESPACE_LIMIT");
                types.prefix(prefix, uri); validator.startPrefixMapping(prefix, uri);
            } catch (IOException failure) { throw new SAXException(failure); }
        }
        @Override public void endPrefixMapping(String prefix) throws SAXException { validator.endPrefixMapping(prefix); }

        @Override public void startElement(String uri, String local, String qualified, Attributes attrs) throws SAXException {
            try {
                check(); name(qualified);
                if (++elements > limits.maxElements()) fail("CDS_EXPORT_XML_ELEMENT_LIMIT");
                if (stack.size() >= limits.maxDepth()) fail("CDS_EXPORT_XML_DEPTH_LIMIT");
                if (attrs.getLength() > MAX_ATTRIBUTES) fail("CDS_EXPORT_XML_ATTRIBUTE_LIMIT");
                for (int i = 0; i < attrs.getLength(); i++) name(attrs.getQName(i));
                int depth = stack.size();
                if (depth == 0 && (!uri.equals("cds") || !local.equals("OmdCds") || elements != 1)) {
                    fail("CDS_EXPORT_XML_ENVELOPE_INVALID");
                }
                if (depth == 1 && (!uri.equals("cds") || !local.equals("PatientRecord") || ++patients != 1 || !headerSeen)) {
                    fail("CDS_EXPORT_XML_ENVELOPE_INVALID");
                }
                if (depth == 2) {
                    if (!uri.equals("cds")) fail("CDS_EXPORT_XML_CATEGORY_INVALID");
                    CdsCategory category;
                    try { category = CdsCategory.fromWireName(local); }
                    catch (IllegalArgumentException failure) { throw new IOException("CDS_EXPORT_XML_CATEGORY_INVALID"); }
                    if (!selected.contains(category)) fail("CDS_EXPORT_XML_UNSELECTED_CATEGORY");
                    counts.compute(category, (key, value) -> Math.addExact(value, 1L));
                    if (category == CdsCategory.REPORTS) report = new Report(counts.get(category));
                }
                Frame parent = stack.peek();
                if (parent != null) parent.child = true;
                types.start(uri, local, attrs);
                Frame frame = new Frame(uri, local);
                frame.scalar.configure(types.elementType());
                if (frame.scalar.type() == null) fail("CDS_EXPORT_XML_UNKNOWN_TYPE");
                frame.capture = depth == 3 && uri.equals("cds") && (local.equals("UniqueVendorIdSequence")
                        && parent.local.equals("Demographics") || report != null
                        && Set.of("Format", "FileExtensionAndVersion", "FilePath").contains(local));
                if (matches(uri, local, "cds:OmdCds", "cds:PatientRecord", "cds:Demographics", "cds:DateOfBirth")) frame.identityField = "DOB";
                if (matches(uri, local, "cds:OmdCds", "cds:PatientRecord", "cds:Demographics", "cds:Names", "cds_dt:LegalName", "cds_dt:FirstName", "cds_dt:Part")) frame.identityField = "FIRST";
                if (matches(uri, local, "cds:OmdCds", "cds:PatientRecord", "cds:Demographics", "cds:Names", "cds_dt:LegalName", "cds_dt:LastName", "cds_dt:Part")) frame.identityField = "LAST";
                frame.capture |= frame.identityField != null;
                stack.push(frame);
                for (int i = 0; i < attrs.getLength(); i++) {
                    if (CdsSchemaTypeCursor.identity(types.attributeType(attrs.getURI(i), attrs.getLocalName(i)))) {
                        char[] lexical = attrs.getValue(i).toCharArray(); identity(new Frame("", ""), lexical, 0, lexical.length);
                    }
                }
                // The identity guard precedes the JDK validator, which retains ID/IDREF state.
                validator.startElement(uri, local, qualified, attrs);
                if (report != null && depth == 4 && parent.local.equals("Content") && uri.equals("cds_dt")) {
                    if (local.equals("TextContent")) report.text = true;
                    if (local.equals("Media")) report.binary = true;
                }
            } catch (IOException failure) { throw new SAXException(failure); }
        }

        @Override public void characters(char[] chars, int offset, int length) throws SAXException {
            try {
                check(); Frame frame = stack.peek();
                if (frame == null) { validator.characters(chars, offset, length); return; }
                if (frame.capture) {
                    if (frame.value.length() + length > 8192) fail("CDS_EXPORT_XML_CAPTURE_LIMIT");
                    frame.value.append(chars, offset, length);
                }
                if (frame.scalar.identity()) identity(frame, chars, offset, length);
                if (frame.scalar.mode() == CdsScalarParser.Mode.BINARY) {
                    frame.scalar.base64(chars, offset, length, OutputStream.nullOutputStream(), limits.maxBinaryBytes());
                } else frame.scalar.validate(chars, offset, length, validator, MAX_MARKUP);
            } catch (IOException failure) { throw new SAXException(failure); }
        }
        @Override public void ignorableWhitespace(char[] chars, int offset, int length) throws SAXException {
            characters(chars, offset, length);
        }

        @Override public void endElement(String uri, String local, String qualified) throws SAXException {
            try {
                check(); Frame frame = stack.peek();
                if (!frame.child) frame.scalar.finishScalar(validator);
                dictionaryLength(frame);
                validator.endElement(uri, local, qualified);
                if (frame.capture) {
                    String value = frame.value.toString();
                    if (frame.identityField != null) {
                        switch (frame.identityField) {
                            case "FIRST" -> legalFirstName = value;
                            case "LAST" -> legalLastName = value;
                            case "DOB" -> dateOfBirth = value;
                            default -> throw new IllegalStateException("CDS_EXPORT_XML_IDENTITY_INVARIANT");
                        }
                    } else if (local.equals("UniqueVendorIdSequence")) {
                        if (senderId != null || !expectedId.equals(value)) fail("CDS_EXPORT_XML_PATIENT_ID_MISMATCH");
                        senderId = value;
                    } else if (local.equals("Format")) report.format = value.strip();
                    else if (local.equals("FileExtensionAndVersion")) report.extension = value.strip();
                    else if (local.equals("FilePath")) report.path = value;
                }
                if (stack.size() == 3 && local.equals("Reports")) {
                    finishReport(); report = null;
                }
                stack.pop(); types.end();
            } catch (IOException failure) { throw new SAXException(failure); }
        }

        private boolean matches(String uri, String local, String... path) {
            if (stack.size() + 1 != path.length || !(uri + ":" + local).equals(path[path.length - 1])) return false;
            int index = path.length - 2;
            for (Frame frame : stack) if (!(frame.uri + ":" + frame.local).equals(path[index--])) return false;
            return true;
        }

        private void finishReport() throws IOException {
            boolean binary = report.binary || report.path != null;
            if ("Binary".equals(report.format) && (report.extension == null || report.extension.isEmpty())) {
                fail("CDS_EXPORT_REPORT_BINARY_EXTENSION_REQUIRED");
            }
            if (binary && !"Binary".equals(report.format)
                    || report.text && !"Text".equals(report.format)) fail("CDS_EXPORT_REPORT_FORMAT_MISMATCH");
            if (report.path != null) {
                relativeReference(report.path);
                references.accept(new CdsExportReportReferenceDto(report.ordinal, report.path, report.extension));
                referenceCount++;
            }
            if (report.text) textReports++;
            if (binary) binaryReports++;
        }

        private void dictionaryLength(Frame frame) throws IOException {
            var type = frame.scalar.type();
            if (type == null || !"cds_dt".equals(type.getTypeNamespace())) return;
            long maximum = switch (type.getTypeName()) {
                case "text1K" -> 1000;
                case "text32K" -> 32000;
                case "text64K" -> 64000;
                default -> Long.MAX_VALUE;
            };
            // Guide 1.10 defines k=1000, whereas these XSD aliases use 1024 multiples.
            // Refuse the ambiguous range for explicit review; never shorten clinical text.
            if (frame.scalar.normalizedCharacters() > maximum) {
                fail("CDS_EXPORT_DICTIONARY_LENGTH_REQUIRES_REVIEW");
            }
        }

        private void identity(Frame frame, char[] chars, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                boolean token = !CdsScalarParser.xmlSpace(chars[i]);
                if (token && !frame.identityToken) identityValues++;
                frame.identityToken = token;
                if (++identityCharacters > limits.maxIdentityCharacters() || identityValues > limits.maxIdentityValues()) {
                    fail("CDS_EXPORT_XML_IDENTITY_LIMIT");
                }
            }
        }
        private void name(String name) throws IOException {
            if (name.length() > MAX_NAME) fail("CDS_EXPORT_XML_NAME_LIMIT");
            names.add(name);
            if (names.size() > MAX_NAMES) fail("CDS_EXPORT_XML_NAME_LIMIT");
        }
        private void check() throws IOException {
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) fail("CDS_EXPORT_CANCELLED");
            if ((System.nanoTime() - started) / 1_000_000L > limits.maxDurationMillis()) fail("CDS_EXPORT_XML_DEADLINE");
        }
        @Override public void warning(SAXParseException failure) throws SAXException {
            throw new SAXException(new IOException("CDS_EXPORT_XML_SCHEMA_WARNING"));
        }
        @Override public void error(SAXParseException failure) throws SAXException {
            throw new SAXException(new IOException("CDS_EXPORT_XML_SCHEMA_INVALID"));
        }
        @Override public void fatalError(SAXParseException failure) throws SAXException { error(failure); }
    }

    /** Forward-slash relative references only; never decode or normalize a different identity. */
    public static void relativeReference(String path) throws IOException {
        if (path == null || path.isEmpty() || path.length() > 8192 || path.startsWith("/") || path.contains("\\")) {
            fail("CDS_EXPORT_REPORT_REFERENCE_INVALID");
        }
        for (String component : path.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || !component.equals(component.strip()) || component.endsWith(".") || component.indexOf(':') >= 0) {
                fail("CDS_EXPORT_REPORT_REFERENCE_INVALID");
            }
            for (int cp : component.codePoints().toArray()) {
                if (Character.isISOControl(cp) || "<>\"|?*".indexOf(cp) >= 0) fail("CDS_EXPORT_REPORT_REFERENCE_INVALID");
            }
        }
    }

    private static void fail(String code) throws IOException { throw new IOException(code); }
    private static final class Frame {
        final String uri;
        final String local;
        final CdsScalarParser scalar = new CdsScalarParser();
        final StringBuilder value = new StringBuilder();
        boolean child;
        boolean capture;
        String identityField;
        boolean identityToken;
        Frame(String uri, String local) { this.uri = uri; this.local = local; }
    }
    private static final class Report {
        final long ordinal;
        String format;
        String extension;
        String path;
        boolean text;
        boolean binary;
        Report(long ordinal) { this.ordinal = ordinal; }
    }
    private static final class CountingInput extends FilterInputStream {
        final MessageDigest digest;
        final long maximum;
        final Runnable checkpoint;
        long count;
        CountingInput(InputStream input, long maximum, Runnable checkpoint) {
            super(input); this.maximum = maximum; this.checkpoint = checkpoint;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        @Override public int read() throws IOException {
            int value = in.read();
            if (value >= 0) { check(1); digest.update((byte) value); }
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int n = in.read(bytes, offset, length);
            if (n > 0) { check(n); digest.update(bytes, offset, n); }
            return n;
        }
        private void check(int length) throws IOException {
            checkpoint.run(); count = Math.addExact(count, length);
            if (Thread.currentThread().isInterrupted()) fail("CDS_EXPORT_CANCELLED");
            if (count > maximum) fail("CDS_EXPORT_XML_BYTE_LIMIT");
        }
        @Override public void close() { /* Input belongs to the caller. */ }
    }
}
