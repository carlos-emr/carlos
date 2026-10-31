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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.webserv.AllergyWs;
import io.github.carlos_emr.carlos.webserv.MeasurementWs;
import io.github.carlos_emr.carlos.webserv.rest.AllergyService;
import io.github.carlos_emr.carlos.webserv.rest.LabService;
import io.github.carlos_emr.carlos.webserv.rest.MeasurementService;
import io.github.carlos_emr.carlos.webserv.rest.RxWebService;
import java.util.HashSet;
import java.util.List;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class CdsEndpointProjectionInjectionUnitTest {
    private static final List<Class<?>> ENDPOINTS=List.of(AllergyService.class,LabService.class,MeasurementService.class,
            RxWebService.class,AllergyWs.class,MeasurementWs.class);
    @Test void shouldWireRequiredGuardsThroughActualSpringBeanLifecycle_whilePreservingPublicNoargEndpoints() throws Exception {
        var guard=mock(CdsLegacyProjectionService.class);
        try(var context=new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            context.registerBean("cdsLegacyProjectionService",CdsLegacyProjectionService.class,()->guard);
            context.registerBean("context",jakarta.xml.ws.WebServiceContext.class,()->mock(jakarta.xml.ws.WebServiceContext.class));
            var registered=new HashSet<Class<?>>();
            for(Class<?> endpoint:ENDPOINTS) {
                assertThat(endpoint.getConstructor().newInstance()).isInstanceOf(endpoint);
                for(var field:endpoint.getDeclaredFields()) if(field.isAnnotationPresent(Autowired.class)&&registered.add(field.getType())) {
                    registerMock(context,field.getType());
                }
                var definition=BeanDefinitionBuilder.genericBeanDefinition(endpoint).getBeanDefinition();
                definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
                context.registerBeanDefinition(endpoint.getSimpleName(),definition);
            }
            context.refresh();
            for(Class<?> endpoint:ENDPOINTS) {
                Object bean=context.getBean(endpoint.getSimpleName());
                String field=endpoint==AllergyService.class||endpoint==RxWebService.class?"legacyProjection":"projections";
                assertThat(ReflectionTestUtils.getField(bean,field)).isSameAs(guard);
            }
        }
    }
    @Test void shouldExcludeInfrastructureSetters_fromActualSoapOperationModels() {
        for(Class<?> endpoint:List.of(AllergyWs.class,MeasurementWs.class)) {
            var bus=new org.apache.cxf.bus.extension.ExtensionManagerBus();
            try {
                var factory=new JaxWsServiceFactoryBean();factory.setBus(bus);factory.setServiceClass(endpoint);
                var service=factory.create();
                var operations=service.getServiceInfos().getFirst().getInterface().getOperations().stream()
                        .map(operation->operation.getName().getLocalPart()).toList();
                assertThat(operations).isNotEmpty().doesNotContain("setCdsLegacyProjectionService");
            } finally { bus.shutdown(true); }
        }
    }
    private static <T> void registerMock(GenericApplicationContext context,Class<T> type) {
        context.registerBean("fixture"+type.getSimpleName(),type,()->mock(type));
    }
}
