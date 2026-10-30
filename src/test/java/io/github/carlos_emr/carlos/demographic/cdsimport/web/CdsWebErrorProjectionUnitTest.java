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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class CdsWebErrorProjectionUnitTest {
    @Test void shouldRecognizeTypedSubclasses_withoutSerializingProtectedCauses() throws Exception {
        var response=new MockHttpServletResponse();
        CdsWeb.failure(response,new CdsImportException("OWNER_FENCED",false,new IllegalStateException("/private/source.xml")) { });
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("OWNER_FENCED").doesNotContain("/private","IllegalStateException");
    }
    @Test void shouldRejectForeignSameNamedException_insteadOfTrustingItsSimpleClassName() throws Exception {
        var response=new MockHttpServletResponse();CdsWeb.failure(response,new Foreign.CdsImportException("SECRET_SOURCE_VALUE"));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("IMPORT_REQUEST_UNAVAILABLE").doesNotContain("SECRET_SOURCE_VALUE");
    }
    @Test void shouldKeepUnclassifiedWrapperDetailsPrivate_whenProjectingFailure() throws Exception {
        var response=new MockHttpServletResponse();CdsWeb.failure(response,new RuntimeException("/private/path",new CdsImportException("OWNER_FENCED")));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("/private","OWNER_FENCED");
    }
    @Test void shouldRefuseEverySensitiveSummaryKeyCase_withLinearSubstringChecks() {
        for(String key:new String[]{"reportLocation","DIRECTORY","sourcePassword","aSeCrEtValue","sourceXPath"}) {
            assertThatThrownBy(()->CdsWeb.summary(Map.of("outer",Map.of(key,"private"))))
                    .hasMessage("IMPORT_SUMMARY_INVALID");
        }
        assertThat(CdsWeb.summary(Map.of("lastCheckpoint","2026-01-01","pendingUnits",2)))
                .isEqualTo(Map.of("lastCheckpoint","2026-01-01","pendingUnits",2));
    }
    private static final class Foreign {
        private static final class CdsImportException extends RuntimeException {
            private static final long serialVersionUID=1L;
            private CdsImportException(String code) { super(code); }
        }
    }
}
