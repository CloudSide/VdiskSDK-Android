
package com.vdisk.net.exception;

import java.io.IOException;

/**
 * Encapsulates an IOExceptions when using the API, typically resulting from
 * network-related issues.
 */
public class VDiskIOException extends VDiskException {
    private static final long serialVersionUID = 2L;

    public VDiskIOException(IOException e) {
        super(e);
    }

    public VDiskIOException(String detailMessage) {
        super(detailMessage);
    }
}
