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

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.Misc" %>
<%@page import="ca.openosp.openo.util.UtilMisc" %>
<%@include file="/casemgmt/taglibs.jsp" %>
<%@taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@page import="java.util.Enumeration" %>
<%@page import="ca.openosp.openo.encounter.pageUtil.NavBarDisplayDAO" %>
<%@page import="java.util.Arrays,java.util.Properties,java.util.List,java.util.Set,java.util.ArrayList,java.util.Enumeration,java.util.HashSet,java.util.Iterator,java.text.SimpleDateFormat,java.util.Calendar,java.util.Date,java.text.ParseException" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.commn.model.UserProperty,ca.openosp.openo.casemgmt.model.*,ca.openosp.openo.casemgmt.service.* " %>
<%@page import="ca.openosp.openo.casemgmt.web.formbeans.*" %>
<%@page import="ca.openosp.openo.PMmodule.model.*" %>
<%@page import="ca.openosp.openo.commn.model.*" %>
<%@page import="ca.openosp.openo.util.DateUtils" %>
<%@page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.casemgmt.common.Colour" %>
<%@page import="ca.openosp.openo.documentManager.EDoc" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="com.quatro.dao.security.*,ca.openosp.openo.model.security.Secrole" %>
<%@page import="ca.openosp.openo.utility.EncounterUtil" %>
<%@page import="org.apache.cxf.common.i18n.UncheckedException" %>
<%@page import="ca.openosp.openo.casemgmt.web.NoteDisplay" %>
<%@page import="ca.openosp.openo.casemgmt.web.CaseManagementViewAction" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@page import="ca.openosp.openo.casemgmt.dao.CaseManagementNoteLinkDAO" %>
<%@page import="ca.openosp.openo.commn.dao.ProfessionalSpecialistDao" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@page import="ca.openosp.openo.casemgmt.web.NoteDisplayNonNote" %>
<%@page import="ca.openosp.openo.commn.dao.EncounterTemplateDao" %>
<%@page import="ca.openosp.openo.casemgmt.web.CheckBoxBean" %>
<%@page import="ca.openosp.openo.commn.model.CasemgmtNoteLock" %>
<%@page import="ca.openosp.openo.commn.model.EmailLog" %>
<%@page import="ca.openosp.openo.managers.EmailManager" %>
<%@page import="ca.openosp.openo.managers.EmailComposeManager"%>
<%@page import="ca.openosp.openo.managers.SecurityInfoManager"%>
<%@ page import="ca.openosp.openo.encounter.pageUtil.EctSessionBean" %>
<%@ page import="ca.openosp.openo.casemgmt.web.formbeans.CaseManagementEntryFormBean" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Facility" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed2 = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed2 = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed2) {
        return;
    }
%>

<%
    String ctx = request.getContextPath();

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Facility facility = loggedInInfo.getCurrentFacility();
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);

    EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);

    String pId = (String) session.getAttribute("case_program_id");
    Program program = null;
    if (pId == null) {
        pId = "";
    } else {
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        program = programDao.getProgram(Integer.valueOf(pId));
    }

    String demographicNo = request.getParameter("demographicNo");
    EctSessionBean bean;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }

    String provNo = bean.providerNo;

    String dateFormat = "dd-MMM-yyyy H:mm";
    String anotherDateFormat = "dd-MM-yyyy";
    long savedId = 0;
    boolean found = false;
    String bgColour;
    ArrayList<Integer> lockedNotes = new ArrayList<Integer>();
    ArrayList<Integer> unLockedNotes = new ArrayList<Integer>();
    ArrayList<Integer> unEditableNotes = new ArrayList<Integer>();

    @SuppressWarnings("unchecked")
    ArrayList<NoteDisplay> notesToDisplay = (ArrayList<NoteDisplay>) request.getAttribute("notesToDisplay");
    int noteSize = notesToDisplay.size();

    SimpleDateFormat jsfmt = new SimpleDateFormat("MMM dd, yyyy");
    Date dToday = new Date();
    String strToday = jsfmt.format(dToday);

    String frmName = "caseManagementEntryForm" + demographicNo;
    CaseManagementEntryFormBean cform = (CaseManagementEntryFormBean) session.getAttribute(frmName);

    if (cform == null) {
        cform = new CaseManagementEntryFormBean();
        session.setAttribute(frmName, cform);
    }

    if (request.getParameter("caseManagementEntryForm") == null) {
        if (cform != null) {
            request.setAttribute("caseManagementEntryForm", cform);
        }
    }

    Integer offset = Integer.parseInt(request.getParameter("offset"));
    int maxId = 0;

//We determine the lock status of the note
    CasemgmtNoteLock casemgmtNoteLock = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demographicNo);
%>

<c:if test="${not empty notesToDisplay}">
    <%
        int idx = 0;

        //Notes list will contain all notes including most recently saved
        //we need to skip this one when displaying

        //if we're editing a note, check to see if it is locked
        //
        if (cform.getCaseNote().getId() != null) {
            savedId = cform.getCaseNote().getId();
        }

        //Check user property for stale date and show appropriately
        UserProperty uProp = (UserProperty) request.getAttribute(UserProperty.STALE_NOTEDATE);

        Date dStaleDate = null;
        int numToDisplay = Integer.MAX_VALUE;
        int numDisplayed = 0;
        Calendar cal = Calendar.getInstance();
        if (uProp != null) {
            String strStaleDate = uProp.getValue();
            if (strStaleDate.equalsIgnoreCase("A")) {
                cal.set(0, 1, 1);
            } else if (strStaleDate.equalsIgnoreCase("0")) {
                cal.add(Calendar.MONTH, 1);
            } else {
                int pastMths = Integer.parseInt(strStaleDate);
                cal.add(Calendar.MONTH, pastMths);
            }

        } else {
            cal.add(Calendar.YEAR, -1);
        }

        dStaleDate = cal.getTime();

        String noteStr;
        int length;

        /*
         *  Cycle through notes starting from the most recent and marking them for full inclusion or one line display
         *  Need to do this now as we only count face to face encounters against limit of how many to fully display
         *  If no user preference, show at most five face to face encounter notes
         *  Else show all notes withing the user preference
         */
        ArrayList<Boolean> fullTxtFormat = new ArrayList<Boolean>(noteSize);
        int pos;
        idx = 0;

        for (pos = noteSize - 1; pos >= 0; --pos) {
            NoteDisplay cmNote = notesToDisplay.get(pos);

            if (cmNote.isCpp()) {
                fullTxtFormat.add(Boolean.FALSE);
                continue;
            }

			if (cmNote.isEmailNote())
			{
				fullTxtFormat.add(Boolean.TRUE);
                continue;
            }

            if (cmNote.getObservationDate() == null) {
                fullTxtFormat.add(Boolean.FALSE);
                continue;
            }

            if (noteSize > numToDisplay) {
                if (uProp == null) {
                    if (numDisplayed < numToDisplay && cmNote.getObservationDate().compareTo(dStaleDate) >= 0) {
                        fullTxtFormat.add(Boolean.TRUE);

                        if (EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue().equalsIgnoreCase(cmNote.getEncounterType())) {
                            ++numDisplayed;
                        }
                    } else {
                        fullTxtFormat.add(Boolean.FALSE);
                    }
                } else {
                    if (cmNote.getObservationDate().compareTo(dStaleDate) >= 0) {
                        fullTxtFormat.add(Boolean.TRUE);
                    } else {
                        fullTxtFormat.add(Boolean.FALSE);
                    }
                }
            } else {
                if (cmNote.getObservationDate().compareTo(dStaleDate) >= 0) {
                    fullTxtFormat.add(Boolean.TRUE);
                } else {
                    fullTxtFormat.add(Boolean.FALSE);
                }
            }
        } //end of for loop

        boolean fulltxt;
        pos = noteSize - 1;

        String issuesToHide = OscarProperties.getInstance().getProperty("encounter.hide_notes_with_issue", "");
        String[] is = issuesToHide.split(",");

        boolean remoteCapableProfessionalSpecialists = professionalSpecialistDao.hasRemoteCapableProfessionalSpecialists();

        int currentNcId = 0;
        String strCurrentNcId = null;
        // begin for loop for rendering notes
        for (idx = 0; idx < noteSize; ++idx) {

            NoteDisplay note = notesToDisplay.get(idx);
            noteStr = note.getNote();
            Integer noteId = note.getNoteId();
            EDoc doc = new EDoc();
            String dispDocNo = "";
            String dispFilename = "";
            String dispStatus = " ";
            String globalNoteId = "";

            if (note.getRemoteFacilityId() != null) {
                globalNoteId = "UUID" + note.getUuid();
            }

            if (noteId != null) {
                globalNoteId = note.getNoteId().toString();

                if (note.isDocument()) {

                    globalNoteId = "DOC" + note.getNoteId();
                    doc = EDocUtil.getDocFromNote((long) noteId.intValue());

                    if (doc != null) {
                        dispDocNo = doc.getDocId();
                        dispFilename = doc.getFileName();
                        Character status = doc.getStatus();

                        if (status == 'A') {
                            dispStatus = "active";
                        }
                        //find docname, docno and docstatus
                    }
                } else if (note.isEformData()) {
                    globalNoteId = "EFORM" + note.getNoteId();
                } else if (note.isInvoice()) {
                    globalNoteId = "INV" + note.getNoteId();
                } else if (note.isEmailNote()) {
					if (!emailComposeManager.hasEmailPrivilege(loggedInInfo, SecurityInfoManager.READ)) { continue; }
                    EmailLog emailLog = emailManager.getEmailLogByCaseManagementNoteId(loggedInInfo, Long.valueOf(noteId));
                    if (emailLog == null) {
                        continue;
                    }
                    dispDocNo = String.valueOf(emailLog.getId());
                }
            }

            noteStr = StringEscapeUtils.escapeHtml4(noteStr);
            // for remote notes, the full text is always shown.
            fulltxt = fullTxtFormat.get(pos) || note.getRemoteFacilityId() != null;
            --pos;
            bgColour = CaseManagementViewAction.getNoteColour(note);
            if (fulltxt) {
                noteStr = noteStr.replaceAll("\n", "<br>");
            } else {
                length = noteStr.length() > 50 ? 50 : noteStr.length();
                noteStr = noteStr.substring(0, length);
            }

            boolean editWarn = !note.isSigned() && !note.getProviderNo().equals(provNo);
            boolean hideCppNotes = OscarProperties.getInstance().isPropertyActive("encounter.hide_cpp_notes");
            boolean hideDocumentNotes = OscarProperties.getInstance().isPropertyActive("encounter.hide_document_notes");
            boolean hideEformNotes = OscarProperties.getInstance().isPropertyActive("encounter.hide_eform_notes");
            //boolean hideMetaData = OscarProperties.getInstance().isPropertyActive("encounter.hide_metadata");
            boolean hideInvoices = OscarProperties.getInstance().isPropertyActive("encounter.hide_invoices");

            String noteDisplay = "block";
            if (note.isCpp() && hideCppNotes) {
                noteDisplay = "none";
            }
            if (note.isDocument() && hideDocumentNotes) {
                noteDisplay = "none";
            }
            if (note.isEformData() && hideEformNotes) {
                noteDisplay = "none";
            }

            if (note.isInvoice() && hideInvoices) {
                noteDisplay = "none";
            }

            if (!noteDisplay.equals("none") && issuesToHide.length() > 0) {
                for (String i : is) {
                    if (note.containsIssue(i)) {
                        noteDisplay = "none";
                        break;
                    }
                }
            }

            strCurrentNcId = offset.toString() + String.valueOf(idx + 1);
            currentNcId = Integer.parseInt(strCurrentNcId);

            if (currentNcId > maxId) {
                maxId = currentNcId;
            }

            //String metaDisplay = (hideMetaData)?"none":"block";

            String noteIdAttribute = new StringBuilder("nc").append(offset > 0 ? offset : "").append(idx + 1).toString();
			boolean isMagicNote = note.isDocument() || note.isCpp() || note.isEformData() || note.isEncounterForm() || note.isInvoice();
            String noteClassAttribute = new StringBuilder("note").append(isMagicNote ? "" : " noteRounded encounter-note").toString();
    %>

		<%
			String cursorStyle = (note.isCpp()) ? "cursor: pointer;" : "";
		%>
    <div id="<%=noteIdAttribute%>"
			 style="display: <%= noteDisplay %>; <%= cursorStyle %>"
         class="<%=noteClassAttribute%>">

        <input type="hidden" id="signed<%=globalNoteId%>" value="<%=note.isSigned()%>"/>
        <input type="hidden" id="full<%=globalNoteId%>"
               value="<%=fulltxt || (note.getNoteId() !=null && note.getNoteId().equals(savedId))%>"/>
        <input type="hidden" id="bgColour<%=globalNoteId%>" value="<%=bgColour%>"/>
        <input type="hidden" id="editWarn<%=globalNoteId%>" value="<%=editWarn%>"/>
			<%
			if (note.isEmailNote()) {
			%>
				<input type="hidden" id="emailNote<%=globalNoteId%>" value="true" />
			<%
			}
			%>

        <div id="n<%=globalNoteId%>" class="note-contents">
            <%
                //display last saved note for editing
                if (note.getNoteId() != null && !"".equals(note.getNoteId()) && note.getNoteId().intValue() == savedId) {
                    found = true;
            %>
            <script>
                savedNoteId =<%=note.getNoteId()%>;
            </script>
            <%
                if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
            %>
            <script>
                _setupNewNote();
            </script>
            <% } %>

            <div class='tool-button print-button'>
                <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.print.title"/>" id='print<%=globalNoteId%>'
                     alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.togglePrintNote.title"/>"
                     onclick="togglePrint(<%=globalNoteId%>, event)" style='float: right; margin-right: 5px;'
                     src='<%=ctx %>/oscarEncounter/graphics/printer.png'/>
            </div>
            <textarea tabindex="7" cols="84" rows="10"
                      class="txtArea boxsizingBorder <%= note.isSigned() ? "" : "unsigned-textarea"%>" wrap="soft"
                      style="line-height: 1.1em;" name="caseNote_note"
                      id="caseNote_note<%=savedId%>"><%=cform.getCaseNote_note()%></textarea>

            <div class="sig <%= note.isSigned() ? "" : "note-unsigned"%>" id="sig<%=globalNoteId%>">
                <%@ include file="noteIssueList.jsp" %>
            </div>


            <%
            } else //else display contents of note for viewing
            {
                if (false) {
            %>
            <div id="txt<%=globalNoteId%>">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.msgLocked"/> <%=DateUtils.getDate(note.getUpdateDate(), dateFormat, request.getLocale()) + " " + note.getProviderName()%>
            </div>
            <%
            } else {
                String rev = note.getRevision();
                if (note.getRemoteFacilityId() == null) // always display full note for remote notes
                {
                    if (note.isDocument() || note.isCpp() || note.isEformData() || note.isEncounterForm() || note.isInvoice() || note.isEmailNote()) {
                        // blank if so it never displays min/max icon for documents
                    } else if (fulltxt) {
            %>
            <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MinDisplay.title"/>" id='quitImg<%=globalNoteId%>'
                 alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MinDisplay.title"/>" onclick="minView(event)"
                 style='float: right; margin-right: 5px; margin-bottom: 3px; margin-top: 2px;'
                 src='<%=ctx %>/oscarEncounter/graphics/triangle_up.gif'/>
            <%
            } else {
            %>
            <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MaxDisplay.title"/>" id='quitImg<%=globalNoteId%>'
                 name='fullViewTrigger' alt="Maximize Display" onclick="fullView(event)"
                 style='float: right; margin-right: 5px; margin-top: 2px;'
                 src='<%=ctx %>/oscarEncounter/graphics/triangle_down.gif'/>
            <%
                    }
                }

                if (note.getRemoteFacilityId() != null) // if it's a remote note, say where if came from on the top of the note
                {
            %>
            <div style="background-color:#ffcccc; text-align:right">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.noteFrom.label"/>&nbsp;<%=note.getLocation()%>
                ,<%=note.getProviderName()%>
            </div>
            <%
                }

                if (note.isGroupNote()) // if it's a remote note, say where if came from on the top of the note
                {
            %>
            <div style="background-color:#33FFCC; text-align:right">
                Group Note - Editable note in this <a href="javascript:void(0)"
                                                      onClick="popupPage(700,1000,'Master1','<%=request.getContextPath()%>/demographic/demographiccontrol.jsp?demographic_no=<%=note.getLocation() %>&displaymode=edit&dboperation=search_detail');return false;">client</a>
            </div>
            <%
                }

                if (!note.isDocument() && !note.isCpp() && !note.isEformData() && !note.isEncounterForm() && !note.isInvoice() && !note.isEmailNote()) {

            %>
            <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.print.title"/>" id='print<%=globalNoteId%>'
                 alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.togglePrintNote.title"/>"
                 onclick="togglePrint('<%=globalNoteId%>'   , event)"
                 style='float: right; margin-right: 5px; margin-top: 2px;'
                 src='<%=ctx %>/oscarEncounter/graphics/printer.png'/>
            <%
                }

                if (!note.isDocument() && !note.isRxAnnotation()) {
                    // only allow editing for local notes
                    // also disallow editing of cpp's inline (can be edited in the cpp area)
                    if (note.getRemoteFacilityId() == null && !note.isCpp() && !note.isEformData() && !note.isEncounterForm() && !note.isInvoice() && !note.isEmailNote()) {
                        if (!note.isReadOnly()) {
            %>
            <a title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>" id="edit<%=globalNoteId%>"
               href="javascript:void(0)" onclick="<%=editWarn?"noPrivs(event)":"editNote(event)"%> ;return false;"
               style="float: right; margin-right: 5px;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>
            </a>
            <%
                }

                if (remoteCapableProfessionalSpecialists) {
            %>
            <a href="javascript:void(0)"
               onclick="window.open('<%=request.getContextPath()+"/lab/CA/ALL/sendOruR01.jsp?noteId="+globalNoteId%>', 'eSend');return(false);"
               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.eSendTitle"/>"
               style="float: right; margin-right: 5px;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.eSend"/></a>
            <%
                    }
                }
            } else if (note.isRxAnnotation())//prescription note
            {
                String winName = "dummie";
                int hash = Math.abs(winName.hashCode());
                //get drug from note id.
                RxPrescriptionData.Prescription rx = note.getRxFromAnnotation(note.getNoteLink());

                if (note.getRemoteFacilityId() == null) // only allow editing for local notes
                {
                    if (!note.isReadOnly()) {
            %>
            <a title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>" id="edit<%=globalNoteId%>"
               href="javascript:void(0);" onclick="<%=editWarn?"noPrivs(event);":"editNote(event);"%> return false;"
               style="float: right; margin-right: 5px; ">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>
            </a>
            <%
                    }
                }

                if (rx != null) {
                    String url = "popupPage(700,800,'" + hash + "', '" + request.getContextPath() + "/oscarRx/StaticScript2.jsp?demographicNo=" + rx.getDemographicNo() + "&regionalIdentifier=" + rx.getRegionalIdentifier() + "&cn=" + response.encodeURL(rx.getCustomName()) + "');";
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<%=rx.getSpecial()%>" id="view<%=globalNoteId%>" href="javascript:void(0);"
                   onclick="<%=url%>" style="float: right; margin-right: 5px; "> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.rxView"/> </a>
            </div>
            <%
                }
            } else if (note.isDocument() && !note.getProviderNo().equals("-1")) {
                //document annotation
                String url;

                Enumeration em = request.getAttributeNames();

                String winName = "docs" + demographicNo;
                int hash = Math.abs(winName.hashCode());

                String encodedDispDocNo = Encode.forUriComponent(dispDocNo);
                url = "popupPage(1000,1200,'" + hash + "', '" + request.getContextPath() + "/documentManager/showDocument.jsp?inWindow=true&segmentID=" + encodedDispDocNo +"');";
                url = url + "return false;";

							String editUrl = "window.open('/oscar/annotation/annotation.jsp?display=Documents&amp;table_id=" + encodedDispDocNo + "&amp;demo=" + demographicNo + "','anwin','width=400,height=500');";

                if (note.getRemoteFacilityId() == null) // only allow editing for local notes
                {
                    if (!note.isReadOnly()) {
            %>
                <a title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>" id="edit<%=globalNoteId%>"
                   href="javascript:void(0);" onclick="<%=editUrl%> return false;" style="<%=bgColour%> order: 1; padding: 2px 5px;">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.edit.msgEdit"/>
                </a>
            <%
                    }
                }
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.docView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0)" onclick="<%=url%>" style="float: right; "> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/> </a>
            </div>
            <%
            } else { //document note
                String url;

                Enumeration em = request.getAttributeNames();
                String winName = "docs" + demographicNo;
                int hash = Math.abs(winName.hashCode());

                String encodedDispDocNo = Encode.forUriComponent(dispDocNo);
                url = "popupPage(1000,1200,'" + hash + "', '" + request.getContextPath() + "/documentManager/showDocument.jsp?inWindow=true&segmentID=" + encodedDispDocNo +"');";
                url = url + "return false;";
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.docView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0);" onclick="<%=url%>">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/>
                </a>
            </div>
            <%
                }

                if (note.isEformData()) {
                    String winName = "eforms" + demographicNo;
                    int hash = Math.abs(winName.hashCode());
                    String url = "popupPage(700,800,'" + hash + "','" + request.getContextPath() + "/eform/efmshowform_data.jsp?appointment=' + appointmentNo + '&fdid=";

                    CaseManagementNoteLink noteLink = note.getNoteLink();
                    if (noteLink != null) url += noteLink.getTableId();
                    else url += note.getNoteId();

                    url += "'); return false;";
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.eformView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0)" onclick="<%=url%>"> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/> </a>
            </div>
            <%
            } else if (note.isInvoice()) {
                String winName = "invoice" + demographicNo;
                int hash = Math.abs(winName.hashCode());
                String url = "popupPage(700,800,'" + hash + "','" + request.getContextPath() + StringEscapeUtils.escapeHtml4(((NoteDisplayNonNote) note).getLinkInfo()) + "'); return false;";
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.eformView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0)" onclick="<%=url%>"> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/> </a>
            </div>
            <%
            } else if (note.isEncounterForm()) {
                NoteDisplayNonNote formEntry = (NoteDisplayNonNote) note;
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(anotherDateFormat);
                String createdDate = "";
                if (formEntry.getCreated() != null) {
                    createdDate = simpleDateFormat.format(formEntry.getCreated());
                }
                String winName = formEntry.getNote().trim() + demographicNo + createdDate;
                int hash = Math.abs(winName.hashCode());
                String url = "popupPage(700,800,'"
                        + hash + "started" + "','"
                        + request.getContextPath()
                        + StringEscapeUtils.escapeHtml4("/form/forwardshortcutname.do?formname=" + formEntry.getNote())
                        + "&demographic_no=" + demographicNo
                        + "&formId=" + formEntry.getNoteId()
                        + "'); return false;";
            %>
            <div class="view-links"
                 style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.eformView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0)" onclick="<%=url%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/></a>
            </div>
            <%
            } else if (note.isEmailNote()) {
                String url = "viewEmailByLogId(1100,1000,'" + request.getContextPath() + "/admin/ManageEmails.do?method=resendEmail&logId=" + dispDocNo + "');" + "return false;";
							if (fulltxt) {
								%>
									<img title='Minimize Display' id='quitImg<%=globalNoteId%>' style='float: right;' alt='Minimize Display' onclick='minNonEditableNoteView(<%=globalNoteId%>)' src='<%=ctx %>/oscarEncounter/graphics/triangle_up.gif'>
								<%
							} else {
                                // STRUTS2 TODO - below image MaxDisplay.title might need further setup to be available to the fmt bundle below
                                // moved over from commit by Italiya 2025-02-10 as part of merging changes during STRUTS2 migration
                               %>
                                    <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MaxDisplay.title"/>" id='fullImg<%=globalNoteId%>' alt="Maximize Display" onclick="fullView(event)" style='float: right;' src='<%=ctx %>/oscarEncounter/graphics/triangle_down.gif' />
                               <%
                            }
                            %>
            <div class="view-links" style="<%=(isMagicNote)?(bgColour):""%>">
                <a class="links" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view.docView"/>" id="view<%=globalNoteId%>"
                   href="javascript:void(0);" onclick="<%=url%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.view"/> </a>
            </div>
            <%
                }
                if (!note.isDocument() && !note.isCpp() && !note.isEformData() && !note.isEncounterForm() && !note.isInvoice() && !note.isEmailNote()) {
                    String atbname = "anno" + String.valueOf(new Date().getTime());
                    String addr = request.getContextPath() + "/annotation/annotation.jsp?atbname=" + atbname + "&table_id=" + String.valueOf(note.getNoteId()) + "&display=EChartNote&demo=" + demographicNo;
            %>
            <input type="image" id="anno<%=globalNoteId%>" src='<%=ctx %>/oscarEncounter/graphics/annotation.png'
                   title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnAnnotation"/>'
                   style="float: right; margin-right: 5px; margin-bottom: 3px; height:10px;width:10px"
                   onclick="window.open('<%=addr%>','anwin','width=400,height=500');$('annotation_attribname').value='<%=atbname%>'; return false;"/>
            <%
                }
            %>

							<div id="wrapper<%=globalNoteId%>" style="<%=(note.isDocument()||note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())?(bgColour):""%>">
                    <%-- render the note contents here --%>
			  				<div id="txt<%=globalNoteId%>" name="<%=(note.isCpp()||note.isEmailNote())?"expandableReadonlyNoteText":""%>">

                    <%=noteStr%>
                </div> <!-- end of txt<%=globalNoteId%> -->
                <%
		  							if (note.isCpp()||note.isEformData()||note.isEncounterForm()||note.isInvoice())
		  							{
                %>
                <div id="observation<%=globalNoteId%>" style="display:ruby;">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.encounterDate.title"/>:&nbsp;
                    <span id="obs<%=globalNoteId%>"><%=note.getObservationDate() != null ? DateUtils.getDate(note.getObservationDate(), dateFormat, request.getLocale()) : "N/A"%></span>
                    <%
                        if (note.isCpp()) {
                    %>
                    &nbsp;
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.noteRev.title"/>
                    <%

                        if (rev != null) {
                            if (globalNoteId.contains("EFORM")) {
                    %>
                    <a style="color:#ddddff;" href="javascript:void(0)"
                       onclick="return showHistory('<%=globalNoteId.replace("EFORM","")%>', event);"><%=rev%>
                    </a>
                    <%
                    } else {
                    %>
                    <a style="color:#ddddff;" href="javascript:void(0)"
                       onclick="return showHistory('<%=globalNoteId%>', event);"><%=rev%>
                    </a>
                    <%
                        }
                    } else {
                    %>
                    N/A
                    <%
                            }
                        }
                    %>
                </div> <!-- end of observation<%=globalNoteId%> -->
                <%
                    }
                %>
            </div> <!-- end of wrapper<%=globalNoteId%> -->
            <%

                if (!note.isEmailNote() && largeNote(noteStr)) {
            %>
            <img title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MinDisplay.title"/>" id='bottomQuitImg<%=globalNoteId%>'
                 alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.MinDisplay.title"/>" onclick="minView(event)"
                 style='float: right; margin-right: 5px; margin-bottom: 3px;'
                 src='<%=ctx %>/oscarEncounter/graphics/triangle_up.gif'/>
            <%
                }

						if (!note.isDocument() && !note.isCpp() && !note.isEformData() && !note.isEncounterForm() && !note.isInvoice())
						{

                    if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
            %>
            <div class="_program" noteId="<%=globalNoteId %>" programName="<%=note.getProgramName() %>"
                 roleName="<%=note.getRoleName() %>">
                <span class="program"><%=note.getProgramName() %> (<%=note.getRoleName() %>)</span>
            </div>
            <%
                }
            %>
							<div id="sig<%=globalNoteId%>" class="sig" style="<%=note.isEmailNote()?(bgColour):""%>">
								<div id="sumary<%=globalNoteId%>" style="<%=note.isEmailNote()?"color: #FFF !important":""%>">
                    <div id="observation<%=globalNoteId%>" style="float: right; margin-right: 3px;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.encounterDate.title"/>:&nbsp;
                        <span id="obs<%=globalNoteId%>"><%=DateUtils.getDate(note.getObservationDate(), dateFormat, request.getLocale())%></span>&nbsp;
                        <%if (!note.isEmailNote()) {%>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.noteRev.title"/>
                        <%
                            if (rev != null) {
                        %>
                        <a href="javascript:void(0)" onclick="return showHistory('<%=globalNoteId%>', event);"><%=rev%>
                        </a>
                        <%
                        } else {
                        %>
                        N/A
                        <%
                            }
                        %>
											<%}%>
                    </div>



									<%if (!note.isEmailNote()) {%>
                    <div>
                        <span style="float: left;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.editors.title"/>:</span>
                        <ul style="list-style: none inside none; margin: 0;">
                            <%
                                ArrayList<String> editorNames = note.getEditorNames();
                                Iterator<String> it = editorNames.iterator();
                                int count = 0;
                                int MAXLINE = 2;
                                while (it.hasNext()) {
                                    String providerName = it.next();

                                    if (count % MAXLINE == 0) {
                                        out.print("<li>" + providerName + "; ");
                                    } else {
                                        out.print(providerName + "</li>");
                                    }
                                    if (it.hasNext()) ++count;
                                }
                                if (count % MAXLINE == 0) out.print("</li>");
                            %>
                        </ul>
                    </div>
									<%}%>


                    <%
                        if (facility.isEnableEncounterTime() || (program != null && program.isEnableEncounterTime())) {
                    %>
                    <div style="clear: right; margin-right: 3px; float: right;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.encounterTime.title"/>:&nbsp;<span
                            id="encTime<%=globalNoteId%>"><%=note.getEncounterTime()%></span>
                    </div>
                    <% } %>
                    <%
                        if (facility.isEnableEncounterTransportationTime() || (program != null && program.isEnableEncounterTransportationTime())) {
                    %>
                    <div style="clear: right; margin-right: 3px; float: right;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.encounterTransportation.title"/>:&nbsp;<span
                            id="encTransTime<%=globalNoteId%>"><%=note.getEncounterTransportationTime()%></span>
                    </div>
                    <% } %>

									<%if (!note.isEmailNote()) {%>
                    <div style="clear: right; margin-right: 3px; float: right;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.encType.title"/>:&nbsp;
                        <span id="encType<%=globalNoteId%>"><%=note.getEncounterType().equals("") ? "" : "&quot;" + note.getEncounterType() + "&quot;"%></span>
                    </div>

                    <div>
                        <span style="float: left;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.assignedIssues.title"/></span>
                        <%
                            ArrayList<String> issueDescriptions = note.getIssueDescriptions();

                            if (issueDescriptions.size() > 0) {
                        %>
                        <ul style="float: left; list-style:none; margin: 0;">
                            <%
                                for (String issueDescription : issueDescriptions) {
                            %>
                            <li><%=issueDescription.trim()%>
                            </li>
                            <%
                                }
                            %>
                        </ul>
                        <%
                            }
                        %>
                        <br style="clear: both;"/>
                    </div> <!-- end of assigned title -->
									<%}%>

									<%if (note.isEmailNote()) {%>
									<div>
										Email Note
									</div>
									<%}%>
                </div> <!-- end of div summary<%=globalNoteId%> -->
            </div> <!-- end of div sig<%=globalNoteId%> -->
            <%
                        } // end of if (!note.isDocument() && !note.isCpp() && !note.isEformData() && !note.isEncounterForm() && !note.isInvoice())
                    }
                }
            %>
        </div><!-- end of div n<%=globalNoteId%> -->
    </div>
    <!-- end of div <%=noteIdAttribute%> -->

    <%--		<% if (request.getAttribute("moreNotes") != null && ((Boolean) request.getAttribute("moreNotes"))) { %>--%>
    <%--		<script type="text/javascript">--%>
    <%--		setupOneNote('<%=offset%><%=idx+1%>');--%>
    <%--		</script>--%>
    <%--		<% } %>--%>

    <%
        //if we are not editing note, remember note ids for setting event listeners
        //Internet Explorer does not play nice with inserting javascript between divs
        //so we store the ids here and list the event listeners at the end of this script
        if (note.getNoteId() != null && note.getNoteId() != savedId) {
            if (false) {
                lockedNotes.add(note.getNoteId());
			}
			else if (!fulltxt && !note.isDocument() && !note.isEformData() && !note.isEncounterForm() && !note.isRxAnnotation() && !note.isInvoice() && !note.isEmailNote())
			{
				%><script> Element.observe('n<%=note.getNoteId()%>', 'click', fullView); </script><%
                    unLockedNotes.add(note.getNoteId());
                }
            }

        } //end for */
    %>
</c:if> <%-- END OF "not empty notesToDisplay" --%>


<%
    if (!found && request.getAttribute("moreNotes") == null) {
        //if we didn't find note but savedId is > 0 then we have a note to edit which is not part of the quick chart
        if (savedId > 0) {
            found = true;
        }

        //savedId = 0;
%>
<div id="nc<%=offset%><%=savedId%>" class="note noteRounded encounter-note">
    <input type="hidden" id="signed<%=savedId%>" value="false"/>
    <input type="hidden" id="full<%=savedId%>" value="true"/>
    <input type="hidden" id="bgColour<%=savedId%>" value="color:#000000;background-color:#CCCCFF;"/>
    <input type="hidden" id="editWarn<%=savedId%>" value="false"/>
    <div id="n<%=savedId%>">
        <textarea tabindex="7" cols="84" rows="10" class="txtArea boxsizingBorder" wrap="soft"
                  style="line-height: 1.1em;" name="caseNote_note"
                  id="caseNote_note<%=savedId%>"><%=cform.getCaseNote_note() %></textarea>
        <div class="sig" id="sig<%=savedId%>">
            <%@ include file="noteIssueList.jsp" %>
        </div> <!-- end of div sig<%=savedId%> -->


    </div> <!-- end of div n<%=savedId%>  -->
</div>
<!-- end of div nc<%=offset%><%=savedId%> -->

<% if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) { %>
<script>
    _setupNewNote();
</script>
<% } %>

<%
    }
%>

<script type="text/javascript">
    maxNcId = <%=maxId%>;
</script>


<% if (request.getAttribute("moreNotes") == null) { %>
<script type="text/javascript">
    caseNote = "caseNote_note" + "<%=savedId%>";
    //save initial note to determine whether save is necessary
	if (document.getElementById(caseNote)) {
		origCaseNote = document.getElementById(caseNote).value;
	}
    <%

        if( casemgmtNoteLock.isLocked() ) {
        //note is locked so display message
    %>
    alert("Another user is currently editing this note.  Please try again later.");
    <%
        }
        else if( casemgmtNoteLock.isLockedBySameUser() && !casemgmtNoteLock.getSessionId().equals(request.getRequestedSessionId()) ) {
            //note is locked by same user so offer to unlock note and view locked note in progress
    %>
    var viewEditedNote = confirm("You have started to edit this note in another window at <%=casemgmtNoteLock.getIpAddress()%>.\nDo you wish to continue?");
    if (viewEditedNote) {
        doscroll();
        var params = "method=updateNoteLock&demographicNo=" + demographicNo;
        jQuery.ajax({
            type: "POST",
            url: "<%=ctx%>/CaseManagementEntry.do",
            data: params,
            success: function () {
                //force save when exiting chart in case we loaded edited note in other chart
                origCaseNote += ".";
                tmpSaveNeeded = true;
            }
        });
    } else {
        window.close();
    }
    <%
        }
    %>

    jQuery(document).ready(function () {

        <%
        String singleLineFormat="false";
        UserProperty slProp = (UserProperty)request.getAttribute(UserProperty.STALE_FORMAT);
        if (slProp != null && slProp.getValue().equals("yes")) {
            singleLineFormat="true";
        }
        %>
        if ('<%=singleLineFormat%>' == 'true') {
            var staleIds = new Array();

            jQuery("img[id^='quitImg']").each(function () {
                if (jQuery(this).attr('src').indexOf('/oscarEncounter/graphics/triangle_down.gif') != -1) {
                    var iid = jQuery(this).attr('id');
                    jQuery(this).trigger('click');
                    staleIds.push(iid);
                }
            });

            for (var i = 0; i < staleIds.length; i++) {
                jQuery("#" + staleIds[i]).trigger('click');
            }
        }

    });

    document.forms["caseManagementEntryForm"].noteId.value = "<%=savedId%>";

    //are we editing existing note?  if not init newNoteIdx as we are dealing with a new note

    <%if (!bean.oscarMsg.equals(""))
             {%>
    $(caseNote).value += "\n\n<%=org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(bean.oscarMsg)%>";
    <%bean.reason = "";
                 bean.oscarMsg = "";
             }

             if (request.getParameter("noteBody") != null)
             {
                 String noteBody = request.getParameter("noteBody");
                 noteBody = noteBody.replaceAll("<br>|<BR>", "\n");%>
    $(caseNote).value += "\n\n<%=org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(noteBody)%>";
    <%}

             if (found != true)
             {%>
    document.forms["caseManagementEntryForm"].newNoteIdx.value = <%=savedId%>;
    <%}
             else
             {%>
    document.forms["caseManagementEntryForm"].note_edit.value = "existing";
    <%}%>
    setupNotes();
	jQuery('#' + caseNote).on('keyup', monitorCaseNote);
	jQuery('#' + caseNote).on('paste', function(e) {
		// Let the paste happen first, then resize
		setTimeout(adjustCaseNote, 0);
	});

    Element.observe(caseNote, 'click', getActiveText);
    <%Integer num;
			Iterator<Integer> iterator = lockedNotes.iterator();
			while (iterator.hasNext())
			{
				num = iterator.next();%>
    Element.observe('n<%=num%>', 'click', unlockNote);
    <%}

			iterator = unLockedNotes.iterator();
			while (iterator.hasNext())
			{
				num = iterator.next();%>
    Element.observe('n<%=num%>', 'click', fullView);
    <%}%>

    //flag for determining if we want to submit case management entry form with enter key pressed in auto completer text box
    var submitIssues = false;
    //AutoCompleter for Issues
    <%--    <c:url value="/CaseManagementEntry.do?method=issueList&demographicNo=${param.demographicNo}&providerNo=${param.providerNo}" var="issueURL" />--%>
    <%--    let issueAutoCompleter = new Ajax.Autocompleter("issueAutocomplete", "issueAutocompleteList", "<c:out value="${issueURL}"/>", {minChars: 3, indicator: 'busy', afterUpdateElement: saveIssueId, onShow: autoCompleteShowMenu, onHide: autoCompleteHideMenu});--%>

    <%int MaxLen = 20;
			int TruncLen = 17;
			String ellipses = "...";
			for (int j = 0; j < bean.templateNames.size(); j++)
			{
				String encounterTmp = bean.templateNames.get(j);
				encounterTmp = StringUtils.maxLenString(encounterTmp, MaxLen, TruncLen, ellipses);
				encounterTmp = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(encounterTmp);%>
    autoCompleted["<%=encounterTmp%>"] = "ajaxInsertTemplate('<%=encounterTmp%>')";
    autoCompList.push("<%=encounterTmp%>");
    itemColours["<%=encounterTmp%>"] = "99CCCC";
    <%}%>
    //set default event for assigning issues
    //we do this here so we can change event listener when changing diagnosis
    var obj = {};
    makeIssue = "makeIssue";
    defaultDiv = "sig<%=savedId%>";
    changeIssueFunc;  //set in changeDiagnosis function above
    addIssueFunc = updateIssues.bindAsEventListener(obj, makeIssue, defaultDiv);
    Element.observe('asgnIssues', 'click', addIssueFunc);
    try {
         new Autocompleter.Local('enTemplate', 'enTemplate_list', autoCompList, {
             colours: itemColours,
             afterUpdateElement: menuAction
         });
    } catch (error) {
        console.error("Failed to initialize Autocompleter.Local:", error);
    }

    //start timer for autosave
    setTimer();

    reason = "<%=insertReason(request)%>";    //function defined bottom of file

    if (typeof messagesLoaded == 'function') {
        messagesLoaded('<%=savedId%>');
    }
    <%
	if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
	%>
    _setupProgramList();
    <% } %>

</script>

<%
    if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
%>
<script type="text/javascript">
    jQuery("._program .program").unbind("click");
    jQuery("._program .program").click(_noteProgramClick);
</script>
<% } %>


<% } %>

<%!
    /*
     *Insert encounter reason for new note
     */

    protected String insertReason(HttpServletRequest request) {
        if (OscarProperties.getInstance().isPropertyActive("encounter.empty_new_note")) {
            return new String();
        }
        String encounterText = "";
        String apptDate = request.getParameter("appointmentDate");
        String reason = request.getParameter("reason");

        if (reason == null) {
            reason = "";
        }

        if (apptDate == null || apptDate.equals("") || apptDate.equalsIgnoreCase("null")) {
            encounterText = "\n[" + UtilDateUtilities.DateToString(new java.util.Date(), "dd-MMM-yyyy", request.getLocale()) + " .: " + reason + "] \n";
        } else {
            apptDate = convertDateFmt(apptDate);
            encounterText = "\n[" + apptDate + " .: " + reason + "]\n";
        }

        encounterText = Encode.forJavaScript(encounterText);
        return encounterText;
    }

    protected String convertDateFmt(String strOldDate) {
        String strNewDate = "";
        if (strOldDate != null && strOldDate.length() > 0) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            try {

                Date tempDate = fmt.parse(strOldDate);
                strNewDate = new SimpleDateFormat("dd-MMM-yyyy").format(tempDate);

            } catch (ParseException ex) {
                MiscUtils.getLogger().error("Error", ex);
            }
        }

        return strNewDate;
    }

    protected boolean largeNote(String note) {
        final int THRESHOLD = 10;
        boolean isLarge = false;
        int pos = -1;

        for (int count = 0; (pos = note.indexOf("\n", pos + 1)) != -1; ++count) {
            if (count == THRESHOLD) {
                isLarge = true;
                break;
            }
        }

        return isLarge;
    }
%>
