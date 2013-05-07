
package com.vdisk.net.exception;

import com.vdisk.net.session.AccessToken;

/**
 * Thrown when the API can only be used when linked but it's not. This will
 * happen either because you have not set an {@link AccessToken} on your
 * session, or because the user unlinked your app (revoked the access token
 * pair).
 */
public class VDiskUnlinkedException extends VDiskException {
    private static final long serialVersionUID = 1L;

    public VDiskUnlinkedException() {
        super();
    }

    public VDiskUnlinkedException(String message) {
        super(message);
    }
}
