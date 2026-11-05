
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.webserv.rest.to.DrugLookupResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;

import jakarta.ws.rs.core.MediaType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RxLookupServiceTest extends BaseRestServiceTest {

    @Test
    public void testSearchAndDetails() {
        if (!enabled) {
            return;
        }

        mediaType = MediaType.APPLICATION_JSON;
        RxLookupService service = getResource(RxLookupService.class);

        // Search for a commonly available medication.
        DrugLookupResponse searchResponse = service.search("acetaminophen");

        assertNotNull(searchResponse);
        assertTrue(
                "Drug search should succeed: " + searchResponse.getMessage(),
                searchResponse.isSuccess()
        );

        List<DrugSearchTo1> drugs = searchResponse.getDrugs();

        assertNotNull(drugs);
        assertFalse("Search should return at least one drug", drugs.isEmpty());

        // Find a result with a usable ID.
        DrugSearchTo1 selectedDrug = drugs.stream()
                .filter(drug -> drug.getId() != null)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Search results should contain a drug ID"));

        assertNotNull(selectedDrug.getName());

        // Retrieve details for the selected drug.
        DrugLookupResponse detailsResponse =
                service.details(selectedDrug.getId().toString());

        assertNotNull(detailsResponse);
        assertTrue(
                "Drug details lookup should succeed: " + detailsResponse.getMessage(),
                detailsResponse.isSuccess()
        );

        assertNotNull(detailsResponse.getDrugs());
        assertEquals(1, detailsResponse.getDrugs().size());

        DrugSearchTo1 details = detailsResponse.getDrugs().get(0);

        assertEquals(selectedDrug.getId(), details.getId());
        assertNotNull(details.getName());
        assertNotNull(details.getComponents());
        assertFalse(details.getComponents().isEmpty());
    }

    @Test
    public void testParseInstructions() {
        if (!enabled) {
            return;
        }

        mediaType = MediaType.APPLICATION_JSON;
        RxLookupService service = getResource(RxLookupService.class);

        String instructions =
                "Take 1 tablet by mouth twice daily for 7 days";

        DrugResponse response = service.parseInstructions(instructions);

        assertNotNull(response);
        assertNotNull(response.getDrug());

        DrugTo1 drug = response.getDrug();

        // Verify the parsed dosage.
        assertEquals(1.0f, drug.getTakeMin(), 0.001f);
        assertEquals(1.0f, drug.getTakeMax(), 0.001f);

        // Verify frequency and route.
        assertEquals("BID", drug.getFrequency());
        assertEquals("PO", drug.getRoute());

        // Verify duration.
        assertEquals(Integer.valueOf(7), drug.getDuration());
        assertEquals("D", drug.getDurationUnit());

        // Verify PRN status.
        assertEquals(Boolean.FALSE, drug.isPrn());
    }
}
