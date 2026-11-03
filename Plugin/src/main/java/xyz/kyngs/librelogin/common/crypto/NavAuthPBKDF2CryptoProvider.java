/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Verify-only provider for the PBKDF2 hashes created by NavAuth ({@code SHA256} / {@code SHA512}
 * algorithms), stored as {@code pbkdf2-sha256$<salt>$<hash>} or {@code pbkdf2-sha512$<salt>$<hash>}
 * with unpadded Base64 salt and hash.
 *
 * <p>NavAuth derives the key with BouncyCastle's {@code PKCS5PasswordToBytes}, which keeps only the
 * low byte of every character, so the password bytes are computed the same way instead of UTF-8. A
 * successful login re-hashes the password with the configured default provider (see {@code
 * LoginCommand}).
 */
public class NavAuthPBKDF2CryptoProvider implements VerifyOnlyCryptoProvider {

    public static final String IDENTIFIER = "NavAuth-PBKDF2";

    private static final int ITERATIONS = 100_000;

    @Override
    @Nullable
    public HashedPassword createHash(String password) {
        return null;
    }

    @Override
    public boolean matches(String input, HashedPassword password) {
        if (input == null || input.isEmpty() || password == null || password.hash() == null) {
            return false;
        }
        var parts = password.hash().split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }
        var mac =
                switch (parts[0]) {
                    case "pbkdf2-sha256" -> "HmacSHA256";
                    case "pbkdf2-sha512" -> "HmacSHA512";
                    default -> null;
                };
        if (mac == null) {
            return false;
        }
        try {
            var salt = Base64.getDecoder().decode(parts[1]);
            var expected = Base64.getDecoder().decode(parts[2]);
            if (expected.length == 0) {
                return false;
            }
            return MessageDigest.isEqual(expected, pbkdf2(mac, input, salt, expected.length));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String algorithm, String password, byte[] salt, int length)
            throws GeneralSecurityException {
        var passwordBytes = new byte[password.length()];
        for (int i = 0; i < passwordBytes.length; i++) {
            passwordBytes[i] = (byte) password.charAt(i);
        }
        var mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(passwordBytes, algorithm));

        var result = new byte[length];
        int blockLength = mac.getMacLength();
        for (int block = 1, offset = 0; offset < length; block++, offset += blockLength) {
            mac.update(salt);
            mac.update(
                    new byte[] {
                        (byte) (block >>> 24),
                        (byte) (block >>> 16),
                        (byte) (block >>> 8),
                        (byte) block
                    });
            var u = mac.doFinal();
            var t = u.clone();
            for (int i = 1; i < ITERATIONS; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++) {
                    t[j] ^= u[j];
                }
            }
            System.arraycopy(t, 0, result, offset, Math.min(blockLength, length - offset));
        }
        return result;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
}
