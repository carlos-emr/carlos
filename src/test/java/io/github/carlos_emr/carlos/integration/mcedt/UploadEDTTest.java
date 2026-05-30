package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.integration.mcedt.DelegateFactory;
import org.junit.Test;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.UploadData;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class UploadEDTTest extends EDTBaseTest {
    /*
     * 1. UPLOAD TESTS
     */

    @Test
    public void testUpload_Valid_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_OBEC_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Vendor_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Vendor_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.VENDOR_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Vendor_Stale_Dated_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Vendor_Stale_Dated_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.VENDOR_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Vendor_OBEC_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Vendor_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.VENDOR_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Claim_File_And_Stale_Dated_Claim_File_And_OBEC_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Valid_Claim_File_And_Stale_Dated_Claim_File_And_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));
        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Five_Valid_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Five_Valid_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));
        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Five_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Five_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));
        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Five_Valid_OBEC_File_SUCCESS_IEDTS0001() throws Exception {
        System.out.println("--------------- testUpload_Five_Valid_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));
        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    /*
     * OSCAR dependency seems to not allow 6 files to be sent at all.
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testUpload_Six_Valid_Claim_File_FAILED_Rejected() {
        System.out.println("--------------- testUpload_Six_Valid_Claim_File_FAILED_Rejected ---------------\n");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        if (uploads.size() > 5) { return; }

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            printResourceResult(resourceResult);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testUpload_Claim_File_With_Invalid_Header_FAILED_ECLAM0002() throws Exception {
        System.out.println("--------------- testUpload_Claim_File_With_Invalid_Header_FAILED_ECLAM0002 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MAL_FORMED_HEADER_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0002", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Claim_File_With_Invalid_Length_FAILED_ECLAM0008() throws Exception {
        System.out.println("--------------- testUpload_Claim_File_With_Invalid_Length_FAILED_ECLAM0008 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.LESS_THAN_79_BYTES_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0008", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Stale_Dated_Claim_File_With_Invalid_Header_FAILED_ECLAM0002() throws Exception {
        System.out.println("--------------- testUpload_Stale_Dated_Claim_File_With_Invalid_Header_FAILED_ECLAM0002 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MAL_FORMED_HEADER_MCEDT_STALE_DATED_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0002", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Stale_Dated_Claim_File_With_Invalid_Length_FAILED_ECLAM0008() throws Exception {
        System.out.println("--------------- testUpload_Stale_Dated_Claim_File_With_Invalid_Length_FAILED_ECLAM0008 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.LESS_THAN_79_BYTES_MCEDT_STALE_DATED_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("ECLAM0008", resourceResult);
    }

    @Test
    public void testUpload_With_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testUpload_With_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance("999999");

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            printResourceResult(resourceResult);
            fail("Test failed, expected response is: EEDTS0012 ");
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Claim_File_With_OBEC_Resource_Type_FAILED_EEDTU0006() throws Exception {
        System.out.println("--------------- testUpload_Claim_File_With_OBEC_Resource_Type_FAILED_EEDTU0006 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("EEDTU0006", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Claim_File_With_Invalid_Resource_Type_FAILED_EEDTS0003() throws Exception {
        System.out.println("--------------- testUpload_Claim_File_With_Invalid_Resource_Type_FAILED_EEDTS0003 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        UploadData uploadData = createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE);
        uploadData.setResourceType("$$");
        uploads.add(uploadData);
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploadData.getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("EEDTS0003", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Missing_Bill_Number_Claim_File_FAILED_ECLAM0003() throws Exception {
        System.out.println("--------------- testUpload_Missing_Bill_Number_Claim_File_FAILED_ECLAM0003 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MISSING_BILL_NUMBER_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0003", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Mismatched_Header_Count_Claim_File_FAILED_ECLAM0005() throws Exception {
        System.out.println("--------------- testUpload_Mismatched_Header_Count_Claim_File_FAILED_ECLAM0005 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MISMATCH_HEADER_COUNT_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0005", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Mismatched_Record_Count_Claim_File_FAILED_ECLAM0007() throws Exception {
        System.out.println("--------------- testUpload_Mismatched_Record_Count_Claim_File_FAILED_ECLAM0007 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MISMATCH_RECORD_COUNT_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("ECLAM0007", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Invalid_Length_Health_Number_OBEC_File_FAILED_EOBEC0004() throws Exception {
        System.out.println("--------------- testUpload_Invalid_Length_Health_Number_OBEC_File_FAILED_EOBEC0004 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.INVALID_LENGTH_HEALTH_NUMBER_MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("EOBEC0004", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Non_Numeric_Health_Number_OBEC_File_FAILED_EOBEC0005() throws Exception {
        System.out.println("--------------- testUpload_Non_Numeric_Health_Number_OBEC_File_FAILED_EOBEC0005 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.NON_NUMERIC_HEALTH_NUMBER_MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertEqualsOnResponseCode("EOBEC0005", resourceResult);
        printResourceResult(resourceResult);
    }

    @Test
    public void testUpload_Mismatched_2_Header_Count_Claim_File_FAILED_ECLAM0006() throws Exception {
        System.out.println("--------------- testUpload_Mismatched_2_Header_Count_Claim_File_FAILED_ECLAM0006 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MISMATCH_2_HEADER_COUNT_MCEDT_CLAIM_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult resourceResult = null;
        try {
            resourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("ECLAM0006", resourceResult);
    }

    @Test
    public void testUpload_Valid_Large_Claim_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testUpload_Valid_Large_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_LARGE_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testUpload_Valid_Vendor_Large_Claim_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testUpload_Valid_Vendor_Large_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.VENDOR_LARGE_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }
}
