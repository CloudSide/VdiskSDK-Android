package com.vdisk.utils;

import com.vdisk.net.VDiskAPI;
import com.vdisk.net.session.AbstractSession;

public class Config {

	/**
	 * If DEBUG_MODE == true, every api request information will be wrote to
	 * local file (default path is "sdcard/vdisk/api.log"), else, we won't write
	 * to the local file.
	 */
	public static void setDebugMode(boolean debugMode) {
		Logger.DEBUG_MODE = debugMode;
	}

	/**
	 * Returns whether sdk is debug mode or not.
	 * 
	 * @return
	 */
	public static boolean isDebugMode() {
		return Logger.DEBUG_MODE;
	}

	/**
	 * Set the debug log path manually, default path is "sdcard/vdisk/api.log".
	 * 
	 * @param path
	 */
	public static void setDebugLogPath(String path) {
		if (path != null) {
			Logger.logPath = path;
		}
	}

	/**
	 * Returns the debug log path.
	 * 
	 * @return
	 */
	public static String getDebugLogPath() {
		return Logger.logPath;
	}

	/**
	 * If needHttps = true, use https to upload a file in the api of
	 * "/files_put/"
	 * 
	 * @param needHttps
	 */
	public static void setHttpsUpload(boolean needHttps) {
		AbstractSession.NEED_HTTPS_UPLOAD = needHttps;
	}

	/**
	 * Return whether use https or http for upload.
	 * @return
	 */
	public static boolean isHttpsUpload() {
		return AbstractSession.NEED_HTTPS_UPLOAD;
	}

	/**
	 * Set the upload socket timeout in millisecond. Default is 1 minute.
	 * @param ms
	 */
	public static void setUploadSocketTimeout(int ms) {
		if (ms > 0) {
			VDiskAPI.UPLOAD_SO_TIMEOUT_MS = ms;
		}
	}

	/**
	 * Get the upload socket timeout.
	 * @return
	 */
	public static int getUploadSocketTimeout() {
		return VDiskAPI.UPLOAD_SO_TIMEOUT_MS;
	}

	/**
	 * Set the upload response timeout. Default is 20 seconds. 
	 * @param s
	 */
	public static void setUploadResponseTimeout(int s) {
		if (s > 0) {
			VDiskAPI.UPLOAD_RESPONSE_TIMEOUT_S = s;
		}
	}

	/**
	 * Get the upload response timeout.
	 * @return
	 */
	public static int getUploadResponseTimeout() {
		return VDiskAPI.UPLOAD_RESPONSE_TIMEOUT_S;
	}

}
