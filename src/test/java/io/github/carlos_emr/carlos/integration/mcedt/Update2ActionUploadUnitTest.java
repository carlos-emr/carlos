/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_UPLOAD_DETAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.ontario.health.edt.CommonResult;
import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.ResponseResult;
import ca.ontario.health.edt.UpdateRequest;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * MCEDT Update Upload replaces an outbox resource's content with a file the user chooses. Struts 7's
 * {@code actionFileUpload} interceptor hands uploads only to {@link UploadedFilesAware} actions, so the
 * old parameter-bound {@code content} file was always {@code null} and every update failed (#2212).
 */
@Tag("unit")
@Tag("mcedt")
@DisplayName("MCEDT Update Upload file binding")
class Update2ActionUploadUnitTest {

    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;
    private File upload;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        upload = File.createTempFile("mcedt-update", ".txt");
        Files.writeString(upload.toPath(), "SYNTHETIC REPLACEMENT CONTENT", StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servlet != null) {
            servlet.close();
        }
        if (upload != null) {
            Files.deleteIfExists(upload.toPath());
        }
    }

    @Test
    @DisplayName("should receive uploads through UploadedFilesAware")
    void shouldImplementUploadedFilesAware_forFrameworkUploads() {
        assertThat(UploadedFilesAware.class).isAssignableFrom(Update2Action.class);
    }

    @Test
    @DisplayName("should keep the form's content file from the upload interceptor")
    void shouldBindContent_fromUploadedFiles() throws Exception {
        Update2Action action = new Update2Action();

        action.withUploadedFiles(List.of(uploaded("content", upload)));

        assertThat(action.getContent()).isNotNull();
        assertThat(action.getContent().getCanonicalPath()).isEqualTo(upload.getCanonicalPath());
    }

    @Test
    @DisplayName("should ignore missing uploads, other input names, and non-file parts")
    void shouldIgnoreOtherInputs_whenNoContentUploadIsSent() {
        Update2Action action = new Update2Action();
        UploadedFile nonFile = mock(UploadedFile.class);
        when(nonFile.getInputName()).thenReturn("content");

        action.withUploadedFiles(Arrays.asList(null, uploaded("other", upload), nonFile));
        action.withUploadedFiles(null);

        assertThat(action.getContent()).isNull();
    }

    @Test
    @DisplayName("should queue the uploaded bytes for the resource being updated")
    void shouldQueueUpdate_withUploadedBytes() {
        Detail details = listedResources(42);
        Update2Action action = new Update2Action();
        action.setResourceId("42");
        action.withUploadedFiles(List.of(uploaded("content", upload)));

        assertThat(action.addUpdateRequest()).isEqualTo("initial");

        List<UpdateRequest> queued = ActionUtils.getUpdateList(request);
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getResourceID()).isEqualTo(BigInteger.valueOf(42));
        assertThat(new String(queued.get(0).getContent(), StandardCharsets.UTF_8))
                .isEqualTo("SYNTHETIC REPLACEMENT CONTENT");
        assertThat(details.getData().get(0).getModifyTimestamp()).as("the listed resource is marked updated").isNotNull();
    }

    @Test
    @DisplayName("should replace, not repeat, a resource that is already queued")
    void shouldReplaceQueuedUpdate_whenSameResourceIsAddedAgain() throws Exception {
        listedResources(42);
        Update2Action first = new Update2Action();
        first.setResourceId("42");
        first.withUploadedFiles(List.of(uploaded("content", upload)));
        first.addUpdateRequest();
        Files.writeString(upload.toPath(), "SYNTHETIC SECOND CONTENT", StandardCharsets.UTF_8);
        Update2Action second = new Update2Action();
        second.setResourceId("42");
        second.withUploadedFiles(List.of(uploaded("content", upload)));

        second.addUpdateRequest();

        List<UpdateRequest> queued = ActionUtils.getUpdateList(request);
        assertThat(queued).hasSize(1);
        assertThat(new String(queued.get(0).getContent(), StandardCharsets.UTF_8))
                .isEqualTo("SYNTHETIC SECOND CONTENT");
    }

    @Test
    @DisplayName("should keep uploads queued during a send, including a replacement for a sent resource")
    void shouldKeepPendingUpdates_whenFilesAreAddedDuringSend() throws Exception {
        Detail details = listedResources(42, 43, 47);
        add(42);
        add(43);
        List<UpdateRequest> original = List.copyOf(ActionUtils.getUpdateList(request));
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch reply = new CountDownLatch(1);
        EDTDelegate delegate = mock(EDTDelegate.class);
        when(delegate.update(anyList())).thenAnswer(call -> {
            List<UpdateRequest> sent = call.getArgument(0);
            sending.countDown();
            assertThat(reply.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(sent).containsExactlyElementsOf(original);
            return reply(42, CONFIRMED, 43, CONFIRMED);
        });
        Update2Action sender = sendingAction();
        MockHttpServletRequest senderRequest = new MockHttpServletRequest();
        senderRequest.setSession((MockHttpSession) request.getSession());
        sender.request = senderRequest;

        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> sender.sendUpdateRequest(() -> delegate));
            try {
                assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
                Files.writeString(upload.toPath(), "SYNTHETIC LATER CONTENT", StandardCharsets.UTF_8);
                add(42);
                add(47);

                EDTDelegate duplicate = mock(EDTDelegate.class);
                Update2Action other = sendingAction();
                doReturn("Send already pending").when(other).getText("updateAction.sendUpdateRequest.inProgress");
                assertThat(other.sendUpdateRequest(() -> duplicate)).isEqualTo("success");
                assertThat(other.getActionErrors()).containsExactly("Send already pending");
                verifyNoInteractions(duplicate);
            } finally {
                reply.countDown();
            }
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("success");
        }

        assertThat(ActionUtils.getUpdateList(request)).extracting(UpdateRequest::getResourceID)
                .containsExactly(BigInteger.valueOf(42), BigInteger.valueOf(47));
        assertThat(new String(ActionUtils.getUpdateList(request).getFirst().getContent(), StandardCharsets.UTF_8))
                .isEqualTo("SYNTHETIC LATER CONTENT");
        assertThat(details.getData().get(0).getModifyTimestamp()).isNotNull();
        assertThat(details.getData().get(1).getModifyTimestamp()).isNull();
        assertThat(details.getData().get(2).getModifyTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should keep the batch pending after a failed service call and allow it to be retried")
    void shouldAllowRetry_whenSendFails() throws Exception {
        listedResources(42);
        add(42);
        EDTDelegate delegate = mock(EDTDelegate.class);
        when(delegate.update(anyList())).thenThrow(new IllegalStateException("Synthetic service failure"))
                .thenReturn(reply(42, CONFIRMED));
        var first = sendingAction();
        doReturn("Send failed").when(first).getText(eq("updateAction.sendUpdateRequest.failure"), any(String[].class));

        assertThat(first.sendUpdateRequest(() -> delegate)).isEqualTo("success");
        assertThat(first.getActionErrors()).containsExactly("Send failed");
        assertThat(ActionUtils.getUpdateList(request)).hasSize(1);

        assertThat(sendingAction().sendUpdateRequest(() -> delegate)).isEqualTo("success");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
        assertThat(request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS)).isNull();
    }

    @Test
    @DisplayName("should discard pending files on explicit cancel and refuse an empty send")
    void shouldDiscardPendingUpdates_whenCancelled() {
        listedResources(42);
        add(42);
        Update2Action action = sendingAction();
        doReturn("Nothing to send").when(action).getText("updateAction.sendUpdateRequest.emptyUpdates");

        assertThat(action.cancel()).isEqualTo("cancel");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
        assertThat(request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS)).isNull();
        assertThat(action.sendUpdateRequest()).isEqualTo("success");
        assertThat(action.getActionErrors()).containsExactly("Nothing to send");
    }

    @Test
    @DisplayName("should keep a refused update queued and report the partial outcome, not success")
    void shouldKeepRefusedUpdateQueued_whenTheReplyRefusesOneResource() throws Exception {
        Detail details = listedResources(42, 43);
        add(42);
        add(43);
        EDTDelegate delegate = mock(EDTDelegate.class);
        when(delegate.update(anyList())).thenReturn(reply(42, CONFIRMED, 43, "EEDTS0056"));
        Update2Action action = sendingAction();
        doReturn("Partly sent").when(action).getText(eq("updateAction.sendUpdateRequest.partial"), any(String[].class));

        assertThat(action.sendUpdateRequest(() -> delegate)).isEqualTo("success");

        assertThat(action.getActionMessages()).isEmpty();
        assertThat(action.getActionErrors()).containsExactly("Partly sent");
        verify(action).getText(eq("updateAction.sendUpdateRequest.partial"),
                argThat((String[] args) -> args.length == 3 && "1".equals(args[0]) && "2".equals(args[1])));
        assertThat(ActionUtils.getUpdateList(request)).extracting(UpdateRequest::getResourceID)
                .containsExactly(BigInteger.valueOf(43));
        assertThat(details.getData().get(0).getModifyTimestamp()).as("42 was confirmed").isNull();
        assertThat(details.getData().get(1).getModifyTimestamp()).as("43 is still queued").isNotNull();
    }

    @Test
    @DisplayName("should retain a resource whose response entries disagree")
    void shouldKeepAnUnconfirmedUpdate_whenItsReplyEntriesDisagree() throws Exception {
        listedResources(42, 43);
        add(42);
        add(43);
        EDTDelegate delegate = mock(EDTDelegate.class);
        ResourceResult response = reply(42, CONFIRMED, 43, CONFIRMED, 42, "EEDTS0056");
        response.getResponse().add(null);
        when(delegate.update(anyList())).thenReturn(response);
        Update2Action action = sendingAction();
        doReturn("Partly sent").when(action).getText(eq("updateAction.sendUpdateRequest.partial"), any(String[].class));

        assertThat(action.sendUpdateRequest(() -> delegate)).isEqualTo("success");

        assertThat(action.getActionMessages()).isEmpty();
        assertThat(action.getActionErrors()).containsExactly("Partly sent");
        verify(action).getText(eq("updateAction.sendUpdateRequest.partial"),
                argThat((String[] args) -> "1".equals(args[0]) && "2".equals(args[1])));
        assertThat(ActionUtils.getUpdateList(request)).extracting(UpdateRequest::getResourceID)
                .containsExactly(BigInteger.valueOf(42));
    }

    @Test
    @DisplayName("should keep an update the reply does not mention, and confirm it on a later send")
    void shouldKeepUnconfirmedUpdateQueued_whenTheReplyOmitsIt() throws Exception {
        listedResources(42, 43);
        add(42);
        add(43);
        EDTDelegate delegate = mock(EDTDelegate.class);
        when(delegate.update(anyList())).thenReturn(reply(42, CONFIRMED)).thenReturn(reply(43, CONFIRMED));
        Update2Action first = sendingAction();
        doReturn("Partly sent").when(first).getText(eq("updateAction.sendUpdateRequest.partial"), any(String[].class));

        first.sendUpdateRequest(() -> delegate);
        assertThat(ActionUtils.getUpdateList(request)).extracting(UpdateRequest::getResourceID)
                .containsExactly(BigInteger.valueOf(43));

        Update2Action retry = sendingAction();
        retry.sendUpdateRequest(() -> delegate);
        assertThat(retry.getActionMessages()).containsExactly("Sent");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
        assertThat(request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS)).isNull();
    }

    @Test
    @DisplayName("should confirm nothing from an empty, malformed or refusing reply")
    void shouldConfirmNothing_whenTheReplyCarriesNoAcceptedResult() {
        listedResources(42);
        add(42);
        List<UpdateRequest> sent = List.copyOf(ActionUtils.getUpdateList(request));
        ResourceResult malformed = reply(42, "EEDTS0056");
        malformed.getResponse().add(null);
        malformed.getResponse().add(new ResponseResult());
        ResponseResult withoutResult = new ResponseResult();
        withoutResult.setResourceID(BigInteger.valueOf(42));
        malformed.getResponse().add(withoutResult);

        assertThat(Update2Action.confirmedUpdates(sent, null)).isEmpty();
        assertThat(Update2Action.confirmedUpdates(sent, malformed)).isEmpty();
        assertThat(Update2Action.confirmedUpdates(sent, reply(42, CONFIRMED))).containsExactlyElementsOf(sent);
    }

    @Test
    @DisplayName("should keep the batch after an empty reply even when the page's resource list is gone")
    void shouldKeepBatch_whenTheReplyIsEmptyAndTheResourceListIsGone() throws Exception {
        listedResources(42);
        add(42);
        request.getSession().removeAttribute(SESSION_KEY_UPLOAD_DETAILS);
        EDTDelegate delegate = mock(EDTDelegate.class);
        when(delegate.update(anyList())).thenReturn(null);
        Update2Action action = sendingAction();
        doReturn("Partly sent").when(action).getText(eq("updateAction.sendUpdateRequest.partial"), any(String[].class));

        assertThat(action.sendUpdateRequest(() -> delegate)).isEqualTo("success");

        assertThat(action.getActionErrors()).containsExactly("Partly sent");
        verify(action).getText(eq("updateAction.sendUpdateRequest.partial"),
                argThat((String[] args) -> "1".equals(args[0]) && "1".equals(args[1]) && "".equals(args[2])));
        assertThat(ActionUtils.getUpdateList(request)).hasSize(1);
    }

    private static final String CONFIRMED = "IEDTS0001";

    /** A synthetic EDT reply: resource ID and result code pairs. */
    private static ResourceResult reply(Object... idsAndCodes) {
        ResourceResult result = new ResourceResult();
        for (int i = 0; i < idsAndCodes.length; i += 2) {
            ResponseResult response = new ResponseResult();
            response.setResourceID(BigInteger.valueOf((Integer) idsAndCodes[i]));
            CommonResult code = new CommonResult();
            code.setCode((String) idsAndCodes[i + 1]);
            code.setMsg("SYNTHETIC RESULT");
            response.setResult(code);
            result.getResponse().add(response);
        }
        return result;
    }

    private void add(int resourceId) {
        Update2Action action = new Update2Action();
        action.setResourceId(Integer.toString(resourceId));
        action.withUploadedFiles(List.of(uploaded("content", upload)));
        assertThat(action.addUpdateRequest()).isEqualTo("initial");
    }

    private Update2Action sendingAction() {
        Update2Action action = spy(new Update2Action());
        doReturn("Sent").when(action).getText(eq("updateAction.sendUpdateRequest.success"), any(String[].class));
        return action;
    }

    @Test
    @DisplayName("should refuse a resource that is not on the list the page loaded")
    void shouldRefuseUpdate_whenResourceIsNotListed() {
        listedResources(42);
        Update2Action action = spy(new Update2Action());
        doReturn("not on the list").when(action).getText("updateAction.addUpdateRequest.unknownResource");
        action.setResourceId("99");
        action.withUploadedFiles(List.of(uploaded("content", upload)));

        assertThat(action.addUpdateRequest()).isEqualTo("initial");

        assertThat(action.getActionErrors()).containsExactly("not on the list");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
    }

    @Test
    @DisplayName("should queue nothing when the page's resource list is no longer in the session")
    void shouldRefuseUpdate_whenResourceListIsMissing() {
        Update2Action action = spy(new Update2Action());
        doReturn("not on the list").when(action).getText("updateAction.addUpdateRequest.unknownResource");
        action.setResourceId("42");
        action.withUploadedFiles(List.of(uploaded("content", upload)));

        assertThat(action.addUpdateRequest()).isEqualTo("initial");

        assertThat(action.getActionErrors()).containsExactly("not on the list");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
    }

    private Detail listedResources(int... ids) {
        Detail details = new Detail();
        for (int id : ids) {
            DetailData data = new DetailData();
            data.setResourceID(BigInteger.valueOf(id));
            details.getData().add(data);
        }
        request.getSession().setAttribute(SESSION_KEY_UPLOAD_DETAILS, details);
        return details;
    }

    @Test
    @DisplayName("should tell the user to choose a file, not fail, when no file arrived")
    void shouldReportMissingFile_whenNoUploadReachedTheAction() {
        Update2Action action = spy(new Update2Action());
        doReturn("Choose a file to upload before updating")
                .when(action).getText("updateAction.addUpdateRequest.missingFile");
        action.setResourceId("42");

        assertThat(action.addUpdateRequest()).isEqualTo("initial");

        assertThat(action.getActionErrors()).containsExactly("Choose a file to upload before updating");
        assertThat(ActionUtils.getUpdateList(request)).isEmpty();
    }

    private static UploadedFile uploaded(String inputName, File file) {
        UploadedFile uploaded = mock(UploadedFile.class);
        when(uploaded.getInputName()).thenReturn(inputName);
        when(uploaded.isFile()).thenReturn(true);
        when(uploaded.getAbsolutePath()).thenReturn(file.getAbsolutePath());
        when(uploaded.getContent()).thenReturn(file);
        return uploaded;
    }
}
