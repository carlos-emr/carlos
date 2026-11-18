/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Saves a provider's default document queue, including creation of a new grantable queue.
 * No role privileges are assigned by queue creation.
 *
 * @since 2026-11-16
 */
@Service
@Transactional
public class ProviderDocumentQueueService {
    private static final int MAX_QUEUE_NAME_CHARACTERS = 40;
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
     * @param name queue name with up to 40 characters after trimming
     * @return the saved default-queue preference
     * @throws IllegalArgumentException when the name is blank, too long or the provider does not exist
     * @throws IllegalStateException when queue persistence fails to assign an ID
     */
    public UserProperty createDefaultQueue(String providerNo, String name) {
        String queueName = StringUtils.trimToNull(name);
        if (StringUtils.isBlank(queueName)) {
            throw new IllegalArgumentException("Document queue name is required");
        }
        if (queueName.codePointCount(0, queueName.length()) > MAX_QUEUE_NAME_CHARACTERS) {
            throw new IllegalArgumentException("Document queue name exceeds 40 characters");
        }
        lockPreferenceOwner(providerNo);
        Integer id = queues.addNewQueueAndGetId(queueName);
        if (id == null) {
            throw new IllegalStateException("Queue persistence did not assign an ID");
        }
        Secobjectname securityObject = new Secobjectname();
        securityObject.setObjectname("_queue." + id);
        securityObject.setDescription(queueName);
        securityObject.setOrgapplicable(0);
        securityObjects.saveOrUpdate(securityObject);
        return saveDefaultPreference(providerNo, id.toString());
    }

    /**
     * @param providerNo provider from the authenticated session
     * @param queueId existing queue selected by the provider, or {@code -1} for None
     * @return the saved default-queue preference
     * @throws IllegalArgumentException when the provider or selected queue does not exist
     */
    public UserProperty selectDefaultQueue(String providerNo, String queueId) {
        lockPreferenceOwner(providerNo);
        int selected = Integer.parseInt(queueId);
        if (selected != -1 && !queues.lockExistingQueue(selected)) {
            throw new IllegalArgumentException("The selected document queue no longer exists");
        }
        return saveDefaultPreference(providerNo, Integer.toString(selected));
    }

    private void lockPreferenceOwner(String providerNo) {
        // Both routes acquire the owner lock before a query can establish the read snapshot.
        if (!properties.lockProviderPreferences(providerNo)) {
            throw new IllegalArgumentException("Unknown document queue preference owner");
        }
    }

    private UserProperty saveDefaultPreference(String providerNo, String queueId) {
        List<UserProperty> defaults = properties.getAllProperties(UserProperty.DOC_DEFAULT_QUEUE, List.of(providerNo));
        UserProperty property = defaults.stream().min(Comparator.comparing(UserProperty::getId)).orElse(null);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.DOC_DEFAULT_QUEUE);
        }
        property.setValue(queueId);
        properties.saveProp(property);
        for (UserProperty duplicate : defaults) {
            if (duplicate != property) {
                properties.delete(duplicate);
            }
        }
        return property;
    }
}
