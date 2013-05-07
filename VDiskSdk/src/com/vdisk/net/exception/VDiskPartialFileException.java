
package com.vdisk.net.exception;

/**
 * Used when only part of a file was transferred to/from VDisk when using the
 * API.
 */
public class VDiskPartialFileException extends VDiskException {
    private static final long serialVersionUID = 2L;

    /**
     * The number of bytes successfully transferred.
     */
    public final long bytesTransferred;

    public VDiskPartialFileException(long transferred) {
        bytesTransferred = transferred;
    }
}
