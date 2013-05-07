package com.vdisk.net;

/**
 * Contains the SDK verison number.
 */
public final class SdkVersion {

    /** Returns the SDK version number. */
    public static String get() {
        return "1.0";  // Filled in by build process.
    }

    public static void main(String[] args) {
        System.out.println("VDisk Java SDK, Version " + get());
    }
}
