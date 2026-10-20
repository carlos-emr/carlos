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
package io.github.carlos_emr.carlos.PMmodule.web;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The PMM client view refuses an unknown client before it renders or logs a read, and keeps no
 * copy of the client in the session (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("pmmodule")
@DisplayName("PMM client view: unknown client")
class ClientManager2ActionEditUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private ClientManager clientManager;

    @BeforeEach
    void setUp() {
        // The action resolves some twenty collaborators in field initializers; a plain mock of each
        // is enough for the path under test, which returns before any of them is used.
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(invocation -> mockedBeans.computeIfAbsent(invocation.getArgument(0),
                        type -> mock((Class<?>) type)));
        clientManager = mock(ClientManager.class);
        registerMock(ClientManager.class, clientManager);

        request = new MockHttpServletRequest("GET", "/carlos/PMmodule/ClientManager");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    @Test
    void shouldRefuseTheView_whenTheClientDoesNotExist() {
        request.setParameter("id", "424242");

        assertThat(new ClientManager2Action().edit()).isEqualTo(ActionSupport.ERROR);

        assertThat(request.getSession().getAttribute("demographicId")).isNull();
        assertThat(request.getSession().getAttribute("clientAge")).isNull();
        assertThat(request.getSession().getAttribute("clientGender")).isNull();
    }

    @Test
    void shouldRefuseTheView_whenOnlyARequestBoundClientPropertyExists() {
        // Struts resolves request.getAttribute("client") through the value stack when the attribute
        // is absent, i.e. to this action's own client property, which client.* parameters fill.
        request.setParameter("id", "424242");
        ClientManager2Action action = new ClientManager2Action();
        action.setClient(new Demographic());

        assertThat(action.edit()).isEqualTo(ActionSupport.ERROR);
    }

    @Test
    void shouldLookTheClientUpOnce_beforeRefusing() {
        request.setParameter("id", "424242");
        when(clientManager.getClientByDemographicNo("424242")).thenReturn(null);

        new ClientManager2Action().edit();

        org.mockito.Mockito.verify(clientManager).getClientByDemographicNo("424242");
    }
}
