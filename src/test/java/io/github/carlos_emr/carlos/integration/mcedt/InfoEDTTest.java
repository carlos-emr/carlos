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
import ca.ontario.health.edt.ResourceStatus;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class InfoEDTTest extends EDTBaseTest {
    /*
     * 8. INFO TESTS
     */

    @Test
    public void testInfo_Of_Submitted_Claim_File_SUCCESS() {
        System.out.println("--------------- testInfo_Of_Submitted_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds
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

        // Get Info of files using resourceIds
        Detail detail = null;
        try {
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detail);
        printDetailList(detail);
    }

    @Test
    public void testInfo_Of_Batch_Edit_File_SUCCESS() {
        System.out.println("--------------- testInfo_Of_Batch_Edit_File_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds
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

        // Get Info of files using resourceIds
        Detail detail = null;
        try {
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detail);
        printDetailList(detail);
    }

    @Test
    public void testInfo_Of_Claim_File_AND_OBEC_File_And_Batch_Edit_Report_SUCCESS() {
        System.out.println("--------------- testInfo_Of_Claim_File_AND_OBEC_File_And_Batch_Edit_Report_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail claimFileDetailList = null;
        Detail obecFileDetailList = null;
        Detail batchEditReportDetailList = null;
        try {
            claimFileDetailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.SUBMITTED, BigInteger.valueOf(1));
            obecFileDetailList = edtDelegate.list(ResourceType.UPLOAD_OBEC_INBOUND_FILE.getType(), ResourceStatus.SUBMITTED, BigInteger.valueOf(1));
            batchEditReportDetailList = edtDelegate.list(ResourceType.DOWNLOAD_BATCH_EDIT.getType(), ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(claimFileDetailList);
        assertTrue(claimFileDetailList.getData().size() > 0);
        resourceIds.add(claimFileDetailList.getData().get(0).getResourceID());

        assertNotNull(obecFileDetailList);
        assertTrue(obecFileDetailList.getData().size() > 0);
        resourceIds.add(obecFileDetailList.getData().get(0).getResourceID());

        assertNotNull(batchEditReportDetailList);
        assertTrue(batchEditReportDetailList.getData().size() > 0);
        resourceIds.add(batchEditReportDetailList.getData().get(0).getResourceID());

        // Get Info of files using resourceIds
        Detail detail = null;
        try {
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detail);
        printDetailList(detail);
    }

    /*
     * Oscar dependency is throwing an error: javax.xml.webserv.soap.SOAPFaultException: javax.xml.webserv.soap.SOAPFaultException: https://204.41.14.200:1443/EDTService/EDTService: cvc-particle 3.1: in element {http://edt.health.ontario.ca/}info of type {http://edt.health.ontario.ca/}info,
     * found </ns5:info> (in namespace http://edt.health.ontario.ca/), but next item should be resourceIDs
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testInfo_Without_Resource_Id_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testInfo_Without_Resource_Id_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();

        // Get Info of files using resourceIds
        try {
            edtDelegate.info(resourceIds);
        } catch (Faultexception | SOAPFaultException e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
        }
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testInfo_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testInfo_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");

        // Getting resourceIds
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_OBEC_INBOUND_FILE.getType(), ResourceStatus.SUBMITTED, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Get Info of files using resourceIds
        Detail detail = null;
        edtDelegate = newDelegate("999999");
        try {
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(detail);
        printDetailList(detail);
        assertEquals("EEDTS0012", detail.getData().get(0).getResult().getCode());
    }

    @Test
    public void testInfo_Invalid_Resource_ID_FAILED_EEDTS0056() {
        System.out.println("--------------- testInfo_Invalid_MOHID_FAILED_EEDTS0056 ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();
        resourceIds.add(new BigInteger("99988888"));

        // Get Info of files using resourceIds
        Detail detail = null;
        try {
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0056", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(detail);
        printDetailList(detail);
        assertEquals("EEDTS0056", detail.getData().get(0).getResult().getCode());
    }

    /*
     * $$: The Info method expects a BigInteger for the resourceID parameter, so I am getting an error: NumberFormat for input string: "$$".
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testInfo_Invalid_Resource_ID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testInfo_Invalid_MOHID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();

        // Get Info of files using resourceIds
        Detail detail = null;
        try {
            resourceIds.add(new BigInteger("$$"));
            detail = edtDelegate.info(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("Rejected By Policy", e.getFaultInfo().getCode());
            return;
        } catch (NumberFormatException e) {
            return;
        }

        assertNotNull(detail);
        printDetailList(detail);
        assertEquals("Rejected By Policy", detail.getData().get(0).getResult().getCode());
    }

    /*
     * Oscar dependency is throwing an error: javax.xml.webserv.soap.SOAPFaultException: https://204.41.14.200:1443/EDTService/EDTService: cvc-particle 3.1: in element {http://edt.health.ontario.ca/}info of type {http://edt.health.ontario.ca/}info,
     * found </ns5:info> (in namespace http://edt.health.ontario.ca/), but next item should be resourceIDs
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testInfo_With_Blank_Resource_ID_And_Blank_MOHID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testInfo_With_Blank_Resource_ID_And_Blank_MOHID_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();
        resourceIds.add(null);

        // Get Info of files using resourceIds
        edtDelegate = newDelegate("");
        try {
            edtDelegate.info(resourceIds);
        } catch (Faultexception | SOAPFaultException e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
        }
    }
}
