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
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CARLOS JAX-RS providers registered on the deployed REST servers must start under the
 * production auto-proxy setup: {@code <aop:aspectj-autoproxy proxy-target-class="true"/>} with
 * {@link WebServiceLoggingAdvice}, whose pointcut covers every public method of a class directly
 * in {@code io.github.carlos_emr.carlos.webserv.rest}. A provider in that package is proxied with
 * CGLIB: a {@code final} one stops the whole web application context from starting, and any other
 * would have its fault-path calls audited as REST service calls.
 */
@Tag("unit")
@Tag("rest")
class RestProviderLoggingAdviceScopeUnitTest {

    private static final String JAXRS_NAMESPACE = "http://cxf.apache.org/jaxrs";

    @ParameterizedTest
    @ValueSource(strings = {"spring_ws.xml", "applicationContextREST.xml"})
    void shouldStartProvidersUnproxied_withRestLoggingAdviceActive(String resourceName) throws Exception {
        List<Class<?>> providers = carlosProviders(resourceName);
        assertThat(providers).as(resourceName + " registers CARLOS JAX-RS providers").isNotEmpty();
        try (var context = new GenericApplicationContext()) {
            // What <aop:aspectj-autoproxy proxy-target-class="true"/> registers in applicationContext.xml.
            AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(context);
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(context);
            context.registerBean(WebServiceLoggingAdvice.class);
            providers.forEach(provider -> context.registerBean(provider.getName(), provider));

            context.refresh();

            for (Class<?> provider : providers) {
                assertThat(AopUtils.isAopProxy(context.getBean(provider.getName())))
                        .as(provider.getName() + " must not be advised by WebServiceLoggingAdvice")
                        .isFalse();
            }
        }
    }

    /** The classes of the inline CARLOS provider beans in the file's jaxrs:server definitions. */
    private static List<Class<?>> carlosProviders(String resourceName) throws Exception {
        var parser = XmlUtils.createSecureDocumentBuilderFactory();
        parser.setNamespaceAware(true);
        Document document;
        try (InputStream stream = new ClassPathResource(resourceName).getInputStream()) {
            document = parser.newDocumentBuilder().parse(stream);
        }
        List<Class<?>> providers = new ArrayList<>();
        NodeList providerLists = document.getElementsByTagNameNS(JAXRS_NAMESPACE, "providers");
        for (int i = 0; i < providerLists.getLength(); i++) {
            for (Node child = providerLists.item(i).getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element bean && "bean".equals(bean.getLocalName())
                        && bean.getAttribute("class").startsWith("io.github.carlos_emr.")) {
                    Class<?> provider = Class.forName(bean.getAttribute("class"));
                    if (!providers.contains(provider)) {
                        providers.add(provider);
                    }
                }
            }
        }
        return providers;
    }
}
