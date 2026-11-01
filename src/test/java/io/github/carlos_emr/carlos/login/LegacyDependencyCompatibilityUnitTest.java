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
package io.github.carlos_emr.carlos.login;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData;
import io.github.carlos_emr.carlos.utility.ImageIoUtils;
import io.github.carlos_emr.carlos.utility.IsoDates;
import io.github.carlos_emr.carlos.utility.QrCodeUtils;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.Key;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional differential oracle against the exact removed versions, isolated from Maven's tree.
 * Run after caching those versions with -Dlegacy.compatibility.repo=/path/to/maven/repository.
 * The ordinary suite also retains independent RFC vectors and legacy date/billing edge cases.
 */
@Tag("unit")
@EnabledIfSystemProperty(named = "legacy.compatibility.repo", matches = ".+")
class LegacyDependencyCompatibilityUnitTest {
    private URLClassLoader legacyJar(String artifact) throws Exception {
        Path jar = Path.of(System.getProperty("legacy.compatibility.repo"), artifact);
        assertThat(jar).isRegularFile();
        return new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
    }

    @Test
    void shouldMatchJavaOtp_forRandomKeysBoundariesAndLocales() throws Exception {
        try (URLClassLoader loader = legacyJar("com/eatthepath/java-otp/0.4.0/java-otp-0.4.0.jar")) {
            Class<?> type = loader.loadClass("com.eatthepath.otp.TimeBasedOneTimePasswordGenerator");
            Object oracle = type.getConstructor().newInstance();
            Method generate = type.getMethod("generateOneTimePasswordString", Key.class, Instant.class);
            TotpGenerator replacement = new TotpGenerator();
            Locale previous = Locale.getDefault();
            try {
                for (String language : new String[]{"en-US", "ar-EG", "fa-IR", "th-TH"}) {
                    Locale.setDefault(Locale.forLanguageTag(language));
                    Random random = new Random(5473);
                    for (int i = 0; i < 128; i++) {
                        byte[] secret = new byte[1 + random.nextInt(128)];
                        random.nextBytes(secret);
                        Key key = new SecretKeySpec(secret, "HmacSHA1");
                        for (int j = 0; j < 32; j++) {
                            long boundary = (random.nextLong() % 1_000_000_000L) * 30_000L;
                            for (long delta : new long[]{-1, 0, 1, 29_999}) {
                                Instant time = Instant.ofEpochMilli(boundary + delta);
                                assertThat(replacement.generateOneTimePasswordString(key, time))
                                        .as("locale %s key %s time %s", language, i, time)
                                        .isEqualTo(generate.invoke(oracle, key, time));
                            }
                        }
                        for (long millis : new long[]{-30_001, -30_000, -29_999, -1, 0, 1, 29_999, 30_000}) {
                            Instant time = Instant.ofEpochMilli(millis);
                            assertThat(replacement.generateOneTimePasswordString(key, time))
                                    .isEqualTo(generate.invoke(oracle, key, time));
                        }
                    }
                }
            } finally {
                Locale.setDefault(previous);
            }
        }
    }

    @Test
    void shouldMatchAntDateParsing_withJvmDefaultsAndLenientInputs() throws Exception {
        try (URLClassLoader loader = legacyJar("org/apache/ant/ant/1.10.18/ant-1.10.18.jar")) {
            Method parse = loader.loadClass("org.apache.tools.ant.util.DateUtils")
                    .getMethod("parseIso8601Date", String.class);
            Locale locale = Locale.getDefault();
            TimeZone zone = TimeZone.getDefault();
            try {
                for (String language : new String[]{"en-US", "th-TH", "ar-EG"}) {
                    Locale.setDefault(Locale.forLanguageTag(language));
                    for (String id : new String[]{"UTC", "America/Toronto", "Pacific/Auckland"}) {
                        TimeZone.setDefault(TimeZone.getTimeZone(id));
                        for (String value : new String[]{"2026-01-02", "2026-01-02T10:00", "2026-13-01",
                                "2026-02-30", "2026-0-0", "2026-1-2 suffix", " 2026-04-05", "99-1-2"}) {
                            assertThat(IsoDates.parseIso8601Date(value)).isEqualTo(parse.invoke(null, value));
                        }
                    }
                }
            } finally {
                Locale.setDefault(locale);
                TimeZone.setDefault(zone);
            }
        }
    }

    @Test
    void shouldProduceByteIdenticalQrPngs_forPayloadsLevelsAndScales() throws Exception {
        try (URLClassLoader loader = legacyJar("com/google/zxing/javase/3.5.4/javase-3.5.4.jar")) {
            Method image = loader.loadClass("com.google.zxing.client.j2se.MatrixToImageWriter")
                    .getMethod("toBufferedImage", BitMatrix.class);
            Method png = QrCodeUtils.class.getDeclaredMethod("toPng", BufferedImage.class);
            png.setAccessible(true);
            for (String payload : new String[]{"test", "https://example.com/", "CARLOS café 中文",
                    "otpauth://totp/CARLOS:test?secret=JBSWY3DPEHPK3PXP&issuer=CARLOS", "x".repeat(500)}) {
                for (ErrorCorrectionLevel level : ErrorCorrectionLevel.values()) {
                    for (int scale : new int[]{1, 2, 8}) {
                        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 100, 100,
                                Map.of(EncodeHintType.ERROR_CORRECTION, level));
                        BufferedImage expected = (BufferedImage) image.invoke(null, matrix);
                        if (scale != 1) {
                            expected = ImageIoUtils.toBufferedImage(expected.getScaledInstance(
                                    expected.getWidth() * scale, expected.getHeight() * scale, Image.SCALE_FAST));
                        }
                        assertThat(QrCodeUtils.toSingleQrCodePng(payload, level, scale))
                                .as("level %s scale %s payload length %s", level, scale, payload.length())
                                .isEqualTo((byte[]) png.invoke(null, expected));
                    }
                }
            }
        }
    }

    @Test
    void shouldMatchVelocityConversion_forNullsScalarsAndNestedArrays() throws Exception {
        try (URLClassLoader loader = legacyJar("org/apache/velocity/tools/velocity-tools-generic/3.1/velocity-tools-generic-3.1.jar")) {
            Method oracle = loader.loadClass("org.apache.velocity.tools.ConversionUtils")
                    .getMethod("toString", Object.class);
            Method replacement = BillingFormData.class.getDeclaredMethod("queryCellToString", Object.class);
            replacement.setAccessible(true);
            for (Object value : new Object[]{null, "", "abc", 42, true, new Object[0], new int[0],
                    new Object[]{null}, new Object[]{new String[]{"first", "second"}},
                    new int[]{1, 2}, new long[]{3}, new short[]{4}, new byte[]{5}, new char[]{'z'},
                    new boolean[]{true}, new float[]{1.5f}, new double[]{2.5}, Instant.EPOCH}) {
                assertThat(replacement.invoke(null, new Object[]{value}))
                        .isEqualTo(oracle.invoke(null, new Object[]{value}));
            }
        }
    }
}
