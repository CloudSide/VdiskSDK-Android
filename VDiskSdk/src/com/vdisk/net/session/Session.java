
package com.vdisk.net.session;

import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

import android.content.Context;

import com.vdisk.net.VDiskAPI;

/**
 * Keeps track of a logged in user. Contains configuration options for the
 * {@link VDiskAPI}.
 */
public interface Session {

    /**
     * Returns the app key and secret.
     */
    public AppKeyPair getAppKeyPair();

    /**
     * Returns the currently logged in user's access token.
     */
    public AccessToken getAccessToken();
    
    /**
     * Returns the currently logged in user's Weibo access token.
     */
    public AccessToken getWeiboAccessToken();

    /**
     * Returns what VDisk access type to use (currently app folder or entire
     * VDisk).
     */
    public AccessType getAccessType();

    /**
     * Returns whether or not this session has a user's access token and
     * secret.
     */
    public boolean isLinked();

    /**
     * Unlinks the session by removing any stored access token and secret.
     */
    public void unlink();

    /**
     * OAuth signs the request with the currently-set tokens and secrets.
     *
     * @param request an {@link HttpRequest}.
     */
    public void sign(HttpRequest request);

    /**
     * Will be called every time a request is made to VDisk, in case the
     * proxy changes between requests. Return null if you do not want to use
     * a proxy, or a {@link ProxyInfo} object with a host and optionally a
     * port set.
     */
    public ProxyInfo getProxyInfo();

    /**
     * Will be called every time a request is made to VDisk, in case you want
     * to use a new client every time. However, it's highly recommended to
     * create a client once and reuse it to take advantage of connection reuse.
     */
    public HttpClient getHttpClient();

    /**
     * Will be called every time right before a request is sent to VDisk. It
     * should set the socket and connection timeouts on the request if you want
     * to override the default values. This is abstracted out to cope with
     * signature changes in the Apache HttpClient libraries.
     */
    public void setRequestTimeout(HttpUriRequest request);

    /**
     * Returns the VDisk API server. Changing this will break things.
     */
    public String getAPIServer();

    /**
     * Returns the VDisk content server. Changing this will break things.
     */
    public String getContentServer();

    /**
     * Returns the VDisk web server. Changing this will break things.
     */
    public String getWebServer();
    
    /**
     * Returns the VDisk upload server. Changing this will break things.
     */
    public String getUploadServer();
    
    public Context getContext();

    public enum AccessType {
        VDISK("basic"), APP_FOLDER("sandbox");

        private final String urlPart;

        private AccessType(String urlPart) {
            this.urlPart = urlPart;
        }

        @Override
        public String toString() {
            return urlPart;
        }
    }

    /**
     * Describes a proxy.
     */
    public static final class ProxyInfo {
        /** The address of the proxy. */
        public final String host;

        /** The port of the proxy, or -1 to use the default port. */
        public final int port;

        /**
         * Creates a proxy info.
         *
         * @param host the host to use without a protocol (required).
         * @param port the port to use, or -1 for default port.
         */
        public ProxyInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Creates a proxy info using the default port.
         *
         * @param host the host to use without a protocol (required).
         */
        public ProxyInfo(String host) {
            this(host, -1);
        }
    }
}
