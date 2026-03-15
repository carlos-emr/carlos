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
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.DownloadResult;
import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceStatus;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class DownloadEDTTest extends EDTBaseTest {
    /*
     * 4. DOWNLOAD TESTS
     */

    @Test
    public void testDownload_Batch_Edit_And_Remittance_Advice_File_SUCCESS() {
        System.out.println("--------------- testDownload_Batch_Edit_And_Remittance_Advice_File_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail obecFileDetailList = null;
        Detail remittanceAdviceFileDetailList = null;
        try {
            obecFileDetailList = edtDelegate.list(ResourceType.DOWNLOAD_BATCH_EDIT.getType(), ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
            remittanceAdviceFileDetailList = edtDelegate.list(ResourceType.DOWNLOAD_REMITTANCE_ADVICE.getType(), ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        if (obecFileDetailList != null && obecFileDetailList.getResultSize().intValue() != 0) {
            resourceIds.add(obecFileDetailList.getData().get(0).getResourceID());
        }

        if (remittanceAdviceFileDetailList != null && remittanceAdviceFileDetailList.getResultSize().intValue() != 0) {
            resourceIds.add(remittanceAdviceFileDetailList.getData().get(0).getResourceID());
        }

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(downloadResult);
        assertEquals("Number of downloads doesn't match the expected size", resourceIds.size(), downloadResult.getData().size());
        printDownloadResult(downloadResult);
    }

    @Test
    public void testDownload_Five_Files_SUCCESS() {
        System.out.println("--------------- testDownload_Five_Files_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 4);
        for (int i = 0; i < 5; i++) {
            DetailData detailData = detailList.getData().get(i);
            resourceIds.add(detailData.getResourceID());
        }

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(downloadResult);
        printDownloadResult(downloadResult);
    }

    /*
     * Oscar dependency is throwing an error: javax.xml.webserv.soap.SOAPFaultException: https://204.41.14.200:1443/EDTService/EDTService: cvc-complex-type 2.4: in element {http://edt.health.ontario.ca/}download of type {http://edt.health.ontario.ca/}download,
     * found <resourceIDs> (in default namespace), but the next item should be an end-element.
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testDownload_Six_Files_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testDownload_Six_Files_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(null, null, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 5);
        for (int i = 0; i < 6; i++) {
            DetailData detailData = detailList.getData().get(i);
            resourceIds.add(detailData.getResourceID());
        }

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception | SOAPFaultException e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
            return;
        }

        assertNotNull(downloadResult);
        printDownloadResult(downloadResult);
    }

    @Test
    public void testDownload_Claim_File_SUCCESS() {
        System.out.println("--------------- testDownload_Claim_File_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), null, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(downloadResult);
        printDownloadResult(downloadResult);
    }

    @Test
    public void testDownload_With_Invalid_MOHID__FAILED_EEDTS0012() {
        System.out.println("--------------- testDownload_With_Invalid_MOHID__FAILED_EEDTS0012 ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.UPLOAD_CLAIM_FILE.getType(), null, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        edtDelegate = newDelegate("999999");
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(downloadResult);
        assertEquals("EEDTS0012", downloadResult.getData().get(0).getResult().getCode());
        printDownloadResult(downloadResult);
    }

    @Test
    public void testDownload_With_Invalid_Resource_ID__FAILED_EEDTS0056() {
        System.out.println("--------------- testDownload_With_Invalid_Resource_ID__FAILED_EEDTS0056 ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();
        resourceIds.add(new BigInteger("99988888"));
        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0056", e.getFaultInfo().getCode());
            return;
        }

        assertNotNull(downloadResult);
        assertEquals("EEDTS0056", downloadResult.getData().get(0).getResult().getCode());
        printDownloadResult(downloadResult);
    }

    /*
     * $$: The Submit method expects a BigInteger for the resourceID parameter, so I am getting an error: NumberFormat for input string: "$$".
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testDownload_With_Invalid_Resource_ID__FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testDownload_With_Invalid_Resource_ID__FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();
        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            resourceIds.add(new BigInteger("$$"));
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("Rejected By Policy", e.getFaultInfo().getCode());
            return;
        } catch (NumberFormatException e) {
            return;
        }

        assertNotNull(downloadResult);
        assertEquals("Rejected By Policy", downloadResult.getData().get(0).getResult().getCode());
        printDownloadResult(downloadResult);
    }

    /*
     * Oscar dependency is throwing an error: javax.xml.webserv.soap.SOAPFaultException: javax.xml.webserv.soap.SOAPFaultException: https://204.41.14.200:1443/EDTService/EDTService: cvc-particle 3.1: in element {http://edt.health.ontario.ca/}download of type {http://edt.health.ontario.ca/}download,
     * found </ns5:download> (in namespace http://edt.health.ontario.ca/), but next item should be resourceIDs
     *
     * Note: Due to the current implementation of the dependency, we are not catching the exact error 'Rejected By Policy'.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated that "catching exceptions isn’t possible is fine here".
     */
    @Test
    public void testDownload_With_Blank_Resource_ID_And_Blank_MOHID_FAILED_Rejected_By_Policy() {
        System.out.println("--------------- testDownload_With_Blank_Resource_ID_And_Blank_MOHID_FAILED_Rejected_By_Policy ---------------\n" + "Actual Results:");

        List<BigInteger> resourceIds = new ArrayList<>();
        resourceIds.add(null);
        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        edtDelegate = newDelegate("");
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception | SOAPFaultException e) {
            if (e instanceof Faultexception) {
                printFaultException((Faultexception) e);
            }
            if (e instanceof SOAPFaultException) {
                logger.error(e);
            }
            return;
        }

        assertNotNull(downloadResult);
        printDownloadResult(downloadResult);
    }

    @Test
    public void testDownload_PDF_Report_SUCCESS() {
        System.out.println("--------------- testDownload_PDF_Report_SUCCESS ---------------\n" + "Actual Results:");

        // Getting resourceIds to download the resource files
        List<BigInteger> resourceIds = new ArrayList<>();
        Detail detailList = null;
        try {
            detailList = edtDelegate.list(ResourceType.DOWNLOAD_GENERAL_COMMUNICATIONS.getType(), ResourceStatus.DOWNLOADABLE, BigInteger.valueOf(1));
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(detailList);
        assertTrue(detailList.getData().size() > 0);
        resourceIds.add(detailList.getData().get(0).getResourceID());

        // Downloading files using resourceIds
        DownloadResult downloadResult = null;
        try {
            downloadResult = edtDelegate.download(resourceIds);
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(downloadResult);
        printDownloadResult(downloadResult);
    }
}
