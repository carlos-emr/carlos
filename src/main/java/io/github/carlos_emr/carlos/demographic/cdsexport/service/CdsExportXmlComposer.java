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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Streaming patient document composition. Category mappers receive only a scoped content
 * writer and cannot close a patient/section boundary. Emission receipts describe actual
 * serialized occurrence bytes; persist/sort them separately before the coverage comparison.
 * This composer does not establish mapping correctness or validate a finished package.
 */
public final class CdsExportXmlComposer {
    @FunctionalInterface
    public interface Content {
        void write(ContentWriter writer) throws IOException;
    }

    @FunctionalInterface
    public interface Dispositions {
        void emitted(CdsExportDispositionDto disposition) throws IOException;
    }

    @FunctionalInterface
    public interface Sections {
        void write(SectionEmitter output) throws IOException;
    }

    public record Section(CdsExportIdentityDto identity, Content content) {
        public Section {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(content);
        }

        @Override
        public String toString() {
            return "Section[protected]";
        }
    }

    public record Result(Map<CdsCategory, Long> occurrences, long xmlBytes) {
        public Result {
            occurrences = Map.copyOf(occurrences);
        }
    }

    /** Caller owns the private output and all source iterators/streams; failures never return a complete result. */
    public Result compose(
            OutputStream output,
            String captureDigest,
            String patientKey,
            String applicationVersion,
            CdsLogicalProfile profile,
            Set<CdsCategory> selectedCategories,
            Iterator<Section> sections,
            Dispositions dispositions,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(sections);
        return composeFromSource(
                output,
                captureDigest,
                patientKey,
                applicationVersion,
                profile,
                selectedCategories,
                emitter -> {
                    while (sections.hasNext()) {
                        checkpoint.run();
                        emitter.emit(sections.next());
                    }
                },
                dispositions,
                maximumBytes,
                checkpoint);
    }

    /**
     * Synchronous push equivalent for captured-row projections. The source must finish all
     * authenticated reads before returning. Output and receipts remain private until this
     * method and the independent coverage/finished-document checks succeed. No producer
     * thread, queue or collection of all patient occurrences is required.
     */
    public Result composeFromSource(
            OutputStream output,
            String captureDigest,
            String patientKey,
            String applicationVersion,
            CdsLogicalProfile profile,
            Set<CdsCategory> selectedCategories,
            Sections sections,
            Dispositions dispositions,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(output);
        Objects.requireNonNull(sections);
        Objects.requireNonNull(dispositions);
        Objects.requireNonNull(checkpoint);
        if (captureDigest == null
                || !captureDigest.matches("[0-9a-f]{64}")
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || maximumBytes < 1)
            throw new IllegalArgumentException("CDS_EXPORT_COMPOSITION_SCOPE_INVALID");
        Set<CdsCategory> selected = Set.copyOf(selectedCategories);
        if (!selected.contains(CdsCategory.DEMOGRAPHICS))
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHICS_REQUIRED");
        String header = new CdsExportHeaderComposer().comment(applicationVersion, profile);
        CountingOutput counted = new CountingOutput(output, maximumBytes, checkpoint);
        EnumMap<CdsCategory, Long> counts = new EnumMap<>(CdsCategory.class);
        selected.forEach(category -> counts.put(category, 0L));
        try {
            XMLStreamWriter xml =
                    XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(counted, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeComment(header);
            xml.writeStartElement("", "OmdCds", "cds");
            xml.writeDefaultNamespace("cds");
            xml.writeNamespace("cds", "cds");
            xml.writeNamespace("cdsd", "cds_dt");
            xml.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xml.writeNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xml.writeStartElement("", "PatientRecord", "cds");
            int[] last = {-1};
            SectionEmitter emitter =
                    new SectionEmitter(
                            section -> {
                                checkpoint.run();
                                CdsCategory category = section.identity().category();
                                if (!patientKey.equals(section.identity().patientKey())
                                        || !selected.contains(category)
                                        || category.ordinal() < last[0]
                                        || category == CdsCategory.DEMOGRAPHICS
                                                && counts.get(category) != 0)
                                    throw new IOException("CDS_EXPORT_SECTION_SCOPE_INVALID");
                                last[0] = category.ordinal();
                                // flush() alone leaves a StAX start tag pending. Close the parent
                                // tag before
                                // measuring this occurrence, so its receipt cannot include the
                                // parent's '>'.
                                try {
                                    xml.writeCharacters("");
                                    xml.flush();
                                    counted.beginOccurrence();
                                    xml.writeStartElement("", category.wireName(), "cds");
                                    ContentWriter content =
                                            new ContentWriter(
                                                    xml, checkpoint, () -> counted.failed = true);
                                    try {
                                        section.content().write(content);
                                    } finally {
                                        content.closed = true;
                                    }
                                    if (content.failed || counted.failed)
                                        throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
                                    if (content.depth != 0)
                                        throw new IOException("CDS_EXPORT_SECTION_UNCLOSED");
                                    xml.writeEndElement();
                                    xml.flush();
                                } catch (XMLStreamException failure) {
                                    throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
                                }
                                String digest = counted.endOccurrence();
                                dispositions.emitted(
                                        new CdsExportDispositionDto(
                                                captureDigest,
                                                section.identity(),
                                                CdsExportDispositionDto.Disposition.EMITTED,
                                                digest,
                                                null));
                                counts.compute(category, (key, value) -> Math.addExact(value, 1L));
                            },
                            () -> counted.failed = true);
            try {
                emitter.source(sections);
            } finally {
                emitter.closed = true;
            }
            if (counted.failed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
            if (counts.get(CdsCategory.DEMOGRAPHICS) != 1L)
                throw new IOException("CDS_EXPORT_DEMOGRAPHICS_REQUIRED");
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            checkpoint.run();
            if (counted.failed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
            return new Result(counts, counted.total);
        } catch (XMLStreamException failure) {
            throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
        }
    }

    @FunctionalInterface
    private interface SectionOutput {
        void emit(Section section) throws IOException;
    }

    /** Scoped, single-threaded producer capability; a swallowed write failure poisons the document. */
    public static final class SectionEmitter {
        private final SectionOutput output;
        private final Runnable poison;
        private final Thread owner = Thread.currentThread();
        private volatile boolean failed;
        private volatile boolean closed;
        private boolean writing;

        private SectionEmitter(SectionOutput output, Runnable poison) {
            this.output = output;
            this.poison = poison;
        }

        private int sourceDepth;

        /** Keeps acquisition, all emitted occurrences and final source authentication in one sticky failure scope. */
        public void source(Sections source) throws IOException {
            boolean entered = false;
            try {
                if (Thread.currentThread() != owner)
                    throw new IOException("CDS_EXPORT_SECTION_EMITTER_THREAD_REFUSED");
                if (closed || failed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
                if (writing || sourceDepth == 16)
                    throw new IOException("CDS_EXPORT_SECTION_EMITTER_REENTRANT");
                sourceDepth++;
                entered = true;
                Objects.requireNonNull(source).write(this);
                if (failed || closed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                poison.run();
                throw failure;
            } finally {
                if (entered) sourceDepth--;
            }
        }

        public void emit(Section section) throws IOException {
            boolean entered = false;
            try {
                if (Thread.currentThread() != owner)
                    throw new IOException("CDS_EXPORT_SECTION_EMITTER_THREAD_REFUSED");
                if (closed) throw new IOException("CDS_EXPORT_SECTION_EMITTER_CLOSED");
                if (failed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
                if (writing) throw new IOException("CDS_EXPORT_SECTION_EMITTER_REENTRANT");
                writing = true;
                entered = true;
                output.emit(Objects.requireNonNull(section));
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                poison.run();
                throw failure;
            } finally {
                if (entered) writing = false;
            }
        }
    }

    /** Short-lived mapper capability; text/binary inputs are streamed to EOF without closing caller-owned input. */
    public static final class ContentWriter {
        private final XMLStreamWriter xml;
        private final Runnable checkpoint;
        private final Runnable poison;
        private int depth;
        private boolean attributePhase;
        private boolean closed;
        private volatile boolean failed;
        private final Thread owner = Thread.currentThread();

        private ContentWriter(XMLStreamWriter xml, Runnable checkpoint, Runnable poison) {
            this.xml = xml;
            this.checkpoint = checkpoint;
            this.poison = poison;
            attributePhase = true;
        }

        /** Keeps source acquisition, complete reads and close-time authentication inside the document failure fence. */
        public void content(Content source) throws IOException {
            perform(() -> Objects.requireNonNull(source).write(this));
        }

        private int floor;

        /** Balanced child content cannot close its enclosing element, even if its callback catches an error. */
        public void fragment(Content source) throws IOException {
            perform(
                    () -> {
                        int previous = floor, boundary = depth;
                        floor = boundary;
                        try {
                            Objects.requireNonNull(source).write(this);
                            check();
                            if (depth != boundary)
                                throw new IOException("CDS_EXPORT_FRAGMENT_UNCLOSED");
                        } finally {
                            floor = previous;
                        }
                    });
        }

        public void start(String namespace, String name) throws IOException {
            perform(() -> doStart(namespace, name));
        }

        private void doStart(String namespace, String name) throws IOException {
            check();
            if (depth >= 252) throw new IOException("CDS_EXPORT_XML_DEPTH_LIMIT");
            name(name);
            String prefix = prefix(namespace, false);
            try {
                xml.writeStartElement(prefix, name, namespace);
                depth++;
                attributePhase = true;
            } catch (XMLStreamException failure) {
                throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
            }
        }

        public void end() throws IOException {
            perform(() -> doEnd());
        }

        private void doEnd() throws IOException {
            check();
            if (depth <= floor) throw new IOException("CDS_EXPORT_SECTION_BOUNDARY_REFUSED");
            try {
                xml.writeEndElement();
                depth--;
                attributePhase = false;
            } catch (XMLStreamException failure) {
                throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
            }
        }

        public void attribute(String namespace, String name, String value) throws IOException {
            perform(() -> doAttribute(namespace, name, value));
        }

        private void doAttribute(String namespace, String name, String value) throws IOException {
            check();
            name(name);
            String prefix = prefix(namespace, true);
            if (!attributePhase || value == null || value.length() > 65_536) {
                throw new IOException("CDS_EXPORT_XML_ATTRIBUTE_INVALID");
            }
            valid(value);
            // XML attribute normalization would change literal tabs, newlines and CRs. Pinned
            // schema attributes are bounded tokens/enums; preserve any exceptional source
            // lexical value through an explicit reviewed mapper rather than changing it here.
            if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IOException("CDS_EXPORT_ATTRIBUTE_NORMALIZATION_REQUIRES_REVIEW");
            }
            try {
                xml.writeAttribute(prefix, namespace, name, value);
            } catch (XMLStreamException failure) {
                throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
            }
        }

        public void element(String namespace, String name, String value) throws IOException {
            perform(() -> doElement(namespace, name, value));
        }

        private void doElement(String namespace, String name, String value) throws IOException {
            start(namespace, name);
            text(value);
            end();
        }

        public void text(String value) throws IOException {
            perform(() -> doText(value));
        }

        private void doText(String value) throws IOException {
            Objects.requireNonNull(value);
            text(new StringReader(value));
        }

        public void text(Reader value) throws IOException {
            perform(() -> doText(value));
        }

        private void doText(Reader value) throws IOException {
            check();
            if (depth == 0) throw new IOException("CDS_EXPORT_SECTION_SCALAR_REFUSED");
            attributePhase = false;
            char[] buffer = new char[8192];
            int n;
            char pending = 0;
            try {
                while ((n = value.read(buffer)) != -1) {
                    check();
                    if (n == 0) throw new IOException("CDS_EXPORT_XML_SOURCE_STALLED");
                    int offset = 0;
                    if (pending != 0) {
                        if (!Character.isLowSurrogate(buffer[0]))
                            throw new IOException("CDS_EXPORT_XML_CHARACTER_INVALID");
                        write(new char[] {pending, buffer[0]}, 0, 2);
                        pending = 0;
                        offset = 1;
                    }
                    int end = n;
                    if (offset < n && Character.isHighSurrogate(buffer[n - 1])) {
                        pending = buffer[n - 1];
                        end--;
                    }
                    if (end > offset) write(buffer, offset, end - offset);
                }
                if (pending != 0) throw new IOException("CDS_EXPORT_XML_CHARACTER_INVALID");
            } finally {
                java.util.Arrays.fill(buffer, '\u0000');
            }
        }

        private void write(char[] text, int offset, int length) throws IOException {
            valid(new String(text, offset, length));
            try {
                int start = offset;
                for (int i = offset; i < offset + length; i++) {
                    if (text[i] == '\r') {
                        if (i > start) xml.writeCharacters(text, start, i - start);
                        xml.writeEntityRef("#13");
                        start = i + 1;
                    }
                }
                if (start < offset + length)
                    xml.writeCharacters(text, start, offset + length - start);
            } catch (XMLStreamException failure) {
                throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
            }
        }

        public void binary(InputStream value) throws IOException {
            perform(() -> doBinary(value));
        }

        private void doBinary(InputStream value) throws IOException {
            check();
            if (depth == 0) throw new IOException("CDS_EXPORT_SECTION_SCALAR_REFUSED");
            Objects.requireNonNull(value);
            attributePhase = false;
            byte[] block = new byte[8190];
            int buffered = 0;
            try {
                while (true) {
                    check();
                    int count = value.read(block, buffered, block.length - buffered);
                    if (count == 0) throw new IOException("CDS_EXPORT_XML_SOURCE_STALLED");
                    if (count == -1) {
                        if (buffered > 0)
                            xml.writeCharacters(
                                    Base64.getEncoder()
                                            .encodeToString(
                                                    java.util.Arrays.copyOf(block, buffered)));
                        return;
                    }
                    if (count < -1 || count > block.length - buffered)
                        throw new IOException("CDS_EXPORT_XML_SOURCE_INVALID");
                    buffered += count;
                    if (buffered == block.length) {
                        xml.writeCharacters(Base64.getEncoder().encodeToString(block));
                        buffered = 0;
                    }
                }
            } catch (XMLStreamException failure) {
                throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
            } finally {
                java.util.Arrays.fill(block, (byte) 0);
            }
        }

        @FunctionalInterface
        private interface Operation {
            void run() throws IOException;
        }

        private void perform(Operation operation) throws IOException {
            try {
                check();
                operation.run();
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                poison.run();
                throw failure;
            }
        }

        private void check() throws IOException {
            if (closed) throw new IOException("CDS_EXPORT_SECTION_WRITER_CLOSED");
            if (failed) throw new IOException("CDS_EXPORT_SECTION_WRITE_FAILED");
            if (Thread.currentThread() != owner)
                throw new IOException("CDS_EXPORT_SECTION_WRITER_THREAD_REFUSED");
            checkpoint.run();
            if (Thread.currentThread().isInterrupted())
                throw new IOException("CDS_EXPORT_CANCELLED");
        }

        private static String prefix(String namespace, boolean attribute) throws IOException {
            return switch (namespace) {
                case "cds" -> attribute ? "cds" : "";
                case "cds_dt" -> "cdsd";
                case "" -> {
                    if (!attribute) throw new IOException("CDS_EXPORT_XML_NAMESPACE_INVALID");
                    yield "";
                }
                case "http://www.w3.org/2001/XMLSchema-instance" -> {
                    if (!attribute) throw new IOException("CDS_EXPORT_XML_NAMESPACE_INVALID");
                    yield "xsi";
                }
                default -> throw new IOException("CDS_EXPORT_XML_NAMESPACE_INVALID");
            };
        }

        private static void name(String name) throws IOException {
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,255}"))
                throw new IOException("CDS_EXPORT_XML_NAME_INVALID");
        }

        private static void valid(String text) throws IOException {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                        throw new IOException("CDS_EXPORT_XML_CHARACTER_INVALID");
                } else if (Character.isLowSurrogate(c)
                        || c < 32 && c != '\t' && c != '\n' && c != '\r'
                        || c == 0xfffe
                        || c == 0xffff) throw new IOException("CDS_EXPORT_XML_CHARACTER_INVALID");
            }
        }
    }

    private static final class CountingOutput extends FilterOutputStream {
        final long maximum;
        final Runnable checkpoint;
        long total;

        // Per-operation incremental digest, owned by this stream/row accumulator; never shared
        // singleton state.
        @SuppressWarnings("java:S9428")
        MessageDigest occurrence;

        volatile boolean failed;

        CountingOutput(OutputStream output, long maximum, Runnable checkpoint) {
            super(output);
            this.maximum = maximum;
            this.checkpoint = checkpoint;
        }

        void beginOccurrence() {
            try {
                occurrence = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        String endOccurrence() {
            String value = HexFormat.of().formatHex(occurrence.digest());
            occurrence = null;
            return value;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            checkpoint.run();
            if (length > maximum - total) throw new IOException("CDS_EXPORT_XML_BYTE_LIMIT");
            out.write(value, offset, length);
            total += length;
            if (occurrence != null) occurrence.update(value, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
