/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

/**
 * Typed input for building a BillingSpec ON claim.
 */
public record BillingSpecClaimRequest(String demoHin,
                                      String demoDob,
                                      String demoHctype,
                                      String xmlBilltype,
                                      String payMethod,
                                      String clinicRefCode,
                                      String clinicNo,
                                      String functionId,
                                      String providers,
                                      String appointmentNo,
                                      String demoName,
                                      String demoSex,
                                      String apptDate,
                                      String svcCode,
                                      String xmlVisittype,
                                      String dxCode,
                                      String apptProvider,
                                      String creator) {
    public BillingSpecClaimRequest {
        demoHin = nullToEmpty(demoHin);
        demoDob = nullToEmpty(demoDob);
        demoHctype = nullToEmpty(demoHctype);
        xmlBilltype = nullToEmpty(xmlBilltype);
        payMethod = nullToEmpty(payMethod);
        clinicRefCode = nullToEmpty(clinicRefCode);
        clinicNo = nullToEmpty(clinicNo);
        functionId = nullToEmpty(functionId);
        providers = nullToEmpty(providers);
        appointmentNo = nullToEmpty(appointmentNo);
        demoName = nullToEmpty(demoName);
        demoSex = nullToEmpty(demoSex);
        apptDate = nullToEmpty(apptDate);
        svcCode = nullToEmpty(svcCode);
        xmlVisittype = nullToEmpty(xmlVisittype);
        dxCode = nullToEmpty(dxCode);
        apptProvider = nullToEmpty(apptProvider);
        creator = nullToEmpty(creator);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
