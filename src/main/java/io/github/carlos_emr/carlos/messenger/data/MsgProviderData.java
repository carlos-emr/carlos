/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.messenger.data;

import io.github.carlos_emr.carlos.caisi_integrator.ws.CachedProvider;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Data model representing a healthcare provider in the messaging system.
 * 
 * <p>This class encapsulates provider information for use in the messaging module,
 * supporting both local providers and remote providers from integrated facilities.
 * It serves as a unified representation of provider data across different sources,
 * including local database providers and cached providers from the CAISI integrator.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Support for both local and remote (integrated) providers</li>
 *   <li>Composite identification through ContactIdentifier</li>
 *   <li>Provider metadata including name, type, and location</li>
 *   <li>Group membership tracking for messaging groups</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @since 2002
 * @see ContactIdentifier
 * @see Provider
 * @see CachedProvider
 */
public final class MsgProviderData {

    /**
     * Composite identifier for the provider including facility and location information.
     */
    private ContactIdentifier id;
    
    /**
     * Provider's first name.
     */
    private String firstName;
    
    /**
     * Provider's last name.
     */
    private String lastName;
    
    /**
     * Professional prefix/title (e.g., "Dr.", "RN").
     */
    private String prefix;
    
    /**
     * Provider type or specialty (e.g., "physician", "nurse", "admin").
     */
    private String providerType;
    
    /**
     * Physical location or facility where the provider practices.
     */
    private String location;
    
    /**
     * Flag indicating if this provider is a member of a specific messaging group.
     */
    private boolean member;

    /**
     * Default constructor creating an empty provider data object.
     */
    public MsgProviderData() {
        // Default constructor for manual population
    }

    /**
     * Constructs provider data from a cached remote provider.
     * 
     * <p>This constructor is used when creating provider data from a remote
     * facility accessed through the CAISI integrator. The location is set
     * to "Integrator" to indicate this is a remote provider.</p>
     * 
     * @param cachedProvider The cached provider from a remote integrated facility
     */
    public MsgProviderData(CachedProvider cachedProvider) {
        getId().setContactId(cachedProvider.getFacilityIdStringCompositePk().getCaisiItemId());
        getId().setFacilityId(cachedProvider.getFacilityIdStringCompositePk().getIntegratorFacilityId());
        setFirstName(cachedProvider.getFirstName());
        setLastName(cachedProvider.getLastName());
        setLocation("Integrator");
        setProviderType(cachedProvider.getSpecialty());
    }

    /**
     * Constructs provider data from a local provider entity.
     * 
     * <p>This constructor is used for providers in the local database.
     * The facility ID is set to 0 to indicate a local provider.</p>
     * 
     * @param provider The local provider entity from the database
     */
    public MsgProviderData(Provider provider) {
        getId().setContactId(provider.getProviderNo());
        getId().setContactUniqueId(provider.getPractitionerNo());
        getId().setFacilityId(0);  // 0 indicates local facility
        setFirstName(provider.getFirstName());
        setLastName(provider.getLastName());
        setLocation(provider.getAddress());
        setProviderType(provider.getSpecialty());
    }

    /**
     * Gets the composite identifier for this provider.
     * 
     * <p>Lazily initializes the ContactIdentifier if not yet created.</p>
     * 
     * @return The provider's contact identifier, never null
     */
    public ContactIdentifier getId() {
        if (id == null) {
            id = new ContactIdentifier();
        }
        return id;
    }

    /**
     * Sets the composite identifier for this provider.
     * 
     * @param id The contact identifier to set
     */
    public void setId(ContactIdentifier id) {
        this.id = id;
    }

    /**
     * Gets the provider's first name.
     * 
     * @return The first name, or empty string if not set
     */
    public String getFirstName() {
        if (firstName == null) {
            return "";
        }
        return firstName;
    }

    /**
     * Sets the provider's first name.
     * 
     * @param firstName The first name to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the provider's last name.
     * 
     * @return The last name, or empty string if not set
     */
    public String getLastName() {
        if (lastName == null) {
            return "";
        }
        return lastName;
    }

    /**
     * Sets the provider's last name.
     * 
     * @param lastName The last name to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the provider's professional prefix or title.
     * 
     * @return The prefix (e.g., "Dr.", "RN"), or null if not set
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Sets the provider's professional prefix or title.
     * 
     * @param prefix The prefix to set
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets the provider type or specialty.
     * 
     * @return The provider type (e.g., "physician", "nurse")
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * Sets the provider type or specialty.
     * 
     * @param providerType The provider type to set
     */
    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    /**
     * Gets the provider's location or facility.
     * 
     * <p>For remote providers, this will be "Integrator".
     * For local providers, this is typically their address.</p>
     * 
     * @return The location string
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the provider's location or facility.
     * 
     * @param location The location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Checks if this provider is a member of a messaging group.
     * 
     * @return true if the provider is a group member, false otherwise
     */
    public boolean isMember() {
        return member;
    }

    /**
     * Sets the group membership status for this provider.
     * 
     * @param member true if the provider is a group member
     */
    public void setMember(boolean member) {
        this.member = member;
    }

}
