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

--%>


<%@page import="io.github.carlos_emr.Misc" %>
<%@page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@include file="/casemgmt/taglibs.jsp" %>
<%@taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@page import="java.util.Enumeration" %>
<%@page import="io.github.carlos_emr.carlos.encounter.pageUtil.NavBarDisplayDAO" %>
<%@page import="java.util.Arrays,java.util.Properties,java.util.List,java.util.Set,java.util.ArrayList,java.util.Enumeration,java.util.HashSet,java.util.Iterator,java.text.SimpleDateFormat,java.util.Calendar,java.util.Date,java.text.ParseException" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty,io.github.carlos_emr.carlos.casemgmt.model.*,io.github.carlos_emr.carlos.casemgmt.service.* " %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.common.Colour" %>
<%@page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="com.quatro.dao.security.*,io.github.carlos_emr.carlos.model.security.Secrole" %>
<%@page import="io.github.carlos_emr.carlos.utility.EncounterUtil" %>
<%@page import="org.apache.cxf.common.i18n.UncheckedException" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@page import="io.github.carlos_emr.OscarProperties" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.NoteDisplayNonNote" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.CheckBoxBean" %>

<% java.util.Properties oscarVariables = OscarProperties.getInstance(); %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<div id="cppBoxes">
    <div id="divR1">
        <!-- social history -->
        <div id="divR1I1" class="topBox">
        </div>

        <div id="divR1I2" class="topBox">

        </div>
    </div>

    <div id="divR2">
        <!--Ongoing Concerns cell -->
        <div id="divR2I1" class="topBox">
        </div>
        <!--Reminders cell -->
        <div id="divR2I2" class="topBox">
        </div>
    </div>

</div>

<div id="notCPP">

</div>