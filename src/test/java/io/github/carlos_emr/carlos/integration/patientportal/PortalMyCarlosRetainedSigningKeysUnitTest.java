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

import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Temporary FAKE keystores only; no configured clinic file or real provider is accessed. */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosRetainedSigningKeysUnitTest {
    @TempDir
    Path directory;

    private PDFSigningConfig selected() throws Exception {
        return PdfSigningTestSupport.enabledConfig(directory.resolve("retained.p12"),
                FIRST_PAIR.getPrivate(),
                PdfSigningTestSupport.createSelfSignedCertificate(FIRST_PAIR, "SHA256withECDSA"));
    }

    @Test
    void shouldLoadOnlyTheExplicitAlias_andProveTheSelectedPrivatePublicPair() throws Exception {
        var configuration = selected();
        var privateKey = PortalMyCarlosRetainedSigningKeys.load(configuration, FIRST);
        var proof = PortalMyCarlosKeyReplacementProofs.mintOnce(CLINIC, FIRST, SECOND, privateKey);
        PortalMyCarlosKeyReplacementProofs.validateAll(
                CLINIC, new PatientPortalClinicKeysPublishRequest(List.of(FIRST, proof)));
        assertThat(PDFSigningUtil.currentSigningCertificate(configuration).getPublicKey().getEncoded())
                .isEqualTo(FIRST_PAIR.getPublic().getEncoded());
    }

    @Test
    void shouldRefuseAnotherPublicKeyOrMissingAlias_withoutDiscoveringAnAlternative() throws Exception {
        var configuration = selected();
        assertThatThrownBy(() -> PortalMyCarlosRetainedSigningKeys.load(configuration, SECOND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the selected retained predecessor key is unavailable").hasNoCause();
        var missing = new PDFSigningConfig(true, configuration.getKeystorePath(), configuration.getKeystoreType(),
                configuration.getKeystorePassword(), "missing-explicit-alias", null, null, null, null, null);
        assertThatThrownBy(() -> PortalMyCarlosRetainedSigningKeys.load(missing, FIRST))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause();
    }

    @Test
    void shouldPermitAnActivePinnedExpiredPredecessor_withoutWeakeningGeneralPdfCertificateValidity()
            throws Exception {
        Instant now = Instant.now();
        var name = new X500Name("CN=FAKE expired retained predecessor");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(now.minusSeconds(172800)), Date.from(now.minusSeconds(86400)),
                name, FIRST_PAIR.getPublic());
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(FIRST_PAIR.getPrivate())));
        var configuration = PdfSigningTestSupport.enabledConfig(
                directory.resolve("expired-retained.p12"), FIRST_PAIR.getPrivate(), certificate);
        var privateKey = PortalMyCarlosRetainedSigningKeys.load(configuration, FIRST);
        assertThat(PortalMyCarlosKeyReplacementProofs.mintOnce(CLINIC, FIRST, SECOND, privateKey)
                .replacementSignature()).isNotNull();
        assertThatThrownBy(() -> PDFSigningUtil.currentSigningCertificate(configuration))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseDisabledSelection_beforeTryingToReadAFile() {
        var disabled = new PDFSigningConfig(
                false, directory.resolve("does-not-exist.p12").toString(),
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> PortalMyCarlosRetainedSigningKeys.load(disabled, FIRST))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause();
        assertThat(directory.resolve("does-not-exist.p12")).doesNotExist();
    }
}
