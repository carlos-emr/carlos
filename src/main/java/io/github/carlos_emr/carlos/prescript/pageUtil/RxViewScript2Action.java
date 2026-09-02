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

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxViewScript2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final PrescriptionSignatureStampService signatureStampService;

    /** Struts-created router: resolves collaborators from the Spring context. */
    public RxViewScript2Action() {
        this(SpringUtils.getBean(PrescriptionSignatureStampService.class));
    }

    RxViewScript2Action(PrescriptionSignatureStampService signatureStampService) {
        this.signatureStampService = signatureStampService;
    }

    public String execute()
            throws IOException, ServletException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        // Setup variables


        HttpSession session = request.getSession();
        RxSessionBean bean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        // Reprint mode. reprint2 (RxRePrescribe2Action) loads the reprinted script into tmpBeanRX
        // and flags the session with rePrint=true; ViewScript2.jsp then renders tmpBeanRX, NOT the
        // live RxSessionBean. Nothing may be persisted or stamped here: the live stash is whatever
        // the prescriber has pending — possibly nothing (saving it would insert an orphan
        // prescription row) or re-prescribed items that still carry their ORIGINAL script number
        // (saving would be skipped and that historical script would be re-signed). A reprint shows
        // the signature stored when the script was first printed, or the pad if it never was.
        if (isReprintMode(session)) {
            RxSessionBean reprinted = (RxSessionBean) session.getAttribute("tmpBeanRX");
            if (reprinted == null) {
                // reprint2 always stores both; a marker without its bean is a stale/inconsistent
                // session and ViewScript2.jsp would dereference the missing bean. Clear the marker
                // so the next view is a normal one, and bail out the way a missing session does.
                // nosemgrep: tainted-session-from-http-request -- value is null literal (clearing session attribute), not user input
                session.setAttribute("rePrint", null);
                response.sendRedirect("error.html");
                return null;
            }
            String reprintedScriptId = persistedScriptId(reprinted);
            if (reprintedScriptId != null) {
                request.setAttribute("scriptId", reprintedScriptId);
            }
            return "viewScript";
        }

        RxPrescriptionData.Prescription rx;
        RxPrescriptionData prescription = new RxPrescriptionData();

        // Reuse an already-persisted script instead of writing a duplicate. This action is reached
        // via popForm2 after "Save And Print", where updateSaveAllDrugs already persisted the stash
        // (each item now carries its drugs row id and the shared script_no). Calling saveScript
        // again here created a SECOND prescription — and duplicate drugs rows — for a single
        // prescribing action. Only save when the stash is not yet persisted.
        String scriptId = persistedScriptId(bean);
        if (scriptId == null) {
            scriptId = prescription.saveScript(loggedInInfo, bean);
            for (int i = 0; i < bean.getStashSize(); i++) {
                rx = bean.getStashItem(i);
                rx.Save(scriptId);
                rx.setScript_no(scriptId);
                rx = null;
            }
        }

        // Expose the saved script id so ViewScript2.jsp builds the fax/print request for THIS
        // script; without it the page falls back to an empty request parameter and the stamp-signed
        // script cannot be faxed.
        request.setAttribute("scriptId", scriptId);

        // Sign the new script with the prescriber's stamp (when one is on file) so the print/fax
        // page can fax it without a hand-drawn signature; the pad stays available to override.
        // Require _rx WRITE — the stamp persists a signature, so a read-only prescriber must not
        // trigger it, matching the manual signature-save path. Eligibility ("is this row already
        // signed, and did the logged-in provider write it?") is decided inside the service from the
        // PERSISTED prescription row.
        if (securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)
                && signatureStampService.applyStampToScript(loggedInInfo, bean, scriptId) != null) {
            request.setAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED, Boolean.TRUE);
        }

        return "viewScript";
    }

    /**
     * Mirrors the ViewScript2.jsp test for "render the reprinted tmpBeanRX instead of the live
     * stash": the session-scoped {@code rePrint} flag set by reprint2 and cleared by the save paths.
     */
    static boolean isReprintMode(HttpSession session) {
        Object rePrint = session.getAttribute("rePrint");
        return rePrint != null && "true".equalsIgnoreCase(String.valueOf(rePrint));
    }

    /**
     * The script number under which the whole stash is already persisted, or {@code null} when the
     * stash is empty, contains any unsaved item, or is split across scripts.
     *
     * <p>"Persisted" is decided per item from {@code drugId}, which only
     * {@code RxPrescriptionData.Prescription.Save} assigns (from the inserted drugs row) — never
     * from {@code script_no} alone. A re-prescribed item is built in memory with the ORIGINAL
     * script number copied onto it ({@code RxPrescriptionData.newPrescription}) and {@code drugId}
     * 0, so trusting a uniform positive {@code script_no} would mistake an unsaved re-prescription
     * for that historical script and both skip its save and stamp the old prescription.</p>
     */
    static String persistedScriptId(RxSessionBean bean) {
        if (bean.getStashSize() == 0) {
            return null;
        }
        String first = bean.getStashItem(0).getScript_no();
        // Only a value the whole downstream chain (stamping + FrmCustomedPDFServlet.parsePositiveInt)
        // would accept counts as "already persisted": 1-10 digits parsing to a positive int. A "0" or
        // an overflow value must fall through to a real saveScript rather than being reused as a
        // (rejected) script id that later surfaces as an unsigned/missing script.
        if (!isPositiveScriptNo(first)) {
            return null;
        }
        for (int i = 0; i < bean.getStashSize(); i++) {
            RxPrescriptionData.Prescription item = bean.getStashItem(i);
            if (item == null || item.getDrugId() <= 0 || !first.equals(item.getScript_no())) {
                return null;
            }
        }
        return first;
    }

    /** True when {@code value} is 1-10 digits parsing to a positive {@code int} (script_no's type). */
    private static boolean isPositiveScriptNo(String value) {
        if (value == null || !value.matches("\\d{1,10}")) {
            return false;
        }
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
