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
 * response-wrapping filters has its real response suspended, so the page those filters flush
 * during unwind is silently discarded. Every Struts result that forwards to another action, and
 * every JSP-to-JSP forward, then reaches the browser as HTTP 200 with an empty body. That is the
 * blank consultation page of #2241 and the blank email compose window reported on #3434. Nothing
 * is logged.</p>
 *
 * <p>The attribute lives in two places, and only one applies to any given install.
 * {@code META-INF/context.xml} is read by the devcontainer deploy. A packaged install deploys
 * from {@code conf/Catalina/localhost/carlos.xml}, which overrides the WAR's descriptor entirely.
 * A copy that loses the attribute breaks that install with no error at startup, so both are
 * checked here. Test harnesses that deploy CARLOS through their own external descriptor must set
 * it too.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("Tomcat forward suspension is disabled in every shipped context descriptor")
@Tag("unit")
@Tag("config")
class TomcatForwardSuspensionRegressionTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "src/main/webapp/META-INF/context.xml",
            "debian/assets/tomcat/Catalina/localhost/carlos.xml"})
    @DisplayName("should keep suspendWrappedResponseAfterForward false")
    void shouldDisableForwardSuspension_forShippedDescriptor(String descriptor) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var context = factory.newDocumentBuilder().parse(Path.of(descriptor).toFile());
        var xpath = XPathFactory.newInstance().newXPath();

        assertThat(xpath.evaluate("count(/Context)", context)).as("one Context in %s", descriptor).isEqualTo("1");
        assertThat(xpath.evaluate("/Context/@suspendWrappedResponseAfterForward", context))
                .as("suspendWrappedResponseAfterForward in %s", descriptor)
                .isEqualTo("false");
    }
}
