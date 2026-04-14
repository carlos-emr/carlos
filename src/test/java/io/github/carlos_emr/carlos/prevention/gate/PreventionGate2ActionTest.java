/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prevention.gate;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the four oscarPrevention gate actions:
 * {@link ViewPreventionIndex2Action} (read),
 * {@link ViewAddPreventionDataDisambiguate2Action} (read),
 * {@link ViewPreventionManager2Action} (conditional-POST write),
 * {@link ViewPreventionListManager2Action} (conditional-POST write).
 *
 * <p>Covers the three behaviors of {@link AbstractPreventionGate2Action}:
 * (1) privilege check against {@code _prevention}, (2) successful forwarding
 * to the gated JSP, and (3) conditional-POST enforcement rejecting GETs that
 * carry a {@code formAction} mutation parameter.
 *
 * @since 2026-04-13
 */
@DisplayName("oscarPrevention Gate 2Actions")
@Tag("unit")
@Tag("prevention")
class PreventionGate2ActionTest extends CarlosWebTestBase {

    @Nested
    @DisplayName("ViewPreventionIndex2Action")
    class IndexGate {

        @Test
        @DisplayName("should return SUCCESS when _prevention read privilege granted")
        void shouldReturnSuccess_whenReadPrivilegeGranted() throws Exception {
            allowPrivilege("_prevention", "r");
            assertThat(executeAction(new ViewPreventionIndex2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should throw SecurityException when _prevention read privilege denied")
        void shouldThrowSecurityException_whenReadPrivilegeDenied() {
            denyPrivilege("_prevention", "r");
            assertThatThrownBy(() -> executeAction(new ViewPreventionIndex2Action()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_prevention");
        }
    }

    @Nested
    @DisplayName("ViewAddPreventionDataDisambiguate2Action")
    class DisambiguateGate {

        @Test
        @DisplayName("should return SUCCESS when _prevention read privilege granted")
        void shouldReturnSuccess_whenReadPrivilegeGranted() throws Exception {
            allowPrivilege("_prevention", "r");
            assertThat(executeAction(new ViewAddPreventionDataDisambiguate2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should throw SecurityException when _prevention read privilege denied")
        void shouldThrowSecurityException_whenReadPrivilegeDenied() {
            denyPrivilege("_prevention", "r");
            assertThatThrownBy(() -> executeAction(new ViewAddPreventionDataDisambiguate2Action()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    @DisplayName("ViewPreventionManager2Action (conditional POST)")
    class ManagerGate {

        @Test
        @DisplayName("should return SUCCESS for GET without formAction when write privilege granted")
        void shouldReturnSuccess_forGetWithoutFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("GET");
            assertThat(executeAction(new ViewPreventionManager2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should return SUCCESS for POST with formAction when write privilege granted")
        void shouldReturnSuccess_forPostWithFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("POST");
            addRequestParameter("formAction", "update");
            assertThat(executeAction(new ViewPreventionManager2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should return NONE and send 405 for GET with formAction")
        void shouldReturn405_forGetWithFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("GET");
            addRequestParameter("formAction", "update");

            String result = executeAction(new ViewPreventionManager2Action());

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus())
                    .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should throw SecurityException when _prevention write privilege denied")
        void shouldThrowSecurityException_whenWritePrivilegeDenied() {
            denyPrivilege("_prevention", "w");
            assertThatThrownBy(() -> executeAction(new ViewPreventionManager2Action()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("ViewPreventionManager2Action");
        }

        @Test
        @DisplayName("should return SUCCESS for GET with empty formAction parameter")
        void shouldReturnSuccess_forGetWithEmptyFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("GET");
            addRequestParameter("formAction", "");
            assertThat(executeAction(new ViewPreventionManager2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should accept lowercase post as a valid POST method")
        void shouldReturnSuccess_forLowercasePost() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("post");
            addRequestParameter("formAction", "update");
            assertThat(executeAction(new ViewPreventionManager2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ViewPreventionListManager2Action (conditional POST)")
    class ListManagerGate {

        @Test
        @DisplayName("should return SUCCESS for GET without formAction when write privilege granted")
        void shouldReturnSuccess_forGetWithoutFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("GET");
            assertThat(executeAction(new ViewPreventionListManager2Action()))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should return NONE and send 405 for GET with formAction")
        void shouldReturn405_forGetWithFormAction() throws Exception {
            allowPrivilege("_prevention", "w");
            getMockRequest().setMethod("GET");
            addRequestParameter("formAction", "update");

            String result = executeAction(new ViewPreventionListManager2Action());

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus())
                    .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
}
