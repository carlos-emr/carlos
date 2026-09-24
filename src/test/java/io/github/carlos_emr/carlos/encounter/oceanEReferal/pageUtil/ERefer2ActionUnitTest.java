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
package io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDao;
import io.github.carlos_emr.carlos.commn.model.EReferAttachment;
import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the server-side ownership checks in {@link ERefer2Action} (issue #3867).
 *
 * <p>The Ocean eReferral endpoint receives attachment ids and a consultation id from the browser.
 * These tests pin that a request naming another patient's record, or another patient's
 * consultation, is rejected before anything is stored or attached, and that the patient's own
 * records still go through.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("ERefer2Action attachment ownership")
@Tag("unit")
@Tag("security")
@Tag("consultation")
class ERefer2ActionUnitTest {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final int REQUEST_ID = 55;
    private static final String PROVIDER_NO = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private SecurityInfoManager securityInfoManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private EReferAttachmentDao eReferAttachmentDao;
    private AttachmentOwnershipService ownershipService;
    private LoggedInInfo loggedInInfo;

    private ERefer2Action action;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        eReferAttachmentDao = mock(EReferAttachmentDao.class);
        ownershipService = mock(AttachmentOwnershipService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull(String.class)))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), anyInt()))
                .thenReturn(true);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        request.setSession(session);
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        action = new ERefer2Action(securityInfoManager, documentAttachmentManager, eReferAttachmentDao, ownershipService);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    private void attachRequest(String documents) {
        request.setParameter("method", "attachOceanEReferralConsult");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("documents", documents);
    }

    private void editRequest(String documents) {
        request.setParameter("method", "editOceanEReferralConsult");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("requestId", String.valueOf(REQUEST_ID));
        request.setParameter("documents", documents);
    }

    @Nested
    @DisplayName("attachOceanEReferralConsult")
    class AttachPath {

        @Test
        @DisplayName("should persist and return the id when every attachment belongs to the patient")
        void shouldPersistAttachments_whenAllIdsOwnedByPatient() throws Exception {
            attachRequest("D10|L20|E30|H40|");
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(true);
            doAnswer(inv -> {
                Object persisted = inv.getArgument(0);
                ReflectionTestUtils.setField(persisted, "id", 77);
                return null;
            }).when(eReferAttachmentDao).persist(any(EReferAttachment.class));

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).isEqualTo("77");
            ArgumentCaptor<EReferAttachment> saved = ArgumentCaptor.forClass(EReferAttachment.class);
            verify(eReferAttachmentDao).persist(saved.capture());
            assertThat(saved.getValue().getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
            assertThat(saved.getValue().getAttachments())
                    .extracting(EReferAttachmentData::getLabType, EReferAttachmentData::getLabId)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("D", 10),
                            org.assertj.core.groups.Tuple.tuple("L", 20),
                            org.assertj.core.groups.Tuple.tuple("E", 30),
                            org.assertj.core.groups.Tuple.tuple("H", 40));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<DocumentType, Set<Integer>>> checked = ArgumentCaptor.forClass(Map.class);
            verify(ownershipService).allBelongToDemographic(checked.capture(), eq(DEMOGRAPHIC_NO));
            assertThat(checked.getValue())
                    .containsEntry(DocumentType.DOC, Set.of(10))
                    .containsEntry(DocumentType.LAB, Set.of(20))
                    .containsEntry(DocumentType.EFORM, Set.of(30))
                    .containsEntry(DocumentType.HRM, Set.of(40));
        }

        @Test
        @DisplayName("should reject with 403 and store nothing when an attachment belongs to another patient")
        void shouldRejectWithForbidden_whenAttachmentBelongsToAnotherPatient() throws Exception {
            attachRequest("D999");
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString())
                    .isEqualTo(ERefer2Action.ATTACHMENTS_NOT_VERIFIED)
                    .doesNotContain("999");
            verifyNoInteractions(eReferAttachmentDao);
        }

        @Test
        @DisplayName("should reject the whole request when own and foreign attachments are mixed")
        void shouldRejectWholeRequest_whenOwnAndForeignIdsMixed() throws Exception {
            attachRequest("D10|L20|D999");
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(eReferAttachmentDao, never()).persist(any());
        }

        @Test
        @DisplayName("should do nothing when the documents parameter is empty")
        void shouldDoNothing_forEmptyDocumentList() throws Exception {
            attachRequest("");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentAsString()).isEmpty();
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }

        @Test
        @DisplayName("should queue nothing when only form tokens are selected")
        void shouldQueueNothing_whenOnlyFormTokensSelected() throws Exception {
            attachRequest("F5|F6|");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }

        @Test
        @DisplayName("should reject with 400 when a token has an unknown type")
        void shouldRejectWithBadRequest_whenTokenTypeUnknown() throws Exception {
            attachRequest("D10|X20");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }

        @Test
        @DisplayName("should reject with 400 when an id is malformed or overflows")
        void shouldRejectWithBadRequest_whenIdMalformed() throws Exception {
            attachRequest("D10|L99999999999");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }

        @Test
        @DisplayName("should reject with 400 when demographicNo is not numeric")
        void shouldRejectWithBadRequest_whenDemographicNoNotNumeric() throws Exception {
            attachRequest("D10");
            request.setParameter("demographicNo", "1001 OR 1=1");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }

        @Test
        @DisplayName("should throw when the user lacks patient-level consultation write access")
        void shouldThrowSecurityException_whenPatientLevelConsultWriteDenied() {
            attachRequest("D10");
            when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), eq(DEMOGRAPHIC_NO)))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_con)");
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }
    }

    @Nested
    @DisplayName("editOceanEReferralConsult")
    class EditPath {

        @Test
        @DisplayName("should attach every type when the consultation and attachments belong to the patient")
        void shouldAttachAllTypes_whenConsultationAndAttachmentsOwned() throws Exception {
            editRequest("D10|D11|L20|E30|H40");
            when(ownershipService.consultationRequestBelongsToDemographic(REQUEST_ID, DEMOGRAPHIC_NO)).thenReturn(true);
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(documentAttachmentManager).attachToConsult(loggedInInfo, DocumentType.DOC,
                    new String[]{"10", "11"}, PROVIDER_NO, REQUEST_ID, DEMOGRAPHIC_NO, Boolean.TRUE);
            verify(documentAttachmentManager).attachToConsult(loggedInInfo, DocumentType.LAB,
                    new String[]{"20"}, PROVIDER_NO, REQUEST_ID, DEMOGRAPHIC_NO, Boolean.TRUE);
            verify(documentAttachmentManager).attachToConsult(loggedInInfo, DocumentType.EFORM,
                    new String[]{"30"}, PROVIDER_NO, REQUEST_ID, DEMOGRAPHIC_NO, Boolean.TRUE);
            verify(documentAttachmentManager).attachToConsult(loggedInInfo, DocumentType.HRM,
                    new String[]{"40"}, PROVIDER_NO, REQUEST_ID, DEMOGRAPHIC_NO, Boolean.TRUE);
        }

        @Test
        @DisplayName("should reject with 403 when the consultation request belongs to another patient")
        void shouldRejectWithForbidden_whenRequestIdBelongsToAnotherPatient() throws Exception {
            editRequest("D10");
            when(ownershipService.consultationRequestBelongsToDemographic(REQUEST_ID, DEMOGRAPHIC_NO)).thenReturn(false);
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(true);

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString()).isEqualTo(ERefer2Action.CONSULTATION_NOT_VERIFIED);
            verifyNoInteractions(documentAttachmentManager);
        }

        @Test
        @DisplayName("should reject with 403 and attach nothing when an attachment belongs to another patient")
        void shouldRejectWithForbidden_whenEditAttachmentBelongsToAnotherPatient() throws Exception {
            editRequest("D10|H999");
            when(ownershipService.consultationRequestBelongsToDemographic(REQUEST_ID, DEMOGRAPHIC_NO)).thenReturn(true);
            when(ownershipService.allBelongToDemographic(anyMap(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString()).isEqualTo(ERefer2Action.ATTACHMENTS_NOT_VERIFIED);
            verifyNoInteractions(documentAttachmentManager);
        }

        @Test
        @DisplayName("should reject with 400 when requestId is not numeric")
        void shouldRejectWithBadRequest_whenRequestIdNotNumeric() throws Exception {
            editRequest("D10");
            request.setParameter("requestId", "55abc");

            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(documentAttachmentManager, ownershipService);
        }
    }

    @Nested
    @DisplayName("request gate")
    class RequestGate {

        @Test
        @DisplayName("should reject GET with 405 before authorization or any side effect")
        void shouldRejectWithMethodNotAllowed_whenMethodIsGet() throws Exception {
            request.setMethod("GET");
            attachRequest("D10");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(securityInfoManager, eReferAttachmentDao, ownershipService, documentAttachmentManager);
        }

        @Test
        @DisplayName("should throw when the user lacks consultation write access")
        void shouldThrowSecurityException_whenConsultWriteDenied() {
            attachRequest("D10");
            when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_con)");
            verifyNoInteractions(eReferAttachmentDao, ownershipService);
        }
    }

    @Nested
    @DisplayName("parseAttachments")
    class ParseAttachments {

        @Test
        @DisplayName("should group ids by type and skip forms and empty tokens")
        void shouldGroupIdsByType_withFormAndEmptyTokensSkipped() {
            Map<DocumentType, Set<Integer>> parsed = ERefer2Action.parseAttachments("D1||d2|F3|L4|");

            assertThat(parsed).containsOnlyKeys(DocumentType.DOC, DocumentType.LAB);
            assertThat(parsed.get(DocumentType.DOC)).containsExactly(1, 2);
            assertThat(parsed.get(DocumentType.LAB)).containsExactly(4);
        }

        @Test
        @DisplayName("should return null when a token has trailing junk")
        void shouldReturnNull_forTokenWithTrailingJunk() {
            assertThat(ERefer2Action.parseAttachments("D1|D2x")).isNull();
            assertThat(ERefer2Action.parseAttachments("1D2")).isNull();
            assertThat(ERefer2Action.parseAttachments("D-1")).isNull();
        }

        @Test
        @DisplayName("should return an empty map for an empty list")
        void shouldReturnEmptyMap_forEmptyList() {
            assertThat(ERefer2Action.parseAttachments("")).isEmpty();
            assertThat(ERefer2Action.parseAttachments("|")).isEmpty();
            assertThat(List.copyOf(ERefer2Action.parseAttachments("F1").keySet())).isEmpty();
        }
    }
}
