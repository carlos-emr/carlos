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
package io.github.carlos_emr.carlos.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Credential logging regression tests")
class CredentialLoggingRegressionTest {

    @Test
    @DisplayName("SOAP login should not log password values")
    void shouldNotLogPasswordValues_forSoapLogin() throws IOException {
        String loginWs = readSource("webserv/LoginWs.java");
        String tokenValidator = readSource("webserv/OscarUsernameTokenValidator.java");

        assertThat(loginWs)
                .doesNotContain("logger.debug(\"Login attempt : p =\" + password)");
        assertThat(tokenValidator)
                .contains("logger.debug(\"userIdString=\" + usernameToken.getName())")
                .doesNotContain("logger.debug(\"password=\" + usernameToken.getPassword())");
    }

    @Test
    @DisplayName("OAuth token parsing should not log token XML or secrets")
    void shouldNotLogTokenXmlOrSecrets_forOauthTokenParsing() throws IOException {
        String oauthConfig = readSource("app/AppOAuth1Config.java");

        assertThat(oauthConfig)
                .contains("logger.debug(\"token XML length: {}\", str == null ? 0 : str.length())")
                .contains("logger.debug(node.getNodeName())")
                .doesNotContain("logger.debug(\"token === \" + str)")
                .doesNotContain("logger.error(node.getNodeName())");
    }

    @Test
    @DisplayName("OAuth REST endpoint should not enable CXF body logging")
    void shouldNotEnableCxfBodyLogging_forOauthEndpoints() throws IOException {
        String restConfig = readResource("applicationContextREST.xml");
        int oauthEndpointsStart = restConfig.indexOf("<jaxrs:server id=\"oauthEndpoints\"");
        int oauthEndpointsEnd = restConfig.indexOf("</jaxrs:server>", oauthEndpointsStart);

        assertThat(oauthEndpointsStart).isNotNegative();
        assertThat(oauthEndpointsEnd).isGreaterThan(oauthEndpointsStart);
        assertThat(restConfig.substring(oauthEndpointsStart, oauthEndpointsEnd))
                .doesNotContain("<cxf:logging");
    }

    @Test
    @DisplayName("Teleplan password save should log presence only")
    void shouldLogPasswordPresenceOnly_forTeleplanPasswordSave() throws IOException {
        String teleplanDao = readSource("billings/ca/bc/Teleplan/TeleplanUserPassDAO.java");

        assertThat(teleplanDao)
                .contains("log.debug(\"has password: true\")")
                .contains("log.debug(\"has password: false\")")
                .doesNotContain("log.debug(\"has password\" + password)")
                .doesNotContain("log.debug(\"not password\" + password)");
    }

    @Test
    @DisplayName("CML upload should log key presence only")
    void shouldLogKeyPresenceOnly_forCmlUpload() throws IOException {
        String labUploadAction = readSource("lab/ca/on/CML/Upload/LabUpload2Action.java");

        assertThat(labUploadAction)
                .contains("_logger.debug(\"upload key present: {}\", key != null)")
                .doesNotContain("MiscUtils.getLogger().debug(\"key=\" + key)");
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/java/io/github/carlos_emr/carlos", relativePath));
    }

    private static String readResource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources", relativePath));
    }
}
