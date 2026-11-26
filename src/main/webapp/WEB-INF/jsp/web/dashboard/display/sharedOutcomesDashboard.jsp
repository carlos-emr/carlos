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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.managers.DashboardManager" %>
<%@page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%
    DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);
%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
    <script>

        // Origin of the shared outcomes dashboard window. Only messages from it may drive a refresh,
        // which publishes provider metrics to the configured destination (an HTTP POST per metric set).
        var outcomesOrigin = (function () {
            try {
                return new URL('<%=SafeEncode.forJavaScript(CarlosProperties.getInstance().getProperty("shared_outcomes_dashboard_host")) %>').origin;
            } catch (e) {
                return null;
            }
        })();

        // The action decodes UTF-8 JSON from Base64. btoa() accepts only Latin-1, so encode to UTF-8 bytes first
        // (metric names can be non-ASCII). jQuery then form-encodes the value, so "+" in Base64 survives.
        function encodeRefreshRequest(params) {
            var bytes = new TextEncoder().encode(JSON.stringify(params));
            var binary = '';
            for (var i = 0; i < bytes.length; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            return btoa(binary);
        }

        var outcomesWindow;
        var outcomesTimeout;

        function closeDashboardChannel() {
            clearInterval(outcomesTimeout);
            if (outcomesWindow && !outcomesWindow.closed) {
                outcomesWindow.postMessage({'response': 'CLOSE'}, outcomesOrigin);
            }
        }

        function launchOutcomesDashboard1() {
            var url = '<%=SafeEncode.forJavaScript(url)%>';
            if (!url || !outcomesOrigin || outcomesOrigin === 'null') return;
            outcomesWindow = window.open(url, "OutcomesWindow", "toolbar=1,scrollbars=1,status=1,statusbar=1,copyhistory=1,resizable=1,width=1280,height=1024");
            if (!outcomesWindow) return;
            outcomesWindow.focus();

            outcomesTimeout = setInterval(function () {
                if (outcomesWindow.closed) {
                    clearInterval(outcomesTimeout);
                    return;
                }
                outcomesWindow.postMessage({'response': 'CONNECT'}, outcomesOrigin);
            }, 500);
        }

        function receiveOutcomesMessage1(event) {
            if (!outcomesWindow || event.source !== outcomesWindow || event.origin !== outcomesOrigin) return;
            var outcomesData = event.data && event.data.outcomes;
            if (!outcomesData) return;
            var params = outcomesData.request;
            switch (outcomesData.interaction) {
                case "CONNECTED" :
                    clearInterval(outcomesTimeout);
                    break;
                case "handlePiePieceClick":
                    if (params && params.hasOwnProperty("query") && params.hasOwnProperty("group") && params.hasOwnProperty("username")) {
                        //console.log("query="+params.query); //OSCAR Metric Test
                        //console.log("group="+params.group); //Up to date, Overdue, ect (the label) - doesn't matter for us
                        //console.log("username="+params.username); //999998
                        $("#drillDownFrame").attr('src', 'DrilldownDisplay?method=getDrilldownBySharedMetricSetName&sharedMetricSetName=' + encodeURIComponent(params.query) + '&sharedMetricSetLable=' + encodeURIComponent(params.group) + "&providerNo=" + encodeURIComponent(params.username));
                        outcomesWindow.postMessage(outcomesData.onSuccess, outcomesOrigin);
                        //window.focus();
                    }
                    break;
                case "submitQuery":
                    if (params && params.hasOwnProperty("queryList")) {
                        jQuery.ajax({
                            url: "<%=request.getContextPath()%>/web/dashboard/OutcomesDashboard",
                            type: 'POST',
                            async: true,
                            data: {data: encodeRefreshRequest(params)},
                            dataType: 'json',
                            success: function () {
                                outcomesWindow.postMessage(outcomesData.onSuccess, outcomesOrigin);
                            },
                            error: function (jqXHR) {
                                // No success message is sent, so the dashboard does not report a refresh that did not happen.
                                console.error('Shared outcomes refresh failed with HTTP status ' + jqXHR.status);
                            }
                        });
                    }

                    break;
            }
        }

        $(document).ready(function () {
            window.addEventListener("message", receiveOutcomesMessage1);
            window.addEventListener("pagehide", closeDashboardChannel);
            launchOutcomesDashboard1();
        });


    </script>
    <style>
        html, body {
            height: 100%
        }
    </style>
</head>
<body>
<h5>CARLOS EMR - Keep window open to interact with Common Provider Dashboard window.</h5>
<input type="button" value="close" onClick="window.close()"/>
<iframe id="drillDownFrame" style="width:100%;height:100%"></iframe>

</body>

</html>
