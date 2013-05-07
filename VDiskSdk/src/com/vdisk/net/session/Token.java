package com.vdisk.net.session;

import java.io.Serializable;

/**
 * A token base class.
 * 
 * @author sina
 */
public class Token implements Serializable {

	private static final long serialVersionUID = -8762944288133769081L;

	/**
	 * AccessToken,用户验证的唯一标识.
	 * 
	 * The unique identifier of the user authentication
	 */
	public String mAccessToken = "";

	/**
	 * 使用此RefreshToken可以对AccessToken进行刷新，从而可以不用登录，就得到最新的AccessToken(仅限于某些高级权限的应用
	 * );
	 * 
	 * Use this RefreshToken to refresh AccessToken, so that you needn't to
	 * login again to get the new AccessToken(limited to some applications with
	 * advanced permission).
	 */
	public String mRefreshToken = "";

	/**
	 * token的有效期，在此时间点之前，token都有效,是一个时间戳,单位是秒.
	 * 
	 * Expire time of token. Token is available before this time. It is a time
	 * stamp, its unit is seconds.
	 * 
	 */
	public String mExpiresIn = "";

	/**
	 * 用户的微博uid
	 * 
	 * User's Weibo uid
	 */
	public String mUid = "";

	public Token() {

	}

	public Token(String accessToken) {
		this.mAccessToken = accessToken;
	}

	public String getToken() {
		return mAccessToken;
	}
}
