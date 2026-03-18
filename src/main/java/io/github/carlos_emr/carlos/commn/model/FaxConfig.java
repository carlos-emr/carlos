/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Portions contributed by Juno EMR.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import jakarta.persistence.*;

/**
 * JPA entity representing fax gateway account configuration.
 *
 * <p>Supports multiple fax provider types (MIDDLEWARE, SRFAX) with encrypted credential storage.
 * Each configuration defines connection parameters, authentication credentials, inbox routing,
 * and active/download flags for scheduler control.</p>
 *
 * <p><strong>Security:</strong> Password fields (passwd, faxPasswd) are automatically encrypted
 * on write and decrypted on read using {@link io.github.carlos_emr.carlos.utility.EncryptionUtils}.
 * Legacy unencrypted passwords are returned as-is on read; re-encryption occurs only
 * when the password is explicitly re-saved through the admin UI.</p>
 *
 * <p><strong>Provider Types:</strong></p>
 * <ul>
 *   <li><strong>MIDDLEWARE:</strong> Relay server intermediary (faxws) - requires url, siteUser, passwd</li>
 *   <li><strong>SRFAX:</strong> Direct SRFax API integration - uses default endpoint (overridable via srfax.api.url property), requires faxUser, faxPasswd</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.fax.provider.FaxProviderClient
 * @see io.github.carlos_emr.carlos.utility.EncryptionUtils
 * @since 2014-08-29
 */
@Entity
@Table(name = "fax_config")
public class FaxConfig extends AbstractModel<Integer> {

    public enum ProviderType {
        MIDDLEWARE,
        SRFAX
    }

    private static final long serialVersionUID = 1L;
    private static final Logger logger = MiscUtils.getLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer Id;

    /** Middleware relay server base URL (MIDDLEWARE only; not used by SRFAX) */
    private String url = "";
    /** Middleware site-level username for Basic Auth (MIDDLEWARE only) */
    private String siteUser = "";
    /** Middleware site-level password, encrypted at rest (MIDDLEWARE only) */
    private String passwd = "";
    /** Fax account username: middleware fax user (MIDDLEWARE) or SRFax account number (SRFAX) */
    private String faxUser = "";
    /** Fax account password, encrypted at rest: middleware fax credential or SRFax API password */
    private String faxPasswd = "";

    /** Outbound caller-ID fax number for this account */
    private String faxNumber = "";
    /** Email address for fax delivery notifications */
    private String senderEmail = "";

    /** Whether this account is enabled for fax operations */
    @Column(columnDefinition = "boolean default false")
    private boolean active;
    /** Document review queue ID for inbound fax routing (valid IDs start at 1; defaults to queue 1 "default") */
    private Integer queue = 1;
    /** Human-readable display name for this fax account */
    private String accountName = "";

    /** Whether inbound fax downloading is enabled for this account */
    @Column(columnDefinition = "boolean default true")
    private boolean download;

    /**
     * Provider type used to route fax transport operations.
     * Defaults to middleware to preserve backward compatibility for existing rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "providerType")
    private ProviderType providerType = ProviderType.MIDDLEWARE;

    @Override
    public Integer getId() {
        return Id;
    }


    /**
     * Returns the URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Returns the site user.
     */
    public String getSiteUser() {
        return siteUser;
    }

    /**
     * Sets the site user.
     */
    public void setSiteUser(String siteUser) {
        this.siteUser = siteUser;
    }

    /**
     * Returns decrypted plain text site password.
     */
    public String getPasswd() {
        return decryptField(passwd, "password");
    }

    /** Sets site password (plain text input, encrypted immediately). */
    public void setPasswd(String passwd) {
        this.passwd = encryptField(passwd, "password");
    }

    /**
     * Returns the fax user.
     */
    public String getFaxUser() {
        return faxUser;
    }

    /**
     * Sets the fax user.
     */
    public void setFaxUser(String faxUser) {
        this.faxUser = faxUser;
    }

    /** Returns decrypted plain text fax password. */
    public String getFaxPasswd() {
        return decryptField(faxPasswd, "fax password");
    }

    /**
     * Sets the fax password by encrypting the provided plain text input.
     */
    public void setFaxPasswd(String faxPasswd) {
        this.faxPasswd = encryptField(faxPasswd, "fax password");
    }

    /**
     * Decrypts a password field, handling both encrypted and legacy plain text values.
     * This method checks if the provided value is not null or empty, then determines
     * if it is encrypted using the EncryptionUtils. If encrypted, it decrypts the value;
     * otherwise, it returns the legacy plain text as-is. In case of decryption failure,
     * an error is logged, and an IllegalStateException is thrown with a descriptive message.
     *
     * @param value the field value (may be encrypted or legacy plain text)
     * @param fieldLabel descriptive label for error messages
     */
    private String decryptField(String value, String fieldLabel) {
        if (value != null && !value.isEmpty()) {
            try {
                if (EncryptionUtils.isEncrypted(value)) {
                    return EncryptionUtils.decrypt(value);
                }
                // Legacy plain text - return as-is, caller decides whether to re-encrypt
                return value;
            } catch (Exception e) {
                logger.error("Failed to decrypt {} - possible key rotation or data corruption. "
                        + "Re-enter the password in Administration > Faxes > Configure Fax to "
                        + "re-encrypt with the current key.", fieldLabel, e);
                throw new IllegalStateException(
                        "Failed to decrypt " + fieldLabel
                        + " - re-enter password in Administration > Faxes > Configure Fax", e);
            }
        }
        return "";
    }

    /**
     * Shared helper method to encrypt a password field.
     *
     * @param plainText the plain text password to encrypt
     * @param fieldLabel descriptive label for error messages
     * @return encrypted password value, or original value if null/empty
     * @throws RuntimeException if encryption fails
     */
    private String encryptField(String plainText, String fieldLabel) {
        try {
            if (plainText != null && !plainText.isEmpty()) {
                return EncryptionUtils.encrypt(plainText);
            }
            return plainText; // null/empty preserved
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt " + fieldLabel, e);
        }
    }


    public String getFaxNumber() {
        return faxNumber;
    }

    /**
     * Sets the fax number.
     */
    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    /**
     * Returns the serial version UID.
     */
    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    /**
     * Sets the ID.
     */
    public void setId(Integer id) {
        Id = id;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Sender email as required by fax gateway integration
     */
    public String getSenderEmail() {
        return senderEmail;
    }

    /**
     * Sender email as required by fax gateway integration
     */
    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }


    public boolean getActive() {
        return active;
    }

    /**
     * Returns the configured provider type or a default middleware type if null.
     */
    public ProviderType getProviderType() {
        if (providerType == null) {
            return ProviderType.MIDDLEWARE;
        }
        return providerType;
    }

    /**
     * Sets the provider type for this account, defaulting to MIDDLEWARE if null.
     */
    public void setProviderType(ProviderType providerType) {
        this.providerType = (providerType != null) ? providerType : ProviderType.MIDDLEWARE;
    }


    /**
     * Sets the active state.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getQueue() {
        return queue;
    }

    /**
     * Sets the queue value.
     */
    public void setQueue(Integer queue) {
        this.queue = queue;
    }

    public String getAccountName() {
        if (accountName == null || accountName.isEmpty()) {
            return getFaxUser();
        }

        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public boolean isDownload() {
        return download;
    }


    public void setDownload(boolean download) {
        this.download = download;
    }
}
