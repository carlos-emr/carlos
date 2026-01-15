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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="ca.openosp.openo.rx.data.*,java.util.*" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>
<%@ page import="ca.openosp.openo.commn.model.PharmacyInfo" %>

<%
    RxSessionBean bean = null;
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
    String surname = "", firstName = "";
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
<!DOCTYPE HTML>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <jsp:include page="/images/spinner.jsp" flush="true"/>

		<script src="${pageContext.request.contextPath}/csrfguard"></script>

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"
                type="text/javascript"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"
                type="text/javascript"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"
                type="text/javascript"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
              type="text/css"/>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/effects.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/lightwindow/javascript/lightwindow.js"></script>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/lightwindow/css/lightwindow.css">


        <c:if test="${empty RxSessionBean}">
            <% response.sendRedirect("error.html"); %>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
            <%
                // Directly access the RxSessionBean from the session
                bean = (RxSessionBean) session.getAttribute("RxSessionBean");
                if (bean != null && !bean.isValid()) {
                    response.sendRedirect("error.html");
                    return; // Ensure no further JSP processing
                }
                RxPatientData.Patient patient = (RxPatientData.Patient) request.getSession().getAttribute("Patient");
                if (patient != null) {
                    surname = patient.getSurname();
                    firstName = patient.getFirstName();
                }
            %>
        </c:if>
        <script type="text/javascript">
            ShowSpin(true);
            (function ($) {
                $(function () {
                    var demo = $("#demographicNo").val();
					if(demo != null && demo !== "") {
						$.post("<%=request.getContextPath() + "/oscarRx/managePharmacy.do?method=getPharmacyFromDemographic&demographicNo="%>" + demo,
                        function (data) {
                            if (data && data.length && data.length > 0) {
                                $("#preferredList").html("");
                                var json;
                                var preferredPharmacyInfo;
                                for (var idx = 1; idx <= data.length; ++idx) {  //deliberately using idx = 1 to start to match the preferredOrder in db which is 1 counting instead of 0 counting
                                    preferredPharmacyInfo = data[idx - 1];
                                    json = JSON.stringify(preferredPharmacyInfo);
                                    var pharm = "<div prefOrder='" + idx + "' pharmId='" + preferredPharmacyInfo.id + "'><table style='width: 100%'><tr><td class='prefAction prefUp'> Move Up </td>";
                                    pharm += "<td rowspan='3' style='padding-left: 5px'>" + preferredPharmacyInfo.name + "<br /> ";
                                    pharm += preferredPharmacyInfo.address + ", " + preferredPharmacyInfo.city + " " + preferredPharmacyInfo.province + "<br /> ";
                                    pharm += preferredPharmacyInfo.postalCode + "<br />";
                                    pharm += "Main Phone: " + preferredPharmacyInfo.phone1 + "<br />";
                                    pharm += "Fax: " + preferredPharmacyInfo.fax + "<br />";
                                    pharm += "<a href='javascript:void(0)'  onclick='viewPharmacy(" + preferredPharmacyInfo.id + "); event.stopPropagation(); return false;'>View More</a>";
                                    pharm += "<p class='add-date' style='color: grey; text-align: right; margin: 0;'><i><small>Added: " + formatTimestamp(preferredPharmacyInfo.demoAddDate) + "</small></i></p>";
                                    pharm += "</tr><tr><td class='prefAction prefUnlink'> Remove from List </td></tr><tr><td class='prefAction prefDown'> Move Down </td></tr></table></div>";
                                    $("#preferredList").append(pharm);
                                }

                                $(".prefUnlink").click(function () {
                                    var data = "pharmacyId=" + $(this).closest("div").attr("pharmId") + "&demographicNo=" + demo;
                                    ShowSpin(true);
                                    $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=unlink",
                                        data, function (data) {
                                            if (data.id) {
                                                window.location.reload(false);
                                            } else {
                                                alert("Unable to unlink pharmacy");
                                                HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                            }
                                        }, "json");
                                });

                                $(".prefUp").click(function () {
                                    if ($(this).closest("div").prev() != null) {
                                        var $curr = $(this).closest("div");
                                        var $prev = $(this).closest("div").prev();

                                        if ($curr.prev().length == 0) {
                                            alert("This pharmacy is already this patient's most preferred pharmacy.");
                                        } else {
                                            var data = "pharmId=" + $curr.attr("pharmId") + "&demographicNo=" + demo + "&preferredOrder=" + (parseInt($curr.attr("prefOrder")) - 1);
                                            ShowSpin(true);
                                            $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=setPreferred",
                                                data, function (data2) {
                                                    if (data2.id) {
                                                        data = "pharmId=" + $prev.attr("pharmId") + "&demographicNo=" + demo + "&preferredOrder=" + (parseInt($prev.attr("prefOrder")) + 1);
                                                        $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=setPreferred",
                                                            data, function (data3) {
                                                                if (data3.id) {
                                                                    window.location.reload(false);
                                                                } else {
                                                                    HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                                                }
                                                            }, "json");
                                                    } else {
                                                        HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                                    }
                                                }, "json");
                                        }
                                    }
                                });

                                $(".prefDown").click(function () {
                                    if ($(this).closest("div").next() != null) {
                                        var $curr = $(this).closest("div");
                                        var $next = $(this).closest("div").next();

                                        if ($curr.next().length == 0) {
                                            alert("This pharmacy is already this patient's least preferred pharmacy.");
                                        } else {
                                            var data = "pharmId=" + $curr.attr("pharmId") + "&demographicNo=" + demo + "&preferredOrder=" + (parseInt($curr.attr("prefOrder")) + 1);
                                            ShowSpin(true);
                                            $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=setPreferred",
                                                data, function (data2) {
                                                    if (data2.id) {
                                                        data = "pharmId=" + $next.attr("pharmId") + "&demographicNo=" + demo + "&preferredOrder=" + (parseInt($next.attr("prefOrder")) - 1);
                                                        $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=setPreferred",
                                                            data, function (data3) {
                                                                if (data3.id) {
                                                                    window.location.reload(false);
                                                                } else {
                                                                    HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                                                }
                                                            }, "json");
                                                    } else {
                                                        HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                                    }
                                                }, "json");
                                        }
                                    }
                                });
                            }
                            HideSpin(true);
                        }, "json");
					}
                    var pharmacyNameKey = new RegExp($("#pharmacySearch").val(), "i");
                    var pharmacyCityKey = new RegExp($("#pharmacyCitySearch").val(), "i");
                    var pharmacyPostalCodeKey = new RegExp($("#pharmacyPostalCodeSearch").val(), "i");
                    var pharmacyFaxKey = new RegExp($("#pharmacyFaxSearch").val(), "i");
                    var pharmacyPhoneKey = new RegExp($("#pharmacyPhoneSearch").val(), "i");
                    var pharmacyAddressKey = new RegExp($("#pharmacyAddressSearch").val(), "i");

                    $("#pharmacySearch").keyup(function () {
                        updateSearchKeys();
                        $(".pharmacyItem").hide();
                        $.each($(".pharmacyName"), function (key, value) {
                            if ($(value).html().toLowerCase().search(pharmacyNameKey) >= 0) {
                                if ($(value).siblings(".city").html().search(pharmacyCityKey) >= 0) {
                                    if ($(value).siblings(".postalCode").html().search(pharmacyPostalCodeKey) >= 0) {
                                        if ($(value).siblings(".fax").html().search(pharmacyFaxKey) >= 0) {
                                            if ($(value).siblings(".fax").html().search(pharmacyAddressKey) >= 0) {
                                                $(value).parent().show();
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $("#pharmacyCitySearch").keyup(function () {
                        updateSearchKeys();
                        $(".pharmacyItem").hide();
                        $.each($(".city"), function (key, value) {
                            if ($(value).html().toLowerCase().search(pharmacyCityKey) >= 0) {
                                if ($(value).siblings(".pharmacyName").html().search(pharmacyNameKey) >= 0) {
                                    if ($(value).siblings(".postalCode").html().search(pharmacyPostalCodeKey) >= 0) {
                                        if ($(value).siblings(".fax").html().search(pharmacyFaxKey) >= 0) {
                                            if ($(value).siblings(".fax").html().search(pharmacyAddressKey) >= 0) {
                                                $(value).parent().show();
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $("#pharmacyPostalCodeSearch").keyup(function () {
                        updateSearchKeys();
                        $(".pharmacyItem").hide();
                        $.each($(".postalCode"), function (key, value) {
                            if ($(value).html().toLowerCase().search(pharmacyPostalCodeKey) >= 0) {
                                if ($(value).siblings(".pharmacyName").html().search(pharmacyNameKey) >= 0) {
                                    if ($(value).siblings(".city").html().search(pharmacyCityKey) >= 0) {
                                        if ($(value).siblings(".fax").html().search(pharmacyFaxKey) >= 0) {
                                            $(value).parent().show();
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $("#pharmacyFaxSearch").keyup(function () {
                        updateSearchKeys();
                        $(".pharmacyItem").hide();
                        $.each($(".fax"), function (key, value) {
                            if ($(value).html().search(pharmacyFaxKey) >= 0 || $(value).html().split("-").join("").search(pharmacyFaxKey) >= 0) {
                                if ($(value).siblings(".pharmacyName").html().search(pharmacyNameKey) >= 0) {
                                    if ($(value).siblings(".city").html().search(pharmacyCityKey) >= 0) {
                                        if ($(value).siblings(".postalCode").html().search(pharmacyPostalCodeKey) >= 0) {
                                            $(value).parent().show();
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $("#pharmacyPhoneSearch").keyup(function () {
                        updateSearchKeys();
                        $(".pharmacyItem").hide();
                        $.each($(".phone"), function (key, value) {
                            if ($(value).html().search(pharmacyPhoneKey) >= 0 || $(value).html().split("-").join("").search(pharmacyPhoneKey) >= 0) {
                                if ($(value).siblings(".pharmacyName").html().search(pharmacyNameKey) >= 0) {
                                    if ($(value).siblings(".city").html().search(pharmacyCityKey) >= 0) {
                                        if ($(value).siblings(".postalCode").html().search(pharmacyPostalCodeKey) >= 0) {
                                            $(value).parent().show();
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $("#pharmacyAddressSearch").keyup(function () {
                        updateSearchKeys()
                        $(".pharmacyItem").hide();
                        $.each($(".address"), function (key, value) {
                            if ($(value).html().search(pharmacyAddressKey) >= 0 || $(value).html().split("-").join("").search(pharmacyAddressKey) >= 0) {
                                if ($(value).siblings(".pharmacyName").html().search(pharmacyNameKey) >= 0) {
                                    if ($(value).siblings(".city").html().search(pharmacyCityKey) >= 0) {
                                        if ($(value).siblings(".postalCode").html().search(pharmacyPostalCodeKey) >= 0) {
                                            $(value).parent().show();
                                        }
                                    }
                                }
                            }
                        });
                    });

                    $(".pharmacyItem").click(function () {
                        var pharmId = $(this).attr("pharmId");

                        $("#preferredList div").each(function () {
                            if ($(this).attr("pharmId") == pharmId) {
                                alert("Selected pharamacy is already selected");
                                return false;
                            }
                        });

                        var data = "pharmId=" + pharmId + "&demographicNo=" + demo + "&preferredOrder=" + ($("#preferredList div").length + 1);
                        ShowSpin(true);
                        $.post("<%=request.getContextPath() + "/oscarRx/managePharmacy.do?method=setPreferred"%>", data, function (data) {
                            if (data.id) {
                                $("html, body").animate({scrollTop: 0}, 1000);
                                window.location.reload(false);
                            } else {
                                alert("There was an error setting your preferred Pharmacy");
                                HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                            }
                        }, "json");
                    });

                    $(".deletePharm").click(function () {
                        let pharmacyData = "pharmacyId=" + $(this).closest("tr").attr("pharmId");
                        ShowSpin(true);
                        $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=getTotalDemographicsPreferedToPharmacy",
                            pharmacyData, function (data) {
                                HideSpin(true);
                                let deletingWarningStr = "WARNING - proceeding will delete this pharmacy from the clinic's database for all users. Only proceed if you are absolutely sure.\n\nType \"yes\" in the box below to proceed.";
                                if (data.totalDemographics && data.totalDemographics > 0) {
                                    deletingWarningStr = "This pharmacy is currently listed as a preferred pharmacy for [" + data.totalDemographics + "] patients.\n\nDeleting this pharmacy from the clinic's database will also remove it as a preferred pharmacy for all of these patients.\n\nOnly proceed if you are absolutely sure. Type \"yes\" in the box below to proceed";
                                }
                                const userInput = prompt(deletingWarningStr);
                                if (userInput == null || userInput.toLowerCase() != "yes") {
                                    alert("This pharmacy has not been deleted because you did not type \"yes\" in the previous box.");
                                    return false;
                                }

                                ShowSpin(true);
                                $.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=delete",
                                    pharmacyData, function (data) {
                                        if (data.success) {
                                            window.location.reload(false);
                                        } else {
                                            alert("There was an error deleting the Pharmacy");
                                            HideSpin(true);  //hiding the spinner is deliberately only in the "else" case of the callback because reloading is slow.  It's better to leave the spinner in place while the page is reloading.
                                        }
                                    }, "json");
                            }, "json");
                    });


                    function updateSearchKeys() {
                        pharmacyNameKey = new RegExp($("#pharmacySearch").val(), "i");
                        pharmacyCityKey = new RegExp($("#pharmacyCitySearch").val(), "i");
                        pharmacyPostalCodeKey = new RegExp($("#pharmacyPostalCodeSearch").val(), "i");
                        pharmacyFaxKey = new RegExp($("#pharmacyFaxSearch").val(), "i");
                        pharmacyPhoneKey = new RegExp($("#pharmacyPhoneSearch").val(), "i");
                        pharmacyAddressKey = new RegExp($("#pharmacyAddressSearch").val(), "i");
                    }
                })
            })(jQuery);

            function addPharmacy() {
                myLightWindow.activateWindow({
                    href: "<%= request.getContextPath() %>/oscarRx/ManagePharmacy2.jsp?type=Add",
                    width: 400,
                    height: 500
                });
            }

            function editPharmacy(id) {
                myLightWindow.activateWindow({
                    href: "<%= request.getContextPath() %>/oscarRx/ManagePharmacy2.jsp?type=Edit&ID=" + id,
                    width: 400,
                    height: 500
                });
                jQuery("html, body").animate({scrollTop: 0}, 1000);
            }

            function viewPharmacy(id) {
                myLightWindow.activateWindow({
                    href: "<%= request.getContextPath() %>/oscarRx/ViewPharmacy.jsp?type=View&ID=" + id,
                    width: 400,
                    height: 500
                });
                jQuery("html, body").animate({scrollTop: 0}, 1000);
            }


            function returnToRx() {
                var rx_enhance = <%=OscarProperties.getInstance().getProperty("rx_enhance")%>;

                if (rx_enhance) {
                    opener.window.refresh();
                    window.close();
                } else {
                    window.location.href = "<%= request.getContextPath() %>/oscarRx/SearchDrug3.jsp";
                }
            }

			function formatTimestamp(timestamp) {
				// Check if the input is null, undefined, or not a number
				if (!timestamp || typeof timestamp !== 'number' || isNaN(timestamp)) {
					return "Unavailable"; // One-word message for invalid input
				}

				// Create a Date object from the timestamp (in milliseconds)
				const date = new Date(timestamp);

				// Check if the Date object is valid
				if (isNaN(date.getTime())) {
					return "Unavailable"; // One-word message for invalid date
				}

				// Extract the year, month, day, hours, and minutes
				const year = date.getFullYear();
				const month = String(date.getMonth() + 1).padStart(2, '0'); // Months are zero-based
				const day = String(date.getDate()).padStart(2, '0');
				const hours = String(date.getHours()).padStart(2, '0');
				const minutes = String(date.getMinutes()).padStart(2, '0');

				// Format the date and time as "YYYY-MM-DD HH:MM"
				return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes;
			}

		</script>
		<style>

            table tr td {
                vertical-align: top;
                text-align: left;
            }

            html, body, table {
                height: 100%;
                line-height: 1 !important;
            }

            .pharmacyItem:hover {
                background: #DCDCDC;
                cursor: pointer;
            }

            #preferredList {
                vertical-align: top;
            }

            #preferredList div {
                margin-top: 10px;
                border: 1px solid #eda;
                background-color: #FDFEC7;
                vertical-align: top;
            }

            #pharmacyList th {
                height: 35px;
                vertical-align: top;
            }

            .prefAction {
                text-align: center;
                width: 92px;
                background-color: #fbf0b7;
            }

            .prefAction:hover {
                color: #FFFFFF;
                background-color: #ffba65;
                cursor: pointer;
            }

            .DivContentSectionHeadTitle {
                background: #FDFEC7;
                border: 1px solid #eda;
                padding: 10px;
                width: 33%;
                vertical-align: bottom;
            }

            .DivContentSelectionTitle {
                background-color: #f5f5f5;
                padding: 10px;
                border: 1px solid #cccccc;
            }

            table tr.sticky-heading th {
                position: sticky;
                top: 0;
                right: 0;
                left: 0;
                z-index: 1;
            }
        </style>
    </head>
    <body>
    <div class="container-fluid" style="margin:auto 15px;">

        <form id="pharmacyForm">
            <input type="hidden" id="demographicNo" name="demographicNo" value="<%=bean.getDemographicNo()%>"/>
            <table id="AutoNumber1">
                <tr>
                    <th class="DivContentTitle">
                        <h2><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.title"/>
                            <span style="font-size: small;">
						<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.nameText"/>
                        <%=surname%>, <%=firstName%>
                    </span>
                            <input type=button class="btn btn-default pull-right" onclick="returnToRx();"
                                   value="Return to RX"/>
                        </h2>
                    </th>
                </tr>

                <tr>
                    <td>
                        <table class="table-condensed">
                            <tr>
                                <th class="DivContentSectionHeadTitle">

                                    <h4>Patient&apos;s Preferred Pharmacies</h4><span style="font-weight: normal;">(In Descending Order of Preference)</span>

                                </th>
                                <th class="DivContentSelectionTitle">

                                    <h4>Clinic&apos;s Database of Pharmacies <span style="font-size: small;"><a
                                            href="javascript:void(0)"
                                            onclick="addPharmacy();">(add missing pharmacy
									to clinic database)</a></span></h4>
                                    <div class="form-inline">
                                        <div class="form-group"><label for="pharmacySearch">Pharmacy Name </label>
                                            <input type="text" class="form-control" id="pharmacySearch"/></div>
                                        <div class="form-group"><label
                                                for="pharmacyAddressSearch">Address </label><input type="text"
                                                                                                   class="form-control"
                                                                                                   id="pharmacyAddressSearch"/>
                                        </div>
                                        <div class="form-group"><label for="pharmacyCitySearch">City </label><input
                                                type="text" class="form-control" id="pharmacyCitySearch"/></div>
                                        <div class="form-group"><label for="pharmacyPostalCodeSearch">Postal
                                            Code </label><input type="text" class="form-control"
                                                                id="pharmacyPostalCodeSearch"/></div>
                                        <div class="form-group"><label for="pharmacyPhoneSearch">Phone </label><input
                                                type="text" class="form-control" id="pharmacyPhoneSearch"/></div>
                                        <div class="form-group"><label for="pharmacyFaxSearch">Fax </label><input
                                                type="text" class="form-control" id="pharmacyFaxSearch"/></div>
                                    </div>
                                    <p> Instructions: Add the patient&apos;s preferred pharmacies by clicking on a
                                        specific
                                        pharmacy below</p>

                                </th>
                            </tr>
                            <tr>
                                <td>
                                    <table class="table-condensed">
                                        <tr class="sticky-heading">
                                            <th id="preferredList" style="font-weight: normal;">
                                                <div style="text-align: center">
                                                    <b>Add pharmacies from the right side list</b>
                                                </div>
                                            </th>
                                        </tr>
                                        <tr>
                                            <td style="height: 100%;vertical-align: top;">&nbsp;</td>
                                        </tr>
                                    </table>
                                </td>
                                <td id="pharmacyListWindow">

                                    <% RxPharmacyData pharmacy = new RxPharmacyData();
                                        List<PharmacyInfo> pharList = pharmacy.getAllPharmacies();
                                    %>
                                    <table id="pharmacyList" class="table-condensed table-striped"
                                           style="margin-top:5px;width:100%">
                                        <tr class="sticky-heading">
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.pharmacyName"/></th>
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.address"/></th>
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.city"/></th>
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.postalCode"/></th>
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.phone"/></th>
                                            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.fax"/></th>
                                            <th></th>
                                            <th></th>
                                        </tr>
                                        <% for (int i = 0; i < pharList.size(); i++) {
                                            PharmacyInfo ph = pharList.get(i);
                                            if (ph.getName() != null && !ph.getName().isEmpty()) {
                                        %>
                                        <tr class="pharmacyItem" pharmId="<%=ph.getId()%>">
                                            <td class="pharmacyName"><%=Encode.forHtmlContent(ph.getName())%>
                                            </td>
                                            <td class="address"><%=Encode.forHtmlContent(ph.getAddress())%>
                                            </td>
                                            <td style="white-space: nowrap;"
                                                class="city"><%=Encode.forHtmlContent(ph.getCity())%>
                                            </td>
                                            <td style="white-space: nowrap;"
                                                class="postalCode"><%=Encode.forHtmlContent(ph.getPostalCode())%>
                                            </td>
                                            <td style="white-space: nowrap;"
                                                class="phone"><%=Encode.forHtmlContent(ph.getPhone1())%>
                                            </td>
                                            <td style="white-space: nowrap;"
                                                class="fax"><%=Encode.forHtmlContent(ph.getFax())%>
                                            </td>
                                            <security:oscarSec roleName="<%=roleName$%>" objectName="_rx.editPharmacy"
                                                               rights="w" reverse="false">

                                                <td onclick="event.stopPropagation()"><a href="javascript:void(0)"
                                                                                         onclick="editPharmacy(<%=ph.getId()%>);"><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.editLink"/></a></td>
                                                <td onclick="event.stopPropagation()"><a href="javascript:void(0)"
                                                                                         class="deletePharm"><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.deleteLink"/></a></td>

                                            </security:oscarSec>
                                        </tr>
                                        <% }
                                        } %>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </form>
    </div>
    </body>

</html>
