<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
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
<%@ include file="/taglibs.jsp" %>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs"><fmt:message key="pmmodule.admin.general.information"/></th>
        </tr>
    </table>
</div>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.name"/></td>
        <td>${carlos:forHtml(program.name)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.facility"/></td>
        <td>${carlos:forHtml(facilityName)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.description"/></td>
        <td>${carlos:forHtml(program.description)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.hic"/></td>
        <td>${carlos:forHtml(program.hic)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.type"/></td>
        <td>${carlos:forHtml(program.type)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.location"/></td>
        <td>${carlos:forHtml(program.location)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.client.participation"/></td>
        <td>${carlos:forHtml(program.numOfMembers)}/${carlos:forHtml(program.maxAllowed)} (${carlos:forHtml(program.queueSize)} waiting)</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.holding.tank"/></td>
        <td>${carlos:forHtml(program.holdingTank)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.allow.batch.admissions"/></td>
        <td>${carlos:forHtml(program.allowBatchAdmission)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.allow.batch.discharges"/></td>
        <td>${carlos:forHtml(program.allowBatchDischarge)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.man.or.woman.1"/></td>
        <td>${carlos:forHtml(program.manOrWoman)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.transgender"/></td>
        <td>${carlos:forHtml(program.transgender)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.first.nation"/></td>
        <td>${carlos:forHtml(program.firstNation)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.alcohol"/></td>
        <td>${carlos:forHtml(program.alcohol)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Abstinence Support?</td>
        <td>${carlos:forHtml(program.abstinenceSupport)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.physical.health"/></td>
        <td>${carlos:forHtml(program.physicalHealth)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.mental.health"/></td>
        <td>${carlos:forHtml(program.mentalHealth)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.housing"/></td>
        <td>${carlos:forHtml(program.housing)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.exclusive.view"/></td>
        <td>${carlos:forHtml(program.exclusiveView)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.minimum.age"/></td>
        <td>${carlos:forHtml(program.ageMin)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.maximum.age"/></td>
        <td>${carlos:forHtml(program.ageMax)}</td>
    </tr>
</table>
