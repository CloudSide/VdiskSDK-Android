package com.vdisk.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskFileNotFoundException;

/**
 * @author sina
 */
public class Digest {

	private static final char HEX_DIGITS[] = { '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static final int BUFFER_SIZE = 128 * 1024;

	/**
	 * @param b
	 * @return
	 */
	public static String toHexString(byte[] b) {
		StringBuilder sb = new StringBuilder(b.length * 2);
		for (int i = 0; i < b.length; i++) {
			sb.append(HEX_DIGITS[(b[i] & 0xf0) >>> 4]);
			sb.append(HEX_DIGITS[b[i] & 0x0f]);
		}
		return sb.toString();
	}

	/**
	 * Get the md5 of a string.
	 * @param str
	 * @return
	 * @throws VDiskException
	 */
	public static String md5String(String str) throws VDiskException {
		byte[] hash;
		try {
			hash = MessageDigest.getInstance("MD5").digest(str.getBytes());
			return Digest.toHexString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new VDiskException(e);
		}
	}

	/**
	 * Get the sha1 of a file.
	 * @param filePath
	 * @return
	 * @throws VDiskException
	 */
	public static String sha1Digest(String filePath) throws VDiskException {
		InputStream fis = null;
		byte[] buffer = new byte[BUFFER_SIZE];
		int numRead = 0;
		MessageDigest sha1;
		try {
			fis = new FileInputStream(filePath);
			sha1 = MessageDigest.getInstance("SHA-1");

			while ((numRead = fis.read(buffer)) > 0) {
				sha1.update(buffer, 0, numRead);
			}
			return toHexString(sha1.digest());
		} catch (FileNotFoundException e) {
			throw new VDiskFileNotFoundException(e);
		} catch (Exception e) {
			throw new VDiskException(e);
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Get a segment's md5 of the upload file.
	 * @param filePath
	 * @param i
	 * @param BlockSize
	 * @return
	 * @throws VDiskException
	 */
	public static String getMD5(RandomAccessFile randomAccessFile, long offset,
			long length) throws VDiskException {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[BUFFER_SIZE];// 设置buffer大小 //Set the size of buffer
			int read;// 实际读取的字节数 // The number of bytes actually read
			randomAccessFile.seek(offset);
			long count = 0;
			while ((read = randomAccessFile.read(buffer, 0, buffer.length)) > 0) {
				count += read;
				messageDigest.update(buffer, 0, read);
				// 到达该块末尾 //Reach the end of the buffer
				if (count == length) {
					break;
				}
			}

			return toHexString(messageDigest.digest());
		} catch (Exception e) {
			throw new VDiskException(e);
		}
	}

	/**
	 * Get each segment's md5, and return a list.
	 * @param filepath
	 * @param fileSize
	 * @return
	 * @throws VDiskFileNotFoundException
	 */
	public static ArrayList<String> getFileMD5s(String filepath,
			long fileSize, long segmentLength)
			throws VDiskException {
		ArrayList<String> md5s = new ArrayList<String>();

		int sum;

		if ((fileSize % segmentLength) > 0) {
			sum = (int) (fileSize / segmentLength + 1);
		} else {
			sum = (int) (fileSize / segmentLength);
		}

		long blockSize = 0;

		try {
			RandomAccessFile randomAccessFile = new RandomAccessFile(filepath,
					"rw");

			for (int i = 0; i < sum; i++) {
				long offset = i * segmentLength;
				String md5;
				if (i == sum - 1) {
					blockSize = fileSize % segmentLength;
					md5 = getMD5(randomAccessFile, offset, blockSize);
				} else {
					md5 = getMD5(randomAccessFile, offset, segmentLength);
				}
				md5s.add(md5);
			}
			return md5s;
		} catch (FileNotFoundException e) {
			throw new VDiskFileNotFoundException(e);
		}
	}
}
