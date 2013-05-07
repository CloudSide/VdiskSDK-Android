package com.vdisk.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import android.content.Context;
import android.util.Log;

import com.vdisk.net.ProgressListener;
import com.vdisk.net.VDiskAPI.Entry;
import com.vdisk.net.VDiskAPI.UploadRequest;
import com.vdisk.net.VDiskAPI.VDiskUploadFileInfo;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskPartialFileException;
import com.vdisk.utils.Digest;

/**
 * 此类在整个上传回话中起到了控制协调的作用。通过此类，可以实时得到当前大文件上传的状态，可以实时得到当前文件的上传进度， 可以中断上传行为。
 * 通过此类，可以读取，更新，删除本地数据库的分段信息
 * 
 * This class plays a coordinating control role in the whole upload session.
 * This class is used to get current status and progress in real time when
 * upload a big file, or stop the uploading. This class is used to read, update
 * and delete segment information in local database.
 * 
 * @author Kevin
 * 
 */
public abstract class ComplexUploadHandler extends ProgressListener {

	private static final String TAG = "ComplexUploadHandler";

	private UploadRequest mRequest;

	private Context mContext;

	private boolean isCanceled;

	public enum ComplexUploadStatus {

		/**
		 * 正在获取离当地最近的Sina Storage Service的域名信息
		 * 
		 * Be getting the the nearest domain information of Sina Storage
		 * Service.
		 */
		ComplexUploadStatusLocateHost,

		/**
		 * 正在计算文件sha1
		 * 
		 * Be computing sha1 of the file.
		 */
		ComplexUploadStatusCreateFileSHA1,

		/**
		 * 正在获取批量签名信息以及每一个文件分段的服务器地址
		 * 
		 * Be getting batch signature information and the server address of each
		 * file section.
		 */
		ComplexUploadStatusInitialize,

		/**
		 * 正在创建每一个文件分段的md5
		 * 
		 * Be computing md5 of each file section.
		 */
		ComplexUploadStatusCreateFileMD5s,

		/**
		 * 正在上传某一段文件
		 * 
		 * Be uploading a section of the file.
		 */
		ComplexUploadStatusUploading,

		/**
		 * 所有分段文件上传完毕，正在访问服务器端的合并文件的接口
		 * 
		 * All the sections of the file have been uploaded. Be accessing the
		 * merge file API on the server.
		 */
		ComplexUploadStatusMerging;
	}

	public ComplexUploadHandler(Context ctx) {
		this.mContext = ctx;
	}

	/**
	 * 此回调方法可以得到当前文件的上传进度
	 * 
	 * Callback to get upload progress of current file.
	 * 
	 * @param bytes
	 *            整个上传会话中，已经上传的总字节数 Total number of bytes uploaded in the whole
	 *            upload session
	 * 
	 * @param total
	 *            当前分段的总长度 Total size of current segment
	 */
	@Override
	public void onProgress(long bytes, long total) {

	}

	/**
	 * 回调方法，实时更新当前上传状态
	 * 
	 * Callback to update current upload status in real time.
	 * 
	 * @param status
	 */
	public abstract void startedWithStatus(ComplexUploadStatus status);

	/**
	 * 回调方法，表示文件已经上传结束。 普通上传结束和秒传成功都会回调此方法
	 * 
	 * Callback to show the file has been uploaded over. The success of common
	 * or blitz upload will callback this method.
	 * 
	 * @param metadata
	 *            上传成功后的文件信息 uploaded file information
	 */
	public abstract void finishedWithMetadata(Entry metadata);

	/**
	 * 从数据库中读取文件分段信息。 通过重写此方法，可以自定义本地文件分段信息的存储方式。
	 * 
	 * Read file segment information in database. Override this method to
	 * user-define a storage mode for segment information.
	 * 
	 * @param srcPath
	 *            本地文件路径 Source path of local file
	 * @param desPath
	 *            微盘目标路径 Target path of file in VDisk
	 * 
	 * @return
	 * @throws VDiskException
	 */
	public VDiskUploadFileInfo readUploadFileInfo(String srcPath, String desPath)
			throws VDiskException {
		String fileId = Digest.md5String(srcPath + desPath);
		VDiskDB db = VDiskDB.getInstance(mContext);
		String serStr = db.readUploadFileInfo(fileId);

		if (serStr != null) {
			VDiskUploadFileInfo fileInfo = (VDiskUploadFileInfo) deserialize(serStr);
			Log.d(TAG, "readUploadFileInfo-->" + fileInfo.point);
			return fileInfo;
		}

		return null;
	}

	/**
	 * 更新数据库中的文件分段信息。 通过重写此方法，可以自定义本地文件分段信息的存储方式。
	 * 
	 * Update file segment information in database. Override this method to
	 * user-define a storage mode for segment information.
	 * 
	 * @throws VDiskException
	 */
	public void updateUploadFileInfo(VDiskUploadFileInfo fileInfo)
			throws VDiskException {
		String serStr = serialize(fileInfo);
		VDiskDB db = VDiskDB.getInstance(mContext);
		db.updateUploadFileInfo(fileInfo.id, serStr);
	}

	/**
	 * 删除数据库中的文件分段信息。 通过重写此方法，可以自定义本地文件分段信息的存储方式。
	 * 
	 * Delete file segment information in database. Override this method to
	 * user-define a storage mode for segment information.
	 * 
	 * @param fileInfo
	 */
	public void deleteUploadFileInfo(VDiskUploadFileInfo fileInfo) {
		VDiskDB db = VDiskDB.getInstance(mContext);
		db.deleteUploadFileInfo(fileInfo.id);
	}

	/**
	 * 计算每一个分段文件的md5，拼接成以逗号分隔的字符串。
	 * 
	 * Compute md5 of each segment file and append them to a string separated by
	 * ",".
	 * 
	 * @param srcPath
	 *            本地文件路径 Path of local file
	 * @param fileSize
	 *            本地文件长度 Size of local file
	 * @param segmentLength
	 *            每段文件的长度 Length of each file segment
	 * @return
	 * @throws VDiskException
	 */
	public String makeMD5s(String srcPath, long fileSize, long segmentLength)
			throws VDiskException {
		ArrayList<String> md5s = Digest.getFileMD5s(srcPath, fileSize,
				segmentLength);

		StringBuilder sb = new StringBuilder();
		int size = md5s.size();
		for (int i = 0; i < size; i++) {
			sb.append(md5s.get(i) + ",");
		}

		String md5sString = sb.toString();
		return md5sString.substring(0, md5sString.length() - 1);
	}

	/**
	 * 中断上传行为
	 * 
	 * Interrupt the uploading.
	 */
	public void abort() {
		isCanceled = true;

		if (mRequest != null) {
			// 4.0系统网络操作需放到子线程执行，避免发生NetworkOnMainThreadException异常。
			// In Android4.0, network operations need to be executed in a child
			// thread to avoid NetworkOnMainThreadException.
			new Thread() {
				@Override
				public void run() {
					mRequest.abort();
				}
			}.start();
		}
	}

	public void setUploadRequest(UploadRequest request) {
		this.mRequest = request;
	}

	public boolean isCanceled() {
		return isCanceled;
	}

	public void assertCanceled() throws VDiskPartialFileException {
		if (isCanceled) {
			throw new VDiskPartialFileException(-1);
		}
	}

	/**
	 * 序列化对象，生成字符串
	 * 
	 * Serialize an object and build a string
	 * 
	 * @param o
	 *            the object to be serialized.
	 * @return
	 * @throws VDiskException
	 */
	private String serialize(Object o) throws VDiskException {
		String serStr;
		ObjectOutputStream objectOutputStream = null;
		ByteArrayOutputStream byteArrayOutputStream = null;
		try {
			byteArrayOutputStream = new ByteArrayOutputStream();
			objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
			objectOutputStream.writeObject(o);
			serStr = byteArrayOutputStream.toString("ISO-8859-1");
			serStr = java.net.URLEncoder.encode(serStr, "UTF-8");

			return serStr;
		} catch (Exception e) {
			throw new VDiskException(e);
		} finally {
			if (objectOutputStream != null) {
				try {
					objectOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (byteArrayOutputStream != null) {
				try {
					byteArrayOutputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * 通过字符串反序列化成为对象
	 * 
	 * Generate an object by deserialize the string
	 * 
	 * @param serStr
	 *            the serialized string of an object.
	 * @return
	 * @throws VDiskException
	 */
	private Object deserialize(String serStr) throws VDiskException {
		ByteArrayInputStream byteArrayInputStream = null;
		ObjectInputStream objectInputStream = null;
		try {
			String redStr = java.net.URLDecoder.decode(serStr, "UTF-8");
			byteArrayInputStream = new ByteArrayInputStream(
					redStr.getBytes("ISO-8859-1"));
			objectInputStream = new ObjectInputStream(byteArrayInputStream);
			Object obj = objectInputStream.readObject();

			return obj;
		} catch (Exception e) {
			throw new VDiskException(e);
		} finally {
			if (objectInputStream != null) {
				try {
					objectInputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (byteArrayInputStream != null) {
				try {
					byteArrayInputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

}
