/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.model;

/**
 * LstOrgcd entity.
 *
 * @author JZhang
 */

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "lst_orgcd")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class LstOrgcd implements java.io.Serializable {

    // Fields

    private String code;
    private String description;
    private Integer activeyn;
    private Integer orderbyindex;
    private String codetree;
    private String fullcode;
    private String codecsv;

    // Constructors
    @jakarta.persistence.Column(name = "codeCsv", length = 80)

    public String getCodecsv() {
        return codecsv;
    }

    public void setCodecsv(String codecsv) {
        this.codecsv = codecsv;
    }

    /**
     * default constructor
     */
    public LstOrgcd() {
    }

    /**
     * minimal constructor
     */
    public LstOrgcd(String code) {
        this.code = code;
    }

    /**
     * full constructor
     */
    public LstOrgcd(String code, String description, Integer activeyn,
                    Integer orderbyindex, String codetree, String fullcode, String codecsv) {
        this.code = code;
        this.description = description;
        this.activeyn = activeyn;
        this.orderbyindex = orderbyindex;
        this.codetree = codetree;
        this.fullcode = fullcode;
        this.codecsv = codecsv;
    }

    // Property accessors
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "code", length = 8)

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    @jakarta.persistence.Column(name = "description", length = 240)

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    @jakarta.persistence.Column(name = "activeyn")

    public Integer getActiveyn() {
        return this.activeyn;
    }

    public void setActiveyn(Integer activeyn) {
        this.activeyn = activeyn;
    }
    @jakarta.persistence.Column(name = "orderbyindex")

    public Integer getOrderbyindex() {
        return this.orderbyindex;
    }

    public void setOrderbyindex(Integer orderbyindex) {
        this.orderbyindex = orderbyindex;
    }
    @jakarta.persistence.Column(name = "codetree", length = 80)

    public String getCodetree() {
        return this.codetree;
    }

    public void setCodetree(String codetree) {
        this.codetree = codetree;
    }
    @jakarta.persistence.Column(name = "fullCode", length = 80)

    public String getFullcode() {
        return this.fullcode;
    }

    public void setFullcode(String fullcode) {
        this.fullcode = fullcode;
    }

}
