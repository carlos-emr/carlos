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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.Misc;
import org.apache.cxf.common.util.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.SecurityArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Service
public class SecurityManager {

	private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private SecurityDao securityDao;

    @Autowired
    private SecurityArchiveDao securityArchiveDao;


    public void saveNewSecurityRecord(LoggedInInfo loggedInInfo, Security security) {
        if (!isSecurityObjectValid(security)) {
            throw new IllegalArgumentException("Invalid Security object built");
        }
        security.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        security.setLastUpdateDate(new Date());

        securityDao.persist(security);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.saveNewSecurityRecord", "id=" + security.getId());
    }

    public void updateSecurityRecord(LoggedInInfo loggedInInfo, Security security) {
        if (!isSecurityObjectValid(security)) {
            throw new IllegalArgumentException("Invalid Security object built");
        }

        Security dbSecurity = securityDao.find(security.getId());

        securityArchiveDao.archiveRecord(dbSecurity);

        security.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        security.setLastUpdateDate(new Date());

        securityDao.merge(security);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.updateSecurityRecord", "id=" + security.getId());
    }

    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "\"0\" is a policy threshold sentinel (zero past passwords to check), "
                    + "not a credential; compared against the pastPasswordsToNotUse config property")
    // "0" = policy threshold, not a password — prevents false positive on method name containing "password"
    public boolean checkPasswordAgainstPrevious(String newPassword, String providerNo) {
        //check previous passwords policy if the password is being changed
        String previousPasswordPolicy = CarlosProperties.getInstance().getProperty("password.pastPasswordsToNotUse", "0");
        try {
            Security dbSecurity = securityDao.getByProviderNo(providerNo);

			if (!"0".equals(previousPasswordPolicy) && !this.matchesPassword(newPassword, dbSecurity.getPassword())) {

                int numToGoBack = Integer.parseInt(previousPasswordPolicy);
                List<String> archives = securityArchiveDao.findPreviousPasswordsByProviderNo(providerNo, numToGoBack);

                boolean foundItInPast = false;

                for (String a : archives) {
					if (this.matchesPassword(newPassword, a)) {
                        foundItInPast = true;
                        break;
                    }
                }

                if (foundItInPast) {
                    return true;
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new RuntimeException(e);
        }
        return false;
    }

	/**
	 * Encode the given password using the configured hashing algorithm.
	 *
	 * @param password The password to encrypt.
	 * @return The encrypted password.
	 */
	public String encodePassword(CharSequence password) {
		return EncryptionUtils.hash(password);
	}

	/**
	 * Encode the given PIN using the configured password hashing algorithm.
	 *
	 * @param pin The PIN to hash.
	 * @return The hashed PIN.
	 */
	public String encodePin(CharSequence pin) {
		return this.encodePassword(pin);
	}

	/**
	 * Validates the password against the provided sec's stored password. If the password is valid and an upgrade
	 * is needed to the existing stored password, the stored password will be upgraded.
	 *
	 * @param rawPassword The password to validate.
	 * @param security    The sec object containing the stored password.
	 */
	public boolean validatePassword(CharSequence rawPassword, Security security) {
		boolean isValid = this.matchesPassword(rawPassword, security.getPassword());
		if (isValid && EncryptionUtils.isPasswordHashUpgradeNeeded(security.getPassword())) {
			boolean isHashUpgraded = this.upgradeSavePasswordHash(rawPassword, security);
			if (!isHashUpgraded)
				logger.error("Error while upgrading password hash");
		}
		return isValid;
	}

	/**
	 * Validates a raw PIN against the stored PIN value without mutating the security record.
	 *
	 * <p>Modern hashes are verified with the configured password hashing algorithm. Legacy plaintext
	 * and legacy encrypted PIN values are still accepted during authentication, but callers must
	 * defer any upgrade persistence until after the full login has succeeded.</p>
	 *
	 * @param rawPin The PIN supplied by the user.
	 * @param security The security record containing the stored PIN.
	 * @return True when the supplied PIN matches the stored PIN in either modern or legacy form.
	 */
	public boolean validatePin(CharSequence rawPin, Security security) {
		if (rawPin == null || rawPin.length() < 3 || security == null || security.getPin() == null) {
			return false;
		}

		String storedPin = security.getPin();
		if (isModernHash(storedPin)) {
			return this.matchesModernHash(rawPin, storedPin);
		}
		return this.matchesLegacyPin(rawPin, storedPin);
	}

	/**
	 * True when the stored credential carries a {@link org.springframework.security.crypto.password.DelegatingPasswordEncoder}
	 * algorithm tag such as {@code {bcrypt}}.
	 *
	 * <p>Untagged values are legacy PINs: plaintext digits, or {@code Misc.encryptPIN} output. The
	 * encoder map currently registers the deprecated SHA encoder under a null algorithm id, so
	 * handing it a legacy value happens to return false rather than throw - but that is a detail of
	 * how the map is built, not a contract we should depend on. Deciding the storage format here
	 * keeps the legacy comparison reachable by construction, and stops a tagged-but-corrupt hash
	 * from being retried as though it were legacy plaintext. PIN characters are digits, and
	 * {@code Misc.encryptPIN} derives its first output character from the first digit's code point,
	 * so no legacy form can begin with '{'.</p>
	 *
	 * @param storedValue The credential value as stored in the security row.
	 * @return True when the value is tagged with a hashing algorithm id.
	 */
	private static boolean isModernHash(String storedValue) {
		return storedValue != null && storedValue.startsWith("{");
	}

	/**
	 * Verifies a raw value against a tagged hash without letting malformed stored data reach the
	 * caller as an exception.
	 *
	 * <p>An unknown algorithm id raises {@code IllegalArgumentException} out of the delegating
	 * encoder. Such a value is unusable credential data rather than a match, and returning false
	 * keeps a corrupt security row from turning the login path into a 500.</p>
	 */
	private boolean matchesModernHash(CharSequence rawValue, String storedHash) {
		try {
			return this.matchesPassword(rawValue, storedHash);
		} catch (IllegalArgumentException e) {
			logger.warn("Stored PIN hash could not be evaluated; treating as non-matching");
			return false;
		}
	}

	/**
	 * Reports whether a validated PIN should be re-hashed with the current algorithm.
	 *
	 * @param security The security record containing the stored PIN.
	 * @return True when the stored PIN is a legacy value or a tagged hash below current strength.
	 */
	public boolean isPinHashUpgradeNeeded(Security security) {
		if (security == null || security.getPin() == null) {
			return false;
		}

		String storedPin = security.getPin();
		if (!isModernHash(storedPin)) {
			return true;
		}

		try {
			return EncryptionUtils.isPasswordHashUpgradeNeeded(storedPin);
		} catch (IllegalArgumentException e) {
			// BCryptPasswordEncoder.upgradeEncoding throws on a value tagged {bcrypt} whose body is
			// not bcrypt-shaped. Such a hash cannot be strengthened in place and no PIN can match
			// it, so reporting "no upgrade" is the fail-safe answer.
			logger.warn("Stored PIN hash could not be evaluated for upgrade; leaving it unchanged");
			return false;
		}
	}

	/**
	 * Upgrades a validated PIN to the current hashing algorithm when the stored representation is
	 * legacy or otherwise needs rehashing.
	 *
	 * @param rawPin The PIN supplied by the user.
	 * @param security The security record containing the stored PIN.
	 * @return True when no upgrade is needed or when the upgrade was persisted successfully.
	 */
	public boolean upgradePinHashIfNeeded(CharSequence rawPin, Security security) {
		if (rawPin == null || rawPin.length() < 3 || security == null || security.getPin() == null) {
			return false;
		}

		if (!this.validatePin(rawPin, security)) {
			return false;
		}

		if (!this.isPinHashUpgradeNeeded(security)) {
			return true;
		}

		return this.upgradeSavePinHash(rawPin, security);
	}

	/**
	 * Validates the password against the provided encoded password.
	 *
	 * @param rawPassword     The password to validate.
	 * @param encodedPassword The encoded password to compare against.
	 * @return True if the password is valid, false otherwise.
	 */
	public boolean matchesPassword(CharSequence rawPassword, String encodedPassword) {
		return EncryptionUtils.verify(rawPassword, encodedPassword);
	}

	/**
	 * Upgrades the password hash and saves the updated Security object.
	 *
	 * @param rawPassword The raw password to hash.
	 * @param security    The Security object to update.
	 * @return True if the password hash was successfully upgraded and saved, false otherwise.
	 */
	public boolean upgradeSavePasswordHash(CharSequence rawPassword, Security security) {
		String hash = this.encodePassword(rawPassword);
		boolean matched = this.matchesPassword(rawPassword, hash);

		if (!matched) // should never happen, but if password upgrade fails.
			return false;

		security.setPassword(hash);
		security.setPasswordUpdateDate(new Date());
		this.securityDao.merge(security);
                return true;
            }

	/**
	 * Hashes the supplied PIN, stores it on the security record, and persists the change.
	 *
	 * @param rawPin The raw PIN to hash.
	 * @param security The security record to update.
	 * @return True if the hash was generated, verified, and persisted.
	 */
	public boolean upgradeSavePinHash(CharSequence rawPin, Security security) {
		if (rawPin == null || security == null) {
			return false;
		}

		String expectedStoredPin = security.getPin();
		if (expectedStoredPin == null || security.getSecurityNo() == null) {
			return false;
		}

		String hash = this.encodePin(rawPin);

		if (!this.matchesPassword(rawPin, hash)) // should never happen, but if the PIN upgrade fails.
			return false;

		// The Security instance handed to us was read at the start of the login request and the
		// upgrade is deferred until the login completes, so the row may have changed in between
		// (self-service PIN change, admin edit, or a concurrent migration on another node).
		// Guarding inside the UPDATE rather than read-then-write keeps that check atomic; merging
		// the stale object would roll a newer PIN back to a hash of the old one.
		Date pinUpdateDate = new Date();
		int rowsUpdated = this.securityDao.updatePinHashIfUnchanged(
				security.getSecurityNo(), expectedStoredPin, hash, pinUpdateDate);

		if (rowsUpdated == 0) {
			// Someone changed the PIN between authentication and here. Their value is newer than
			// ours, so leaving it alone is the correct outcome, not a failure to report.
			logger.info("Skipping PIN hash upgrade; stored PIN changed since authentication");
			return true;
		}

		// The bulk update bypasses the persistence context, so align the caller's copy by hand.
		security.setPin(hash);
		security.setPinUpdateDate(pinUpdateDate);
		return true;
	}

	private boolean matchesLegacyPin(CharSequence rawPin, String storedPin) {
		String rawPinValue = rawPin.toString();
		return constantTimeEquals(rawPinValue, storedPin) || constantTimeEquals(encryptLegacyPin(rawPinValue), storedPin);
	}

	@SuppressWarnings("deprecation")
	private String encryptLegacyPin(String rawPin) {
		return Misc.encryptPIN(rawPin);
	}

	private boolean constantTimeEquals(String first, String second) {
		if (first == null || second == null) {
			return false;
		}
		return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
	}

    public Security findByProviderNo(LoggedInInfo loggedInInfo, String providerNo) {

        List<Security> results = securityDao.findByProviderNo(providerNo);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findByProviderNo", "providerNo=" + providerNo);

        if (!results.isEmpty()) {
            return results.get(0);
        }

        return null;
    }

    public Security find(LoggedInInfo loggedInInfo, Integer id) {

        Security result = securityDao.find(id);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.find", "id=" + id);

        return result;
    }

    public List<Security> findByUserName(LoggedInInfo loggedInInfo, String userName) {

        List<Security> results = securityDao.findByUserName(userName);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findByUserName", "userName=" + userName);

        return results;
    }

    public boolean getPasswordResetFlag(String userName) {

        List<Security> results = securityDao.findByUserName(userName);

        if (results.isEmpty()) {
            return false;
        }

        return (results.get(0).isForcePasswordReset() != null && results.get(0).isForcePasswordReset().equals(Boolean.TRUE));
    }

    public List<Security> findByProviderSite(LoggedInInfo loggedInInfo, String providerNo) {

        List<Security> results = securityDao.findByProviderSite(providerNo);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findByProviderSite", "providerNo=" + providerNo);

        return results;
    }

    public List<Security> findAllOrderByUserName(LoggedInInfo loggedInInfo) {

        List<Security> results = securityDao.findAllOrderBy("userName");

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findAllOrderByUserName", "");

        return results;

    }

    public List<Security> findByLikeProviderNo(LoggedInInfo loggedInInfo, String providerNo) {
        List<Security> results = securityDao.findByLikeProviderNo(providerNo);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findByLikeProviderNo", "providerNo=" + providerNo);

        return results;
    }

    public List<Security> findByLikeUserName(LoggedInInfo loggedInInfo, String userName) {
        List<Security> results = securityDao.findByLikeUserName(userName);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findByLikeUserName", "userName=" + userName);

        return results;
    }

    public void remove(LoggedInInfo loggedInInfo, Integer id) {

        securityDao.remove(id);

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.remove", "id=" + id);
    }

    public List<Object[]> findProviders(LoggedInInfo loggedInInfo) {

        List<Object[]> results = securityDao.findProviders();

        LogAction.addLogSynchronous(loggedInInfo, "SecurityManager.findProviders", "");

        return results;
    }

    protected boolean isSecurityObjectValid(Security security) {
        if (security == null) {
            return false;
        }

        if (StringUtils.isEmpty(security.getPassword())) {
            return false;
        }

        if (StringUtils.isEmpty(security.getProviderNo())) {
            return false;
        }

        if (StringUtils.isEmpty(security.getUserName())) {
            return false;
        }

        return true;
    }
}
