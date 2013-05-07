
package com.vdisk.net.exception;

/**
 * A base for all exceptions from using the API. Catch this instead of specific
 * subclasses when you want to deal with those issues in a generic way.
 */
public class VDiskException extends Exception {

    private static final long serialVersionUID = 1L;
    
    protected VDiskException() {
        super();
    }

    public VDiskException(String detailMessage) {
        super(detailMessage);
    }

    public VDiskException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public VDiskException(Throwable throwable) {
        super(throwable);
    }
}
