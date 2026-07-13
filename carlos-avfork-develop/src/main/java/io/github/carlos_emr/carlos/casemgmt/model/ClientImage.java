/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.model;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Date;

import javax.sql.rowset.serial.SerialBlob;

import org.apache.commons.codec.binary.Base64;
import io.github.carlos_emr.carlos.model.BaseObject;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "client_image")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class ClientImage extends BaseObject {
    public static final String imageMissingPlaceholderUrl = "/images/defaultR_img.jpg";
    public static final String imagePresentPlaceholderUrl = "/images/default_img.jpg";

    private Long id;
    private int demographic_no;
    private String image_type;
    private byte[] image_data;
    private Date update_date;

    public ClientImage() {
        update_date = new Date();
    }
    @jakarta.persistence.Column(name = "demographic_no")

    public int getDemographic_no() {
        return demographic_no;
    }

    public void setDemographic_no(int demographic_no) {
        this.demographic_no = demographic_no;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)

    @jakarta.persistence.Column(name = "image_id")

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    @jakarta.persistence.Transient

    public byte[] getImage_data() {
        return image_data;
    }

    public void setImage_data(byte[] image_data) {
        this.image_data = image_data;
    }
    @jakarta.persistence.Column(name = "image_type")

    public String getImage_type() {
        return image_type;
    }

    public void setImage_type(String image_type) {
        this.image_type = image_type;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)
    @jakarta.persistence.Column(name = "update_date")

    public Date getUpdate_date() {
        return update_date;
    }

    /**
     * Normalizes legacy client image type values to renderable image subtypes.
     *
     * <p>Accepts historical file-extension and MIME-style values used by CARLOS EMR
     * client photo records, including {@code jpg}, {@code jpeg}, {@code image/jpg},
     * {@code image/jpeg}, {@code gif}, and {@code image/gif}.</p>
     *
     * @param imageType String the stored image type value from the client image record
     * @return String the normalized renderable subtype ({@code "jpeg"} or {@code "gif"}),
     *         or {@code null} when the value is unsupported
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static String getRenderableImageType(String imageType) {
        if (imageType == null) {
            return null;
        }

        String normalized = imageType.trim().toLowerCase();
        if ("jpg".equals(normalized) || "jpeg".equals(normalized) || "image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) {
            return "jpeg";
        }
        if ("gif".equals(normalized) || "image/gif".equals(normalized)) {
            return "gif";
        }
        return null;
    }

    public void setUpdate_date(Date update_date) {
        this.update_date = update_date;
    }
    @jakarta.persistence.Lob
    @jakarta.persistence.Basic(fetch = jakarta.persistence.FetchType.LAZY)

    @jakarta.persistence.Column(name = "contents")

    public Blob getImage_contents() {
        if (image_data == null) {
            return null;
        }
        try {
            Blob imageBlob = new SerialBlob(Base64.encodeBase64(image_data));
            return imageBlob;
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Failed to create image blob", e);
        }
        return null;
    }

    public void setImage_contents(Blob image_contents) {
        if (image_contents != null) {
            try {
                this.image_data = Base64.decodeBase64(this.blobToByteArray(image_contents));
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                this.image_data = null;
            }
        }
    }

    private byte[] blobToByteArray(Blob image_contents) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4000];
        InputStream is = null;
        try {
            is = image_contents.getBinaryStream();
            for (;; ) {
                int dataSize = is.read(buf);
                if (dataSize == -1)
                    break;
                baos.write(buf, 0, dataSize);
            }
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            if (is != null)
                is.close();
        }

        return baos.toByteArray();
    }

}
