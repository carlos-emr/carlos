package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Ignore;
import org.junit.Test;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.ResourceStatus;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class DeleteEDTTest extends EDTBaseTest {
    /*
     * 5. DELETE TESTS
     */

    @Test
    public void testDelete_Uploaded_Claim_File_SUCCESS() {
        System.out.println("--------------- testDelete_Uploaded_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds to delete the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, BigInteger.valueOf(1));
            resourceIds.add(detailList.getData().get(0).getResourceID());
            detailList = edtDelegate.list(ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, BigInteger.valueOf(1));
            resourceIds.add(detailList.getData().get(0).getResourceID());
            detailList = edtDelegate.list(ResourceType.UPLOAD_OBEC_INBOUND_FILE.getType(), ResourceStatus.UPLOADED, BigInteger.valueOf(1));
            resourceIds.add(detailList.getData().get(0).getResourceID());
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testDelete_Submited_Claim_File_FAILED_EEDTS0057() {
        System.out.println("--------------- testDelete_Submited_Claim_File_FAILED_EEDTS0057 ---------------\n" + "Actual Results:");

        // Getting resourceIds to delete the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.SUBMITTED, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0057", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0057", resourceResult);
    }

    @Test
    public void testDelete_Downloadable_Batch_Edit_File_FAILED_EEDTS0057() {
        System.out.println("--------------- testDelete_Downloadable_Batch_Edit_File_FAILED_EEDTS0057 ---------------\n" + "Actual Results:");

        // Getting resourceIds to delete the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.DOWNLOAD_BATCH_EDIT.getType(), ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0057", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0057", resourceResult);
    }

    @Test
    public void testDelete_Uploaded_Claim_File_With_Different_MOHID_FAILED_EEDTS0058() {
        System.out.println("--------------- testDelete_Uploaded_Claim_File_With_Different_MOHID_FAILED_EEDTS0058 ---------------\n" + "Actual Results:");

        // Getting resourceIds to delete the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        edtDelegate = newDelegate("001CF");
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0058", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0058", resourceResult);
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testDelete_Uploaded_Claim_File_With_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testDelete_Uploaded_Claim_File_With_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");

        // Getting resourceIds to delete the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        edtDelegate = newDelegate("999999");
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0012", resourceResult);
    }

    @Test
    public void testDelete_Uploaded_Claim_File_With_Invalid_Resource_Id_FAILED_EEDTS0056() {
        System.out.println("--------------- testDelete_Uploaded_Claim_File_With_Invalid_Resource_Id_FAILED_EEDTS0056 ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();

        resourceIds.add(new BigInteger("99988888"));

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0056", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0056", resourceResult);
    }

    /*
     * $$: The Delete method expects a BigInteger for the resourceID parameter, so I am getting an error: NumberFormat for input string: "$$".
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testDelete_Uploaded_Claim_File_With_Invalid_Resource_Id_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testDelete_Uploaded_Claim_File_With_Invalid_Resource_Id_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        try {
            resourceIds.add(new BigInteger("$$"));
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("Rejected By Policy", e.getFaultInfo().getCode());
            return;
        } catch (NumberFormatException e) {
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("Rejected By Policy", resourceResult);
    }

    /*
     * Oscar dependency is throwing an error: javax.xml.webserv.soap.SOAPFaultException: https://204.41.14.200:1443/EDTService/EDTService: cvc-particle 3.1: in element {http://edt.health.ontario.ca/}delete of type {http://edt.health.ontario.ca/}delete,
     * found </ns5:delete> (in namespace http://edt.health.ontario.ca/), but next item should be resourceIDs
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testDelete_With_Blank_Resource_ID_And_Blank_MOHID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testDelete_With_Blank_Resource_ID_And_Blank_MOHID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();

        resourceIds.add(null);

        // Delete files using resourceIds
        ResourceResult resourceResult = null;
        edtDelegate = newDelegate("");
        try {
            resourceResult = edtDelegate.delete(resourceIds);
        } catch (Faultexception | SOAPFaultException e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
    }
}
