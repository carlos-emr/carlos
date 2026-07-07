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
  newEncounterHeader.jsp is a fragment 
  loaded from newEncounterLayout.jsp
  the new is misleading as the code is circa
  @since 2008
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

<%@ page import="java.util.Date" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

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

%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<script type="text/javascript">
function copyToClip(text, el) {
    function showFeedback() {
        el.style.opacity = '0.5';
        setTimeout(function() { el.style.opacity = '1'; }, 600);
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
<div id="header-top-row">
   <div id="left-column">
      <div id="branding-logo">
         <img alt="CARLOS EMR" src="${ctx}/images/oscar_logo_small.png" width="19px">
      </div>    
      <div id="patient-label">
         <div id="patient-full-name">
            <h1><a href="${ctx}/demographic/DemographicEdit?demographic_no=<%=demoNo%>" target="_blank">
            <carlos:encode value='<%=(demographic.getTitle() != null && demographic.getTitle().length() > 0) ? demographic.getTitle() + " " : ""%>' context="html"/>
            <carlos:encode value='<%=demographic.getFormattedName()%>' context="html"/></a></h1>
         </div>
      <c:if test="<%=(demographic.getPronoun() != null && !demographic.getPronoun().isEmpty())%>">
         <div id="patient-pronouns">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/>
            </div>
            <carlos:encode value='<%= demographic.getPronoun() %>' context="html"/>
         </div>
      </c:if> 
         <div id="patient-sex">
            <div class="label">
               <fmt:message key="demographic.demographicaddrecordhtm.formSex"/>
            </div>
            <carlos:encode value='<%=demographic.getSex()%>' context="html"/>
         </div>
      <c:if test="<%=(demographic.getGender() != null && !demographic.getGender().isEmpty())%>">
         <div id="patient-gender">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formGender"/>
            </div>
            <carlos:encode value='<%= demographic.getGender() %>' context="html"/>
         </div>
      </c:if>     
         <div id="patient-dob">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formDOB"/>
            </div>
            <carlos:encode value='<%=demographic.getBirthDayAsString()%>' context="html"/>
         </div>
         <div id="patient-age">
            <div class="label">
              <fmt:message key="global.age"/>
            </div>
            <carlos:encode value='<%=demographic.getAgeAsOf(new Date(), request.getLocale())%>' context="html"/>
         </div>
      <c:if test="<%=(demographic.getHin() != null && !demographic.getHin().isEmpty())%>">
         <div id="patient-hin" class="copyable" onclick="copyToClip('<carlos:encode value='<%= demographic.getHin() %>' context="javaScriptAttribute"/>',this)">
            <div class="label">
              <fmt:message key="demographic.patient.context.hin"/>
            </div>
            (<carlos:encode value='<%= demographic.getHcType() %>' context="html"/>)
            <carlos:encode value='<%= demographic.getHin() %>' context="html"/>&nbsp;
            <carlos:encode value='<%= demographic.getVer() %>' context="html"/>
         </div>
      </c:if>
      <c:if test="<%=(demographic.getPhone() != null && !demographic.getPhone().isEmpty())%>">
         <div id="patient-phone" class="copyable" title="" onclick="copyToClip('<carlos:encode value='<%= demographic.getPhone() %>' context="javaScriptAttribute"/>',this)">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formPhone"/>
            </div>
            <carlos:encode value='<%= demographic.getPhone() %>' context="html"/>
         </div>
      </c:if>
      <c:if test="<%=(demographic.getCellPhone() != null && !demographic.getCellPhone().isEmpty())%>">
         <div id="patient-cell-phone" class="copyable" title="" onclick="copyToClip('<carlos:encode value='<%= demographic.getCellPhone() %>' context="javaScriptAttribute"/>',this)">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formPhoneCell"/>
            </div>
            <carlos:encode value='<%= demographic.getCellPhone() %>' context="html"/>
         </div>
      </c:if>
      <c:if test="<%=(demographic.getEmail() != null && !demographic.getEmail().isEmpty())%>">
         <div id="patient-email" class="copyable" onclick="copyToClip('<carlos:encode value='<%= demographic.getEmail() %>' context="javaScriptAttribute"/>',this)">
            <div class="label">
              <fmt:message key="demographic.demographicaddrecordhtm.formEMail"/>
            </div>
            <carlos:encode value='<%= demographic.getEmail() %>' context="html"/>
         </div>
      </c:if>
         <div id="patient-next-appointment">
            <div class="label"><a href="${ctx}/demographic/DemographicApptHistory?demographic_no=<%=demoNo%>&amp;orderby=appointment_date&amp;dboperation=appt_history&amp;limit1=0&amp;limit2=25" title="<fmt:message key="eform.groups.page.viewAll"/>" target="_blank">
              <fmt:message key="global.nextAppointment"/></a></div>
              <c:choose>
                <c:when test="<%=(demographic.getNextAppointment() != null && !demographic.getNextAppointment().isEmpty())%>">
                  <carlos:encode value='<%= demographic.getNextAppointment() %>' context="html"/>
                </c:when>
                <c:otherwise>
                  <fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/>
                </c:otherwise>
              </c:choose>

         </div>
         <div id="patient-mrp">
            <div class="label">
              <fmt:message key="demographic.demographiceditdemographic.formMRP"/>
            </div>    
              <c:choose>
                <c:when test="<%=(demographic.getMrp() != null)%>">
                  <carlos:encode value='<%= demographic.getMrp().getFormattedName() %>' context="html"/>
                </c:when>
                <c:otherwise>
                  <fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/>
                </c:otherwise>
              </c:choose>
         </div>
      </div>
   </div>
   <div id="right-column">
   </div>
</div>
<div id="header-bottom-row">
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
