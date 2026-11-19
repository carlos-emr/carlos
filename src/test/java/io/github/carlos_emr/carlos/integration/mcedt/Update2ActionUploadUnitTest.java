/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_UPLOAD_DETAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.UpdateRequest;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
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
    @DisplayName("should ignore uploads sent under another input name, and an empty list")
    void shouldIgnoreOtherInputs_whenNoContentUploadIsSent() {
        Update2Action action = new Update2Action();

        action.withUploadedFiles(List.of(uploaded("other", upload)));
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
