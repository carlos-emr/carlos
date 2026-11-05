package io.github.carlos_emr.carlos.webserv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Test;

import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.DemographicService;
import io.github.carlos_emr.carlos.webserv.rest.MeasurementService;
import io.github.carlos_emr.carlos.webserv.rest.to.MeasurementResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;

public class MeasurementServiceTest extends BaseRestServiceTest {

    protected static DemographicTo1 newRandomDemographic() {
        DemographicTo1 demo = new DemographicTo1();
        populate(demo);
        demo.setDemographicNo(null);
        return demo;
    }

    @Test
    public void testMeasurementService() {
        if (!enabled) {
            return;
        }

        // This service consumes and produces JSON, unlike the existing XML REST tests.
        mediaType = MediaType.APPLICATION_JSON;

        DemographicService demographicService = getResource(DemographicService.class);
        MeasurementService measurementService = getResource(MeasurementService.class);
        MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);

        DemographicTo1 demo = null;
        Integer demoId = null;

        try {
            // Create a dummy patient.
            demo = demographicService.createDemographicData(newRandomDemographic());
            assertNotNull(demo);
            assertNotNull(demo.getDemographicNo());

            demoId = demo.getDemographicNo();

            // Insert several measurements directly through the DAO.
            Date observedAt = new Date();

            Measurement bloodPressure = createMeasurement(
                    demoId, "BP", "120/80", observedAt);
            Measurement weight = createMeasurement(
                    demoId, "WT", "75", observedAt);
            Measurement height = createMeasurement(
                    demoId, "HT", "175", observedAt);

            measurementDao.persist(bloodPressure);
            measurementDao.persist(weight);
            measurementDao.persist(height);

            assertNotNull(bloodPressure.getId());
            assertNotNull(weight.getId());
            assertNotNull(height.getId());

            // Request the three measurement types through the REST service.
            ObjectNode request = JsonNodeFactory.instance.objectNode();
            request.putArray("types")
                    .add("BP")
                    .add("WT")
                    .add("HT")
                    .add("TEMP");

            MeasurementResponse response =
                    measurementService.getMeasurements(request, demoId);

            assertNotNull(response);

            Map<String, List<Measurement>> measurements =
                    response.getMeasurements();

            assertNotNull(measurements);

            // Verify that each requested type was returned.
            assertTrue(measurements.containsKey("BP"));
            assertTrue(measurements.containsKey("WT"));
            assertTrue(measurements.containsKey("HT"));

            assertEquals(1, measurements.get("BP").size());
            assertEquals(1, measurements.get("WT").size());
            assertEquals(1, measurements.get("HT").size());

            // Verify the measurement values and patient associations.
            assertMeasurement(measurements.get("BP").get(0), demoId, "BP", "120/80");
            assertMeasurement(measurements.get("WT").get(0), demoId, "WT", "75");
            assertMeasurement(measurements.get("HT").get(0), demoId, "HT", "175");

            // TEMP was requested but no TEMP measurement was inserted.
            assertFalse(measurements.containsKey("TEMP"));

            // Verify that an empty type list returns an empty response.
            ObjectNode emptyRequest = JsonNodeFactory.instance.objectNode();
            emptyRequest.putArray("types");

            MeasurementResponse emptyResponse =
                    measurementService.getMeasurements(emptyRequest, demoId);

            assertNotNull(emptyResponse);
            assertTrue(emptyResponse.getMeasurements().isEmpty());

        } finally {
            // Remove inserted measurements before deleting the patient.
            if (demoId != null) {
                try {
                    List<Measurement> patientMeasurements =
                            measurementDao.findByDemographicNo(demoId);

                    for (Measurement measurement : patientMeasurements) {
                        measurementDao.remove(measurement);
                    }
                } finally {
                    if (demo != null) {
                        demographicService.deleteDemographicData(demoId);
                    }
                }
            }
        }
    }

    private Measurement createMeasurement(
            Integer demoId, String type, String value, Date observedAt) {

        Measurement measurement = new Measurement();
        measurement.setDemographicId(demoId);
        measurement.setType(type);
        measurement.setDataField(value);
        measurement.setDateObserved(observedAt);
        measurement.setCreateDate(new Date());

        return measurement;
    }

    private void assertMeasurement(
            Measurement measurement,
            Integer expectedDemoId,
            String expectedType,
            String expectedValue) {

        assertNotNull(measurement);
        assertEquals(expectedDemoId, measurement.getDemographicId());
        assertEquals(expectedType, measurement.getType());
        assertEquals(expectedValue, measurement.getDataField());
    }
}
