//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incomingLabRules")
public class IncomingLabRules extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "provider_no")
    private String providerNo;

    private String status;

    @Column(name = "frwdProvider_no")
    private String frwdProviderNo;

    private String archive = "0"; // false?!?

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "forward_rule_id", referencedColumnName = "id")
    private List<IncomingLabRulesType> forwardTypes = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFrwdProviderNo() {
        return frwdProviderNo;
    }

    public void setFrwdProviderNo(String frwdProviderNo) {
        this.frwdProviderNo = frwdProviderNo;
    }

    public String getArchive() {
        return archive;
    }

    public void setArchive(String archive) {
        this.archive = archive;
    }

    public List<IncomingLabRulesType> getForwardTypes() {
        return forwardTypes;
    }

    public List<String> getForwardTypeStrings() {
        List<String> forwardTypeStrings = new ArrayList<>();
        for (IncomingLabRulesType type : forwardTypes) {
            forwardTypeStrings.add(type.getType());
        }

        //if the forward types are empty add all types just in case the data is invalid
        if (forwardTypeStrings.isEmpty()) {
            forwardTypeStrings.add("HL7");
            forwardTypeStrings.add("DOC");
            forwardTypeStrings.add("HRM");
        }
        return forwardTypeStrings;
    }

    public void setForwardTypes(ArrayList<IncomingLabRulesType> forwardTypes) {
        this.forwardTypes = forwardTypes;
    }

    public boolean addForwardType(String newType) {
        for (IncomingLabRulesType forwardType : forwardTypes) {
            if (newType.equals(forwardType.getType())) {
                return false;
            }
        }
        IncomingLabRulesType newIncomingLabRulesType = new IncomingLabRulesType();
        newIncomingLabRulesType.setType(newType);
        newIncomingLabRulesType.setIncomingLabRules(this);
        forwardTypes.add(newIncomingLabRulesType);
        return true;
    }
}
