/**
 * Copyright (c) 2026. CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
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
 */
package io.github.carlos_emr.carlos.fhir.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client wrapper for communicating with the self-hosted Medplum FHIR R4 server.
 *
 * <p>Wraps the HAPI FHIR {@link IGenericClient} with CARLOS-specific defaults:
 * connection to the devcontainer Medplum instance, R4 context, and logging.</p>
 *
 * <p>This client is the single entry point for all FHIR server operations.
 * It is used by {@link io.github.carlos_emr.carlos.fhir.repository.FhirClinicalRepository}
 * and should not be called directly from managers or actions.</p>
 *
 * <p>Thread-safe: the underlying {@link FhirContext} and {@link IGenericClient}
 * are both thread-safe per HAPI FHIR documentation.</p>
 *
 * @since 2026-04-02
 */
public class MedplumFhirClient {

    private static final Logger logger = LoggerFactory.getLogger(MedplumFhirClient.class);

    /** Default Medplum server URL inside the devcontainer network. */
    private static final String DEFAULT_SERVER_URL = "http://medplum-server:8103/fhir/R4";

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    private final IGenericClient client;

    /**
     * Creates a client connected to the default devcontainer Medplum server.
     */
    public MedplumFhirClient() {
        this(DEFAULT_SERVER_URL);
    }

    /**
     * Creates a client connected to a Medplum server at the given URL.
     *
     * @param serverUrl the FHIR base URL (e.g., {@code http://medplum-server:8103/fhir/R4})
     */
    public MedplumFhirClient(String serverUrl) {
        FHIR_CONTEXT.getRestfulClientFactory()
                .setServerValidationMode(ServerValidationModeEnum.NEVER);
        FHIR_CONTEXT.getRestfulClientFactory()
                .setConnectTimeout(10_000);
        FHIR_CONTEXT.getRestfulClientFactory()
                .setSocketTimeout(30_000);
        this.client = FHIR_CONTEXT.newRestfulGenericClient(serverUrl);
        logger.info("MedplumFhirClient initialized for server: {}", serverUrl);
    }

    /**
     * Reads a single FHIR resource by type and ID.
     *
     * @param resourceType the resource class (e.g., {@code Patient.class})
     * @param id           the FHIR resource ID
     * @param <R>          the resource type
     * @return the resource, or {@code null} if not found
     */
    public <R extends Resource> R read(Class<R> resourceType, String id) {
        try {
            return client.read().resource(resourceType).withId(id).execute();
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Searches for FHIR resources with pagination.
     *
     * @param resourceType the resource class
     * @param offset       zero-based offset
     * @param count        maximum results to return
     * @param <R>          the resource type
     * @return a Bundle containing matching resources
     */
    public <R extends Resource> Bundle search(Class<R> resourceType, int offset, int count) {
        return client.search()
                .forResource(resourceType)
                .offset(offset)
                .count(count)
                .returnBundle(Bundle.class)
                .execute();
    }

    /**
     * Creates a new FHIR resource on the server, or updates it if it has an ID.
     *
     * @param resource the resource to create or update
     * @param <R>      the resource type
     * @return the server-assigned resource (with ID and metadata populated)
     */
    @SuppressWarnings("unchecked")
    public <R extends Resource> R createOrUpdate(R resource) {
        if (resource.hasId()) {
            IUpdateTyped update = client.update().resource(resource);
            update.execute();
            return resource;
        } else {
            ICreateTyped create = client.create().resource(resource);
            var outcome = create.execute();
            resource.setId(outcome.getId());
            return resource;
        }
    }

    /**
     * Deletes a FHIR resource by type and ID.
     *
     * @param resourceType the resource class
     * @param id           the FHIR resource ID
     * @param <R>          the resource type
     * @return {@code true} if deleted successfully
     */
    public <R extends Resource> boolean delete(Class<R> resourceType, String id) {
        try {
            client.delete().resourceById(resourceType.getSimpleName(), id).execute();
            return true;
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns the underlying HAPI FHIR generic client for advanced queries.
     *
     * <p>Prefer the typed methods ({@link #read}, {@link #search}, etc.) for
     * standard operations. Use this only for complex FHIR queries that require
     * direct client access (e.g., chained searches, includes, custom operations).</p>
     *
     * @return the HAPI FHIR generic client
     */
    public IGenericClient getClient() {
        return client;
    }

    /**
     * Returns the shared R4 FHIR context.
     *
     * @return the FHIR R4 context (singleton, thread-safe)
     */
    public static FhirContext getFhirContext() {
        return FHIR_CONTEXT;
    }
}
