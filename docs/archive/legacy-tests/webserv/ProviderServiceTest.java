
package io.github.carlos_emr.carlos.webserv.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;

public class ProviderServiceTest extends BaseRestServiceTest {

    private ProviderService service;

    @Before
    public void setUp() {
        mediaType = MediaType.APPLICATION_JSON;

        if (enabled) {
            service = getResource(ProviderService.class);
        }
    }

    /**
     * Tests retrieving all active providers through the legacy endpoint.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testGetProviders() {
        if (!enabled) {
            return;
        }

        OscarSearchResponse<ProviderTransfer> response = service.getProviders();

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertEquals(response.getContent().size(), response.getTotal());
    }

    /**
     * Tests retrieving all active providers as JSON.
     */
    @Test
    public void testGetProvidersAsJSON() {
        if (!enabled) {
            return;
        }

        AbstractSearchResponse<ProviderTo1> response =
                service.getProvidersAsJSON();

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertEquals(response.getContent().size(), response.getTotal());

        for (ProviderTo1 provider : response.getContent()) {
            assertNotNull(provider);
            assertNotNull(provider.getProviderNo());
        }
    }

    /**
     * Tests retrieving an individual provider by ID.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testGetProvider() {
        if (!enabled) {
            return;
        }

        String providerNo = getFirstProviderNo();

        ProviderTransfer response = service.getProvider(providerNo);

        assertNotNull(response);
        assertEquals(providerNo, response.getProviderNo());
    }

    /**
     * Tests retrieving an individual provider as JSON by ID.
     */
    @Test
    public void testGetProviderAsJSON() throws Exception {
        if (!enabled) {
            return;
        }

        String providerNo = getFirstProviderNo();

        String response = service.getProviderAsJSON(providerNo);

        assertNotNull(response);

        JsonNode json = new ObjectMapper().readTree(response);

        assertNotNull(json);
        assertEquals(providerNo, json.get("providerNo").asText());
    }

    /**
     * Gets a provider ID from the active provider list so the individual
     * provider tests do not depend on a hardcoded provider.
     */
    private String getFirstProviderNo() {
        AbstractSearchResponse<ProviderTo1> response =
                service.getProvidersAsJSON();

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertTrue("At least one active provider is required for this test",
                !response.getContent().isEmpty());

        return response.getContent().get(0).getProviderNo();
    }
}
