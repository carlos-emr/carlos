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
<%@ page
        import="ca.openosp.openo.providers.data.*,ca.openosp.OscarProperties, ca.openosp.openo.clinic.ClinicData, java.util.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="ca.openosp.openo.utility.DigitalSignatureUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.ui.servlet.ImageRenderingServlet" %>
<%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>


<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="ca.openosp.openo.managers.FaxManager" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.PMmodule.service.ProviderManager" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.providers.data.ProviderData" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="ca.openosp.openo.commn.model.enumerator.ModuleType" %>
<%@ page import="ca.openosp.openo.providers.data.ProSignatureData" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxProviderData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>

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
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.title"/></title>

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
                createAnewRx = "window.location.href = '" + request.getContextPath() + "/oscarRx/SearchDrug.jsp'";
            } else {
                createAnewRx = "javascript:clearPending('')";
            }

// for satellite clinics
            Vector vecAddressName = null;
            Vector vecAddress = null;
            Vector vecAddressPhone = null;
            Vector vecAddressFax = null;
            OscarProperties props = OscarProperties.getInstance();
            if (bMultisites) {
                String appt_no = (String) session.getAttribute("cur_appointment_no");
                String location = null;
                if (appt_no != null) {
                    Appointment result = appointmentDao.find(Integer.parseInt(appt_no));
                    if (result != null) location = result.getLocation();
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
                doctorName = doctorName.replaceAll("\\d{6}", "");
                doctorName = doctorName.replaceAll("\\-", "");

                vecAddressName = new Vector();
                vecAddress = new Vector();
                vecAddressPhone = new Vector();
                vecAddressFax = new Vector();

                java.util.ResourceBundle rb = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));

                for (int i = 0; i < sites.size(); i++) {
                    Site s = sites.get(i);
                    vecAddressName.add(s.getName());
                    vecAddress.add("<b>" + doctorName + "</b><br>" + s.getName() + "<br>" + s.getAddress() + "<br>" + s.getCity() + ", " + s.getProvince() + " " + s.getPostal() + "<br>" + rb.getString("RxPreview.msgTel") + ": " + s.getPhone() + "<br>" + rb.getString("RxPreview.msgFax") + ": " + s.getFax());
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

                for (int i = 0; i < temp0.length; i++) {
                    vecAddressName.add(temp0[i]);
                    vecAddress.add("<b>" + Encode.forHtml(doctorName) + "</b><br>" + Encode.forHtml(temp0[i]) + "<br>" + Encode.forHtml(temp1[i]) + "<br>" + temp2[i] + ", " + temp3[i] + " " + temp4[i] + "<br>" + rb.getString("RxPreview.msgTel") + ": " + temp5[i] + "<br>" + rb.getString("RxPreview.msgFax") + ": " + temp6[i]);
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
                    prefPharmacy = pharmacy.getName().replace("'", "\\'");
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
            <%--<link rel="stylesheet" type="text/css" href="styles.css" />--%>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <script type="text/javascript">
            function resetStash() {
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=clearStash";
                var data = "";
                new Ajax.Request(url, {
                    method: 'post', parameters: data, onSuccess: function (transport) {
                        parent.document.getElementById('rxText').innerHTML = "";//make pending prescriptions disappear.
                        parent.document.getElementById('searchString').focus();
                    }
                });
            }

            function resetReRxDrugList() {
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=clearReRxDrugList";
                var data = "";
                new Ajax.Request(url, {
                    method: 'post', parameters: data
                });
            }


            function onPrint2(method, scriptId) {
                var useSC = false;
                var scAddress = "";
                var rxPageSize = $('printPageSize').value;
                console.log("rxPagesize  " + rxPageSize);

                <% if(vecAddressName != null) { %>
                useSC = true;
                <%for(int i=0; i<vecAddressName.size(); i++) {%>
                if (document.getElementById("addressSel").value == "<%=i%>") {
                    scAddress = "<%=Encode.forUriComponent(StringEscapeUtils.unescapeHtml4((String)vecAddress.get(i)))%>";
                }
                <%}
            }%>
                let action = "<%= request.getContextPath() %>/form/createcustomedpdf?__title=Rx&__method=" + method + "&useSC=" + useSC + "&scAddress=" + scAddress + "&rxPageSize=" + rxPageSize + "&scriptId=" + scriptId;
                document.getElementById("preview").contentWindow.document.getElementById("preview2Form").action = action;
                if (method !== "oscarRxFax") {
                    document.getElementById("preview").contentWindow.document.getElementById("preview2Form").target = "_blank";
                }
                document.getElementById("preview").contentWindow.document.getElementById("preview2Form").submit();

                return true;
            }

            function setComment() {
                frames['preview'].document.getElementById('additNotes').innerHTML = '<%=Encode.forJavaScript(comment.replaceAll("\n", "<br>"))%>';
                frames['preview'].document.getElementsByName('additNotes')[0].value = frames['preview'].document.getElementById('additNotes').innerHTML;
            }

            function setDefaultAddr() {
                var url = '<c:out value="${ctx}"/>/oscarRx/setDefaultAddr.jsp';
                var ran_number = Math.round(Math.random() * 1000000);
                var addr = encodeURIComponent(document.getElementById('addressSel').value);
                var params = "addr=" + addr + "&rand=" + ran_number;
                new Ajax.Request(url, {method: 'post', parameters: params});
            }



            function addNotes() {

                var url = '<c:out value="${ctx}"/>/oscarRx/AddRxComment.jsp';
                var ran_number = Math.round(Math.random() * 1000000);
                var comment = encodeURIComponent(document.getElementById('additionalNotes').value);
                var params = "scriptNo=<%=request.getAttribute("scriptId")%>&comment=" + comment + "&rand=" + ran_number;  //]
                new Ajax.Request(url, {method: 'post', parameters: params});
                frames['preview'].document.getElementById('additNotes').innerHTML = document.getElementById('additionalNotes').value.replace(/\n/g, "<br>");
                frames['preview'].document.getElementsByName('additNotes')[0].value = document.getElementById('additionalNotes').value.replace(/\n/g, "\r\n");
            }


            function printIframe() {
                var browserName = navigator.appName;
                if (browserName == "Microsoft Internet Explorer") {
                    alert("Use of Microsoft Internet Explorer is not permitted")
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

            function printPaste2Parent(print, fax, pasteRx) {
                //console.log("in printPaste2Parent");
                try {
                    text = "";
                    <% if (props.isPropertyActive("rx_paste_asterisk")) { %>
                    text += "**********************************************************************************\n";
                    <% } %>

                    if (print) {
                        text += "Prescribed and printed by <%= Encode.forJavaScript(loggedInInfo.getLoggedInProvider().getFormattedName())%>\n";
                    } else if (fax) {
                        <%--    	 <% if(echartPreferencesMap.getOrDefault("echart_paste_fax_note", false)) {--%>
                        <% String timeStamp = new SimpleDateFormat("dd-MMM-yyyy hh:mm a").format(Calendar.getInstance().getTime()); %>
                        // %>
                        text = "[Rx faxed to " + '<%= pharmacy!=null?StringEscapeUtils.escapeEcmaScript(pharmacy.getName()):""%>' + " Fax#: " + '<%= pharmacy!=null?pharmacy.getFax():""%>';

                        <%--    	 <% if (rxPreferencesMap.getOrDefault("rx_paste_provider_to_echart", false)) { %>--%>
                        text += " prescribed by <%= Encode.forJavaScript(loggedInInfo.getLoggedInProvider().getFormattedName())%>";
                        <%--    	 <% } %>--%>
                        text += ", <%= timeStamp %>]\n";
                        <%--   		 <%--%>
                        <%--    	 }--%>
                        <%--    	 %>    	--%>
                    }

                    if (pasteRx) {
                        if (document.all) {
                            text += preview.document.forms[0].rx_no_newlines.value
                        } else {
                            text += preview.document.forms[0].rx_no_newlines.value + "\n";
                        }

                        if (document.getElementById('additionalNotes') !== null) {
                            text += document.getElementById('additionalNotes').value + "\n";
                        }
                    }
                    <% if (props.isPropertyActive("rx_paste_asterisk")) {
                            if(prefPharmacy!=null && prefPharmacy.trim()!=""){ %>
                    text += "<%=prefPharmacy%>\n"
                    <% } %>
                    text += "****<%=Encode.forJavaScript(ProviderData.getProviderName(bean.getProviderNo()))%>********************************************************************************\n";
                    <% } %>

                    //we support pasting into orig encounter and new casemanagement
                    demographicNo = <%=bean.getDemographicNo()%>;
                    noteEditor = "noteEditor" + demographicNo;
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
                            writeToEncounter(print, text);
                        }
                    } else {
                        writeToEncounter(print, text);
                    }

                } catch (e) {
                    alert("ERROR: could not paste to EMR" + e);
                    if (print) {
                        printIframe();
                    }
                }

            }

	function writeToEncounter(print, text) {
    	try {
			var url = "<%=request.getContextPath() %>/oscarRx/WriteToEncounter.do";
			var prefPharmacy = "<%=prefPharmacy != null ? Encode.forJavaScriptBlock(prefPharmacy) : ""%>";
			new Ajax.Request(url, {method: 'post',
				parameters: "prefPharmacy=" + encodeURIComponent(prefPharmacy) +
						"&additionalNotes=" +
						"&body="+ encodeURIComponent(text),
				onSuccess:function(ret){
					//console.log("success")
					if (print) {
						printIframe();
					}
					openEncounter();
				},
				onError: function(e) {
					alert("ERROR: could not paste to EMR" + e);
					if (print) {
						printIframe();
					}
					openEncounter();
				}});
		} catch (e) {
			alert("ERROR: could not paste to EMR" + e);
		}
	}

            function openEncounter() {
                var windowprops = "height=710,width=1024,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=20,left=20";
                var currentDate = new Date().toISOString().substring(0, 10);
                var url = "<%= request.getContextPath() %>/oscarEncounter/IncomingEncounter.do?providerNo=<%= bean.getProviderNo() %>&demographicNo=<%= bean.getDemographicNo() %>&curProviderNo=<%= bean.getProviderNo() %>&userName=<%=Encode.forUriComponent(ProviderData.getProviderName(bean.getProviderNo()))%>&curDate=" + currentDate;

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
                    frames['preview'].document.getElementById("clinicAddress").innerHTML = "<%=vecAddress.get(i)%>";
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
        %>
        <script type="text/javascript">
            var POLL_TIME = 1500;
            var counter = 0;
            var isRxFaxEnabled = "<%=OscarProperties.getInstance().isRxFaxEnabled()%>";

            function refreshImage() {
                counter = counter + 1;
                if (frames["preview"].document.getElementById("signature") != null) {
                    frames["preview"].document.getElementById("signature").src = "<%=imageUrl%>&rand=" + counter;
                }
                frames['preview'].document.getElementById('imgFile').value = '<%=System.getProperty("java.io.tmpdir").replaceAll("\\\\", "/")%>/signature_<%=signatureRequestId%>.jpg';
            }

            function sendFax() {
                if ('function' === typeof window.onbeforeunload) {
                    window.onbeforeunload = null;
                }
                let faxNumber = document.getElementById('faxNumber');
                frames['preview'].document.getElementById('finalFax').value = faxNumber.options[faxNumber.selectedIndex].value;
                frames['preview'].document.getElementById('pdfId').value = '<%=signatureRequestId%>';
                onPrint2('oscarRxFax', "<%=request.getParameter("scriptId")%>");

            }

            function unloadMess() {
                mess = "Signature found, but fax has not been sent.";
                if (isSignatureDirty) {
                    mess = "Signature changed, but not saved and fax not sent.";
                }
                return mess;
            }

            var isSignatureDirty = false;
            var isSignatureSaved = false;
            <% if (OscarProperties.getInstance().isRxFaxEnabled()) { %>
            var hasFaxNumber = <%= pharmacy != null && pharmacy.getFax() != null && pharmacy.getFax().trim().length() > 0 ? "true" : "false" %>;
            <% } %>

            function signatureHandler(e) {
                isSignatureDirty = e.isDirty;
                isSignatureSaved = e.isSave;
                e.target.onbeforeunload = null;
                <% if (OscarProperties.getInstance().isRxFaxEnabled()) { //%>
                let disabled = !hasFaxNumber || !e.isSave;
                toggleFaxButtons(disabled);
                <% } %>
                if (e.isSave) {
                    <% if (OscarProperties.getInstance().isRxFaxEnabled()) { //%>
                    if (hasFaxNumber) {
                        e.target.onbeforeunload = unloadMess;
                    }
		<% }

		if (bean.getStashSize() > 0) {
		%>
		try {
			let signId = new URLSearchParams(e.storedImageUrl.split('?')[1]).get('digitalSignatureId')
			this.setDigitalSignatureToRx(signId, <%=bean.getStashItem(0).getScript_no() %>);
		} catch (e) {
			console.error(e);
		}
                    <% } %>

                    refreshImage();
                }
            }

function setDigitalSignatureToRx(digitalSignatureId, scriptId) {
	new Ajax.Request('<%=request.getContextPath() %>/oscarRx/saveDigitalSignature.do?method=saveDigitalSignature'
			+ '&digitalSignatureId=' + digitalSignatureId
			+ "&scriptId=" + scriptId, {
		method: 'get'
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
							src="oscarRx/Preview2.jsp?scriptId=<%=bean.getStashItem(0).getScript_no()%>&rePrint=<%=reprint%>&pharmacyId=<%=request.getParameter("pharmacyId")%>"
							align=center border=0 frameborder=0></iframe></div>
					<% } %>
                                </td>

                                <td valign=top><form action="${pageContext.request.contextPath}/oscarRx/clearPending.do" method="post">
                                    <input type="hidden" name="action" id="action" value=""/>
                                    <div class="warning-note" id="faxWarningNote">
                                        <strong>Warning:</strong> faxing is disabled because no pharmacy fax number is
                                        available.</br></br>To enable faxing, close this window and select a pharmacy
                                        with a fax number before trying again.
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
                                            parent.window.location = "<%= request.getContextPath() %>/oscarRx/close.html";
                                            parent.myLightWindow.deactivate();
                                        }

                                        function ShowDrugInfo(drug) {
                                            window.open('<c:out value="${ctx}"/>/oscarRx/drugInfo.do?GN=' + encodeURIComponent(drug), "_blank",
                                                "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
                                        }


                                function printPharmacy(id){
                                    //ajax call to get all info about a pharmacy
                                    //use json to write to html
	                                if(! id) {
										return;
	                                }
                                    var url="<c:out value="${ctx}"/>"+"/oscarRx/managePharmacy2.do?";
                                    var data="method=getPharmacyInfo&pharmacyId="+id;
                                    new Ajax.Request(url, {method: 'get',parameters:data, onSuccess:function(transport){
                                        var json=transport.responseText.evalJSON();

                                                    if (json != null) {
                                                        var text = json.name + "<br>" + json.address + "<br>" + json.city + ", " + json.province + ", "
                                                            + json.postalCode + "<br>Tel:" + json.phone1 + " " + json.phone2 + "<br>Fax:" + json.fax + "<br>Email:" + json.email + "<br>Note:" + json.notes;

                                                        text += '<br><br><a class="noprint" style="text-align:center;" onclick="parent.reducePreview();" href="javascript:void(0);">Remove Pharmacy Info</a>';
                                                        text += "<input type='hidden' name='pharmacyInfo' value=" + id + " />"
                                                        expandPreview(text);
                                                    }
                                                }
                                            });

                                        }

                                        function expandPreview(text) {
                                            parent.document.getElementById('lightwindow_container').style.width = "1140px";
                                            parent.document.getElementById('lightwindow_contents').style.width = "1120px";
                                            document.getElementById('preview').style.width = "600px";
                                            frames['preview'].document.getElementById('pharmInfo').innerHTML = text;
                                            $("selectedPharmacy").innerHTML = '<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.printPharmacyInfo.paperSizeWarning"/>';
                                        }

                                        function reducePreview() {
                                            parent.document.getElementById('lightwindow_container').style.width = "1000px";
                                            parent.document.getElementById('lightwindow_contents').style.width = "980px";
                                            document.getElementById('preview').style.width = "460px";
                                            frames['preview'].document.getElementById('pharmInfo').innerHTML = "";
                                            $("selectedPharmacy").innerHTML = "";
                                        }
                                    </script>

                                    <table cellpadding=10 cellspacingp=0>
                                        <% //vecAddress=null;
                                            if (vecAddress != null) { %>
                                        <tr>
                                            <td align="left" colspan=2><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgAddress"/>
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
                                            <td colspan=2 style="font-weight: bold;"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgActions"/></span>
                                            </td>
                                        </tr>

                                        <tr>
                                            <!--td width=10px></td-->
                                            <td>Page size:
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
                                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgPrint"/>"
                                                                                       class="ControlPushButton"
                                                                                       style="width: 210px"
                                                                                       onClick="javascript:printIframe();"/></span>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding-top: 0"><span><input type=button
                                                    <%=reprint.equals("true") ? "disabled='true'" : ""%>
                                                                                    value="Print &amp; Add to encounter note"
                                                                                    class="ControlPushButton"
                                                                                    style="width: 210px"
                                                                                    onClick="printPaste2Parent(true, false, true);"/></span>
                                            </td>
                                        </tr>
                                        <% if (OscarProperties.getInstance().isRxFaxEnabled()) {
                                            FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
                                            List<FaxConfig> faxConfigs = faxManager.getFaxGatewayAccounts(loggedInInfo);
                                        %>
                                        <tr>
                                            <td style="padding-bottom: 0">
                                                <span>From Fax Number:</span>
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
						String isFaxDisabled = (bean.getStashSize() == 0 || Objects.isNull(bean.getStashItem(0).getDigitalSignatureId()))
								? "disabled" : "";
					%>
                                        <tr>
						<td style="padding-top: 0; padding-bottom: 0"><span><input type=button value="Fax"
										 class="ControlPushButton" id="faxButton" style="width: 210px"
										 onClick="sendFax();" <%=isFaxDisabled%>/></span>
                                            </td>
                                        </tr>
                                        <tr>
                            <td style="padding-top: 0"><span><input type=button value="Fax &amp; Add to encounter note"
                                    class="ControlPushButton" id="faxPasteButton" style="width: 210px"
                                    onClick="printPaste2Parent(false, true, true);sendFax();" <%=isFaxDisabled%>/></span>

                                            </td>
                                        </tr>

                                        <% } %>
                                        <tr>
                                            <td><span><input type=button
                                                             value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgCreateNewRx"/>"
                                                             class="ControlPushButton"
                                                             style="width: 210px"
                                                             onClick="resetStash();resetReRxDrugList();javascript:parent.myLightWindow.deactivate();"/></span>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td><span><input type=button
                                                             value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgBackToOscar"/>"
                                                             class="ControlPushButton" style="width: 210px"
                                                             onClick="javascript:clearPending('close');parent.window.close();"/></span>
                                            </td>
                                        </tr>
                                        <%
                                            if (request.getSession().getAttribute("rePrint") == null) {%>

                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgAddNotesRx"/></span></td>
                                        </tr>
                                        <tr>
                                            <!--td width=10px></td-->
                                            <td>
                                                <textarea id="additionalNotes" style="width: 200px"
                                                          onchange="javascript:addNotes();"></textarea>
                                                <input type="button" value="Additional Rx Notes"
                                                       onclick="javascript:addNotes();"/>
                                            </td>
                                        </tr>

                                        <%}%>
                                        <% if (OscarProperties.getInstance().isRxSignatureEnabled()) { %>
                                        <%-- Topaz signature pad check removed - HTML5 signature is now standard --%>
						<% if (bean.getStashSize() == 0 || Objects.isNull(bean.getStashItem(0).getDigitalSignatureId())) { %>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span>Signature</span></td>
                                        </tr>
                                        <tr>
                                            <td>
									<input type="hidden" name="<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>"
										   value="<%=signatureRequestId%>"/>
									<iframe style="width:500px; height:132px;" id="signatureFrame"
											src="<%= request.getContextPath() %>/signature_pad/tabletSignature.jsp?inWindow=true&<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>=<%=signatureRequestId%>&saveToDB=true&demographicNo=<%=bean.getDemographicNo()%>&<%=ModuleType.class.getSimpleName()%>=<%=ModuleType.PRESCRIPTION%>"></iframe>
                                            </td>
                                        </tr>
						<% } %>
                                        <%}%>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgDrugInfo"/></span></td>
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
