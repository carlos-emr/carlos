//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.casemgmt.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tools.ant.util.DateUtils;
import ca.openosp.openo.commn.dao.AdmissionDao;
import ca.openosp.openo.PMmodule.dao.ProgramAccessDAO;
import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.PMmodule.model.DefaultRoleAccess;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramAccess;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.dao.RoleProgramAccessDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.commn.dao.ProviderDefaultProgramDao;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.ProviderDefaultProgram;
import ca.openosp.openo.utility.JsDateSerializer;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.daos.security.SecroleDao;
import ca.openosp.openo.model.security.Secrole;

/**
 * This class describes a servlet that allows one to change and view the permissions and scope of a CaseManagementNote object.
 *
 * @author jennifer@indivica.com
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class NotePermissions2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("getNoteScope".equals(method)) {
            return getNoteScope();
        } else if ("setNotePermissions".equals(method)) {
            return setNotePermissions();
        } else if ("getDefaultProgramAndRole".equals(method)) {
            return getDefaultProgramAndRole();
        } else if ("visibleProgramsAndRoles".equals(method)) {
            return visibleProgramsAndRoles();
        }
        return getNoteScope();
    }

    public String getNoteScope() throws Exception {
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        CaseManagementNoteDAO noteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        RoleProgramAccessDAO roleProgramAccessDao = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);

        String noteId = request.getParameter("noteId");
        String programNo, role = "";
        // Get the note
        if (noteId != null && noteId.equalsIgnoreCase("0") && request.getParameter("programNo") != null) {
            programNo = request.getParameter("programNo");
        } else {
            CaseManagementNote note = noteDao.getNote(Long.parseLong(noteId));
            programNo = note.getProgram_no();
            role = note.getReporter_caisi_role();
        }
        Program program = programDao.getProgram(Integer.parseInt(programNo));

        List<ProgramAccess> programAccessList = programAccessDAO.getAccessListByProgramId(Long.valueOf(programNo));
        Map<String, ProgramAccess> programAccessMap = convertProgramAccessListToMap(programAccessList);


        if (noteId != null && noteId.equalsIgnoreCase("0") && request.getParameter("roleId") != null)
            role = request.getParameter("roleId");

        String roleName = secroleDao.getRole(Integer.parseInt(role)).getName().toLowerCase();

        @SuppressWarnings("unchecked")
        List<ProgramProvider> programProviders = programProviderDao.getProgramProviders(Long.parseLong(programNo));

        List<NotePermission> permissionList = new ArrayList<NotePermission>();

        for (ProgramProvider provider : programProviders) {
            if (programAccessMap.containsKey("read " + roleName + " notes")) {
                ProgramAccess programAccess = programAccessMap.get("read " + roleName + " notes");
                if (programAccess.isAllRoles()) {
                    // all roles have access to this permission
                    permissionList.add(new NotePermission(provider, NotePermission.AccessType.ALL_ROLES));
                    continue;
                } else {
                    boolean hasRoleAccess = false;
                    for (Secrole programRole : programAccess.getRoles()) {
                        if (programRole.getId().longValue() == provider.getRoleId().longValue()) {
                            // This providers has access based on their role
                            hasRoleAccess = true;
                        }
                    }

                    if (hasRoleAccess) // This role is included in the list of roles granted this permission
                        permissionList.add(new NotePermission(provider, NotePermission.AccessType.ROLE));
                    else // The providers's role is not included in the list of roles granted this permission
                        permissionList.add(new NotePermission(provider, NotePermission.AccessType.NO_ACCESS));
                }
            } else if (roleProgramAccessDao.hasAccess("read " + roleName + " notes", provider.getRoleId())) {
                // Provider's role has access based on global role permissions
                permissionList.add(new NotePermission(provider, NotePermission.AccessType.ROLE_GLOBAL));
            } else {
                // Provider does not have access
                permissionList.add(new NotePermission(provider, NotePermission.AccessType.NO_ACCESS));
            }
        }

        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("programName", program.getName());
        hashMap.put("programNo", program.getId());
        hashMap.put("roleName", roleName);
        hashMap.put("permissionList", permissionList);

        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.getOutputStream().write(json.toString().getBytes());
        return null;

    }

    public String setNotePermissions() throws Exception {
        CaseManagementNoteDAO noteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);

        String noteId = request.getParameter("noteId");
        String newProgramNo = request.getParameter("program");
        String newRoleId = request.getParameter("role");

        HashMap<String, Object> hashMap = new HashMap<String, Object>();

        hashMap.put("noteId", noteId);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        Provider provider = loggedInInfo.getLoggedInProvider();

        if (canNoteBeModified(providerNo, noteId)) {
            CaseManagementNote note = noteDao.getNote(Long.parseLong(noteId));


            // Are this note's permissions being changed after all?
            if (note.getReporter_caisi_role() != null && note.getReporter_caisi_role().equalsIgnoreCase(newRoleId)
                    && note.getProgram_no() != null && note.getProgram_no().equalsIgnoreCase(newProgramNo)) {
                hashMap.put("success", "NO_CHANGE");
            } else if (providerHasAccess(providerNo, newProgramNo, newRoleId, note.getDemographic_no())) {
                // Check if this providers has access to the requested program & role pair
                Program p = programDao.getProgram(Integer.parseInt(newProgramNo));
                Secrole r = secroleDao.getRole(Integer.parseInt(newRoleId));

                note.setProgram_no(newProgramNo);
                note.setReporter_caisi_role(newRoleId);
                Date newDate = new Date();
                note.setUpdate_date(newDate);

                String newNote = note.getNote();
                newNote += "\n[" + DateUtils.format(newDate, "yyyy-MM-dd") + " Program/role changed to: " + p.getName() + " (" + r.getName() + ") by " + provider.getLastName() + ", " + provider.getFirstName() + "]";
                note.setNote(newNote);

                Object id = noteDao.saveAndReturn(note);

                hashMap.put("success", "SAVED");
                hashMap.put("newProgramNo", newProgramNo);
                hashMap.put("newRoleId", newRoleId);
                hashMap.put("newProgramName", p.getName());
                hashMap.put("newRoleName", r.getName());
                hashMap.put("noteContent", newNote);
                hashMap.put("newNoteId", id);
            } else {
                // The providers making this modification won't be able to see the note if this change is made
                hashMap.put("error", "MAKES_INVISIBLE");
            }
        } else {
            // The providers doesn't have permission to see this note, so they can't modify it
            hashMap.put("error", "PERMISSION_DENIED");
        }

        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.getOutputStream().write(json.toString().getBytes());

        return null;
    }

    public String getDefaultProgramAndRole() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String demoNo = request.getParameter("demoNo");

        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProviderDefaultProgramDao defaultProgramDao = (ProviderDefaultProgramDao) SpringUtils.getBean(ProviderDefaultProgramDao.class);

        @SuppressWarnings("unchecked")
        List<ProviderDefaultProgram> programs = defaultProgramDao.getProgramByProviderNo(providerNo);

        HashMap<String, Object> hashMap = new HashMap<String, Object>();

        HashMap<Program, List<Secrole>> rolesForDemo = getAllProviderAccessibleRolesForDemo(providerNo, demoNo);
        for (ProviderDefaultProgram pdp : programs) {
            for (Program p : rolesForDemo.keySet()) {
                if (pdp.getProgramId() == p.getId().intValue()) {
                    List<ProgramProvider> programProviderList = programProviderDao.getProgramProviderByProviderProgramId(providerNo, (long) pdp.getProgramId());
                    hashMap.put("success", true);
                    hashMap.put("defaultProgram", pdp.getProgramId());
                    hashMap.put("defaultProgramName", p.getName());
                    hashMap.put("defaultRole", programProviderList.get(0).getRoleId());
                    hashMap.put("defaultRoleName", programProviderList.get(0).getRole().getName());

                    ObjectNode json = objectMapper.valueToTree(hashMap);
                    response.getOutputStream().write(json.toString().getBytes());

                    return null;
                }
            }
        }

        // The providers default program is not one of the programs that the patient is admitted to,
        // so choose the first one that they're admitted to that the providers can access
        HashMap<Program, List<Secrole>> providerDemoPrograms = getAllProviderAccessibleRolesForDemo(providerNo, demoNo);
        if (!providerDemoPrograms.isEmpty()) {
            Program program = providerDemoPrograms.keySet().iterator().next();
            ProgramProvider programProvider = programProviderDao.getProgramProvider(providerNo, (long) program.getId());

            hashMap.put("success", true);
            hashMap.put("defaultProgram", program.getId());
            hashMap.put("defaultRole", programProvider.getRoleId());
            hashMap.put("defaultProgramName", program.getName());
            hashMap.put("defaultRoleName", programProvider.getRole().getName());

        } else
            hashMap.put("success", false);

        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.getOutputStream().write(json.toString().getBytes());

        return null;
    }

    public String visibleProgramsAndRoles() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String demoNo = request.getParameter("demoNo");
        HashMap<Program, List<Secrole>> visibleMap = getAllProviderAccessibleRolesForDemo(providerNo, demoNo);

        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();

        for (Program p : visibleMap.keySet()) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("programNo", p.getId());
            map.put("programName", p.getName());

            map.put("roleAccess", visibleMap.get(p));

            mapList.add(map);
        }

//		for (HashMap<String, String> p : getDischargedPrograms(demoNo)) {
//			HashMap<String, Object> map = new HashMap<String, Object>();
//			map.put("programNo", p.get("programNo"));
//			map.put("programName", p.get("programName") + " (discharged)");
//
//			map.put("roleAccess", new ArrayList<String>());
//
//			mapList.add(map);
//		}

        hashMap.put("programs", mapList);


        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.getOutputStream().write(json.toString().getBytes());

        return null;
    }

    private boolean canNoteBeModified(String providerNo, String noteId) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        CaseManagementNoteDAO noteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        RoleProgramAccessDAO roleProgramAccessDao = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);

        // Get the note
        CaseManagementNote note = noteDao.getNote(Long.parseLong(noteId));
        CaseManagementNote mostRecentNote = noteDao.getMostRecentNote(note.getUuid());
        if (note.getId().longValue() != mostRecentNote.getId().longValue())
            return false;

        String programNo = note.getProgram_no();

        List<ProgramAccess> programAccessList = programAccessDAO.getAccessListByProgramId(Long.valueOf(programNo));
        Map<String, ProgramAccess> programAccessMap = convertProgramAccessListToMap(programAccessList);


        String role = note.getReporter_caisi_role();
        String roleName = secroleDao.getRole(Integer.parseInt(role)).getName().toLowerCase();

        List<ProgramProvider> programProviderList = programProviderDao.getProgramProviderByProviderProgramId(providerNo, Long.parseLong(programNo));


        for (ProgramProvider provider : programProviderList) {
            if (programAccessMap.containsKey("read " + roleName + " notes")) {
                ProgramAccess programAccess = programAccessMap.get("read " + roleName + " notes");
                if (programAccess.isAllRoles()) {
                    // all roles have access to this permission
                    return true;
                } else {
                    for (Secrole programRole : programAccess.getRoles()) {
                        if (programRole.getId().longValue() == provider.getRoleId().longValue()) {
                            // This providers has access based on their role
                            return true;
                        }
                    }
                }
            } else if (roleProgramAccessDao.hasAccess("read " + roleName + " notes", provider.getRoleId())) {
                // Provider's role has access based on global role permissions
                return true;
            }
        }

        return false;
    }

    public boolean providerHasAccess(String providerNo, String programNo, String roleId, String demographicNo) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        RoleProgramAccessDAO roleProgramAccessDao = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);
        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);

        List<ProgramAccess> programAccessList = programAccessDAO.getAccessListByProgramId(Long.valueOf(programNo));
        Map<String, ProgramAccess> programAccessMap = convertProgramAccessListToMap(programAccessList);
        String roleName = secroleDao.getRole(Integer.parseInt(roleId)).getName().toLowerCase();

        List<ProgramProvider> programProviderList = programProviderDao.getProgramProviderByProviderProgramId(providerNo, Long.parseLong(programNo));

        Admission demoAdmission = admissionDao.getAdmission(Integer.parseInt(programNo), Integer.parseInt(demographicNo));

        for (ProgramProvider provider : programProviderList) {
            if (programAccessMap.containsKey("read " + roleName + " notes")) {
                for (Secrole programRole : programAccessMap.get("read " + roleName + " notes").getRoles()) {
                    if (programRole.getId().longValue() == provider.getRoleId().longValue()) {
                        // This providers has access based on their role
                        // Return whether the demo has an active admission to this program
                        return (demoAdmission != null);
                    }
                }
            } else if (roleProgramAccessDao.hasAccess("read " + roleName + " notes", provider.getRoleId())) {
                // Provider's role has access based on global role permissions
                // Check whether the demo's admission is current to this program
                return (demoAdmission != null);
            }
        }

        return false;

    }

    public static HashMap<Program, List<Secrole>> getAllProviderAccessibleRolesForDemo(String providerNo, String demoNo) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        RoleProgramAccessDAO roleProgramAccessDao = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);
        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);

        HashMap<Program, List<Secrole>> visibleRoles = new HashMap<Program, List<Secrole>>();

        @SuppressWarnings("unchecked")
        List<ProgramProvider> programProviderList = programProviderDao.getProgramProvidersByProvider(providerNo);

        List<Integer> demoPrograms = new ArrayList<Integer>();
        for (Admission a : admissionDao.getCurrentAdmissions(Integer.parseInt(demoNo))) {
            demoPrograms.add(a.getProgramId());
        }

        for (ProgramProvider provider : programProviderList) {
            if (!demoPrograms.contains(provider.getProgram().getId()))
                continue;

            if (!visibleRoles.containsKey(provider.getProgram())) {
                visibleRoles.put(provider.getProgram(), new ArrayList<Secrole>());
            }

            List<Secrole> roleList = visibleRoles.get(provider.getProgram());
            if (!roleList.contains(provider.getRole())) {
                roleList.add(provider.getRole());

                // This role definitely has access to these permissions -> get role names and add to list
                List<DefaultRoleAccess> defaultAccess = roleProgramAccessDao.getDefaultSpecificAccessRightByRole(provider.getRoleId(), "read%notes");
                for (DefaultRoleAccess access : defaultAccess) {
                    String roleName = access.getAccess_type().getName().substring(5, access.getAccess_type().getName().length() - 6);
                    Secrole role = secroleDao.getRoleByName(roleName);
                    if (!roleList.contains(role))
                        roleList.add(role);
                }

                // This role also has access to these permissions -> add them to the list as well
                List<ProgramAccess> programAccess = programAccessDAO.getProgramAccessListByType(provider.getProgramId(), "read%notes");
                for (ProgramAccess access : programAccess) {
                    if (access.getRoles().contains(provider.getRole())) {
                        String roleName = access.getAccessType().getName().substring(5, access.getAccessType().getName().length() - 6);
                        Secrole role = secroleDao.getRoleByName(roleName);
                        if (!roleList.contains(role))
                            roleList.add(role);
                    }
                }
            }
        }

        return visibleRoles;
    }

    private Map<String, ProgramAccess> convertProgramAccessListToMap(List<ProgramAccess> paList) {
        Map<String, ProgramAccess> map = new HashMap<String, ProgramAccess>();
        if (paList == null) return map;
        for (ProgramAccess pa : paList) {
            map.put(pa.getAccessType().getName().toLowerCase(), pa);
        }
        return map;
    }
}
