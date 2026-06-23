-- OAuth 1.0a anti-replay: persist consumed request nonces so a captured,
-- validly-signed request cannot be replayed while its oauth_timestamp is still
-- inside the accepted skew window. The unique key on (consumerKey, tokenId,
-- nonce) is what lets a duplicate be detected and rejected. Issue #2955.
CREATE TABLE IF NOT EXISTS `ServiceOAuthNonce` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `consumerKey` varchar(80) NOT NULL,
  `tokenId` varchar(80) NOT NULL DEFAULT '',
  `nonce` varchar(80) NOT NULL,
  `oauthTimestamp` bigint(20) NOT NULL,
  `dateCreated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_service_oauth_nonce` (`consumerKey`,`tokenId`,`nonce`),
  KEY `idx_service_oauth_nonce_ts` (`oauthTimestamp`)
);
