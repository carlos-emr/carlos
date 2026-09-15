/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.web;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import io.github.carlos_emr.carlos.lab.ca.all.util.CMLLabHL7Generator;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link SubmitLabByForm2Action}, focused on the null guard
 * for HL7 generation and PHI-safe MSH segment extraction.
 *
 * @since 2026-04-03
 */
@Tag("unit")
@Tag("lab")
@DisplayName("SubmitLabByForm2Action")
class SubmitLabByForm2ActionUnitTest extends CarlosWebTestBase {

    private SubmitLabByForm2Action action;
    private MockedStatic<CMLLabHL7Generator> cmlGeneratorMock;

    @BeforeEach
    void setUp() throws Exception {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        allowPrivilege("_lab", "w");

        action = new SubmitLabByForm2Action();

        // Inject mocked SecurityInfoManager via reflection (field initialized at declaration time)
        java.lang.reflect.Field secField = SubmitLabByForm2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);

        // Set up minimal valid request parameters for Lab construction
        addRequestParameter("labname", "CML");
        addRequestParameter("accession", "ACC001");
        addRequestParameter("lab_req_date", "2026-04-03 10:00");
        addRequestParameter("lastname", "Test");
        addRequestParameter("firstname", "Patient");
        addRequestParameter("hin", "1234567890");
        addRequestParameter("sex", "M");
        addRequestParameter("dob", "1990-01-01");
        addRequestParameter("phone", "555-0100");
        addRequestParameter("billingNo", "B001");
        addRequestParameter("pLastname", "Doc");
        addRequestParameter("pFirstname", "Test");
        addRequestParameter("cc", "");
        addRequestParameter("test_num", "0");
    }

    @AfterEach
    void tearDownStatic() {
        if (cmlGeneratorMock != null) {
            cmlGeneratorMock.close();
        }
    }

    @Test
    @DisplayName("should return manage with action error when HL7 generation returns null")
    void shouldReturnManageWithActionError_whenHL7GenerationReturnsNull() throws Exception {
        // Given — CML generator returns null
        cmlGeneratorMock = mockStatic(CMLLabHL7Generator.class);
        cmlGeneratorMock.when(() -> CMLLabHL7Generator.generate(any())).thenReturn(null);

        // When
        String result = executeActionMethod(action, "saveManage");

        // Then — action returns "manage" (from the early return) with user-facing error
        assertThat(result).isEqualTo("manage");
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(action.getActionErrors().iterator().next())
            .contains("Failed to generate lab result");
    }

    @Test
    @DisplayName("should extract only MSH segment when HL7 uses LF separators")
    void shouldExtractOnlyMshSegment_whenHL7UsesLfSeparators() throws Exception {
        // Given — HL7 with \n separators (as all three generators produce)
        // MSH is safe metadata; PID contains PHI (name, HIN, DOB)
        String msh = "MSH|^~\\&|CML|CML|OSCAR|OSCAR|20260403100000||ORU^R01|BAR260403100000|P|2.3|||ER|AL";
        String pid = "PID||||1234567890|Test^Patient||19900101|M|||||555-0100||||||X1234567890";
        String hl7WithLf = msh + "\n" + pid + "\n";

        cmlGeneratorMock = mockStatic(CMLLabHL7Generator.class);
        cmlGeneratorMock.when(() -> CMLLabHL7Generator.generate(any())).thenReturn(hl7WithLf);

        // Stop immediately after the log statement so this test never writes to the configured
        // DOCUMENT_DIR or invokes the downstream lab import infrastructure.
        try (MockedStatic<Utilities> utilitiesMock = mockStatic(Utilities.class);
                LogCapture logCapture = LogCapture.forLogger(SubmitLabByForm2Action.class)) {
            utilitiesMock.when(() -> Utilities.saveFile(any(InputStream.class), anyString()))
                    .thenThrow(new IllegalStateException("stop after HL7 metadata logging"));

            assertThatThrownBy(() -> executeActionMethod(action, "saveManage"))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(logCapture.messages()).anySatisfy(message -> {
                assertThat(message).contains("HL7 generated", "MSH=", "CML|CML|OSCAR|OSCAR");
                assertThat(message).doesNotContain("PID");
                assertThat(message).doesNotContain("Test^Patient");
                assertThat(message).doesNotContain("1234567890");
            });
        }
    }
}
