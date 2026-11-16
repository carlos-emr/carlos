/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collection;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class SecObjPrivilegeDaoImpl extends AbstractDaoImpl<SecObjPrivilege> implements SecObjPrivilegeDao {

    public SecObjPrivilegeDaoImpl() {
        super(SecObjPrivilege.class);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto readCdsExportMergePrivilegeSchema() {
        return CdsExportPatientTableReadSupport.mergePrivilegeSchema(entityManager);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<io.github.carlos_emr.carlos.commn.dto.CdsExportMergePrivilegeRowDto> findCdsExportMergePrivileges(int patient) {
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_MERGE_QUERY_INVALID");
        // The row is found by the same comparison CARLOS uses (the columns' collation); its
        // stored bytes are read as they are, so no driver substitution can change a value.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = CdsExportPatientTableReadSupport.query(entityManager,
                        "select CAST(p.`roleUserGroup` AS BINARY), CAST(p.`objectName` AS BINARY),"
                                + " CAST(p.`privilege` AS BINARY), CAST(p.`priority` AS BINARY),"
                                + " CAST(p.`provider_no` AS BINARY) from `secObjPrivilege` p"
                                + " where p.`roleUserGroup`=:role and p.`objectName`=:object")
                .setParameter("role", io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.PRIVILEGE_ROLE)
                .setParameter("object", io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.PRIVILEGE_OBJECT_PREFIX + patient)
                .getResultList();
        var result = new java.util.ArrayList<io.github.carlos_emr.carlos.commn.dto.CdsExportMergePrivilegeRowDto>(rows.size());
        for (Object[] row : rows) {
            if (row == null || row.length != 5) throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID");
            result.add(new io.github.carlos_emr.carlos.commn.dto.CdsExportMergePrivilegeRowDto(
                    text(row[0]), text(row[1]), text(row[2]), text(row[3]), text(row[4])));
        }
        return result;
    }

    /** Strict UTF-8 text of a stored value read as bytes; SQL NULL stays null. */
    private static String text(Object value) {
        if (value == null) return null;
        if (!(value instanceof byte[] bytes)) throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID");
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID");
        }
    }

    @Override
    public List<SecObjPrivilege> findByRoleUserGroupAndObjectName(String roleUserGroup, String objectName) {
        String sql = "select s FROM SecObjPrivilege s WHERE s.id.roleUserGroup = ?1 AND  s.id.objectName = ?2";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, roleUserGroup);
        query.setParameter(2, objectName);


        List<SecObjPrivilege> result = query.getResultList();

        return result;
    }

    @Override
    public List<SecObjPrivilege> findByObjectNames(Collection<String> objectNames) {
        String sql = "select s FROM SecObjPrivilege s WHERE s.id.objectName IN (?1) order by s.priority desc";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, objectNames);


        List<SecObjPrivilege> result = query.getResultList();

        return result;
    }

    @Override
    public List<SecObjPrivilege> findByRoleUserGroup(String roleUserGroup) {
        String sql = "select s FROM SecObjPrivilege s WHERE s.id.roleUserGroup like ?1 order by s.id.roleUserGroup, s.id.objectName";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, roleUserGroup);

        List<SecObjPrivilege> result = query.getResultList();

        return result;
    }

    @Override
    public List<SecObjPrivilege> findByObjectName(String objectName) {
        String sql = "select s FROM SecObjPrivilege s WHERE s.id.objectName like ?1 order by s.id.objectName, s.id.roleUserGroup";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, objectName);

        List<SecObjPrivilege> result = query.getResultList();

        return result;
    }

    @Override
    public int countObjectsByName(String objName) {
        String sql = "SELECT COUNT(*) FROM SecObjPrivilege p WHERE p.id.objectName = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, objName);
        List<Object> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            return 0;
        }
        return (((Long) resultList.get(0))).intValue();
    }

    @Override
    public List<Object[]> findByFormNamePrivilegeAndProviderNo(String formName, String privilege, String providerNo) {
        String sql = "SELECT p, r FROM SecObjPrivilege p, Secuserrole r "
                + "WHERE p.id.roleUserGroup = r.roleName "
                + "AND p.id.objectName = ?1 "
                + "AND p.privilege = ?2 "
                + "AND r.providerNo = ?3 "
                + "AND r.activeyn = 1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, formName);
        query.setParameter(2, privilege);
        query.setParameter(3, providerNo);
        return query.getResultList();
    }
}
