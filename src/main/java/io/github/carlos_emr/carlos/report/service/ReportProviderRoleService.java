/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validated role administration for the PHCP report's provider settings.
 *
 * @since 2026-10-17
 */
@Service
public class ReportProviderRoleService {
    public static final List<String> CLINICAL_ROLES = List.of("doctor", "resident", "nurse", "social worker", "other");
    private final SecurityInfoManager security;
    private final SecuserroleDao roles;
    private final ProviderDao providers;

    public ReportProviderRoleService(SecurityInfoManager security, SecuserroleDao roles, ProviderDao providers) {
        this.security = security;
        this.roles = roles;
        this.providers = providers;
    }

    public boolean canManage(LoggedInInfo user) {
        return user != null && (security.hasPrivilege(user, "_admin", "w", null)
                || security.hasPrivilege(user, "_admin.userAdmin", "w", null));
    }

    private void requireWrite(LoggedInInfo user) {
        if (!canManage(user)) throw new SecurityException("missing required sec object (_admin.userAdmin w)");
    }

    private void validate(String provider, String role) {
        if (provider == null || !provider.matches("[0-9]+") || !CLINICAL_ROLES.contains(role == null ? "" : role)
                || providers.getProvider(provider) == null) {
            throw new IllegalArgumentException("Select an existing provider and an allowed clinical role.");
        }
    }

    @Transactional
    public void update(LoggedInInfo user, String provider, String role) {
        requireWrite(user);
        validate(provider, role);
        List<Secuserrole> assignments = roles.findByProviderNo(provider);
        List<Secuserrole> existing = assignments.stream()
                .filter(row -> row.getRoleName() != null && CLINICAL_ROLES.contains(row.getRoleName())).toList();
        if (existing.isEmpty()) throw new IllegalArgumentException("No clinical role exists for this provider.");
        // Never replace unrelated administrator/program roles from this clinical-role report.
        for (Secuserrole row : existing) roles.updateRoleName(row.getId(), role);
    }

    @Transactional
    public void add(LoggedInInfo user, Map<String, String> requested) {
        requireWrite(user);
        Map<String, String> additions = new LinkedHashMap<>();
        // Validate the whole batch before the first write; existing role assignments are preserved.
        requested.forEach((provider, role) -> {
            validate(provider, role);
            if (roles.findByProviderNo(provider).isEmpty()) additions.put(provider, role);
        });
        additions.forEach((provider, role) -> {
            Secuserrole row = new Secuserrole();
            row.setProviderNo(provider);
            row.setRoleName(role);
            roles.save(row);
        });
    }
}
