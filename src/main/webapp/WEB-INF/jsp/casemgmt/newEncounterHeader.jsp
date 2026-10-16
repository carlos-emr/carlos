<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
  Purpose: Render the eChart header bar above the encounter, in OSCAR 19's format.
  Features: One compact bar in the viewer's provider colour: MRP (underlined in the MRP's own colour),
  the patient's name (opens the Master Record), sex, date of birth or age, HIN, a phone number, email,
  next appointment, the clinical calculators, and configured chart links.
  What the bar shows is each provider's choice: the "eChart" section of their Preferences
  (EchartDisplayPreference) turns the date of birth (age otherwise), cell phone, HIN and email on or off.
  EchartHeaderViewModelAssembler applies those settings and OSCAR 19's phone rules, so this JSP only
  tests values for emptiness and encodes them. Values the viewer may want to paste elsewhere (date of
  birth or age, HIN, phone, email) are native buttons that copy to the clipboard; a provider can
  instead make the phone numbers tel: click-to-dial links.
  The calculator menu resolves clinical defaults server-side using the originating
  chart's record reference instead of including age or sex in the header URL, and
  there is exactly one entry point to it -- a second link to the same page, differing
  only in how it passed the patient's attributes, was reported as a duplicate control.
  Parameters: EctSessionBean and the authenticated session supply the encounter
  and provider context; there are no direct request parameters for this fragment.
  i18n: every label here resolves against the BROWSER locale. Labels are ordinary <fmt:message>
  tags; JSTL and the Java-formatted age both receive the negotiated bundle locale explicitly because
  the JVM default and LocaleContextHolder both report the server's language on this request path. The
  negotiated language is published as the lang attribute of #header-top-row (and of the chart
  page's <html>, see newEncounterLayout.jsp) so a rendered page states which language the
  server resolved for that request; LocaleUtils logs the same at DEBUG with the raw
  Accept-Language it came from.
  @since 2026-09-17 (OSCAR 19 bar and per-provider eChart settings 2026-10-16)
--%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setLocale value="<%= LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreferenceResolver" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreferences" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.display.EchartHeaderViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.display.EchartHeaderViewModelAssembler" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ page import="java.util.Locale" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect(request.getContextPath() + "/casemgmt/ViewError");
        return;
    }

    String demoNo = bean.demographicNo;
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographicWithExt(loggedInInfo, Integer.parseInt(demoNo));

    if (demographic == null ){
        response.sendRedirect(request.getContextPath() + "/casemgmt/ViewError");
        return;
    }

    // this is accessed in the newEncounterLayout after this header is included.
    String privateConsentEnabledProperty = CarlosProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");
    String popupPatientSex = bean == null || bean.patientSex == null ? "" : bean.patientSex;
    String popupPatientAge = demographic == null ? "" : String.valueOf(demographic.getAge());
    pageContext.setAttribute("popupPatientSex", popupPatientSex);
    pageContext.setAttribute("popupPatientAge", popupPatientAge);
    pageContext.setAttribute("popupDemographicNo", demoNo);

    // Same resolution the <fmt:message> tags below perform, so the Java-formatted age and the
    // JSP-rendered labels can never end up in two different languages.
    Locale browserLocale = LocaleUtils.resolveBundleLocale(request);
    // Published on the header container as its lang attribute: correct HTML for a fragment whose
    // language is negotiated per request, and the one place a field report can show which
    // language the SERVER chose for that very render (a screenshot of the text alone cannot).
    pageContext.setAttribute("negotiatedLanguageTag", browserLocale.toLanguageTag());

    // The viewer's eChart display settings decide which optional items the bar carries.
    String viewerProviderNo = loggedInInfo.getLoggedInProviderNo();
    EchartDisplayPreferences echartPrefs = SpringUtils.getBean(EchartDisplayPreferenceResolver.class)
            .resolve(viewerProviderNo);
    EchartHeaderViewModel echartHeader = SpringUtils.getBean(EchartHeaderViewModelAssembler.class)
            .assemble(viewerProviderNo, demographic, echartPrefs, browserLocale);
    pageContext.setAttribute("echartHeader", echartHeader);
%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<fmt:message key="global.copiedToClipboard" var="copiedToClipboardMsg"/>
<fmt:message key="encounter.header.clickToCopy" var="clickToCopyMsg"/>
<fmt:message key="encounter.header.clickToCall" var="clickToCallMsg"/>
<fmt:message key="demographic.demographicaddrecordhtm.formPhone" var="homePhoneLabel"/>
<fmt:message key="demographic.demographicaddrecordhtm.formPhoneCell" var="cellPhoneLabel"/>
<fmt:message key="demographic.demographicaddrecordhtm.formPhoneWork" var="workPhoneLabel"/>
<script type="text/javascript">
var CARLOS_COPIED_MSG = '${carlos:forJavaScript(copiedToClipboardMsg)}';
function copyToClip(text, el) {
    var orig = el.title;
    function showFeedback() {
        el.title = CARLOS_COPIED_MSG;
        el.style.opacity = '0.5';
        setTimeout(function() { el.style.opacity = '1'; el.title = orig; }, 600);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(showFeedback).catch(function() {
            fallbackCopy(text);
            showFeedback();
        });
    } else {
        fallbackCopy(text);
        showFeedback();
    }
}
function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
}
</script>
<%-- OSCAR 19's eChart bar (casemgmt/newEncounterHeader.jsp + encounterStyles.css), as its users see it:
     "MRP  DOCTOR NAME" in the table font, then the patient's details in bold at 80% of that size, and
     links in OSCAR's blue, darkened to 4.5:1 contrast. The colours come from EchartHeaderViewModelAssembler,
     which only ever returns validated #RRGGBB values (a provider colour that is anything else falls
     back to OSCAR's default). --%>
<div id="header-top-row" lang="${carlos:forHtmlAttribute(negotiatedLanguageTag)}" class="echart-header-bar"
     style="background-color: ${carlos:forHtmlAttribute(echartHeader.headerBackground)}; color: ${carlos:forHtmlAttribute(echartHeader.headerText)}; --echart-header-link: ${carlos:forHtmlAttribute(echartHeader.headerLink)};">
   <div id="left-column">
      <c:if test="${not empty echartHeader.mrpName}">
         <span id="patient-mrp" class="echart-header-mrp"
               style="border-bottom-color: ${carlos:forHtmlAttribute(echartHeader.mrpUnderline)};"><span
               class="label echart-header-mrp-label"><fmt:message key="encounter.Index.msgMRP"/></span>&nbsp;&nbsp;<span
               class="echart-header-mrp-name"><carlos:encode value="${echartHeader.mrpName}"/></span></span>
      </c:if>
      <%-- OSCAR 19's span.Header: everything about the patient is bold, at 80% of the bar's size. --%>
      <span class="echart-header-details">
      <fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile" var="masterRecordTitle"/>
      <a id="patient-full-name" class="echart-header-item echart-header-name"
         href="${carlos:forHtmlAttribute(ctx)}/demographic/DemographicEdit?demographic_no=${carlos:forUriComponent(popupDemographicNo)}"
         target="_blank" title="${carlos:forHtmlAttribute(masterRecordTitle)}"><carlos:encode value="${echartHeader.patientName}"/></a>
      <span id="patient-sex" class="echart-header-item"><carlos:encode value="${echartHeader.sex}"/><c:if
            test="${not empty echartHeader.gender}"><span id="patient-gender"> / <carlos:encode value="${echartHeader.gender}"/></span></c:if><c:if
            test="${not empty echartHeader.pronoun}"><span id="patient-pronouns"> (<carlos:encode value="${echartHeader.pronoun}"/>)</span></c:if></span>
      <c:choose>
         <c:when test="${echartHeader.showDateOfBirth}">
            <button type="button" id="patient-dob" class="copyable echart-header-item"
                    title="${carlos:forHtmlAttribute(echartHeader.age)}"
                    onclick="copyToClip('${carlos:forJavaScriptAttribute(echartHeader.dateOfBirth)}',this)"><carlos:encode value="${echartHeader.dateOfBirth}"/></button>
         </c:when>
         <c:otherwise>
            <button type="button" id="patient-age" class="copyable echart-header-item"
                    title="${carlos:forHtmlAttribute(echartHeader.dateOfBirth)}"
                    onclick="copyToClip('${carlos:forJavaScriptAttribute(echartHeader.age)}',this)"><carlos:encode value="${echartHeader.age}"/></button>
         </c:otherwise>
      </c:choose>
      <c:if test="${not empty echartHeader.hin}">
         <button type="button" id="patient-hin" class="copyable echart-header-item"
                 title="${carlos:forHtmlAttribute(clickToCopyMsg)}"
                 onclick="copyToClip('${carlos:forJavaScriptAttribute(echartHeader.hin)}',this)"><span class="label"><fmt:message key="demographic.patient.context.hin"/></span><carlos:encode value="${echartHeader.hin}"/><c:if
                 test="${not empty echartHeader.hinVersion}">&nbsp;<carlos:encode value="${echartHeader.hinVersion}"/></c:if></button>
      </c:if>
      <%-- One number, or every number the patient marked preferred; the tooltip names the other
           number when there is one (see EchartHeaderViewModelAssembler#selectPhones). With the
           provider's "click-to-dial" setting on, each number is a tel: link (issue #852) instead of
           a copy button; the id, icon and tooltip stay the same. --%>
      <c:forEach var="phone" items="${echartHeader.phones}">
         <c:choose>
            <c:when test="${phone.kind == 'CELL'}"><c:set var="phoneId" value="patient-cell-phone"/><c:set var="phoneIcon" value="fa-mobile-screen-button"/><c:set var="phoneLabel" value="${cellPhoneLabel}"/></c:when>
            <c:when test="${phone.kind == 'WORK'}"><c:set var="phoneId" value="patient-work-phone"/><c:set var="phoneIcon" value="fa-briefcase"/><c:set var="phoneLabel" value="${workPhoneLabel}"/></c:when>
            <c:otherwise><c:set var="phoneId" value="patient-phone"/><c:set var="phoneIcon" value="fa-phone"/><c:set var="phoneLabel" value="${homePhoneLabel}"/></c:otherwise>
         </c:choose>
         <c:set var="phoneTelHref" value="${echartHeader.phoneTelLinks ? phone.telHref() : ''}"/>
         <c:choose>
            <c:when test="${phone.alternateKind == 'HOME'}"><c:set var="phoneTitle" value="${homePhoneLabel}: ${phone.alternateNumber}"/></c:when>
            <c:when test="${phone.alternateKind == 'CELL'}"><c:set var="phoneTitle" value="${cellPhoneLabel}: ${phone.alternateNumber}"/></c:when>
            <c:when test="${not empty phoneTelHref}"><c:set var="phoneTitle" value="${clickToCallMsg}"/></c:when>
            <c:otherwise><c:set var="phoneTitle" value="${clickToCopyMsg}"/></c:otherwise>
         </c:choose>
         <c:choose>
            <c:when test="${not empty phoneTelHref}">
               <a id="${carlos:forHtmlAttribute(phoneId)}" class="echart-header-item echart-header-tel"
                  href="${carlos:forHtmlAttribute(phoneTelHref)}"
                  title="${carlos:forHtmlAttribute(phoneTitle)}"><i class="fa-solid ${carlos:forHtmlAttribute(phoneIcon)}" role="img" aria-label="${carlos:forHtmlAttribute(phoneLabel)}"></i>&nbsp;<carlos:encode value="${phone.number}"/></a>
            </c:when>
            <c:otherwise>
               <button type="button" id="${carlos:forHtmlAttribute(phoneId)}" class="copyable echart-header-item"
                       title="${carlos:forHtmlAttribute(phoneTitle)}"
                       onclick="copyToClip('${carlos:forJavaScriptAttribute(phone.number)}',this)"><i class="fa-solid ${carlos:forHtmlAttribute(phoneIcon)}" role="img" aria-label="${carlos:forHtmlAttribute(phoneLabel)}"></i>&nbsp;<carlos:encode value="${phone.number}"/></button>
            </c:otherwise>
         </c:choose>
      </c:forEach>
      <c:if test="${not empty echartHeader.email}">
         <c:choose>
            <%-- A patient who consented to email for care gets a mailto link, as in OSCAR 19. --%>
            <c:when test="${echartHeader.emailConsented}">
               <a id="patient-email-link" class="echart-header-item"
                  href="${carlos:forHtmlAttribute(echartHeader.mailtoHref())}"><carlos:encode value="${echartHeader.email}"/></a>
            </c:when>
            <c:otherwise>
               <button type="button" id="patient-email" class="copyable echart-header-item"
                       title="${carlos:forHtmlAttribute(clickToCopyMsg)}"
                       onclick="copyToClip('${carlos:forJavaScriptAttribute(echartHeader.email)}',this)"><carlos:encode value="${echartHeader.email}"/></button>
            </c:otherwise>
         </c:choose>
      </c:if>
      <%-- "Next Appt: <date>", the date left blank when nothing is booked, exactly as OSCAR 19 showed it. --%>
      <fmt:message key="encounter.Header.nextApptMsg" var="appointmentHistoryTitle"/>
      <a id="patient-next-appointment" class="echart-header-item echart-header-next-appt"
         href="${carlos:forHtmlAttribute(ctx)}/demographic/DemographicApptHistory?demographic_no=${carlos:forUriComponent(popupDemographicNo)}&amp;orderby=appointment_date&amp;dboperation=appt_history&amp;limit1=0&amp;limit2=25"
         title="${carlos:forHtmlAttribute(appointmentHistoryTitle)}" target="_blank"><span class="label"><fmt:message
         key="encounter.Header.nextAppt"/></span><carlos:encode value="${echartHeader.nextAppointment}"/></a>
      <%-- The chart's single entry point to the clinical calculators (CARLOS keeps it in OSCAR 19's
           bar). A second anchor to the same route used to sit lower in this bar, labelled identically
           and differing only in passing sex/age in the query string; phc007 reported the pair as two
           calculator links in the header. This one is the form to keep: calculators.jsp resolves sex
           and age from the record when given demo=, which keeps patient attributes out of navigation
           URLs (asserted by scripts/admin-fragment-navigation.test.js).
           The href is the real route, not javascript:void(0): the click opens the popup and
           returns false, so a plain click behaves like the other header popups, while a
           middle-click or keyboard follow still reaches the page (and the anchor is a link, not a
           button dressed as one -- Sonar S6844). --%>
      <fmt:message key="encounter.Index.calculators" var="calculatorsTitle"/>
      <span class="echart-header-item echart-header-link">
        <a href="${carlos:forHtmlAttribute(ctx)}/encounter/ViewCalculators?demo=${carlos:forUriComponent(popupDemographicNo)}"
           id="chartCalculatorsLink"
           title="${carlos:forHtmlAttribute(calculatorsTitle)}"
           onclick="window.open('${carlos:forJavaScriptAttribute(ctx)}/encounter/ViewCalculators?demo=${carlos:forUriComponent(popupDemographicNo)}', 'ClinicalCalculators', 'width=800,height=650,scrollbars=yes,resizable=yes'); return false;"><fmt:message key="encounter.Index.calculators"/></a>
      </span>
      <% if (CarlosProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
      <span class="echart-header-item echart-header-link">
        <a href="javascript:void(0);" onClick="popupPage(600,175,'Calculators','${carlos:forJavaScript(ctx)}/commons/omdDiseaseList.jsp?sex=${carlos:forUriComponent(popupPatientSex)}&age=${carlos:forUriComponent(popupPatientAge)}'); return false;"><fmt:message key="encounter.Header.OntMD"/></a>
      </span>
      <%}%>
      <span class="echart-header-item echart-header-link"><%=getEChartLinks() %></span>
      </span>
   </div>
</div>

<%!
    String getEChartLinks() {
        String str = CarlosProperties.getInstance().getProperty("ECHART_LINK");
        if (str == null) {
            return "";
        }
        try {
            String[] httpLink = str.split("\\|");
            return "<a target=\"_blank\" href=\"" + httpLink[1] + "\">" + httpLink[0] + "</a>";
        } catch (Exception e) {
            MiscUtils.getLogger().error("ECHART_LINK is not in the correct format. title|url :" + str, e);
        }
        return "";
    }
%>
