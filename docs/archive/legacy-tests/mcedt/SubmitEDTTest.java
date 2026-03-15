package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.ws.soap.SOAPFaultException;

import io.github.carlos_emr.carlos.integration.mcedt.DelegateFactory;
import org.junit.Test;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;

import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.UploadData;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class SubmitEDTTest extends EDTBaseTest {
    /*
     * 2. SUBMIT TESTS
     */

    @Test
    public void testSubmit_Valid_Claim_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testSubmit_Valid_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testSubmit_Valid_Stale_Dated_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_Valid_OBEC_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testSubmit_Valid_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_Valid_Claim_File_And_Stale_Dated_Claim_File_And_OBEC_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testSubmit_Valid_Claim_File_And_Stale_Dated_Claim_File_And_OBEC_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_STALE_DATED_CLAIMS_FILE, ResourceType.UPLOAD_STALE_DATED_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_Five_Valid_Claim_File_SUCCESS_IEDTS0001() {
        System.out.println("--------------- testSubmit_Five_Valid_Claim_File_SUCCESS_IEDTS0001 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));
        uploads.add(createUploadData(FilePath.MCEDT_CLAIMS_FILE, ResourceType.UPLOAD_CLAIM_FILE));

        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            assertEqualsOnResponseCode("IEDTS0001", resourceResult);
            printResourceResult(resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: IEDTS0001 but got: " + e.getFaultInfo().getCode());
        }
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testSubmit_With_Valid_ResourceID_And_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testSubmit_With_Valid_ResourceID_And_Invalid_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            edtDelegate = newDelegate("999999");
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            printResourceResult(resourceResult);
            fail("Test failed, expected response is: EEDTS0012 ");
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_With_Invalid_ResourceID_And_Valid_MOHID_FAILED_EEDTS0056() {
        System.out.println("--------------- testSubmit_With_Invalid_ResourceID_And_Valid_MOHID_FAILED_EEDTS0056 ---------------\n" + "Actual Results:");
        List<BigInteger> ids = Arrays.asList(new BigInteger("99988888"));
        try {
            ResourceResult resourceResult = edtDelegate.submit(ids);
            printResourceResult(resourceResult);
            assertEqualsOnResponseCode("EEDTS0056", resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0056", e.getFaultInfo().getCode());
        }
    }

    /*
     * $$$$$$: The Submit method expects a BigInteger for the resourceID parameter, so I am getting an error: NumberFormat for input string: "$$$$$$".
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testSubmit_With_Invalid_ResourceID_And_Valid_MOHID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testSubmit_With_Invalid_ResourceID_And_Valid_MOHID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");
        try {
            List<BigInteger> ids = Arrays.asList(new BigInteger("$$$$$$"));
            ResourceResult resourceResult = edtDelegate.submit(ids);
            printResourceResult(resourceResult);
            assertEqualsOnResponseCode("Rejected By Policy", resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("Rejected By Policy", e.getFaultInfo().getCode());
        } catch (NumberFormatException e) {
            return;
        }
    }

    /*
     * Oscar dependency is showing an error: Failed to Submit: https://204.41.14.200:1443/EDTService/EDTService: cvc-particle 3.1: in element {http://edt.health.ontario.ca/}submit of type {http://edt.health.ontario.ca/}submit,
     * found </ns5> (in namespace http://edt.health.ontario.ca/), but the next item should be resourceIDs.
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testSubmit_With_Blank_ResourceID_And_Blank_MOHID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testSubmit_With_Blank_ResourceID_And_Blank_MOHID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");
        List<BigInteger> ids = new ArrayList<>();
        ids.add(null);
        try {
            edtDelegate = newDelegate("");
            ResourceResult resourceResult = edtDelegate.submit(ids);
            assertEquals(null, resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            fail("Test failed, expected response is: 'Rejected by Policy' but got: " + e.getFaultInfo().getCode());
        } catch (SOAPFaultException e) {
            return;
        }
    }

    @Test
    public void testUpload_Valid_File_Then_Submit_With_Valid_ResourceID_And_Invalid_MOHID_FAILED_EEDTS0054() {
        System.out.println("--------------- testUpload_Valid_File_Then_Submit_With_Valid_ResourceID_And_Invalid_MOHID_FAILED_EEDTS0054 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            edtDelegate = newDelegate("001CF");
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            printResourceResult(resourceResult);
            assertEqualsOnResponseCode("EEDTS0054", resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0054", e.getFaultInfo().getCode());
        }
    }

    @Test
    public void testSubmit_Already_Submitted_File_FAILED_EEDTS0055() {
        System.out.println("--------------- testSubmit_Already_Submitted_File_FAILED_EEDTS0055 ---------------\n" + "Actual Results:");
        List<UploadData> uploads = new ArrayList<UploadData>();
        uploads.add(createUploadData(FilePath.MCEDT_OBEC_FILE, ResourceType.UPLOAD_OBEC_INBOUND_FILE));
        edtDelegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(uploads.get(0).getDescription()));

        try {
            ResourceResult resourceResult = edtDelegate.upload(uploads);
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            resourceResult = edtDelegate.submit(getResourceIds(resourceResult));
            printResourceResult(resourceResult);
            assertEqualsOnResponseCode("EEDTS0055", resourceResult);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0055", e.getFaultInfo().getCode());
        }
    }
}
