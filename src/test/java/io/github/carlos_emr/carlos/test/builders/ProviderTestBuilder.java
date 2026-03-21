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

import io.github.carlos_emr.carlos.commn.model.Provider;

import java.util.Date;

/**
 * Test data builder for {@link Provider} entities.
 *
 * <p>Handles the dual Provider/SecProvider table mapping by satisfying NOT NULL
 * constraints from both HBM mappings. The {@code providerNo} field is VARCHAR(6)
 * so values must be 6 characters or fewer.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Provider doc = ProviderTestBuilder.aProvider().build();
 * Provider specialist = ProviderTestBuilder.aProvider()
 *     .withProviderNo("000002")
 *     .withLastName("Smith")
 *     .withProviderType("specialist")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class ProviderTestBuilder {

    private String providerNo = "999990";
    private String firstName = "TestDoc";
    private String lastName = "Provider";
    private String providerType = "doctor";
    private String sex = "M";
    private String specialty = "";
    private String status = "1";
    private String phone = "416-555-0200";
    private String workPhone = "";
    private String address = "";
    private String email = "provider@test.com";
    private String ohipNo = "";
    private String billingNo = "";
    private String hsoNo = "";
    private String team = "";
    private String comments = "";
    private Date lastUpdateDate = new Date(1704067200000L); // 2024-01-01

    private ProviderTestBuilder() {
    }

    /**
     * Creates a new builder with defaults that satisfy both Provider and SecProvider
     * NOT NULL constraints.
     *
     * @return a new builder instance
     */
    public static ProviderTestBuilder aProvider() {
        return new ProviderTestBuilder();
    }

    public ProviderTestBuilder withProviderNo(String providerNo) {
        this.providerNo = providerNo;
        return this;
    }

    public ProviderTestBuilder withFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public ProviderTestBuilder withLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public ProviderTestBuilder withProviderType(String providerType) {
        this.providerType = providerType;
        return this;
    }

    public ProviderTestBuilder withSex(String sex) {
        this.sex = sex;
        return this;
    }

    public ProviderTestBuilder withSpecialty(String specialty) {
        this.specialty = specialty;
        return this;
    }

    public ProviderTestBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public ProviderTestBuilder withPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public ProviderTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public ProviderTestBuilder withOhipNo(String ohipNo) {
        this.ohipNo = ohipNo;
        return this;
    }

    public ProviderTestBuilder withBillingNo(String billingNo) {
        this.billingNo = billingNo;
        return this;
    }

    public ProviderTestBuilder withTeam(String team) {
        this.team = team;
        return this;
    }

    /**
     * Creates an inactive provider.
     *
     * @return this builder
     */
    public ProviderTestBuilder inactive() {
        this.status = "0";
        return this;
    }

    public Provider build() {
        Provider p = new Provider();
        p.setProviderNo(providerNo);
        p.setFirstName(firstName);
        p.setLastName(lastName);
        p.setProviderType(providerType);
        p.setSex(sex);
        p.setSpecialty(specialty);
        p.setStatus(status);
        p.setPhone(phone);
        p.setWorkPhone(workPhone);
        p.setAddress(address);
        p.setEmail(email);
        p.setOhipNo(ohipNo);
        p.setBillingNo(billingNo);
        p.setHsoNo(hsoNo);
        p.setTeam(team);
        p.setComments(comments);
        p.setLastUpdateDate(lastUpdateDate);
        return p;
    }
}
