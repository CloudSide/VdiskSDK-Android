
package com.vdisk.net.exception;

/**
 * Thrown when trying to upload a file larger than the API can handle. See
 * {@link VDiskAPI#MAX_UPLOAD_SIZE}.
 */
public class VDiskFileSizeException extends VDiskException {
    private static final long serialVersionUID = 1L;

    public VDiskFileSizeException(String s) {
        super(s);
    }
}
