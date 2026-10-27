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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportJobService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("unit")
class CdsRulesetFingerprintMappingUnitTest {
    @Test
    @ResourceLock("java.util.TimeZone")
    @ResourceLock("java.util.Locale")
    void shouldChangeFullFingerprint_whenActualProgramOrAuthorizationMappingBytesChange() {
        ClassLoader deployed = CdsImportJobService.class.getClassLoader();
        var original = CdsRulesetFingerprint.forClassLoader(deployed, URL::openStream);
        for (String mapping :
                List.of(
                        "PMmodule/model/Program",
                        "PMmodule/model/ProgramProvider",
                        "commn/model/ProviderDefaultProgram",
                        "model/security/Secrole",
                        "commn/model/Facility")) {
            String resource = "io/github/carlos_emr/carlos/" + mapping + ".class";
            var reads = new AtomicInteger();
            var changed =
                    CdsRulesetFingerprint.forClassLoader(
                            deployed,
                            url -> {
                                if (!url.toExternalForm().endsWith("/" + resource))
                                    return url.openStream();
                                reads.incrementAndGet();
                                // Alter only the streamed hash input; never edit or define a
                                // modified entity class.
                                return new java.io.FilterInputStream(url.openStream()) {
                                    private boolean first = true;

                                    @Override
                                    public int read() throws IOException {
                                        int value = in.read();
                                        if (value >= 0 && first) {
                                            first = false;
                                            return value ^ 1;
                                        }
                                        return value;
                                    }

                                    @Override
                                    public int read(byte[] bytes, int offset, int length)
                                            throws IOException {
                                        int count = in.read(bytes, offset, length);
                                        if (count > 0 && first) {
                                            first = false;
                                            bytes[offset] ^= 1;
                                        }
                                        return count;
                                    }
                                };
                            });
            assertThat(changed.sha256())
                    .as("deployed mapped dependency %s", mapping)
                    .isNotEqualTo(original.sha256());
            assertThat(reads)
                    .as("actual mapped resource was hashed: %s", mapping)
                    .hasPositiveValue();
        }
        assertThat(CdsRulesetFingerprint.forClassLoader(deployed, URL::openStream))
                .isEqualTo(original);
    }
}
