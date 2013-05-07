
package com.vdisk.net.exception;

import javax.net.ssl.SSLException;

/**
 * Wraps any SSL-related exceptions thrown when using the API.
 */
public class VDiskSSLException extends VDiskIOException {

    private static final long serialVersionUID = 1L;

    public VDiskSSLException(SSLException e) {
        super(e);
    }

}
