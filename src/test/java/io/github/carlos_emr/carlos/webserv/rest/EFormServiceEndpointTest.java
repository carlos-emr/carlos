/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.eform.EFormInstanceService;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link EFormService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("EFormService REST endpoint tests")
class EFormServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private EFormDao mockEFormDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private EFormInstanceService mockInstanceService;

    @Override
    protected Object getServiceBean() {
        EFormService service = new EFormService();
        injectDependency(service, "eFormDao", mockEFormDao);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "eFormInstanceService", mockInstanceService);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), any(), any()))
            .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(mockLoggedInInfo.getIp()).thenReturn("127.0.0.1");
    }

    @Nested
    @DisplayName("GET /eform/{dataId}")
    class LoadEForm {

        @Test
        @DisplayName("should return 200 with eForm when found")
        void shouldReturn200_whenEFormFound() {
            EForm eform = new EForm();
            eform.setFormName("Test Form");
            eform.setFormHtml("<html></html>");
            when(mockEFormDao.findById(1)).thenReturn(eform);

            Response response = request().path("/eform/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/body/formName").textValue()).isEqualTo("Test Form");
            assertThat(wireJson.at("/status").textValue()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("should return 404 with error when eForm not found")
        void shouldReturn404WithError_whenEFormNotFound() {
            when(mockEFormDao.findById(999)).thenReturn(null);

            Response response = request().path("/eform/999").get();

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.readEntity(String.class)).contains("Failed to find EForm");
        }
    }

    @Nested
    @DisplayName("POST /eform/json")
    class SaveEFormJson {

        @Test
        @DisplayName("should return 200 when saving valid eForm JSON")
        void shouldReturn200_whenSavingValidJson() {
            when(mockEFormDao.findByName(any())).thenReturn(null);

            String json = "{\"formName\":\"New Form\",\"formHtml\":\"<html>test</html>\"}";

            Response response = request().path("/eform/json")
                .post(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/body/formName").textValue()).isEqualTo("New Form");
            assertThat(wireJson.at("/status").textValue()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("should return 200 with error when name already exists")
        void shouldReturn200WithError_whenNameAlreadyExists() {
            EForm existing = new EForm();
            existing.setFormName("Existing Form");
            when(mockEFormDao.findByName("Existing Form")).thenReturn(existing);

            String json = "{\"formName\":\"Existing Form\",\"formHtml\":\"<html>test</html>\"}";

            Response response = request().path("/eform/json")
                .post(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("EForm Name Already in Use");
            assertThat(wireJson.at("/status").textValue()).isEqualTo("ERROR");
        }
    }

    private static EFormData instanceRow(int fdid, boolean current) {
        EFormData data = new EFormData();
        data.setId(fdid);
        data.setFormId(3);
        data.setFormName("Vitals");
        data.setSubject("Follow-up");
        data.setDemographicId(42);
        data.setProviderNo("999998");
        data.setCurrent(current);
        data.setFormData("<html><form><input name=\"bp\" value=\"120/80\"></form></html>");
        return data;
    }

    @Nested
    @DisplayName("GET /eform/instance/{fdid}")
    class GetInstance {

        @Test
        @DisplayName("should return the instance with its HTML and values")
        void shouldReturnInstance_withHtmlAndValues() {
            when(mockInstanceService.get(any(), eq(5)))
                .thenReturn(new EFormInstanceService.Instance(instanceRow(5, true), Map.of("bp", "120/80")));

            Response response = request().path("/eform/instance/5").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("SUCCESS");
            assertThat(wireJson.at("/body/fdid").intValue()).isEqualTo(5);
            assertThat(wireJson.at("/body/fid").intValue()).isEqualTo(3);
            assertThat(wireJson.at("/body/demographicNo").intValue()).isEqualTo(42);
            assertThat(wireJson.at("/body/current").booleanValue()).isTrue();
            assertThat(wireJson.at("/body/values/bp").textValue()).isEqualTo("120/80");
            assertThat(wireJson.at("/body/formHtml").textValue()).contains("120/80");
        }

        @Test
        @DisplayName("should return 404 when no instance has the id")
        void shouldReturn404_whenInstanceMissing() {
            when(mockInstanceService.get(any(), eq(9)))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND));

            Response response = request().path("/eform/instance/9").get();

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.readEntity(String.class)).contains("EForm instance not found");
        }

        @Test
        @DisplayName("should answer access denied when the service refuses the caller")
        void shouldAnswerAccessDenied_whenServiceRefusesCaller() {
            when(mockInstanceService.get(any(), eq(5)))
                .thenThrow(new SecurityException("missing required sec object (_eform)"));

            Response response = request().path("/eform/instance/5").get();

            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("ERROR");
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("Access Denied");
        }

        @Test
        @DisplayName("should answer 404, as for a missing instance, when the refusal is for this instance's patient or role")
        void shouldReturn404_whenInstanceAccessDenied() {
            when(mockInstanceService.get(any(), eq(5)))
                .thenThrow(new EFormInstanceService.InstanceAccessDeniedException("missing required sec object (_eform)"));
            when(mockInstanceService.revise(any(), eq(5), any(), any(), any()))
                .thenThrow(new EFormInstanceService.InstanceAccessDeniedException("missing required sec object (_eform)"));
            doThrow(new EFormInstanceService.InstanceAccessDeniedException("missing required sec object (_eform)"))
                .when(mockInstanceService).delete(any(), eq(5));

            for (Response response : List.of(request().path("/eform/instance/5").get(),
                    request().path("/eform/instance/5").put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}")),
                    request().path("/eform/instance/5").delete())) {
                assertThat(response.getStatus()).isEqualTo(404);
                assertThat(response.readEntity(String.class)).contains("EForm instance not found");
            }
        }
    }

    @Nested
    @DisplayName("POST /eform/instance")
    class CreateInstance {

        @Test
        @DisplayName("should create the instance from the template id, patient and values")
        void shouldCreateInstance_fromTemplatePatientAndValues() {
            when(mockInstanceService.create(any(), eq(3), eq(42), eq("Follow-up"), eq(Map.of("bp", "120/80")), any()))
                .thenReturn(instanceRow(77, true));

            String json = "{\"fid\":3,\"demographicNo\":42,\"subject\":\"Follow-up\",\"values\":{\"bp\":\"120/80\"}}";
            Response response = request().path("/eform/instance").post(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("SUCCESS");
            assertThat(wireJson.at("/body/fdid").intValue()).isEqualTo(77);
            assertThat(wireJson.at("/body/values/bp").textValue()).isEqualTo("120/80");
            assertThat(wireJson.at("/body/formHtml").isNull() || wireJson.at("/body/formHtml").isMissingNode()).isTrue();
        }

        @Test
        @DisplayName("should refuse without calling the service when fid or demographicNo is missing")
        void shouldRefuseWithoutCallingService_whenRequiredIdsMissing() {
            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3}"));

            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("ERROR");
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("fid and demographicNo are required");
            org.mockito.Mockito.verifyNoInteractions(mockInstanceService);
        }

        @Test
        @DisplayName("should report the reason when the template cannot be used")
        void shouldReportReason_whenTemplateUnavailable() {
            when(mockInstanceService.create(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.TEMPLATE_UNAVAILABLE));

            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3,\"demographicNo\":42}"));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("EForm template not found or not active");
        }
    }

    @Nested
    @DisplayName("PUT /eform/instance/{fdid}")
    class ReviseInstance {

        @Test
        @DisplayName("should return the new revision when the instance is current")
        void shouldReturnNewRevision_whenInstanceCurrent() {
            when(mockInstanceService.revise(any(), eq(5), isNull(), anyMap(), any())).thenReturn(instanceRow(78, true));

            // A GET response sent straight back: server-set fields, including fdid, are ignored.
            String json = "{\"fdid\":5,\"fid\":3,\"demographicNo\":42,\"current\":true,\"formHtml\":\"x\",\"values\":{\"bp\":\"130/85\"}}";
            Response response = request().path("/eform/instance/5").put(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/body/fdid").intValue()).isEqualTo(78);
            verify(mockInstanceService).revise(any(), eq(5), isNull(), eq(Map.of("bp", "130/85")), any());
        }

        @Test
        @DisplayName("should return 409 when the instance was already deleted or revised")
        void shouldReturn409_whenInstanceRetired() {
            when(mockInstanceService.revise(any(), eq(5), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.RETIRED));

            Response response = request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}"));

            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.readEntity(String.class)).contains("EForm instance is no longer current");
        }

        @Test
        @DisplayName("should return 409 when a newer form of a latest-only template replaced the instance")
        void shouldReturn409_whenInstanceSuperseded() {
            when(mockInstanceService.revise(any(), eq(5), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.SUPERSEDED));

            Response response = request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}"));

            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.readEntity(String.class)).contains("A newer form of this template exists");
        }

        @Test
        @DisplayName("should pass a body without values to the service as missing, not as an empty map")
        void shouldPassMissingValues_whenBodyNamesNone() {
            when(mockInstanceService.revise(any(), eq(5), any(), isNull(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.VALUES_REQUIRED));

            Response response = request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"subject\":\"only a subject\"}"));

            assertThat(responseJson(response).at("/error/message").textValue())
                .isEqualTo(EFormInstanceException.Reason.VALUES_REQUIRED.message());
            verify(mockInstanceService).revise(any(), eq(5), eq("only a subject"), isNull(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /eform/instance/{fdid}")
    class DeleteInstance {

        @Test
        @DisplayName("should return the retired fdid when the instance is current")
        void shouldReturnRetiredFdid_whenInstanceCurrent() {
            Response response = request().path("/eform/instance/5").delete();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("SUCCESS");
            assertThat(wireJson.at("/body").intValue()).isEqualTo(5);
            verify(mockInstanceService).delete(any(), eq(5));
        }

        @Test
        @DisplayName("should accept the request when the client sends no content type")
        void shouldAcceptRequest_whenNoContentTypeSent() {
            Response response = request().path("/eform/instance/5")
                .replaceHeader("Content-Type", (Object) null).delete();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 404 when no instance has the id")
        void shouldReturn404_whenInstanceMissing() {
            doThrow(new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND))
                .when(mockInstanceService).delete(any(), eq(9));

            Response response = request().path("/eform/instance/9").delete();

            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    /**
     * Each instance endpoint first checks the {@code _eform} right with no patient attached, so a
     * caller without it learns nothing about which fdids exist. Only the right the endpoint needs
     * is refused here, which also pins which right that is.
     */
    @Nested
    @DisplayName("instance endpoints: coarse _eform gate")
    class CoarseGate {

        private void denyGlobal(String privilege) {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq(privilege), isNull()))
                .thenReturn(false);
        }

        private void assertAccessDeniedWithoutService(Response response) {
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("ERROR");
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("Access Denied");
            verifyNoInteractions(mockInstanceService);
        }

        @Test
        @DisplayName("GET should need _eform read")
        void shouldDenyWithoutCallingService_whenReadRightMissing() {
            denyGlobal(SecurityInfoManager.READ);

            assertAccessDeniedWithoutService(request().path("/eform/instance/5").get());
        }

        @Test
        @DisplayName("POST should need _eform write")
        void shouldDenyWithoutCallingService_whenWriteRightMissingForCreate() {
            denyGlobal(SecurityInfoManager.WRITE);

            assertAccessDeniedWithoutService(request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3,\"demographicNo\":42}")));
        }

        @Test
        @DisplayName("PUT should need _eform write, as the browser re-save does")
        void shouldDenyWithoutCallingService_whenWriteRightMissingForRevise() {
            denyGlobal(SecurityInfoManager.WRITE);

            assertAccessDeniedWithoutService(request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}")));
        }

        @Test
        @DisplayName("DELETE should need _eform write")
        void shouldDenyWithoutCallingService_whenWriteRightMissingForDelete() {
            denyGlobal(SecurityInfoManager.WRITE);

            assertAccessDeniedWithoutService(request().path("/eform/instance/5").delete());
        }
    }

    @Nested
    @DisplayName("instance endpoints: refusals from the service")
    class ServiceRefusals {

        @Test
        @DisplayName("GET should return a retired revision as history with current=false")
        void shouldReturnRetiredRevision_withCurrentFalse() {
            when(mockInstanceService.get(any(), eq(5)))
                .thenReturn(new EFormInstanceService.Instance(instanceRow(5, false), Map.of()));

            Response response = request().path("/eform/instance/5").get();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).at("/body/current").booleanValue()).isFalse();
        }

        @Test
        @DisplayName("POST should refuse without calling the service when fid is missing")
        void shouldRefuseWithoutCallingService_whenFidMissing() {
            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"demographicNo\":42}"));

            assertThat(responseJson(response).at("/error/message").textValue())
                .isEqualTo("fid and demographicNo are required");
            verifyNoInteractions(mockInstanceService);
        }

        @Test
        @DisplayName("POST should report the reason when the patient does not exist")
        void shouldReportReason_whenPatientMissing() {
            when(mockInstanceService.create(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.PATIENT_NOT_FOUND));

            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3,\"demographicNo\":42}"));

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).at("/error/message").textValue()).isEqualTo("Patient not found");
        }

        @Test
        @DisplayName("POST should report the reason when a field name is blank")
        void shouldReportReason_whenFieldNameBlank() {
            when(mockInstanceService.create(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.INVALID_FIELD_NAME));

            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3,\"demographicNo\":42,\"values\":{\" \":\"x\"}}"));

            assertThat(responseJson(response).at("/error/message").textValue())
                .isEqualTo(EFormInstanceException.Reason.INVALID_FIELD_NAME.message());
        }

        @Test
        @DisplayName("POST should answer access denied when the service refuses the patient")
        void shouldAnswerAccessDenied_whenServiceRefusesCreate() {
            when(mockInstanceService.create(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new SecurityException("missing required sec object (_eform)"));

            Response response = request().path("/eform/instance")
                .post(jakarta.ws.rs.client.Entity.json("{\"fid\":3,\"demographicNo\":42}"));

            assertThat(responseJson(response).at("/error/message").textValue()).isEqualTo("Access Denied");
        }

        @Test
        @DisplayName("PUT should pass a new subject through to the revision")
        void shouldPassSubjectThrough_whenRevising() {
            when(mockInstanceService.revise(any(), eq(5), eq("Corrected"), anyMap(), any())).thenReturn(instanceRow(78, true));

            Response response = request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"subject\":\"Corrected\",\"values\":{\"bp\":\"1\"}}"));

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockInstanceService).revise(any(), eq(5), eq("Corrected"), eq(Map.of("bp", "1")), any());
        }

        @Test
        @DisplayName("PUT should return 404 when no instance has the id")
        void shouldReturn404_whenRevisingMissingInstance() {
            when(mockInstanceService.revise(any(), eq(9), any(), any(), any()))
                .thenThrow(new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND));

            Response response = request().path("/eform/instance/9")
                .put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}"));

            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("PUT should answer access denied when the service refuses the patient")
        void shouldAnswerAccessDenied_whenServiceRefusesRevise() {
            when(mockInstanceService.revise(any(), eq(5), any(), any(), any()))
                .thenThrow(new SecurityException("missing required sec object (_eform)"));

            Response response = request().path("/eform/instance/5")
                .put(jakarta.ws.rs.client.Entity.json("{\"values\":{}}"));

            assertThat(responseJson(response).at("/error/message").textValue()).isEqualTo("Access Denied");
        }

        @Test
        @DisplayName("DELETE should return 409 when the instance was already deleted or revised")
        void shouldReturn409_whenDeletingRetiredInstance() {
            doThrow(new EFormInstanceException(EFormInstanceException.Reason.RETIRED))
                .when(mockInstanceService).delete(any(), eq(5));

            Response response = request().path("/eform/instance/5").delete();

            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.readEntity(String.class)).contains("EForm instance is no longer current");
        }

        @Test
        @DisplayName("DELETE should answer access denied when the service refuses the patient")
        void shouldAnswerAccessDenied_whenServiceRefusesDelete() {
            doThrow(new SecurityException("missing required sec object (_eform)"))
                .when(mockInstanceService).delete(any(), eq(5));

            Response response = request().path("/eform/instance/5").delete();

            var wireJson = responseJson(response);
            assertThat(wireJson.at("/status").textValue()).isEqualTo("ERROR");
            assertThat(wireJson.at("/error/message").textValue()).isEqualTo("Access Denied");
        }
    }
}
