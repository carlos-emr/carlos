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

--%>

<%@page import="ca.openosp.OscarProperties" %>
<%@page import="java.util.*" %>
<%@page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    RxSessionBean bean2 = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
    RxPrescriptionData.Prescription[] allRxInStash = bean2.getStash();
    List allRandomIdInStash = new ArrayList();
    for (RxPrescriptionData.Prescription rx : allRxInStash) {
        allRandomIdInStash.add(rx.getRandomId());
    }
    String interactingDrugList = bean2.getInteractingDrugList();


%>

<%if (OscarProperties.getInstance().getProperty("rx_enhance") != null && OscarProperties.getInstance().getProperty("rx_enhance").equals("true")) { %>
<script type="text/javascript">

    var errorMsg = "Failed";
    //oscarLog("errorMsg="+errorMsg);
    var interactStr = '<%=Encode.forJavaScript(interactingDrugList != null ? interactingDrugList : "")%>';
    var randomIds = '<%=Encode.forJavaScript(allRandomIdInStash != null ? allRandomIdInStash.toString() : "")%>';
    //clear all warnings - remove all bracket characters
    randomIds = randomIds.replace(/[\[\]]/g, "");
    if (randomIds.length > 0) {
        var randomIdArr = randomIds.split(",");
        for (var h = 0; h < randomIdArr.length; h++) {
            var randId = randomIdArr[h];
            randId = randId.replace(/\s/g, "");//trim

            if (document.getElementById('major_' + randId) != null &&
                document.getElementById('moderate_' + randId) != null &&
                document.getElementById('minor_' + randId) != null &&
                document.getElementById('unknown_' + randId) != null) {
                document.getElementById('major_' + randId).style.display = "none";
                document.getElementById('moderate_' + randId).style.display = "none";
                document.getElementById('major_' + randId).innerHTML = "";
                document.getElementById('moderate_' + randId).innerHTML = "";
                document.getElementById('minor_' + randId).style.display = "none";
                document.getElementById('minor_' + randId).innerHTML = "";
                document.getElementById('unknown_' + randId).style.display = "none";
                document.getElementById('unknown_' + randId).innerHTML = "";
            }
        }
    }


    if (interactStr.length > 0) {
        if (interactStr == errorMsg) {
            document.getElementById("interactingDrugErrorMsg").style.display = "inline";
            document.getElementById("interactingDrugErrorMsg").innerHTML = "<span style='color:red'>" + errorMsg + "</span>";
        } else {
            document.getElementById("interactingDrugErrorMsg").style.display = "none";
            document.getElementById("interactingDrugErrorMsg").innerHTML = "";
            var arr1 = interactStr.split(",");
            for (var i = 0; i < arr1.length; i++) {
                var str = arr1[i];
                var arr2 = str.split("=");
                var id = arr2[0];
                var idArr = arr2[0].split("_");
                var significance = idArr[0].toUpperCase();
                var title = arr2[1];
                var interactionData = title.split("|");
                var effectStr = interactionData[0];
                var evidenceStr = interactionData[1];
                var drugName = interactionData[2];
                var htmlStr = "<span title='" + (drugName ? drugName.replace(/'/g, "&#39;").replace(/"/g, "&quot;") : "") + "'><strong>" + significance + "</strong>: " + getEffect(effectStr) + ": " + (drugName ? drugName.replace(/</g, "&lt;").replace(/>/g, "&gt;") : "") + " " + getEvidence(evidenceStr) + "</span>";
                id = id.replace(/\s/g, "");
                if (document.getElementById(id) != null) {
                    document.getElementById(id).style.display = "inline";
                    document.getElementById(id).update(htmlStr);
                }
            }
        }
    } else {
        document.getElementById("interactingDrugErrorMsg").style.display = "none";
        document.getElementById("interactingDrugErrorMsg").innerHTML = "";
    }


    function getEffect(str) {
        switch (str) {
            case "a":
                return "Augments (no clinical effect)";
            case "A":
                return "Augments";
            case "i":
                return "Inhibits (no clinical effect)";
            case "I":
                return "Inhibits";
            case "n":
            case "N":
                return "No Effect";
            default:
                return "Unknown";
        }
    }

    function getEvidence(str) {
        switch (str) {
            case "P":
                return "Poor";
            case "F":
                return "Fair";
            case "G":
                return "Good";
            default:
                return "Unknown";
        }
    }
</script>

<%} else {%>
<script type="text/javascript">

    var errorMsg = "Failed";
    //oscarLog("errorMsg="+errorMsg);
    var interactStr = '<%=Encode.forJavaScript(interactingDrugList != null ? interactingDrugList : "")%>';
    var randomIds = '<%=Encode.forJavaScript(allRandomIdInStash != null ? allRandomIdInStash.toString() : "")%>';
    //clear all warnings - remove all bracket characters
    randomIds = randomIds.replace(/[\[\]]/g, "");
    if (randomIds.length > 0) {
        var randomIdArr = randomIds.split(",");
        for (var h = 0; h < randomIdArr.length; h++) {
            var randId = randomIdArr[h];
            randId = randId.replace(/\s/g, "");//trim
            $('major_' + randId).hide();
            $('major_' + randId).update("");
            $('moderate_' + randId).hide();
            $('moderate_' + randId).update("");
            $('minor_' + randId).hide();
            $('minor_' + randId).update("");
            $('unknown_' + randId).hide();
            $('unknown_' + randId).update("");
        }
    }


    if (interactStr.length > 0) {
        if (interactStr == errorMsg) {
            $("interactingDrugErrorMsg").show();
            $("interactingDrugErrorMsg").update("<span style='color:red'>" + errorMsg + "</span>");
        } else {
            $("interactingDrugErrorMsg").hide();
            $("interactingDrugErrorMsg").update("");
            var arr1 = interactStr.split(",");
            for (var i = 0; i < arr1.length; i++) {
                var str = arr1[i];
                var arr2 = str.split("=");
                var id = arr2[0];
                var title = arr2[1];
                var htmlStr = "<a title='" + (title ? title.replace(/'/g, "&#39;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;") : "") + "'>&nbsp;&nbsp;</a>";
                id = id.replace(/\s/g, "");
                $(id).show();
                $(id).update(htmlStr);
            }
        }
    } else {
        $("interactingDrugErrorMsg").hide();
        $("interactingDrugErrorMsg").update("");
    }

</script>
<%} %>
