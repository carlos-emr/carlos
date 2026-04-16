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
            <th title="Programs">General Information</th>
        </tr>
    </table>
</div>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%">Name:</td>
        <td>${e:forHtml(program.name)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Facility:</td>
        <td>${e:forHtml(facilityName)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Description:</td>
        <td>${e:forHtml(program.description)}</td>
    </tr>
    <tr class="b">
        <td width="20%">HIC:</td>
        <td>${e:forHtml(program.hic)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Type:</td>
        <td>${e:forHtml(program.type)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Location:</td>
        <td>${e:forHtml(program.location)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Client Participation:</td>
        <td>${e:forHtml(program.numOfMembers)}/${e:forHtml(program.maxAllowed)} (${e:forHtml(program.queueSize)} waiting)</td>
    </tr>
    <tr class="b">
        <td width="20%">Holding Tank:</td>
        <td>${e:forHtml(program.holdingTank)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Allow Batch Admissions:</td>
        <td>${e:forHtml(program.allowBatchAdmission)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Allow Batch Discharges:</td>
        <td>${e:forHtml(program.allowBatchDischarge)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Man Or Woman:</td>
        <td>${e:forHtml(program.manOrWoman)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Transgender:</td>
        <td>${e:forHtml(program.transgender)}</td>
    </tr>
    <tr class="b">
        <td width="20%">First Nation:</td>
        <td>${e:forHtml(program.firstNation)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Alcohol:</td>
        <td>${e:forHtml(program.alcohol)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Abstinence Support?</td>
        <td>${e:forHtml(program.abstinenceSupport)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Physical Health:</td>
        <td>${e:forHtml(program.physicalHealth)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Mental Health:</td>
        <td>${e:forHtml(program.mentalHealth)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Housing:</td>
        <td>${e:forHtml(program.housing)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Exclusive View:</td>
        <td>${e:forHtml(program.exclusiveView)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Minimum Age:</td>
        <td>${e:forHtml(program.ageMin)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Maximum Age:</td>
        <td>${e:forHtml(program.ageMax)}</td>
    </tr>
</table>
