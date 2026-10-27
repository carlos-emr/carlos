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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsParseResultDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsXmlText;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsCorrectionFiles;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.TypeInfoProvider;
import javax.xml.validation.ValidatorHandler;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.w3c.dom.TypeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Service;

/**
 * Streaming CDS envelope/schema validation and immutable occurrence/field spooling.
 *
 * <p>Every instance character is parsed by the hardened XML reader. The pinned XSD sees the
 * original structure, namespaces, choices and bounded scalar values. Only a schema-type/facet
 * proven unconstrained text or binary scalar is replaced in that event stream: unrestricted
 * text needs no further lexical check beyond XML characters, while binary is independently
 * decoded and checked for the exact XML Schema Base64 grammar, including canonical pad bits.
 * Thus the XSD validator never retains an entire attachment or unbounded ResidualInfo value.
 * The original input and original text remain preserved; this is not validation of an
 * arbitrary reduced document. A changed schema hash/type/facet cannot silently use this path.</p>
 */
@Service
public class CdsXmlParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> SECTIONS = Set.of("Demographics", "PersonalHistory", "FamilyHistory",
            "PastHealth", "ProblemList", "RiskFactors", "AllergiesAndAdverseReactions",
            "MedicationsAndTreatments", "Immunizations", "LaboratoryResults", "Appointments",
            "ClinicalNotes", "Reports", "CareElements", "AlertsAndSpecialNeeds", "NewCategory");

    /** Emitted descriptors are preflight data, never permission for clinical writes before success. */
    public CdsParseResultDto parse(Path sourceXml, Path privateSpoolDirectory, CdsInputLimitsDto limits,
            BooleanSupplier cancelled, Consumer<CdsRecordDto> records) throws IOException {
        return parse(sourceXml, privateSpoolDirectory, limits, cancelled, records, null);
    }

    /** Reviewed scalar changes affect validation only; all captured lexical bytes stay original. */
    public CdsParseResultDto parse(Path sourceXml, Path privateSpoolDirectory, CdsInputLimitsDto limits,
            BooleanSupplier cancelled, Consumer<CdsRecordDto> records, CdsCorrectionSetDto corrections) throws IOException {
        try { return parseInternal(sourceXml, privateSpoolDirectory, limits, cancelled, records, corrections); }
        catch (CdsInputException safe) { throw safe; }
        catch (IOException failed) {
            String protectedDetail = failed.getMessage();
            if (protectedDetail != null && protectedDetail.length() > 4096) protectedDetail = protectedDetail.substring(0, 4096);
            throw new CdsInputException("CDS_XML_READ_FAILED", "", protectedDetail);
        }
    }

    private CdsParseResultDto parseInternal(Path sourceXml, Path privateSpoolDirectory, CdsInputLimitsDto limits,
            BooleanSupplier cancelled, Consumer<CdsRecordDto> records, CdsCorrectionSetDto corrections) throws IOException {
        if (limits == null || cancelled == null || records == null) throw new IllegalArgumentException("Missing CDS parser context");
        Path parent = privateSpoolDirectory.toAbsolutePath().getParent();
        Path spool = CdsPaths.privateDirectory(privateSpoolDirectory, parent);
        CdsCorrectionSetDto staged=corrections==null ? null : CdsCorrectionFiles.stage(corrections, spool);
        Path fields = spool.resolve("fields.jsonl");
        Path values = spool.resolve("values.bin");
        Path manifest = spool.resolve("records.jsonl");
        Path diagnostics = spool.resolve("validation-errors.jsonl");
        MessageDigest sourceHash = CdsPaths.sha256();
        try (InputStream raw = Files.newInputStream(sourceXml, LinkOption.NOFOLLOW_LINKS);
                CountingInput counted = new CountingInput(raw, limits.maxSourceBytes());
                DigestInputStream hashed = new DigestInputStream(counted, sourceHash);
                Reader text = CdsXmlText.reader(hashed, limits.maxBoundedScalarCharacters());
                Pipeline pipeline = new Pipeline(spool, fields, values, manifest, diagnostics, limits, cancelled, records, staged)) {
            XMLReader reader = xmlReader();
            reader.setContentHandler(pipeline);
            reader.setErrorHandler(pipeline);
            reader.parse(new InputSource(text));
            String sourceDigest=CdsPaths.hex(sourceHash.digest());
            pipeline.finish(sourceDigest, counted.count);
            return new CdsParseResultDto(sourceDigest, counted.count,
                    pipeline.records, pipeline.fields, pipeline.binaryBytes, manifest, values,
                    pipeline.valuesSha256, pipeline.valueOutput.count);
        } catch (CdsInputException failure) {
            throw failure;
        } catch (SAXException failure) {
            Throwable nested = failure.getException();
            if (nested instanceof CdsInputException safe) throw safe;
            throw new CdsInputException("CDS_XML_INVALID", "", "See protected validation-errors.jsonl");
        } catch (javax.xml.parsers.ParserConfigurationException failure) {
            throw new IllegalStateException("CDS_SECURE_XML_PARSER_UNAVAILABLE");
        }
    }

    static XMLReader xmlReader() throws javax.xml.parsers.ParserConfigurationException, SAXException {
        SAXParserFactory factory = new SAXParserFactoryImpl();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("CDS_EXTERNAL_ENTITY_BLOCKED"); });
        return reader;
    }

    private static final class Pipeline extends DefaultHandler implements AutoCloseable {
        private final Path spool;
        private final Path fieldsPath;
        private final Path valuesPath;
        private final Path manifestPath;
        private final Path diagnosticsPath;
        private final CdsInputLimitsDto limits;
        private final BooleanSupplier cancelled;
        private final Consumer<CdsRecordDto> consumer;
        private final ValidatorHandler validator;
        private final TypeInfoProvider types;
        private final CountedOutput fieldOutput;
        private final ValueOutput valueOutput;
        private final Writer valueWriter;
        private final CountedOutput manifestOutput;
        private final CountedOutput diagnosticOutput;
        private final Deque<Frame> stack = new ArrayDeque<>();
        private final Map<String, Long> sectionOrdinals = new HashMap<>();
        // Xerces' symbol table retains names for the document. Bound namespace/name diversity,
        // including invalid input, so new aliases cannot grow heap with occurrence count.
        private final Set<String> prefixes = new HashSet<>();
        private final Set<String> namespaceUris = new HashSet<>();
        private final Set<String> qualifiedNames = new HashSet<>();
        private final long started = System.nanoTime();
        private Locator locator;
        private MessageDigest recordDigest;
        private String section;
        private long sectionOrdinal;
        private long recordFieldsOffset;
        private long recordFieldsBefore;
        private long records;
        private long fields;
        private long binaryBytes;
        private long patients;
        private long schemaErrors;
        private long schemaWarnings;
        private boolean rootSeen;
        private long nextDiskCheck;
        private long nextCancellationCheck;
        private String valuesSha256;
        private final CdsCorrectionSetDto corrections;
        private final Map<String, JsonNode> reviewed=new HashMap<>();
        private final Set<String> applied=new HashSet<>();
        private final CdsFieldParser correctionParser=new CdsFieldParser();
        private Frame closingScalar;

        Pipeline(Path spool, Path fields, Path values, Path manifest, Path diagnostics,
                CdsInputLimitsDto limits, BooleanSupplier cancelled, Consumer<CdsRecordDto> consumer, CdsCorrectionSetDto corrections)
                throws IOException {
            this.spool = spool; fieldsPath = fields; valuesPath = values; manifestPath = manifest;
            diagnosticsPath = diagnostics; this.limits = limits; this.cancelled = cancelled; this.consumer = consumer;
            this.corrections=corrections;
            if (corrections!=null) for (JsonNode entry:correctionParser.correctionEntries(corrections, corrections.manifestFile())) {
                String path=entry.path("sourceXPath").asText();
                if (path.isEmpty() || reviewed.put(path, entry)!=null) throw CdsPaths.failure("CDS_CORRECTION_TARGET_AMBIGUOUS");
            }
            fieldOutput = new CountedOutput(new BufferedOutputStream(CdsPaths.newPrivateFile(fields, spool), 65536));
            valueOutput = new ValueOutput(new BufferedOutputStream(CdsPaths.newPrivateFile(values, spool), 65536));
            valueWriter = new OutputStreamWriter(valueOutput, StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
            manifestOutput = new CountedOutput(new BufferedOutputStream(CdsPaths.newPrivateFile(manifest, spool), 65536));
            diagnosticOutput = new CountedOutput(new BufferedOutputStream(CdsPaths.newPrivateFile(diagnostics, spool), 65536));
            validator = CdsSchemaValidator.newValidator();
            types = validator.getTypeInfoProvider();
            validator.setContentHandler(new DefaultHandler() {
                @Override public void startElement(String uri, String local, String qualified, Attributes attrs)
                        throws SAXException {
                    Frame frame = stack.peek();
                    frame.configure(types.getElementTypeInfo());
                    if (frame.correction!=null && (frame.mode==Mode.BINARY || frame.mode==Mode.COMPLEX)) {
                        throw new SAXException(CdsPaths.failure("CDS_CORRECTION_TARGET_NOT_SCALAR"));
                    }
                    for (int i = 0; i < attrs.getLength(); i++) {
                        frame.attributeTypes.put(attrs.getURI(i) + "\u0000" + attrs.getLocalName(i), types.getAttributeTypeInfo(i));
                    }
                }
                @Override public void characters(char[] chars, int offset, int length) throws SAXException {
                    if (closingScalar!=null) {
                        if (closingScalar.schemaDefault.length()+length>64) throw new SAXException(CdsPaths.failure("CDS_SCHEMA_DEFAULT_RESOURCE_LIMIT"));
                        closingScalar.schemaDefault.append(chars, offset, length);
                    }
                }
            });
            validator.setErrorHandler(new DefaultHandler() {
                @Override public void warning(SAXParseException warning) throws SAXException {schemaDiagnostic(warning,false);}
                @Override public void error(SAXParseException failure) throws SAXException { schemaError(failure); }
                @Override public void fatalError(SAXParseException failure) throws SAXException { schemaError(failure); throw failure; }
            });
        }

        @Override public void setDocumentLocator(Locator value) { locator = value; validator.setDocumentLocator(value); }
        @Override public void startDocument() throws SAXException { validator.startDocument(); }
        @Override public void endDocument() throws SAXException { validator.endDocument(); }
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {
            try {
                if (prefix.length() > limits.maxXmlNameCharacters() || uri.length() > limits.maxNamespaceUriCharacters()) fail("CDS_XML_NAMESPACE_RESOURCE_LIMIT");
                prefixes.add(prefix); namespaceUris.add(uri);
                if (prefixes.size() > limits.maxNamespaceAliases() || namespaceUris.size() > limits.maxNamespaceAliases()) fail("CDS_XML_NAMESPACE_RESOURCE_LIMIT");
                validator.startPrefixMapping(prefix, uri);
            } catch (IOException failure) { throw protectedFailure(failure); }
        }
        @Override public void endPrefixMapping(String prefix) throws SAXException { validator.endPrefixMapping(prefix); }

        @Override public void startElement(String uri, String local, String qualified, Attributes attrs)
                throws SAXException {
            try {
                check();
                valueWriter.flush();
                name(qualified);
                if (attrs.getLength() > 128) fail("CDS_XML_ATTRIBUTE_RESOURCE_LIMIT");
                for (int i = 0; i < attrs.getLength(); i++) name(attrs.getQName(i));
                if (stack.size() >= limits.maxXmlDepth()) fail("CDS_XML_DEPTH_LIMIT");
                if (!rootSeen) {
                    if (!stack.isEmpty() || !uri.equals("cds") || !local.equals("OmdCds")) fail("CDS_XML_ENVELOPE_INVALID");
                    rootSeen = true;
                } else if (stack.isEmpty()) fail("CDS_XML_ENVELOPE_INVALID");
                if (stack.size() == 1) {
                    if (!uri.equals("cds") || !local.equals("PatientRecord") || ++patients != 1) fail("CDS_XML_PATIENT_CARDINALITY");
                }
                long ordinal = 1;
                String relative = "";
                if (!stack.isEmpty()) {
                    Frame parent = stack.peek();
                    parent.child = true;
                    String key = uri + "\u0000" + local;
                    if (!parent.children.containsKey(key) && parent.children.size() >= 128) fail("CDS_XML_CHILD_NAME_LIMIT");
                    ordinal = parent.children.merge(key, 1L, Math::addExact);
                    relative = parent.relative.isEmpty() ? local + "[" + ordinal + "]" : parent.relative + "/" + local + "[" + ordinal + "]";
                }
                if (stack.size() == 2) {
                    if (!uri.equals("cds") || !SECTIONS.contains(local)) fail("CDS_XML_SECTION_INVALID");
                    section = local;
                    sectionOrdinal = sectionOrdinals.merge(section, 1L, Math::addExact);
                    recordFieldsOffset = fieldOutput.count; recordFieldsBefore = fields;
                    recordDigest = CdsPaths.sha256(); relative = "";
                }
                if (relative.length() > limits.maxPathCharacters()) fail("CDS_XML_PATH_LIMIT");
                Frame frame = new Frame(uri, local, relative);
                frame.correction=section==null ? null : reviewed.get(sourcePath(relative));
                stack.push(frame);
                Attributes validatedAttrs=attrs;
                for (int i=0; i<attrs.getLength() && section!=null; i++) {
                    String path=relative+(relative.isEmpty() ? "" : "/")+"@"+attrs.getLocalName(i);
                    JsonNode change=reviewed.get(sourcePath(path));
                    if (change!=null) {
                        if (!attrs.getURI(i).isEmpty() || !change.path("kind").asText().equals("ATTRIBUTE")) fail("CDS_CORRECTION_STRUCTURAL_ATTRIBUTE_REFUSED");
                        if (validatedAttrs==attrs) validatedAttrs=new AttributesImpl(attrs);
                        ((AttributesImpl) validatedAttrs).setValue(i, CdsFieldParser.replacementLexical(change, corrections));
                    }
                }
                validator.startElement(uri, local, qualified, validatedAttrs);
                if (section != null) {
                    for (int i = 0; i < attrs.getLength(); i++) {
                        String value = attrs.getValue(i);
                        frame.beginValue(valueOutput);
                        valueWriter.write(value); valueWriter.flush();
                        String path = relative + (relative.isEmpty() ? "" : "/") + "@" + attrs.getLocalName(i);
                        writeField(frame, path, attrs.getURI(i), "ATTRIBUTE",
                                frame.attributeTypes.get(attrs.getURI(i) + "\u0000" + attrs.getLocalName(i)));
                    }
                    frame.beginValue(valueOutput);
                }
            } catch (IOException failed) { throw protectedFailure(failed); }
        }

        @Override public void characters(char[] chars, int offset, int length) throws SAXException {
            try {
                check();
                Frame frame = stack.peek();
                if (frame == null) { validator.characters(chars, offset, length); return; }
                if (frame.mode == Mode.BINARY) {
                    if (frame.child) fail("CDS_BINARY_CHILD_INVALID");
                    frame.base64(chars, offset, length, valueOutput, limits.maxBinaryBytes());
                } else {
                    if (section != null && !frame.child && frame.mode != Mode.COMPLEX) valueWriter.write(chars, offset, length);
                    if (frame.correction==null) frame.validate(chars, offset, length, validator, limits.maxBoundedScalarCharacters());
                }
            } catch (IOException failed) { throw protectedFailure(failed); }
        }

        @Override public void ignorableWhitespace(char[] chars, int offset, int length) throws SAXException { characters(chars, offset, length); }

        @Override public void endElement(String uri, String local, String qualified) throws SAXException {
            try {
                Frame frame = stack.peek();
                boolean scalar=section!=null && !frame.child;
                if (scalar) {
                    if (frame.correction!=null) {
                        valueWriter.flush();
                        frame.completedDigest=CdsPaths.hex(valueOutput.fieldDigest.digest());
                        CdsFieldDto original=descriptor(frame, frame.relative, frame.namespace, "TEXT", frame.type);
                        CdsFieldParser.verifyCorrection(frame.correction, original);
                        String replacement=CdsFieldParser.replacementLexical(frame.correction, corrections);
                        char[] chars=replacement.toCharArray();
                        frame.validate(chars, 0, chars.length, validator, limits.maxBoundedScalarCharacters());
                    }
                    frame.finishScalar(validator);
                    valueWriter.flush();
                }
                closingScalar=scalar ? frame : null;
                try {validator.endElement(uri, local, qualified); } finally {closingScalar=null; }
                if (scalar) {
                    writeField(frame, frame.relative, frame.namespace,
                            frame.mode == Mode.BINARY ? "BINARY" : "TEXT", frame.type);
                    if (frame.mode == Mode.BINARY) binaryBytes = Math.addExact(binaryBytes, valueOutput.count - frame.valueStart);
                }
                if (stack.size() == 3) {
                    fieldOutput.flush(); valueWriter.flush(); valueOutput.flush();
                    CdsRecordDto record = new CdsRecordDto(section, sectionOrdinal,
                            "/OmdCds[1]/PatientRecord[1]/" + section + "[" + sectionOrdinal + "]",
                            fieldsPath, recordFieldsOffset, fieldOutput.count - recordFieldsOffset,
                            valuesPath, CdsPaths.hex(recordDigest.digest()), fields - recordFieldsBefore);
                    if (corrections!=null) record=record.withCorrections(corrections);
                    ObjectNode row = JSON.createObjectNode().put("section", record.section()).put("ordinal", record.ordinal())
                            .put("sourceXPath", record.sourceXPath()).put("fieldsFile", fieldsPath.getFileName().toString())
                            .put("fieldsOffset", record.fieldsOffset()).put("fieldsLength", record.fieldsLength())
                            .put("valuesFile", valuesPath.getFileName().toString()).put("sha256", record.sha256())
                            .put("fieldCount", record.fieldCount());
                    if (corrections!=null) row.set("corrections", JSON.valueToTree(corrections));
                    manifestOutput.write(JSON.writeValueAsBytes(row)); manifestOutput.write('\n'); manifestOutput.flush();
                    records++; consumer.accept(record); section = null; recordDigest = null;
                }
                stack.pop();
            } catch (IOException failed) { throw protectedFailure(failed); }
        }

        private void writeField(Frame frame, String path, String namespace, String kind, TypeInfo type)
                throws IOException {
            CdsFieldDto field=descriptor(frame, path, namespace, kind, type);
            JsonNode change=reviewed.get(sourcePath(path));
            if (change!=null) {
                CdsFieldParser.verifyCorrection(change, field);
                if (!applied.add(sourcePath(path))) fail("CDS_CORRECTION_TARGET_AMBIGUOUS");
            }
            frame.completedDigest=null;
            byte[] row = JSON.writeValueAsBytes(field);
            fieldOutput.write(row); fieldOutput.write('\n');
            recordDigest.update(CdsFieldParser.identity(field));
            fields++;
            valueOutput.fieldDigest = CdsPaths.sha256();
        }

        private CdsFieldDto descriptor(Frame frame, String path, String namespace, String kind, TypeInfo type) {
            String schemaType = type == null ? "" : "{" + type.getTypeNamespace() + "}" + type.getTypeName();
            return new CdsFieldDto(path, namespace, kind, frame.valueStart,
                    valueOutput.count - frame.valueStart, frame.completedDigest==null ? CdsPaths.hex(valueOutput.fieldDigest.digest()) : frame.completedDigest,
                    schemaType, Frame.whitespace(type), kind.equals("TEXT") ? frame.schemaDefault.toString() : "");
        }

        private String sourcePath(String relative) {
            return "/OmdCds[1]/PatientRecord[1]/"+section+"["+sectionOrdinal+"]"+(relative.isEmpty() ? "" : "/"+relative);
        }

        @Override public void error(SAXParseException failure) throws SAXException { schemaError(failure); }
        @Override public void fatalError(SAXParseException failure) throws SAXException { schemaError(failure); throw failure; }

        private void schemaError(SAXParseException failure) throws SAXException {schemaDiagnostic(failure,true);}

        @Override public void warning(SAXParseException warning) throws SAXException {schemaDiagnostic(warning,false);}

        private void schemaDiagnostic(SAXParseException failure,boolean error) throws SAXException {
            if(error)schemaErrors++;else schemaWarnings++;
            String detail = failure.getMessage();
            if (detail != null && detail.length() > 4096) detail = detail.substring(0, 4096);
            ObjectNode row = JSON.createObjectNode().put("code", error?"CDS_XML_SCHEMA_INVALID":"CDS_XML_SCHEMA_WARNING")
                    .put("severity",error?"ERROR":"WARNING").put("blocking",error)
                    .put("sourceXPath", xpath()).put("line", failure.getLineNumber())
                    .put("column", failure.getColumnNumber()).put("protectedDetailPreview", detail)
                    .put("detailPreviewLimited", failure.getMessage() != null && failure.getMessage().length() > 4096);
            try { diagnosticOutput.write(JSON.writeValueAsBytes(row)); diagnosticOutput.write('\n'); }
            catch (IOException unwritable) { throw new SAXException(CdsPaths.failure("CDS_DIAGNOSTIC_WRITE_FAILED")); }
        }

        private void check() throws IOException {
            long now = System.nanoTime();
            if (Thread.currentThread().isInterrupted()) fail("CDS_INPUT_CANCELLED");
            if (now >= nextCancellationCheck) {
                if (cancelled.getAsBoolean()) fail("CDS_INPUT_CANCELLED");
                nextCancellationCheck = now + 250_000_000L;
            }
            if ((now - started) / 1_000_000 > limits.maxDurationMillis()) fail("CDS_INPUT_DEADLINE");
            long spoolBytes = Math.addExact(Math.addExact(valueOutput.count, fieldOutput.count),
                    Math.addExact(manifestOutput.count, diagnosticOutput.count));
            if (spoolBytes >= nextDiskCheck) {
                if (Files.getFileStore(spool).getUsableSpace() < limits.minimumFreeDiskBytes()) fail("CDS_INPUT_DISK_RESERVE");
                nextDiskCheck = Math.addExact(spoolBytes, 16L << 20);
            }
            if (spoolBytes > limits.maxExpandedBytes()) fail("CDS_INPUT_BYTE_LIMIT");
        }

        private void fail(String code) throws CdsInputException { throw new CdsInputException(code, xpath(), ""); }
        private void name(String qualified) throws CdsInputException {
            if (qualified.length() > limits.maxXmlNameCharacters()) fail("CDS_XML_NAME_RESOURCE_LIMIT");
            qualifiedNames.add(qualified);
            if (qualifiedNames.size() > limits.maxUniqueXmlNames()) fail("CDS_XML_NAME_RESOURCE_LIMIT");
        }
        private SAXException protectedFailure(IOException failure) {
            if (failure instanceof CdsInputException known) {
                return new SAXException(new CdsInputException(known.code(), xpath(), known.protectedDetail()));
            }
            String message = failure.getMessage();
            if (message != null && message.length() > 4096) message = message.substring(0, 4096);
            return new SAXException(new CdsInputException("CDS_SPOOL_IO_FAILED", xpath(), message));
        }
        private String xpath() {
            if (section == null) return "/OmdCds";
            String relative = stack.isEmpty() ? "" : stack.peek().relative;
            return "/OmdCds[1]/PatientRecord[1]/" + section + "[" + sectionOrdinal + "]" + (relative.isEmpty() ? "" : "/" + relative);
        }

        void finish(String sourceSha256, long sourceBytes) throws IOException {
            check();
            if (cancelled.getAsBoolean()) fail("CDS_INPUT_CANCELLED");
            if (!rootSeen || patients != 1 || !stack.isEmpty()) fail("CDS_XML_ENVELOPE_INVALID");
            if (corrections!=null && (!corrections.acceptedXmlSha256().equals(sourceSha256) || applied.size()!=reviewed.size())) fail("CDS_CORRECTION_SOURCE_XML_OR_TARGET_MISMATCH");
            fieldOutput.flush(); valueWriter.flush(); valueOutput.flush(); manifestOutput.flush(); diagnosticOutput.flush();
            CdsPaths.forceFile(fieldsPath); CdsPaths.forceFile(valuesPath); CdsPaths.forceFile(manifestPath); CdsPaths.forceFile(diagnosticsPath);
            if (schemaErrors != 0) {
                throw new CdsInputException("CDS_XML_SCHEMA_INVALID", "", "validation-errors.jsonl;count=" + schemaErrors);
            }
            valuesSha256 = CdsPaths.hex(valueOutput.fileDigest.digest());
            ObjectNode valuesMetadata = JSON.createObjectNode().put("version", 1).put("valuesFile", "values.bin")
                    .put("sha256", valuesSha256).put("length", valueOutput.count);
            byte[] metadataBytes=(JSON.writeValueAsString(valuesMetadata)+"\n").getBytes(StandardCharsets.UTF_8);
            try (OutputStream metadata = CdsPaths.newPrivateFile(spool.resolve("values-meta.json"), spool)) {
                metadata.write(metadataBytes);
            }
            CdsPaths.forceFile(spool.resolve("values-meta.json"));
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) fail("CDS_INPUT_CANCELLED");
            try (OutputStream seal = CdsPaths.newPrivateFile(spool.resolve("parse.seal"), spool)) {
                ObjectNode proof=JSON.createObjectNode().put("version", 2).put("sourceSha256", sourceSha256).put("sourceBytes", sourceBytes)
                        .put("schemaWarnings",schemaWarnings)
                        .put("correctionsSha256", corrections==null ? "" : corrections.sha256())
                        .put("records", records).put("fields", fields).put("valuesBytes", valueOutput.count).put("valuesSha256", valuesSha256)
                        .put("fieldsSha256", CdsPaths.hex(fieldOutput.digest.digest()))
                        .put("recordsSha256", CdsPaths.hex(manifestOutput.digest.digest()))
                        .put("metadataSha256", CdsPaths.hex(CdsPaths.sha256().digest(metadataBytes)));
                seal.write(JSON.writeValueAsBytes(proof)); seal.write('\n');
            }
            CdsPaths.forceFile(spool.resolve("parse.seal")); CdsPaths.forceDirectory(spool);
        }

        @Override public void close() throws IOException {
            IOException primary = null;
            for (AutoCloseable closeable : new AutoCloseable[]{valueWriter, valueOutput, fieldOutput, manifestOutput, diagnosticOutput}) {
                try { closeable.close(); }
                catch (Exception failed) { if (primary == null) primary = new IOException("CDS_SPOOL_CLOSE_FAILED"); else primary.addSuppressed(failed); }
            }
            if (primary != null) throw primary;
        }
    }

    private enum Mode { COMPLEX, BOUNDED, TEXT, LENGTH_TEXT, BINARY, INTEGER, DECIMAL }

    private static final class Frame {
        private final String namespace;
        private final String local;
        private final String relative;
        private final Map<String, Long> children = new HashMap<>();
        private final Map<String, TypeInfo> attributeTypes = new HashMap<>();
        private boolean child;
        private TypeInfo type;
        private JsonNode correction;
        private String completedDigest;
        private final StringBuilder schemaDefault=new StringBuilder(4);
        private Mode mode = Mode.COMPLEX;
        private String whitespace = "preserve";
        private long valueStart;
        private long validatedCharacters;
        private boolean started;
        private boolean pendingSpace;
        private int base64Count;
        private final int[] quartet = new int[4];
        private boolean padded;
        private long binaryCount;
        private byte[] binaryBuffer;
        private int binaryBuffered;
        private boolean numberSign;
        private boolean numberNegative;
        private boolean numberDigits;
        private boolean numberNonzero;
        private boolean numberDot;
        private boolean numberTrailingWhitespace;

        Frame(String namespace, String local, String relative) { this.namespace = namespace; this.local = local; this.relative = relative; }

        void configure(TypeInfo info) {
            type = info; whitespace = whitespace(info);
            if (!(info instanceof XSSimpleTypeDefinition simple)) { mode = Mode.COMPLEX; return; }
            mode = Mode.BOUNDED;
            short relevant = (short) (simple.getDefinedFacets() & ~XSSimpleTypeDefinition.FACET_WHITESPACE);
            if (simple.getVariety() == XSSimpleTypeDefinition.VARIETY_ATOMIC && relevant == 0) {
                if (simple.getPrimitiveType().getBuiltInKind() == XSConstants.STRING_DT) mode = Mode.TEXT;
                if (simple.getBuiltInKind() == XSConstants.BASE64BINARY_DT) { mode = Mode.BINARY; binaryBuffer = new byte[8192]; }
                if (simple.getBuiltInKind() == XSConstants.DECIMAL_DT) mode = Mode.DECIMAL;
            }
            short lengthFacets = XSSimpleTypeDefinition.FACET_LENGTH | XSSimpleTypeDefinition.FACET_MINLENGTH
                    | XSSimpleTypeDefinition.FACET_MAXLENGTH;
            if (simple.getVariety() == XSSimpleTypeDefinition.VARIETY_ATOMIC
                    && simple.getPrimitiveType().getBuiltInKind() == XSConstants.STRING_DT
                    && relevant != 0 && (relevant & ~lengthFacets) == 0) {
                // Xerces2.12 counts UTF16 units for string-length facets. XML Schema counts
                // Unicode characters. Only pure length/whitespace restrictions can use this
                // equivalent BMP witness; patterns/enumerations must see their actual value.
                mode = Mode.LENGTH_TEXT;
            }
            if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(simple.getNamespace())
                    && Set.of("integer", "nonNegativeInteger", "positiveInteger").contains(simple.getName())) {
                mode = Mode.INTEGER;
            }
        }

        static String whitespace(TypeInfo info) {
            if (info instanceof XSSimpleTypeDefinition simple) {
                String value = simple.getLexicalFacetValue(XSSimpleTypeDefinition.FACET_WHITESPACE);
                if (value != null && Set.of("preserve", "replace", "collapse").contains(value)) return value;
            }
            return "preserve";
        }

        void beginValue(ValueOutput output) { valueStart = output.count; output.fieldDigest = CdsPaths.sha256(); }

        void validate(char[] chars, int offset, int length, ValidatorHandler validator, int maximum)
                throws SAXException, IOException {
            if (mode == Mode.TEXT) return;
            if (mode == Mode.INTEGER || mode == Mode.DECIMAL) {
                for (int i = 0; i < length; i++) number(chars[offset + i]);
                return;
            }
            if (mode == Mode.COMPLEX) { validator.characters(chars, offset, length); return; }
            char[] normalized = new char[length + 1];
            int count = 0;
            for (int i = 0; i < length; i++) {
                char c = chars[offset + i];
                boolean space = xmlSpace(c);
                if (whitespace.equals("collapse") && space) { pendingSpace = started; continue; }
                if (pendingSpace) { normalized[count++] = ' '; validatedCharacters++; pendingSpace = false; }
                if (whitespace.equals("replace") && space) c = ' ';
                normalized[count++] = c;
                if (!Character.isLowSurrogate(c)) validatedCharacters++;
                started = true;
                if (validatedCharacters > maximum) throw CdsPaths.failure("CDS_XML_SCALAR_RESOURCE_LIMIT");
            }
            if (count > 0 && mode != Mode.LENGTH_TEXT) validator.characters(normalized, 0, count);
        }

        void number(char c) throws IOException {
            if (xmlSpace(c)) { if (numberDigits || numberSign || numberDot) numberTrailingWhitespace = true; return; }
            if (numberTrailingWhitespace) throw CdsPaths.failure("CDS_XML_NUMERIC_LEXICAL_INVALID");
            if ((c == '+' || c == '-') && !numberSign && !numberDigits && !numberDot) {
                numberSign = true; numberNegative = c == '-'; return;
            }
            if (c >= '0' && c <= '9') { numberDigits = true; numberNonzero |= c != '0'; return; }
            if (mode == Mode.DECIMAL && c == '.' && !numberDot) { numberDot = true; return; }
            throw CdsPaths.failure("CDS_XML_NUMERIC_LEXICAL_INVALID");
        }

        void base64(char[] chars, int offset, int length, OutputStream target, long maximum) throws IOException {
            for (int i = 0; i < length; i++) {
                char c = chars[offset + i];
                if (xmlSpace(c)) continue;
                if (padded) throw CdsPaths.failure("CDS_BASE64_TRAILING_DATA");
                int value = c == '=' ? -2 : decode(c);
                if (value == -1) throw CdsPaths.failure("CDS_BASE64_LEXICAL_INVALID");
                quartet[base64Count++] = value;
                if (base64Count == 4) {
                    int a = quartet[0], b = quartet[1], third = quartet[2], fourth = quartet[3];
                    if (a < 0 || b < 0 || (third == -2 && fourth != -2)) throw CdsPaths.failure("CDS_BASE64_PADDING_INVALID");
                    binaryByte((a << 2) | (b >> 4), target); binaryCount++;
                    if (third == -2) {
                        if ((b & 15) != 0) throw CdsPaths.failure("CDS_BASE64_PAD_BITS_INVALID");
                        padded = true;
                    } else {
                        binaryByte((b << 4) | (third >> 2), target); binaryCount++;
                        if (fourth == -2) {
                            if ((third & 3) != 0) throw CdsPaths.failure("CDS_BASE64_PAD_BITS_INVALID");
                            padded = true;
                        } else { binaryByte((third << 6) | fourth, target); binaryCount++; }
                    }
                    if (binaryCount > maximum) throw CdsPaths.failure("CDS_BINARY_BYTE_LIMIT");
                    base64Count = 0;
                }
            }
            if (binaryBuffered > 0) { target.write(binaryBuffer, 0, binaryBuffered); binaryBuffered = 0; }
        }

        private void binaryByte(int value, OutputStream target) throws IOException {
            binaryBuffer[binaryBuffered++] = (byte) value;
            if (binaryBuffered == binaryBuffer.length) {
                target.write(binaryBuffer, 0, binaryBuffered); binaryBuffered = 0;
            }
        }

        void finishScalar(ValidatorHandler validator) throws SAXException, IOException {
            if (mode == Mode.BINARY && base64Count != 0) throw CdsPaths.failure("CDS_BASE64_TRUNCATED");
            if (mode == Mode.LENGTH_TEXT) {
                char[] lengthWitness = new char[Math.toIntExact(validatedCharacters)];
                java.util.Arrays.fill(lengthWitness, 'x');
                if (lengthWitness.length > 0) validator.characters(lengthWitness, 0, lengthWitness.length);
            }
            if (mode == Mode.INTEGER || mode == Mode.DECIMAL) {
                if (!numberDigits) throw CdsPaths.failure("CDS_XML_NUMERIC_LEXICAL_INVALID");
                if (mode == Mode.INTEGER && type != null) {
                    if (type.getTypeName().equals("positiveInteger") && (numberNegative || !numberNonzero)) {
                        throw CdsPaths.failure("CDS_XML_NUMERIC_RANGE_INVALID");
                    }
                    if (type.getTypeName().equals("nonNegativeInteger") && numberNegative && numberNonzero) {
                        throw CdsPaths.failure("CDS_XML_NUMERIC_RANGE_INVALID");
                    }
                }
                char[] value = (mode == Mode.INTEGER && type.getTypeName().equals("positiveInteger") ? "1" : "0").toCharArray();
                validator.characters(value, 0, value.length);
            }
        }

        private static boolean xmlSpace(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }
        private static int decode(char c) {
            if (c >= 'A' && c <= 'Z') return c - 'A';
            if (c >= 'a' && c <= 'z') return c - 'a' + 26;
            if (c >= '0' && c <= '9') return c - '0' + 52;
            return c == '+' ? 62 : c == '/' ? 63 : -1;
        }
    }

    private static class CountedOutput extends OutputStream {
        private final OutputStream delegate;
        private final MessageDigest digest;
        protected long count;
        CountedOutput(OutputStream delegate) { this(delegate, true); }
        CountedOutput(OutputStream delegate, boolean hash) { this.delegate = delegate; digest=hash ? CdsPaths.sha256() : null; }
        @Override public void write(int value) throws IOException { delegate.write(value); count++; if (digest!=null) digest.update((byte) value); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length); count = Math.addExact(count, length);
            if (digest!=null) digest.update(bytes, offset, length);
        }
        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }

    private static final class ValueOutput extends CountedOutput {
        private MessageDigest fieldDigest = CdsPaths.sha256();
        private final MessageDigest fileDigest = CdsPaths.sha256();
        ValueOutput(OutputStream output) { super(output, false); }
        @Override public void write(int value) throws IOException { super.write(value); fieldDigest.update((byte) value); fileDigest.update((byte) value); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            super.write(bytes, offset, length); fieldDigest.update(bytes, offset, length); fileDigest.update(bytes, offset, length);
        }
    }

    private static final class CountingInput extends FilterInputStream {
        private final long maximum;
        private long count;
        CountingInput(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException {
            int value = in.read(); if (value >= 0) add(1); return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int n = in.read(bytes, offset, length); if (n > 0) add(n); return n;
        }
        private void add(int amount) throws IOException {
            count = Math.addExact(count, amount);
            if (count > maximum) throw CdsPaths.failure("CDS_INPUT_BYTE_LIMIT");
        }
    }
}
