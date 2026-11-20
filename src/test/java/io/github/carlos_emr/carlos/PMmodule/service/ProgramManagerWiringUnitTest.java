/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.PMmodule.service;

import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Admission;
import java.util.List;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.FileSystemResource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class ProgramManagerWiringUnitTest {
    @Test
    void shouldWireTheScannedAdmissionDao_forTeamClientLookup() throws Exception {
        var resource = new FileSystemResource("src/main/resources/applicationContext.xml");
        var parser = DocumentBuilderFactory.newDefaultInstance();
        parser.setNamespaceAware(true);
        parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try (var input = resource.getInputStream(); var context = new GenericApplicationContext()) {
            var document = parser.newDocumentBuilder().parse(input);
            Element root = document.getDocumentElement();
            for (Node child = root.getFirstChild(); child != null; ) {
                Node next = child.getNextSibling();
                if (!(child instanceof Element element) || !"programManager".equals(element.getAttribute("id"))) {
                    root.removeChild(child);
                }
                child = next;
            }
            new XmlBeanDefinitionReader(context).registerBeanDefinitions(document, resource);

            var admission = new Admission();
            var dao = mock(AdmissionDao.class);
            when(dao.getAdmissionsInTeam(42, 7)).thenReturn(List.of(admission));
            var definition = new AnnotatedGenericBeanDefinition(AdmissionDaoImpl.class);
            String daoName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(definition, context);
            context.getBeanFactory().registerSingleton(daoName, dao);

            var settings = new Properties();
            settings.setProperty("pmm.refer.temporaryAdmission.enabled", "false");
            var placeholders = new PropertySourcesPlaceholderConfigurer();
            placeholders.setProperties(settings);
            context.addBeanFactoryPostProcessor(placeholders);
            context.refresh();

            var manager = context.getBean("programManager", ProgramManager.class);
            assertThat(manager.getAllClientsInTeam(42, 7)).singleElement().isSameAs(admission);
        }
    }
}
