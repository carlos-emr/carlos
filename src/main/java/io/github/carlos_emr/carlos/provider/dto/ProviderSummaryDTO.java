/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.provider.dto;

import java.io.Serializable;

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Lightweight data transfer object for provider name lookups and list display,
 * optimized for JPQL constructor expression projection. Carries only the fields
 * needed for provider identification in dropdowns, autocomplete, and list views.
 *
 * <p>Omits billing numbers, phone, address, DOB, email, and other fields that
 * are only relevant on the provider admin/edit page.</p>
 *
 * @since 2026-04-11
 */
public class ProviderSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String providerNo;
    private String lastName;
    private String firstName;
    private String specialty;
    private String status;
    private String team;

    /**
     * Default constructor required by frameworks.
     */
    public ProviderSummaryDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions. Parameter order
     * must match the SELECT NEW clause exactly.
     *
     * @param providerNo String the provider number
     * @param lastName String the provider's last name
     * @param firstName String the provider's first name
     * @param specialty String the provider's specialty
     * @param status String the provider's active status ("1" = active, "0" = inactive)
     * @param team String the provider's team assignment
     */
    public ProviderSummaryDTO(String providerNo, String lastName, String firstName,
                              String specialty, String status, String team) {
        this.providerNo = providerNo;
        this.lastName = lastName;
        this.firstName = firstName;
        this.specialty = specialty;
        this.status = status;
        this.team = team;
    }

    /**
     * Creates a ProviderSummaryDTO from a full Provider entity, copying only the
     * summary fields.
     *
     * @param provider Provider the entity to convert; must not be null
     * @return ProviderSummaryDTO a lightweight projection of the provider
     */
    public static ProviderSummaryDTO fromEntity(Provider provider) {
        return new ProviderSummaryDTO(
                provider.getProviderNo(),
                provider.getLastName(),
                provider.getFirstName(),
                provider.getSpecialty(),
                provider.getStatus(),
                provider.getTeam()
        );
    }

    /**
     * Returns the provider's formatted name as "LastName, FirstName".
     *
     * @return String the formatted name, or "N/A" if both names are null
     */
    public String getFormattedName() {
        if (lastName == null && firstName == null) {
            return "N/A";
        }
        StringBuilder sb = new StringBuilder();
        if (lastName != null) {
            sb.append(lastName.trim());
        }
        if (firstName != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(firstName.trim());
        }
        return sb.toString();
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }
}
