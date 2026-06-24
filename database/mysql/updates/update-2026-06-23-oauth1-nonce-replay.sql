-- OAuth 1.0a anti-replay: persist consumed request nonces so a captured,
-- validly-signed request cannot be replayed while its oauth_timestamp is still
-- inside the accepted skew window. The unique key is a fixed-length SHA-256
-- hash of the canonical (consumerKey, tokenId, nonce) tuple, which keeps the
-- index small and independent of the descriptive column lengths, avoiding
-- index key-length limits. Issue #2955.
--
-- IF NOT EXISTS is used to match the existing Service* token-table migrations
-- and to keep this script idempotent. The table is introduced by this change,
-- so there is no pre-existing divergent ServiceOAuthNonce to silently skip; the
-- UNIQUE KEY below is therefore guaranteed to be created on first apply.
CREATE TABLE IF NOT EXISTS `ServiceOAuthNonce` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nonceKeyHash` char(64) NOT NULL,
  `consumerKey` varchar(255) NOT NULL,
  `tokenId` varchar(255) NOT NULL DEFAULT '',
  `nonce` varchar(255) NOT NULL,
  `oauthTimestamp` bigint(20) NOT NULL,
  `dateCreated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `lastUpdateUser` varchar(20) DEFAULT NULL,
  `lastUpdateDate` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_service_oauth_nonce` (`nonceKeyHash`),
  KEY `idx_service_oauth_nonce_ts` (`oauthTimestamp`)
);
