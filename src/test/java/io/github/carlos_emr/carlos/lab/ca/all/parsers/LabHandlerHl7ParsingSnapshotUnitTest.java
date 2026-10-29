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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * Pins what every HAPI-backed lab handler extracts from the synthetic lab fixtures (issue #5414).
 *
 * <p>Each case feeds a fixture file through the production message splitter and the handler
 * class configured for its lab type in {@code message_config.xml}, renders every observable value
 * with {@link LabHandlerSnapshot}, and compares the result with a snapshot recorded under HAPI HL7
 * v2 1.0.1, the library release CARLOS used before this test existed. A difference means a HAPI
 * upgrade (or a handler change) altered a clinical field, a segment or repetition count, an OBX
 * value, unescaping, a timestamp or an error outcome, and must be reviewed rather than silently
 * accepted.</p>
 *
 * <p>Snapshots live under {@code src/test/resources/labs/hapi-characterization/golden}. After a
 * deliberate, reviewed behaviour change, regenerate them with
 * {@code mvn test -Dtest=LabHandlerHl7ParsingSnapshotUnitTest -Dcarlos.hl7.snapshot.update=true}
 * and explain each changed line in the pull request.</p>
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("Lab handler HL7 parsing snapshots")
class LabHandlerHl7ParsingSnapshotUnitTest {

    private static final Path LABS = Path.of("src", "test", "resources", "labs");
    private static final Path SYNTHETIC = LABS.resolve("hapi-characterization");
    private static final Path GOLDEN = SYNTHETIC.resolve("golden");
    private static final Path RESOURCES = Path.of("src", "test", "resources");
    private static final boolean UPDATE = Boolean.getBoolean("carlos.hl7.snapshot.update");
    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[(.*?)(?:]]>|\\z)", Pattern.DOTALL);
    /** Differing lines shown in a failure message before it is truncated. */
    private static final int MAX_REPORTED_DIFFERENCES = 40;

    @TempDir
    Path documentDir;

    private MockedStatic<SpringUtils> springUtils;

    @BeforeEach
    void mockSpringBeans() {
        // A few handlers look up Hl7TextInfoDao from getters; a default mock answers with empty
        // results so the snapshot depends only on the message.
        Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
        springUtils = mockStatic(SpringUtils.class);
        springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(invocation -> beans.computeIfAbsent(invocation.getArgument(0), type -> mock((Class<?>) type)));
    }

    @AfterEach
    void closeSpringBeans() {
        springUtils.close();
    }

    /** One fixture file run through one lab type's handler. */
    record Case(String id, String labType, Path source, Kind kind) {
        @Override
        public String toString() {
            return id;
        }
    }

    /** How a fixture file is turned into the message bodies a handler receives. */
    enum Kind {
        /** Plain HL7 text split with the production upload splitter. */
        SPLIT,
        /** One message per zip entry, the shape the existing handler tests use. */
        ZIP_ENTRY,
        /** Excelleris Ontario XML wrapper with one CDATA message per {@code Message} element. */
        EXCELLERIS_ON_XML
    }

    static Stream<Arguments> cases() throws IOException {
        List<Case> cases = new ArrayList<>();
        addDirectory(cases, LABS.resolve("HL7-CML"), "CML", ".hl7");
        addDirectory(cases, LABS.resolve("HL7-CLS"), "CLS", ".hl7");
        addDirectory(cases, LABS.resolve("HL7-Epsilon"), "EPSILON", ".hl7");
        try (Stream<Path> files = Files.list(LABS.resolve("Excelleris-ON"))) {
            files.filter(file -> file.toString().endsWith(".xml")).sorted()
                    .forEach(file -> cases.add(new Case("ExcellerisON/" + file.getFileName(), "ExcellerisON",
                            file, Kind.EXCELLERIS_ON_XML)));
        }
        cases.add(new Case("PATHL7/EXCELLERIS_test_data.zip", "PATHL7",
                RESOURCES.resolve("EXCELLERIS_test_data.zip"), Kind.ZIP_ENTRY));
        cases.add(new Case("MEDITECH/MEDITECH_test_data.zip", "MEDITECH",
                RESOURCES.resolve("MEDITECH_test_data.zip"), Kind.ZIP_ENTRY));
        cases.add(new Case("IHAPOI/IHAPOI_test_data.zip", "IHAPOI",
                RESOURCES.resolve("IHAPOI_test_data.zip"), Kind.ZIP_ENTRY));
        // Synthetic fixtures written for the upgrade: one directory per lab type.
        try (Stream<Path> types = Files.list(SYNTHETIC)) {
            for (Path typeDir : types.filter(Files::isDirectory)
                    .filter(dir -> !dir.equals(GOLDEN)).sorted().toList()) {
                addDirectory(cases, typeDir, typeDir.getFileName().toString(), ".hl7");
            }
        }
        return cases.stream().map(Arguments::of);
    }

    private static void addDirectory(List<Case> cases, Path dir, String labType, String extension) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(file -> file.toString().endsWith(extension)).sorted()
                    .forEach(file -> cases.add(new Case(labType + "/" + file.getFileName(), labType, file, Kind.SPLIT)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("should extract the recorded values for each lab fixture")
    void shouldExtractRecordedValues_forEachLabFixture(Case testCase) throws Exception {
        Class<? extends MessageHandler> handlerClass = handlerClassFor(testCase.labType());
        List<String> messages = messagesOf(testCase);
        assertThat(messages).as("fixture %s yields at least one message", testCase.id()).isNotEmpty();

        StringBuilder actual = new StringBuilder();
        // Handlers convert timestamps that carry an offset into the server's local time, so the
        // snapshots are recorded and compared in UTC whatever zone the build machine uses.
        TimeZone serverZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            for (int index = 0; index < messages.size(); index++) {
                actual.append("=== message ").append(index + 1).append(" of ").append(messages.size()).append(" ===\n");
                actual.append(LabHandlerSnapshot.render(handlerClass, messages.get(index)));
            }
        } finally {
            TimeZone.setDefault(serverZone);
        }

        Path golden = GOLDEN.resolve(testCase.id().replace('/', '_') + ".txt.gz");
        if (UPDATE) {
            Files.createDirectories(golden.getParent());
            writeGzip(golden, actual.toString());
            return;
        }
        if (!Files.exists(golden)) {
            fail("No snapshot recorded for " + testCase.id() + " at " + golden
                    + "; record one with -Dcarlos.hl7.snapshot.update=true and review it");
        }
        String expected = readGzip(golden);
        if (!expected.contentEquals(actual)) {
            fail("Values extracted from " + testCase.id() + " by " + handlerClass.getSimpleName()
                    + " differ from the recorded snapshot " + golden + ":\n"
                    + differences(expected, actual.toString()));
        }
    }

    /**
     * Snapshots are stored gzip-compressed: the larger vendor fixtures render to more than a
     * megabyte of text. Java writes a zero modification time in the gzip header, so regenerating
     * an unchanged snapshot reproduces the same bytes.
     */
    private static void writeGzip(Path file, String text) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readGzip(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Lists differing lines by number so a failure can be reviewed without unpacking the snapshot. */
    private static String differences(String expected, String actual) {
        List<String> expectedLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        StringBuilder report = new StringBuilder();
        int reported = 0;
        int lines = Math.max(expectedLines.size(), actualLines.size());
        for (int i = 0; i < lines && reported < MAX_REPORTED_DIFFERENCES; i++) {
            String want = i < expectedLines.size() ? expectedLines.get(i) : "<missing>";
            String got = i < actualLines.size() ? actualLines.get(i) : "<missing>";
            if (!want.equals(got)) {
                report.append("line ").append(i + 1).append("\n  recorded: ").append(want)
                        .append("\n  actual:   ").append(got).append('\n');
                reported++;
            }
        }
        if (expectedLines.size() != actualLines.size()) {
            report.append("recorded ").append(expectedLines.size()).append(" lines, actual ")
                    .append(actualLines.size()).append('\n');
        }
        return report.toString();
    }

    private List<String> messagesOf(Case testCase) throws Exception {
        return switch (testCase.kind()) {
            case SPLIT -> split(testCase.source());
            case ZIP_ENTRY -> zipEntries(testCase.source());
            case EXCELLERIS_ON_XML -> excellerisOntarioMessages(testCase.source());
        };
    }

    private List<String> split(Path fixture) throws Exception {
        Path feed = documentDir.resolve(fixture.getFileName().toString());
        Files.copy(fixture, feed);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        try (MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            return Utilities.separateMessages(feed.toString());
        }
    }

    private static List<String> zipEntries(Path zip) throws IOException {
        List<String> messages = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            List<? extends ZipEntry> entries = Collections.list((Enumeration<? extends ZipEntry>) zipFile.entries());
            entries.sort((left, right) -> left.getName().compareTo(right.getName()));
            for (ZipEntry entry : entries) {
                if (entry.getName().endsWith(".txt")) {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        messages.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return messages;
    }

    /**
     * Returns the CDATA body of each {@code Message} element, which is what the upload handler
     * routes. Three of the vendor sample files are truncated fragments rather than well-formed XML
     * (their last CDATA section never closes), so the sections are read directly instead of through
     * a DOM parser, and an unterminated final section runs to the end of the file. They serve here
     * only as HL7 text for the handler.
     */
    private static List<String> excellerisOntarioMessages(Path xml) throws IOException {
        Matcher cdata = CDATA.matcher(Files.readString(xml, StandardCharsets.UTF_8));
        List<String> messages = new ArrayList<>();
        while (cdata.find()) {
            messages.add(cdata.group(1));
        }
        return messages;
    }

    private static final Map<String, Class<? extends MessageHandler>> HANDLERS = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static synchronized Class<? extends MessageHandler> handlerClassFor(String labType) throws Exception {
        if (HANDLERS.isEmpty()) {
            try (InputStream config = LabHandlerHl7ParsingSnapshotUnitTest.class.getClassLoader()
                    .getResourceAsStream("io/github/carlos_emr/carlos/lab/ca/all/parsers/message_config.xml")) {
                for (Element handler : XmlUtils.createSecureSAXBuilder().build(config).getRootElement().getChildren()) {
                    Class<?> type;
                    try {
                        type = Class.forName(handler.getAttributeValue("className"), false,
                                LabHandlerHl7ParsingSnapshotUnitTest.class.getClassLoader());
                    } catch (ClassNotFoundException _) {
                        // Factory.getHandler falls back to DefaultGenericHandler for these too.
                        continue;
                    }
                    if (MessageHandler.class.isAssignableFrom(type)) {
                        HANDLERS.put(handler.getAttributeValue("name"), (Class<? extends MessageHandler>) type);
                    }
                }
            }
        }
        Class<? extends MessageHandler> handler = HANDLERS.get(labType);
        if (handler == null) {
            // DefaultGenericHandler is the fallback for unconfigured types in Factory.getHandler.
            return "DEFAULT".equals(labType) ? DefaultGenericHandler.class : fail("no handler for " + labType);
        }
        return handler;
    }
}
