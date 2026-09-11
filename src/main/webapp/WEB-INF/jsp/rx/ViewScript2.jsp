<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page
        import="io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.CarlosProperties, io.github.carlos_emr.carlos.clinic.ClinicData, java.util.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.DigitalSignatureUtils" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxSatelliteClinicAddress" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet" %>
<%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>


<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.managers.FaxManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProviderManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProSignatureData" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxPharmacyData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>

<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="ViewScript.title"/></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <c:if test="${empty sessionScope.RxSessionBean}">
            <c:redirect url="error.html"/>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
            <c:set var="bean" value="${sessionScope.RxSessionBean}" scope="page"/>
            <c:if test="${bean.valid == false}">
                <c:redirect url="error.html"/>
            </c:if>
        </c:if>
        <c:set var="ctx" value="${pageContext.request.contextPath}"/>
        <%!
            ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);

            /**
             * The first candidate the fax/print servlet would accept as a script id, or "" when none
             * qualify. This mirrors FrmCustomedPDFServlet.parsePositiveInt EXACTLY — 1-10 digits that
             * parse to a positive {@code int} — so a value the servlet rejects (0, or a 10-digit value
             * above Integer.MAX_VALUE such as 9999999999) can never "win" over a later valid source
             * and reintroduce the unsigned-fax failure this helper prevents. Used to resolve the
             * scriptId across request parameter, request attribute and stash.
             */
            private static String firstValidScriptId(String... candidates) {
                for (String candidate : candidates) {
                    if (candidate != null && candidate.matches("\\d{1,10}")) {
                        try {
                            if (Integer.parseInt(candidate) > 0) {
                                return candidate;
                            }
                        } catch (NumberFormatException ignored) {
                            // > Integer.MAX_VALUE: the servlet would reject it, so skip to the next.
                        }
                    }
                }
                return "";
            }
        %>
        <%
            RxSessionBean bean = (RxSessionBean) pageContext.findAttribute("bean");
            Provider provider = providerManager.getProvider(bean.getProviderNo());
            String providerFax = provider.getWorkPhone();
            if (providerFax == null) {
                providerFax = "";
            }
            providerFax = providerFax.replaceAll("[^0-9]", "");

            Vector vecPageSizes = new Vector();
            vecPageSizes.add("A4 page");
            vecPageSizes.add("A6 page");
            vecPageSizes.add("Letter page");
            Vector vecPageSizeValues = new Vector();
            vecPageSizeValues.add("PageSize.A4");
            vecPageSizeValues.add("PageSize.A6");
            vecPageSizeValues.add("PageSize.Letter");
//are we printing in the past?
//String reprint = (String)request.getAttribute("rePrint") != null ? (String)request.getAttribute("rePrint") : "false";

            String reprint = (String) request.getSession().getAttribute("rePrint") != null ? (String) request.getSession().getAttribute("rePrint") : "false";

            String createAnewRx;
            if (reprint.equalsIgnoreCase("true")) {
                bean = (RxSessionBean) session.getAttribute("tmpBeanRX");
                createAnewRx = "window.location.href = '" + request.getContextPath() + "/rx/searchDrug'";
            } else {
                createAnewRx = "javascript:clearPending('')";
            }
// for satellite clinics
            Vector vecAddressName = null;
            Vector vecAddress = null;
            Vector vecAddressPhone = null;
            Vector vecAddressFax = null;
            CarlosProperties props = CarlosProperties.getInstance();
            if (bMultisites) {
                String appt_no = (String) session.getAttribute("cur_appointment_no");
                String location = null;
                if (appt_no != null) {
                    try {
                        Appointment result = appointmentDao.find(Integer.parseInt(appt_no));
                        if (result != null) location = result.getLocation();
                    } catch (NumberFormatException e) {
                        // Malformed appointment number in session — skip location lookup
                    }
                }

                RxProviderData.Provider rxprovider = new RxProviderData().getProvider(bean.getProviderNo());
                ProSignatureData sig = new ProSignatureData();
                boolean hasSig = sig.hasSignature(bean.getProviderNo());
                String doctorName = "";
                if (hasSig) {
                    doctorName = sig.getSignature(bean.getProviderNo());
                } else {
                    doctorName = (rxprovider.getFirstName() + ' ' + rxprovider.getSurname());
                }

                vecAddressName = new Vector();
                vecAddress = new Vector();
                vecAddressPhone = new Vector();
                vecAddressFax = new Vector();

                java.util.ResourceBundle rb = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                // A cross-provider reprint still shows and faxes the persisted prescriber's clinic
                // identity. Offer that prescriber's sites so the preview and server-bound fax
                // cannot diverge when a covering clinician sends it.
                List<Site> sites = siteDao.getActiveSitesByProviderNo(bean.getProviderNo());

                String encodedDoctorName = SafeEncode.forHtml(doctorName);
                String encodedTelLabel = SafeEncode.forHtml(rb.getString("RxPreview.msgTel"));
                String encodedFaxLabel = SafeEncode.forHtml(rb.getString("RxPreview.msgFax"));

                for (int i = 0; i < sites.size(); i++) {
                    Site s = sites.get(i);
                    vecAddressName.add(s.getName());
                    // One composer for this block on both ends: FrmCustomedPDFServlet parses the
                    // chosen block back out of scAddress AND recomputes the blocks this prescriber was
                    // offered, so a fax cannot carry a clinic header the request made up.
                    String addressHtml = RxSatelliteClinicAddress.html(encodedDoctorName, s.getName(), s.getAddress(),
                            s.getCity(), s.getProvince(), s.getPostal(), s.getPhone(), s.getFax(), encodedTelLabel, encodedFaxLabel);
                    vecAddress.add(addressHtml);
                    if (s.getName().equals(location))
                        session.setAttribute("RX_ADDR", String.valueOf(i));
                }


            } else if (props.getProperty("clinicSatelliteName") != null) {
                RxProviderData.Provider rxprovider = new RxProviderData().getProvider(bean.getProviderNo());
                ProSignatureData sig = new ProSignatureData();
                boolean hasSig = sig.hasSignature(bean.getProviderNo());
                String doctorName = "";
                if (hasSig) {
                    doctorName = sig.getSignature(bean.getProviderNo());
                } else {
                    doctorName = (rxprovider.getFirstName() + ' ' + rxprovider.getSurname());
                }

                ClinicData clinic = new ClinicData();
                vecAddressName = new Vector();
                vecAddress = new Vector();
                vecAddressPhone = new Vector();
                vecAddressFax = new Vector();
                String[] temp0 = props.getProperty("clinicSatelliteName", "").split("\\|");
                String[] temp1 = props.getProperty("clinicSatelliteAddress", "").split("\\|");
                String[] temp2 = props.getProperty("clinicSatelliteCity", "").split("\\|");
                String[] temp3 = props.getProperty("clinicSatelliteProvince", "").split("\\|");
                String[] temp4 = props.getProperty("clinicSatellitePostal", "").split("\\|");
                String[] temp5 = props.getProperty("clinicSatellitePhone", "").split("\\|");
                String[] temp6 = props.getProperty("clinicSatelliteFax", "").split("\\|");
                java.util.ResourceBundle rb = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

                String encodedDoctorName = SafeEncode.forHtml(doctorName);
                String encodedTelLabel = SafeEncode.forHtml(rb.getString("RxPreview.msgTel"));
                String encodedFaxLabel = SafeEncode.forHtml(rb.getString("RxPreview.msgFax"));

                for (int i = 0; i < temp0.length; i++) {
                    vecAddressName.add(temp0[i]);
                    // Every list is indexed by the name list; a shorter one reads as blank (RxSatelliteClinicAddress.at).
                    String addressHtml = RxSatelliteClinicAddress.html(encodedDoctorName, temp0[i],
                            RxSatelliteClinicAddress.at(temp1, i), RxSatelliteClinicAddress.at(temp2, i),
                            RxSatelliteClinicAddress.at(temp3, i), RxSatelliteClinicAddress.at(temp4, i),
                            RxSatelliteClinicAddress.at(temp5, i), RxSatelliteClinicAddress.at(temp6, i),
                            encodedTelLabel, encodedFaxLabel);
                    vecAddress.add(addressHtml);
                }
            }
            String comment = request.getSession().getAttribute("comment") != null ? request.getSession().getAttribute("comment").toString() : "";
            request.getSession().removeAttribute("comment");
            String pharmacyId = request.getParameter("pharmacyId");
            RxPharmacyData pharmacyData = new RxPharmacyData();
            PharmacyInfo pharmacy = null;

            String prefPharmacy = "";
            String prefPharmacyId = "";
            if (pharmacyId != null && !"null".equalsIgnoreCase(pharmacyId)) {
                pharmacy = pharmacyData.getPharmacy(pharmacyId);
                if (pharmacy != null) {
                    prefPharmacy = pharmacy.getName();
                    prefPharmacyId = String.valueOf(pharmacy.getId());
                    prefPharmacy = prefPharmacy.trim();
                    prefPharmacyId = prefPharmacyId.trim();
                }
            }

            String userAgent = request.getHeader("User-Agent");
            String browserType = "";
            if (userAgent != null) {
                if (userAgent.toLowerCase().indexOf("ipad") > -1) {
                    browserType = "IPAD";
                } else {
                    browserType = "ALL";
                }
            }
        %>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css">
        <script src="<%= request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <%-- Pre-declare i18n messages used in JavaScript so they can be safely embedded
             in JavaScript string literals using OWASP forJavaScript() encoding --%>
                <fmt:message key="ViewScript.js.msieNotPermitted"  var="msg_msieNotPermitted"/>
        <fmt:message key="ViewScript.js.signatureSent"     var="msg_signatureSent"/>
        <fmt:message key="ViewScript.js.signatureDirty"    var="msg_signatureDirty"/>
        <fmt:message key="ViewScript.msgRemovePharmacyInfo" var="msg_removePharmacyInfo"/>
        <fmt:message key="tickler.ticklerMain.errorNoteSaveFailed" var="msg_noteSaveFailed"/>

        <script type="text/javascript">
            /*
             * CSRFGuard's client script populates the hidden CSRF-TOKEN input on
             * DOMContentLoaded, after this block has already executed — a parse-time
             * capture is therefore always empty/stale and every fetch() below gets
             * rejected with 403. Read the token at call time instead (same pattern
             * as labDisplay.jsp's getCsrfToken()).
             */
            function getCsrfToken() {
                var el = document.querySelector('input[name="CSRF-TOKEN"]');
                if (!el) {
                    console.warn('CSRF-TOKEN hidden input not found. POST requests will be rejected.');
                    return '';
                }
                return el.value;
            }

            function resetStash() {
                var url = "${carlos:forJavaScript(ctx)}" + "/rx/deleteRx?parameterValue=clearStash";
                fetch(url, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
                    credentials: 'same-origin',
                    body: ''
                }).then(function() {
                    parent.document.getElementById('rxText').textContent = "";//make pending prescriptions disappear.
                    parent.document.getElementById('searchString').focus();
                });
            }

            function resetReRxDrugList() {
                var url = "${carlos:forJavaScript(ctx)}" + "/rx/deleteRx?parameterValue=clearReRxDrugList";
                fetch(url, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
                    credentials: 'same-origin',
                    body: ''
                });
            }


            /*
             * The most recent Additional Notes save, so a fax can wait for it. A fax renders
             * additNotes from the STORED prescription row (FrmCustomedPDFServlet.bindFaxContentToRecord
             * replaces the posted value, deliberately, so a caller cannot print arbitrary text above
             * another prescriber's signature). addNotes() saves that row with a fire-and-forget
             * fetch, and the textarea's own onchange fires as focus leaves it for the Fax button --
             * so without this the fax POST races the save, and a note the clinician just typed, and
             * can still see in the preview, is silently absent from the outgoing fax.
             */
            var pendingNotesSave = Promise.resolve();

            function onPrint2(method, scriptId, faxDocumentId, pasteAfterSuccess, capturedPasteText,
                              previousUnloadHandler) {
                var useSC = false;
                var scAddress = "";
                var rxPageSize = document.getElementById('printPageSize').value;
                console.log("rxPagesize  " + rxPageSize);

                <% if(vecAddressName != null) { %>
                useSC = true;
                <%for(int i=0; i<vecAddressName.size(); i++) {%>
                if (document.getElementById("addressSel").value == "<%=i%>") {
                    scAddress = "<carlos:encode value='<%= StringEscapeUtils.unescapeHtml4((String)vecAddress.get(i)) %>' context="uriComponent"/>";
                }
                <%}
            }%>
                let action = "<%= request.getContextPath() %>/form/createcustomedpdf?__title=Rx&__method=" + method + "&useSC=" + useSC + "&scAddress=" + scAddress + "&rxPageSize=" + rxPageSize + "&scriptId=" + scriptId;
                var previewForm = document.getElementById("preview").contentWindow.document.getElementById("preview2Form");
                previewForm.action = action;
                if (method === "oscarRxFax") {
                    // Only the fax waits. A print renders additNotes from the request, which
                    // addNotes() already updated synchronously, so there is nothing to wait for --
                    // and deferring a target="_blank" submit out of the click's user-gesture context
                    // would hand it to the popup blocker.
                    pendingNotesSave.then(function () {
                        // Set the target at submit time, not click time: a print in the same modal
                        // leaves target="_blank" on this shared form, and the fax must post back into
                        // the modal, never open a tab. Doing it here also covers a print that lands
                        // while this fax is still waiting on the notes save.
                        previewForm.target = "";
                        previewForm.action = action;
                        previewForm.querySelector('#pdfId').value = faxDocumentId;
                        var previewFrame = document.getElementById('preview');
                        var faxResultHandler = function () {
                            try {
                                var faxSucceeded = frames['preview'].document.getElementById('fax-success');
                                var faxFailed = frames['preview'].document.getElementById('fax-failure');
                                // Access to #preview2Form can become possible just before the
                                // iframe's initial load event. Ignore that unrelated event; only a
                                // response from the fax POST carries one of these result markers.
                                if (!faxSucceeded && !faxFailed) {
                                    // A container-generated 4xx/5xx page has no fax marker, but it
                                    // also cannot contain the prescription preview form. Treat that
                                    // as a failed attempt. The real preview document does contain
                                    // the form, including its possible late baseline load event.
                                    if (!frames['preview'].document.getElementById('preview2Form')) {
                                        previewFrame.removeEventListener('load', faxResultHandler);
                                        resetFailedFaxSubmission(previousUnloadHandler);
                                    }
                                    return;
                                }
                                previewFrame.removeEventListener('load', faxResultHandler);
                                if (faxSucceeded) {
                                    // The server accepted the fax. Nothing on this page may queue it
                                    // a second time from here on, whatever the encounter write does.
                                    faxQueued = true;
                                    if (pasteAfterSuccess) {
                                        // The fax is already queued. Keep this page alive until the
                                        // encounter write confirms success; closing earlier can abort
                                        // the fetch and leave a fax with no chart note.
                                        printPaste2Parent(false, true, true, capturedPasteText)
                                                .then(function (pasted) {
                                                    if (pasted) {
                                                        setTimeout(function () { window.top.close(); }, 3000);
                                                    } else {
                                                        // The pharmacy has the prescription but the chart
                                                        // note does not. Leaving the page frozen here was
                                                        // a dead end: only a reload cleared it, and the
                                                        // reload lost the exact faxed text.
                                                        enterFaxPasteRecovery(lastFaxPasteText || capturedPasteText);
                                                    }
                                                }, function (e) {
                                                    console.error('Encounter paste failed after the fax was queued', e);
                                                    enterFaxPasteRecovery(lastFaxPasteText || capturedPasteText);
                                                });
                                    } else {
                                        setTimeout(function () { window.top.close(); }, 3000);
                                    }
                                } else {
                                    resetFailedFaxSubmission(previousUnloadHandler);
                                }
                            } catch (e) {
                                resetFailedFaxSubmission(previousUnloadHandler);
                                console.error('Could not confirm fax result', e);
                            }
                        };
                        previewFrame.addEventListener('load', faxResultHandler);
                        previewForm.submit();
                    }).catch(function (e) {
                        console.error('Additional notes save failed; fax cancelled', e);
                        alert('${carlos:forJavaScript(msg_noteSaveFailed)}');
                        resetFailedFaxSubmission(previousUnloadHandler);
                    });
                } else {
                    previewForm.target = "_blank";
                    previewForm.submit();
                }

                return true;
            }

            function setComment() {
                frames['preview'].document.getElementById('additNotes').innerHTML = '<carlos:encode value='<%= comment.replaceAll("\n", "<br>") %>' context="javaScriptBlock"/>';
                frames['preview'].document.getElementsByName('additNotes')[0].value = frames['preview'].document.getElementById('additNotes').innerHTML;
            }

            function setDefaultAddr() {
                var url = '${carlos:forJavaScript(ctx)}/rx/ViewSetDefaultAddr';
                var ran_number = Math.round(Math.random() * 1000000);
                var addr = encodeURIComponent(document.getElementById('addressSel').value);
                var params = "addr=" + addr + "&rand=" + ran_number;
                fetch(url, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
                    credentials: 'same-origin',
                    body: params
                });
            }



            function addNotes() {
                // A later blur/button event must not enqueue a write after the fax has frozen
                // its note. The server renders the stored note, so that write could otherwise
                // overtake the fax request and disagree with the captured encounter-paste text.
                // Once a fax has been queued the same holds permanently: the stored note is the
                // record of what the pharmacy received.
                if (faxSubmissionPending || faxQueued) {
                    if (faxNotesState) faxNotesState.notes.value = faxNotesState.value;
                    return false;
                }
                var url = '${carlos:forJavaScript(ctx)}/rx/ViewAddRxComment';
                var ran_number = Math.round(Math.random() * 1000000);
                var comment = encodeURIComponent(document.getElementById('additionalNotes').value);
                var params = "scriptNo=" + encodeURIComponent(faxScriptNo) + "&comment=" + comment + "&rand=" + ran_number;  //]
                // CHAIN onto the previous save, never replace it. Two edits in quick succession
                // (type, blur, type, blur) would otherwise leave pendingNotesSave holding only the
                // second request: if that one resolved first the fax would submit while the first
                // was still in flight, and the first committing afterwards would overwrite the row
                // with the older note -- the same stale-note fax this is meant to prevent, just
                // harder to see. Chaining serializes the writes AND makes the fax await all of them.
                //
                // A non-2xx is a failed save: fetch() only rejects on network errors, so a CSRF
                // rejection or a 500 would otherwise resolve and let the fax race ahead silently.
                // Recover a previous rejected save only when a later edit actually retries it. If
                // this save fails and the clinician clicks Fax, keep the rejection visible to
                // onPrint2 so it cancels instead of sending the previously stored note.
                pendingNotesSave = pendingNotesSave.catch(function () {
                    return undefined;
                }).then(function () {
                    return fetch(url, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
                        credentials: 'same-origin',
                        body: params
                    }).then(function (response) {
                        if (!response.ok) {
                            throw new Error('ViewAddRxComment returned HTTP ' + response.status);
                        }
                    });
                });
                var additNotesEl = frames['preview'].document.getElementById('additNotes');
                additNotesEl.style.whiteSpace = 'pre-wrap';
                additNotesEl.textContent = document.getElementById('additionalNotes').value;
                frames['preview'].document.getElementsByName('additNotes')[0].value = document.getElementById('additionalNotes').value.replace(/\n/g, "\r\n");
            }


            function printIframe() {
                var browserName = navigator.appName;
                if (browserName == "Microsoft Internet Explorer") {
                    alert('${carlos:forJavaScript(msg_msieNotPermitted)}')
                } else {
                    if ('function' === typeof window.onbeforeunload) {
                        window.onbeforeunload = null;
                    }

                    preview.focus();
                    preview.print();

                    self.onfocus = function () {
                        self.setTimeout(
                            function () {
                                self.parent.close();
                            }, 1000);
                    };
                    self.focus();
                }
            }

            function printPaste2Parent(print, fax, pasteRx, capturedPasteText, useCapturedPasteTextAsIs) {
                //console.log("in printPaste2Parent");
                try {
                    var text = "";
                    if (fax && pasteRx && useCapturedPasteTextAsIs && typeof capturedPasteText === 'string') {
                        text = capturedPasteText;
                    } else {
                        <% if (props.isPropertyActive("rx_paste_asterisk")) { %>
                        text += "**********************************************************************************\n";
                        <% } %>

                        if (print) {
                            text += "Prescribed and printed by <carlos:encode value='<%= loggedInInfo.getLoggedInProvider().getFormattedName() %>' context="javaScript"/>\n";
                        } else if (fax) {
                            <%--    	 <% if(echartPreferencesMap.getOrDefault("echart_paste_fax_note", false)) {--%>
                            <% String timeStamp = new SimpleDateFormat("dd-MMM-yyyy hh:mm a").format(Calendar.getInstance().getTime()); %>
                            // %>
                            text = "[Rx faxed to " + '<%= pharmacy!=null?SafeEncode.forJavaScript(pharmacy.getName()):""%>' + " Fax#: " + '<%= pharmacy!=null?SafeEncode.forJavaScript(pharmacy.getFax()):""%>';

                            <%--    	 <% if (rxPreferencesMap.getOrDefault("rx_paste_provider_to_echart", false)) { %>--%>
                            text += " prescribed by <carlos:encode value='<%= loggedInInfo.getLoggedInProvider().getFormattedName() %>' context="javaScript"/>";
                            <%--    	 <% } %>--%>
                            text += ", <%= timeStamp %>]\n";
                            <%--   		 <%--%>
                            <%--    	 }--%>
                            <%--    	 %>    	--%>
                        }

                        if (pasteRx) {
                            if (typeof capturedPasteText === 'string') {
                                text += capturedPasteText;
                            } else if (document.all) {
                                text += preview.document.forms[0].rx_no_newlines.value
                            } else {
                                text += preview.document.forms[0].rx_no_newlines.value + "\n";
                            }

                            if (typeof capturedPasteText !== 'string' && document.getElementById('additionalNotes') !== null) {
                                text += document.getElementById('additionalNotes').value + "\n";
                            }
                        }
                        <% if (props.isPropertyActive("rx_paste_asterisk")) {
                                if(prefPharmacy!=null && prefPharmacy.trim()!=""){ %>
                        text += "<carlos:encode value='<%= prefPharmacy %>' context="javaScript"/>\n"
                        <% } %>
                        text += "****<carlos:encode value='<%= ProviderData.getProviderName(bean.getProviderNo()) %>' context="javaScript"/>********************************************************************************\n";
                        <% } %>
                    }

                    if (fax && pasteRx && typeof text === 'string' && text.length > 0) {
                        lastFaxPasteText = text;
                    }

                    //we support pasting into orig encounter and new casemanagement
                    demographicNo = <%=bean.getDemographicNo()%>;
                    noteEditor = "noteEditor" + demographicNo;
                    var pasteResult = Promise.resolve(true);
                    if (window.parent.opener) {
                        if (window.parent.opener.document.forms["caseManagementEntryForm"] != undefined &&
                            window.parent.opener.document.forms["caseManagementEntryForm"].demographicNo &&
                            window.parent.opener.document.forms["caseManagementEntryForm"].demographicNo.value === "<%=bean.getDemographicNo()%>") {
                            //oscarLog("3");
                            window.parent.opener.pasteToEncounterNote(text);
                            if (print) {
                                printIframe();
                            }
                        } else if (window.parent.opener.document.encForm != undefined) {
                            //oscarLog("4");
                            window.parent.opener.document.encForm.enTextarea.value = window.parent.opener.document.encForm.enTextarea.value + text;
                            if (print) {
                                printIframe();
                            }
                        } else if (window.parent.opener.document.getElementById(noteEditor) != undefined) {
                            window.parent.opener.document.getElementById(noteEditor).value = window.parent.opener.document.getElementById(noteEditor).value + text;
                            if (print) {
                                printIframe();
                            }
                        } else if (pasteRx) {
                            pasteResult = writeToEncounter(print, text);
                        }
                    } else {
                        pasteResult = writeToEncounter(print, text);
                    }
                    return pasteResult;
                } catch (e) {
                    alert("ERROR: could not paste to EMR" + e);
                    if (print) {
                        printIframe();
                    }
                    return Promise.resolve(false);
                }

            }

	function writeToEncounter(print, text) {
    	try {
			var url = "<%=request.getContextPath() %>/rx/WriteToEncounter";
			var prefPharmacy = "<%=prefPharmacy != null ? SafeEncode.forJavaScriptBlock(prefPharmacy) : ""%>";
			return fetch(url, {
				method: 'POST',
				headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
				credentials: 'same-origin',
				body: "prefPharmacy=" + encodeURIComponent(prefPharmacy) +
						"&additionalNotes=" +
						"&body="+ encodeURIComponent(text)
			}).then(function(ret){
				if (!ret.ok) {
					throw new Error('WriteToEncounter returned HTTP ' + ret.status);
				}
				if (print) {
					printIframe();
				}
				openEncounter();
				return true;
			}).catch(function(e) {
				alert("ERROR: could not paste to EMR" + e);
				if (print) {
					printIframe();
				}
				return false;
			});
		} catch (e) {
			alert("ERROR: could not paste to EMR" + e);
			return Promise.resolve(false);
		}
	}

            function openEncounter() {
                var windowprops = "height=710,width=1024,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=20,left=20";
                var currentDate = new Date().toISOString().substring(0, 10);
                var url = "<%= request.getContextPath() %>/encounter/IncomingEncounter?providerNo=<%= bean.getProviderNo() %>&demographicNo=<%= bean.getDemographicNo() %>&curProviderNo=<%= bean.getProviderNo() %>&userName=<carlos:encode value='<%= ProviderData.getProviderName(bean.getProviderNo()) %>' context="uriComponent"/>&curDate=" + currentDate;

                if (window.parent.opener && window.parent.opener.document.forms["caseManagementEntryForm"] != undefined) {
                    // redirect if encounter window open
                    window.parent.opener.location = url;
                    return window.parent.opener;
                }

                return window.open(url, "encounter", windowprops);
            }

            var rxToPaste = null;

            function pasteRxToEchart() {
                var encounterWindow = openEncounter();
                encounterWindow.rxToPaste = rxToPaste;
            }

            function addressSelect() {
                <% if(vecAddressName != null) {
                 %>
                setDefaultAddr();
                <%      for(int i=0; i<vecAddressName.size(); i++) {%>
                if (document.getElementById("addressSel").value == "<%=i%>") {
                    frames['preview'].document.getElementById("clinicAddress").innerHTML = "<%=SafeEncode.forJavaScript((String) vecAddress.get(i))%>";
                }
                <%       }
                      }%>

                <%if (comment != null){ %>
                setComment();
                <%}%>


            }


            function popupPrint(vheight, vwidth, varpage) { //open a new popup window
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
                var popup = window.open(page, "groupno", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function resizeFrame(height) {
                document.getElementById("preview").height = (parseInt(height) + 10) + "px";
            }

        </script>
        <%
            String signatureRequestId = "";
            String imageUrl = "";
            signatureRequestId = DigitalSignatureUtils.generateSignatureRequestId(loggedInInfo.getLoggedInProviderNo());
            imageUrl = request.getContextPath() + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_preview.name() + "&" + DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY + "=" + signatureRequestId;

            // The saved script id for the fax/print request. On a fresh write (updateAndPrint /
            // saveDrug) the id is set as a request ATTRIBUTE, not a parameter, so reading only the
            // parameter yields "" and the fax request cannot identify the script — the stamp-signed
            // prescription is then rejected as unsigned. Resolve parameter -> attribute -> the saved
            // stash script number so every path faxes THIS script. Each source is accepted only if it
            // is shaped like a real script id: a positive integer of 1-10 digits, matching the PDF
            // servlet's own parsePositiveInt. That rejects the literal strings "null"/"undefined" a
            // caller may build from a JS variable, "" , "0", and overlong values — any of which would
            // otherwise be passed on and refused as unsigned — so a bad source falls through to the
            // next rather than winning over a good id.
            String scriptIdForFax = firstValidScriptId(
                    StringUtils.noNull(request.getParameter("scriptId")),
                    request.getAttribute("scriptId") == null ? "" : String.valueOf(request.getAttribute("scriptId")),
                    (bean.getStashSize() > 0 && bean.getStashItem(0).getScript_no() != null)
                            ? bean.getStashItem(0).getScript_no() : "");

            // Faxing persists a FaxJob, so FrmCustomedPDFServlet requires _rx WRITE for the script's
            // patient; gate the Fax buttons on the same right so the page never offers a fax the
            // server will refuse (a read-only reprint of a signed script would otherwise show an
            // enabled Fax button and then a refusal).
            //
            // The gate MUST resolve the same target the server authorizes: the persisted prescription
            // named by scriptIdForFax, NOT the session bean's demographic. scriptIdForFax can come
            // from a request parameter, so it may name a script belonging to a different patient than
            // the chart the bean holds; gating on the bean would then enable Fax for a fax the server
            // refuses (or hide one it would allow). No id, or an id that resolves to nothing or to a
            // row with no patient, means there is nothing faxable — closed, not open.
            //
            // Nothing in here may throw. hasPrivilege rethrows PatientDirectiveException
            // (SecurityInfoManagerImpl) and the DAO lookup can fail on its own; from a JSP either is a
            // 500 on the whole print/fax page rather than a disabled button. Any failure leaves the
            // gate false: Fax off, page still renders.
            // BOTH halves of the gate — may-I-fax and is-it-signed — must describe the SAME
            // prescription, the one scriptIdForFax names, because that is the row the servlet signs
            // from. Reading the permission from the persisted row and the signature from the session
            // stash would let a stash holding a different script decide whether Fax lights up.
            boolean canFaxScript = false;
            boolean faxTargetSigned = false;
            try {
                if (!scriptIdForFax.isEmpty()) {
                    io.github.carlos_emr.carlos.commn.model.Prescription faxTarget =
                            SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.PrescriptionDao.class)
                                    .find(Integer.parseInt(scriptIdForFax));
                    if (faxTarget != null && faxTarget.getDemographicId() != null) {
                        io.github.carlos_emr.carlos.managers.SecurityInfoManager faxSecurityManager =
                                SpringUtils.getBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
                        io.github.carlos_emr.carlos.utility.LoggedInInfo faxLoggedInInfo =
                                io.github.carlos_emr.carlos.utility.LoggedInInfo.getLoggedInInfoFromSession(request);
                        canFaxScript = faxSecurityManager.hasPrivilege(faxLoggedInInfo,
                                        "_rx", "w", String.valueOf(faxTarget.getDemographicId()))
                                && faxSecurityManager.hasPrivilege(faxLoggedInInfo, "_fax", "w", null);
                        faxTargetSigned = faxTarget.getDigitalSignatureId() != null;
                    }
                }
            } catch (RuntimeException e) {
                io.github.carlos_emr.carlos.utility.MiscUtils.getLogger()
                        .warn("Fax gate could not be resolved; leaving Fax disabled", e);
                canFaxScript = false;
                faxTargetSigned = false;
            }
            // The third condition on the Fax buttons: the servlet refuses a fax whose destination is
            // not usable ("Valid fax number not found!"). Computed once here so the server-rendered
            // disabled state and the JavaScript gate cannot drift apart — the initial markup must
            // already reflect it, or the buttons render live until the first signature-pad event
            // fires and a click in that window submits a fax the servlet rejects.
            //
            // Mirror the servlet's own test EXACTLY (FrmCustomedPDFServlet: pharmaFax is trimmed,
            // stripped to digits, and refused outside 7..11). A merely non-blank value is not
            // enough: "N/A", "555-12", or a value wider than faxes.destination would offer a Fax
            // the server then refuses.
            int normalizedPharmacyFaxLength = pharmacy == null || pharmacy.getFax() == null ? 0
                    : pharmacy.getFax().trim().replaceAll("\\D", "").length();
            boolean hasPharmacyFax = normalizedPharmacyFaxLength >= 7
                    && normalizedPharmacyFaxLength <= 11;
            List<FaxConfig> faxConfigs = java.util.Collections.emptyList();
            if (CarlosProperties.getInstance().isRxFaxEnabled()) {
                try {
                    faxConfigs = SpringUtils.getBean(FaxManager.class).getFaxGatewayAccounts(loggedInInfo);
                } catch (RuntimeException e) {
                    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger()
                            .warn("Fax sender accounts could not be loaded; leaving Fax disabled", e);
                }
            }
            boolean hasFaxSenderAccount = faxConfigs != null && !faxConfigs.isEmpty();
            // The fourth condition, and the reason it is a variable both halves of the gate read:
            // sendFax() reads frames['preview'].document, and the #preview iframe is only emitted
            // inside `if (bean.getStashSize() > 0)` further down. With an empty stash the buttons
            // would look live and the click would die on an undefined frame with nothing shown to
            // the user. The signature pad, by contrast, IS rendered when the stash is empty, so a
            // pad event can reach signatureHandler there — the JavaScript gate must carry this
            // term too, or it re-enables buttons the server deliberately rendered disabled.
            boolean previewAvailable = bean.getStashSize() > 0;
        %>
        <script type="text/javascript">
            var POLL_TIME = 1500;
            var counter = 0;
            var isRxFaxEnabled = "<%=CarlosProperties.getInstance().isRxFaxEnabled()%>";
            var faxScriptNo = "<carlos:encode value='<%= scriptIdForFax %>' context="javaScriptBlock"/>";
            var faxSubmissionPending = false;
            var faxNotesState = null;
            // A fax the server has accepted for this script. The prescription is with the
            // pharmacy from that point on, so no later step -- including recovery from a failed
            // encounter paste -- may re-open Fax or Fax & Paste, or let the frozen note that was
            // faxed be rewritten on the stored record.
            var faxQueued = false;
            // The exact text the queued fax carried, held so a failed encounter paste can be
            // retried with that text rather than whatever the page shows afterwards.
            var faxPasteRetryText = null;
            // The fully composed encounter note text for the in-flight fax. Retry must reuse this
            // exact string, including the original header/footer lines, rather than rebuilding
            // them with a later timestamp or changed page state.
            var lastFaxPasteText = null;

            function lockFaxNotes() {
                var notes = document.getElementById('additionalNotes');
                if (!notes || faxNotesState) return;
                var saveButton = document.getElementById('saveAdditionalNotes');
                faxNotesState = {
                    notes: notes,
                    value: notes.value,
                    readOnly: notes.readOnly,
                    disabled: notes.disabled,
                    saveButton: saveButton,
                    saveDisabled: saveButton ? saveButton.disabled : false
                };
                // Read-only keeps the frozen note selectable for copying after a paste error.
                notes.readOnly = true;
                if (saveButton) saveButton.disabled = true;
            }

            function unlockFaxNotes() {
                if (!faxNotesState) return;
                faxNotesState.notes.value = faxNotesState.value;
                faxNotesState.notes.readOnly = faxNotesState.readOnly;
                faxNotesState.notes.disabled = faxNotesState.disabled;
                if (faxNotesState.saveButton) {
                    faxNotesState.saveButton.disabled = faxNotesState.saveDisabled;
                }
                faxNotesState = null;
            }

            function setFaxControlsDisabled(disabled) {
                ['faxButton', 'faxPasteButton'].forEach(function (id) {
                    var control = document.getElementById(id);
                    if (control) control.disabled = disabled;
                });
            }

            function shouldDisableFaxControls() {
                return faxSubmissionPending
                        || faxQueued
                        || typeof hasPreview === 'undefined'
                        || !hasPreview
                        || !hasFaxNumber
                        || !hasFaxSenderAccount
                        || !canFaxScript
                        || !(isSignatureSaved || hasStoredSignature);
            }

            function resetFailedFaxSubmission(previousUnloadHandler) {
                faxSubmissionPending = false;
                lastFaxPasteText = null;
                unlockFaxNotes();
                setFaxControlsDisabled(shouldDisableFaxControls());
                window.onbeforeunload = previousUnloadHandler;
            }

            function enterFaxPasteRecovery(capturedPasteText) {
                // Fax & Paste queued the fax and then failed to write the encounter note.
                // The submission is over, so the page must stop behaving as if one were in
                // flight, but the fax itself must not become repeatable: shouldDisableFaxControls()
                // keeps both fax buttons disabled through faxQueued.
                faxSubmissionPending = false;
                faxQueued = true;
                setFaxControlsDisabled(shouldDisableFaxControls());
                // The note stays frozen on purpose -- read-only, and still holding exactly what
                // the fax carried, so it can be selected and copied if every retry fails.
                faxPasteRetryText = capturedPasteText;
                var retryRow = document.getElementById('faxPasteRetryRow');
                if (retryRow) retryRow.style.display = '';
                var retryButton = document.getElementById('faxPasteRetryButton');
                if (retryButton) retryButton.disabled = false;
                // The fax was sent, so the unload guard's "fax has not been sent" warning would
                // now be false. Leave it cleared and let the clinician close the window.
            }

            function retryFaxPaste() {
                if (typeof faxPasteRetryText !== 'string') return false;
                var retryButton = document.getElementById('faxPasteRetryButton');
                if (retryButton) retryButton.disabled = true;
                // Repeat the exact encounter note text the fax already carried, including its
                // original header/footer, never a later recomposition from current page state.
                printPaste2Parent(false, true, true, faxPasteRetryText, true).then(function (pasted) {
                    if (pasted) {
                        faxPasteRetryText = null;
                        lastFaxPasteText = null;
                        var retryRow = document.getElementById('faxPasteRetryRow');
                        if (retryRow) retryRow.style.display = 'none';
                        setTimeout(function () { window.top.close(); }, 3000);
                    } else if (retryButton) {
                        retryButton.disabled = false;
                    }
                }, function (e) {
                    console.error('Encounter paste retry failed', e);
                    if (retryButton) retryButton.disabled = false;
                });
                return true;
            }

            function refreshImage() {
                counter = counter + 1;
                if (frames["preview"].document.getElementById("signature") != null) {
                    frames["preview"].document.getElementById("signature").src = "<%=imageUrl%>&rand=" + counter;
                }
                frames['preview'].document.getElementById('imgFile').value = '<%=System.getProperty("java.io.tmpdir").replaceAll("\\\\", "/")%>/signature_<%=signatureRequestId%>.jpg';
            }

            function sendFax(pasteAfterSuccess) {
                if (faxSubmissionPending || faxQueued) {
                    return false;
                }
                let faxNumber = document.getElementById('faxNumber');
                if (!faxNumber || faxNumber.selectedIndex < 0) {
                    return false;
                }
                var previousUnloadHandler = window.onbeforeunload;
                window.onbeforeunload = null;
                frames['preview'].document.getElementById('finalFax').value = faxNumber.options[faxNumber.selectedIndex].value;
                // A signature-pad request id identifies a signature capture, not a fax attempt.
                // Reusing it made a second click collide with the first attempt's clinical PDF.
                // Give every fax submission a fresh path-safe identifier instead.
                var faxDocumentId = '<%=signatureRequestId%>-' + Date.now() + '-' + Math.random().toString(36).slice(2, 12);
                // Bind Fax & Paste to the exact text visible at the click. Freeze notes until
                // this attempt finishes so a subsequent edit cannot overtake the fax's stored
                // record read while the first notes save is still in flight.
                var capturedPasteText = null;
                if (pasteAfterSuccess) {
                    var previewForm = document.getElementById('preview').contentWindow.document.getElementById('preview2Form');
                    capturedPasteText = previewForm.elements['rx_no_newlines'].value +
                            (document.all ? '' : '\n');
                    if (document.getElementById('additionalNotes') !== null) {
                        capturedPasteText += document.getElementById('additionalNotes').value + '\n';
                    }
                }
                lastFaxPasteText = null;
                faxSubmissionPending = true;
                setFaxControlsDisabled(true);
                try {
                    lockFaxNotes();
                    onPrint2('oscarRxFax', faxScriptNo,
                            faxDocumentId, Boolean(pasteAfterSuccess), capturedPasteText,
                            previousUnloadHandler);
                } catch (e) {
                    resetFailedFaxSubmission(previousUnloadHandler);
                    throw e;
                }
                return true;

            }

            function unloadMess() {
                mess = '${carlos:forJavaScript(msg_signatureSent)}';
                if (isSignatureDirty) {
                    mess = '${carlos:forJavaScript(msg_signatureDirty)}';
                }
                return mess;
            }

            var isSignatureDirty = false;
            var isSignatureSaved = false;
            <% if (CarlosProperties.getInstance().isRxFaxEnabled()) { %>
            var hasFaxNumber = <%= hasPharmacyFax ? "true" : "false" %>;
            var hasFaxSenderAccount = <%= hasFaxSenderAccount ? "true" : "false" %>;
            var canFaxScript = <%= canFaxScript ? "true" : "false" %>;
            // The script already carries a stored signature (the prescriber's stamp applied on write,
            // or a signature saved earlier). The fax servlet signs from it whenever no fresh pad
            // capture is present, so pad strokes or Clear must not grey out Fax for such a script.
            var hasStoredSignature = <%= faxTargetSigned ? "true" : "false" %>;
            // Same stash term the server-rendered disabled attribute uses, so a pad event cannot
            // re-enable Fax on a page that has no #preview iframe for sendFax() to read.
            var hasPreview = <%= previewAvailable ? "true" : "false" %>;
            <% } %>

            function signatureHandler(e) {
                isSignatureDirty = e.isDirty;
                isSignatureSaved = e.isSave;
                e.target.onbeforeunload = null;
                <% if (CarlosProperties.getInstance().isRxFaxEnabled()) { //%>
                setFaxControlsDisabled(shouldDisableFaxControls());
                <% } %>
                if (e.isSave) {
                    <% if (CarlosProperties.getInstance().isRxFaxEnabled()) { //%>
                    if (hasFaxNumber) {
                        e.target.onbeforeunload = unloadMess;
                    }
		<% }

		// Link the drawn signature to the script that will actually be FAXED, not to the stash
		// row. The two can differ (scriptIdForFax may come from a request parameter), and the fax
		// gate is now resolved from the fax target: linking to the stash row would flip the gate
		// open while leaving the faxed prescription unsigned in storage, so the fax would then be
		// refused as unsigned. Only the digits-validated scriptIdForFax is emitted here.
		if (!scriptIdForFax.isEmpty()) {
		%>
		try {
			let signId = new URLSearchParams(e.storedImageUrl.split('?')[1]).get('digitalSignatureId')
			this.setDigitalSignatureToRx(signId, '<%= scriptIdForFax %>');
		} catch (e) {
			console.error(e);
		}
                    <% } %>

                    refreshImage();
                }
            }

function setDigitalSignatureToRx(digitalSignatureId, scriptId) {
	fetch('<%=request.getContextPath() %>/rx/saveDigitalSignature', {
		method: 'POST',
		headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': getCsrfToken()},
		credentials: 'same-origin',
		body: 'method=saveDigitalSignature&digitalSignatureId=' + encodeURIComponent(digitalSignatureId) + '&scriptId=' + encodeURIComponent(scriptId)
	}).then(function (response) {
		// Once the drawn signature is linked to the script it is a STORED signature like the stamp
		// was, so a later pad stroke or Clear must keep Fax enabled (see signatureHandler).
		if (response.ok && typeof hasStoredSignature !== 'undefined') {
			hasStoredSignature = true;
		}
	}).catch(function (error) {
		console.error(error);
	});
}

            function toggleFaxButtons(disabled) {
                document.getElementById("faxButton").disabled = disabled;
                document.getElementById("faxPasteButton").disabled = disabled;
            }

            function enableExistingSignature() {
                toggleFaxButtons(false);
                frames["preview"].document.onreadystatechange = function (event, readystate) {
                    if (frames["preview"].document.readyState === "complete") {
                        refreshImage();
                    }
                }
            }

            function showFaxWarning() {
                if (typeof hasFaxNumber !== 'undefined' && !hasFaxNumber) {
                    document.getElementById("faxWarningNote").style.display = "block";
                }
            }

            var requestIdKey = "<%=signatureRequestId %>";

        </script>
        <style media="all">
            * {
                font: 13px/1.231 arial, helvetica, clean, sans-serif;
            }

            .warning-note {
                background-color: #ffffcc;
                color: #cc6600;
                padding: 20px;
                border: 1px solid #cc6600;
                border-radius: 5px;
                display: none;
            }
        </style>

    </head>

<body topmargin="0" leftmargin="0" vlink="#0000FF"
	onload="addressSelect();printPharmacy('<%=prefPharmacyId%>');showFaxWarning();">

    <!-- HSFO functionality removed -->
    <div id="bodyView">


            <table border="0" cellpadding="0" cellspacing="0"
                   style="border-collapse: collapse" bordercolor="#111111" width="100%"
                   id="AutoNumber1" height="100%">
                <tr>
                    <td width="100%"
                        style="padding-left: 3px; padding-right: 3px; padding-top: 2px; padding-bottom: 2px"
                        height="0%" colspan="2">

                    </td>
                </tr>

                <tr>
                    <td width="100%" class="leftGreyLine" height="100%" valign="top">
                        <table style="border-collapse: collapse" bordercolor="#111111"
                               width="100%" height="100%">

                            <tr>
				<td>
                                    <div class="DivContentPadding">
					<% if (bean.getStashSize() > 0) { %>
                                        <iframe id='preview' name='preview' width=420px height=890px
							src="<%= request.getContextPath() %>/rx/ViewPreview2?scriptId=<%=bean.getStashItem(0).getScript_no()%>&rePrint=<%=reprint%>&pharmacyId=<carlos:encode value='<%= StringUtils.noNull(request.getParameter("pharmacyId")) %>' context="uriComponent"/>"
							align=center border=0 frameborder=0></iframe></div>
					<% } %>
                                </td>

                                <td valign=top><form name="RxClearPendingForm" action="${pageContext.request.contextPath}/rx/clearPending" method="post">
                                    <input type="hidden" name="action" id="action" value=""/>
                                    <div class="warning-note" id="faxWarningNote">
                                        <strong><fmt:message key="ViewScript.msgWarning"/></strong> <fmt:message key="ViewScript.msgFaxWarning"/><br/><br/><fmt:message key="ViewScript.msgFaxWarningHelp"/>
                                    </div>
                                </form>
                                    <script type="text/javascript">
                                        function clearPending(actionValue) {
                                            var form = document.forms["RxClearPendingForm"];
                                            if (form && form.elements["action"]) {
                                                form.elements["action"].value = actionValue;
                                                form.submit();
                                            } else {
                                                console.warn("RxClearPendingForm not found, skipping clearPending()");
                                            }
                                        }

                                        function clearPendingFax() {
                                            parent.window.location = "<%= request.getContextPath() %>/rx/close.html";
                                            try { var m = parent.document.getElementById('carlosModal'); if (m) { var bs = (typeof parent.bootstrap !== 'undefined') ? parent.bootstrap : (typeof bootstrap !== 'undefined' ? bootstrap : null); if (bs) { var modal = bs.Modal.getInstance(m); if (modal) { modal.hide(); } } } } catch(e) { parent.window.location = '<%= request.getContextPath() %>/rx/close.html'; }
                                        }

                                        function ShowDrugInfo(drug) {
                                            window.open('${carlos:forJavaScript(ctx)}/rx/drugInfo?GN=' + encodeURIComponent(drug), "_blank",
                                                "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
                                        }


                                function printPharmacy(id){
                                    //ajax call to get all info about a pharmacy
                                    //use json to write to html
	                                if(! id) {
										return;
	                                }
                                    var url="${carlos:forJavaScript(ctx)}"+"/rx/managePharmacy2?method=getPharmacyInfo&pharmacyId="+id;
                                    fetch(url, {
                                        method: 'GET',
                                        headers: {'X-Requested-With': 'XMLHttpRequest'},
                                        credentials: 'same-origin'
                                    }).then(function(resp){ return resp.text(); }).then(function(responseText){
                                        var json = JSON.parse(responseText);

                                                    if (json != null) {
                                                        var text = json.name + "<br>" + json.address + "<br>" + json.city + ", " + json.province + ", "
                                                            + json.postalCode + "<br>Tel:" + json.phone1 + " " + json.phone2 + "<br>Fax:" + json.fax + "<br>Email:" + json.email + "<br>Note:" + json.notes;

                                                        text += '<br><br><a class="noprint" style="text-align:center;" onclick="parent.reducePreview();" href="javascript:void(0);">${carlos:forJavaScript(msg_removePharmacyInfo)}</a>';
                                                        text += "<input type='hidden' name='pharmacyInfo' value=" + id + " />"
                                                        expandPreview(text);
                                                    }
                                                });

                                        }

                                        function expandPreview(text) {
                                            try { var dlg = parent.document.querySelector('#carlosModal .modal-dialog'); if (dlg) dlg.classList.add('modal-xl'); } catch(e) {}
                                            document.getElementById('preview').style.width = "600px";
                                            frames['preview'].document.getElementById('pharmInfo').innerHTML = text;
                                            document.getElementById("selectedPharmacy").innerHTML = '<fmt:message key="oscarRx.printPharmacyInfo.paperSizeWarning"/>';
                                        }

                                        function reducePreview() {
                                            try { var dlg = parent.document.querySelector('#carlosModal .modal-dialog'); if (dlg) dlg.classList.remove('modal-xl'); } catch(e) {}
                                            document.getElementById('preview').style.width = "460px";
                                            frames['preview'].document.getElementById('pharmInfo').innerHTML = "";
                                            document.getElementById("selectedPharmacy").innerHTML = "";
                                        }
                                    </script>

                                    <table cellpadding=10 cellspacingp=0>
                                        <% //vecAddress=null;
                                            if (vecAddress != null) { %>
                                        <tr>
                                            <td align="left" colspan=2><fmt:message key="ViewScript.msgAddress"/>
                                                <select name="addressSel" id="addressSel" onChange="addressSelect()"
                                                        style="width:200px;">
                                                    <% String rxAddr = (String) session.getAttribute("RX_ADDR");
                                                        for (int i = 0; i < vecAddressName.size(); i++) {
                                                            String te = (String) vecAddressName.get(i);
                                                            String tf = (String) vecAddress.get(i);%>

                                                    <option value="<%=i%>"
                                                            <% if ( rxAddr != null && rxAddr.equals(""+i)){ %>SELECTED<%}%>
                                                    ><%=te%>
                                                    </option>
                                                    <% }%>

                                                </select>
                                            </td>
                                        </tr>
                                        <% } %>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold;"><span><fmt:message key="ViewScript.msgActions"/></span>
                                            </td>
                                        </tr>

                                        <tr>
                                            <!--td width=10px></td-->
                                            <td><fmt:message key="ViewScript.msgPageSize"/>
                                                <select name="printPageSize" id="printPageSize"
                                                        style="height:20px;font-size:10px">
                                                    <%
                                                        String rxPageSize = (String) request.getSession().getAttribute("rxPageSize");
                                                        for (int i = 0; i < vecPageSizes.size(); i++) {
                                                            String te = (String) vecPageSizes.get(i);
                                                            String tf = (String) vecPageSizeValues.get(i);%>
                                                    <option value="<%=tf%>"
                                                            <%if(rxPageSize!=null && rxPageSize.equals(tf)){%>SELECTED<%}%>
                                                    ><%=te%>
                                                    </option>
                                                    <% }%>
                                                </select>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding-bottom: 0"><span><input type=button
                                                                                       value="<fmt:message key="ViewScript.msgPrint"/>"
                                                                                       class="btn btn-outline-secondary"
                                                                                       style="width: 210px"
                                                                                       onClick="javascript:printIframe();"/></span>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding-top: 0"><span><input type=button
                                                    <%=reprint.equals("true") ? "disabled='true'" : ""%>
                                                                                    value="<fmt:message key="ViewScript.msgPrintAndPaste"/>"
                                                                                    class="btn btn-outline-primary"
                                                                                    style="width: 210px"
                                                                                    onClick="printPaste2Parent(true, false, true);"/></span>
                                            </td>
                                        </tr>
                                        <% if (CarlosProperties.getInstance().isRxFaxEnabled()) { %>
                                        <tr>
                                            <td style="padding-bottom: 0">
                                                <span><fmt:message key="ViewScript.msgFromFaxNumber"/></span>
                                                <select id="faxNumber" name="faxNumber">
                                                    <%
                                                        for (FaxConfig faxConfig : faxConfigs) {
                                                    %>
                                                    <option value="<%=faxConfig.getFaxNumber()%>"
                                                            selected="<%=providerFax.equals(faxConfig.getFaxNumber())%>"><%=faxConfig.getAccountName()%>
                                                    </option>
                                                    <%
                                                        }
                                                    %>
                                                </select>
                                            </td>
                                        </tr>

					<%
						// Exactly the conditions the JavaScript gate applies, from the same values:
						// same prescription for both halves (the persisted row scriptIdForFax names,
						// never the session stash), and the same pharmacy-fax requirement.
						//
						// previewAvailable is declared once with the other gate terms above and
						// read by both halves: enabling Fax requires BOTH a faxable record and the
						// preview sendFax() depends on.
						String isFaxDisabled =
								(!canFaxScript || !faxTargetSigned || !hasPharmacyFax
										|| !hasFaxSenderAccount || !previewAvailable)
										? "disabled" : "";
					%>
                                        <tr>
						<td style="padding-top: 0; padding-bottom: 0"><span><input type=button value="<fmt:message key="ViewScript.msgFax"/>"
										 class="btn btn-outline-secondary" id="faxButton" style="width: 210px"
										 onClick="sendFax(false);" <%=isFaxDisabled%>/></span>
                                            </td>
                                        </tr>
                                        <tr>
                            <td style="padding-top: 0"><span><input type=button value="<fmt:message key="ViewScript.msgFaxAndPaste"/>"
                                    class="btn btn-outline-primary" id="faxPasteButton" style="width: 210px"
                                    onClick="sendFax(true);" <%=isFaxDisabled%>/></span>

                                            </td>
                                        </tr>
                                        <%-- Shown only by enterFaxPasteRecovery(), when the fax was queued
                                             but the encounter note could not be written. It repeats the
                                             encounter paste with the text the fax carried; the fax itself
                                             stays disabled so the pharmacy cannot receive it twice. --%>
                                        <tr id="faxPasteRetryRow" style="display: none">
                                            <td style="padding-top: 0"><span><input type=button
                                                    value="<fmt:message key="ViewScript.msgRetryPaste"/>"
                                                    class="btn btn-outline-danger" id="faxPasteRetryButton"
                                                    style="width: 210px" onClick="retryFaxPaste();"/></span>
                                            </td>
                                        </tr>

                                        <% } %>
                                        <tr>
                                            <td><span><input type=button
                                                             value="<fmt:message key="ViewScript.msgCreateNewRx"/>"
                                                             class="btn btn-outline-secondary"
                                                             style="width: 210px"
                                                             onClick="resetStash();resetReRxDrugList();try{var m=parent.document.getElementById('carlosModal');if(m){var bs=(typeof parent.bootstrap!=='undefined')?parent.bootstrap:(typeof bootstrap!=='undefined'?bootstrap:null);if(bs){var modal=bs.Modal.getInstance(m);if(modal){modal.hide();}}}}catch(e){}"/></span>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td><span><input type=button
                                                             value="<fmt:message key="ViewScript.msgBackToOscar"/>"
                                                             class="btn btn-outline-secondary" style="width: 210px"
                                                             onClick="javascript:clearPending('close');parent.window.close();"/></span>
                                            </td>
                                        </tr>
                                        <%
                                            if (request.getSession().getAttribute("rePrint") == null) {%>

                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:message key="ViewScript.msgAddNotesRx"/></span></td>
                                        </tr>
                                        <tr>
                                            <!--td width=10px></td-->
                                            <td>
                                                <textarea id="additionalNotes" style="width: 200px"
                                                          onchange="javascript:addNotes();"></textarea>
                                                <input type="button" id="saveAdditionalNotes" value="<fmt:message key="ViewScript.msgAdditionalRxNotes"/>"
                                                       class="btn btn-outline-secondary" onclick="javascript:addNotes();"/>
                                            </td>
                                        </tr>

                                        <%}%>
                                        <% if (CarlosProperties.getInstance().isRxSignatureEnabled()) { %>
                                        <%-- Topaz signature pad check removed - HTML5 signature is now standard --%>
                                        <%-- The pad is hidden once the script carries a stored signature, EXCEPT when that
                                             signature is the prescriber's stamp applied automatically on write: the stamp is
                                             a default, and drawing a signature here replaces it (saveDigitalSignature). --%>
						<% boolean stampApplied = Boolean.TRUE.equals(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED));
						   if (bean.getStashSize() == 0 || Objects.isNull(bean.getStashItem(0).getDigitalSignatureId()) || stampApplied) { %>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:message key="ViewScript.msgSignature"/></span></td>
                                        </tr>
                                        <tr>
                                            <td>
									<input type="hidden" name="<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>"
										   value="<%=signatureRequestId%>"/>
									<iframe style="width:500px; height:132px;" id="signatureFrame"
											src="<%= request.getContextPath() %>/signature_pad/tabletSignature?inWindow=true&<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>=<%=signatureRequestId%>&saveToDB=true&demographicNo=<%=bean.getDemographicNo()%>&<%=ModuleType.class.getSimpleName()%>=<%=ModuleType.PRESCRIPTION%>"></iframe>
                                            </td>
                                        </tr>
						<% } %>
                                        <%}%>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:message key="ViewScript.msgDrugInfo"/></span></td>
                                        </tr>
                                        <%
                                            for (int i = 0; i < bean.getStashSize(); i++) {
                                                RxPrescriptionData.Prescription rx
                                                        = bean.getStashItem(i);

                                                if (!rx.isCustom()) {
                                        %>
                                        <tr>
                                            <td><span><a
                                                    href="javascript:ShowDrugInfo('<%= rx.getGenericName() %>');">
						<%= rx.getGenericName() %> (<%= rx.getBrandName() %>) </a></span></td>
                                        </tr>
                                        <%
                                                }
                                            }
                                        %>
                                    </table>
                                </td>
                            </tr>
                            <tr height="100%">
                                <td></td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td height="0%" class="leftBottomGreyLine"></td>
                    <td height="0%" class="leftBottomGreyLine"></td>
                </tr>
                <tr>
                    <td width="100%" height="0%" colspan="2">&nbsp;</td>
                </tr>
            </table>

        </div>
    </body>
</html>
