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

import io.github.carlos_emr.carlos.lab.ca.all.util.CMLLabHL7Generator;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link SubmitLabByForm2Action}, focused on the null guard
 * for HL7 generation and protection of request-derived data in logs.
 *
 * @since 2026-04-03
 */
@Tag("unit")
@Tag("lab")
@DisplayName("SubmitLabByForm2Action")
class SubmitLabByForm2ActionTest extends CarlosWebTestBase {

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
    @DisplayName("should not log any generated HL7 fields")
    void shouldNotLogHl7Fields_whenLabIsGenerated() throws Exception {
        // MSH can also contain sender-supplied fields, so neither segment is safe to log.
        String msh = "MSH|^~\\&|private-sender|CML|OSCAR|OSCAR|20260403100000||ORU^R01|BAR260403100000|P|2.3|||ER|AL";
        String pid = "PID||||1234567890|Test^Patient||19900101|M|||||555-0100||||||X1234567890";
        String hl7WithLf = msh + "\n" + pid + "\n";

        cmlGeneratorMock = mockStatic(CMLLabHL7Generator.class);
        cmlGeneratorMock.when(() -> CMLLabHL7Generator.generate(any())).thenReturn(hl7WithLf);

        try (LogCapture logs = LogCapture.forLogger(SubmitLabByForm2Action.class)) {
            // File-save infrastructure is not set up in this unit test; inspect the logs
            // emitted before that boundary regardless of its outcome.
            try {
                executeActionMethod(action, "saveManage");
            } catch (Exception expected) {
                // The request has already passed HL7 generation.
            }

            assertThat(logs.messages()).contains("HL7 generated for lab submission");
            assertThat(String.join("\n", logs.messages()))
                    // Every PHI-bearing value in the generated MSH/PID segments, plus the raw
                    // request values they were built from (DOB, accession, billing number).
                    .doesNotContain("private-sender", "BAR260403100000", "Test^Patient", "1234567890",
                            "19900101", "555-0100", "X1234567890", "1990-01-01", "ACC001", "B001");
        }
    }
}
