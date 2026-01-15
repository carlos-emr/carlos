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

<%--
/**
 * PDF Preview Generation Page for Message Attachments
 *
 * This JSP page provides functionality to generate PDF previews of various medical documents
 * that can be attached to messages in the OpenO EMR messenger system. It supports multiple
 * types of attachments including demographic information, encounters, and prescriptions.
 *
 * Main Features:
 * - Interactive document selection interface with checkboxes for multiple document types
 * - Real-time PDF preview generation using client-side JavaScript
 * - Support for demographic information, encounter records, and prescription profiles
 * - Frameset-based preview display with automatic content loading
 * - Batch attachment processing with progress tracking
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 * - Validates demographic_no parameter for patient context
 *
 * Request Parameters:
 * - demographic_no: Required patient demographic number for context
 * - isPreview: Boolean flag indicating preview mode vs attachment mode
 * - isAttaching: Boolean flag for attachment processing state
 * - attachmentCount: Current attachment count for batch processing
 *
 * Session Dependencies:
 * - msgSessionBean: Message session state management
 * - EctSessionBean: Encounter session for patient context
 * - RxSessionBean: Prescription session for medication data
 * - Patient object for prescription profile generation
 *
 * JavaScript Functions:
 * - PreviewPDF(): Initiates PDF preview generation in iframe
 * - AttachingPDF(): Handles batch attachment processing
 * - CheckSrcText(): Monitors iframe content loading for auto-submission
 * - SetBottomURL(): Updates iframe source for document loading
 * - GetBottomSRC(): Extracts HTML content from loaded iframe
 *
 * @since 2003
 */
--%>

<%@ page
        import="ca.openosp.openo.messenger.docxfer.send.*,
                ca.openosp.openo.messenger.docxfer.util.*,
                ca.openosp.openo.encounter.data.*,
                ca.openosp.openo.encounter.pageUtil.EctSessionBean,
                ca.openosp.openo.prescript.pageUtil.RxSessionBean,
                ca.openosp.openo.prescript.data.RxPatientData,
                ca.openosp.openo.messenger.pageUtil.MsgSessionBean,
                ca.openosp.openo.demographic.data.*" %>

<%@ page import=" java.util.*, org.w3c.dom.*, java.sql.*, ca.openosp.*, java.text.*, java.lang.*,java.net.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.EChartDao" %>
<%@ page import="ca.openosp.openo.commn.model.EChart" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%
    EChartDao eChartDao = SpringUtils.getBean(EChartDao.class);
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>


<%
    String demographic_no = request.getParameter("demographic_no");

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(loggedInInfo, demographic_no);
    String demoName = "";
    if (demo != null) {
        demoName = demo.getLastName() + ", " + demo.getFirstName();
    }


    int indexCount = 0;
%>


<%

    EctSessionBean bean = new EctSessionBean();
    bean.demographicNo = demographic_no;

    MsgSessionBean MsgSessionBean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

    request.getSession().setAttribute("EctSessionBean", bean);


%>


<link rel="stylesheet" type="text/css" href="encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.title"/>
        </title>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>


        <script type="text/javascript">
            // Global timer variables for monitoring iframe content loading
            var timerID = null
            var timerRunning = false

            /**
             * Updates the iframe source URL for document loading
             * @param {string} url - URL to load in the preview iframe
             */
            function SetBottomURL(url) {
                f = parent.srcFrame;

                if (url != "") {
                    loc = url;
                } else {
                    loc = document.forms[0].url.value;
                }
                f.location = loc;
            }

            /**
             * Extracts HTML content from the loaded iframe and stores in form field
             */
            function GetBottomSRC() {
                f = parent.srcFrame;
                document.forms[0].srcText.value = f.document.body.innerHTML;
            }

            /**
             * Initiates PDF preview generation process
             * @param {string} url - Document URL to preview
             */
            function PreviewPDF(url) {
                document.forms[0].srcText.value = "";
                document.forms[0].isPreview.value = true;
                SetBottomURL(url);
                // Wait for iframe to load, then start monitoring content
                setTimeout("GetBottomSRC()", 1000);
                timerID = setInterval("CheckSrcText()", 1000);
                timerRunning = true;
            }

            function testing() {
                document.forms[0].isPreview.value = true;
                timerID = setInterval("CheckSrcText()", 1000);
                timerRunning = true;
            }

            /**
             * Handles batch PDF attachment processing for selected documents
             * @param {number} number - Index of attachment to process (-1 for batch mode)
             */
            function AttachingPDF(number) {
                var uriArray = document.forms[0].uriArray;
                var titleArray = document.forms[0].titleArray;
                var indexArray = document.forms[0].indexArray;
                var wantedIndex = 0;
                
                // Reset form state for attachment processing
                document.forms[0].srcText.value = "";
                document.forms[0].isPreview.value = false;
                document.forms[0].isAttaching.value = true;

                // Handle batch vs single attachment mode
                if (number == -1) {
                    document.forms[0].isNew.value = true;
                    wantedIndex = -1;
                } else {
                    document.forms[0].isNew.value = false;
                }

                j = 0;

                // Find the specific attachment to process by index
                if (number != -1) {
                    for (i = 0; i < indexArray.length; i++) {
                        if (indexArray[i].checked) {
                            if (number == j) {
                                wantedIndex = i;
                            }
                            j++;
                        }
                    }
                } else {
                    // Count checked items and find first one for batch processing
                    for (i = 0; i < indexArray.length; i++) {
                        if (indexArray[i].checked) {
                            j++;
                            if (wantedIndex < 0) {
                                wantedIndex = i;
                            }
                        }
                    }
                }

                // Submit immediately if no items selected
                if (j == 0) {
                    document.forms[0].submit();
                    return;
                }

                // Set up attachment processing with selected document
                document.forms[0].attachmentCount.value = j;
                document.forms[0].attachmentTitle.value = titleArray[wantedIndex].value;
                SetBottomURL(uriArray[wantedIndex].value);
                setTimeout("GetBottomSRC()", 1000);
                timerID = setInterval("CheckSrcText()", 1000);
                timerRunning = true;
            }

            /**
             * Monitors iframe content loading and submits form when content is ready
             */
            function CheckSrcText() {
                if (document.forms[0].srcText.value != "") {
                    if (timerRunning) {
                        clearInterval(timerID)
                    }
                    timerRunning = false
                    document.forms[0].submit();
                }
                return;
            }

        </script>


    </head>


    <body class="BodyStyle" vlink="#0000FF">

    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgMessenger"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>Attach document for: <%=demoName%>
                        </td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <table>

                    <tr>
                        <td>
                            <table cellspacing=3>
                                <tr>
                                    <td>
                                        <table class=messButtonsA cellspacing=0 cellpadding=3>
                                            <tr>
                                                <td class="messengerButtonsA"><a href="#"
                                                                                 onclick="javascript:top.window.close()"
                                                                                 class="messengerButtons"> Close
                                                    Attachment </a></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>


                    <tr>

                        <td bgcolor="#EEEEFF"><form action="${pageContext.request.contextPath}/messenger/Doc2PDF.do" method="post">


                            <table border="0" cellpadding="0" cellspacing="1" width="400">
                                <tr>
                                    <th align="left" bgcolor="#DDDDFF" colspan="3">Demographic
                                        information
                                    </th>
                                </tr>
                                <tr>
                                    <td>
                                        <% String currentURI = request.getContextPath() + "/demographic/demographiccontrol.jsp?demographic_no=" + demographic_no + "&displaymode=pdflabel&dboperation=search_detail"; %>
                                        <input type="checkbox" name="uriArray" value="<%=currentURI%>"
                                                       style="display:none"/>

                                        <input type="checkbox" name="indexArray" value="<%= Integer.toString(indexCount++) %>"/>
                                        <input
                                                type=checkbox name="titleArray"
                                                value="<%=demoName%> information" style="display: none"/></td>
                                    <td><%=demoName%> Information</td>
                                    <td>
                                        <% if (request.getParameter("isAttaching") == null) { %> <input
                                            type="button" value=Preview onclick="PreviewPDF( '<%=currentURI%>')"/>
                                        <% } %> &nbsp;
                                    </td>
                                </tr>


                                <tr>

                                    <th align="left" bgcolor="#DDDDFF" colspan="3">Encounters:</th>

                                </tr>
                                <%

                                    String datetime = null;

                                    EChart ec = eChartDao.getLatestChart(Integer.parseInt(demographic_no));

                                    if (ec != null) {

                                %>
                                <tr>
                                    <td>
                                        <% currentURI = request.getContextPath() + "/oscarEncounter/echarthistoryprint.jsp?echartid=" + ec.getId() + "&demographic_no=" + demographic_no; %>
                                        <input type="checkbox" name="uriArray" value="<%=currentURI%>"
                                                       style="display:none"/>
                                        <input type="checkbox" name="indexArray" value="<%= Integer.toString(indexCount++) %>"/>
                                        <input
                                                type=checkbox name="titleArray"
                                                value='Encounter: <%=ec.getTimestamp().toString()%>'
                                                style="display: none"/></td>
                                    <td><%=ec.getTimestamp().toString()%>
                                    </td>
                                    <td>
                                        <% if (request.getParameter("isAttaching") == null) { %> <input
                                            type=button value="Preview" onclick="PreviewPDF( '<%=currentURI%>')"/>
                                        <% } %> &nbsp;
                                    </td>
                                </tr>

                                <%
                                    }

                                %>

                                <tr>

                                    <th align="left" bgcolor="#DDDDFF" colspan="3">
                                        Prescriptions
                                    </th>


                                </tr>
                                <tr>
                                    <td>
                                        <%
                                            // Setup prescription session bean and patient data for drug profile generation
                                            // This ensures the prescription attachment has proper patient context
                                            RxSessionBean Rxbean;

                                            if (request.getSession().getAttribute("RxSessionBean") != null) {
                                                Rxbean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
                                            } else {
                                                Rxbean = new RxSessionBean();
                                            }

                                            request.getSession().setAttribute("RxSessionBean", Rxbean);

                                            // Load patient data for prescription context
                                            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demographic_no);

                                            if (patient != null) {
                                                request.getSession().setAttribute("Patient", patient);
                                            }

                                            // Set provider and demographic context for prescription profile
                                            Rxbean.setProviderNo((String) request.getSession().getAttribute("user"));
                                            Rxbean.setDemographicNo(Integer.parseInt(demographic_no));

                                        %> <% currentURI = request.getContextPath() + "/oscarRx/PrintDrugProfile.jsp?demographic_no=" + demographic_no; %>

                                        <input type="checkbox" name="uriArray" value="<%=currentURI%>"
                                                       style="display:none"/>
                                        <input type="checkbox" name="indexArray" value="<%= Integer.toString(indexCount++) %>"/>
                                        <input
                                                type=checkbox name="titleArray" value='Current prescriptions'
                                                style="display: none"/></td>
                                    <td>Current prescriptions</td>
                                    <td>
                                        <% if (request.getParameter("isAttaching") == null) { %> <input
                                            type="button" value=Preview onclick="PreviewPDF( '<%=currentURI%>')"/>
                                        <% } %> &nbsp;
                                    </td>
                                </tr>

                                <tr>
                                    <td colspan="3" align="center">
                                        <% if (request.getParameter("isAttaching") != null) { %> <input
                                            type=text name=status value=''/> <% } else { %> <input
                                            type="button" name="Attach" value="Attach Document"
                                            onclick="AttachingPDF(-1)"/> <% } %> <br/>
                                    </td>
                                </tr>

                                <tr>
                                    <td colspan="3"><input type="hidden" name="srcText" id="srcText" value=''/>

                                        <input type="hidden" name="attachmentCount" id="attachmentCount" value='<%=request.getParameter("attachmentCount")==null?"0":request.getParameter("attachmentCount")%>'/>
                                        <input type="hidden" name="demographic_no" id="demographic_no" value='<%=demographic_no%>'/>
                                        <input type="hidden" name="isPreview" id="isPreview" value='<%=request.getParameter("isPreview")==null?"false":request.getParameter("isPreview")%>'/>
                                        <input type="hidden" name="isAttaching" id="isAttaching" value='<%=request.getParameter("isAttaching")==null?"false":request.getParameter("isAttaching")%>'/>
                                        <input type="hidden" name="isNew" id="isNew" value='true'/>
                                        <input type="hidden" name="attachmentTitle" id="attachmentTitle" value=''/></td>
                                </tr>

                            </table>
                        </form>
                            <script>
                                if (document.forms[0].isAttaching.value == "true") {

                                    j = 0;
                                    var indexArray = document.forms[0].indexArray;
                                    for (i = 0; i < indexArray.length; i++) {
                                        if (indexArray[i].checked) {
                                            j++;
                                        }
                                    }

                                    document.forms[0].status.value = "Attaching <%=MsgSessionBean.getCurrentAttachmentCount() + 1%> out of " + j;
                                    AttachingPDF(<%=MsgSessionBean.getCurrentAttachmentCount()%>);

                                }


                            </script>
                        </td>


                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>
    </body>
</html>
