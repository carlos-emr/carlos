/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.integration.mchcv;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import ca.ontario.health.hcv.*;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidationFactory;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidationResult;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Date;

/**
 * Unit tests to use for MCEDT HCV conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class HinValidatorTest {

    private static HCValidator validator;

    @Before
    public void init() {
        validator = HCValidationFactory.getHCValidator();
    }

    @After
    public void tearDown() {
        validator = null;
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testFAILED_MOD10() {

        HCValidationResult validationResult = validator.validate("1286844022", "YX");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testFAILED_MOD10", validationResult));

        assertEquals(false, isValid);
        assertEquals("10", rcode);
        assertEquals("FAILED_MOD10", responseID.value());

        System.out.println();
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testIS_IN_DISTRIBUTED_STATUS() {
        HCValidationResult validationResult = validator.validate("1102686738", "");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_IN_DISTRIBUTED_STATUS", validationResult));

        assertEquals(false, isValid);
        assertEquals("15", rcode);
        assertEquals(ResponseID.IS_IN_DISTRIBUTED_STATUS.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testIS_NOT_ELIGIBLE() {
        HCValidationResult validationResult = validator.validate("9267294685", "", "A110");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_NOT_ELIGIBLE", validationResult));

        assertEquals(false, isValid);
        assertEquals("20", rcode);
        assertEquals(ResponseID.IS_NOT_ELIGIBLE.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testNOT_ON_ACTIVE_ROSTER() {
        HCValidationResult validationResult = validator.validate("1023947722", "J", "P108");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testNOT_ON_ACTIVE_ROSTER", validationResult));

        assertEquals("102", validationResult.getFeeServiceResponseCode("P108"));
        assertEquals("The FSC entered by the providers is not valid.", validationResult.getFeeServiceResponseDescription("P108"));
        assertNull(validationResult.getFeeServiceDate("P108"));

        assertEquals(true, isValid);
        assertEquals("50", rcode);
        assertEquals(ResponseID.NOT_ON_ACTIVE_ROSTER.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testNOT_ON_ACTIVE_ROSTER_TWO() {
        HCValidationResult validationResult = validator.validate("9287170261", "DK", "A110");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testNOT_ON_ACTIVE_ROSTER_TWO", validationResult));

        assertEquals(true, isValid);
        assertEquals("50", rcode);
        assertEquals(ResponseID.NOT_ON_ACTIVE_ROSTER.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testIS_ON_ACTIVE_ROSTER() {
        HCValidationResult validationResult = validator.validate("1006395956", "WG");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_ON_ACTIVE_ROSTER", validationResult));

        assertEquals(true, isValid);
        assertEquals("51", rcode);
        assertEquals(ResponseID.IS_ON_ACTIVE_ROSTER.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testHAS_NOTICE() {
        HCValidationResult validationResult = validator.validate("1357557162", "", "J695");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testHAS_NOTICE", validationResult));

        assertEquals(true, isValid);
        assertEquals("52", rcode);
        assertEquals(ResponseID.HAS_NOTICE.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testIS_RQ_HAS_EXPIRED() {
        HCValidationResult validationResult = validator.validate("1262643149", "CT", "V406");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_RQ_HAS_EXPIRED", validationResult));

        assertEquals(true, isValid);
        assertEquals("53", rcode);
        assertEquals(ResponseID.IS_RQ_HAS_EXPIRED.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testRETURNED_MAIL() {
        HCValidationResult validationResult = validator.validate("1097189623", "", "X148");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testRETURNED_MAIL", validationResult));

        assertEquals(true, isValid);
        assertEquals("55", rcode);
        assertEquals(ResponseID.RETURNED_MAIL.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testINVALID_VERSION_CODE() {
        HCValidationResult validationResult = validator.validate("1097624546", "AA", "A237");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testINVALID_VERSION_CODE", validationResult));

        assertEquals(false, isValid);
        assertEquals("65", rcode);
        assertEquals(ResponseID.INVALID_VERSION_CODE.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testIS_STOLEN() {
        HCValidationResult validationResult = validator.validate("5123673328", "AN", "A112");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_STOLEN", validationResult));

        assertEquals(false, isValid);
        assertEquals("70", rcode);
        assertEquals(ResponseID.IS_STOLEN.name(), responseID.value());
    }


    @Test
    @Ignore("Valid only for online validation")
    public void testIS_CANCELLED_OR_VOIDED() {
        HCValidationResult validationResult = validator.validate("1108809904", "CW", "J889");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testIS_CANCELLED_OR_VOIDED", validationResult));

        assertEquals(false, isValid);
        assertEquals("75", rcode);
        assertEquals(ResponseID.IS_CANCELLED_OR_VOIDED.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testDAMAGED_STATE() {
        HCValidationResult validationResult = validator.validate("4525895969", "RM");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testDAMAGED_STATE", validationResult));

        assertEquals(false, isValid);
        assertEquals("80", rcode);
        assertEquals(ResponseID.DAMAGED_STATE.name(), responseID.value());
    }


    @Test
    @Ignore("Valid only for online validation")
    public void testLOST_STATE() {
        HCValidationResult validationResult = validator.validate("1268844022", "YX", "V404");
        boolean isValid = validationResult.isValid();
        String rcode = validationResult.getResponseCode();
        ResponseID responseID = validationResult.getResponseID();

        System.out.println(printOutput("testLOST_STATE", validationResult));

        assertEquals(false, isValid);
        assertEquals("83", rcode);
        assertEquals(ResponseID.LOST_STATE.name(), responseID.value());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testMultipleHIN() {

        Requests requests = new Requests();
        HcvRequest request = new HcvRequest();
        request.setHealthNumber("1268844022");
        request.setVersionCode("YX");
        requests.getHcvRequest().add(request);
        request = new HcvRequest();
        request.setHealthNumber("4525895969");
        request.setVersionCode("RM");
        requests.getHcvRequest().add(request);
        request = new HcvRequest();
        request.setHealthNumber("1108809904");
        request.setVersionCode("CW");
        request.getFeeServiceCodes().add("J889");
        requests.getHcvRequest().add(request);
        request = new HcvRequest();
        request.setHealthNumber("5123673328");
        request.setVersionCode("AN");
        requests.getHcvRequest().add(request);
        HcvResults validationResult = null;
        try {
            validationResult = validator.validate(requests, "en");
        } catch (Faultexception e) {
            e.printStackTrace();
        }
        System.out.println("----- testMultipleHIN ------");

        for (int i = 0; i < validationResult.getResults().size(); i++) {
            System.out.println(printOutput("", HCValidator.createSingleResult(validationResult, i)));
        }
    }

    @Test
    /**
     * The Service ID token needs to be changed in the properties file
     * to 999999 in order to trigger this error.
     */
    @Ignore("Valid only for online validation")
    public void testEHCAU0023Response() {
        HCValidationResult validationResult = validator.validate("1006395956", "WG");

        System.out.println(printOutput("testEHCAU0023Response", validationResult));

    }

    @Test
    @Ignore("Valid only for online validation")
    public void testRejected_by_Policy() {
        HCValidationResult validationResult = validator.validate("1268J84402", "YX");

        System.out.println(printOutput("testRejected_by_Policy", validationResult));

        assertEquals(false, validationResult.isValid());
        assertEquals("https://204.41.14.78:1444/HCVService/HCValidationService: cvc-simple-type 1: element healthNumber value '1268J84402' is not a valid instance of type {http://hcv.health.ontario.ca/}hn", validationResult.getEbsFault().getMessage());
    }

    @Test
    @Ignore("Valid only for online validation")
    public void testRejected_by_Policy_Two() {
        HCValidationResult validationResult = validator.validate("1268844022", "Y1");

        System.out.println(printOutput("testRejected_by_Policy_Two", validationResult));

        assertEquals(false, validationResult.isValid());
        assertEquals("https://204.41.14.78:1444/HCVService/HCValidationService: cvc-simple-type 1: element versionCode value 'Y1' is not a valid instance of type {http://hcv.health.ontario.ca/}vc", validationResult.getEbsFault().getMessage());
    }

    private String printOutput(String method, HCValidationResult validationResult) {
        return new StringBuilder()
                .append("----- " + method + " ------" + "\n")
                .append("Actual Results: " + "\n")
                .append("   Service User: " + validator.getBuilder().getConfig().getServiceId() + "\n")
                .append("   End User Identifier: " + validator.getBuilder().getConfig().getUserNameTokenUser() + "\n")
                .append("   Time Stamp: " + new Date() + "\n")
                .append("   Request Audit UID: " + validator.getBuilder().getConfig().getAuditId() + "\n")
                .append(validationResult + "\n")
                .toString();
    }

}