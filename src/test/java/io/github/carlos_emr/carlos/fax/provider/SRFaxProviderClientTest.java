package io.github.carlos_emr.carlos.fax.provider;

import static org.junit.Assert.assertEquals;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.junit.Test;

/**
 * Unit tests for SRFax status mapping semantics.
 */
public class SRFaxProviderClientTest {

    private final SRFaxProviderClient client = new SRFaxProviderClient();

    @Test
    public void mapStatusComplete() {
        assertEquals(FaxJob.STATUS.COMPLETE, client.mapStatus("Successfully Sent"));
    }

    @Test
    public void mapStatusSent() {
        assertEquals(FaxJob.STATUS.SENT, client.mapStatus("In queue"));
    }

    @Test
    public void mapStatusCancelled() {
        assertEquals(FaxJob.STATUS.CANCELLED, client.mapStatus("Cancelled by user"));
    }

    @Test
    public void mapStatusError() {
        assertEquals(FaxJob.STATUS.ERROR, client.mapStatus("No answer"));
    }

    @Test
    public void mapStatusUnknown() {
        assertEquals(FaxJob.STATUS.UNKNOWN, client.mapStatus("Other state"));
        assertEquals(FaxJob.STATUS.UNKNOWN, client.mapStatus(null));
    }
}
