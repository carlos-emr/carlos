/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService.InvalidConfigurationException;
import io.github.carlos_emr.carlos.utility.XmlUtils;

@DisplayName("Antenatal risk configuration service")
@Tag("unit")
@Tag("decision")
class AntenatalRiskConfigServiceUnitTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final int MAX_PARENT_SEARCH_DEPTH = 5;
    private static final Path DEFAULT_RISK_CONFIG = Path.of(
            "src/main/webapp/decision/antenatal/desantenatalplannerrisks_99_12.xml");
    private static final String VALID_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <riskFactors>
              <section>
                <section_title>Other &amp; special &lt;risks&gt;</section_title>
                <subsection>
                  <subsection_title>History</subsection_title>
                  <risk name="risk101" href="https://example.test/risk">Stillbirth</risk>
                  <entry name="weeks">Gestational weeks</entry>
                  <heading href="/guidance/nutrition">Nutrition</heading>
                </subsection>
              </section>
            </riskFactors>
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("should parse, validate and serialize valid existing content")
    void shouldSave_validExistingContent() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        String existingContent = Files.readString(
                resolveProjectPath(DEFAULT_RISK_CONFIG),
                StandardCharsets.UTF_8);

        new AntenatalRiskConfigService(target).save(existingContent);

        // The point of this test is that the shipped default survives the grammar
        // and the round trip, so assert the shape rather than a risk count — adding
        // or removing a risk in that config is a routine edit that must not fail here.
        Document document = secureFactory().newDocumentBuilder().parse(target.toFile());
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("riskFactors");
        assertThat(document.getElementsByTagName("risk").getLength())
                .isEqualTo(countElements(existingContent, "risk"));
        assertThat(document.getElementsByTagName("section").getLength())
                .isEqualTo(countElements(existingContent, "section"));
    }

    /** Counts elements in the submitted source so assertions track the fixture, not a literal. */
    private static int countElements(String xml, String tagName) throws Exception {
        Document source = secureFactory().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        return source.getElementsByTagName(tagName).getLength();
    }

    @Test
    @DisplayName("should preserve XML-sensitive clinical text through API serialization")
    void shouldPreserve_xmlSensitiveText() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);

        new AntenatalRiskConfigService(target).save(VALID_XML);

        String stored = Files.readString(target, StandardCharsets.UTF_8);
        Document document = secureFactory().newDocumentBuilder().parse(target.toFile());
        assertThat(stored).contains("&amp;").contains("&lt;risks&gt;");
        assertThat(document.getElementsByTagName("section_title").item(0).getTextContent())
                .isEqualTo("Other & special <risks>");
    }

    @Test
    @DisplayName("should store byte-identical output when the stored document is saved again")
    void shouldStoreIdenticalOutput_whenSavedRepeatedly() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        AntenatalRiskConfigService service = new AntenatalRiskConfigService(target);
        service.save(Files.readString(resolveProjectPath(DEFAULT_RISK_CONFIG), StandardCharsets.UTF_8));
        String firstSave = Files.readString(target, StandardCharsets.UTF_8);

        // Re-saving what the editor now shows must be a fixed point. A serializer
        // that layers new indentation over the indentation of the previous save
        // (the JDK's built-in one does) grows the document on every edit.
        for (int round = 0; round < 3; round++) {
            service.save(Files.readString(target, StandardCharsets.UTF_8));
            assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(firstSave);
        }
        assertThat(firstSave.lines().filter(String::isBlank)).isEmpty();
    }

    @Test
    @DisplayName("should keep the permissions the existing configuration file carries")
    void shouldPreserve_existingFilePermissions() throws Exception {
        Path target = originalTarget();
        Set<PosixFilePermission> groupReadable = PosixFilePermissions.fromString("rw-r--r--");
        try {
            Files.setPosixFilePermissions(target, groupReadable);
        } catch (UnsupportedOperationException e) {
            // Reported as skipped, not passed: the assertion below never ran.
            abort("filesystem does not support POSIX permissions");
        }

        new AntenatalRiskConfigService(target).save(VALID_XML);

        // The atomic replacement must not silently tighten a file the operator left
        // readable for a sibling instance or backup tooling.
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(groupReadable);
    }

    @Test
    @DisplayName("should refuse to replace a target that is not a regular file")
    void shouldRefuse_whenTargetIsNotARegularFile() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        Files.createDirectory(target);

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(VALID_XML))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a readable file");
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    @DisplayName("should refuse to replace a symbolic link at the configured path")
    void shouldRefuse_whenTargetIsSymbolicLink() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        Path linkTarget = temporaryDirectory.resolve("elsewhere.xml");
        try {
            Files.createSymbolicLink(target, linkTarget);
        } catch (UnsupportedOperationException | IOException e) {
            abort("filesystem or platform does not support symbolic links");
        }

        // Dangling here, which is the case Files.exists() reports as absent — the
        // save would otherwise have replaced the link itself with a regular file.
        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(VALID_XML))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
        assertThat(Files.isSymbolicLink(target)).isTrue();
        assertThat(Files.exists(linkTarget)).isFalse();
    }

    @Test
    @DisplayName("should refuse to replace an unreadable existing configuration")
    void shouldRefuse_whenExistingConfigurationIsUnreadable() throws Exception {
        Path target = originalTarget();
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("-w--w--w-"));
        } catch (UnsupportedOperationException e) {
            abort("filesystem does not support POSIX permissions");
        }
        // A privileged process ignores the mode bits, so there is nothing to assert.
        assumeTrue(!Files.isReadable(target), "running as a user that bypasses file permissions");

        // Otherwise the mode would be carried onto the replacement and every reader
        // would fall back to the packaged default while the save reported success.
        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(VALID_XML))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a readable file");
    }

    @Test
    @DisplayName("should reject injected elements without changing the current file")
    void shouldReject_injectedElements() throws Exception {
        Path target = originalTarget();
        String injected = VALID_XML.replace("Stillbirth", "Stillbirth<script>alert(1)</script>");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(injected))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("text only");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject DOCTYPE and external entity payloads")
    void shouldReject_doctypeEntity() throws Exception {
        Path target = originalTarget();
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE riskFactors [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <riskFactors><section><section_title>&xxe;</section_title></section></riskFactors>
                """;

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(payload))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("DOCTYPE");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject malformed XML")
    void shouldReject_malformedXml() throws Exception {
        Path target = originalTarget();

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target)
                .save("<riskFactors><section></riskFactors>"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("well-formed");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject an attribute outside the allowlist")
    void shouldReject_unknownAttribute() throws Exception {
        Path target = originalTarget();
        String unknownAttribute = VALID_XML.replace("name=\"risk101\"", "name=\"risk101\" onclick=\"alert(1)\"");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(unknownAttribute))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("onclick");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject an unsafe link scheme")
    void shouldReject_unsafeLinkScheme() throws Exception {
        Path target = originalTarget();
        String unsafeLink = VALID_XML.replace("https://example.test/risk", "javascript:alert(1)");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(unsafeLink))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("HTTP");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a document larger than the accepted maximum")
    void shouldReject_oversizedDocument() throws Exception {
        Path target = originalTarget();
        String padding = "x".repeat(AntenatalRiskConfigService.MAX_XML_BYTES);
        String oversized = VALID_XML.replace("Stillbirth", "Stillbirth" + padding);

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(oversized))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("1 MiB");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a document that only exceeds the maximum once serialized")
    void shouldReject_whenSerializedOutputExceedsMaximum() throws Exception {
        Path target = originalTarget();
        // Under the cap as submitted; indentation added during serialization pushes it
        // over. Accepting it would store a document the editor then refuses to re-save.
        StringBuilder risks = new StringBuilder();
        for (int i = 0; risks.length() < AntenatalRiskConfigService.MAX_XML_BYTES - 20_000; i++) {
            risks.append("<risk name=\"risk").append(i).append("\">Risk ").append(i).append("</risk>");
        }
        String nearLimit = "<riskFactors><section><section_title>S</section_title>"
                + risks + "</section></riskFactors>";
        assertThat(nearLimit.length()).isLessThan(AntenatalRiskConfigService.MAX_XML_BYTES);

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(nearLimit))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("1 MiB");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a namespaced document")
    void shouldReject_namespacedRoot() throws Exception {
        Path target = originalTarget();
        String namespaced = VALID_XML.replace("<riskFactors>", "<riskFactors xmlns=\"urn:example\">");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(namespaced))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a document with no section")
    void shouldReject_documentWithoutSection() throws Exception {
        Path target = originalTarget();

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save("<riskFactors></riskFactors>"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("at least one");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject content outside the root element")
    void shouldReject_contentOutsideRoot() throws Exception {
        Path target = originalTarget();

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target)
                .save("<?xml version=\"1.0\"?><?stylesheet href=\"x\"?>" + VALID_XML.replaceAll("<\\?xml[^>]*\\?>", "")))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("Only comments");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject duplicate risk names")
    void shouldReject_duplicateFieldNames() throws Exception {
        Path target = originalTarget();
        String duplicated = "<riskFactors><section><section_title>S</section_title>"
                + "<risk name=\"dup\">One</risk><risk name=\"dup\">Two</risk></section></riskFactors>";

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(duplicated))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("unique");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a risk name outside the accepted pattern")
    void shouldReject_invalidFieldName() throws Exception {
        Path target = originalTarget();
        String badName = VALID_XML.replace("name=\"risk101\"", "name=\"1 bad name\"");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(badName))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("names must start with a letter");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should reject a risk that is missing its required name")
    void shouldReject_missingRequiredName() throws Exception {
        Path target = originalTarget();
        String missingName = "<riskFactors><section><section_title>S</section_title>"
                + "<risk>Unnamed</risk></section></riskFactors>";

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(missingName))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("requires attribute name");
        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    @DisplayName("should leave the current file intact when the atomic move fails")
    void shouldPreserve_fileOnWriteFailure() throws Exception {
        Path target = originalTarget();
        AntenatalRiskConfigService service = new AntenatalRiskConfigService(target, (source, destination) -> {
            throw new IOException("simulated move failure");
        });

        assertThatThrownBy(() -> service.save(VALID_XML))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("simulated");
        assertThat(Files.readString(target)).isEqualTo("original");
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactly(AntenatalRiskConfigService.FILE_NAME);
        }
    }

    @Test
    @DisplayName("should produce one complete document after concurrent saves")
    void shouldPreserve_concurrentWrites() throws Exception {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        AntenatalRiskConfigService service = new AntenatalRiskConfigService(target);
        int saveCount = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Future<?>> saves = new ArrayList<>();
        try {
            for (int i = 0; i < saveCount; i++) {
                int index = i;
                saves.add(executor.submit(() -> {
                    start.await();
                    service.save(documentFor(index));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> save : saves) {
                save.get();
            }
        } finally {
            executor.shutdownNow();
        }

        Document document = secureFactory().newDocumentBuilder().parse(target.toFile());
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("riskFactors");
        assertThat(document.getElementsByTagName("risk").getLength()).isEqualTo(1);
        assertThat(document.getElementsByTagName("risk").item(0).getTextContent())
                .startsWith("Concurrent risk ");
    }

    private Path originalTarget() throws IOException {
        Path target = temporaryDirectory.resolve(AntenatalRiskConfigService.FILE_NAME);
        Files.writeString(target, "original");
        return target;
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        return factory;
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }

    private static String documentFor(int index) {
        return "<riskFactors><section><section_title>Concurrent</section_title>"
                + "<risk name=\"risk" + index + "\">Concurrent risk " + index
                + "</risk></section></riskFactors>";
    }
}
