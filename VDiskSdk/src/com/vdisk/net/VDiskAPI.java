package com.vdisk.net;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.vdisk.android.ComplexUploadHandler;
import com.vdisk.android.ComplexUploadHandler.ComplexUploadStatus;
import com.vdisk.android.VDiskAuthSession;
import com.vdisk.net.ProgressListener.ProgressHttpEntity;
import com.vdisk.net.RESTUtility.RequestMethod;
import com.vdisk.net.exception.VDiskDownloadFileExistException;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskFileSizeException;
import com.vdisk.net.exception.VDiskIOException;
import com.vdisk.net.exception.VDiskLocalStorageFullException;
import com.vdisk.net.exception.VDiskParseException;
import com.vdisk.net.exception.VDiskPartialFileException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.exception.VDiskUnlinkedException;
import com.vdisk.net.jsonextract.JsonExtractionException;
import com.vdisk.net.jsonextract.JsonExtractor;
import com.vdisk.net.jsonextract.JsonList;
import com.vdisk.net.jsonextract.JsonMap;
import com.vdisk.net.jsonextract.JsonThing;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session;
import com.vdisk.net.session.WeiboAccessToken;
import com.vdisk.utils.Digest;
import com.vdisk.utils.Logger;
import com.vdisk.utils.Signature;

/**
 * Location of the VDisk API functions.
 * 
 * The class is parameterized with the type of session it uses. This will be the
 * same as the type of session you pass into the constructor.
 */
public class VDiskAPI<SESS_T extends Session> {

	private static final String TAG = "VDiskAPI";
	/**
	 * The version of the API that this code uses.
	 */
	public static final int VERSION = 2;

	/**
	 * The version of this VDisk SDK.
	 */
	public static final String SDK_VERSION = SdkVersion.get();

	/**
	 * The max upload file size that VDisk servers can handle, in bytes.
	 */
	public static final long MAX_UPLOAD_SIZE = 500 * 1024 * 1024; // 500MB

	private static final int REVISION_DEFAULT_LIMIT = 10;
	private static final int SEARCH_DEFAULT_LIMIT = 1000;
	public static int UPLOAD_SO_TIMEOUT_MS = 1 * 60 * 1000; // 1 minute
	private static final int UPLOAD_MERGE_TIMEOUT_MS = 2 * 60 * 1000;
	
	public static int UPLOAD_RESPONSE_TIMEOUT_S = 60; //1 minute
	

	private static final String DOWNLOAD_TEMP_FILE_SUFFIX = ".vdisktemp";

	private static String SINA_STORAGE_SERVICE_HOST;

	private static final long UPLOAD_DEFAULT_SECTION_SIZE = 4 * 1024 * 1024; // 4MB

	protected final SESS_T session;

	public VDiskAPI(SESS_T session) {
		if (session == null) {
			throw new IllegalArgumentException("Session must not be null.");
		}
		this.session = session;
	}

	/**
	 * Information about a user's account.
	 */
	public static class Account implements Serializable {

		private static final long serialVersionUID = 2097522622341535732L;

		/** The user's quota, in bytes. */
		public final long quota;

		/** The user's consumed quota. */
		public final long consumed;

		/** The user's vdisk ID. */
		public final long uid;

		/** The user's weibo ID. */
		public final long sina_uid;

		protected Account(Map<String, Object> map) {
			uid = getFromMapAsLong(map, "uid");
			sina_uid = getFromMapAsLong(map, "sina_uid");

			Object quotaInfo = map.get("quota_info");
			@SuppressWarnings("unchecked")
			Map<String, Object> quotamap = (Map<String, Object>) quotaInfo;
			quota = getFromMapAsLong(quotamap, "quota");
			consumed = getFromMapAsLong(quotamap, "consumed");
		}

		/**
		 * Creates an account object from an initial set of values.
		 */
		protected Account(long uid, long sina_uid, long quota, long consumed) {
			this.uid = uid;
			this.sina_uid = sina_uid;
			this.quota = quota;
			this.consumed = consumed;
		}
	}

	/**
	 * Information about big file sections for uploading.
	 */
	@SuppressLint("UseSparseArrays")
	public static class VDiskUploadFileInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		public String uploadKey;
		public String uploadId;
		public HashMap<Integer, String> partSigns;// collection of each
													// segment's uri.
		public String s3Host;
		public String md5s;// all segment's md5 splited by ",".
		public String sha1;// sha1 of file.
		public int point;// identify which segment will to be uploaded，start
							// from zero.
		public long expireTime;// upload key will expire in 2 days.
		public long segmentLength; // the length of each upload segment.
		public int segmentNum;

		public Entry metadata;
		/**
		 * If true, it means there's a same file already on server, you needn't
		 * upload it, server can copy one to your vdisk cloud.
		 */
		public boolean isBlitzUpload;

		public String srcPath; // local file path.
		public String desPath; // target server path.
		public String id; // the key to identify the file.

		protected VDiskUploadFileInfo(Map<String, Object> map, int segmentNum,
				String s3Host, long segmentLength, String sha1, String srcPath,
				String desPath) throws VDiskException {
			this.s3Host = s3Host;
			this.segmentLength = segmentLength;
			this.sha1 = sha1;
			this.segmentNum = segmentNum;
			this.srcPath = srcPath;
			this.desPath = desPath;
			setFileId();

			uploadKey = getFromMapAsString(map, "upload_key");
			uploadId = getFromMapAsString(map, "upload_id");

			if (uploadKey == null || uploadId == null) {
				metadata = new Entry(map);
				isBlitzUpload = true;
			} else {
				partSigns = new HashMap<Integer, String>();
				Object sectionInfo = map.get("part_sign");

				@SuppressWarnings("unchecked")
				Map<String, Object> sectionMap = (Map<String, Object>) sectionInfo;

				try {
					for (int i = 1; i <= segmentNum; i++) {
						@SuppressWarnings("unchecked")
						Map<String, Object> section = (Map<String, Object>) sectionMap
								.get(String.valueOf(i));
						String number = getFromMapAsString(section,
								"part_number");
						String uri = getFromMapAsString(section, "uri");
						partSigns.put(Integer.parseInt(number), uri);
					}

					expireTime = System.currentTimeMillis() + 2 * 24 * 60 * 60
							* 1000;
				} catch (NumberFormatException e) {
					throw new VDiskParseException(
							"Invalid segment info from server when uploading large file.");
				}
			}
		}

		private void setFileId() throws VDiskException {
			this.id = Digest.md5String(srcPath + desPath);
		}

	}

	/**
	 * A metadata entry that describes a file or folder.
	 */
	public static class Entry {

		/** Size of the file. */
		public long bytes;

		/**
		 * If a directory, the hash is its "current version". If the hash
		 * changes between calls, then one of the directory's immediate children
		 * has changed.
		 */
		public String hash;

		/**
		 * Name of the icon to display for this entry.
		 */
		public String icon;

		/** True if this entry is a directory, or false if it's a file. */
		public boolean isDir;

		/**
		 * Last modified date, in "EEE, dd MMM yyyy kk:mm:ss ZZZZZ" form (see
		 * {@code RESTUtility#parseDate(String)} for parsing this value.
		 */
		public String modified;

		/**
		 * For a file, this is the modification time set by the client when the
		 * file was added to VDisk. Since this time is not verified (the VDisk
		 * server stores whatever the client sends up) this should only be used
		 * for display purposes (such as sorting) and not, for example, to
		 * determine if a file has changed or not.
		 * 
		 * <p>
		 * This is not set for folders.
		 * </p>
		 */
		public String clientMtime;

		/** Path to the file from the root. */
		public String path;

		/**
		 * Name of the root, usually either "VDisk" or "app_folder".
		 */
		public String root;

		/**
		 * Human-readable (and localized, if possible) description of the file
		 * size.
		 */
		public String size;

		/** The file's MIME type. */
		public String mimeType;

		/** The file's md5. */
		public String md5;

		/** The file's sha1. */
		public String sha1;

		public String revision;

		public String thumb;

		/**
		 * Full unique ID for this file's revision. This is a string, and not
		 * equivalent to the old revision integer.
		 */
		public String rev;

		/** Whether a thumbnail for this is available. */
		public boolean thumbExists;

		/**
		 * Whether this entry has been deleted but not removed from the metadata
		 * yet. Most likely you'll only want to show entries with isDeleted ==
		 * false.
		 */
		public boolean isDeleted;

		/** A list of immediate children if this is a directory. */
		public List<Entry> contents;

		/**
		 * Creates an entry from a map, usually received from the metadata call.
		 * It's unlikely you'll want to create these yourself.
		 * 
		 * @param map
		 *            the map representation of the JSON received from the
		 *            metadata call, which should look like this:
		 * 
		 *            <pre>
		 * {
		 *    "hash": "528dda36e3150ba28040052bbf1bfbd1",
		 *    "thumb_exists": false,
		 *    "bytes": 0,
		 *    "modified": "Sat, 12 Jan 2008 23:10:10 +0000",
		 *    "path": "/Public",
		 *    "is_dir": true,
		 *    "size": "0 bytes",
		 *    "root": "VDisk",
		 *    "contents": [
		 *    {
		 *        "thumb_exists": false,
		 *        "bytes": 0,
		 *        "modified": "Wed, 16 Jan 2008 09:11:59 +0000",
		 *        "path": "/Public/\u2665asdas\u2665",
		 *        "is_dir": true,
		 *        "icon": "folder",
		 *        "size": "0 bytes"
		 *    },
		 *    {
		 *        "thumb_exists": false,
		 *        "bytes": 4392763,
		 *        "modified": "Thu, 15 Jan 2009 02:52:43 +0000",
		 *        "path": "/Public/\u540d\u79f0\u672a\u8a2d\u5b9a\u30d5\u30a9\u30eb\u30c0.zip",
		 *        "is_dir": false,
		 *        "icon": "page_white_compressed",
		 *        "size": "4.2MB"
		 *    }
		 *    ],
		 *    "icon": "folder_public"
		 * }
		 * </pre>
		 */
		@SuppressWarnings("unchecked")
		public Entry(Map<String, Object> map) {
			bytes = getFromMapAsLong(map, "bytes");
			hash = (String) map.get("hash");
			icon = getFromMapAsString(map, "icon");
			isDir = getFromMapAsBoolean(map, "is_dir");
			modified = (String) map.get("modified");
			clientMtime = (String) map.get("client_mtime");
			path = (String) map.get("path");
			root = (String) map.get("root");
			size = (String) map.get("size");
			mimeType = (String) map.get("mime_type");
			rev = getFromMapAsString(map, "rev");
			revision = getFromMapAsString(map, "revision");
			md5 = (String) map.get("md5");
			sha1 = (String) map.get("sha1");
			thumbExists = getFromMapAsBoolean(map, "thumb_exists");
			isDeleted = getFromMapAsBoolean(map, "is_deleted");
			thumb = (String) map.get("thumb");

			Object json_contents = map.get("contents");
			if (json_contents != null && json_contents instanceof JSONArray) {
				contents = new ArrayList<Entry>();
				Object entry;
				Iterator<?> it = ((JSONArray) json_contents).iterator();
				while (it.hasNext()) {
					entry = it.next();
					if (entry instanceof Map) {
						contents.add(new Entry((Map<String, Object>) entry));
					}
				}
			} else {
				contents = null;
			}
		}

		public Entry() {
		}

		/**
		 * Returns the file name if this is a file (the part after the last
		 * slash in the path).
		 */
		public String fileName() {
			int ind = path.lastIndexOf('/');
			return path.substring(ind + 1, path.length());
		}

		/**
		 * Returns the path of the parent directory if this is a file.
		 */
		public String parentPath() {
			if (path.equals("/")) {
				return "";
			} else {
				int ind = path.lastIndexOf('/');
				return path.substring(0, ind + 1);
			}
		}

		public static final JsonExtractor<Entry> JsonExtractor = new JsonExtractor<Entry>() {
			public Entry extract(JsonThing jt) throws JsonExtractionException {
				return new Entry(jt.expectMap().internal);
			}
		};
	}

	/**
	 * Contains info describing a downloaded file.
	 */
	public static final class VDiskFileInfo implements Serializable {

		private static final long serialVersionUID = -385278101788930L;

		private String mimeType = null;
		private long fileSize = -1;
		private long contentLength = -1;
		private String charset = null;
		private Entry metadata = null;
		private String downloadURL = null;

		// fileSize and metadata are guaranteed to be valid if the constructor
		// doesn't throw an exception.
		private VDiskFileInfo(HttpResponse response, Entry metadata,
				String location) throws VDiskException {
			this.metadata = metadata;
			this.downloadURL = location;

			if (metadata == null) {
				throw new VDiskParseException("Error parsing metadata.");
			}

			fileSize = parseFileSize(response, metadata);
			contentLength = parseContentLength(response);

			if (fileSize == -1) {
				throw new VDiskParseException("Error determining file size.");
			}

			// Parse mime type and charset.
			Header contentType = response.getFirstHeader("Content-Type");
			if (contentType != null) {
				String contentVal = contentType.getValue();
				if (contentVal != null) {
					String[] splits = contentVal.split(";");
					if (splits.length > 0) {
						mimeType = splits[0].trim();
					}
					if (splits.length > 1) {
						splits = splits[1].split("=");
						if (splits.length > 1) {
							charset = splits[1].trim();
						}
					}
				}
			}
		}

		/**
		 * Parses the JSON in the the 'x-VDisk-metadata' header field of the
		 * http response.
		 * 
		 * @param response
		 *            The http response for the downloaded file.
		 * @return An Entry object based on the metadata JSON. Can be null if
		 *         metadata isn't available.
		 */
		public static Entry parseXVDiskMetadata(HttpResponse response) {
			if (response == null) {
				return null;
			}

			Header xVDiskMetadataHeader = response
					.getFirstHeader("X-VDisk-Metadata");
			if (xVDiskMetadataHeader == null) {
				return null;
			}

			// Returns null if the parsing fails.
			String json = xVDiskMetadataHeader.getValue();
			Object metadata = JSONValue.parse(json);
			if (metadata == null) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) metadata;
			return new Entry(map);
		}

		/**
		 * Determines the size of the downloaded file.
		 * 
		 * @param response
		 *            The http response for the file whose size we're interested
		 *            in.
		 * @param metadata
		 *            The metadata associated with the file. Can be null if
		 *            unavailable.
		 * 
		 * @return The determined file size. -1 if the size of the file can't be
		 *         determined.
		 */
		private static long parseFileSize(HttpResponse response, Entry metadata) {
			// Fall back on the metadata, if available.
			if (metadata != null) {
				return metadata.bytes;
			}

			// Use the response's content-length, if available (negative if
			// unavailable).
			long contentLength = response.getEntity().getContentLength();
			if (contentLength >= 0) {
				return contentLength;
			}

			return -1;
		}

		/**
		 * Determines the content length of the downloaded file.
		 * 
		 * @param response
		 *            The http response for the file whose size we're interested
		 *            in.
		 * @return The file content length. -1 if the content length of the file
		 *         can't be determined.
		 */
		private static long parseContentLength(HttpResponse response) {
			// Use the response's content-length, if available (negative if
			// unavailable).
			long contentLength = response.getEntity().getContentLength();
			if (contentLength >= 0) {
				return contentLength;
			}

			return -1;
		}

		/**
		 * Returns the MIME type of the associated file, or null if it is
		 * unknown.
		 */
		public final String getMimeType() {
			return mimeType;
		}

		/**
		 * Returns the content length of the file in bytes (always >= 0).
		 */
		public final long getContentLength() {
			return contentLength;
		}

		/**
		 * Returns the size of the file in bytes (always >= 0).
		 */
		public final long getFileSize() {
			return fileSize;
		}

		/**
		 * Returns the charset of the associated file, or null if it is unknown.
		 */
		public final String getCharset() {
			return charset;
		}

		/**
		 * Returns the metadata of the associated file (always non-null).
		 */
		public final Entry getMetadata() {
			return metadata;
		}

		/**
		 * Returns the s3_url of the associated file (always non-null).
		 * 
		 * @return
		 */
		public final String getDownloadURL() {
			return downloadURL;
		}

	}

	/**
	 * An {@link InputStream} for a file download that includes the associated
	 * {@link VDiskFileInfo}. Closing this stream will cancel the associated
	 * download request.
	 */
	public static class VDiskInputStream extends FilterInputStream {
		private final HttpUriRequest request;
		private final VDiskFileInfo info;

		public VDiskInputStream(HttpUriRequest request, HttpResponse response,
				Entry metadata, String location) throws VDiskException {
			super(null);
			HttpEntity entity = response.getEntity();
			if (entity == null) {
			}
			try {
				if (entity != null) {
					in = entity.getContent();
				}
			} catch (IOException e) {
				throw new VDiskIOException(e);
			}

			this.request = request;
			info = new VDiskFileInfo(response, metadata, location);
		}

		/**
		 * Closes this stream and aborts the request to VDisk, releasing any
		 * associated resources. No more bytes will be downloaded after this is
		 * called.
		 * 
		 * @throws IOException
		 *             if an error occurs while closing this stream.
		 */
		@Override
		public void close() throws IOException {
			// Aborting the request also closes the input stream that it
			// creates (the in variable). Do not try to close it again.
			request.abort();
		}

		/**
		 * Returns the {@link VDiskFileInfo} for the associated file.
		 */
		public VDiskFileInfo getFileInfo() {
			return info;
		}

		/**
		 * Copies from a {@link VDiskInputStream} to an {@link OutputStream},
		 * optionally providing updates via a {@link ProgressListener}. You
		 * probably won't have a use for this function because most API
		 * functions that return a {@link VDiskInputStream} have an alternate
		 * that will copy to an {@link OutputStream} for you.
		 * 
		 * @param os
		 *            the stream to copy to.
		 * @param listener
		 *            an optional {@link ProgressListener} to receive progress
		 *            updates as the stream is copied, or null.
		 * 
		 * @throws VDiskPartialFileException
		 *             if only part of the input stream was copied.
		 * @throws VDiskIOException
		 *             for network-related errors.
		 * @throws VDiskLocalStorageFullException
		 *             if there is no more room to write to the output stream.
		 * @throws VDiskException
		 *             for any other unknown errors. This is also a superclass
		 *             of all other VDisk exceptions, so you may want to only
		 *             catch this exception which signals that some kind of
		 *             error occurred.
		 */
		public void copyStreamToOutput(OutputStream os, File targetFile,
				ProgressListener listener, boolean isThumnail)
				throws VDiskIOException, VDiskPartialFileException,
				VDiskLocalStorageFullException {
			BufferedOutputStream bos = null;
			long totalRead = getRange(targetFile);
			long lastListened = 0;
			long length = isThumnail ? info.getContentLength() : info
					.getFileSize();

			try {
				bos = new BufferedOutputStream(os);

				byte[] buffer = new byte[4096];
				int read;
				while (true) {
					read = read(buffer);
					if (read < 0) {
						if (length >= 0 && totalRead < length) {
							// We've reached the end of the file, but it's
							// unexpected.
							targetFile.delete();
							throw new VDiskPartialFileException(totalRead);
						}
						// TODO check for partial success, if possible
						break;
					}

					bos.write(buffer, 0, read);

					totalRead += read;

					if (listener != null) {
						long now = System.currentTimeMillis();
						if (now - lastListened > listener.progressInterval()) {
							lastListened = now;
							listener.onProgress(totalRead, length);
						}
					}
				}

				bos.flush();
				os.flush();
				// Make sure it's flushed out to disk
				try {
					if (os instanceof FileOutputStream) {
						((FileOutputStream) os).getFD().sync();
					}
				} catch (SyncFailedException e) {
				}

				if (targetFile != null) {
					String tempName = targetFile.getName();
					File parent = targetFile.getParentFile();
					if (tempName.endsWith(VDiskAPI.DOWNLOAD_TEMP_FILE_SUFFIX)) {
						String filename = tempName.substring(0, tempName.lastIndexOf("."));
						File newFile = new File(parent, filename);
						targetFile.renameTo(newFile);
					}
				}

			} catch (IOException e) {
				String message = e.getMessage();
				if (message != null && message.startsWith("No space")) {
					// This is a hack, but it seems to be the only way to check
					// which exception it is.
					throw new VDiskLocalStorageFullException();
				} else {
					/*
					 * If the output stream was closed, we notify the caller
					 * that only part of the file was copied. This could have
					 * been because this request is being intentionally
					 * canceled.
					 */
					e.printStackTrace();
					throw new VDiskPartialFileException(totalRead);
				}
			} finally {
				if (bos != null) {
					try {
						bos.close();
					} catch (IOException e) {
					}
				}
				if (os != null) {
					try {
						os.close();
					} catch (IOException e) {
					}
				}
				// This will also abort/finish the request if the download is
				// canceled early.
				try {
					close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * A request to upload a file to VDisk. This request can be canceled by
	 * calling abort().
	 */
	public interface UploadRequest {
		/**
		 * Aborts the request. The original call to upload() will throw a
		 * {@link VDiskPartialFileException}.
		 */
		public void abort();

		/**
		 * Executes the request.
		 * 
		 * @return an {@link Entry} representing the uploaded file.
		 * 
		 * @throws VDiskPartialFileException
		 *             if the request was canceled before completion.
		 * @throws VDiskServerException
		 *             if the server responds with an error code. See the
		 *             constants in {@link VDiskServerException} for the meaning
		 *             of each error code. The most common error codes you can
		 *             expect from this call are 404 (path to upload not found),
		 *             507 (user over quota), and 400 (unexpected parent rev).
		 * @throws VDiskIOException
		 *             if any network-related error occurs.
		 * @throws VDiskException
		 *             for any other unknown errors. This is also a superclass
		 *             of all other VDisk exceptions, so you may want to only
		 *             catch this exception which signals that some kind of
		 *             error occurred.
		 */
		public Entry upload() throws VDiskException;
	}

	protected static final class BasicUploadRequest implements UploadRequest {
		private final HttpUriRequest request;
		private final Session session;

		public BasicUploadRequest(HttpUriRequest request, Session session) {
			this.request = request;
			this.session = session;
		}

		/**
		 * Aborts the request. The original call to upload() will throw a
		 * {@link VDiskPartialFileException}.
		 */
		@Override
		public void abort() {
			request.abort();
		}

		/**
		 * Executes the request.
		 * 
		 * @return an {@link Entry} representing the uploaded file.
		 * 
		 * @throws VDiskPartialFileException
		 *             if the request was canceled before completion.
		 * @throws VDiskServerException
		 *             if the server responds with an error code. See the
		 *             constants in {@link VDiskServerException} for the meaning
		 *             of each error code. The most common error codes you can
		 *             expect from this call are 404 (path to upload not found),
		 *             507 (user over quota), and 400 (unexpected parent rev).
		 * @throws VDiskIOException
		 *             if any network-related error occurs.
		 * @throws VDiskException
		 *             for any other unknown errors. This is also a superclass
		 *             of all other VDisk exceptions, so you may want to only
		 *             catch this exception which signals that some kind of
		 *             error occurred.
		 */
		@Override
		public Entry upload() throws VDiskException {
			HttpResponse hresp;
			try {
				hresp = RESTUtility.execute(session, request,
						UPLOAD_SO_TIMEOUT_MS);
			} catch (VDiskIOException e) {
				if (request.isAborted()) {
					throw new VDiskPartialFileException(-1);
				} else {
					throw e;
				}
			}

			Object resp = RESTUtility.parseAsJSON(hresp);

			@SuppressWarnings("unchecked")
			Map<String, Object> ret = (Map<String, Object>) resp;

			return new Entry(ret);
		}
	}

	protected static final class ComplexUploadRequest implements UploadRequest {
		private final HttpUriRequest request;
		private final Session session;

		public ComplexUploadRequest(HttpUriRequest request, Session session) {
			this.request = request;
			this.session = session;
		}

		@Override
		public void abort() {
			request.abort();
		}

		@Override
		public Entry upload() throws VDiskException {
			try {
				RESTUtility.execute(session, request, UPLOAD_SO_TIMEOUT_MS);
			} catch (VDiskIOException e) {
				if (request.isAborted()) {
					throw new VDiskPartialFileException(-1);
				} else {
					throw e;
				}
			}

			return null;
		}
	}

	/**
	 * Holds an {@link HttpUriRequest} and the associated {@link HttpResponse}.
	 */
	public static final class RequestAndResponse {
		/** The request */
		public final HttpUriRequest request;
		/** The response */
		public final HttpResponse response;

		protected RequestAndResponse(HttpUriRequest request,
				HttpResponse response) {
			this.request = request;
			this.response = response;
		}
	}

	/**
	 * Contains a link to a VDisk stream or share and its expiration date.
	 */
	public static class VDiskLink {
		/** The url it links to */
		public final String url;
		/** When the url expires (after which this link will no longer work) */
		public final Date expires;

		private VDiskLink(String returl, boolean secure) {
			if (!secure && returl.startsWith("https://")) {
				returl = returl.replaceFirst("https://", "http://");
				returl = returl.replaceFirst(":443/", "/");
			}
			url = returl;
			expires = null;
		}

		private VDiskLink(Map<String, Object> map) {
			this(map, true);
		}

		/**
		 * Creates a VDiskLink, with security optionally set to false. This is
		 * useful for some clients, such as Android, which use this to play a
		 * streaming audio or video file, and which are unable to play from
		 * streaming https links.
		 * 
		 * @param map
		 *            the parsed parameters returned from VDisk
		 * @param secure
		 *            if false, returns an http link
		 */
		private VDiskLink(Map<String, Object> map, boolean secure) {
			String returl = (String) map.get("url");
			String exp = (String) map.get("expires");
			if (exp != null) {
				expires = RESTUtility.parseDate(exp);
			} else {
				expires = null;
			}

			if (!secure && returl.startsWith("https://")) {
				returl = returl.replaceFirst("https://", "http://");
				returl = returl.replaceFirst(":443/", "/");
			}
			url = returl;
		}
	}

	/**
	 * Represents the size of thumbnails that the API can return.
	 */
	public enum ThumbSize {

		ICON_32x32("small"),
		/** 60 width or 60 height, with original aspect ratio. */
		ICON_60x60("s"),
		/** 128 width or 128 height, with original aspect ratio. */
		ICON_100x100("m"),

		ICON_191x191("large"),
		/** 513 width or 513 height, with original aspect ratio. */
		ICON_640x480("l"),
		/** 768 width or 1024 height, with original aspect ratio. */
		ICON_1024x768("xl");

		private String size;

		ThumbSize(String size) {
			this.size = size;
		}

		public String toAPISize() {
			return size;
		}
	}

	/**
	 * Returns the {@link Session} that this API is using.
	 */
	public SESS_T getSession() {
		return session;
	}

	/**
	 * oauth2.0 authorization_code
	 * 
	 * @throws VDiskException
	 */
	public String doOAuth2Authorization(AppKeyPair appKeyPair, String code,
			String redirectUrl, Context ctx) throws VDiskException {

		String[] params = { "client_id", appKeyPair.key, "client_secret",
				appKeyPair.secret, "grant_type", "authorization_code", "code",
				code, "redirect_uri", redirectUrl };
		return RESTUtility.parseResponse(session,
				VDiskAuthSession.URL_OAUTH2_ACCESS_TOKEN, params, ctx);
	}

	/**
	 * oauth2.0 password
	 * 
	 * @throws VDiskException
	 */
	public String doOAuth2Password(String usrname, String password,
			AppKeyPair appKeyPair, Context ctx) throws VDiskException {

		String[] params = { "username", usrname, "password", password,
				"client_id ", appKeyPair.key, "client_secret",
				appKeyPair.secret, "grant_type", "password" };
		return RESTUtility.parseResponse(session,
				VDiskAuthSession.URL_OAUTH2_ACCESS_TOKEN, params, ctx);
	}

	/**
	 * oauth2.0 refresh_token
	 * 
	 * @throws VDiskException
	 */
	public String doOAuth2Refresh(AppKeyPair appKeyPair, String refreshToken,
			Context ctx) throws VDiskException {

		String[] params = { "client_id", appKeyPair.key, "client_secret",
				appKeyPair.secret, "grant_type", "refresh_token",
				"refresh_token", refreshToken };
		return RESTUtility.parseResponse(session,
				VDiskAuthSession.URL_OAUTH2_ACCESS_TOKEN, params, ctx);
	}

	/**
	 * Returns the {@link Account} associated with the current {@link Session}.
	 * 
	 * @return the current session's {@link Account}.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code.
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Account accountInfo() throws VDiskException {
		assertAuthenticated();

		@SuppressWarnings("unchecked")
		Map<String, Object> accountInfo = (Map<String, Object>) RESTUtility
				.request(RequestMethod.GET, session.getAPIServer(),
						"/account/info", VERSION, null, session);

		return new Account(accountInfo);
	}

	/**
	 * Downloads a file from VDisk, copying it to the output stream. Returns the
	 * {@link VDiskFileInfo} for the file.
	 * 
	 * @param path
	 *            the VDisk path to the file.
	 * @param rev
	 *            the revision (from the file's metadata) of the file to
	 *            download, or null to get the latest version.
	 * @param os
	 *            the {@link OutputStream} to write the file to.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive progress
	 *            updates as the file downloads, or null.
	 * 
	 * @return the {@link VDiskFileInfo} for the downloaded file.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path not found) and 400 (bad rev).
	 * @throws VDiskPartialFileException
	 *             if a network error occurs during the download.
	 * @throws VDiskIOException
	 *             for some network-related errors.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public VDiskFileInfo getFile(String path, String rev, OutputStream os,
			File targetFile, ProgressListener listener) throws VDiskException {
		VDiskInputStream dis = getFileStream(path, rev, targetFile);

		if (dis != null) {
			dis.copyStreamToOutput(os, targetFile, listener, false);
			return dis.getFileInfo();
		}
		return null;
	}

	/**
	 * Create the download file dirs by the target path.
	 * 
	 * @param path
	 * @return
	 */
	public File createDownloadDirFile(String targetPath) {
		String filepath = targetPath + DOWNLOAD_TEMP_FILE_SUFFIX;
		int pos = filepath.lastIndexOf("/");
		String dirpath = filepath.substring(0, pos + 1);
		if (!dirpath.startsWith("/"))
			dirpath = "/" + dirpath;
		File f = new File(dirpath);
		if (!f.exists())
			f.mkdirs();
		return new File(filepath);
	}

	/**
	 * Get the info of the file which will be downloaded, including the download
	 * url.
	 * 
	 * @param path
	 * @param rev
	 * @return
	 * @throws VDiskException
	 */
	public VDiskFileInfo getFileLink(String path, String rev)
			throws VDiskException {
		VDiskFileInfo fileInfo = getVDiskFileInfo(path, rev);
		if (fileInfo != null) {
			return fileInfo;
		}
		return null;
	}

	/**
	 * Downloads a file from VDisk. Returns a {@link VDiskInputStream} via which
	 * the file contents can be read from the network. You must close the stream
	 * when you're done with it to release all resources.
	 * 
	 * You can also cancel the download by closing the returned
	 * {@link VDiskInputStream} at any time.
	 * 
	 * @param path
	 *            the VDisk path to the file.
	 * @param rev
	 *            the revision (from the file's metadata) of the file to
	 *            download, or null to get the latest version.
	 * 
	 * @return a {@link VDiskInputStream} from which to read the file contents.
	 *         The contents are retrieved from the network and not stored
	 *         locally.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path not found) and 400 (bad rev).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public VDiskInputStream getFileStream(String path, String rev,
			File targetFile) throws VDiskException {
		VDiskFileInfo fileInfo = getVDiskFileInfo(path, rev);

		if (fileInfo != null) {
			Entry metadata = fileInfo.metadata;
			String location = fileInfo.downloadURL;

			Log.d(TAG, "download location: " + location);

			String filename = targetFile.getName().replace(
					DOWNLOAD_TEMP_FILE_SUFFIX, "");
			File file = new File(targetFile.getParent(), filename);

			if (file.exists() && file.length() == metadata.bytes) {
				targetFile.delete();
				throw new VDiskDownloadFileExistException();
			}

			RequestAndResponse rp = RESTUtility.streamRequestAndResponse(
					RequestMethod.GET, session, location, null,
					getRange(targetFile), metadata.md5, false, -1);

			return new VDiskInputStream(rp.request, rp.response, metadata,
					location);
		}

		return null;
	}

	/**
	 * Get the info of the file which will be downloaded. The info includes the
	 * download url and the metadata of the file.
	 * 
	 * @param path
	 *            the VDisk path to the file.
	 * @param rev
	 *            the revision (from the file's metadata) of the file to
	 *            download, or null to get the latest version.
	 * @return
	 * @throws VDiskException
	 */
	public VDiskFileInfo getVDiskFileInfo(String path, String rev)
			throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String url = "/files/" + session.getAccessType() + path;

		String[] args = new String[] { "rev", rev, };

		RequestAndResponse r = RESTUtility.streamRequest(RequestMethod.GET,
				session.getAPIServer(), url, VERSION, args, session);

		int statusCode = r.response.getStatusLine().getStatusCode();
		if (statusCode == VDiskServerException._302_FOUND) {
			String location = VDiskServerException.getHeader(r.response,
					"location");
			Entry metadata = VDiskFileInfo.parseXVDiskMetadata(r.response);
			return new VDiskFileInfo(r.response, metadata, location);
		}
		return null;
	}

	/**
	 * Get the range for resuming download
	 * 
	 * @param targetFile
	 * @return
	 */
	private static long getRange(File targetFile) {

		long range = 0;

		if (targetFile != null && targetFile.exists()) {
			range = targetFile.length();
		}

		return range;
	}

	/**
	 * Uploads a file to VDisk. The upload will not overwrite any existing
	 * version of the file, unless the latest version on the VDisk server has
	 * the same rev as the parentRev given. Pass in null if you're expecting
	 * this to create a new file. Note: use {@code putFileRequest()} if you want
	 * to be able to cancel the upload.
	 * 
	 * @param path
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param is
	 *            the {@link InputStream} from which to upload.
	 * @param length
	 *            the amount of bytes to read from the {@link InputStream}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive upload
	 *            progress updates, or null.
	 * 
	 * @return a metadata {@link Entry} representing the uploaded file.
	 * 
	 * @throws IllegalArgumentException
	 *             if the file does not exist.
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskFileSizeException
	 *             if the file is bigger than the maximum allowed by the API.
	 *             See {@code VDiskAPI.MAX_UPLOAD_SIZE}.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path to upload not found), 507 (user over
	 *             quota), and 400 (unexpected parent rev).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry putFile(String path, InputStream is, long length,
			String parentRev, ProgressListener listener) throws VDiskException {
		UploadRequest request = putFileRequest(path, is, length, parentRev,
				listener);
		return request.upload();
	}

	/**
	 * Creates a request to upload a file to VDisk, which you can then
	 * {@code upload()} or {@code abort()}. The upload will not overwrite any
	 * existing version of the file, unless the latest version has the same rev
	 * as the parentRev given. Pass in null if you're expecting this to create a
	 * new file.
	 * 
	 * @param path
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param is
	 *            the {@link InputStream} from which to upload.
	 * @param length
	 *            the amount of bytes to read from the {@link InputStream}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive upload
	 *            progress updates, or null.
	 * 
	 * @return an {@link UploadRequest}.
	 * 
	 * @throws IllegalArgumentException
	 *             if the file does not exist.
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskFileSizeException
	 *             if the file is bigger than the maximum allowed by the API.
	 *             See {@code VDiskAPI.MAX_UPLOAD_SIZE}.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public UploadRequest putFileRequest(String path, InputStream is,
			long length, String parentRev, ProgressListener listener)
			throws VDiskException {
		return putFileRequest(path, is, length, false, parentRev, listener);
	}

	/**
	 * Uploads a file to VDisk. The upload will overwrite any existing version
	 * of the file. Use {@code putFileRequest()} if you want to be able to
	 * cancel the upload. If you expect the user to be able to edit a file
	 * remotely and locally, then conflicts may arise and you won't want to use
	 * this call: see {@code putFileRequest} instead.
	 * 
	 * @param path
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param is
	 *            the {@link InputStream} from which to upload.
	 * @param length
	 *            the amount of bytes to read from the {@link InputStream}.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive upload
	 *            progress updates, or null.
	 * 
	 * @return a metadata {@link Entry} representing the uploaded file.
	 * 
	 * @throws IllegalArgumentException
	 *             if the file does not exist.
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskFileSizeException
	 *             if the file is bigger than the maximum allowed by the API.
	 *             See {@code VDiskAPI.MAX_UPLOAD_SIZE}.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path to upload not found), 507 (user over
	 *             quota), and 400 (unexpected parent rev).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry putFileOverwrite(String path, InputStream is, long length,
			ProgressListener listener) throws VDiskException {
		UploadRequest request = putFileOverwriteRequest(path, is, length,
				listener);
		return request.upload();
	}

	/**
	 * Creates a request to upload a file to VDisk, which you can then
	 * {@code upload()} or {@code abort()}. The upload will overwrite any
	 * existing version of the file. If you expect the user to be able to edit a
	 * file remotely and locally, then conflicts may arise and you won't want to
	 * use this call: see {@code putFileRequest} instead.
	 * 
	 * @param path
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param is
	 *            the {@link InputStream} from which to upload.
	 * @param length
	 *            the amount of bytes to read from the {@link InputStream}.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive upload
	 *            progress updates, or null.
	 * 
	 * @return an {@link UploadRequest}.
	 * 
	 * @throws IllegalArgumentException
	 *             if the file does not exist locally.
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskFileSizeException
	 *             if the file is bigger than the maximum allowed by the API.
	 *             See {@code VDiskAPI.MAX_UPLOAD_SIZE}.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public UploadRequest putFileOverwriteRequest(String path, InputStream is,
			long length, ProgressListener listener) throws VDiskException {
		return putFileRequest(path, is, length, true, null, listener);
	}

	/**
	 * Upload a large file to VDisk by cutting the file to several segments. The
	 * upload will overwrite any existing version of the file.If you expect the
	 * user to be able to edit a file remotely and locally, then conflicts may
	 * arise and you won't want to use this call: see
	 * {@code putLargeFileRequest} instead.
	 * 
	 * @param srcPath
	 *            the local path of the file.
	 * @param desPath
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param length
	 *            the length of the local file.
	 * @param handler
	 *            the handler to control the upload session.
	 * @throws VDiskException
	 */
	public void putLargeFileOverwriteRequest(String srcPath, String desPath,
			long length, ComplexUploadHandler handler) throws VDiskException {
		putLargeFileRequest(srcPath, desPath, length, -1, true, null, handler);
	}

	/**
	 * Upload a large file to VDisk by cutting the file to several segments.
	 * 
	 * @param srcPath
	 *            the local path of the file.
	 * @param desPath
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param length
	 *            the length of the local file.
	 * @param segmentLength
	 *            the length of a segment after you cutting the local file,
	 *            default length is 4Mb.
	 * @param overwrite
	 *            whether to overwrite the file if it already exists. If true,
	 *            any existing file will always be overwritten. If false, files
	 *            will be overwritten only if the {@code parentRev} matches the
	 *            current rev on the server or otherwise a conflicted copy of
	 *            the file will be created and you will get the new file's
	 *            metadata {@link Entry}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @param handler
	 *            the handler to control the upload session.
	 * @throws VDiskException
	 */
	public void putLargeFileRequest(String srcPath, String desPath,
			long length, long segmentLength, boolean overwrite,
			String parentRev, ComplexUploadHandler handler)
			throws VDiskException {
		assertAuthenticated();

		if (desPath == null || desPath.equals("")) {
			throw new IllegalArgumentException("path is null or empty.");
		}

		if (!desPath.startsWith("/")) {
			desPath = "/" + desPath;
		}

		if (segmentLength <= 0) {
			segmentLength = UPLOAD_DEFAULT_SECTION_SIZE;
		}

		// 查询数据库信息 // Read information in database
		VDiskUploadFileInfo fileInfo = handler.readUploadFileInfo(srcPath,
				desPath);

		if (fileInfo == null
				|| fileInfo.expireTime < System.currentTimeMillis()) {
			// 计算sha1 // Compute sha1
			handler.assertCanceled();
			handler.startedWithStatus(ComplexUploadStatus.ComplexUploadStatusCreateFileSHA1);
			String sha1 = Digest.sha1Digest(srcPath);
			Log.d(TAG, "sha1-->" + sha1);

			// 批量签名 // Batch signature
			handler.assertCanceled();
			handler.startedWithStatus(ComplexUploadStatus.ComplexUploadStatusInitialize);
			fileInfo = startComplexUploadMultipartInit(srcPath, desPath,
					segmentLength, length, sha1, getS3Host(handler), overwrite,
					parentRev);

			if (fileInfo.isBlitzUpload) {
				// 秒传成功，直接返回 //Second transmission succeed and directly return
				handler.finishedWithMetadata(fileInfo.metadata);
				return;
			}

			// 计算md5s // Compute md5s
			handler.assertCanceled();
			handler.startedWithStatus(ComplexUploadStatus.ComplexUploadStatusCreateFileMD5s);
			fileInfo.md5s = handler.makeMD5s(srcPath, length, segmentLength);
			Log.d(TAG, "md5s-->" + fileInfo.md5s);
		}

		// 开始上传 // Start to upload
		handler.assertCanceled();
		startComplexUpload(fileInfo, length, handler, overwrite, parentRev);
	}

	/**
	 * Start uploading all segments to server.
	 * 
	 * @param fileInfo
	 *            the file info of the uploading file.
	 * @param length
	 *            the length of the local file.
	 * @param handler
	 *            the handler to control the upload session.
	 * @param overwrite
	 *            whether to overwrite the file if it already exists. If true,
	 *            any existing file will always be overwritten. If false, files
	 *            will be overwritten only if the {@code parentRev} matches the
	 *            current rev on the server or otherwise a conflicted copy of
	 *            the file will be created and you will get the new file's
	 *            metadata {@link Entry}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @return
	 * @throws VDiskException
	 */
	private Entry startComplexUpload(VDiskUploadFileInfo fileInfo, long length,
			ComplexUploadHandler handler, boolean overwrite, String parentRev)
			throws VDiskException {
		int startPoint = fileInfo.point;

		Log.d(TAG, "startPoint-->" + startPoint + ";segmentNum-->"
				+ fileInfo.segmentNum);
		// 开始续传 // Start to continuously upload
		for (int i = startPoint; i < fileInfo.segmentNum; i++) {
			handler.startedWithStatus(ComplexUploadStatus.ComplexUploadStatusUploading);

			String url = "http://" + fileInfo.s3Host
					+ fileInfo.partSigns.get(i + 1);

			Log.i(TAG, "large upload url-->" + url);

			HttpPut req = new HttpPut(url);

			long segmentLength = fileInfo.segmentLength;
			long startPos = i * segmentLength;
			long readLength = 0;
			if (i == fileInfo.segmentNum - 1) {
				readLength = length % segmentLength;
			} else {
				readLength = segmentLength;
			}

			ComplexUploadInputStreamEntity isEntity;
			try {
				isEntity = new ComplexUploadInputStreamEntity(fileInfo.srcPath,
						startPos, readLength);
				Log.i(TAG, "startPos-->" + startPos + ";readLength-->"
						+ readLength);
			} catch (IOException e) {
				throw new VDiskIOException(e);
			}
			isEntity.setContentType("application/octet-stream");
			isEntity.setChunked(false);

			HttpEntity entity = isEntity;

			if (handler != null) {
				entity = new ProgressHttpEntity(entity, handler);
			}

			req.setEntity(entity);

			ComplexUploadRequest uploadRequest = new ComplexUploadRequest(req,
					session);

			handler.setUploadRequest(uploadRequest);

			uploadRequest.upload();

			fileInfo.point++;
			handler.updateUploadFileInfo(fileInfo);
		}

		Entry entry = null;
		try {
			entry = startComplexUploadMerge(fileInfo, overwrite, parentRev);
		} catch (VDiskServerException e) {
			handler.deleteUploadFileInfo(fileInfo);
			throw new VDiskServerException(e);
		}

		handler.deleteUploadFileInfo(fileInfo);
		handler.finishedWithMetadata(entry);

		return entry;
	}

	/**
	 * After uploading all segments to server, we should request the server to
	 * merge the segments.
	 * 
	 * @param fileInfo
	 *            the file info of the uploading file.
	 * @param overwrite
	 *            whether to overwrite the file if it already exists. If true,
	 *            any existing file will always be overwritten. If false, files
	 *            will be overwritten only if the {@code parentRev} matches the
	 *            current rev on the server or otherwise a conflicted copy of
	 *            the file will be created and you will get the new file's
	 *            metadata {@link Entry}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @return
	 * @throws VDiskException
	 */
	private Entry startComplexUploadMerge(VDiskUploadFileInfo fileInfo,
			boolean overwrite, String parentRev) throws VDiskException {
		assertAuthenticated();

		String target = "multipart/complete";

		String[] params = new String[] { "root",
				session.getAccessType().toString(), "path", fileInfo.desPath,
				"s3host", fileInfo.s3Host, "upload_id", fileInfo.uploadId,
				"upload_key", fileInfo.uploadKey, "sha1", fileInfo.sha1,
				"md5_list", fileInfo.md5s, "overwrite",
				String.valueOf(overwrite), "parent_rev", parentRev };

		@SuppressWarnings("unchecked")
		Map<String, Object> entry = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(), target, VERSION,
				params, session, UPLOAD_MERGE_TIMEOUT_MS);

		return new Entry(entry);
	}

	/**
	 * Get signature of each segment of the upload file.
	 * 
	 * @param srcPath
	 *            the local path of the file.
	 * @param desPath
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param length
	 *            the length of the local file.
	 * @param segmentLength
	 *            the length of a segment after you cutting the local file,
	 *            default length is 4Mb.
	 * @param sha1
	 *            the sha1 of the upload file.
	 * @param overwrite
	 *            whether to overwrite the file if it already exists. If true,
	 *            any existing file will always be overwritten. If false, files
	 *            will be overwritten only if the {@code parentRev} matches the
	 *            current rev on the server or otherwise a conflicted copy of
	 *            the file will be created and you will get the new file's
	 *            metadata {@link Entry}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @return
	 * @throws VDiskException
	 */
	public VDiskUploadFileInfo startComplexUploadMultipartInit(String srcPath,
			String desPath, long segmentLength, long length, String sha1,
			String s3Host, boolean overwrite, String parentRev)
			throws VDiskException {
		assertAuthenticated();

		int segmentNum = 1;
		if ((length % segmentLength) > 0) {
			segmentNum = (int) (length / segmentLength + 1);
		} else {
			segmentNum = (int) (length / segmentLength);
		}

		String target = "/multipart/init";

		String[] params = new String[] { "root",
				session.getAccessType().toString(), "path", desPath, "s3host",
				s3Host, "part_total", String.valueOf(segmentNum), "size",
				String.valueOf(length), "sha1", sha1, "overwrite",
				String.valueOf(overwrite), "parent_rev", parentRev };

		Log.d(TAG, "init size-->" + length);
		Log.d(TAG, "part_total-->" + String.valueOf(segmentNum));

		@SuppressWarnings("unchecked")
		Map<String, Object> setcionInfo = (Map<String, Object>) RESTUtility
				.request(RequestMethod.POST, session.getAPIServer(), target,
						VERSION, params, session);

		VDiskUploadFileInfo fileInfo = new VDiskUploadFileInfo(setcionInfo,
				segmentNum, s3Host, segmentLength, sha1, srcPath, desPath);

		return fileInfo;
	}

	/**
	 * Get the nearest server for uploading file. S3 means Sina Storage Service.
	 * VDisk put all upload files to S3.
	 * 
	 * @throws VDiskException
	 */
	public String getS3Host(ComplexUploadHandler handler) throws VDiskException {
		if (SINA_STORAGE_SERVICE_HOST != null) {
			return SINA_STORAGE_SERVICE_HOST;
		}

		handler.assertCanceled();
		handler.startedWithStatus(ComplexUploadStatus.ComplexUploadStatusLocateHost);
		HttpResponse resp = RESTUtility.streamRequestAndResponse(
				RequestMethod.GET, session,
				"http://up.sinastorage.com/?extra&op=domain.json", null).response;
		HttpEntity entity = resp.getEntity();

		try {
			String server = EntityUtils.toString(entity);
			if (!TextUtils.isEmpty(server)) {
				SINA_STORAGE_SERVICE_HOST = server.trim().replace("\"", "");
			}
			Log.d(TAG, "s3 server-->" + SINA_STORAGE_SERVICE_HOST);
			return SINA_STORAGE_SERVICE_HOST;
		} catch (Exception e) {
			throw new VDiskIOException("Get S3 server failed.");
		}
	}

	/**
	 * Downloads a thumbnail from VDisk, copying it to the output stream.
	 * Returns the {@link VDiskFileInfo} for the downloaded thumbnail.
	 * 
	 * @param path
	 *            the VDisk path to the file for which you want to get a
	 *            thumbnail.
	 * @param os
	 *            the {@link OutputStream} to write the thumbnail to.
	 * @param size
	 *            the size of the thumbnail to download.
	 * @param format
	 *            the image format of the thumbnail to download.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive progress
	 *            updates as the thumbnail downloads, or null.
	 * 
	 * @return the {@link VDiskFileInfo} for the downloaded thumbnail.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path not found or can't be thumbnailed), 415
	 *             (this type of file can't be thumbnailed), and 500 (internal
	 *             error while creating thumbnail).
	 * @throws VDiskPartialFileException
	 *             if a network error occurs during the download.
	 * @throws VDiskIOException
	 *             for some network-related errors.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public VDiskFileInfo getThumbnail(String path, OutputStream os,
			ThumbSize size, ProgressListener listener) throws VDiskException {
		VDiskInputStream thumb = getThumbnailStream(path, size);

		if (thumb != null) {
			thumb.copyStreamToOutput(os, null, listener, true);
			return thumb.getFileInfo();
		} else {
			return null;
		}
	}

	/**
	 * Downloads a thumbnail from VDisk. Returns a {@link VDiskInputStream} via
	 * which the thumbnail can be read from the network. You must close the
	 * stream when you're done with it to release all resources.
	 * 
	 * You can also cancel the thumbnail download by closing the returned
	 * {@link VDiskInputStream} at any time.
	 * 
	 * @param path
	 *            the VDisk path to the file for which you want to get a
	 *            thumbnail.
	 * @param size
	 *            the size of the thumbnail to download.
	 * @param format
	 *            the image format of the thumbnail to download.
	 * 
	 * @return a {@link VDiskInputStream} from which to read the thumbnail. The
	 *         contents are retrieved from the network and not stored locally.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 404 (path not found or can't be thumbnailed), 415
	 *             (this type of file can't be thumbnailed), and 500 (internal
	 *             error while creating thumbnail)
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public VDiskInputStream getThumbnailStream(String path, ThumbSize size)
			throws VDiskException {
		VDiskFileInfo fileInfo = getThumbnailInfo(path, size);

		if (fileInfo != null) {
			String location = fileInfo.downloadURL;
			Log.d(TAG, "thumbnail location:" + location);
			Entry metadata = fileInfo.metadata;
			RequestAndResponse rp = RESTUtility.streamRequestAndResponse(
					RequestMethod.GET, session, location, null);
			return new VDiskInputStream(rp.request, rp.response, metadata,
					location);
		}

		return null;
	}

	/**
	 * Get the thumnail info.
	 * 
	 * @param path
	 *            the VDisk path to the file for which you want to get a
	 *            thumbnail.
	 * @param size
	 *            the size of the thumbnail to download.
	 * @return
	 * @throws VDiskException
	 */
	public VDiskFileInfo getThumbnailInfo(String path, ThumbSize size)
			throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String target = "/thumbnails/" + session.getAccessType() + path;
		String[] params = { "size", size.toAPISize() };
		RequestAndResponse r = RESTUtility.streamRequest(RequestMethod.GET,
				session.getAPIServer(), target, VERSION, params, session);

		StatusLine statusLine = r.response.getStatusLine();
		int statusCode = statusLine.getStatusCode();

		if (statusCode == VDiskServerException._302_FOUND) {
			String location = VDiskServerException.getHeader(r.response,
					"location");
			Entry metadata = VDiskFileInfo.parseXVDiskMetadata(r.response);
			return new VDiskFileInfo(r.response, metadata, location);
		}

		return null;
	}

	/**
	 * Returns the metadata for a file, or for a directory and (optionally) its
	 * immediate children.
	 * 
	 * @param path
	 *            the VDisk path to the file or directory for which to get
	 *            metadata.
	 * 
	 * @param hash
	 *            if you previously got metadata for a directory and have it
	 *            stored, pass in the returned hash. If the directory has not
	 *            changed since you got the hash, a 304
	 *            {@link VDiskServerException} will be thrown. Pass in null for
	 *            files or unknown directories.
	 * @param list
	 *            if true, returns metadata for a directory's immediate
	 *            children, or just the directory entry itself if false. Ignored
	 *            for files.
	 * @param includeDeleted
	 *            optionally gets metadata for a file at a prior rev (does not
	 *            apply to folders). Use false for the latest metadata.
	 * 
	 * @return a metadata {@link Entry}.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 304 (contents haven't changed based on the hash),
	 *             404 (path not found or unknown rev for path), and 406 (too
	 *             many entries to return).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry metadata(String path, String hash, boolean list,
			boolean includeDeleted) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String[] params = { "hash", hash, "list", String.valueOf(list),
				"include_deleted", String.valueOf(includeDeleted) };

		String url_path = "/metadata/" + session.getAccessType() + path;

		@SuppressWarnings("unchecked")
		Map<String, Object> dirinfo = (Map<String, Object>) RESTUtility
				.request(RequestMethod.GET, session.getAPIServer(), url_path,
						VERSION, params, session);

		return new Entry(dirinfo);
	}

	/**
	 * Returns a list of metadata for all revs of the path.
	 * 
	 * @param path
	 *            the VDisk path to the file for which to get revisions
	 *            (directories are not supported).
	 * @param revLimit
	 *            the maximum number of revisions to return. Default is 10 if
	 *            you pass in 0 or less, and 1,000 is the most that will ever be
	 *            returned.
	 * 
	 * @return a list of metadata entries.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error code you can expect from this
	 *             call is 404 (no revisions found for path).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	@SuppressWarnings("unchecked")
	public List<Entry> revisions(String path, int revLimit)
			throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		if (revLimit <= 0) {
			revLimit = REVISION_DEFAULT_LIMIT;
		}

		String[] params = { "rev_limit", String.valueOf(revLimit) };

		String url_path = "/revisions/" + session.getAccessType() + path;

		JSONArray revs = (JSONArray) RESTUtility.request(RequestMethod.GET,
				session.getAPIServer(), url_path, VERSION, params, session);

		List<Entry> entries = new LinkedList<Entry>();
		for (Object metadata : revs) {
			entries.add(new Entry((Map<String, Object>) metadata));
		}

		return entries;
	}

	/**
	 * Searches a directory for entries matching the query.
	 * 
	 * @param path
	 *            the VDisk directory to search in.
	 * @param query
	 *            the query to search for (minimum 3 characters).
	 * @param fileLimit
	 *            the maximum number of file entries to return. Default is 1,000
	 *            if you pass in 0 or less, and 1,000 is the most that will ever
	 *            be returned.
	 * @param includeDeleted
	 *            whether to include deleted files in search results.
	 * 
	 * @return a list of metadata entries of matching files.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code.
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public List<Entry> search(String path, String query, int fileLimit,
			boolean includeDeleted) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		if (fileLimit <= 0) {
			fileLimit = SEARCH_DEFAULT_LIMIT;
		}

		String target = "/search/" + session.getAccessType() + path;

		String[] params = { "query", query, "file_limit",
				String.valueOf(fileLimit), "include_deleted",
				String.valueOf(includeDeleted) };

		Object response = RESTUtility.request(RequestMethod.GET,
				session.getAPIServer(), target, VERSION, params, session);

		ArrayList<Entry> ret = new ArrayList<Entry>();
		if (response instanceof JSONArray) {
			JSONArray jresp = (JSONArray) response;
			for (Object next : jresp) {
				if (next instanceof Map) {
					@SuppressWarnings("unchecked")
					Entry ent = new Entry((Map<String, Object>) next);
					ret.add(ent);
				}
			}
		}

		return ret;
	}

	/**
	 * Moves a file or folder (and all of the folder's contents) from one path
	 * to another.
	 * 
	 * @param fromPath
	 *            the VDisk path to move from.
	 * @param toPath
	 *            the full VDisk path to move to (not just a directory).
	 * 
	 * @return a metadata {@link Entry}.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 403 (operation is forbidden), 404 (path not found),
	 *             and 507 (user over quota).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry move(String fromPath, String toPath) throws VDiskException {
		assertAuthenticated();

		if (!fromPath.startsWith("/")) {
			fromPath = "/" + fromPath;
		}

		if (!toPath.startsWith("/")) {
			toPath = "/" + toPath;
		}

		String[] params = { "root", session.getAccessType().toString(),
				"from_path", fromPath, "to_path", toPath };

		@SuppressWarnings("unchecked")
		Map<String, Object> resp = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(), "/fileops/move",
				VERSION, params, session);

		return new Entry(resp);
	}

	/**
	 * Copies a file or folder (and all of the folder's contents) from one path
	 * to another.
	 * 
	 * @param fromPath
	 *            the VDisk path to copy from.
	 * @param toPath
	 *            the full VDisk path to copy to (not just a directory).
	 * 
	 * @return a metadata {@link Entry}.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 403 (operation is forbidden), 404 (path not found),
	 *             and 507 (user over quota).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry copy(String fromPath, String toPath) throws VDiskException {
		assertAuthenticated();

		if (!fromPath.startsWith("/")) {
			fromPath = "/" + fromPath;
		}

		if (!toPath.startsWith("/")) {
			toPath = "/" + toPath;
		}

		String[] params = { "root", session.getAccessType().toString(),
				"from_path", fromPath, "to_path", toPath };

		@SuppressWarnings("unchecked")
		Map<String, Object> resp = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(), "/fileops/copy",
				VERSION, params, session);

		return new Entry(resp);
	}

	/**
	 * Creates a new VDisk folder.
	 * 
	 * @param path
	 *            the VDisk path to the new folder.
	 * 
	 * @return a metadata {@link Entry} for the new folder.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error codes you can expect from this
	 *             call are 403 (something already exists at that path), 404
	 *             (path not found), and 507 (user over quota).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry createFolder(String path) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String[] params = { "root", session.getAccessType().toString(), "path",
				path };

		@SuppressWarnings("unchecked")
		Map<String, Object> resp = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(),
				"/fileops/create_folder", VERSION, params, session);

		return new Entry(resp);
	}

	/**
	 * Deletes a file or folder (and all of the folder's contents). After
	 * deletion, metadata calls may still return this file or folder for some
	 * time, but the metadata {@link Entry}'s {@code isDeleted} attribute will
	 * be set to {@code true}.
	 * 
	 * @param path
	 *            the VDisk path to delete.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error code you can expect from this
	 *             call is 404 (path not found).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry delete(String path) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String[] params = { "root", session.getAccessType().toString(), "path",
				path };

		@SuppressWarnings("unchecked")
		Map<String, Object> dirinfo = (Map<String, Object>) RESTUtility
				.request(RequestMethod.POST, session.getAPIServer(),
						"/fileops/delete", VERSION, params, session);

		return new Entry(dirinfo);
	}

	/**
	 * Restores a file to a previous rev.
	 * 
	 * @param path
	 *            the VDisk path to the file to restore.
	 * @param rev
	 *            the rev to restore to (obtained from a metadata or revisions
	 *            call).
	 * 
	 * @return a metadata {@link Entry} for the newly restored file.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error code you can expect from this
	 *             call is 404 (path not found or unknown revision).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry restore(String path, String rev) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String[] params = { "rev", rev };

		String target = "/restore/" + session.getAccessType() + path;

		@SuppressWarnings("unchecked")
		Map<String, Object> metadata = (Map<String, Object>) RESTUtility
				.request(RequestMethod.POST, session.getAPIServer(), target,
						VERSION, params, session);

		return new Entry(metadata);
	}

	/**
	 * Returns a {@link VDiskLink} for a stream of the given file path.
	 * 
	 * @param path
	 *            the VDisk path of the file for which to get a streaming link.
	 * @param ssl
	 *            whether the streaming URL is https or http. Some Android and
	 *            other platforms won't play https streams, so false converts
	 *            the link to an http link before returning it.
	 * 
	 * @return a {@link VDiskLink} for streaming the file.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error code you can expect from this
	 *             call is 404 (path not found).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public VDiskLink media(String path, boolean ssl) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String target = "/media/" + session.getAccessType() + path;

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) RESTUtility.request(
				RequestMethod.GET, session.getAPIServer(), target, VERSION,
				null, session);

		return new VDiskLink(map, ssl);
	}

	/**
	 * Get the download url of by a file's copy reference.
	 * 
	 * @param sourceCopyRef
	 *            file's copy Reference
	 * @return
	 * @throws VDiskException
	 */
	public VDiskLink getLinkByCopyRef(String sourceCopyRef)
			throws VDiskException {

		assertAuthenticated();

		String[] params = { "from_copy_ref", sourceCopyRef };

		String target = "/shareops/media";

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) RESTUtility.request(
				RequestMethod.GET, session.getAPIServer(), target, VERSION,
				params, session);

		return new VDiskLink(map, true);
	}

	/**
	 * Generates a Link for sharing the specified file.
	 * 
	 * Notice: the file'size need to be less than 200MB;
	 * 
	 * the file should be in the following types:
	 * 
	 * 'application/epub+zip', 'application/kswps', 'application/msword',
	 * 'application/octet-stream', 'application/pdf', 'application/postscript',
	 * 'application/vnd.android.package-archive',
	 * 'application/vnd.ms-cab-compressed', 'application/vnd.ms-excel',
	 * 'application/vnd.ms-powerpoint',
	 * 'application/vnd.openxmlformats-officedocument.presentationml.presentatio
	 * n ' ,
	 * 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
	 * 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
	 * , 'application/vnd.rn-realmedia', 'application/vnd.rn-realmedia-vbr',
	 * 'application/vnd.symbian.install', 'application/x-7z-compressed',
	 * 'application/x-bzip2', 'application/x-chm', 'application/x-itunes-ipa',
	 * 'application/x-rar-compressed', 'application/x-shockwave-flash',
	 * 'application/x-gzip', 'application/x-zip', 'audio/mp3', 'audio/x-aac',
	 * 'audio/x-flac', 'audio/x-monkeys-audio', 'audio/x-ms-wma', 'image/bmp',
	 * 'image/gif', 'image/jpeg', 'image/pjpeg', 'image/png',
	 * 'image/vnd.adobe.photoshop', 'text/plain', 'video/3gpp', 'video/avi',
	 * 'video/mp4', 'video/quicktime', 'video/mpeg', 'video/x-flv',
	 * 'video/x-matroska', 'video/x-ms-wmv', 'x-epoc/x-sisx-app'
	 * 
	 * @param path
	 *            the VDisk file path to share.
	 * 
	 * @return a Share Link for the path.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskServerException
	 *             if the server responds with an error code. See the constants
	 *             in {@link VDiskServerException} for the meaning of each error
	 *             code. The most common error code you can expect from this
	 *             call is 404 (path not found).
	 * @throws VDiskIOException
	 *             if any network-related error occurs.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public String share(String path) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String target = "/shares/" + session.getAccessType() + path;

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(), target, VERSION,
				null, session);

		String url = (String) map.get("url");

		if (url == null) {
			throw new VDiskParseException("Could not parse share response.");
		}

		return url;
	}

	/**
	 * Cancel the share of the specified file.
	 * 
	 * @param path
	 *            the VDisk path to cancel share.
	 * @return the metaData of the file which is share canceled.
	 * @throws VDiskException
	 */
	public Entry cancelShare(String path) throws VDiskException {
		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String target = "/shares/" + session.getAccessType() + path;

		String[] params = new String[] { "cancel", "true" };
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) RESTUtility.request(
				RequestMethod.POST, session.getAPIServer(), target, VERSION,
				params, session);

		return new Entry(map);
	}

	/**
	 * Helper function to read boolean JSON return values
	 * 
	 * @param map
	 *            the one to read from
	 * @param name
	 *            the parameter name to read
	 * @return the value, with false as a default if no parameter set
	 */
	protected static boolean getFromMapAsBoolean(Map<String, Object> map,
			String name) {
		Object val = map.get(name);
		if (val != null && val instanceof Boolean) {
			return ((Boolean) val).booleanValue();
		} else {
			return false;
		}
	}

	/**
	 * Creates a request to upload an {@link InputStream} to a VDisk file. You
	 * can then {@code upload()} or {@code abort()} this request. This is the
	 * advanced version, which you should only use if you really need the
	 * flexibility of uploading using an {@link InputStream}.
	 * 
	 * @param path
	 *            the full VDisk path where to put the file, including
	 *            directories and filename.
	 * @param is
	 *            the {@link InputStream} from which to upload.
	 * @param length
	 *            the amount of bytes to read from the {@link InputStream}.
	 * @param overwrite
	 *            whether to overwrite the file if it already exists. If true,
	 *            any existing file will always be overwritten. If false, files
	 *            will be overwritten only if the {@code parentRev} matches the
	 *            current rev on the server or otherwise a conflicted copy of
	 *            the file will be created and you will get the new file's
	 *            metadata {@link Entry}.
	 * @param parentRev
	 *            the rev of the file at which the user started editing it
	 *            (obtained from a metadata call), or null if this is a new
	 *            upload. If null, or if it does not match the latest rev on the
	 *            server, a copy of the file will be created and you'll receive
	 *            the new metadata upon executing the request.
	 * @param listener
	 *            an optional {@link ProgressListener} to receive upload
	 *            progress updates, or null.
	 * 
	 * @return an {@link UploadRequest}.
	 * 
	 * @throws IllegalArgumentException
	 *             if {@code newFilename} is null or empty.
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskFileSizeException
	 *             if the file is bigger than the maximum allowed by the API.
	 *             See {@code VDiskAPI.MAX_UPLOAD_SIZE}.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	private UploadRequest putFileRequest(String path, InputStream is,
			long length, boolean overwrite, String parentRev,
			ProgressListener listener) throws VDiskException {
		if (path == null || path.equals("")) {
			throw new IllegalArgumentException("path is null or empty.");
		}

		assertAuthenticated();

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		String target = "/files_put/" + session.getAccessType() + path;

		String[] params = new String[] { "overwrite",
				String.valueOf(overwrite), "parent_rev", parentRev };

		String url = RESTUtility.buildURL(session.getUploadServer(), VERSION,
				target, params);

		Log.i(TAG, "upload url-->" + url);

		HttpPut req = new HttpPut(url);
		session.sign(req);

		req.setHeader("Host", session.getUploadServer());

		if (Logger.DEBUG_MODE) {
			String curlHeader = "";
			if (session.getWeiboAccessToken() != null) {
				// 使用微博Token进行认证
				// Use Weibo token for authentication
				String token = Signature.getWeiboHeader(
						session.getAppKeyPair(),
						(WeiboAccessToken) session.getWeiboAccessToken());
				token = token.replace("\"", "\\\"");
				curlHeader = "Authorization:Weibo " + token;
			} else if (session.getAccessToken() != null) {
				// 使用微盘Token进行认证
				// Use VDisk token for authentication
				curlHeader = "Authorization:OAuth2 "
						+ session.getAccessToken().mAccessToken;
			}

			Log.i(TAG, "curl -H \"" + curlHeader + "\" " + "-H \"Host:"
					+ session.getUploadServer() + "\" " + "\"" + url
					+ "\" -k -v");
			Logger.writeHeader(session.getContext());
			Logger.writeToFile("curl -H \"" + curlHeader + "\" " + "-H \"Host:"
					+ session.getUploadServer() + "\" " + "\"" + url
					+ "\" -k -v");
		}

		SimpleUploadInputStreamEntity isEntity = new SimpleUploadInputStreamEntity(
				is, length);
		isEntity.setContentEncoding("application/octet-stream");
		isEntity.setChunked(false);

		HttpEntity entity = isEntity;

		if (listener != null) {
			entity = new ProgressHttpEntity(entity, listener);
		}

		req.setEntity(entity);

		return new BasicUploadRequest(req, session);
	}

	/**
	 * A way of letting you keep up with changes to files and folders in a
	 * user's VDisk. You can periodically call this function to get a list of
	 * "delta entries", which are instructions on how to update your local state
	 * to match the server's state.
	 * 
	 * @param cursor
	 *            On the first call, you should pass in <code>null</code>. On
	 *            subsequent calls, pass in the {@link DeltaPage#cursor cursor}
	 *            returned by the previous call.
	 * 
	 * @return A single {@link DeltaPage DeltaPage} of results. The
	 *         {@link DeltaPage#hasMore hasMore} field will tell you whether the
	 *         server has more pages of results to return. If the server doesn't
	 *         have more results, you can wait a bit (say, 5 or 10 minutes) and
	 *         poll again.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public DeltaPage<Entry> delta(String cursor) throws VDiskException {
		String[] params = new String[] { "cursor", cursor };
		String path = "/delta/" + session.getAccessType();

		Object json = RESTUtility.request(RequestMethod.POST,
				session.getAPIServer(), path, VERSION, params, session);
		try {
			return DeltaPage.extractFromJson(new JsonThing(json),
					Entry.JsonExtractor);
		} catch (JsonExtractionException ex) {
			throw new VDiskParseException("Error parsing /delta results: "
					+ ex.getMessage());
		}
	}

	/**
	 * A page of {@link DeltaEntry DeltaEntry}s (returned by {@link #delta
	 * delta}).
	 */
	public static final class DeltaPage<MD> {
		/**
		 * If <code>true</code>, then you should reset your local state to be an
		 * empty folder before processing the list of delta entries. This is
		 * only <code>true</code> in rare situations.
		 */
		public final boolean reset;

		/**
		 * A string that is used to keep track of your current state. On the
		 * next call to {@link #delta delta}, pass in this value to pick up
		 * where you left off.
		 */
		public final String cursor;

		/**
		 * Apply these entries to your local state to catch up with the VDisk
		 * server's state.
		 */
		public final List<DeltaEntry<MD>> entries;

		/**
		 * If <code>true</code>, then there are more entries available; you can
		 * call {@link #delta delta} again immediately to retrieve those
		 * entries. If <code>false</code>, then wait at least 5 minutes
		 * (preferably longer) before checking again.
		 */
		public final boolean hasMore;

		public DeltaPage(boolean reset, List<DeltaEntry<MD>> entries,
				String cursor, boolean hasMore) {
			this.reset = reset;
			this.entries = entries;
			this.cursor = cursor;
			this.hasMore = hasMore;
		}

		public static <MD> DeltaPage<MD> extractFromJson(JsonThing j,
				JsonExtractor<MD> entryExtractor)
				throws JsonExtractionException {
			JsonMap m = j.expectMap();
			boolean reset = m.get("reset").expectBoolean();
			String cursor = m.get("cursor").expectString();
			boolean hasMore = m.get("has_more").expectBoolean();
			List<DeltaEntry<MD>> entries = m.get("entries").expectList()
					.extract(new DeltaEntry.JsonExtractor<MD>(entryExtractor));

			return new DeltaPage<MD>(reset, entries, cursor, hasMore);
		}

	}

	/**
	 * A single entry in a {@link DeltaPage DeltaPage}.
	 */
	public static final class DeltaEntry<MD> {
		/**
		 * The lower-cased path of the entry. VDisk compares file paths in a
		 * case-insensitive manner. For example, an entry for
		 * <code>"/readme.txt"</code> should overwrite the entry for
		 * <code>"/ReadMe.TXT"</code>.
		 * 
		 * <p>
		 * To get the original case-preserved path, look in the
		 * {@link #metadata metadata} field.
		 * </p>
		 */
		public final String lcPath;

		/**
		 * If this is <code>null</code>, it means that this path doesn't exist
		 * on on VDisk's copy of the file system. To update your local state to
		 * match, delete whatever is at that path, including any children. If
		 * your local state doesn't have anything at this path, ignore this
		 * entry.
		 * 
		 * <p>
		 * If this is not <code>null</code>, it means that VDisk has a
		 * file/folder at this path with the given metadata. To update your
		 * local state to match, add the entry to your local state as well.
		 * </p>
		 * <ul>
		 * <li>
		 * If the path refers to parent folders that don't exist yet in your
		 * local state, create those parent folders in your local state.</li>
		 * <li>
		 * If the metadata is for a file, replace whatever your local state has
		 * at that path with the new entry.</li>
		 * <li>
		 * If the metadata is for a folder, check what your local state has at
		 * the path. If it's a file, replace it with the new entry. If it's a
		 * folder, apply the new metadata to the folder, but do not modify the
		 * folder's children.</li>
		 * </ul>
		 */
		public final MD metadata;

		public DeltaEntry(String lcPath, MD metadata) {
			this.lcPath = lcPath;
			this.metadata = metadata;
		}

		public static final class JsonExtractor<MD> extends
				com.vdisk.net.jsonextract.JsonExtractor<DeltaEntry<MD>> {
			public final com.vdisk.net.jsonextract.JsonExtractor<MD> mdExtractor;

			public JsonExtractor(
					com.vdisk.net.jsonextract.JsonExtractor<MD> mdExtractor) {
				this.mdExtractor = mdExtractor;
			}

			public DeltaEntry<MD> extract(JsonThing j)
					throws JsonExtractionException {
				return extract(j, this.mdExtractor);
			}

			public static <MD> DeltaEntry<MD> extract(JsonThing j,
					com.vdisk.net.jsonextract.JsonExtractor<MD> mdExtractor)
					throws JsonExtractionException {
				JsonList l = j.expectList();
				String path = l.get(0).expectString();
				MD metadata = l.get(1).optionalExtract(mdExtractor);
				return new DeltaEntry<MD>(path, metadata);
			}
		}

	}

	/**
	 * Creates a reference to a path that can be used with
	 * {@link #addFromCopyRef addFromCopyRef()} to copy the contents of the file
	 * at that path to a different VDisk account. This is more efficient than
	 * copying the content
	 * 
	 * @param sourcePath
	 *            The full path to the file that you want a
	 * 
	 * @return A string representation of the file pointer.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public CreatedCopyRef createCopyRef(String sourcePath)
			throws VDiskException {
		assertAuthenticated();

		if (!sourcePath.startsWith("/")) {
			sourcePath = "/" + sourcePath;
		}

		String url_path = "/copy_ref/" + session.getAccessType() + sourcePath;

		Object result = RESTUtility.request(RequestMethod.POST,
				session.getAPIServer(), url_path, VERSION, null, session);

		try {
			return CreatedCopyRef.extractFromJson(new JsonThing(result));
		} catch (JsonExtractionException ex) {
			throw new VDiskParseException("Error parsing /copy_ref results: "
					+ ex.getMessage());
		}
	}

	public static final class CreatedCopyRef {
		public final String copyRef;
		public final String expiration;

		public CreatedCopyRef(String copyRef, String expiration) {
			this.copyRef = copyRef;
			this.expiration = expiration;
		}

		public static CreatedCopyRef extractFromJson(JsonThing j)
				throws JsonExtractionException {
			JsonMap m = j.expectMap();
			String string = m.get("copy_ref").expectString();
			// String expiration = m.get("expires").expectString();
			return new CreatedCopyRef(string, null);
		}
	}

	/**
	 * Creates a file in the VDisk that the client is currently connected to,
	 * using the contents from a
	 * {@link com.VDisk.client2.VDiskAPI.CreatedCopyRef CopyRef} created with
	 * {@link #createCopyRef createCopyRef()}. The {@link CreatedCopyRef
	 * CreatedCopyRef} can be for a file in a different VDisk account.
	 * 
	 * @param sourceCopyRef
	 *            The copy-ref to use as the source of the file data (comes from
	 *            {@link CreatedCopyRef#copyRef CreatedCopyRef.copyRef}, which
	 *            is created through {@link #createCopyRef createCopyRef()}).
	 * @param targetPath
	 *            The path that you want to create the file at.
	 * 
	 * @return The {@link Entry} for the new file.
	 * 
	 * @throws VDiskUnlinkedException
	 *             if you have not set an access token pair on the session, or
	 *             if the user has revoked access.
	 * @throws VDiskException
	 *             for any other unknown errors. This is also a superclass of
	 *             all other VDisk exceptions, so you may want to only catch
	 *             this exception which signals that some kind of error
	 *             occurred.
	 */
	public Entry addFromCopyRef(String sourceCopyRef, String targetPath)
			throws VDiskException {
		assertAuthenticated();

		if (!targetPath.startsWith("/")) {
			targetPath = "/" + targetPath;
		}

		String[] params = { "root", session.getAccessType().toString(),
				"from_copy_ref", sourceCopyRef, "to_path", targetPath };

		String url_path = "/fileops/copy";

		@SuppressWarnings("unchecked")
		Map<String, Object> dirinfo = (Map<String, Object>) RESTUtility
				.request(RequestMethod.POST, session.getAPIServer(), url_path,
						VERSION, params, session);

		return new Entry(dirinfo);
	}

	/**
	 * 
	 * 递归列出某目录下全部子目录。目前仅支持 root 为 basic。
	 * 
	 * Use recursion to list all sub-directories under the specified
	 * directory. Currently only supports root of basic.
	 * 
	 * @param targetPath
	 *            想要获取所有相册的目标路径 Target path of all albums you want to get
	 * @param cursor
	 *            表示已返回的列表在操作记录中的位置 The location of the returned list in
	 *            operation log
	 */
	public AlbumPage<Entry> getAlbums(String targetPath, String cursor)
			throws VDiskException {
		assertAuthenticated();

		if (!targetPath.startsWith("/")) {
			targetPath = "/" + targetPath;
		}

		String[] params = { "root", session.getAccessType().toString(), "path",
				targetPath, "cursor", cursor };

		Object json = RESTUtility.request(RequestMethod.GET,
				session.getAPIServer(), "/folder/recur", VERSION, params,
				session);

		try {
			return AlbumPage.extractFromJson(new JsonThing(json),
					Entry.JsonExtractor);
		} catch (JsonExtractionException ex) {
			throw new VDiskParseException("Error parsing /delta results: "
					+ ex.getMessage());
		}

	}

	public static final class AlbumPage<MD> {
		/**
		 * A string that is used to keep track of your current state. On the
		 * next call to {@link #delta delta}, pass in this value to pick up
		 * where you left off.
		 */
		public final String cursor;

		/**
		 * Apply these entries to your local state to catch up with the VDisk
		 * server's state.
		 */
		public final List<Entry> entries;

		/**
		 * If <code>true</code>, then there are more entries available; you can
		 * call {@link #delta delta} again immediately to retrieve those
		 * entries. If <code>false</code>, then wait at least 5 minutes
		 * (preferably longer) before checking again.
		 */
		public final boolean hasMore;

		public AlbumPage(List<Entry> entries, String cursor, boolean hasMore) {
			this.entries = entries;
			this.cursor = cursor;
			this.hasMore = hasMore;
		}

		public static <MD> AlbumPage<MD> extractFromJson(JsonThing j,
				JsonExtractor<MD> entryExtractor)
				throws JsonExtractionException {
			JsonMap m = j.expectMap();
			String cursor = m.get("cursor").expectString();
			boolean hasMore = m.get("has_more").expectBoolean();
			List<Entry> entries = m.get("data").expectList()
					.extract(Entry.JsonExtractor);

			return new AlbumPage<MD>(entries, cursor, hasMore);
		}
	}

	/**
	 * 
	 * 带筛选的目录列表，按文件类型列出目录下的全部文件。目前仅支持 root 为 basic。
	 * 
	 * A directory list with filter, listing all files under the specified
	 * directory by file type.Currently only supports root of basic.
	 * 
	 * @param targetPath
	 *            要获取列表的目录路径 Directory path of the list you want to get
	 * @param size
	 *            缩略图尺寸 Size of thumbnail
	 * @return
	 * @throws VDiskException
	 */
	@SuppressWarnings("unchecked")
	public List<Entry> getPhotos(String targetPath, ThumbSize size)
			throws VDiskException {
		assertAuthenticated();

		String[] params = { "root", session.getAccessType().toString(), "path",
				targetPath, "file_type", "img", "dimension", size.toAPISize() };

		JSONArray photos = (JSONArray) RESTUtility.request(RequestMethod.GET,
				session.getAPIServer(), "/folder/filter", VERSION, params,
				session);

		List<Entry> entries = new LinkedList<Entry>();
		for (Object metadata : photos) {
			entries.add(new Entry((Map<String, Object>) metadata));
		}
		return entries;
	}

	/**
	 * Throws a {@link VDiskUnlinkedException} if the session in this instance
	 * is not linked.
	 */
	protected void assertAuthenticated() throws VDiskUnlinkedException {
		if (!session.isLinked()) {
			throw new VDiskUnlinkedException();
		}
	}

	/**
	 * Helper function to read long JSON return values
	 * 
	 * @param map
	 *            the one to read from
	 * @param name
	 *            the parameter name to read
	 * @return the value, with 0 as a default if no parameter set
	 */
	protected static long getFromMapAsLong(Map<String, Object> map, String name) {
		Object val = map.get(name);
		long ret = 0;
		if (val != null) {
			if (val instanceof Number) {
				ret = ((Number) val).longValue();
			} else if (val instanceof String) {
				// To parse cases where JSON can't represent a Long, so
				// it's stored as a string
				ret = Long.parseLong((String) val);
			}
		}
		return ret;
	}

	/**
	 * Helper function to read string JSON return values
	 * 
	 * @param map
	 *            the one to read from
	 * @param name
	 *            the parameter name to read
	 * @return the value, with false as a default if no parameter set
	 */
	protected static String getFromMapAsString(Map<String, Object> map,
			String name) {
		Object val = map.get(name);
		String ret = null;
		if (val != null) {
			if (val instanceof Number) {
				ret = String.valueOf(((Number) val).longValue());
			} else if (val instanceof String) {
				ret = (String) val;
			}
		}
		return ret;
	}
}
