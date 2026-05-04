/**
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.login.LoginCredentialCache.LoginCredentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@Tag("unit")
@DisplayName("LoginCredentialCache")
class LoginCredentialCacheUnitTest {

    private final LoginCredentialCache cache = LoginCredentialCache.getInstance();

    @Test
    @DisplayName("should round-trip credentials via store and peek")
    void shouldRoundTripCredentials_whenTokenIsValid() {
        LoginCredentials creds = new LoginCredentials("alice", "hash123", "1234", "/index");
        String token = cache.store(creds);

        LoginCredentials peeked = cache.peek(token);

        assertThat(peeked).isNotNull();
        assertThat(peeked.getUserName()).isEqualTo("alice");
        assertThat(peeked.getEncodedPassword()).isEqualTo("hash123");
        assertThat(peeked.getPin()).isEqualTo("1234");
        assertThat(peeked.getNextPage()).isEqualTo("/index");

        // peek must not remove the entry (forced-password-reset allows retries)
        LoginCredentials peekedAgain = cache.peek(token);
        assertThat(peekedAgain).isNotNull();
        assertThat(peekedAgain.getUserName()).isEqualTo("alice");

        cache.invalidate(token);
    }

    @Test
    @DisplayName("should return null and remove entry when consume is called")
    void shouldRemoveEntry_whenConsumed() {
        LoginCredentials creds = new LoginCredentials("bob", "hash", "0000", null);
        String token = cache.store(creds);

        LoginCredentials first = cache.consume(token);
        LoginCredentials second = cache.consume(token);
        LoginCredentials peek = cache.peek(token);

        assertThat(first).isNotNull();
        assertThat(first.getUserName()).isEqualTo("bob");
        assertThat(second).isNull();
        assertThat(peek).isNull();
    }

    @Test
    @DisplayName("should invalidate cached entry via invalidate()")
    void shouldRemoveEntry_whenInvalidated() {
        String token = cache.store(new LoginCredentials("carol", "h", "1111", "/home"));
        assertThat(cache.peek(token)).isNotNull();

        cache.invalidate(token);

        assertThat(cache.peek(token)).isNull();
        assertThat(cache.consume(token)).isNull();
    }

    @Test
    @DisplayName("should return null for null, empty, or unknown tokens")
    void shouldReturnNull_forInvalidTokens() {
        assertThat(cache.peek(null)).isNull();
        assertThat(cache.peek("")).isNull();
        assertThat(cache.peek("not-a-real-token-xyzzy")).isNull();
        assertThat(cache.consume(null)).isNull();
        assertThat(cache.consume("")).isNull();
        assertThat(cache.consume("not-a-real-token-xyzzy")).isNull();
        // invalidate must tolerate null/empty without throwing
        cache.invalidate(null);
        cache.invalidate("");
    }

    @Test
    @DisplayName("should reject null credentials on store")
    void shouldThrowNPE_whenStoringNullCredentials() {
        assertThatNullPointerException().isThrownBy(() -> cache.store(null));
    }

    @Test
    @DisplayName("should generate unique, high-entropy tokens")
    void shouldGenerateUniqueTokens_acrossStoreCalls() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            LoginCredentials c = new LoginCredentials("u" + i, "h" + i, "0000", null);
            tokens.add(cache.store(c));
        }
        assertThat(tokens).hasSize(100);
        // URL-safe Base64 of 32 random bytes -> 43 chars (no padding)
        tokens.forEach(t -> {
            assertThat(t).hasSize(43);
            assertThat(t).matches("[A-Za-z0-9_-]+");
        });
        tokens.forEach(cache::invalidate);
    }

    @Test
    @DisplayName("should expose singleton instance")
    void shouldExposeSingleton_viaGetInstance() {
        assertThat(LoginCredentialCache.getInstance())
                .isNotNull()
                .isSameAs(LoginCredentialCache.getInstance());
    }
}
