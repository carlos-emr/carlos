package io.github.carlos_emr.carlos.fax.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.junit.Test;

public class FaxProviderClientFactoryTest {

    @Test
    public void getClientDefaultsToMiddleware() throws Exception {
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Collections.singletonList(middlewareClient));

        FaxConfig faxConfig = new FaxConfig();
        assertEquals(FaxConfig.ProviderType.MIDDLEWARE, faxConfig.getProviderType());
        assertEquals(middlewareClient, factory.getClient(faxConfig));
    }

    @Test
    public void getClientSelectsConfiguredProviderType() throws Exception {
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClient srfaxClient = new TestClient(FaxConfig.ProviderType.SRFAX);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Arrays.asList(middlewareClient, srfaxClient));

        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setProviderType(FaxConfig.ProviderType.SRFAX);

        assertEquals(srfaxClient, factory.getClient(faxConfig));
    }


    @Test
    public void getClientThrowsForNullConfig() throws Exception {
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Collections.singletonList(middlewareClient));

        try {
            factory.getClient(null);
            fail("Expected FaxProviderException for null config");
        } catch (FaxProviderException expected) {
            assertEquals("Fax configuration is required to resolve provider client", expected.getMessage());
        }
    }

    @Test
    public void constructorThrowsForDuplicateProviderType() {
        FaxProviderClient first = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClient second = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);

        try {
            new FaxProviderClientFactory(Arrays.asList(first, second));
            fail("Expected IllegalStateException for duplicate provider type");
        } catch (IllegalStateException expected) {
            assertEquals("Duplicate FaxProviderClient beans configured for provider type: MIDDLEWARE", expected.getMessage());
        }
    }

    private static class TestClient implements FaxProviderClient {
        private final FaxConfig.ProviderType type;

        private TestClient(FaxConfig.ProviderType type) {
            this.type = type;
        }

        @Override
        public FaxConfig.ProviderType getProviderType() {
            return type;
        }

        @Override
        public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) {
            return faxJob;
        }

        @Override
        public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) {
            return Collections.emptyList();
        }

        @Override
        public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) {
            return fax;
        }

        @Override
        public void deleteFax(FaxConfig faxConfig, FaxJob fax) {
        }

        @Override
        public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) {
            return faxJob;
        }
    }
}
