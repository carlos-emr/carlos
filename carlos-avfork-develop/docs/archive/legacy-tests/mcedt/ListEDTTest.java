package io.github.carlos_emr.carlos.integration.mcedt;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.math.BigInteger;

import io.github.carlos_emr.carlos.integration.mcedt.DelegateFactory;
import org.junit.Test;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceStatus;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class ListEDTTest extends EDTBaseTest {
    /*
     * 3. LIST TESTS
     */

    @Test
    public void testList_All_Claim_Files_Uploaded_SUCCESS() {
        System.out.println("--------------- testList_All_Valid_Claim_Files_Uploaded_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());

        printDetailList(detailList);
    }

    @Test
    public void testList_All_Stale_Dated_Claim_Files_Uploaded_SUCCESS() {
        System.out.println("--------------- testList_All_Stale_Dated_Claim_Files_Uploaded_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE.getType(), ResourceStatus.UPLOADED, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());

        printDetailList(detailList);
    }

    @Test
    public void testList_All_OBEC_Files_Uploaded_SUCCESS() {
        System.out.println("--------------- testList_All_OBEC_Files_Uploaded_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_OBEC_INBOUND_FILE.getType(), ResourceStatus.SUBMITTED, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());

        printDetailList(detailList);
    }

    @Test
    public void testList_All_Reports_Downloadable_SUCCESS() {
        System.out.println("--------------- testList_All_Reports_Downloadable_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, ResourceStatus.DOWNLOADABLE, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());

        printDetailList(detailList);
    }

    @Test
    public void testList_Claim_And_Stale_Dated_Claim_And_OBEC_Files_Deleted_SUCCESS() {
        System.out.println("--------------- testList_Claim_And_Stale_Dated_Claim_And_OBEC_Files_Deleted_SUCCESS ---------------\n" + "Actual Results:");
        Detail claimFilesDetailList = null;
        Detail staleDatedClaimFilesDetailList = null;
        Detail obecFilesDetailList = null;
        try {
            claimFilesDetailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.DELETED, new BigInteger("1"));
            staleDatedClaimFilesDetailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), ResourceStatus.DELETED, new BigInteger("1"));
            obecFilesDetailList = edtDelegate.list(ResourceType.UPLOAD_OBEC_INBOUND_FILE.getType(), ResourceStatus.DELETED, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNull(claimFilesDetailList);
        assertNull(staleDatedClaimFilesDetailList);
        assertNull(obecFilesDetailList);

        printDetailList(claimFilesDetailList);
        printDetailList(staleDatedClaimFilesDetailList);
        printDetailList(obecFilesDetailList);
    }

    @Test
    public void testList_All_Resources_SUCCESS() {
        System.out.println("--------------- testList_All_Resources_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, null, null);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());

        printDetailList(detailList);
    }

    @Test
    public void testList_All_Resources_With_Invalid_Resource_Type_FAILED_EEDTS0003() {
        System.out.println("--------------- testList_All_Resources_With_Invalid_Resource_Type_FAILED_EEDTS0003 ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list("99", ResourceStatus.UPLOADED, null);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0003", e.getFaultInfo().getCode());
            return;
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());
        assertEquals("EEDTS0003", detailList.getData().get(0).getResult().getCode());

        printDetailList(detailList);
    }

    @Test
    public void testList_Downloadable_Resources_With_Invalid_MOHID_FAILED_EEDTS0061() {
        System.out.println("--------------- testList_Downloadable_Resources_With_Invalid_MOHID_FAILED_EEDTS0061 ---------------\n" + "Actual Results:");
        edtDelegate = DelegateFactory.getEDTDelegateInstance("001CF");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, ResourceStatus.DOWNLOADABLE, null);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0061", e.getFaultInfo().getCode());
            return;
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());
        assertEquals("EEDTS0061", detailList.getData().get(0).getResult().getCode());

        printDetailList(detailList);
    }

    /*
     * This test only works when there are more than 50 resources uploaded.
     */
    @Test
    public void testTotal_Resources_On_First_Page_50_SUCCESS() {
        System.out.println("--------------- testTotal_Resources_On_First_Page_50_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, null, new BigInteger("1"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());
        assertEquals(50, detailList.getData().size());

        printDetailList(detailList);
    }

    /*
     * This test only works when there are more than 100 resources uploaded.
     */
    @Test
    public void testTotal_Resources_On_Second_Page_50_SUCCESS() {
        System.out.println("--------------- testTotal_Resources_On_Second_Page_50_SUCCESS ---------------\n" + "Actual Results:");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, null, new BigInteger("2"));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());
        assertEquals(50, detailList.getData().size());

        printDetailList(detailList);
    }

    @Test
    public void testList_Resources_With_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testList_Resources_With_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");
        edtDelegate = DelegateFactory.getEDTDelegateInstance("999999");
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, ResourceStatus.UPLOADED, null);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
            return;
        }
        assertNotNull(detailList);
        assertFalse(detailList.getData().isEmpty());
        assertEquals("EEDTS0012", detailList.getData().get(0).getResult().getCode());

        printDetailList(detailList);
    }
}
