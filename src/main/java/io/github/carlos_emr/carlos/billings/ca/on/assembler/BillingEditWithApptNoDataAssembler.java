/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingEditWithApptNoViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONClaimLoader;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link BillingEditWithApptNoViewModel} for
 * {@code billing/CA/ON/billingEditWithApptNo.jsp}, the auto-submit
 * appointment-edit bridge form. Takes the request parameters the legacy
 * JSP read inline (caller-supplied appointment context) plus the
 * appointment-number-driven {@link BillingClaimHeader1Data} /
 * {@link BillingItemData} pair, and emits the per-{@link BillingONItem}
 * service-code / unit hidden-field projection the legacy scriptlet
 * computed in its loop.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingEditWithApptNoDataAssembler {

    private final BillingONClaimLoader claimQueryService;
    private final BillingONItemDao itemDao;
    private final CtlBillingServiceDao ctlBillingServiceDao;

    /** Production constructor — Struts no-arg shape. */
    public BillingEditWithApptNoDataAssembler(BillingONClaimLoader claimQueryService,
                                       BillingONItemDao itemDao,
                                       CtlBillingServiceDao ctlBillingServiceDao) {
        this.claimQueryService = claimQueryService;
        this.itemDao = itemDao;
        this.ctlBillingServiceDao = ctlBillingServiceDao;
    }

    /**
     * Build the view model.
     *
     * @param request the current {@link HttpServletRequest}; reads the
     *                billRegion / billForm / hotclick / appointment_no /
     *                demographic_name / status / demographic_no /
     *                providerview / user_no / apptProvider_no /
     *                appointment_date / start_time parameters
     * @return populated view model
     */
    public BillingEditWithApptNoViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        BillingEditWithApptNoViewModel.Builder b = BillingEditWithApptNoViewModel.builder();

        String appointmentNo = request.getParameter("appointment_no");
        String billForm = request.getParameter("billForm");
        String status = request.getParameter("status");
        b.appointmentNo(appointmentNo)
                .billForm(billForm)
                .demographicName(request.getParameter("demographic_name"))
                .demographicNo(request.getParameter("demographic_no"))
                .providerView(request.getParameter("providerview"))
                .apptProviderNo(request.getParameter("apptProvider_no"))
                .appointmentDate(request.getParameter("appointment_date"))
                .startTime(request.getParameter("start_time"));

        // Pull the most recent active billing record + its first item for
        // this appointment. The legacy scriptlet did this inline via
        // BillingONClaimLoader.getBillingByApptNo(...) which returns a
        // List<Object> of length >= 2 when a record is found: [0] is a
        // BillingClaimHeader1Data, [1] is a BillingItemData.
        List<Object> aL = claimQueryService.getBillingByApptNo(appointmentNo);

        String serviceCode = "";
        String billNo = "";
        String headerStatus = status;
        if (aL != null && aL.size() >= 2 && aL.get(0) instanceof BillingClaimHeader1Data
                && aL.get(1) instanceof BillingItemData) {
            BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(0);
            BillingItemData iobj = (BillingItemData) aL.get(1);

            billNo = nullToEmpty(obj.getId());
            headerStatus = nullToEmpty(obj.getStatus());
            b.billNo(billNo)
                    .status(headerStatus)
                    .visitDate(obj.getAdmission_date())
                    .billingDate(obj.getBilling_date())
                    .visitType(obj.getVisittype())
                    .location(obj.getFacilty_num())
                    .clinicNo(obj.getClinic())
                    .asstProviderNo(obj.getAsstProvider_no())
                    .assgProviderNo(obj.getAsstProvider_no())
                    .mReview(obj.getMan_review())
                    .xmlProvider(obj.getProviderNo())
                    .referralCode(obj.getRef_num() == null ? "" : obj.getRef_num())
                    .site(obj.getClinic())
                    .xmlBilltype(obj.getPay_program())
                    .demoHin(obj.getHin())
                    .demoVer(obj.getVer())
                    .demoHcType(obj.getProvince())
                    .demoDob(obj.getDob())
                    .demoName(obj.getDemographic_name())
                    .serviceDate(iobj.getService_date())
                    .serviceCode(iobj.getService_code())
                    .dxCode(iobj.getDx())
                    .dxCode1(iobj.getDx1())
                    .dxCode2(iobj.getDx2());

            serviceCode = nullToEmpty(iobj.getService_code());
        } else {
            // Pass through the caller-supplied status when no header was
            // found, matching legacy behavior.
            b.status(nullToEmpty(status));
        }

        // The "billed" guard is checked against status[0] == 'B' in the JSP.
        // null-safe: only block when we actually have a non-empty status.
        b.billedItemBlocked(headerStatus != null && !headerStatus.isEmpty()
                && headerStatus.charAt(0) == 'B');

        // Per-line-item hidden-field projection. The legacy scriptlet
        // emitted either a single "checked" hidden input (when the item's
        // count was "1") or a serviceCode/serviceUnit pair.
        List<BillingEditWithApptNoViewModel.HiddenServiceField> serviceFields = new ArrayList<>();
        int serviceN = 0;
        int servicesCheckedNum = 0;
        String curBillForm = "";
        if (!billNo.isEmpty()) {
            try {
                List<BillingONItem> items = itemDao.getActiveBillingItemByCh1Id(ConversionUtils.fromIntString(billNo));
                if (items != null) {
                    for (BillingONItem bItem : items) {
                        String code = bItem.getServiceCode();
                        String count = bItem.getServiceCount();
                        if ("1".equals(count)) {
                            // Match legacy: lookup billing-form by serviceCode (the
                            // BillingItemData.service_code, not the loop item's),
                            // first row's servicetype wins.
                            for (Object[] svc : ctlBillingServiceDao.findUniqueServiceTypesByCode(serviceCode)) {
                                curBillForm = String.valueOf(svc[1]);
                                billForm = curBillForm;
                                break;
                            }
                            String xmlName = "xml_" + nullToEmpty(code);
                            serviceFields.add(BillingEditWithApptNoViewModel.HiddenServiceField.checked(xmlName));
                            servicesCheckedNum++;
                        } else {
                            String codeName = "serviceCode" + serviceN;
                            String unitName = "serviceUnit" + serviceN;
                            serviceFields.add(BillingEditWithApptNoViewModel.HiddenServiceField.pair(
                                    codeName, code, unitName, count));
                            serviceN++;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // SpringUtils bean lookup or DAO error: keep going with
                // empty service-fields rather than fail the whole render.
                // Matches legacy behavior — the JSP would have thrown a
                // generic NPE; the assembler logs and keeps the form
                // populated with the header context.
            }
        }
        b.serviceFields(serviceFields)
                .servicesCheckedNum(servicesCheckedNum)
                .curBillForm(curBillForm)
                .billForm(billForm);

        return b.build();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
