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
<!DOCTYPE HTML>


<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.model.ScratchPad" %>
<%@ page import="ca.openosp.openo.utility.DateUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar"%>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite"%>

<%
    String date = null;
    String id = null;
    String title = "";
    boolean deleted = false;
    ScratchPad scratchPad = null;

    String userfirstname = (String) request.getSession().getAttribute("userfirstname");
    String userlastname = (String) request.getSession().getAttribute("userlastname");

    if (request.getAttribute("actionDeleted") != null) {
        deleted = true;
    } else {
        scratchPad = (ScratchPad) request.getAttribute("ScratchPad");
        date = DateUtils.formatDateTime(scratchPad.getDateTime(), request.getLocale());
        id = scratchPad.getId().toString();
        title = "Version from " + date;
    }
%>

<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="ScratchPad.title"/> <%=Encode.forHtmlContent(title)%>
    </title>
    <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>

    <link rel="stylesheet" type="text/css"
          href="<%=request.getContextPath()%>/library/bootstrap/5.0.2/css/bootstrap.min.css"/>

    <style>
        :root * {
            font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
        }

        :root *:not(h2):not(h4) {
            font-size: 12px;
            line-height: 1 !important;
            overscroll-behavior: none;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        .heading {
            display: flex;
            justify-content: flex-start;
            align-items: first baseline;
            width: 100%;
        }

        .heading .page-title {
            width: 100%;
        }

        .heading .user-name {
            width: 100%;
            justify-content: center;
        }

        table {
            border-collapse: collapse;
            width: 100%;
            display: flex;
            justify-content: center;
            align-content: flex-start;
            align-items: stretch;
            flex-direction: column;
        }

        table tr {
            display: flex;
            flex-direction: row;
        }

        table tr td {
            padding: 10px;
            vertical-align: top;
        }

        table tr td.MainTableRightColumn {
            width: 100%;
            display: flex;
            flex-direction: column;
            flex: 1;
        }

        .MainTableRightColumn > textarea {
            flex: 1;
        }

        #scratchpad-version {
            width: 100%;
            box-sizing: border-box;
            min-height: 346px;
            background-color: whitesmoke;
            box-shadow: 0 2px 4px 0 rgba(38, 40, 42, 0.3);
            border-radius: 4px;
            border: lightgray thin solid;
            padding: 10px;
        }
        .alert {
            width: 100%;
        }
    </style>

    <script type="text/javascript">
        /*
         * reset parent window whenever this window is closed.
         */
	    window.addEventListener("beforeunload", function (event) {
		    updateScratch();
	    });
    </script>
</head>
<body>
<div class="container">

    <div class="heading">
        <div class="page-title">
            <h2 style="font-size: 30px;margin-top: 20px;margin-bottom: 10px;font-weight:bold;line-height: 1.1">

            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-card-list"
                     viewBox="0 0 16 16">
                <path d="M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z"></path>
                <path d="M5 8a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 5 8m0-2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m0 5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m-1-5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0M4 8a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0m0 2.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0"></path>
                </svg>

              <fmt:setBundle basename="oscarResources"/><fmt:message key="ScratchPad.title"/>
            </h2>
        </div>
        <div class="user-name">
            <h4><%=Encode.forHtmlContent(userfirstname)%> <%=Encode.forHtmlContent(userlastname)%>
            </h4>
        </div>

    </div>
    <table class="MainTable table-condensed table-borderless" id="scrollNumber1">
        <tr>
            <td>&nbsp;</td>
            <td>
                <div class="pad-version"><fmt:message key="ScratchPad.title"/> <%=Encode.forHtmlContent(title)%>
                </div>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">
                <button name="deleteVersion" class="btn btn-danger" onclick="deleteVersion('<%=Encode.forUriComponent(id)%>')">Delete</button>
            </td>
            <td class="MainTableRightColumn" id="scratchpad-version" style="white-space: pre-line;">
                <%=Encode.forHtmlContent(scratchPad.getText())%>
            </td>
        </tr>

    </table>

    <script>
	    const context = "<%=Encode.forJavaScript(request.getContextPath())%>";

	    function deleteVersion(id) {
			let action = confirm("Are you sure you would like to delete this version?");

			if (action) {
				// Disable button to prevent double-clicks
				const button = event.target;
				if (button) button.disabled = true;

				$.post(
					context + "/Scratch.do",
					{
						method: "delete",
						id: id,
						_csrf: document.querySelector('input[name="_csrf"]')?.value || ""
					},
					function (response) {

						let alert = document.createElement("div");
						if (response && response.success) {
							alert.className = "alert alert-success";
							alert.innerHTML = "Scratchpad version " + response.version + " deleted successfully.";
							alert.role = "alert";
							document.getElementById("scratchpad-version").replaceWith(alert);
						} else {
							alert.className = "alert alert-danger";
							alert.innerHTML = "Error deleting scratchpad version: " + response.version;
							alert.role = "alert";
							document.getElementById("scratchpad-version").appendChild(alert);
							if (button) button.disabled = false;
						}

						updateScratch();
					},
					"json"
				).fail(function (jqXHR, textStatus, errorThrown) {
					alert("Error deleting version: " + textStatus + ". Please try again.");
					if (button) button.disabled = false;
				});
			}
		}

		function updateScratch() {
			window.opener.location.reload();
		}
    </script>
</div>
</body>