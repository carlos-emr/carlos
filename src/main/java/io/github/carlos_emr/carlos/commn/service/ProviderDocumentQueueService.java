/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a grantable document queue and its owning provider's default as one transaction.
 * No role privileges are assigned by queue creation.
 *
 * @since 2026-11-16
 */
@Service
@Transactional
public class ProviderDocumentQueueService {
    private final QueueDao queues;
    private final SecObjectNameDao securityObjects;
    private final UserPropertyDAO properties;

    /**
     * @param queues queue persistence
     * @param securityObjects security-object persistence used by document administration
     * @param properties provider preferences
     */
    public ProviderDocumentQueueService(QueueDao queues, SecObjectNameDao securityObjects, UserPropertyDAO properties) {
        this.queues = queues;
        this.securityObjects = securityObjects;
        this.properties = properties;
    }

    /**
     * @param providerNo provider from the authenticated session
     * @param name nonblank queue name, preserved as entered
     * @return the saved default-queue preference
     * @throws IllegalArgumentException when the name is blank
     * @throws IllegalStateException when queue persistence fails to assign an ID
     */
    public UserProperty createDefaultQueue(String providerNo, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document queue name is required");
        }
        Integer id = queues.addNewQueueAndGetId(name);
        if (id == null) {
            throw new IllegalStateException("Queue persistence did not assign an ID");
        }
        Secobjectname securityObject = new Secobjectname();
        securityObject.setObjectname("_queue." + id);
        securityObject.setDescription(name);
        securityObject.setOrgapplicable(0);
        securityObjects.saveOrUpdate(securityObject);
        UserProperty property = properties.getProp(providerNo, UserProperty.DOC_DEFAULT_QUEUE);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.DOC_DEFAULT_QUEUE);
        }
        property.setValue(id.toString());
        properties.saveProp(property);
        return property;
    }
}
