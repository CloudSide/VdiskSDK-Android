package com.vdisk.net.session;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.ProtocolVersion;
import org.apache.http.TokenIterator;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;
import android.util.Log;

import com.vdisk.net.VDiskAPI;
import com.vdisk.utils.Signature;

/**
 * Keeps track of a logged in user and contains configuration options for the
 * {@link VDiskAPI}. This is a base class to use for creating your own
 * {@link Session}s.
 */
public abstract class AbstractSession implements Session {

	private static final String API_SERVER = "api.weipan.cn";
	private static final String UPLOAD_SERVER = "upload-vdisk.sina.com.cn";
	private static final String UPLOAD_SERVER_HTTPS = "upload-vdisk.sina.com.cn:4443";
	private static final String CONTENT_SERVER = "api-content.weipan.cn";
	private static final String WEB_SERVER = "vdisk.weibo.com";

	// If true, use https to upload a file in the api of "/files_put/"
	public static boolean NEED_HTTPS_UPLOAD = false;

	private static final int DEFAULT_MAX_RETRIES = 3;

	protected static boolean useWeiboToken;

	/** How long connections are kept alive. */
	private static final int KEEP_ALIVE_DURATION_SECS = 20;

	/** How often the monitoring thread checks for connections to close. */
	private static final int KEEP_ALIVE_MONITOR_INTERVAL_SECS = 5;

	/** The default timeout for client connections. */
	private static final int DEFAULT_TIMEOUT_MILLIS = 30000; // 30 seconds

	private final AccessType accessType;
	private final AppKeyPair appKeyPair;
	protected AccessToken accessToken = null;
	protected WeiboAccessToken weiboAccessToken = null;

	protected static Context mContext;

	private HttpClient client = null;

	/**
	 * Creates a new session with the given app key and secret, and access type.
	 * The session will not be linked because it has no access token pair.
	 */
	public AbstractSession(AppKeyPair appKeyPair, AccessType type) {
		this(appKeyPair, type, null);
	}

	/**
	 * Creates a new session with the given app key and secret, and access type.
	 * The session will be linked to the account corresponding to the given
	 * access token pair.
	 */
	public AbstractSession(AppKeyPair appKeyPair, AccessType type,
			AccessToken accessTokenPair) {
		if (appKeyPair == null)
			throw new IllegalArgumentException("'appKeyPair' must be non-null");
		if (type == null)
			throw new IllegalArgumentException("'type' must be non-null");

		this.appKeyPair = appKeyPair;
		this.accessType = type;
		this.accessToken = accessTokenPair;
	}

	/**
	 * Links the session with the given access token and secret.
	 */
	public void setAccessTokenPair(AccessToken accessTokenPair) {
		if (accessTokenPair == null)
			throw new IllegalArgumentException("'accessToken' must be non-null");
		this.accessToken = accessTokenPair;
	}

	@Override
	public AppKeyPair getAppKeyPair() {
		return appKeyPair;
	}

	@Override
	public AccessToken getAccessToken() {
		return accessToken;
	}

	@Override
	public AccessToken getWeiboAccessToken() {
		return weiboAccessToken;
	}

	@Override
	public AccessType getAccessType() {
		return accessType;
	}

	@Override
	public boolean isLinked() {

		if (useWeiboToken) {
			if (weiboAccessToken != null
					&& !TextUtils.isEmpty(weiboAccessToken.mAccessToken)) {
				return true;
			}
			return false;
		} else {
			if (accessToken != null
					&& !TextUtils.isEmpty(accessToken.mAccessToken)) {
				return true;
			}
			return false;
		}
	}

	@Override
	public void unlink() {

		if (useWeiboToken) {
			weiboAccessToken = null;
		} else {
			accessToken = null;
		}
		useWeiboToken = false;
	}

	/**
	 * Signs the request by using's OAuth's HTTP header authorization scheme and
	 * the PLAINTEXT signature method. As such, this should only be used over
	 * secure connections (i.e. HTTPS). Using this over regular HTTP connections
	 * is completely insecure.
	 * 
	 * @see Session#sign
	 */
	@Override
	public void sign(HttpRequest request) {

		if (useWeiboToken) {
			// 使用微博Token进行认证
			// Use Weibo token for authentication
			request.setHeader(
					"Authorization",
					"Weibo "
							+ Signature.getWeiboHeader(this.appKeyPair,
									this.weiboAccessToken));

			Log.d("weibo_access_Token",
					"Weibo "
							+ Signature.getWeiboHeader(this.appKeyPair,
									this.weiboAccessToken));
		} else {
			// 使用微盘Token进行认证
			// Use VDisk token for authentication
			request.setHeader("Authorization", "OAuth2 "
					+ this.accessToken.mAccessToken);
			Log.d("access_Token", this.accessToken.mAccessToken);
		}
		request.setHeader("Host", API_SERVER);
	}

	/**
	 * {@inheritDoc} <br/>
	 * <br/>
	 * The default implementation always returns null.
	 */
	@Override
	public synchronized ProxyInfo getProxyInfo() {
		return null;
	}

	/**
	 * {@inheritDoc} <br/>
	 * <br/>
	 * The default implementation does all of this and more, including using a
	 * connection pool and killing connections after a timeout to use less
	 * battery power on mobile devices. It's unlikely that you'll want to change
	 * this behavior.
	 */
	@Override
	public synchronized HttpClient getHttpClient() {
		if (client == null) {
			// Set up default connection params. There are two routes to
			// VDisk - api server and content server.
			HttpParams connParams = new BasicHttpParams();
			ConnManagerParams.setMaxConnectionsPerRoute(connParams,
					new ConnPerRoute() {
						@Override
						public int getMaxForRoute(HttpRoute route) {
							return 10;
						}
					});
			ConnManagerParams.setMaxTotalConnections(connParams, 20);

			// Set up scheme registry.
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("http", PlainSocketFactory
					.getSocketFactory(), 80));
			try {
				schemeRegistry.register(new Scheme("https",
						TrustAllSSLSocketFactory.getDefault(), 443));
			} catch (Exception e) {
			}

			DBClientConnManager cm = new DBClientConnManager(connParams,
					schemeRegistry);

			// Set up client params.
			HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams,
					DEFAULT_TIMEOUT_MILLIS);
			HttpConnectionParams.setSoTimeout(httpParams,
					DEFAULT_TIMEOUT_MILLIS);
			HttpConnectionParams.setSocketBufferSize(httpParams, 8192);
			HttpProtocolParams.setUserAgent(httpParams, makeUserAgent());
			httpParams.setParameter(ClientPNames.HANDLE_REDIRECTS, false);

			DefaultHttpClient c = new DefaultHttpClient(cm, httpParams) {
				@Override
				protected ConnectionKeepAliveStrategy createConnectionKeepAliveStrategy() {
					return new DBKeepAliveStrategy();
				}

				@Override
				protected ConnectionReuseStrategy createConnectionReuseStrategy() {
					return new DBConnectionReuseStrategy();
				}
			};

			c.addRequestInterceptor(new HttpRequestInterceptor() {
				@Override
				public void process(final HttpRequest request,
						final HttpContext context) throws HttpException,
						IOException {
					if (!request.containsHeader("Accept-Encoding")) {
						request.addHeader("Accept-Encoding", "gzip");
					}
				}
			});

			c.addResponseInterceptor(new HttpResponseInterceptor() {
				@Override
				public void process(final HttpResponse response,
						final HttpContext context) throws HttpException,
						IOException {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						Header ceheader = entity.getContentEncoding();
						if (ceheader != null) {
							HeaderElement[] codecs = ceheader.getElements();
							for (HeaderElement codec : codecs) {
								if (codec.getName().equalsIgnoreCase("gzip")) {
									response.setEntity(new GzipDecompressingEntity(
											response.getEntity()));
									return;
								}
							}
						}
					}
				}
			});

			c.setHttpRequestRetryHandler(new RetryHandler(DEFAULT_MAX_RETRIES));

			client = c;
		}

		return client;
	}

	private String makeUserAgent() {

		String str = "OfficialVdiskAndroidSdk/" + VDiskAPI.SDK_VERSION;
		if (mContext != null) {
			PackageInfo info;
			try {
				info = mContext.getPackageManager().getPackageInfo(
						mContext.getPackageName(), 0);
				// 当前应用的版本名称
				// Version name of current application
				String versionName = info.versionName;
				// 当前版本的包名
				// Package name of current application
				String packageNames = info.packageName;
				str = packageNames + "/" + versionName + " " + str;
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}
		}
		return str;
	}

	public static class TrustAllSSLSocketFactory extends SSLSocketFactory {
		public javax.net.ssl.SSLSocketFactory factory;

		public TrustAllSSLSocketFactory() throws KeyManagementException,
				NoSuchAlgorithmException, KeyStoreException,
				UnrecoverableKeyException {
			super(null);
			try {
				SSLContext sslcontext = SSLContext.getInstance("TLS");
				sslcontext.init(null,
						new TrustManager[] { new TrustAllManager() }, null);
				factory = sslcontext.getSocketFactory();
				setHostnameVerifier(new AllowAllHostnameVerifier());
			} catch (Exception ex) {
			}
		}

		public static SocketFactory getDefault() throws KeyManagementException,
				NoSuchAlgorithmException, KeyStoreException,
				UnrecoverableKeyException {
			return new TrustAllSSLSocketFactory();
		}

		@Override
		public Socket createSocket() throws IOException {
			return factory.createSocket();
		}

		@Override
		public Socket createSocket(Socket socket, String s, int i, boolean flag)
				throws IOException {
			return factory.createSocket(socket, s, i, flag);
		}

		public Socket createSocket(InetAddress inaddr, int i,
				InetAddress inaddr1, int j) throws IOException {
			return factory.createSocket(inaddr, i, inaddr1, j);
		}

		public Socket createSocket(InetAddress inaddr, int i)
				throws IOException {
			return factory.createSocket(inaddr, i);
		}

		public Socket createSocket(String s, int i, InetAddress inaddr, int j)
				throws IOException {
			return factory.createSocket(s, i, inaddr, j);
		}

		public Socket createSocket(String s, int i) throws IOException {
			return factory.createSocket(s, i);
		}

		public String[] getDefaultCipherSuites() {
			return factory.getDefaultCipherSuites();
		}

		public String[] getSupportedCipherSuites() {
			return factory.getSupportedCipherSuites();
		}
	}

	public static class TrustAllManager implements X509TrustManager {
		public void checkClientTrusted(X509Certificate[] cert, String authType)
				throws CertificateException {
		}

		public void checkServerTrusted(X509Certificate[] cert, String authType)
				throws CertificateException {
		}

		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}

	/**
	 * {@inheritDoc} <br/>
	 * <br/>
	 * The default implementation always sets a 30 second timeout.
	 */
	@Override
	public void setRequestTimeout(HttpUriRequest request) {
		HttpParams reqParams = request.getParams();
		HttpConnectionParams.setSoTimeout(reqParams, DEFAULT_TIMEOUT_MILLIS);
		HttpConnectionParams.setConnectionTimeout(reqParams,
				DEFAULT_TIMEOUT_MILLIS);
	}

	@Override
	public String getAPIServer() {
		return API_SERVER;
	}

	@Override
	public String getContentServer() {
		return CONTENT_SERVER;
	}

	@Override
	public String getWebServer() {
		return WEB_SERVER;
	}

	@Override
	public String getUploadServer() {
		if (NEED_HTTPS_UPLOAD) {
			return UPLOAD_SERVER_HTTPS;
		} else {
			return UPLOAD_SERVER;
		}
	}

	private static class DBKeepAliveStrategy implements
			ConnectionKeepAliveStrategy {
		@Override
		public long getKeepAliveDuration(HttpResponse response,
				HttpContext context) {
			// Keep-alive for the shorter of 20 seconds or what the server
			// specifies.
			long timeout = KEEP_ALIVE_DURATION_SECS * 1000;

			HeaderElementIterator i = new BasicHeaderElementIterator(
					response.headerIterator(HTTP.CONN_KEEP_ALIVE));
			while (i.hasNext()) {
				HeaderElement element = i.nextElement();
				String name = element.getName();
				String value = element.getValue();
				if (value != null && name.equalsIgnoreCase("timeout")) {
					try {
						timeout = Math.min(timeout,
								Long.parseLong(value) * 1000);
					} catch (NumberFormatException e) {
					}
				}
			}

			return timeout;
		}
	}

	private static class DBConnectionReuseStrategy extends
			DefaultConnectionReuseStrategy {

		/**
		 * Implements a patch out in 4.1.x and 4.2 that isn't available in 4.0.x
		 * which fixes a bug where connections aren't reused when the response
		 * is gzipped. See https://issues.apache.org/jira/browse/HTTPCORE-257
		 * for info about the issue, and
		 * http://svn.apache.org/viewvc?view=revision&revision=1124215 for the
		 * patch which is copied here.
		 */
		@Override
		public boolean keepAlive(final HttpResponse response,
				final HttpContext context) {
			if (response == null) {
				throw new IllegalArgumentException(
						"HTTP response may not be null.");
			}
			if (context == null) {
				throw new IllegalArgumentException(
						"HTTP context may not be null.");
			}

			// Check for a self-terminating entity. If the end of the entity
			// will
			// be indicated by closing the connection, there is no keep-alive.
			ProtocolVersion ver = response.getStatusLine().getProtocolVersion();
			Header teh = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
			if (teh != null) {
				if (!HTTP.CHUNK_CODING.equalsIgnoreCase(teh.getValue())) {
					return false;
				}
			} else {
				Header[] clhs = response.getHeaders(HTTP.CONTENT_LEN);
				// Do not reuse if not properly content-length delimited
				if (clhs == null || clhs.length != 1) {
					return false;
				}
				Header clh = clhs[0];
				try {
					int contentLen = Integer.parseInt(clh.getValue());
					if (contentLen < 0) {
						return false;
					}
				} catch (NumberFormatException ex) {
					return false;
				}
			}

			// Check for the "Connection" header. If that is absent, check for
			// the "Proxy-Connection" header. The latter is an unspecified and
			// broken but unfortunately common extension of HTTP.
			HeaderIterator hit = response.headerIterator(HTTP.CONN_DIRECTIVE);
			if (!hit.hasNext())
				hit = response.headerIterator("Proxy-Connection");

			// Experimental usage of the "Connection" header in HTTP/1.0 is
			// documented in RFC 2068, section 19.7.1. A token "keep-alive" is
			// used to indicate that the connection should be persistent.
			// Note that the final specification of HTTP/1.1 in RFC 2616 does
			// not
			// include this information. Neither is the "Connection" header
			// mentioned in RFC 1945, which informally describes HTTP/1.0.
			//
			// RFC 2616 specifies "close" as the only connection token with a
			// specific meaning: it disables persistent connections.
			//
			// The "Proxy-Connection" header is not formally specified anywhere,
			// but is commonly used to carry one token, "close" or "keep-alive".
			// The "Connection" header, on the other hand, is defined as a
			// sequence of tokens, where each token is a header name, and the
			// token "close" has the above-mentioned additional meaning.
			//
			// To get through this mess, we treat the "Proxy-Connection" header
			// in exactly the same way as the "Connection" header, but only if
			// the latter is missing. We scan the sequence of tokens for both
			// "close" and "keep-alive". As "close" is specified by RFC 2068,
			// it takes precedence and indicates a non-persistent connection.
			// If there is no "close" but a "keep-alive", we take the hint.

			if (hit.hasNext()) {
				try {
					TokenIterator ti = createTokenIterator(hit);
					boolean keepalive = false;
					while (ti.hasNext()) {
						final String token = ti.nextToken();
						if (HTTP.CONN_CLOSE.equalsIgnoreCase(token)) {
							return false;
						} else if (HTTP.CONN_KEEP_ALIVE.equalsIgnoreCase(token)) {
							// continue the loop, there may be a "close"
							// afterwards
							keepalive = true;
						}
					}
					if (keepalive)
						return true;
					// neither "close" nor "keep-alive", use default policy

				} catch (ParseException px) {
					// invalid connection header means no persistent connection
					// we don't have logging in HttpCore, so the exception is
					// lost
					return false;
				}
			}

			// default since HTTP/1.1 is persistent, before it was
			// non-persistent
			return !ver.lessEquals(HttpVersion.HTTP_1_0);
		}
	}

	private static class DBClientConnManager extends
			ThreadSafeClientConnManager {
		public DBClientConnManager(HttpParams params, SchemeRegistry schreg) {
			super(params, schreg);
		}

		@Override
		public ClientConnectionRequest requestConnection(HttpRoute route,
				Object state) {
			IdleConnectionCloserThread.ensureRunning(this,
					KEEP_ALIVE_DURATION_SECS, KEEP_ALIVE_MONITOR_INTERVAL_SECS);
			return super.requestConnection(route, state);
		}
	}

	private static class IdleConnectionCloserThread extends Thread {
		private final DBClientConnManager manager;
		private final int idleTimeoutSeconds;
		private final int checkIntervalMs;
		private static IdleConnectionCloserThread thread = null;

		public IdleConnectionCloserThread(DBClientConnManager manager,
				int idleTimeoutSeconds, int checkIntervalSeconds) {
			super();
			this.manager = manager;
			this.idleTimeoutSeconds = idleTimeoutSeconds;
			this.checkIntervalMs = checkIntervalSeconds * 1000;
		}

		public static synchronized void ensureRunning(
				DBClientConnManager manager, int idleTimeoutSeconds,
				int checkIntervalSeconds) {
			if (thread == null) {
				thread = new IdleConnectionCloserThread(manager,
						idleTimeoutSeconds, checkIntervalSeconds);
				thread.start();
			}
		}

		@Override
		public void run() {
			try {
				while (true) {
					synchronized (this) {
						wait(checkIntervalMs);
					}
					manager.closeExpiredConnections();
					manager.closeIdleConnections(idleTimeoutSeconds,
							TimeUnit.SECONDS);
					synchronized (IdleConnectionCloserThread.class) {
						if (manager.getConnectionsInPool() == 0) {
							thread = null;
							return;
						}
					}
				}
			} catch (InterruptedException e) {
				thread = null;
			}
		}
	}

	private static class GzipDecompressingEntity extends HttpEntityWrapper {

		/*
		 * From Apache HttpClient Examples.
		 * 
		 * ====================================================================
		 * Licensed to the Apache Software Foundation (ASF) under one or more
		 * contributor license agreements. See the NOTICE file distributed with
		 * this work for additional information regarding copyright ownership.
		 * The ASF licenses this file to you under the Apache License, Version
		 * 2.0 (the "License"); you may not use this file except in compliance
		 * with the License. You may obtain a copy of the License at
		 * 
		 * http://www.apache.org/licenses/LICENSE-2.0
		 * 
		 * Unless required by applicable law or agreed to in writing, software
		 * distributed under the License is distributed on an "AS IS" BASIS,
		 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
		 * implied. See the License for the specific language governing
		 * permissions and limitations under the License.
		 * ====================================================================
		 * 
		 * This software consists of voluntary contributions made by many
		 * individuals on behalf of the Apache Software Foundation. For more
		 * information on the Apache Software Foundation, please see
		 * <http://www.apache.org/>.
		 */

		public GzipDecompressingEntity(final HttpEntity entity) {
			super(entity);
		}

		@Override
		public InputStream getContent() throws IOException,
				IllegalStateException {

			// the wrapped entity's getContent() decides about repeatability
			InputStream wrappedin = wrappedEntity.getContent();

			return new GZIPInputStream(wrappedin);
		}

		@Override
		public long getContentLength() {
			// length of ungzipped content is not known
			return -1;
		}
	}

	/**
	 * 使用微博的Token进行认证
	 * 
	 * Use Weibo token for authentication
	 */
	public void enabledWeiboAccessToken() {

		useWeiboToken = true;
	}

	/**
	 * 取消使用微博的Token进行认证
	 * 
	 * Use VDisk token for authentication
	 */
	public void disabledWeiboAccessToken() {

		useWeiboToken = false;
		weiboAccessToken = null;
	}

	/**
	 * 使用微博的Token进行认证,并设置微博Token
	 * 
	 * Use Weibo token for authentication, and set Weibo token
	 */
	public void enabledAndSetWeiboAccessToken(WeiboAccessToken weiboToken) {

		setWeiboAccessToken(weiboToken);
		useWeiboToken = true;
	}

	/**
	 * 此方法可以用来设置或者更新微博的Token
	 * 
	 * This method is used to set or update Weibo token
	 * 
	 * @param weiboToken
	 */
	public void setWeiboAccessToken(WeiboAccessToken weiboToken) {

		this.weiboAccessToken = weiboToken;
	}

	@Override
	public Context getContext() {
		return mContext;
	}
}
