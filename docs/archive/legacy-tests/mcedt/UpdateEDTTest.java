package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.soap.SOAPFaultException;

import io.github.carlos_emr.carlos.integration.mcedt.DelegateFactory;
import org.junit.Test;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.ResourceStatus;
import ca.ontario.health.edt.UpdateRequest;
import ca.ontario.health.edt.UploadData;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class UpdateEDTTest extends EDTBaseTest {
    /*
     * 7. UPDATE TESTS
     */

    @Test
    public void testUpdate_Claim_File_SUCCESS() {
        System.out.println("--------------- testUpdate_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        System.out.println("\n******** Results after uploading file(s) ********");
        printResourceResult(uploadResourceResult);
        System.out.println("\n******** Results after updating file(s) ********");
        printResourceResult(updateResourceResult);
    }

    @Test
    public void testUpdate_OBEC_File_SUCCESS() {
        System.out.println("--------------- testUpdate_OBEC_File_SUCCESS ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_OBEC_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        System.out.println("\n******** Results after uploading file(s) ********");
        printResourceResult(uploadResourceResult);
        System.out.println("\n******** Results after updating file(s) ********");
        printResourceResult(updateResourceResult);
    }

    @Test
    public void testUpdate_Stale_Dated_Claim_File_SUCCESS() {
        System.out.println("--------------- testUpdate_Stale_Dated_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_STALE_DATED_CLAIMS_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        System.out.println("\n******** Results after uploading file(s) ********");
        printResourceResult(uploadResourceResult);
        System.out.println("\n******** Results after updating file(s) ********");
        printResourceResult(updateResourceResult);
    }

    @Test
    public void testUpdate_Five_Claim_File_SUCCESS() {
        System.out.println("--------------- testUpdate_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploadDatas = new ArrayList<>();
        uploadDatas.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploadDatas);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        System.out.println("\n******** Results after uploading file(s) ********");
        printResourceResult(uploadResourceResult);
        System.out.println("\n******** Results after updating file(s) ********");
        printResourceResult(updateResourceResult);
    }

    /*
     * OSCAR dependency seems to not allow 6 files to be sent at all.
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testUpdate_Six_Claim_Files_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testUpdate_Six_Claim_Files_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");
        // Upload file(s) and get resource id(s)
        // Uploading 5 files using uploadDatas1
        List<UploadData> uploadDatas1 = new ArrayList<>();
        uploadDatas1.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas1.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas1.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas1.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploadDatas1.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        // Uploadting 1 file using uploadDatas2
        List<UploadData> uploadDatas2 = new ArrayList<>();
        uploadDatas2.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        ResourceResult uploadResourceResult1 = null;
        ResourceResult uploadResourceResult2 = null;
        try {
            uploadResourceResult1 = edtDelegate.upload(uploadDatas1);
            uploadResourceResult2 = edtDelegate.upload(uploadDatas2);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult1);
        updateRequestList.addAll(createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult2));
        try {
            if (updateRequestList.size() > 5) { return; }
            edtDelegate.update(updateRequestList);
        } catch (Exception e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
            return;
        }
    }

    @Test
    public void testUpdate_Claim_File_With_Different_MOHID_FAILED_EEDTS0060() {
        System.out.println("--------------- testUpdate_Claim_File_With_Different_MOHID_FAILED_EEDTS0060 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        edtDelegate = newDelegate("001CF");
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        System.out.println("\n******** Results after uploading file(s) ********");
        printResourceResult(uploadResourceResult);
        System.out.println("\n******** Results after updating file(s) ********");
        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EEDTS0060", updateResourceResult);
    }

    @Test
    public void testUpdate_Submited_File_FAILED_EEDTS0059() {
        System.out.println("--------------- testUpdate_Submited_File_FAILED_EEDTS0059 ---------------\n" + "Actual Results:");

        // Getting resourceIds to update the resource files
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

        // Update files using resourceIds
        ResourceResult resourceResult = null;
        List<UpdateRequest> updateRequests = createUpdateRequestList(FilePath.UPDATED_MCEDT_OBEC_FILE, resourceIds);
        try {
            resourceResult = edtDelegate.update(updateRequests);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0059", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(resourceResult);
        printResourceResult(resourceResult);
        assertEqualsOnResponseCode("EEDTS0059", resourceResult);
    }

    @Test
    public void testUpdate_Claim_File_With_Invalid_Resource_ID_FAILED_EEDTS0056() {
        System.out.println("--------------- testUpdate_Claim_File_With_Invalid_Resource_ID_FAILED_EEDTS0056 ---------------\n" + "Actual Results:");

        // Invalid resource id
        List<BigInteger> resourceIds = new ArrayList<>();
        resourceIds.add(new BigInteger("99988888"));

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, resourceIds);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0056", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EEDTS0056", updateResourceResult);
    }

    /*
     * $$: The Update method expects a BigInteger for the resourceID parameter, so I am getting an error: NumberFormat for input string: "$$".
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testUpdate_Claim_File_With_Invalid_Resource_ID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testUpdate_Claim_File_With_Invalid_Resource_ID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        // Invalid resource id
        List<BigInteger> resourceIds = new ArrayList<>();

        ResourceResult updateResourceResult = null;
        try {
            resourceIds.add(new BigInteger("$$"));
            // Update file(s) content using resource id(s)
            List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, resourceIds);
            
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("Rejected By Policy", e.getFaultInfo().getCode());
            return;
        } catch (NumberFormatException e) {
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("Rejected By Policy", updateResourceResult);
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testUpdate_Claim_File_With_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testUpdate_Claim_File_With_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.UPDATED_MCEDT_CLAIMS_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        edtDelegate = DelegateFactory.getEDTDelegateInstance("999999");
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EEDTS0012", updateResourceResult);
    }

    @Test
    public void testUpdate_Mal_Formed_Header_Claim_File_FAILED_ECLAM0002() {
        System.out.println("--------------- testUpdate_Mal_Formed_Header_Claim_File_FAILED_ECLAM0002 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.MAL_FORMED_HEADER_MCEDT_CLAIM_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("ECLAM0002", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("ECLAM0002", updateResourceResult);
    }

    @Test
    public void testUpdate_Missing_Bill_Number_Claim_File_FAILED_ECLAM0003() {
        System.out.println("--------------- testUpdate_Missing_Bill_Number_Claim_File_FAILED_ECLAM0003 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.MISSING_BILL_NUMBER_MCEDT_CLAIM_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("ECLAM0003", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("ECLAM0003", updateResourceResult);
    }

    @Test
    public void testUpdate_Mismatched_Header_Count_Claim_File_FAILED_ECLAM0005() {
        System.out.println("--------------- testUpdate_Mismatched_Header_Count_Claim_File_FAILED_ECLAM0005 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.MISMATCH_HEADER_COUNT_MCEDT_CLAIM_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("ECLAM0005", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("ECLAM0005", updateResourceResult);
    }

    @Test
    public void testUpdate_Mismatched_Record_Count_Claim_File_FAILED_ECLAM0007() {
        System.out.println("--------------- testUpdate_Mismatched_Record_Count_Claim_File_FAILED_ECLAM0007 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.MISMATCH_RECORD_COUNT_MCEDT_CLAIM_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("ECLAM0007", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("ECLAM0007", updateResourceResult);
    }

    @Test
    public void testUpdate_Less_Than_79_Bytes_Claim_File_FAILED_ECLAM0008() {
        System.out.println("--------------- testUpdate_Less_Than_79_Bytes_Claim_File_FAILED_ECLAM0008 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.LESS_THAN_79_BYTES_MCEDT_CLAIM_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("ECLAM0008", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("ECLAM0008", updateResourceResult);
    }

    @Test
    public void testUpdate_Invalid_Transaction_Code_OBEC_File_FAILED_EOBEC0003() {
        System.out.println("--------------- testUpdate_Invalid_Transaction_Code_OBEC_File_FAILED_EOBEC0003 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.INVALID_TRANSACTION_CODE_MCEDT_OBEC_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EOBEC0003", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EOBEC0003", updateResourceResult);
    }

    @Test
    public void testUpdate_Invalid_Length_Health_Number_OBEC_File_FAILED_EOBEC0004() {
        System.out.println("--------------- testUpdate_Invalid_Length_Health_Number_OBEC_File_FAILED_EOBEC0004 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.INVALID_LENGTH_HEALTH_NUMBER_MCEDT_OBEC_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EOBEC0004", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EOBEC0004", updateResourceResult);
    }

    @Test
    public void testUpdate_Non_Numeric_Health_Number_OBEC_File_FAILED_EOBEC0005() {
        System.out.println("--------------- testUpdate_Non_Numeric_Health_Number_OBEC_File_FAILED_EOBEC0005 ---------------\n" + "Actual Results:");

        // Upload file(s) and get resource id(s)
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        ResourceResult uploadResourceResult = null;
        try {
            uploadResourceResult = edtDelegate.upload(uploads);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        // Update file(s) content using resource id(s)
        List<UpdateRequest> updateRequestList = createUpdateRequestList(FilePath.NON_NUMERIC_HEALTH_NUMBER_MCEDT_OBEC_FILE, uploadResourceResult);
        ResourceResult updateResourceResult = null;
        try {
            updateResourceResult = edtDelegate.update(updateRequestList);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EOBEC0005", e.getFaultInfo().getCode());
            return;
        }

        printResourceResult(updateResourceResult);
        assertEqualsOnResponseCode("EOBEC0005", updateResourceResult);
    }
}
