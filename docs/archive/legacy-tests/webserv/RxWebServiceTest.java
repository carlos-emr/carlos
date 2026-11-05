
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.webserv.DemographicService;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.PrescriptionResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PrescriptionTo1;

import jakarta.ws.rs.core.MediaType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class RxWebServiceTest extends BaseRestServiceTest {

    @Test
    public void testMedicationLifecycle() {
        if (!enabled) {
            return;
        }

        mediaType = MediaType.APPLICATION_JSON;

        DemographicService demographicService =
                getResource(DemographicService.class);
        RxWebService rxService = getResource(RxWebService.class);

        DemographicTo1 demographic = new DemographicTo1();
        populate(demographic);
        demographic.setDemographicNo(null);

        demographic = demographicService.createDemographicData(demographic);
        Integer demographicNo = demographic.getDemographicNo();

        assertNotNull("Patient should have been created", demographicNo);

        try {
            // Create four medications.
            DrugTo1 drug1 = createDrug(demographicNo, "Test Drug A", true);
            DrugTo1 drug2 = createDrug(demographicNo, "Test Drug B", false);
            DrugTo1 drug3 = createDrug(demographicNo, "Test Drug C", true);
            DrugTo1 drug4 = createDrug(demographicNo, "Test Drug D", false);

            DrugResponse response1 = rxService.addDrug(drug1, demographicNo);
            DrugResponse response2 = rxService.addDrug(drug2, demographicNo);
            DrugResponse response3 = rxService.addDrug(drug3, demographicNo);
            DrugResponse response4 = rxService.addDrug(drug4, demographicNo);

            assertTrue("First drug should be added: " + response1.getMessage(),
                    response1.isSuccess());
            assertTrue("Second drug should be added: " + response2.getMessage(),
                    response2.isSuccess());
            assertTrue("Third drug should be added: " + response3.getMessage(),
                    response3.isSuccess());
            assertTrue("Fourth drug should be added: " + response4.getMessage(),
                    response4.isSuccess());

            Integer drugId1 = response1.getDrug().getDrugId();
            Integer drugId2 = response2.getDrug().getDrugId();
            Integer drugId3 = response3.getDrug().getDrugId();
            Integer drugId4 = response4.getDrug().getDrugId();

            assertNotNull(drugId1);
            assertNotNull(drugId2);
            assertNotNull(drugId3);
            assertNotNull(drugId4);

            // Discontinue two medications.
            assertNotNull(rxService.discontinueDrug(
                    drugId2, "Integration test discontinuation", demographicNo));

            assertNotNull(rxService.discontinueDrug(
                    drugId4, "Integration test discontinuation", demographicNo));

            // Retrieve all medications.
            DrugSearchResponse allDrugs =
                    rxService.getAllDrugs(demographicNo);

            assertNotNull(allDrugs);
            assertNotNull(allDrugs.getContent());
            assertTrue(allDrugs.getContent().size() >= 4);

            // Retrieve current medications.
            DrugSearchResponse currentDrugs =
                    rxService.getCurrentDrugs(demographicNo);

            assertNotNull(currentDrugs);
            assertNotNull(currentDrugs.getContent());

            // Retrieve long-term medications.
            DrugSearchResponse longTermDrugs =
                    rxService.getLongtermDrugs(demographicNo);

            assertNotNull(longTermDrugs);
            assertNotNull(longTermDrugs.getContent());

            // Retrieve archived medications.
            DrugSearchResponse archivedDrugs =
                    rxService.getArchivedDrugs(demographicNo);

            assertNotNull(archivedDrugs);
            assertNotNull(archivedDrugs.getContent());

            // Verify that discontinued medications appear in the archive.
            assertTrue(
                    archivedDrugs.getContent().stream()
                            .anyMatch(d -> drugId2.equals(d.getDrugId()))
            );

            assertTrue(
                    archivedDrugs.getContent().stream()
                            .anyMatch(d -> drugId4.equals(d.getDrugId()))
            );

            // Verify that the long-term medication appears in the long-term list.
            assertTrue(
                    longTermDrugs.getContent().stream()
                            .anyMatch(d -> drugId1.equals(d.getDrugId()))
            );

            // Retrieve the history of a medication.
            DrugSearchResponse history =
                    rxService.history(drugId2, demographicNo);

            assertNotNull(history);
            assertNotNull(history.getContent());

            // Prescribe a new medication.
            DrugTo1 prescriptionDrug =
                    createDrug(demographicNo, "Test Prescription Drug", false);

            List<DrugTo1> prescriptionDrugs = new ArrayList<>();
            prescriptionDrugs.add(prescriptionDrug);

            PrescriptionResponse prescriptionResponse =
                    rxService.prescribe(prescriptionDrugs, demographicNo);

            assertNotNull(prescriptionResponse);
            assertTrue(
                    "Prescription should succeed: "
                            + prescriptionResponse.getMessage(),
                    prescriptionResponse.isSuccess()
            );

            assertNotNull(prescriptionResponse.getPrescription());

            // Retrieve prescriptions.
            List<PrescriptionTo1> prescriptions =
                    rxService.getPrescriptions(demographicNo);

            assertNotNull(prescriptions);
            assertFalse("Patient should have at least one prescription",
                    prescriptions.isEmpty());

        } finally {
            demographicService.deleteDemographicData(demographicNo);
        }
    }

    private DrugTo1 createDrug(
            Integer demographicNo,
            String name,
            boolean longTerm) {

        DrugTo1 drug = new DrugTo1();

        drug.setDrugId(null);
        drug.setDemographicNo(demographicNo);
        drug.setBrandName(name);
        drug.setGenericName(name);
        drug.setStrength(10.0f);
        drug.setStrengthUnit("mg");

        drug.setTakeMin(1.0f);
        drug.setTakeMax(1.0f);

        Date now = new Date();
        drug.setRxDate(now);
        drug.setWrittenDate(now);

        drug.setFrequency("BID");
        drug.setDuration(30);
        drug.setDurationUnit("D");
        drug.setRoute("PO");
        drug.setForm("Tablet");
        drug.setMethod("Oral");

        drug.setQuantity(60);
        drug.setRepeats(0);
        drug.setInstructions("Take one tablet twice daily");

        drug.setPrn(false);
        drug.setArchived(false);
        drug.setLongTerm(longTerm);
        drug.setNoSubstitutions(false);

        return drug;
    }
}
