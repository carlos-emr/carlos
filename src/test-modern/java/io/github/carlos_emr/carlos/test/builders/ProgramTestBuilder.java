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

import io.github.carlos_emr.carlos.PMmodule.model.Program;

/**
 * Test data builder for {@link Program} entities.
 *
 * <p>Provides deterministic defaults for program management test data.
 * Note: Program uses a manually-assigned ID (not auto-generated), so tests
 * must set IDs explicitly to avoid conflicts.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Program program = ProgramTestBuilder.aProgram().build();
 * Program community = ProgramTestBuilder.aProgram()
 *     .withId(10010)
 *     .withName("Community Program")
 *     .withType("community")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class ProgramTestBuilder {

    private Integer id;
    private String name = "Test Program";
    private String description = "Test program for automated tests";
    private String type = "Service";
    private String programStatus = "active";
    private int facilityId = 1;
    private int maxAllowed = 100;
    private boolean holdingTank = false;
    private boolean allowBatchAdmission = false;
    private boolean allowBatchDischarge = false;
    private boolean hic = false;
    private String exclusiveView = "no";
    private String manOrWoman = "B";

    private ProgramTestBuilder() {
    }

    /**
     * Creates a new builder with standard program defaults.
     *
     * @return a new builder instance
     */
    public static ProgramTestBuilder aProgram() {
        return new ProgramTestBuilder();
    }

    public ProgramTestBuilder withId(Integer id) {
        this.id = id;
        return this;
    }

    public ProgramTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ProgramTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public ProgramTestBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public ProgramTestBuilder withProgramStatus(String programStatus) {
        this.programStatus = programStatus;
        return this;
    }

    public ProgramTestBuilder withFacilityId(int facilityId) {
        this.facilityId = facilityId;
        return this;
    }

    public ProgramTestBuilder withMaxAllowed(int maxAllowed) {
        this.maxAllowed = maxAllowed;
        return this;
    }

    public ProgramTestBuilder withExclusiveView(String exclusiveView) {
        this.exclusiveView = exclusiveView;
        return this;
    }

    /**
     * Creates a community-type program.
     *
     * @return this builder
     */
    public ProgramTestBuilder asCommunity() {
        this.type = "community";
        this.name = "Community Program";
        return this;
    }

    /**
     * Creates an inactive program.
     *
     * @return this builder
     */
    public ProgramTestBuilder inactive() {
        this.programStatus = "inactive";
        return this;
    }

    public Program build() {
        Program p = new Program();
        if (id != null) {
            p.setId(id);
        }
        p.setName(name);
        p.setDescription(description);
        p.setType(type);
        p.setProgramStatus(programStatus);
        p.setFacilityId(facilityId);
        p.setMaxAllowed(maxAllowed);
        p.setHoldingTank(holdingTank);
        p.setAllowBatchAdmission(allowBatchAdmission);
        p.setAllowBatchDischarge(allowBatchDischarge);
        p.setHic(hic);
        p.setExclusiveView(exclusiveView);
        p.setManOrWoman(manOrWoman);
        return p;
    }
}
