/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
@Tag("security")
class SoapInterceptorStartupUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldInitializeSecurityWithoutInstantiatingRequestOnlyBeans() throws Exception {
        // Exercise the real XML bean definition without starting databases or CXF endpoints.
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        org.w3c.dom.Document document;
        try (var stream = new ClassPathResource("spring_ws.xml").getInputStream()) {
            document = factory.newDocumentBuilder().parse(stream);
        }
        var root = document.getDocumentElement();
        for (var child = root.getFirstChild(); child != null;) {
            var next = child.getNextSibling();
            if (!(child instanceof org.w3c.dom.Element element)
                    || !"authenticationInWSS4JInterceptor".equals(element.getAttribute("id"))) {
                root.removeChild(child);
            }
            child = next;
        }
        var transformers = TransformerFactory.newDefaultInstance();
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var xml = new StringWriter();
        transformers.newTransformer().transform(new DOMSource(document), new StreamResult(xml));

        var requested = new AtomicBoolean();
        var validator = mock(OscarUsernameTokenValidator.class);
        try (var context = new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            context.getBeanFactory().registerSingleton("oscarUsernameTokenValidator", validator);
            var requestOnly = new RootBeanDefinition(Object.class, () -> {
                requested.set(true);
                throw new IllegalStateException("No HTTP request exists during startup");
            });
            requestOnly.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
            context.registerBeanDefinition("requestOnlyAction", requestOnly);
            new XmlBeanDefinitionReader(context).loadBeanDefinitions(
                    new ByteArrayResource(xml.toString().getBytes(StandardCharsets.UTF_8)));
            context.refresh();

            var interceptor = context.getBean(AuthenticationInWSS4JInterceptor.class);
            assertThat(requested).isFalse();
            assertThat(ReflectionTestUtils.getField(interceptor, "oscarUsernameTokenValidator"))
                    .isSameAs(validator);
            assertThat(interceptor.getProperties()).containsEntry(
                    WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
            assertThat(interceptor.getProperties()).containsEntry(WSHandlerConstants.PW_CALLBACK_REF, interceptor);
        }
    }
}
