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
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosSigningIdentityUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldBindIdentityToTheConfiguredPublicCertificate_withoutDiscoveringAnyAliasCatalogue() {
        var certificate = mock(X509Certificate.class);
        when(certificate.getPublicKey()).thenReturn(FIRST_PAIR.getPublic());
        assertThat(PortalMyCarlosSigningIdentity.fromValidatedCertificate(certificate)).isEqualTo(FIRST);
    }

    @Test
    void shouldKeepTheP256RestrictionLocalToMyCarlos_withoutChangingTheGeneralPdfSigner() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var rsa = generator.generateKeyPair();
        var certificate = mock(X509Certificate.class);
        when(certificate.getPublicKey()).thenReturn(rsa.getPublic());
        assertThatThrownBy(() -> PortalMyCarlosSigningIdentity.fromValidatedCertificate(certificate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("myCarlos document signing identity is unavailable")
                .hasNoCause();
    }
}
