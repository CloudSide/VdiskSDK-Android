package com.vdisk.android.example;

import java.io.File;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.vdisk.android.ComplexUploadHandler;
import com.vdisk.net.VDiskAPI;
import com.vdisk.net.VDiskAPI.Entry;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskFileNotFoundException;
import com.vdisk.net.exception.VDiskFileSizeException;
import com.vdisk.net.exception.VDiskIOException;
import com.vdisk.net.exception.VDiskParseException;
import com.vdisk.net.exception.VDiskPartialFileException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.exception.VDiskUnlinkedException;

/**
 * Here we show uploading a large file in a background thread, trying to show
 * typical exception handling and flow of control for an app that uploads a file
 * from VDisk.
 */
public class LargeFileUpload extends AsyncTask<Void, Long, Boolean> {

	private static final String TAG = "LargeFileUpload";
	private VDiskAPI<?> mApi;
	private String mPath;
	private File mFile;

	private long mFileLen;
	private Context mContext;
	private final ProgressDialog mDialog;

	private String mErrorMsg;
	private String mSrcPath;
	private ComplexUploadHandler handler;

	public LargeFileUpload(Context context, VDiskAPI<?> api, String VDiskPath,
			File file) {
		// We set the context this way so we don't accidentally leak activities
		mContext = context.getApplicationContext();

		mSrcPath = file.getAbsolutePath();

		mFileLen = file.length();
		mApi = api;
		mPath = VDiskPath;
		mFile = file;

		mDialog = new ProgressDialog(context);
		mDialog.setMax(100);
		mDialog.setMessage(mContext.getString(R.string.be_uploading) + " "
				+ file.getName());
		mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mDialog.setProgress(0);
		mDialog.setButton(mContext.getString(R.string.be_cancel),
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// This will cancel the putFile operation
						handler.abort();
					}
				});
		mDialog.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				handler.abort();
			}
		});
		mDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			if (!mPath.endsWith("/")) {
				mPath = mPath + "/";
			}

			String desPath = mPath + mFile.getName();

			handler = new ComplexUploadHandler(mContext) {
				@Override
				public void onProgress(long bytes, long total) {
					publishProgress(bytes);
				}

				@Override
				public void startedWithStatus(ComplexUploadStatus status) {
					switch (status) {
					case ComplexUploadStatusLocateHost:
						Log.d(TAG, "Getting the nearest host...");
						break;
					case ComplexUploadStatusCreateFileSHA1:
						Log.d(TAG, "Creating the sha1 of file");
						break;
					case ComplexUploadStatusInitialize:
						Log.d(TAG, "Signing each segment of file...");
						break;
					case ComplexUploadStatusCreateFileMD5s:
						Log.d(TAG, "Creating each segment's md5...");
						break;
					case ComplexUploadStatusUploading:
						Log.d(TAG, "Uploading one segment...");
						break;
					case ComplexUploadStatusMerging:
						Log.d(TAG, "File Merging...");
						break;
					default:
						break;
					}
				}

				@Override
				public void finishedWithMetadata(Entry metadata) {
					Log.d(TAG, "Upload success : " + metadata.fileName());
				}
			};

			mApi.putLargeFileOverwriteRequest(mSrcPath, desPath,
					mFile.length(), handler);

			return true;

		} catch (VDiskUnlinkedException e) {
			// This session wasn't authenticated properly or user unlinked
			mErrorMsg = "This app wasn't authenticated properly.";
		} catch (VDiskFileSizeException e) {
			// File size too big to upload via the API
			mErrorMsg = "This file is too big to upload";
		} catch (VDiskPartialFileException e) {
			// We canceled the operation
			mErrorMsg = "Upload canceled";
		} catch (VDiskServerException e) {
			// Server-side exception. These are examples of what could happen,
			// but we don't do anything special with them here.
			if (e.error == VDiskServerException._401_UNAUTHORIZED) {
				// Unauthorized, so we should unlink them. You may want to
				// automatically log the user out in this case.
			} else if (e.error == VDiskServerException._403_FORBIDDEN) {
				// Not allowed to access this
			} else if (e.error == VDiskServerException._404_NOT_FOUND) {
				// path not found (or if it was the thumbnail, can't be
				// thumbnailed)
			} else if (e.error == VDiskServerException._507_INSUFFICIENT_STORAGE) {
				// user is over quota
			} else {
				// Something else
			}
			// This gets the VDisk error, translated into the user's language
			mErrorMsg = e.body.userError;
			if (mErrorMsg == null) {
				mErrorMsg = e.body.error;
			}
			e.printStackTrace();
		} catch (VDiskIOException e) {
			// Happens all the time, probably want to retry automatically.
			e.printStackTrace();
			mErrorMsg = "Network error.  Try again.";
		} catch (VDiskParseException e) {
			// Probably due to VDisk server restarting, should retry
			mErrorMsg = "VDisk error.  Try again.";
			e.printStackTrace();
		} catch (VDiskFileNotFoundException e) {
			mErrorMsg = "File not found.";
			e.printStackTrace();
		} catch (VDiskException e) {
			// Unknown error
			mErrorMsg = "Unknown error.  Try again.";
			e.printStackTrace();
		}

		return false;
	}

	@Override
	protected void onProgressUpdate(Long... progress) {
		int percent = (int) (100.0 * (double) progress[0] / mFileLen + 0.5);
		mDialog.setProgress(percent);
		Log.d(TAG, "upload progress-->" + progress[0] + "/" + mFileLen);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		mDialog.dismiss();
		if (result) {
			showToast(mContext.getString(R.string.suc_up_file));
		} else {
			showToast(mErrorMsg);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
		error.show();
	}
}
