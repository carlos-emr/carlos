/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code suspendWrappedResponseAfterForward="false"} on every context descriptor CARLOS
 * ships (#3434, #3440).
 *
 * <p>Tomcat 11 changed the default to {@code true}. With it, a forward that returns through the
 * response-wrapping filters while the page is still buffered has its real response suspended, so
 * what those filters flush during unwind is silently discarded. Struts results that forward to
 * another action, and JSP-to-JSP forwards, then reach the browser as HTTP 200 with an empty body,
 * with nothing logged for the blank page; a forward on a 4xx becomes a raw 500. That is the blank
 * consultation page of #2241 and the blank email compose window reported on #3434.</p>
 *
 * <p>The attribute lives in two places, and only one applies to any given install.
 * {@code META-INF/context.xml} is read by the devcontainer deploy. A packaged install deploys
 * from {@code conf/Catalina/localhost/carlos.xml}, which overrides the WAR's descriptor entirely.
 * The package's postinst warns after the fact, and the pr-hardening Playwright probe catches it
 * against a running server; this pins it in the build. Test harnesses that deploy CARLOS through
 * their own external descriptor must set it too.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("Tomcat forward suspension is disabled in every shipped context descriptor")
@Tag("unit")
@Tag("regression")
class TomcatForwardSuspensionRegressionTest {

    @ParameterizedTest(name = "{0} keeps suspendWrappedResponseAfterForward false")
    @ValueSource(strings = {
            "src/main/webapp/META-INF/context.xml",
            "debian/assets/tomcat/Catalina/localhost/carlos.xml"})
    void shouldDisableForwardSuspension_forShippedDescriptor(String descriptor) throws Exception {
        Path path = resolveProjectPath(Path.of(descriptor));
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var context = factory.newDocumentBuilder().parse(path.toFile());
        var xpath = XPathFactory.newInstance().newXPath();

        assertThat(xpath.evaluate("count(/Context)", context)).as("one Context in %s", descriptor).isEqualTo("1");
        assertThat(xpath.evaluate("/Context/@suspendWrappedResponseAfterForward", context))
                .as("suspendWrappedResponseAfterForward in %s", descriptor)
                .isEqualTo("false");
        // The postinst check greps for this exact spelling, so keep the double-quoted form too.
        assertThat(Files.readString(path)).as("double-quoted attribute in %s", descriptor)
                .contains("suspendWrappedResponseAfterForward=\"false\"");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relativePath);
    }
}
