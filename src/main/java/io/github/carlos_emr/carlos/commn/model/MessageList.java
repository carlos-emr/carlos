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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "messagelisttbl")
public class MessageList extends AbstractModel<Integer> {

    public static final String STATUS_DELETED = "del";
    public static final String STATUS_NEW = "new";
    public static final String STATUS_READ = "read";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_REMOTE = "remote";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private long message;

    @Column(name = "provider_no")
    private String providerNo;

    private String status;

    private int remoteLocation;

    @Column(name = "destinationFacilityId")
    private int destinationFacilityId;

    @Column(name = "sourceFacilityId")
    private int sourceFacilityId;

    public int getDestinationFacilityId() {
        return destinationFacilityId;
    }

    public void setDestinationFacilityId(int destinationFacilityId) {
        this.destinationFacilityId = destinationFacilityId;
    }

    public int getSourceFacilityId() {
        return sourceFacilityId;
    }

    public void setSourceFacilityId(int sourceFacilityId) {
        this.sourceFacilityId = sourceFacilityId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public long getMessage() {
        return message;
    }

    public void setMessage(long message) {
        this.message = message;
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

    public int getRemoteLocation() {
        return remoteLocation;
    }

    public void setRemoteLocation(int remoteLocation) {
        this.remoteLocation = remoteLocation;
    }

    public void setDeleted(boolean isDeleted) {
        if (isDeleted) {
            setStatus(STATUS_DELETED);
        } else {
            setStatus(null);
        }
    }


}
