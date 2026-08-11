/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
class AntenatalRiskConfigServiceTest {

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
                Path.of("src/main/webapp/decision/antenatal/desantenatalplannerrisks_99_12.xml"),
                StandardCharsets.UTF_8);

        new AntenatalRiskConfigService(target).save(existingContent);

        Document document = secureFactory().newDocumentBuilder().parse(target.toFile());
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("riskFactors");
        assertThat(document.getElementsByTagName("risk").getLength()).isEqualTo(9);
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
    @DisplayName("should reject unknown attributes and unsafe link schemes")
    void shouldReject_unsupportedMarkup() throws Exception {
        Path target = originalTarget();
        String unknownAttribute = VALID_XML.replace("name=\"risk101\"", "name=\"risk101\" onclick=\"alert(1)\"");
        String unsafeLink = VALID_XML.replace("https://example.test/risk", "javascript:alert(1)");

        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(unknownAttribute))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("onclick");
        assertThatThrownBy(() -> new AntenatalRiskConfigService(target).save(unsafeLink))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("HTTP");
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

    private static String documentFor(int index) {
        return "<riskFactors><section><section_title>Concurrent</section_title>"
                + "<risk name=\"risk" + index + "\">Concurrent risk " + index
                + "</risk></section></riskFactors>";
    }
}
