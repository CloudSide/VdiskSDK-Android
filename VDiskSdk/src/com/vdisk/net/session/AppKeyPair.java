package com.vdisk.net.session;

import java.io.Serializable;

/**
 * <p>
 * Holds your app's key and secret.
 * </p>
 */
public final class AppKeyPair implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
     * The "key" portion of the pair.  For example, the "consumer key",
     * "request token", or "access token".  Will never contain the "|"
     * character.
     */
    public final String key;

    /**
     * The "secret" portion of the pair.  For example, the "consumer secret",
     * "request token secret", or "access token secret".
     */
    public final String secret;

    /**
     * @param key assigned to {@link #key}.
     * @param secret assigned to {@link #secret}.
     *
     * @throws IllegalArgumentException if key or secret is null or invalid.
     */
    public AppKeyPair(String key, String secret) {
        if (key == null)
            throw new IllegalArgumentException("'key' must be non-null");
        if (key.contains("|"))
            throw new IllegalArgumentException("'key' must not contain a \"|\" character: \"" + key + "\"");
        if (secret == null)
            throw new IllegalArgumentException("'secret' must be non-null");

        this.key = key;
        this.secret = secret;
    }
}
