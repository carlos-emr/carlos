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

import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Moves the stash cursor and removes staged prescription cards from the Rx session.
 *
 * <p>{@code parameterValue=deletePrescribe} and the legacy {@code action=delete} form both remove
 * a staged card, so they are POST-only (CSRFGuard only checks non-GET methods and the route name
 * {@code rx/rxStashDelete} does not match {@code HttpMethodGuardFilter}'s mutator vocabulary).
 * {@code setStashIndex} and the {@code action=edit} cursor move stay verb-open.</p>
 *
 * @since 2004-02-05
 */
public final class RxStash2Action extends ActionSupport {

    private static final String METHOD_DELETE_PRESCRIBE = "deletePrescribe";
    private static final String ACTION_DELETE = "delete";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Legacy stash dispatch. {@code action=edit} moves the cursor (read, falls back to the active patient);
     * {@code action=delete} removes a staged card and is POST-only, for the explicitly named patient with
     * {@code _rx} write for that patient.
     *
     * @return the staging view, {@code NONE} after an error response, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String execute()
            throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }


        String method = request.getParameter("parameterValue");
        if (removesStashItem(method) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if ("setStashIndex".equals(method)) {
            return setStashIndex();
        } else if (METHOD_DELETE_PRESCRIBE.equals(method)) {
            return deletePrescribe();
        }

        // action=delete removes a staged card, so it needs the explicitly named patient's bean; the
        // action=edit cursor move is a read and may use the active-patient fallback (#3875).
        RxSessionBean bean = ACTION_DELETE.equals(this.getAction())
                ? RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w")
                : RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        synchronized (bean) {
            applyLegacyAction(bean);
        }
        return SUCCESS;
    }

    /**
     * Whether this request removes a staged card. The legacy action=delete branch runs for ANY
     * parameterValue other than the two named dispatches (null, blank or unrelated), so the gate
     * matches the dispatch in {@link #execute} rather than only {@code method == null}; otherwise a
     * GET with {@code parameterValue=} could remove a card. Both the raw parameter and the
     * Struts-bound field are checked since execute() acts on the latter.
     */
    private boolean removesStashItem(String method) {
        boolean legacyDelete = !"setStashIndex".equals(method) && !METHOD_DELETE_PRESCRIBE.equals(method)
                && (ACTION_DELETE.equals(request.getParameter("action")) || ACTION_DELETE.equals(this.getAction()));
        return METHOD_DELETE_PRESCRIBE.equals(method) || legacyDelete;
    }

    /** The legacy edit (move the cursor) or delete (remove the card) of the card at stashId. */
    private void applyLegacyAction(RxSessionBean bean) {
        if (this.getStashId() < 0 || this.getStashId() >= bean.getStashSize()) {
            return;
        }
        if ("edit".equals(this.getAction())) {
            request.setAttribute("BoxNoFillFirstLoad", "true");
            bean.setStashIndex(this.getStashId());
        }
        if (ACTION_DELETE.equals(this.getAction())) {
            bean.removeStashItem(this.getStashId());
            if (bean.getStashIndex() >= bean.getStashSize()) {
                bean.setStashIndex(bean.getStashSize() - 1);
            }
        }
    }

    public String setStashIndex() {


        String wp = null;
        try {
            wp = request.getParameter("randomId");

            int randomId;


            if (wp != null && !wp.equals("null")) {

                randomId = Integer.parseInt(wp);

            } else {

                randomId = -1;
            }


            // Setup variables
            RxSessionBean bean = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");

            if (bean == null) {
                response.sendRedirect("error.html");
                return null;
            }


            //find the stashIndex corresponding to the random number
            synchronized (bean) {
                int stashId = bean.getIndexFromRx(randomId);
                if (stashId >= 0 && stashId < bean.getStashSize()) {
                    bean.setStashIndex(stashId);
                }
            }


        } catch (SecurityException e) {
            // A refused patient is a 403, not a logged-and-ignored error (#3908).
            throw e;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only: removes exactly one staged card by its stash key ({@code randomId}) and, for a
     * re-prescribed card, drops its source from the ReRx list unless another staged card still uses it.
     * A malformed key is a 400.
     *
     * @return {@code NONE}; a missing patient workspace receives HTTP 409
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String deletePrescribe()
            throws IOException {
        MiscUtils.getLogger().debug("===========start in deletePrescribe ===========");


        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");

        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        // randomId is the card's stash key (the <rand> in set_<rand>), never a drug id.
        int randomId;
        try {
            randomId = Integer.parseInt(request.getParameter("randomId"));
        } catch (NumberFormatException _) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        // Looking up the key and removing its positional entry must be one operation. Another
        // window can close a preceding card between accessor calls and otherwise shift this
        // index onto a different medication. Keep ReRx cleanup in the same critical section.
        synchronized (bean) {
            int stashId = bean.getIndexFromRx(randomId);
            if (stashId != -1) {
                int sourceDrugId = bean.getStashItem(stashId).getDrugReferenceId();
                bean.removeStashItem(stashId);
                // Closing a re-prescribed card also un-ticks its source for ReRx. This request is the
                // card X button's only server call: it used to send removeFromReRxDrugIdList as well,
                // which removes the first stash entry for that source in a second, unordered request
                // and could drop a different draft (#3908). The source stays listed while another
                // staged card still re-prescribes it.
                if (sourceDrugId > 0 && !stagesSource(bean, sourceDrugId)) {
                    bean.getReRxDrugIdList().remove(String.valueOf(sourceDrugId));
                }
                if (bean.getStashIndex() >= bean.getStashSize()) {
                    bean.setStashIndex(bean.getStashSize() - 1);
                }
            } else {
                MiscUtils.getLogger().debug("deletePrescribe: no staged card for the requested random id");
            }
        }

        return SUCCESS;
    }


    private static boolean stagesSource(RxSessionBean bean, int sourceDrugId) {
        for (int i = 0; i < bean.getStashSize(); i++) {
            RxPrescriptionData.Prescription item = bean.getStashItem(i);
            if (item != null && item.getDrugReferenceId() == sourceDrugId) {
                return true;
            }
        }
        return false;
    }

    private String action = null;
    private int stashId = -1;

    public String getAction() {
        return this.action;
    }

    @StrutsParameter
    public void setAction(String RHS) {
        this.action = RHS;
    }

    public int getStashId() {
        return this.stashId;
    }

    @StrutsParameter
    public void setStashId(int RHS) {
        this.stashId = RHS;
    }
}
