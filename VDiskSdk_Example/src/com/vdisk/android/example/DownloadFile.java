package com.vdisk.android.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.vdisk.net.ProgressListener;
import com.vdisk.net.VDiskAPI;
import com.vdisk.net.VDiskAPI.VDiskFileInfo;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskIOException;
import com.vdisk.net.exception.VDiskParseException;
import com.vdisk.net.exception.VDiskPartialFileException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.exception.VDiskUnlinkedException;
import com.vdisk.net.exception.VDiskDownloadFileExistException;

/**
 * Here we show getting metadata for a directory and downloading a file in a
 * background thread, trying to show typical exception handling and flow of
 * control for an app that downloads a file from VDisk.
 */

public class DownloadFile extends AsyncTask<Void, Long, Boolean> {

	private Context mContext;
	private final ProgressDialog mDialog;
	private VDiskAPI<?> mApi;
	private String mPath;
	private String mTargetPath;

	private FileOutputStream mFos;

	private boolean mCanceled;
	private long mFileLen;
	private String mErrorMsg;

	private VDiskFileInfo info;
	private File file;
	
	// Note that, since we use a single file name here for simplicity, you
	// won't be able to use this code for two simultaneous downloads.

	public DownloadFile(Context context, VDiskAPI<?> api, String filePath, String targetPath) {
		// We set the context this way so we don't accidentally leak activities
		mContext = context.getApplicationContext();

		mApi = api;
		mPath = filePath;
		mTargetPath = targetPath;

		mDialog = new ProgressDialog(context);
		mDialog.setMax(100);
		mDialog.setMessage(mContext.getString(R.string.be_downloading)+" " + filePath);
		mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mDialog.setProgress(0);
		mDialog.setButton(mContext.getString(R.string.be_cancel), new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				mCanceled = true;
				mErrorMsg = "Canceled";

				// This will cancel the getFile operation by closing
				// its stream
				if (mFos != null) {
					try {
						mFos.close();
					} catch (IOException e) {
					}
				}
			}
		});

		mDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			if (mCanceled) {
				return false;
			}

			file = mApi.createDownloadDirFile(mTargetPath);
			
			try {
				mFos = new FileOutputStream(file, true);
			} catch (FileNotFoundException e) {
				mErrorMsg = "Couldn't create a local file to store the file";
				return false;
			}

			info = mApi.getFile(mPath, null, mFos, file,
					new ProgressListener() {

						@Override
						public long progressInterval() {
							return super.progressInterval();
						}

						@Override
						public void onProgress(long bytes, long total) {
							mFileLen = total;
							publishProgress(bytes);
						}
					});

			if (mCanceled) {
				return false;
			}

			if (info != null) {
				return true;
			}

			return false;

		} catch (VDiskUnlinkedException e) {
			// The AuthSession wasn't properly authenticated or user unlinked.
			mErrorMsg = "Unlinked";
		} catch (VDiskPartialFileException e) {
			// We canceled the operation
			mErrorMsg = "Download canceled";
		} catch (VDiskServerException e) {
			// Server-side exception. These are examples of what could happen,
			// but we don't do anything special with them here.
			if (e.error == VDiskServerException._304_NOT_MODIFIED) {
				// won't happen since we don't pass in revision with metadata
			} else if (e.error == VDiskServerException._401_UNAUTHORIZED) {
				// Unauthorized, so we should unlink them. You may want to
				// automatically log the user out in this case.
			} else if (e.error == VDiskServerException._403_FORBIDDEN) {
				// Not allowed to access this
			} else if (e.error == VDiskServerException._404_NOT_FOUND) {
				// path not found (or if it was the thumbnail, can't be
				// thumbnailed)
			} else if (e.error == VDiskServerException._406_NOT_ACCEPTABLE) {
				// too many entries to return
			} else if (e.error == VDiskServerException._415_UNSUPPORTED_MEDIA) {
				// can't be thumbnailed
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
		} catch (VDiskIOException e) {
			// Happens all the time, probably want to retry automatically.
			mErrorMsg = "Network error.  Try again.";
		} catch (VDiskParseException e) {
			// Probably due to VDisk server restarting, should retry
			mErrorMsg = "VDisk error.  Try again.";
		} catch (VDiskDownloadFileExistException e) {
			mErrorMsg = "Download file already exists in your target path.";
		} catch (VDiskException e) {
			// Unknown error
			mErrorMsg = "Unknown error.  Try again.";
		}
		return false;
	}

	@Override
	protected void onProgressUpdate(Long... progress) {
		Log.d("Test",  progress[0] + "/" + mFileLen);
		int percent = (int) (100.0 * (double) progress[0] / mFileLen + 0.5);
		mDialog.setProgress(percent);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		mDialog.dismiss();
		if (result) {
			showToast(mContext.getString(R.string.down_url)+":" + info.getDownloadURL() + "\n"
					+ "metadata:" + info.getMetadata().fileName());
		} else {
			// Couldn't download it, so show an error
			showToast(mErrorMsg);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
		error.show();
	}
}
