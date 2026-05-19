/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Fax2Action}.
 */
@DisplayName("Fax2Action Unit Tests")
@Tag("unit")
@Tag("fax")
class Fax2ActionTest extends CarlosUnitTestBase {

    @Mock private FaxManager mockFaxManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private SecurityInfoManager mockSecurityInfoManager;

    @TempDir
    private Path previewDirectory;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoSession;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Fax2Action action;

    @BeforeEach
    void setUp() {
        mockitoSession = MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(FaxManager.class, mockFaxManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        action = new Fax2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mockitoSession != null) {
            mockitoSession.close();
        }
    }

    @Test
    @DisplayName("should evict oldest preview token when cache exceeds cap")
    void shouldEvictOldestPreviewToken_whenCacheLimitExceeded() throws Exception {
        Method registerPreviewPath = Fax2Action.class.getDeclaredMethod("registerFaxPreviewPath", Path.class);
        Method getPreviewPaths = Fax2Action.class.getDeclaredMethod("faxPreviewPaths", boolean.class);
        registerPreviewPath.setAccessible(true);
        getPreviewPaths.setAccessible(true);

        int maxTokens = getMaxFaxPreviewTokenLimit();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i <= maxTokens; i++) {
            Path fakePdf = Files.createTempFile(previewDirectory, "preview-" + i, ".pdf");
            String token = (String) registerPreviewPath.invoke(action, fakePdf);
            tokens.add(token);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> previewPaths = (Map<String, String>) getPreviewPaths.invoke(action, false);

        assertThat(previewPaths).hasSize(maxTokens);
        assertThat(previewPaths).doesNotContainKey(tokens.get(0));
        assertThat(previewPaths).containsKey(tokens.get(tokens.size() - 1));
        assertThat(previewPaths).isInstanceOf(ConcurrentHashMap.class);
    }

    @Test
    @DisplayName("should migrate legacy preview map instances from session to concurrent map")
    void shouldMigrateLegacyPreviewMap_whenSessionContainsHashMap() throws Exception {
        String legacyToken = "legacy-token";
        String legacyPath = Files.createTempFile(previewDirectory, "legacy", ".pdf").toString();
        Map<String, String> legacyMap = new java.util.HashMap<>();
        legacyMap.put(legacyToken, legacyPath);

        setSessionAttribute("FAX_PREVIEW_PATHS_SESSION_KEY", legacyMap);

        Method registerPreviewPath = Fax2Action.class.getDeclaredMethod("registerFaxPreviewPath", Path.class);
        Method getPreviewPaths = Fax2Action.class.getDeclaredMethod("faxPreviewPaths", boolean.class);
        registerPreviewPath.setAccessible(true);
        getPreviewPaths.setAccessible(true);

        String newToken = (String) registerPreviewPath.invoke(action, Files.createTempFile(previewDirectory, "new", ".pdf"));
        @SuppressWarnings("unchecked")
        Map<String, String> previewPaths = (Map<String, String>) getPreviewPaths.invoke(action, false);

        assertThat(previewPaths).isInstanceOf(ConcurrentHashMap.class);
        assertThat(previewPaths).containsKey(legacyToken);
        assertThat(previewPaths).containsKey(newToken);
        assertThat(previewPaths.get(legacyToken)).isEqualTo(legacyPath);
    }

    private int getMaxFaxPreviewTokenLimit() throws Exception {
        Field maxField = Fax2Action.class.getDeclaredField("MAX_FAX_PREVIEW_PATHS");
        maxField.setAccessible(true);
        return (int) maxField.get(null);
    }

    private void setSessionAttribute(String constantFieldName, Object value) throws Exception {
        Field sessionKeyField = Fax2Action.class.getDeclaredField(constantFieldName);
        sessionKeyField.setAccessible(true);
        request.getSession(true).setAttribute((String) sessionKeyField.get(null), value);
    }
}
