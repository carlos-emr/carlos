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
package io.github.carlos_emr.carlos.web;

import static javax.xml.xpath.XPathConstants.NODESET;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@DisplayName("JavaMelody monitoring configuration regression tests")
@Tag("unit")
@Tag("regression")
class JavaMelodyMonitoringConfigurationRegressionTest {
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path DEVCONTAINER_DIRECTORY = Path.of(".devcontainer");
    private static final Path DEVCONTAINER_DOCKERFILE = Path.of(".devcontainer/development/Dockerfile");
    private static final String ENABLED_SYSTEM_ACTIONS_OPTION =
            "-Djavamelody.system-actions-enabled=true";
    private static final Pattern SYSTEM_ACTIONS_JVM_OPTION = Pattern.compile(
            "(?:^|[\\s\"'=])(-Djavamelody\\.system-actions-enabled=[^\\s\"'\\\\]++)");
    private static final String MONITORING_FILTER =
            "/*[local-name()='web-app']/*[local-name()='filter']"
                    + "[*[local-name()='filter-name' and normalize-space()='monitoring']]"
                    + "[*[local-name()='filter-class'"
                    + " and normalize-space()='net.bull.javamelody.MonitoringFilter']]";
    private static final String SYSTEM_ACTIONS_PARAM =
            "./*[local-name()='init-param']"
                    + "[*[local-name()='param-name'"
                    + " and normalize-space()='system-actions-enabled']]";

    @Test
    @DisplayName("production web.xml should disable JavaMelody system actions")
    void shouldDisableJavaMelodySystemActions_inProductionWebXml()
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Document webXml = parseWebXml();
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList monitoringFilters = (NodeList) xpath.evaluate(MONITORING_FILTER, webXml, NODESET);

        assertThat(monitoringFilters.getLength())
                .as("production web.xml must declare exactly one JavaMelody monitoring filter")
                .isEqualTo(1);

        NodeList systemActionsParameters =
                (NodeList) xpath.evaluate(SYSTEM_ACTIONS_PARAM, monitoringFilters.item(0), NODESET);
        assertThat(systemActionsParameters.getLength())
                .as("the JavaMelody monitoring filter must declare system-actions-enabled exactly once")
                .isEqualTo(1);

        Node systemActionsParameter = systemActionsParameters.item(0);
        NodeList values = (NodeList)
                xpath.evaluate("./*[local-name()='param-value']", systemActionsParameter, NODESET);
        assertThat(values.getLength())
                .as("system-actions-enabled must have exactly one value")
                .isEqualTo(1);
        assertThat(values.item(0).getTextContent().trim())
                .as("production monitoring credentials must not allow heap dumps or other JVM system actions")
                .isEqualTo("false");
    }

    @Test
    @DisplayName("devcontainer should enable JavaMelody system actions")
    void shouldEnableJavaMelodySystemActions_inDevcontainer() throws IOException {
        List<JvmOptionDeclaration> declarations = findDevcontainerSystemActionsJvmOptions();

        assertThat(declarations)
                .as("the devcontainer must declare exactly one JavaMelody system-actions JVM option")
                .singleElement()
                .satisfies(declaration -> {
                    assertThat(declaration.source()).isEqualTo(DEVCONTAINER_DOCKERFILE);
                    assertThat(declaration.option()).isEqualTo(ENABLED_SYSTEM_ACTIONS_OPTION);
                });
    }

    private static List<JvmOptionDeclaration> findDevcontainerSystemActionsJvmOptions() throws IOException {
        List<JvmOptionDeclaration> declarations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(DEVCONTAINER_DIRECTORY)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(JavaMelodyMonitoringConfigurationRegressionTest::isRuntimeConfiguration)
                    .toList()) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String configuration = line.strip();
                    if (configuration.startsWith("#")) {
                        continue;
                    }

                    Matcher matcher = SYSTEM_ACTIONS_JVM_OPTION.matcher(configuration);
                    while (matcher.find()) {
                        declarations.add(new JvmOptionDeclaration(path, matcher.group(1)));
                    }
                }
            }
        }

        return declarations;
    }

    private static boolean isRuntimeConfiguration(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("Dockerfile")
                || fileName.equals("devcontainer.json")
                || fileName.equals("make")
                || fileName.equals("server")
                || fileName.endsWith(".env")
                || fileName.endsWith(".sh")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".yml");
    }

    private static Document parseWebXml()
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(WEB_XML.toFile());
    }

    private record JvmOptionDeclaration(Path source, String option) {}
}
