package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.TypeListResult;

/**
 * Unit tests to use for MCEDT conformance testing.
 * Not intended to be run as build tests.  Leave the @Ignore annotation to
 * avoid errors during build.
 */
public class GetTypeListEDTTest extends EDTBaseTest {
    /*
     * 6. GET TYPE LIST TESTS
     */

    @Test
    public void testGet_Type_List_With_Valid_MOHID_SUCCESS() {
        System.out.println("--------------- testGet_Type_List_With_Valid_MOHID_SUCCESS ---------------\n" + "Actual Results:");
        TypeListResult typeListData = null;
        try {
            typeListData = edtDelegate.getTypeList();
        } catch (Faultexception e) {
            printFaultException(e);
            fail();
        }

        assertNotNull(typeListData);
        assertFalse("Expected result to contain resource data", typeListData.getData().isEmpty());

        printTypeList(typeListData);
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testGet_Type_List_With_Blank_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testGet_Type_List_With_Blank_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");
        edtDelegate = newDelegate("");
        TypeListResult typeListData = null;
        try {
            typeListData = edtDelegate.getTypeList();
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
        }

        assertNull("Expected result should be empty", typeListData);
    }

    /*
     * Note: The error code 'EHCAU0023' is not being received; instead, we are getting the 'EEDTS0012' error code.
     * This has been confirmed with the MOH MCEDT Conformance Testing team, who stated, "You’re triggering all expected logic here. Please proceed."
     */
    @Test
    public void testGet_Type_List_With_Invalid_MOHID_FAILED_EEDTS0012() {
        System.out.println("--------------- testGet_Type_List_With_Blank_MOHID_FAILED_EEDTS0012 ---------------\n" + "Actual Results:");
        edtDelegate = newDelegate("999999");
        TypeListResult typeListData = null;
        try {
            typeListData = edtDelegate.getTypeList();
        } catch (Faultexception e) {
            printFaultException(e);
            assertEquals("EEDTS0012", e.getFaultInfo().getCode());
        }

        assertNull("Expected result should be empty", typeListData);
    }
}
