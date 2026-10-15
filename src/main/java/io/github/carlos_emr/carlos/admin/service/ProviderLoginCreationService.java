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

import java.util.List;
import java.util.Objects;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.admin.support.AssignableRoles;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
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
 * here is therefore indistinguishable from one created and then given a role on that page. A
 * provider can already hold the role (Assign Role works before a login exists); that row is
 * reused, and reactivated if needed, so a later removal on that page cannot leave a duplicate
 * row still granting the role.</p>
 *
 * <p>Because a role now comes with the login, the server also re-checks which provider it is for
 * ({@link #isEligibleProvider}): a crafted POST must not create a login, and a role, for a
 * provider the form's dropdown withholds from this administrator.</p>
 *
 * <p>The login and role audit entries are written strictly, in the same transaction: a granted
 * role must never commit without its audit record, nor an audit record survive a rolled-back
 * grant.</p>
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
    private final ProviderDao providerDao;
    private final ProviderSiteDao providerSiteDao;
    private final SecurityInfoManager securityInfoManager;

    public ProviderLoginCreationService(SecurityDao securityDao,
                                        SecuserroleDao secuserroleDao,
                                        SecRoleDao secRoleDao,
                                        ProgramDao programDao,
                                        ProgramProviderDAO programProviderDao,
                                        ProviderDao providerDao,
                                        ProviderSiteDao providerSiteDao,
                                        SecurityInfoManager securityInfoManager) {
        this.securityDao = securityDao;
        this.secuserroleDao = secuserroleDao;
        this.secRoleDao = secRoleDao;
        this.programDao = programDao;
        this.programProviderDao = programProviderDao;
        this.providerDao = providerDao;
        this.providerSiteDao = providerSiteDao;
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
        return AssignableRoles.filter(
                secRoleDao.findAllOrderByRole().stream().map(SecRole::getName).toList(),
                hasSiteAccessPrivacy(loggedInInfo),
                IsPropertiesOn.isMultisitesEnable(),
                CarlosProperties.getInstance().getProperty("multioffice.admin.role.name", ""));
    }

    /**
     * Whether the administrator may create a login for this provider.
     *
     * <p>The same rule the Add a Login Record Provider dropdown applies: an active provider whose
     * number is not a system or imported one ({@code -} prefix), and, for a site-scoped
     * administrator on a multisite install, one sharing a site with them. The caller still
     * refuses a provider that already has a login.</p>
     *
     * @param loggedInInfo the administrator creating the login
     * @param providerNo   the submitted provider number
     * @return {@code true} when the dropdown would have offered this provider
     */
    @Transactional(readOnly = true)
    public boolean isEligibleProvider(LoggedInInfo loggedInInfo, String providerNo) {
        if (providerNo == null || providerNo.isEmpty() || providerNo.startsWith("-")) {
            return false;
        }
        if (IsPropertiesOn.isMultisitesEnable() && hasSiteAccessPrivacy(loggedInInfo)) {
            return providerSiteDao.findActiveProvidersBySharedSites(loggedInInfo.getLoggedInProviderNo()).stream()
                    .anyMatch(shared -> providerNo.equals(shared.getProviderNo()));
        }
        Provider provider = providerDao.getProvider(providerNo);
        return provider != null && "1".equals(provider.getStatus());
    }

    private boolean hasSiteAccessPrivacy(LoggedInInfo loggedInInfo) {
        return loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", SecurityInfoManager.READ, null);
    }

    /**
     * Persists a new login and gives its provider the chosen role.
     *
     * <p>The caller has already validated the credentials and checked {@code roleName} against
     * {@link #assignableRoleNames(LoggedInInfo)}; the role is looked up again here so a role
     * deleted in the meantime fails the whole creation instead of writing a dangling name.</p>
     *
     * @param loggedInInfo the administrator creating the login, recorded on the audit entries
     * @param login        the unsaved login row, with its provider number set
     * @param roleName     the role the administrator selected
     * @throws UnknownRoleException when {@code roleName} names no configured role; nothing is
     *                              written
     * @throws RuntimeException     when an audit entry cannot be persisted; the transaction,
     *                              including the login and the role, rolls back
     */
    public void createLoginWithRole(LoggedInInfo loggedInInfo, Security login, String roleName) {
        Objects.requireNonNull(loggedInInfo, "loggedInInfo");
        SecRole role = roleName == null ? null : secRoleDao.findByName(roleName);
        if (role == null) {
            throw new UnknownRoleException();
        }

        securityDao.persist(login);

        Secuserrole assignment = existingAssignment(login.getProviderNo(), role.getName());
        if (assignment == null) {
            assignment = new Secuserrole();
            assignment.setProviderNo(login.getProviderNo());
            assignment.setRoleName(role.getName());
        }
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

        // Same audit entries as before (and as Assign Role to Provider writes for a role), but
        // strict: they join this transaction instead of the best-effort asynchronous queue.
        LogAction.addLogStrict(loggedInInfo, LogConst.ADD, LogConst.CON_SECURITY, login.getUserName(), null, null);
        LogAction.addLogStrict(loggedInInfo, LogConst.ADD, LogConst.CON_ROLE,
                login.getProviderNo() + "|" + role.getName(), null, null);
    }

    /** The provider's existing assignment of this role, active or not, or {@code null}. */
    private Secuserrole existingAssignment(String providerNo, String roleName) {
        for (Object row : secuserroleDao.findByProviderNo(providerNo)) {
            Secuserrole assignment = (Secuserrole) row;
            if (roleName.equals(assignment.getRoleName())) {
                return assignment;
            }
        }
        return null;
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
