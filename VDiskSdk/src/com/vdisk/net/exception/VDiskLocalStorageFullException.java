
package com.vdisk.net.exception;

/**
 * Thrown when writing to local storage and there is no more room.
 */
public class VDiskLocalStorageFullException extends VDiskException {
    private static final long serialVersionUID = 2L;

    public VDiskLocalStorageFullException() {}
}
