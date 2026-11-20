/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Exercises the deployed interceptor definition with Spring's actual dependency resolution. */
@Tag("unit")
@Tag("security")
class AuthenticationInterceptorWiringUnitTest {

    @Test
    void shouldInitializeSecurity_withoutCreatingUnrelatedRequestActions() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document;
        try (InputStream input = getClass().getResourceAsStream("/spring_ws.xml")) {
            assertThat(input).isNotNull();
            document = factory.newDocumentBuilder().parse(input);
        }
        // Keep the actual bean and root defaults, excluding unrelated CXF endpoints and scans.
        Element root = document.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            if (!(child instanceof Element element)
                    || !"authenticationInWSS4JInterceptor".equals(element.getAttribute("id"))) {
                root.removeChild(child);
            }
            child = next;
        }

        AtomicInteger requestActionCreations = new AtomicInteger();
        OscarUsernameTokenValidator validator = mock(OscarUsernameTokenValidator.class);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            context.getBeanFactory().registerSingleton("oscarUsernameTokenValidator", validator);
            // By-type Map<String, Object> injection collects Object-valued beans;
            // it does not require each candidate to implement Map. Restoring the
            // old XML autowire="byType" invokes this supplier and fails refresh.
            RootBeanDefinition action = new RootBeanDefinition(Object.class, () -> {
                requestActionCreations.incrementAndGet();
                throw new IllegalStateException("Request action cannot be created outside an HTTP request");
            });
            action.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            context.registerBeanDefinition("requestDependentAction", action);
            new XmlBeanDefinitionReader(context).registerBeanDefinitions(document, null);
            context.refresh();

            AuthenticationInWSS4JInterceptor interceptor = context.getBean(
                    "authenticationInWSS4JInterceptor", AuthenticationInWSS4JInterceptor.class);
            assertThat(requestActionCreations).hasValue(0);
            assertThat(ReflectionTestUtils.getField(interceptor, "oscarUsernameTokenValidator"))
                    .isSameAs(validator);
            assertThat(interceptor.getOption(WSHandlerConstants.ACTION))
                    .isEqualTo(WSHandlerConstants.USERNAME_TOKEN);
            assertThat(interceptor.getOption(WSHandlerConstants.PASSWORD_TYPE))
                    .isEqualTo(WSS4JConstants.PW_TEXT);
            assertThat(interceptor.getOption(WSHandlerConstants.PW_CALLBACK_REF)).isSameAs(interceptor);
        }
    }
}
