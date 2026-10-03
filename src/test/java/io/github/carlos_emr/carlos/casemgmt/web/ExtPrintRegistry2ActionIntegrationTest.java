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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.util.ExtPrintRegistry;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import io.github.carlos_emr.carlos.casemgmt.util.ExtPrint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP error and privilege contracts for chart print registration. */
@Tag("integration")
class ExtPrintRegistry2ActionIntegrationTest extends CarlosWebTestBase {
    @BeforeEach
    void registerConfiguredPrinter() {
        ((DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory())
                .registerSingleton("extPrintIssue4171Test", mock(ExtPrint.class));
    }

    @AfterEach
    void removeConfiguredPrinter() {
        ((DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory())
                .destroySingleton("extPrintIssue4171Test");
    }

    @Test
    void shouldRejectArbitraryAlias_forConfiguredPrinter() throws Exception {
        mockRequest.setMethod("POST");
        allowPrivilege("_demographic", "w");
        addRequestParameter("name", "arbitraryAlias");
        addRequestParameter("bean", "extPrintIssue4171Test");
        try (MockedStatic<ExtPrintRegistry> registry = mockStatic(ExtPrintRegistry.class)) {
            executeAction(new ExtPrintRegistry2Action());
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            registry.verifyNoInteractions();
        }
    }

    @Test
    void shouldRejectUnconfiguredPrinter_beforeConsumingCapacity() throws Exception {
        mockRequest.setMethod("POST");
        allowPrivilege("_demographic", "w");
        addRequestParameter("name", "Issue4171Missing");
        addRequestParameter("bean", "extPrintIssue4171Missing");
        try (MockedStatic<ExtPrintRegistry> registry = mockStatic(ExtPrintRegistry.class)) {
            executeAction(new ExtPrintRegistry2Action());
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            registry.verifyNoInteractions();
        }
    }

    @Test
    void shouldRejectGet_beforeRegistration() throws Exception {
        mockRequest.setMethod("GET");
        try (MockedStatic<ExtPrintRegistry> registry = mockStatic(ExtPrintRegistry.class)) {
            executeAction(new ExtPrintRegistry2Action());
            assertThat(mockResponse.getStatus()).isEqualTo(405);
            assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
            registry.verifyNoInteractions();
        }
    }

    @Test
    void shouldRejectDirectRegistration_withoutPrivilege() {
        mockRequest.setMethod("POST");
        denyPrivilege("_demographic", "w");
        ExtPrintRegistry2Action action = new ExtPrintRegistry2Action();
        assertThatThrownBy(action::register).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldReturnBadRequest_withMissingName() throws Exception {
        mockRequest.setMethod("POST");
        allowPrivilege("_demographic", "w");
        addRequestParameter("bean", "extPrintIssue4171Test");
        executeAction(new ExtPrintRegistry2Action());
        assertThat(mockResponse.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldReturnConflict_whenRegistryIsFull() throws Exception {
        mockRequest.setMethod("POST");
        allowPrivilege("_demographic", "w");
        addRequestParameter("name", "Issue4171Test");
        addRequestParameter("bean", "extPrintIssue4171Test");
        try (MockedStatic<ExtPrintRegistry> registry = mockStatic(ExtPrintRegistry.class)) {
            registry.when(() -> ExtPrintRegistry.addEntry("Issue4171Test", "extPrintIssue4171Test"))
                    .thenThrow(new IllegalStateException("full"));
            executeAction(new ExtPrintRegistry2Action());
            assertThat(mockResponse.getStatus()).isEqualTo(409);
        }
    }

    @Test
    void shouldRegisterExtension_withAuthorizedPost() throws Exception {
        mockRequest.setMethod("POST");
        allowPrivilege("_demographic", "w");
        addRequestParameter("name", "Issue4171Test");
        addRequestParameter("bean", "extPrintIssue4171Test");
        try (MockedStatic<ExtPrintRegistry> registry = mockStatic(ExtPrintRegistry.class)) {
            executeAction(new ExtPrintRegistry2Action());
            assertThat(mockResponse.getStatus()).isEqualTo(200);
            registry.verify(() -> ExtPrintRegistry.addEntry("Issue4171Test", "extPrintIssue4171Test"));
        }
    }
}
