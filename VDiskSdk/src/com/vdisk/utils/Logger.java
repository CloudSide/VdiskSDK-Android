package com.vdisk.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.text.TextUtils;

public class Logger {

	/**
	 * If DEBUG_MODE == true, every api request information will be wrote to
	 * local file (sdcard/vdisk/api.log), else, we won't write to the local
	 * file.
	 */
	public static boolean DEBUG_MODE = false;

	public static String logPath = Environment
			.getExternalStorageDirectory().getAbsolutePath() + "/vdisk";

	public static synchronized void writeToFile(String message) {
		if (TextUtils.isEmpty(message)) {
			return;
		}

		File dirFile = new File(logPath);

		if (!dirFile.exists()) {
			dirFile.mkdirs();
		}

		File file = new File(logPath + "/api.log");

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file, true);
			fos.write(message.getBytes());
			fos.write("\n".getBytes());
			fos.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void writeHeader(Context context) {
		Logger.writeToFile("\n\n");
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"[dd/MMM/yyyy:HH:mm:ss ZZZ]", Locale.US);
		Logger.writeToFile(dateFormat.format(new Date()));

		switch (getNetworkState(context)) {
		case MOBILE:
			Logger.writeToFile("network state: 3G");
			break;
		case WIFI:
			Logger.writeToFile("network state: wifi");
			break;
		case NOTHING:
			Logger.writeToFile("network state: no");
			break;
		}
	}

	public static void writeException(Throwable e) {
		if (e != null) {
			Logger.writeToFile(e.getClass().getName() + ":" + e.getMessage());
		}
	}

	public static void writeException(String msg, Throwable e) {
		if (e != null) {
			Logger.writeToFile(msg + " " + e.getClass().getName() + ":"
					+ e.getMessage());
		}
	}

	public enum NetworkState {
		NOTHING, MOBILE, WIFI
	}

	public static NetworkState getNetworkState(Context ctx) {
		ConnectivityManager cm = (ConnectivityManager) ctx
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = cm.getActiveNetworkInfo();
		if (info == null || !info.isAvailable()) {
			return NetworkState.NOTHING;
		} else {
			if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
				return NetworkState.MOBILE;
			} else {
				return NetworkState.WIFI;
			}
		}
	}

}
