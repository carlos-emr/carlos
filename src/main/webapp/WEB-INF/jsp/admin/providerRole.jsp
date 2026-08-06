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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="io.github.carlos_emr.*" %>
<%@ page import="io.github.carlos_emr.carlos.log.*" %>
<%@ page import="org.springframework.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.SecRole" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecRoleDao" %>
<%@ page import="io.github.carlos_emr.carlos.model.security.Secuserrole" %>
<%@ page import="io.github.carlos_emr.carlos.daos.security.SecuserroleDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.RecycleBin" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.RecycleBinDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
    SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);

    SecuserroleDao secUserRoleDao = (SecuserroleDao) SpringUtils.getBean(SecuserroleDao.class);
    RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
    ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);

    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curUser_no = (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false"><%isSiteAccessPrivacy=true; %></security:oscarSec>

<%!
    ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
    SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);

    SecuserroleDao secUserRoleDao = SpringUtils.getBean(SecuserroleDao.class);
    RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
    ProgramProviderDAO programProviderDao = SpringUtils.getBean(ProgramProviderDAO.class);

    // ObjectMapper is thread-safe once configured, so one instance serves every request.
    static final ObjectMapper HELD_ROLES_MAPPER = new ObjectMapper();
%>
<%
    //check to see if new case management is request
    ArrayList<String> users = (ArrayList<String>) session.getServletContext().getAttribute("CaseMgmtUsers");
    boolean newCaseManagement = false;

    if (!IsPropertiesOn.isCaisiEnable()) {
        //This should only temporarily apply to oscar, not caisi.
        //You cannot assign providers to one program "OSCAR" here if you have caisi enabled.
        //If there is no program called "OSCAR", it will only assign empty program to the providers which is not acceptable.
        if ((users != null && users.size() > 0) || CarlosProperties.getInstance().getProperty("CASEMANAGEMENT", "").equalsIgnoreCase("all"))
            newCaseManagement = true;
    }

    String ip = request.getRemoteAddr();
    String msg = "";
    String caisiProgram = null;

//get caisi programid for oscar
    if (newCaseManagement) {
        Program p = programDao.getProgramByName("OSCAR");
        if (p != null) {
            caisiProgram = String.valueOf(p.getId());
        }
    }

// get role from database
    List<String> vecRoleName = new ArrayList<String>();

    String omit = "";
    if (isSiteAccessPrivacy) {
        omit = CarlosProperties.getInstance().getProperty("multioffice.admin.role.name", "");
    }

    List<SecRole> secRoles = secRoleDao.findAllOrderByRole();
    for (SecRole secRole : secRoles) {
        if (!secRole.getName().equals(omit)) {
            vecRoleName.add(secRole.getName());
        }
    }
    java.util.ResourceBundle oscarRec = ResourceBundle.getBundle("oscarResources", request.getLocale());
//set the primary role
    if (request.getParameter("buttonSetPrimaryRole") != null && request.getParameter("buttonSetPrimaryRole").length() > 0) {
        String providerNo = request.getParameter("primaryRoleProvider");
        String roleName = request.getParameter("primaryRoleRole");
        ProviderData provider = StringUtils.hasText(providerNo) ? providerDao.findByProviderNo(providerNo) : null;

        /* The primary role designates which of the provider's own roles leads; it is not a way
         * to grant a new one (use Add in the table above for that). The selector only offers
         * roles the provider holds, and the server re-checks it here because a POST can carry
         * anything. A primary role outside secUserRole would also be unmarkable in the
         * "Primary EMR Role" column and would grant note access for a role they do not hold.
         */
        boolean providerHoldsRole = false;
        // The selector hides the multioffice role, but a POST can still name it.
        if (provider != null && StringUtils.hasText(roleName) && !roleName.equals(omit)) {
            // Active assignments only — an inactive or legacy-NULL row grants no authority, so it
            // must not become the primary role that drives note access.
            List assignedRoles = secUserRoleDao.findActiveByProviderNo(providerNo);
            if (assignedRoles != null) {
                for (Object assignedRole : assignedRoles) {
                    if (roleName.equals(((Secuserrole) assignedRole).getRoleName())) {
                        providerHoldsRole = true;
                        break;
                    }
                }
            }
        }
        SecRole secRole = providerHoldsRole ? secRoleDao.findByName(roleName) : null;

        if (provider != null && "1".equals(provider.getStatus()) && secRole != null && caisiProgram != null) {
            Long roleId = secRole.getId().longValue();
            Long programId = Long.valueOf(caisiProgram);
            ProgramProvider pp = programProviderDao.getProgramProvider(providerNo, programId);
            if (pp == null) {
                pp = new ProgramProvider();
                pp.setProgramId(programId);
                pp.setProviderNo(providerNo);
            }
            pp.setRoleId(roleId);
            programProviderDao.saveProgramProvider(pp);

            /* The primary role drives clinical-note access rights through
             * CaseManagementManagerImpl#getAccessType (program_provider.role_id ->
             * DefaultRoleAccess), so this write is a privilege change and must be
             * audited like the add/update/delete role mutations below.
             */
            LogAction.addLog(curUser_no, LogConst.UPDATE, LogConst.CON_ROLE, providerNo + "|primaryRole>" + roleName, ip);
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgUpdated"),
                    SafeEncode.forHtml(roleName), SafeEncode.forHtml(providerNo));
        } else {
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgNotUpdated"),
                    SafeEncode.forHtml(roleName), SafeEncode.forHtml(providerNo));
        }
    }


// update the role
    if (request.getParameter("buttonUpdate") != null && request.getParameter("buttonUpdate").length() > 0) {
    String number = request.getParameter("providerId") != null ? request.getParameter("providerId") : "";
        String roleId = request.getParameter("roleId");
        String roleOld = request.getParameter("roleOld");
        String roleNew = request.getParameter("roleNew");
        String encodedRoleNew = SafeEncode.forHtml(roleNew);

        if (!"-".equals(roleNew)) {
            Secuserrole secUserRole = secUserRoleDao.findById(Integer.parseInt(roleId));
            if (secUserRole != null) {
                secUserRole.setRoleName(roleNew);
                secUserRoleDao.updateRoleName(Integer.parseInt(roleId), roleNew);
                msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgUpdated"), encodedRoleNew, SafeEncode.forHtml(number));

                RecycleBin recycleBin = new RecycleBin();
                recycleBin.setProviderNo(curUser_no);
                recycleBin.setUpdateDateTime(new java.util.Date());
                recycleBin.setTableName("secUserRole");
                recycleBin.setKeyword(number + "|" + roleOld);
                recycleBin.setTableContent("<provider_no>" + number + "</provider_no>" + "<role_name>" + roleOld + "</role_name>" + "<role_id>" + roleId + "</role_id>");
                recycleBinDao.persist(recycleBin);

                LogAction.addLog(curUser_no, LogConst.UPDATE, LogConst.CON_ROLE, number + "|" + roleOld + ">" + roleNew, ip);

			if( newCaseManagement && caisiProgram != null) {
                    ProgramProvider programProvider = programProviderDao.getProgramProvider(number, Long.valueOf(caisiProgram));
                    if (programProvider == null) {
                        programProvider = new ProgramProvider();
                    }

                    programProvider.setProgramId(Long.valueOf(caisiProgram));
                    programProvider.setProviderNo(number);
                    programProvider.setRoleId(Long.valueOf(secRoleDao.findByName(roleNew).getId()));
                    programProviderDao.saveProgramProvider(programProvider);
                }

            } else {
                msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgNotUpdated"), encodedRoleNew, SafeEncode.forHtml(number));
            }
        }

    }

// add the role
    String add = oscarRec.getString("global.btnAdd");
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(add)) {
        String number = request.getParameter("providerId") != null ? request.getParameter("providerId") : "";
        String roleNew = request.getParameter("roleNew");
        String encodedRoleNew = SafeEncode.forHtml(roleNew);
        if (!"-".equals(roleNew)) {
            Secuserrole secUserRole = new Secuserrole();
            secUserRole.setProviderNo(number);
            secUserRole.setRoleName(roleNew);
            secUserRole.setActiveyn(1);
            secUserRoleDao.save(secUserRole);
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgAdded"), encodedRoleNew, SafeEncode.forHtml(number));
            LogAction.addLog(curUser_no, LogConst.ADD, LogConst.CON_ROLE, number + "|" + roleNew, ip);
	    if( newCaseManagement && caisiProgram != null) {
                ProgramProvider programProvider = programProviderDao.getProgramProvider(number, Long.valueOf(caisiProgram));
                if (programProvider == null) {
                    programProvider = new ProgramProvider();
                }
                programProvider.setProgramId(Long.valueOf(caisiProgram));
                programProvider.setProviderNo(number);
                programProvider.setRoleId(Long.valueOf(secRoleDao.findByName(roleNew).getId()));
                programProviderDao.saveProgramProvider(programProvider);
            }
        } else {
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgNotAdded"), encodedRoleNew, SafeEncode.forHtml(number));
        }

    }

// delete the role
    String delete = oscarRec.getString("global.btnDelete");
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(delete)) {
    String number = request.getParameter("providerId") != null ? request.getParameter("providerId") : "";
        String roleId = request.getParameter("roleId");
        String roleOld = request.getParameter("roleOld");
        String roleNew = request.getParameter("roleNew");
        String encodedRoleOld = SafeEncode.forHtml(roleOld);

	List secUserRoles = secUserRoleDao.findByProviderNo(number);

    if(secUserRoles != null) {
		Iterator listIterator = secUserRoles.iterator();
		while(listIterator.hasNext()) {
            Secuserrole secUserRole = (Secuserrole) listIterator.next();
            if(secUserRole.getId() == Integer.parseInt(roleId)) {

            secUserRoleDao.deleteById(secUserRole.getId());
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgDeleted"), encodedRoleOld, SafeEncode.forHtml(number));
                listIterator.remove();

            RecycleBin recycleBin = new RecycleBin();
            recycleBin.setProviderNo(curUser_no);
            recycleBin.setUpdateDateTime(new java.util.Date());
            recycleBin.setTableName("secUserRole");
            recycleBin.setKeyword(number + "|" + roleOld);
            recycleBin.setTableContent("<provider_no>" + number + "</provider_no>" + "<role_name>" + roleOld + "</role_name>");
            recycleBinDao.persist(recycleBin);

            LogAction.addLog(curUser_no, LogConst.DELETE, LogConst.CON_ROLE, number + "|" + roleOld, ip);

                if( newCaseManagement && caisiProgram != null) {

                    // get the role identifier
                    String roleName = secUserRole.getRoleName();
                    Long roleIdentifier = Long.valueOf(secRoleDao.findByName(roleName).getId());
                    ProgramProvider programProvider = programProviderDao.getProgramProvider(number, Long.valueOf(caisiProgram), roleIdentifier);

                    /* Try to assign a new primary role in programProvider if the role being deleted is a primary role
                     * AND a single role remains after deletion.
                     */
                    if(programProvider != null && ! secUserRoles.isEmpty()) {
						// select the next user role in the list to set as primary
                        secUserRole = (Secuserrole) secUserRoles.get(0);
                        roleName = secUserRole.getRoleName();
                        roleIdentifier = Long.valueOf(secRoleDao.findByName(roleName).getId());
						programProvider.setRoleId(roleIdentifier);
                        programProviderDao.saveProgramProvider(programProvider);
                    }

					/* delete the primary role only, then let the primary role
                     * detector prompt the user for a new primary role if there
                     * are multiple roles remaining.
                     */
                    else if(programProvider != null) {
                        programProviderDao.deleteProgramProvider(programProvider.getId());
                }

					else {
						// do nothing
                    }
            }

            }
        }

        } else {
            msg = MessageFormat.format(oscarRec.getString("admin.providerrole.msgNotDeleted"), encodedRoleOld, SafeEncode.forHtml(number));
        }

    }

    String keyword = request.getParameter("keyword") != null ? request.getParameter("keyword") : "";


    String lastName = "";
    String firstName = "";
    String[] temp = keyword.split("\\,");
    if (temp.length > 1) {
        lastName = temp[0] + "%";
        firstName = temp[1] + "%";
    } else {
        lastName = keyword + "%";
        firstName = "%";
    }

    List<Object[]> providerList = null;
    providerList = providerDao.findProviderSecUserRoles(lastName, firstName);

    Vector<Properties> vec = new Vector<Properties>();
    for (Object[] providerSecUser : providerList) {

        String id = String.valueOf(providerSecUser[0]);
        String role_name = String.valueOf(providerSecUser[1]);
        String provider_no = String.valueOf(providerSecUser[2]);
        String first_name = String.valueOf(providerSecUser[3]);
        String last_name = String.valueOf(providerSecUser[4]);

        Properties prop = new Properties();
        prop.setProperty("provider_no", provider_no == "null" ? "" : provider_no);
        prop.setProperty("first_name", first_name);
        prop.setProperty("last_name", last_name);
        prop.setProperty("role_id", id != "null" ? id : "");
        prop.setProperty("role_name", role_name != "null" ? role_name : "");
        // Legacy rows carry a NULL activeyn and, like an explicit 0, must not count as active.
        prop.setProperty("activeyn", "1".equals(String.valueOf(providerSecUser[5])) ? "1" : "");
        vec.add(prop);
    }

    /* Roles each listed provider already holds. The primary-role selector is scoped to these
     * because the primary role designates which of a provider's own roles leads; granting a
     * new role is the Add action in the table above.
     *
     * Only active assignments qualify. An inactive role is ignored by authorization
     * (SecurityInfoManagerImpl reads findActiveByProviderNo), but program_provider.role_id
     * still feeds CaseManagementManagerImpl#getAccessType, so making a disabled role primary
     * would grant note access that the security layer does not recognise.
     *
     * The multioffice role withheld from vecRoleName is excluded for the same reason: making it
     * primary activates its note access, so an administrator who may not assign that role must
     * not be able to promote it either.
     */
    Map<String, List<String>> heldRolesByProvider = new LinkedHashMap<String, List<String>>();
    for (Properties prop : vec) {
        String heldProviderNo = prop.getProperty("provider_no", "");
        String heldRoleName = prop.getProperty("role_name", "");
        if (heldProviderNo.isEmpty() || heldRoleName.isEmpty()
                || !"1".equals(prop.getProperty("activeyn", ""))
                || heldRoleName.equals(omit)) {
            continue;
        }
        List<String> held = heldRolesByProvider.get(heldProviderNo);
        if (held == null) {
            held = new ArrayList<String>();
            heldRolesByProvider.put(heldProviderNo, held);
        }
        if (!held.contains(heldRoleName)) {
            held.add(heldRoleName);
        }
    }

    List<Boolean> primaries = new ArrayList<Boolean>();

//when caisi is off, we need to show which role is the one in the program_provider table for each providers.
    if (newCaseManagement) {

        // audit all roles for if primary is set.
        List secUserRoleList = secUserRoleDao.findAll();

        // get all the user roles.
        Set<String> activeUsers = new HashSet<>();
        if(secUserRoleList != null) {
            for(Object secUserRoleItem : secUserRoleList) {
                Secuserrole secUserRole = (Secuserrole) secUserRoleItem;
                activeUsers.add(secUserRole.getProviderNo());
            }
        }

        // check if the primary is set for each user role
        for(String user : activeUsers) {

            List programProvider = programProviderDao.getProgramProvidersByProvider(user);

            if(programProvider == null || programProvider.isEmpty()) {
                ProviderData provider = providerDao.findByProviderNo(user);
                if (provider != null) {
                    msg += String.format("</br><span style='color:red;'>WARNING: Provider %s requires a primary role assignment.</span>", SafeEncode.forHtml(provider.getFirstName() + " " + provider.getLastName()));
                }
            }
        }

        for (Properties prop : vec) {
            boolean res = false;
            String providerNo = prop.getProperty("provider_no");
            String secUserRoleId = prop.getProperty("role_id");
            String roleName = prop.getProperty("role_name");
            if (!roleName.equals("")) {
                SecRole secRole = secRoleDao.findByName(roleName);
                if (secRole != null) {
                    ProgramProvider pp = programProviderDao.getProgramProvider(providerNo, Long.valueOf(caisiProgram), secRole.getId().longValue());
                    res = (pp != null);
                }
            }
            primaries.add(res);
        }
    }


%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>

    <link href="${ pageContext.request.contextPath }/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="${ pageContext.request.contextPath }/css/fontawesome-all.min.css">

    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-compat.js"></script>

    <title><fmt:message key="global.update"/> <fmt:message key="admin.admin.provider"/> <fmt:message key="role"/></title>

    <script>

        function setfocus() {
            this.focus();
            document.forms[0].keyword.select();
	    window.scrollTo( 0,  '<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("scrollPosition")) %>' context="javaScriptBlock"/>');
        }

        function submit(form) {
            form.submit();
        }

        /*
         * The primary role designates which of a provider's own roles leads, so the selector
         * only exposes roles that provider holds. Each provider option carries its held roles
         * as encoded JSON in data-roles; picking a provider rebuilds the role options from that
         * JSON. The roles themselves are never assigned here — that is Add in the table.
         */
        function primaryRoleChooseProvider() {
            const providerSelect = document.getElementById('primaryRoleProvider');
            const roleSelect = document.getElementById('primaryRoleRole');
            if (!providerSelect || !roleSelect) {
                return;
            }
            // Rebuild rather than hide: one option per provider/role pair would repeat the same
            // value many times, leaving selection by value ambiguous.
            while (roleSelect.options.length > 1) {
                roleSelect.remove(1);
            }
            roleSelect.value = "";
            const selectedProvider = providerSelect.selectedOptions[0];
            if (!selectedProvider || !selectedProvider.dataset.roles) {
                return;
            }
            JSON.parse(selectedProvider.dataset.roles).forEach(function (heldRole) {
                const option = document.createElement('option');
                option.value = heldRole;
                option.textContent = heldRole;
                roleSelect.appendChild(option);
            });
        }

        document.addEventListener('DOMContentLoaded', function () {
            const primaryRoleProvider = document.getElementById('primaryRoleProvider');
            if (primaryRoleProvider) {
                primaryRoleProvider.value = "";
            }
            primaryRoleChooseProvider();
        });

        function setPrimaryRole() {
            var providerNo = document.getElementById('primaryRoleProvider').value;
            var roleName = document.getElementById('primaryRoleRole').value;
            if(providerNo !== '' && roleName !== '') {
                return true;
            } else {
                alert('Please enter in a providers and a corresponding role');
                return false;
            }
        }

	/*
	 * allow the addition of a role only if the role selection does not
	 * equal any of the roles already selected for the target providers.
	 */
    function enableAddRoleButton(e) {
		// get the originally selected values from all the other
        // roles set for this providers.
	    const forms = document.getElementsByClassName(e.form.attributes.class.value);
	    const SELECTED_ROLE_VALUES = [];
	    for(let i=0;i<forms.length;i++) {
		    SELECTED_ROLE_VALUES.push(forms[i].elements.roleNew.dataset.org);
	    }

		// Compare the value being selected against the values already selected.
        // Unlock the add button if not already selected.
	    e.form.elements.submit[0].disabled = SELECTED_ROLE_VALUES.includes(e.value);
	    SELECTED_ROLE_VALUES.length = 0;
    }
    </script>

</head>
<body onLoad="setfocus()">

<div id="header" class="navbar">
    <div class="container-fluid">
        <div class="navbar-brand"><i class="fa-solid fa-lock"></i>&nbsp;<fmt:message key="global.update"/>&nbsp;<fmt:message key="admin.admin.provider"/>&nbsp;<fmt:message key="role"/></div>
    </div>
</div>


<form name="myform" action="${pageContext.request.contextPath}/admin/ProviderRole" method="POST">

    <% if (msg.length() > 1) {%>
    <div class="alert alert-info">
        <%=msg%>
    </div>
    <% } %>
    <div class="card card-body bg-body-tertiary">

        <div>
            <div class="input-group">
                <input type="text" placeholder="<fmt:message key="admin.providerrole.formSearch"/>" name="keyword"
                       value="<carlos:encode value='<%= keyword %>' context="htmlAttribute"/>"/>
                <input type="submit" class="btn btn-primary" name="search" value="<fmt:message key='admin.providerrole.filter'/>" >
            </div>
        </div>

    </div>
</form>

<table id="provTable" class="table table-striped table-hover table-sm">
    <thead>
    <tr>
        <th><fmt:message key="admin.admin.provider"/></th>
        <th><fmt:message key="admin.provider.formFirstName"/></th>
        <th><fmt:message key="admin.provider.formLastName"/></th>
        <% if (newCaseManagement) { %>
        <th>
            <fmt:message key="role"/>
        </th>
        <th>
            <fmt:message key="demographic.demographiceditdemographic.primaryEMR"/> <fmt:message key="role"/>
        </th>
        <% } else {%>
        <th>
            <fmt:message key="role"/>
        </th>
        <%} %>
        <th><fmt:message key="admin.providerrole.action"/></th>
    </tr>
    </thead>
    <tbody>
    <%

        for (int i = 0; i < vec.size(); i++) {
            Properties item = vec.get(i);
            String providerNo = item.getProperty("provider_no", "");
    %>
      <form name="myform" class="myform myform-<carlos:encode value='<%= providerNo %>' context="htmlAttribute"/>" action="${pageContext.request.contextPath}/admin/ProviderRole" method="POST" onSubmit="this.scrollPosition.value=window.scrollY">
        <tr>

              <td><carlos:encode value='<%= providerNo %>' context="html"/></td>
              <td><carlos:encode value='<%= item.getProperty("first_name", "") %>' context="html"/></td>
              <td><carlos:encode value='<%= item.getProperty("last_name", "") %>' context="html"/></td>
            <td>
              <select name="roleNew" onchange="enableAddRoleButton(this)" data-org="<carlos:encode value='<%= item.getProperty("role_name", "") %>' context="htmlAttribute"/>">
                    <option value="-">-</option>
                    <%
                        /* A role withheld by the multioffice guard is still listed on the row that
                         * already holds it. Without this the select has no matching option and
                         * silently falls back to "-", misrepresenting the stored assignment — the
                         * row then reads as blank while "Primary EMR Role" says Yes. It is added
                         * only to its own row, so the guard still prevents conferring it on anyone.
                         */
                        String currentRoleName = item.getProperty("role_name", "");
                        if (!currentRoleName.isEmpty() && !vecRoleName.contains(currentRoleName)) {
                    %>
                      <option value="<carlos:encode value='<%= currentRoleName %>' context="htmlAttribute"/>" selected>
                        <carlos:encode value='<%= currentRoleName %>' context="html"/>
                    </option>
                    <%
                        }
                        for (int j = 0; j < vecRoleName.size(); j++) {
                    %>
                      <option value="<carlos:encode value='<%= String.valueOf(vecRoleName.get(j)) %>' context="htmlAttribute"/>"
                              <%= vecRoleName.get(j).equals(item.getProperty("role_name", ""))?"selected":"" %>>
                        <carlos:encode value='<%= String.valueOf(vecRoleName.get(j)) %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select>
            </td>
            <% if (newCaseManagement) { %>
            <td>
                <%=(primaries.get(i) != null && (primaries.get(i)).booleanValue() == true) ? oscarRec.getString("global.yes") : "" %>
            </td>
            <% } %>

            <td>
                <%--
                    Use caution when adding elements to this form.
                    Javascript method enableAddRoleButton(this) uses indexes to
                    locate the Add button.
                    Changing the index order will cause the button to fail
                --%>
                <input type="hidden" name="scrollPosition" class="scrollPosition" />
                <input type="hidden" name="keyword" value="<carlos:encode value='<%= keyword %>' context="htmlAttribute"/>"/>
              <input type="hidden" name="providerId" value="<carlos:encode value='<%= providerNo %>' context="htmlAttribute"/>">
                <input type="hidden" name="roleId" value="<%= item.getProperty("role_id", "")%>">
                <input type="hidden" name="roleOld"
                       value="<carlos:encode value='<%= item.getProperty("role_name", "") %>' context="htmlAttribute"/>">
                <div class="button-group">
                    <input type="submit" name="submit" class="btn btn-primary"
                           value="<fmt:message key="global.btnAdd"/>" disabled="disabled">
                    <input type="submit" name="buttonUpdate" class="btn btn-info"
                           value="<fmt:message key="global.update"/>" <%= StringUtils.hasText(item.getProperty("role_id"))?"":"disabled"%>>
                    <input type="submit" name="submit" class="btn btn-link" style="color:red;"
                           value="<fmt:message key="global.btnDelete"/>" <%= StringUtils.hasText(item.getProperty("role_id"))?"":"disabled"%>>
                </div>
            </td>
        </tr>
    </form>
    <%
        }
    %>
    </tbody>
</table>

      <% if( newCaseManagement ) {
      %>
<div class="card card-body bg-body-tertiary">
       <form name="myform" action="${pageContext.request.contextPath}/admin/ProviderRole" method="POST" onSubmit="this.scrollPosition.value=window.scrollY">
        <table>
            <tr>
                <td><fmt:message key="global.update"/>&nbsp;<fmt:message key="demographic.demographiceditdemographic.primaryEMR"/>&nbsp;<fmt:message key="role"/></td>
            </tr>
            <tr>
                <td>
                    <label class="form-label" for="primaryRoleProvider"><fmt:message key="admin.admin.provider"/>:</label>
                    <select id="primaryRoleProvider" name="primaryRoleProvider" onchange="primaryRoleChooseProvider()">
                        <option value=""><fmt:message key="admin.providerupdateprovider.selectBelow"/></option>
                        <%
                            List<String> temp1 = new ArrayList<String>();
                            for (Properties prop : vec) {
                                String providerNo = prop.getProperty("provider_no");
                                if (!temp1.contains(providerNo)) {
                                    /* Held roles travel with the provider option as JSON so the role
                                     * selector can be rebuilt client-side without a round trip.
                                     * Jackson is used rather than hand-escaping: a role name holding
                                     * a control character would otherwise emit invalid JSON, and the
                                     * JSON.parse below would fail silently, leaving an empty selector.
                                     */
                                    List<String> heldRoles = heldRolesByProvider.get(providerNo);
                                    String heldRolesJson = HELD_ROLES_MAPPER.writeValueAsString(
                                            heldRoles != null ? heldRoles : Collections.emptyList());
                        %>
                        <option value="<carlos:encode value='<%= providerNo %>' context="htmlAttribute"/>"
                                data-roles="<carlos:encode value='<%= heldRolesJson %>' context="htmlAttribute"/>"><carlos:encode value='<%= prop.getProperty("last_name") + "," + prop.getProperty("first_name") %>' context="html"/>
                        </option>
                        <%
                                    temp1.add(providerNo);
                                }
                            }
                        %>
                    </select>
                </td>
            </tr>

            <tr>
                <td>
                    <label class="form-label" for="primaryRoleRole"><fmt:message key="role"/>:</label>
                    <%-- Options are populated by primaryRoleChooseProvider() from the selected
                         provider's data-roles, so only roles that provider holds are offered. --%>
                    <select id="primaryRoleRole" name="primaryRoleRole">
                        <option value=""><fmt:message key="admin.providerupdateprovider.selectBelow"/></option>
                    </select>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="hidden" name="scrollPosition" class="scrollPosition" />
                    <input type="submit" name="buttonSetPrimaryRole"
                           value="<fmt:message key="global.update"/>&nbsp;<fmt:message key="demographic.demographiceditdemographic.primaryEMR"/>&nbsp;<fmt:message key="role"/>"
                           class="btn btn-primary" onClick="return setPrimaryRole();">
                </td>
            </tr>
        </table>
    </form>
</div>
<% } %>

</body>
</html>
