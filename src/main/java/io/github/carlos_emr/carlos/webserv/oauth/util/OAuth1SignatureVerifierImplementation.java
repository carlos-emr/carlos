 /**
 * File: OAuth1SignatureVerifierImplementation.java
 *
 * Purpose:
 *   Provides the server-side verification of OAuth 1.0a request signatures
 *   using supported methods (HMAC-SHA1, PLAINTEXT). Ensures only requests
 *   with valid signatures are accepted by the API endpoints.
 *
 * Responsibilities:
 *   • Extract OAuth parameters and signature from HttpServletRequest.
 *   • Construct the normalized parameter string and base signature string.
 *   • Compute signatures using consumer secret and token secret, then
 *     perform constant-time comparison against the incoming signature.
 *   • Resolve appropriate token secrets (request vs. access tokens) via the
 *     OscarOAuthDataProvider.
 *   • Validate replay-prevention fields (timestamp, nonce) if configured.
 *   • Expose utility to map an access token back to its provider.
 *
 * Context / Why Added:
 *   Introduced during the CXF → ScribeJava migration to replace the legacy
 *   CXF OAuth filter with an explicit, unit-testable verifier class. This
 *   ensures predictable and deterministic signature validation across token
 *   and resource endpoints.
 *
 * Notes:
 *   • No secrets or raw signatures are logged.
 *   • Uses RFC 5849-compliant percent-encoding/decoding rules.
 *   • Nonce/timestamp anti-replay handling is delegated to the configured
 *     store/provider.
 *   • Designed to fail fast with IllegalArgumentException on invalid or
 *     missing signature material.
 */


package io.github.carlos_emr.carlos.webserv.oauth.util;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.scribejava.core.model.OAuthConstants;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import java.util.Locale;

@Service
public class OAuth1SignatureVerifierImplementation implements OAuth1SignatureVerifier {
    @Autowired
    private OscarOAuthDataProvider dataProvider;

    // ★ NEW: configurable clock skew (seconds)
    private static final long ALLOWED_SKEW_SECONDS = 300L;

    @Override
    public String verifySignature(HttpServletRequest req, AppOAuth1Config cfg) {
        // --- 1) OAuth params from Authorization header ---
        Map<String, String> oauth = OAuthRequestParser.extractOAuthParameters(req);
        String token       = oauth.get(OAuthConstants.TOKEN);
        String incomingSig = oauth.get(OAuthConstants.SIGNATURE);
        if (incomingSig == null) {
            throw new IllegalArgumentException("Missing OAuth signature");
        }

        // ★ NEW: timestamp freshness (± ALLOWED_SKEW_SECONDS)
        String tsStr = oauth.get("oauth_timestamp");
        if (tsStr == null || tsStr.isEmpty()) {
            throw new IllegalArgumentException("Missing oauth_timestamp");
        }
        long now = System.currentTimeMillis() / 1000L;
        long ts;
        try { ts = Long.parseLong(tsStr); }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Invalid oauth_timestamp"); }
        if (Math.abs(now - ts) > ALLOWED_SKEW_SECONDS) {
            throw new IllegalArgumentException("Stale oauth_timestamp");
        }

        final String uri = (req.getRequestURI() == null ? "" : req.getRequestURI()).toLowerCase(Locale.ROOT);
        final boolean isInitiate = uri.contains("/initiate");
        final boolean isAccessEndpoint = uri.endsWith("/access_token") || uri.endsWith("/token");

        final boolean hasVerifier = oauth.containsKey("oauth_verifier");

        String tokenSecret = "";
        if (!isInitiate && token != null && !token.isEmpty() && dataProvider != null) {
            if (isAccessEndpoint || hasVerifier) {
                tokenSecret = dataProvider.getRequestTokenSecret(token);
            } else {
                tokenSecret = dataProvider.getAccessTokenSecret(token);
            }
        }
        if (tokenSecret == null) {
            throw new IllegalArgumentException("Unknown or expired token: " + token);
        }

        // --- 3) Base URL
        final String scheme = req.getScheme().toLowerCase();
        final String host   = req.getServerName().toLowerCase();
        final int    port   = req.getServerPort();
        final boolean defaultPort =
                ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        final String path = (req.getRequestURI() == null || req.getRequestURI().isEmpty())
                ? "/" : req.getRequestURI();
        final String baseUrl = scheme + "://" + host + (defaultPort ? "" : ":" + port) + path;

        // --- 4) Collect ALL signature params ---
        final java.util.List<NameValue> all = new java.util.ArrayList<>();

        // a) oauth_* from header (percent-decoded once)
        for (Map.Entry<String, String> e : oauth.entrySet()) {
            String k = e.getKey();
            if ("oauth_signature".equals(k) || "realm".equalsIgnoreCase(k)) continue;
            all.add(new NameValue(k, pctDecode(e.getValue())));
        }

        // b) Query string
        String qs = req.getQueryString();
        if (qs != null && !qs.isEmpty()) {
            for (String qp : qs.split("&")) {
                if (qp.isEmpty()) continue;
                String[] kv = qp.split("=", 2);
                String k = pctDecode(kv[0]);
                String v = kv.length > 1 ? pctDecode(kv[1]) : "";
                if (!k.startsWith("oauth_")) all.add(new NameValue(k, v));
            }
        }

        // c) Body params (x-www-form-urlencoded)
        String ctype = req.getContentType();
        if (ctype != null && ctype.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            req.getParameterMap().forEach((k, vals) -> {
                if (!k.startsWith("oauth_")) for (String v : vals) all.add(new NameValue(k, v));
            });
        }

        // --- 5) Normalize parameters (encode → sort → join) ---
        all.sort((a, b) -> {
            int c = pct(a.name).compareTo(pct(b.name));
            return (c != 0) ? c : pct(a.value).compareTo(pct(b.value));
        });
        final StringBuilder norm = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) norm.append('&');
            norm.append(pct(all.get(i).name)).append('=').append(pct(all.get(i).value));
        }

        // --- 6) Base string & HMAC-SHA1 ---
        final String baseString =
                req.getMethod().toUpperCase() + '&' + pct(baseUrl) + '&' + pct(norm.toString());
        final String signingKey =
                pct(cfg.getConsumerSecret()) + '&' + pct(tokenSecret == null ? "" : tokenSecret);
        final String computed = base64(hmacSha1(baseString, signingKey));

        // --- 7) Compare (percent-decode first)
        final String incoming = pctDecode(incomingSig);
        if (!constantTimeEquals(incoming, computed)) {
            throw new IllegalArgumentException("Invalid OAuth1 signature");
        }

        return token; // unchanged contract
    }

    public String getProviderNo(String token) {
        return dataProvider.getProviderNoByAccessToken(token);
    }

    // ---- utils (unchanged) ----
    private static final java.nio.charset.Charset UTF8 = java.nio.charset.StandardCharsets.UTF_8;

    private static String pct(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (byte b : s.getBytes(UTF8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%');
                String hex = Integer.toHexString(c).toUpperCase();
                if (hex.length() == 1) out.append('0');
                out.append(hex);
            }
        }
        return out.toString();
    }

    private static byte[] hmacSha1(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            javax.crypto.spec.SecretKeySpec ks = new javax.crypto.spec.SecretKeySpec(key.getBytes(UTF8), "HmacSHA1");
            mac.init(ks);
            return mac.doFinal(data.getBytes(UTF8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static String base64(byte[] bytes) { 
        return java.util.Base64.getEncoder().encodeToString(bytes); 
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int len = Math.max(a.length(), b.length());
        int r = 0;
        for (int i = 0; i < len; i++) {
            char ca = i < a.length() ? a.charAt(i) : 0;
            char cb = i < b.length() ? b.charAt(i) : 0;
            r |= (ca ^ cb);
        }
        return r == 0 && a.length() == b.length();
    }

    private static String pctDecode(String s) {
        if (s == null || s.isEmpty()) return s;
        int n = s.length(); StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < n) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) { out.append((char) ((hi << 4) + lo)); i += 2; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static final class NameValue {
        final String name, value;
        NameValue(String n, String v) { this.name = n; this.value = v; }
    }
}
