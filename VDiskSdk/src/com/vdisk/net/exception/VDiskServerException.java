package com.vdisk.net.exception;

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;

/**
 * Wraps any non-200 HTTP responses from an API call. See the constants in this
 * class for the meaning of each error code. You'll typically only want to
 * handle a few specific error codes and show some kind of generic error or
 * retry for the rest.
 */
public class VDiskServerException extends VDiskException {

	public static class Error {

		/** English version of the error. */
		public String error;

		/**
		 * The error in the user's locale, if intended to be displayed to the
		 * user.
		 */
		public String userError;

		@SuppressWarnings("unchecked")
		public Error(Map<String, Object> map) {
			if (map != null) {
				Object err = map.get("error");
				if (err instanceof String) {
					error = (String) err;
				} else if (err instanceof Map<?, ?>) {
					Map<String, Object> detail = (Map<String, Object>) err;
					for (Object val : detail.values()) {
						if (val instanceof String) {
							error = (String) val;
						}
					}
				}
				Object uerr = map.get("user_error");
				if (uerr instanceof String) {
					userError = (String) uerr;
				}
			}
		}
	}

	/** The request was successful. This won't ever be thrown in an exception. */
	public static final int _200_OK = 200;

	/** Resuming download response ok */
	public static final int _206_OK = 206;

	/** Moved to a new location temporarily. */
	public static final int _302_FOUND = 302;

	/**
	 * Contents have not changed (from the given hash, revision, ETag, or
	 * similar parameter).
	 */
	public static final int _304_NOT_MODIFIED = 304;

	/** Bad input parameter. Error message should indicate which one and why. */
	public static final int _400_BAD_REQUEST = 400;

	/** Bad or expired access token. Need to re-authenticate user. */
	public static final int _401_UNAUTHORIZED = 401;

	/** Usually from an invalid app key pair or other permanent error. */
	public static final int _403_FORBIDDEN = 403;

	/** Path not found. */
	public static final int _404_NOT_FOUND = 404;

	/**
	 * Request method not allowed. You shouldn't see this unless writing your
	 * own API calls.
	 */
	public static final int _405_METHOD_NOT_ALLOWED = 405;

	/** Too many metadata entries to return. */
	public static final int _406_NOT_ACCEPTABLE = 406;

	public static final int _409_CONFLICT = 409;

	/**
	 * Typically from trying to upload over HTTP using chunked encoding. The
	 * VDisk API currently does not support chunked transfer encoding.
	 */
	public static final int _411_LENGTH_REQUIRED = 411;

	/** When a thumbnail cannot be created for the input file. */
	public static final int _415_UNSUPPORTED_MEDIA = 415;

	/**
	 * Internal server error. Best to try again or wait until corrected by
	 * VDisk.
	 */
	public static final int _500_INTERNAL_SERVER_ERROR = 500;

	/** Not implemented. */
	public static final int _501_NOT_IMPLEMENTED = 501;

	/** If a VDisk server is down - try again later. */
	public static final int _502_BAD_GATEWAY = 502;

	/** If a VDisk server is not working properly - try again later. */
	public static final int _503_SERVICE_UNAVAILABLE = 503;

	/** User is over quota. */
	public static final int _507_INSUFFICIENT_STORAGE = 507;

	private static final long serialVersionUID = 1L;

	/** The body, if any, of the returned error. */
	public Error body;

	/** The HTTP error code. */
	public int error;

	/** The reason string associated with the error. */
	public String reason;

	/** The server string from the headers. */
	public String server;

	/** The location string from the headers (to handle redirects). */
	public String location;

	/**
	 * Creates a {@link VDiskServerException} from an {@link HttpResponse}.
	 */
	public VDiskServerException(HttpResponse response) {
		this.fillInStackTrace();
		StatusLine status = response.getStatusLine();
		error = status.getStatusCode();
		reason = status.getReasonPhrase();
		server = getHeader(response, "server");
		location = getHeader(response, "location");
	}

	public VDiskServerException(String msg, int statusCode) {
		error = statusCode;
		reason = msg;
	}

	public VDiskServerException(VDiskServerException e) {
		error = e.error;
		reason = e.reason;
		body = e.body;
		server = e.server;
		location = e.location;
	}

	public Map<String, Object> parsedResponse;

	/**
	 * Creates a {@link VDiskServerException} from an {@link HttpResponse}. The
	 * rest parameter must be a Map of String to Object.
	 */
	@SuppressWarnings("unchecked")
	public VDiskServerException(HttpResponse response, Object rest) {
		this(response);

		if (rest != null && rest instanceof Map<?, ?>) {
			parsedResponse = (Map<String, Object>) rest;
			body = new Error(parsedResponse);
		}
	}

	/**
	 * When this exception comes from creating a new account, returns whether
	 * the request failed because an account with the email address already
	 * exists.
	 */
	public boolean isDuplicateAccount() {
		return (error == 400 && body != null && body.error.contains("taken"));
	}

	@Override
	public String toString() {
		return "VDiskServerException (" + server + "): " + error + " " + reason
				+ " (" + body.error + ")";
	}

	/**
	 * Whether the given response is valid when it has no body (only some error
	 * codes are allowed without a reason, currently 302 and 304).
	 */
	public static boolean isValidWithNullBody(HttpResponse response) {
		int code = response.getStatusLine().getStatusCode();
		if (code == _302_FOUND) {
			/*
			 * String location = getHeader(response, "location");
			 * 
			 * if (location != null) { int loc = location.indexOf("://"); if
			 * (loc > -1) { location = location.substring(loc+3); loc =
			 * location.indexOf("/"); if (loc > -1) { location =
			 * location.substring(0, loc); if
			 * (location.toLowerCase().contains("data.vdisk.me")) { return true;
			 * } } }
			 * 
			 * }
			 */
			return false;
		} else if (code == _304_NOT_MODIFIED) {
			return true;
		}
		return false;
	}

	public static String getHeader(HttpResponse response, String name) {
		String value = null;
		Header serverheader = response.getFirstHeader(name);
		if (serverheader != null) {
			value = serverheader.getValue();
		}
		return value;
	}
}
