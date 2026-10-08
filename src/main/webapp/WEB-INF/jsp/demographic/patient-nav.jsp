<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%--
    patient-nav.jsp - the patient's navigation down the left of the master record, shared with the
    patient portal page (included with jsp:include, so it adds nothing to the including page's
    generated method).

    Reads the request attribute "patientNav" (PatientNavModel), which the including page's action
    sets after checking that the user may read the patient. The privilege checks are the record's
    own security:oscarSec checks, plus the check each target page makes, so a link shows only to a
    user who may open it; the model only supplies the links' targets and the conditions that are not
    privileges. Popup links carry real targets: patient-nav.js opens them in the record's usual popup
    windows (or tabs, if the user prefers).

    @since 2026-10-08
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="nav" value="${requestScope.patientNav}"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/share/css/patient-nav.css">
<script src="${carlos:forHtmlAttribute(ctx)}/share/javascript/demographic/patient-nav.js"></script>
<div class="patient-nav" data-open-in-tab="${nav.openInTab}">
    <table border=0 cellspacing=0 width="100%" id="appt_table">
        <c:if test="${nav.onPortalPage}">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.recordUrl)}"><fmt:message key="encounter.Index.masterFile"/></a></td>
            </tr>
        </c:if>
        <tr class="Header">
            <td style="font-weight: bold"><fmt:message key="demographic.demographiceditdemographic.msgAppt"/></td>
        </tr>
        <tr id="appt_hx">
            <td><a href="${carlos:forHtmlAttribute(nav.appointmentHistoryUrl)}"><fmt:message key="demographic.demographiceditdemographic.btnApptHist"/></a></td>
        </tr>
        <c:if test="${nav.waitingListShown}">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.waitingListUrl)}"><fmt:message key="demographic.demographiceditdemographic.msgWaitList"/></a></td>
            </tr>
        </c:if>
        <security:oscarSec roleName="${nav.roleName}" objectName="_billing" rights="r">
            <tr class="Header">
                <td style="font-weight: bold"><fmt:message key="admin.admin.billing"/></td>
            </tr>
            <tr>
                <td>
                    <c:choose>
                        <c:when test="${nav.ontarioBilling}">
                            <a href="${carlos:forHtmlAttribute(nav.billingHistoryUrl)}" data-nav-popup="page"
                               data-popup-height="500" data-popup-width="800"><fmt:message key="demographic.demographiceditdemographic.msgBillHistory"/></a>
                        </c:when>
                        <c:otherwise>
                            <security:oscarSec roleName="${nav.roleName}" objectName="_billing" rights="w">
                                <a href="${carlos:forHtmlAttribute(nav.invoiceListUrl)}" data-nav-popup="page"
                                   data-popup-height="800" data-popup-width="1000"><fmt:message key="demographic.demographiceditdemographic.msgInvoiceList"/></a>
                            </security:oscarSec>
                            <a href="#" data-nav-eligibility="${carlos:forHtmlAttribute(nav.eligibilityUrl)}"
                               data-demographic-no="${carlos:forHtmlAttribute(nav.demographicNo)}"
                               aria-controls="patient-nav-eligibility" aria-expanded="false"><fmt:message key="demographic.demographiceditdemographic.btnCheckElig"/></a>
                            <div id="patient-nav-eligibility" class="patient-nav__eligibility" role="status" hidden>
                                <span data-role="eligibility-loading"><fmt:message key="demographic.demographiceditdemographic.msgLoading"/></span>
                                <span data-role="eligibility-result"></span>
                                <span data-role="eligibility-error" hidden><fmt:message key="demographic.demographiceditdemographic.msgEligibilityFailed"/></span>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
            <%-- Ontario's billing form needs read; BC's billing form also needs write (billingBC.jsp). --%>
            <c:choose>
                <c:when test="${nav.ontarioBilling}">
                    <tr>
                        <td><a href="${carlos:forHtmlAttribute(nav.createInvoiceUrl)}" data-nav-popup="page"
                               data-popup-height="700" data-popup-width="1000"
                               title="<fmt:message key="demographic.demographiceditdemographic.msgBillPatient"/>"><fmt:message key="demographic.demographiceditdemographic.msgCreateInvoice"/></a></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <security:oscarSec roleName="${nav.roleName}" objectName="_billing" rights="w">
                        <tr>
                            <td><a href="${carlos:forHtmlAttribute(nav.createInvoiceUrl)}" data-nav-popup="page"
                                   data-popup-height="700" data-popup-width="1000"
                                   title="<fmt:message key="demographic.demographiceditdemographic.msgBillPatient"/>"><fmt:message key="demographic.demographiceditdemographic.msgCreateInvoice"/></a></td>
                        </tr>
                    </security:oscarSec>
                </c:otherwise>
            </c:choose>
        </security:oscarSec>
        <tr class="Header">
            <td style="font-weight: bold"><fmt:message key="encounter.Index.clinicalModules"/></td>
        </tr>
        <security:oscarSec roleName="${nav.roleName}" objectName="_eChart" rights="r">
            <security:oscarSec roleName="${nav.roleName}" objectName="_con" rights="r">
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.consultationsUrl)}" data-nav-popup="page"
                           data-popup-height="700" data-popup-width="960"><fmt:message key="demographic.demographiceditdemographic.btnConsultation"/></a></td>
                </tr>
            </security:oscarSec>
        </security:oscarSec>
        <security:oscarSec roleName="${nav.roleName}" objectName="_rx" rights="r">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.prescriptionsUrl)}" data-nav-popup="rx"
                       data-popup-height="700" data-popup-width="1027"><fmt:message key="global.prescriptions"/></a></td>
            </tr>
        </security:oscarSec>
        <security:oscarSec roleName="${nav.roleName}" objectName="_eChart" rights="r" reverse="false">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.echartUrl)}" data-nav-popup="echart"
                       data-popup-height="710" data-popup-width="1024"
                       title="<fmt:message key="demographic.demographiceditdemographic.btnEChart"/>"><fmt:message key="demographic.demographiceditdemographic.btnEChart"/></a></td>
            </tr>
            <security:oscarSec roleName="${nav.roleName}" objectName="_prevention" rights="r">
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.preventionsUrl)}" data-nav-popup="page"
                           data-popup-height="700" data-popup-width="960"><fmt:message key="encounter.LeftNavBar.Prevent"/></a></td>
                </tr>
            </security:oscarSec>
        </security:oscarSec>
        <security:oscarSec roleName="${nav.roleName}" objectName="_tickler" rights="r">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.ticklerUrl)}" data-nav-popup="page"
                       data-popup-height="700" data-popup-width="1000"><fmt:message key="global.tickler"/></a></td>
            </tr>
        </security:oscarSec>
        <%-- Patient portal (issue #3854): a link only while the portal is switched on and the user can read
             invitations or accounts; the page's gate and JSON routes re-check both. On the portal page
             itself (which its own action already allowed) it marks the current page, switched on or not. --%>
        <c:choose>
            <c:when test="${nav.onPortalPage}">
                <tr>
                    <td><span class="patient-nav__current" aria-current="page"><fmt:message key="demographic.portal.link"/></span></td>
                </tr>
            </c:when>
            <c:when test="${nav.portalSwitchedOn}">
                <security:oscarSec roleName="${nav.roleName}" objectName="_portal.invite,_portal.account" rights="r">
                    <tr>
                        <td><a href="${carlos:forHtmlAttribute(nav.portalUrl)}"><fmt:message key="demographic.portal.link"/></a></td>
                    </tr>
                </security:oscarSec>
            </c:when>
        </c:choose>
        <c:if test="${nav.arFormsShown}">
            <security:oscarSec roleName="${nav.roleName}" objectName="_form" rights="r">
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.getArFormUrl('AR1'))}" data-nav-popup="page"
                           data-popup-height="700" data-popup-width="1000">AR1</a></td>
                </tr>
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.getArFormUrl('AR2'))}" data-nav-popup="page"
                           data-popup-height="700" data-popup-width="1000">AR2</a></td>
                </tr>
            </security:oscarSec>
        </c:if>
        <tr class="Header">
            <td style="font-weight: bold"><fmt:message key="encounter.Index.clinicalResources"/></td>
        </tr>
        <special:SpecialPlugin moduleName="inboxmnger">
            <%-- As on the eChart, whose own Inbox Manager link needs document read. --%>
            <security:oscarSec roleName="${nav.roleName}" objectName="_edoc" rights="r">
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.inboxManagerUrl)}" data-nav-popup="window">Inbox Manager</a></td>
                </tr>
            </security:oscarSec>
        </special:SpecialPlugin>
        <special:SpecialPlugin moduleName="inboxmnger" reverse="true">
            <security:oscarSec roleName="${nav.roleName}" objectName="_edoc" rights="r">
                <tr>
                    <td><a href="${carlos:forHtmlAttribute(nav.documentsUrl)}" data-nav-popup="page"
                           data-popup-height="710" data-popup-width="970"><fmt:message key="demographic.demographiceditdemographic.msgDocuments"/></a></td>
                </tr>
                <c:if test="${nav.documentBrowserShown}">
                    <tr>
                        <td><a href="${carlos:forHtmlAttribute(nav.documentBrowserUrl)}" data-nav-popup="page"
                               data-popup-height="710" data-popup-width="970"><fmt:message key="demographic.demographiceditdemographic.msgDocumentBrowser"/></a></td>
                    </tr>
                </c:if>
            </security:oscarSec>
        </special:SpecialPlugin>
        <security:oscarSec roleName="${nav.roleName}" objectName="_eform" rights="r">
            <tr>
                <td><a href="${carlos:forHtmlAttribute(nav.eformsUrl)}"><fmt:message key="demographic.demographiceditdemographic.btnEForm"/></a></td>
            </tr>
        </security:oscarSec>
    </table>
</div>
