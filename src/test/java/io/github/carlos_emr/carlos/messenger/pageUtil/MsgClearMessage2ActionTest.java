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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

/**
 * Regression coverage for clearing Messenger attachments and its missing-bean redirect.
 *
 * @since 2026-08-11
 */
@DisplayName("MsgClearMessage2Action")
@Tag("integration")
@Tag("messenger")
class MsgClearMessage2ActionTest extends CarlosWebTestBase {

    private static final int MAX_PARENT_SEARCH_DEPTH = 8;
    private static final Path WEB_XML = resolveProjectPath(
            Path.of("src", "main", "webapp", "WEB-INF", "web.xml"));
    private MsgClearMessage2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        mockResponse = spy(new MockHttpServletResponse());
        setUpActionContext();
        action = new MsgClearMessage2Action();
    }

    @Test
    @DisplayName("should redirect without a session identifier when the message bean is missing")
    void shouldRedirectWithoutSessionId_whenMessageBeanIsMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setContextPath("/carlos");
        getMockRequest().setRequestURI("/carlos/messenger/ClearMessage");
        doAnswer(invocation -> invocation.<String>getArgument(0) + ";jsessionid=simulated-url-session")
                .when(getMockResponse())
                .encodeRedirectURL(anyString());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        String redirect = getMockResponse().getRedirectedUrl();
        assertThat(redirect)
                .isEqualTo("DisplayMessages")
                .doesNotContainIgnoringCase("jsessionid");
        assertThat(URI.create(getMockRequest().getRequestURL().toString()).resolve(redirect).getPath())
                .isEqualTo("/carlos/messenger/DisplayMessages");
        verify(getMockResponse(), never()).encodeRedirectURL(anyString());
    }

    @Test
    @DisplayName("should clear every attachment when the message bean exists")
    void shouldClearAttachments_whenMessageBeanExists() throws Exception {
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setAttachment("<item>document</item>");
        bean.setPDFAttachment("encoded-pdf");
        bean.setTotalAttachmentCount(3);
        bean.setCurrentAttachmentCount(2);
        getMockSession().setAttribute("msgSessionBean", bean);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(bean.getAttachment()).isNull();
        assertThat(bean.getPDFAttachment()).isNull();
        assertThat(bean.getTotalAttachmentCount()).isZero();
        assertThat(bean.getCurrentAttachmentCount()).isZero();
        assertThat(getMockResponse().getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should configure the application for cookie-only session tracking")
    void shouldUseCookieOnlySessionTracking_inDeploymentDescriptor() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document webXml = builder.parse(WEB_XML.toFile());
        NodeList trackingModes = webXml.getElementsByTagName("tracking-mode");

        assertThat(trackingModes.getLength()).isEqualTo(1);
        assertThat(trackingModes.item(0).getTextContent().trim()).isEqualTo("COOKIE");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
