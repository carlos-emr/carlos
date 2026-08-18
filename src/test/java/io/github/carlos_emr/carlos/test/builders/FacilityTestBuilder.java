/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Facility;

/**
 * Test data builder for {@link Facility} entities.
 *
 * <p>Provides deterministic defaults for healthcare facility test data.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Facility facility = FacilityTestBuilder.aFacility().build();
 * Facility clinic = FacilityTestBuilder.aFacility()
 *     .withName("Downtown Clinic")
 *     .withDescription("Primary care clinic")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class FacilityTestBuilder {

    private String name = "Test Facility";
    private String description = "Test facility for automated tests";
    private String contactName = "Admin";
    private String contactEmail = "admin@testfacility.com";
    private String contactPhone = "416-555-0300";
    private boolean hic = false;
    private boolean disabled = false;
    private Integer orgId;
    private Integer sectorId;
    private boolean enableHealthNumberRegistry = true;

    private FacilityTestBuilder() {
    }

    /**
     * Creates a new builder with standard facility defaults.
     *
     * @return a new builder instance
     */
    public static FacilityTestBuilder aFacility() {
        return new FacilityTestBuilder();
    }

    public FacilityTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public FacilityTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public FacilityTestBuilder withContactName(String contactName) {
        this.contactName = contactName;
        return this;
    }

    public FacilityTestBuilder withContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
        return this;
    }

    public FacilityTestBuilder withContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
        return this;
    }

    public FacilityTestBuilder withHic(boolean hic) {
        this.hic = hic;
        return this;
    }

    public FacilityTestBuilder withDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public FacilityTestBuilder withOrgId(Integer orgId) {
        this.orgId = orgId;
        return this;
    }

    /**
     * Creates a disabled facility.
     *
     * @return this builder
     */
    public FacilityTestBuilder disabled() {
        this.disabled = true;
        return this;
    }

    public Facility build() {
        Facility f = new Facility();
        f.setName(name);
        f.setDescription(description);
        f.setContactName(contactName);
        f.setContactEmail(contactEmail);
        f.setContactPhone(contactPhone);
        f.setHic(hic);
        f.setDisabled(disabled);
        f.setOrgId(orgId);
        f.setSectorId(sectorId);
        f.setEnableHealthNumberRegistry(enableHealthNumberRegistry);
        return f;
    }
}
