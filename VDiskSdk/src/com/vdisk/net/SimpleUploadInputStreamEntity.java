package com.vdisk.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.entity.AbstractHttpEntity;

public class SimpleUploadInputStreamEntity extends AbstractHttpEntity {

	private final static int BUFFER_SIZE = 8192;

	private final InputStream content;
	private final long length;
	private boolean consumed = false;

	public SimpleUploadInputStreamEntity(final InputStream instream, long length) {
		super();
		if (instream == null) {
			throw new IllegalArgumentException(
					"Source input stream may not be null");
		}
		this.content = instream;
		this.length = length;
	}

	public boolean isRepeatable() {
		return false;
	}

	public long getContentLength() {
		return this.length;
	}

	public InputStream getContent() throws IOException {
		return this.content;
	}

	public void writeTo(final OutputStream outstream) throws IOException {
		if (outstream == null) {
			throw new IllegalArgumentException("Output stream may not be null");
		}
		InputStream instream = this.content;
		byte[] buffer = new byte[BUFFER_SIZE];
		int l;
		if (this.length < 0) {
			// consume until EOF
			while ((l = instream.read(buffer)) != -1) {
				judgeTransfer(outstream, buffer, l);
			}
		} else {
			// consume no more than length
			long remaining = this.length;
			while (remaining > 0) {
				l = instream.read(buffer, 0,
						(int) Math.min(BUFFER_SIZE, remaining));
				if (l == -1) {
					break;
				}
				judgeTransfer(outstream, buffer, l);
				remaining -= l;
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
		// If the input stream is from a connection, closing it will read to
		// the end of the content. Otherwise, we don't care what it does.
		this.content.close();
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
