package com.vdisk.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.entity.AbstractHttpEntity;

import android.util.Log;

/**
 * 对上传文件流的封装。实现了对大文件的分段控制。
 * 
 * Encapsulation of upload input stream. To do the segmented control of big
 * files.
 * 
 * @author Kevin
 * 
 */
public class ComplexUploadInputStreamEntity extends AbstractHttpEntity {

	private final static int BUFFER_SIZE = 8192;

	private long length;
	private long startPos;
	private boolean consumed = false;
	RandomAccessFile randomAccessFile = null;

	public ComplexUploadInputStreamEntity(String srcPath, long startPos,
			long length) throws IOException {
		super();

		this.length = length;
		this.startPos = startPos;
		randomAccessFile = new RandomAccessFile(srcPath, "rw");
		randomAccessFile.seek(startPos);
	}

	public boolean isRepeatable() {
		return false;
	}

	public long getContentLength() {
		return this.length;
	}

	public long getStartPosition() {
		return startPos;
	}

	public InputStream getContent() throws IOException {
		return null;
	}

	public void writeTo(final OutputStream outstream) throws IOException {
		if (outstream == null) {
			throw new IllegalArgumentException("Output stream may not be null");
		}

		byte[] buffer = new byte[BUFFER_SIZE];
		int read;
		long count = 0;

		while (true) {
			read = randomAccessFile.read(buffer, 0, buffer.length);

			if (read < 0) {
				break;
			}

			count += read;

			judgeTransfer(outstream, buffer, read);

			/**
			 * 判断是否到达单片末尾 Judge whether reach the end of one section
			 */
			if (count == length) {
				Log.d("Test", "count-->" + count);
				outstream.flush();
				break;
			}
		}

		this.consumed = true;
	}

	// non-javadoc, see interface HttpEntity
	public boolean isStreaming() {
		return !this.consumed;
	}

	// non-javadoc, see interface HttpEntity
	public void consumeContent() throws IOException {
		this.consumed = true;
		randomAccessFile.close();
	}

	ExecutorService executor = Executors.newSingleThreadExecutor();

	public boolean judgeTransfer(final OutputStream bos, final byte[] buffer,
			final int len) throws IOException {

		Future<String> result = executor.submit(new Callable<String>() {

			@Override
			public String call() throws Exception {

				bos.write(buffer, 0, len);
				bos.flush();

				return "success";
			}
		});

		String str;
		try {
			str = result.get(VDiskAPI.UPLOAD_RESPONSE_TIMEOUT_S,
					TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			e.printStackTrace();
			throw new IOException("Long time no response.");
		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException();
		}

		if (str != null) {
			return true;
		}

		return false;
	}

}
