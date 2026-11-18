/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.integration.mchcv;

import ca.ontario.health.hcv.HCValidation;
import ca.ontario.health.hcv.HcvResults;
import ca.ontario.health.hcv.Requests;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import java.util.ArrayList;
import java.util.List;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientLifeCycleListener;
import org.apache.cxf.endpoint.ClientLifeCycleManager;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("integration-ebs")
class OnlineHCValidatorLifecycleUnitTest {
    private static final String SYNTHETIC_FAULT = "Synthetic local HCV fault";

    @Test
    void shouldReleaseEachClient_whenOnlineValidationReturnsASoapFault() {
        CarlosProperties configuration = onlineProperties();
        Bus previousBus = BusFactory.getThreadDefaultBus(false);
        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);
        ClientLifeCycleManager lifecycle = bus.getExtension(ClientLifeCycleManager.class);
        List<Client> created = new ArrayList<>();
        List<Client> destroyed = new ArrayList<>();
        ClientLifeCycleListener listener = new ClientLifeCycleListener() {
            @Override
            public void clientCreated(Client client) {
                created.add(client);
                client.getOutInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.SETUP) {
                    @Override
                    public void handleMessage(Message message) {
                        throw new SoapFault(SYNTHETIC_FAULT, Soap11.getInstance().getSender());
                    }
                });
            }

            @Override
            public void clientDestroyed(Client client) {
                destroyed.add(client);
            }
        };
        lifecycle.registerListener(listener);

        try (var properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            HCValidator validator = HCValidationFactory.getHCValidator();
            int clientsBeforeValidation = created.size();

            for (int attempt = 0; attempt < 2; attempt++) {
                HCValidationResult result = validator.validate("0000000000", "ZZ");
                assertThat(result.getResponseCode()).isEqualTo(HCValidator.NOT_VALID_RESPONSE_CODE);
                assertThat(result.getEbsFault().getMessage()).isEqualTo(SYNTHETIC_FAULT);
                assertThat(destroyed).containsExactlyElementsOf(created);
            }

            assertThat(created).hasSize(2).doesNotHaveDuplicates();
            assertThat(clientsBeforeValidation).isZero();
        } finally {
            lifecycle.unRegisterListener(listener);
            created.stream().filter(client -> !destroyed.contains(client)).forEach(Client::destroy);
            BusFactory.setThreadDefaultBus(previousBus);
            bus.shutdown(true);
        }
    }

    @Test
    void shouldReleaseClient_andReturnVendorBatchResult() throws Exception {
        CarlosProperties configuration = onlineProperties();
        HCValidation vendor = mock(HCValidation.class);
        Client client = mock(Client.class);
        Requests requests = new Requests();
        HcvResults vendorResults = new HcvResults();
        when(vendor.validate(requests, "fr")).thenReturn(vendorResults);

        try (var properties = mockStatic(CarlosProperties.class);
             var builder = mockConstruction(EdtClientBuilder.class,
                     (mock, context) -> when(mock.build(HCValidation.class)).thenReturn(vendor));
             var proxy = mockStatic(ClientProxy.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(configuration);
            proxy.when(() -> ClientProxy.getClient(vendor)).thenReturn(client);

            assertThat(HCValidationFactory.getHCValidator().validate(requests, "fr")).isSameAs(vendorResults);
            verify(client).destroy();
        }
    }

    private static CarlosProperties onlineProperties() {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("hcv.type")).thenReturn("online");
        when(properties.getProperty("hcv.service.url")).thenReturn("http://127.0.0.1:1/synthetic-5907");
        when(properties.getProperty("hcv.service.id")).thenReturn("synthetic-5907");
        when(properties.getProperty("hcv.service.conformanceKey")).thenReturn("synthetic-5907");
        when(properties.getProperty("hcv.logging.skip")).thenReturn("true");
        return properties;
    }
}
