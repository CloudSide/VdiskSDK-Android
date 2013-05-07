package com.vdisk.net.exception;

import java.io.BufferedReader;
import java.io.IOException;

import com.vdisk.utils.Logger;

/**
 * Indicates there was trouble parsing a response from VDisk.
 */
public class VDiskParseException extends VDiskException {
	private static final long serialVersionUID = 1L;

	/*
	 * Takes a BufferedReader so it can be reset back to the beginning and read
	 * again into the body variable.
	 */
	public VDiskParseException(BufferedReader reader) {
		super("failed to parse: " + stringifyBody(reader));

		if (Logger.DEBUG_MODE) {
			Logger.writeToFile(stringifyBody(reader));
		}
	}

	public static String stringifyBody(BufferedReader reader) {
		String inputLine = null;

		try {
			if (reader != null) {
				reader.reset();
			}
		} catch (IOException ioe) {
		}
		StringBuffer result = new StringBuffer();
		try {
			while ((inputLine = reader.readLine()) != null) {
				result.append(inputLine);
			}
		} catch (IOException e) {
		}

		return result.toString();
	}

	public VDiskParseException(String message) {
		super(message);
	}
}
