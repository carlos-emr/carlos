/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.service;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.admin.support.AssignableRoles;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a provider login together with the first role the administrator chose for it.
 *
 * <p>Issue #2689: Add a Login Record used to write only the {@code security} row. The new user
 * could authenticate but held no {@code secUserRole} row, so the schedule landing page refused
 * them with a bare "insufficient privileges" page. No role is ever granted implicitly; the
 * administrator picks one on the form, and this service writes the login and that role in one
 * transaction, so a failure cannot leave a login with no role or a role with no login.</p>
 *
 * <p>The role write matches the Add action of Assign Role to Provider ({@code providerRole.jsp}):
 * an active {@code secUserRole} row and, when new case management runs on the {@code OSCAR}
 * program, the provider's {@code program_provider} membership with that role. A login created
 * here is therefore indistinguishable from one created and then given a role on that page.</p>
 *
 * @since 2026-10-15
 */
@Service
@Transactional
public class ProviderLoginCreationService {

    /** Program that new case management files notes under (see providerRole.jsp). */
    static final String CASE_MANAGEMENT_PROGRAM_NAME = "OSCAR";

    private final SecurityDao securityDao;
    private final SecuserroleDao secuserroleDao;
    private final SecRoleDao secRoleDao;
    private final ProgramDao programDao;
    private final ProgramProviderDAO programProviderDao;
    private final SecurityInfoManager securityInfoManager;

    public ProviderLoginCreationService(SecurityDao securityDao,
                                        SecuserroleDao secuserroleDao,
                                        SecRoleDao secRoleDao,
                                        ProgramDao programDao,
                                        ProgramProviderDAO programProviderDao,
                                        SecurityInfoManager securityInfoManager) {
        this.securityDao = securityDao;
        this.secuserroleDao = secuserroleDao;
        this.secRoleDao = secRoleDao;
        this.programDao = programDao;
        this.programProviderDao = programProviderDao;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Role names the acting administrator may give a new login.
     *
     * <p>Same list as the Assign Role to Provider dropdown: every configured role, less the
     * multisite super-root role for a site-scoped administrator ({@link AssignableRoles}). The
     * Add a Login Record form renders this list and the server checks the submitted role against
     * it, so a crafted POST cannot hand out a role the dropdown withholds.</p>
     *
     * @param loggedInInfo the administrator creating the login
     * @return assignable role names in role-name order; never {@code null}
     */
    @Transactional(readOnly = true)
    public List<String> assignableRoleNames(LoggedInInfo loggedInInfo) {
        List<String> allRoleNames = new ArrayList<>();
        for (SecRole role : secRoleDao.findAllOrderByRole()) {
            allRoleNames.add(role.getName());
        }
        boolean siteAccessPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", SecurityInfoManager.READ, null);
        return AssignableRoles.filter(
                allRoleNames,
                siteAccessPrivacy,
                IsPropertiesOn.isMultisitesEnable(),
                CarlosProperties.getInstance().getProperty("multioffice.admin.role.name", ""));
    }

    /**
     * Persists a new login and gives its provider the chosen role.
     *
     * <p>The caller has already validated the credentials and checked {@code roleName} against
     * {@link #assignableRoleNames(LoggedInInfo)}; the role is looked up again here so a role
     * deleted in the meantime fails the whole creation instead of writing a dangling name.</p>
     *
     * @param login    the unsaved login row, with its provider number set
     * @param roleName the role the administrator selected
     * @throws UnknownRoleException when {@code roleName} names no configured role; nothing is
     *                              written
     */
    public void createLoginWithRole(Security login, String roleName) {
        SecRole role = roleName == null ? null : secRoleDao.findByName(roleName);
        if (role == null) {
            throw new UnknownRoleException();
        }

        securityDao.persist(login);

        Secuserrole assignment = new Secuserrole();
        assignment.setProviderNo(login.getProviderNo());
        assignment.setRoleName(role.getName());
        assignment.setActiveyn(1);
        secuserroleDao.save(assignment);

        Long programId = caseManagementProgramId();
        if (programId != null) {
            ProgramProvider membership = programProviderDao.getProgramProvider(login.getProviderNo(), programId);
            if (membership == null) {
                membership = new ProgramProvider();
                membership.setProgramId(programId);
                membership.setProviderNo(login.getProviderNo());
            }
            membership.setRoleId(role.getId().longValue());
            programProviderDao.saveProgramProvider(membership);
        }
    }

    /** The chosen role is not configured (any more); the login was not created. */
    public static final class UnknownRoleException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnknownRoleException() {
            super("unknown role");
        }
    }

    /**
     * The {@code OSCAR} program id when new case management is in use, else {@code null}.
     *
     * <p>providerRole.jsp also counts a non-empty servlet-context {@code CaseMgmtUsers} list, but
     * Login2Action only fills that list when CAISI is enabled, and CAISI being enabled already
     * rules the program out; the property alone decides it.</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private Long caseManagementProgramId() {
        if (IsPropertiesOn.isCaisiEnable()
                || !"all".equalsIgnoreCase(CarlosProperties.getInstance().getProperty("CASEMANAGEMENT", ""))) {
            return null;
        }
        Program program = programDao.getProgramByName(CASE_MANAGEMENT_PROGRAM_NAME);
        return program == null || program.getId() == null ? null : program.getId().longValue();
    }
}
