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
  Purpose: Render the patient identity and utility links above the encounter.
  Features: Identity copying, calculator navigation and configured chart links.
  The calculator menu resolves clinical defaults server-side using the originating
  chart's record reference instead of including age or sex in the header URL, and
  there is exactly one entry point to it -- a second link to the same page, differing
  only in how it passed the patient's attributes, was reported as a duplicate control.
  Parameters: EctSessionBean and the authenticated session supply the encounter
  and provider context; there are no direct request parameters for this fragment.
  i18n: every label here resolves against the BROWSER locale. JSTL and the Java-rendered
  identity block both receive the negotiated bundle locale explicitly (see Demographic#getStandardIdentificationHtml) because the JVM default
  and LocaleContextHolder both report the server's language on this request path. The
  negotiated language is published as the lang attribute of #header-top-row (and of the chart
  page's <html>, see newEncounterLayout.jsp) so a rendered page states which language the
  server resolved for that request; LocaleUtils logs the same at DEBUG with the raw
  Accept-Language it came from.
  @since 2026-09-17
--%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setLocale value="<%= LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo, io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ page import="java.util.Locale" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect(request.getContextPath() + "/casemgmt/ViewError");
        return;
    }

    Facility facility = loggedInInfo.getCurrentFacility();
    String demoNo = bean.demographicNo;
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographicWithExt(loggedInInfo, Integer.parseInt(demoNo));

    // this is accessed in the newEncounterLayout after this header is included.
    String privateConsentEnabledProperty = CarlosProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");
    String popupPatientSex = bean == null || bean.patientSex == null ? "" : bean.patientSex;
    String popupPatientAge = demographic == null ? "" : String.valueOf(demographic.getAge());
    pageContext.setAttribute("popupPatientSex", popupPatientSex);
    pageContext.setAttribute("popupPatientAge", popupPatientAge);
    pageContext.setAttribute("popupDemographicNo", demoNo);

    // Same resolution the <fmt:message> tags below perform, so the Java-rendered identity
    // block and the JSP-rendered labels can never end up in two different languages.
    Locale browserLocale = LocaleUtils.resolveBundleLocale(request);
    // Published on the header container as its lang attribute: correct HTML for a fragment whose
    // language is negotiated per request, and the one place a field report can show which
    // language the SERVER chose for that very render (a screenshot of the text alone cannot).
    pageContext.setAttribute("negotiatedLanguageTag", browserLocale.toLanguageTag());
%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<fmt:message key="global.copiedToClipboard" var="copiedToClipboardMsg"/>
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

<div id="header-top-row" lang="${carlos:forHtmlAttribute(negotiatedLanguageTag)}">
    <div id="left-column">
        <div id="branding-logo">
            <img alt="CARLOS EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19px">
        </div>
        <%= demographic.getStandardIdentificationHtml(request.getContextPath(), browserLocale) %>
    </div>
    <div id="right-column">
    </div>
</div>

<div id="header-bottom-row">
    <%-- The chart's single entry point to the clinical calculators. A second anchor to the
         same route used to sit lower in this bar, labelled identically and differing only in
         passing sex/age in the query string; phc007 reported the pair as two calculator links
         in the header. This one is the form to keep: calculators.jsp resolves sex and age from
         the record when given demo=, which keeps patient attributes out of navigation URLs
         (asserted by scripts/admin-fragment-navigation.test.js).
         The href is the real route, not javascript:void(0): the click opens the popup and
         returns false, so a plain click behaves like the other header popups, while a
         middle-click or keyboard follow still reaches the page (and the anchor is a link, not a
         button dressed as one -- Sonar S6844). --%>
    <div>
        <fmt:message key="encounter.Index.calculators" var="calculatorsTitle"/>
        <a href="${carlos:forHtmlAttribute(ctx)}/encounter/ViewCalculators?demo=${carlos:forUriComponent(popupDemographicNo)}"
           id="chartCalculatorsLink"
           title="${carlos:forHtmlAttribute(calculatorsTitle)}"
           onclick="window.open('${carlos:forJavaScriptAttribute(ctx)}/encounter/ViewCalculators?demo=${carlos:forUriComponent(popupDemographicNo)}', 'ClinicalCalculators', 'width=800,height=650,scrollbars=yes,resizable=yes'); return false;"><fmt:message key="encounter.Index.calculators"/></a>
    </div>
    <% if (CarlosProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
        <div>
        <a href="javascript:void(0);" onClick="popupPage(600,175,'Calculators','${carlos:forJavaScript(ctx)}/commons/omdDiseaseList.jsp?sex=${carlos:forUriComponent(popupPatientSex)}&age=${carlos:forUriComponent(popupPatientAge)}'); return false;"><fmt:message key="encounter.Header.OntMD"/></a>
    </div>
    <%}%>

    <div>
        <%=getEChartLinks() %>
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
