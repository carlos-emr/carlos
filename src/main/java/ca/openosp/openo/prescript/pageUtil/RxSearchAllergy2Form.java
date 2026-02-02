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


package ca.openosp.openo.prescript.pageUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Form bean for allergy search requests in RxSearchAllergy2Action.
 *
 * Note: JsonIgnoreProperties is required because the search form in ShowAllergies2.jsp
 * contains a nested AJAX-loaded form (AddReaction2.jsp) with additional fields like
 * 'reactionDescription', 'ID', 'name', etc. When JavaScript serializes all form fields
 * for the search request, these extra fields would cause Jackson parsing errors without
 * this annotation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RxSearchAllergy2Form {

    private String searchString = null;
    private boolean type5 = false;
    private boolean type4 = false;
    private boolean type3 = false;
    private boolean type2 = false;
    private boolean type1 = false;

    private String iNKDA;
    private String hasDrugAllergy;
    private String submit;

    public String getSearchString() {
        return (this.searchString);
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public boolean getType5() {
        return (this.type5);
    }

    public void setType5(boolean RHS) {
        this.type5 = RHS;
    }

    public boolean getType4() {
        return (this.type4);
    }

    public void setType4(boolean RHS) {
        this.type4 = RHS;
    }

    public boolean getType3() {
        return (this.type3);
    }

    public void setType3(boolean RHS) {
        this.type3 = RHS;
    }

    public boolean getType2() {
        return (this.type2);
    }

    public void setType2(boolean RHS) {
        this.type2 = RHS;
    }

    public boolean getType1() {
        return (this.type1);
    }

    public void setType1(boolean RHS) {
        this.type1 = RHS;
    }

    public String getiNKDA() {
        return iNKDA;
    }

    public void setiNKDA(String iNKDA) {
        this.iNKDA = iNKDA;
    }

    public String getHasDrugAllergy() {
        return hasDrugAllergy;
    }

    public void setHasDrugAllergy(String hasDrugAllergy) {
        this.hasDrugAllergy = hasDrugAllergy;
    }

    public String getSubmit() {
        return submit;
    }

    public void setSubmit(String submit) {
        this.submit = submit;
    }
}
