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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import jakarta.servlet.ServletRequestEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.WebUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Request leases prevent eviction between resolving a bean and acquiring its mutation monitor. */
@Tag("unit")
@Tag("prescript")
class RxSessionBeanRequestListenerUnitTest {
    private final MockHttpSession session = new MockHttpSession();
    private final RxSessionBeanRequestListener listener = new RxSessionBeanRequestListener();

    private MockHttpServletRequest request(int patient) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("demographicNo", String.valueOf(patient));
        return request;
    }

    private void finish(MockHttpServletRequest request) {
        listener.requestDestroyed(new ServletRequestEvent(request.getServletContext(), request));
    }

    private RxSessionBean openCompleted(int patient) {
        MockHttpServletRequest request = request(patient);
        RxSessionBean bean = RxSessionBeanResolver.activate(request, patient, "999998");
        finish(request);
        return bean;
    }

    private void exceedTarget(int first) {
        for (int i = first; i <= first + RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION; i++) {
            openCompleted(i);
        }
    }

    @Test
    void shouldKeepResolvedBeanAttached_untilConcurrentStagingFinishes() throws Exception {
        // Exercise the custom mutex as well as the bean monitor; the worker pauses before
        // acquiring the bean monitor, which an eviction-only synchronized block cannot protect.
        session.setAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE, new Object());
        RxSessionBean original = openCompleted(1);
        CountDownLatch resolved = new CountDownLatch(1);
        CountDownLatch mayStage = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var staging = executor.submit(() -> {
                MockHttpServletRequest request = request(1);
                try {
                    RxSessionBean bean = RxSessionBeanResolver.resolveForWrite(request);
                    resolved.countDown();
                    if (!mayStage.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Staging was not released");
                    }
                    synchronized (bean) {
                        bean.getStashList().add(new RxPrescriptionData.Prescription(0, "999998", 1));
                    }
                    return bean;
                } finally {
                    finish(request);
                }
            });
            try {
                assertThat(resolved.await(5, TimeUnit.SECONDS)).isTrue();
                exceedTarget(2);
                assertThat(RxSessionBeanResolver.find(session, 1)).isSameAs(original);
            } finally {
                mayStage.countDown();
            }
            assertThat(staging.get(5, TimeUnit.SECONDS)).isSameAs(original);
        }
        exceedTarget(100);
        assertThat(RxSessionBeanResolver.find(session, 1)).isSameAs(original);
        assertThat(original.getStashSize()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"activate", "ensure", "resolve"})
    void shouldPinEveryRequestEntryPoint_untilRequestDestroyed(String entryPoint) {
        RxSessionBean original = openCompleted(1);
        MockHttpServletRequest request = request(1);
        RxSessionBean bean = switch (entryPoint) {
            case "activate" -> RxSessionBeanResolver.activate(request, 1, "999998");
            case "ensure" -> RxSessionBeanResolver.ensure(request, 1, "999998");
            default -> RxSessionBeanResolver.resolve(request);
        };
        // Repeated action/JSP resolution in one request must acquire just one lease.
        assertThat(RxSessionBeanResolver.resolve(request)).isSameAs(bean);
        exceedTarget(2);
        assertThat(RxSessionBeanResolver.find(session, 1)).isSameAs(original);

        finish(request);
        openCompleted(100);

        assertThat(RxSessionBeanResolver.find(session, 1)).isNull();
    }

    @Test
    void shouldRetainBean_untilEveryOverlappingRequestCompletes() {
        RxSessionBean original = openCompleted(1);
        MockHttpServletRequest first = request(1);
        MockHttpServletRequest second = request(1);
        RxSessionBeanResolver.resolve(first);
        RxSessionBeanResolver.resolve(second);
        finish(first);
        finish(first); // Cleanup is idempotent, including after an error dispatch.
        exceedTarget(2);
        assertThat(RxSessionBeanResolver.find(session, 1)).isSameAs(original);

        second.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("Request failed"));
        finish(second);
        openCompleted(100);

        assertThat(RxSessionBeanResolver.find(session, 1)).isNull();
    }

    @Test
    void shouldReleaseAfterLogout_withoutAccessingOrRecreatingTheSession() {
        MockHttpServletRequest request = request(1);
        RxSessionBeanResolver.activate(request, 1, "999998");
        session.invalidate();

        assertThatCode(() -> finish(request)).doesNotThrowAnyException();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldAvoidCreatingSession_whenRequestIsUnrelated() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        finish(request);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldRegisterCleanupListener_inProductionWebDescriptor() throws Exception {
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml")))
                .contains("<listener-class>" + RxSessionBeanRequestListener.class.getName() + "</listener-class>");
    }
}
