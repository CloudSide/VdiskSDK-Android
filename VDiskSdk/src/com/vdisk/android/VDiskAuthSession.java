package com.vdisk.android;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import com.vdisk.net.RESTUtility;
import com.vdisk.net.VDiskAPI;
import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.session.AbstractSession;
import com.vdisk.net.session.AccessToken;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session;
import com.vdisk.net.session.Token;
import com.vdisk.utils.DesEncrypt;

/**
 * Keeps track of a logged in user and contains configuration options for the
 * {@link VDiskAPI}. Has methods specific to Android for authenticating users
 * via the VDisk app or web site. <br>
 * <br>
 * A typical authentication flow when no user access token pair is saved is as
 * follows: <br>
 * 
 * <pre>
 * VDiskAuthSession session = new VDiskAuthSession(myAppKeys, myAccessType);
 * 
 * When a user returns to your app and you have tokens stored, just create a
 * new session with them:
 * <br>
 * 
 * <pre>
 * VDiskAuthSession session = new VDiskAuthSession(myAppKeys, myAccessType,
 * 		new AccessToken(storedAccessKey, storedAccessSecret));
 * </pre>
 * 
 * @author sina
 * 
 */

public class VDiskAuthSession extends AbstractSession {

	public static String URL_OAUTH2_ACCESS_AUTHORIZE = "https://auth.sina.com.cn/oauth2/authorize";
	public static String URL_OAUTH2_ACCESS_TOKEN = "https://auth.sina.com.cn/oauth2/access_token";

	private String mRedirectUrl = "";

	public static String APP_KEY = "";
	public static String APP_SECRET = "";

	private VDiskDialogListener mAuthDialogListener;
	private VDiskAPI<Session> mApi = null;

	public static final String OAUTH2_TOKEN = "oauth2_token";
	public static final String OAUTH2_PREFS = "prefs";
	public static final String OAUTH2_PREFS_ACCESS_TOKEN = "access_token";
	public static final String OAUTH2_PREFS_EXPIRES_IN = "expires_in";
	public static final String OAUTH2_PREFS_REFRESH_TOKEN = "refresh_token";
	public static final String OAUTH2_PREFS_WEIBO_UID = "uid";

	private static VDiskAuthSession session;

	private final static String TAG = VDiskAuthSession.class.getSimpleName();

	public VDiskAuthSession(AppKeyPair appKeyPair, AccessType type) {
		super(appKeyPair, type);
	}

	public VDiskAuthSession(AppKeyPair appKeyPair, AccessType type,
			AccessToken accessTokenPair) {
		super(appKeyPair, type, accessTokenPair);
	}

	public void finishAuthorize(AccessToken token) {
		setAccessTokenPair(token);
	}

	/**
	 * 获取一个VDiskAuthSession实例
	 * 
	 * Get a instance of VDiskAuthSession.
	 * 
	 * @param ctx
	 * @param appKeyPair
	 * @param type
	 * @return
	 */
	public static VDiskAuthSession getInstance(Context ctx,
			AppKeyPair appKeyPair, AccessType type) {

		mContext = ctx;

		if (null == session) {
			session = buildSession(ctx, appKeyPair, type);
		}
		return session;
	}

	/**
	 * 创建一个Session对象。如果本地已经保存了AccessToken，就创建一个带有AccessToken的Session对象。
	 * 如果没有保存，就创建一个无AccessToken的Session对象。
	 * 
	 * Create a instance of Session. If there is a AccessToken saved in local,
	 * create a instance of Session having AccessToken. Otherwise, create a
	 * instance of Session without AccessToken.
	 * 
	 * @param ctx
	 * @param appKeyPair
	 * @param type
	 * @return
	 */
	private static VDiskAuthSession buildSession(Context ctx,
			AppKeyPair appKeyPair, AccessType type) {
		VDiskAuthSession session;

		AccessToken accessToken = getOAuth2Preference(ctx, appKeyPair);

		if (!TextUtils.isEmpty(accessToken.mAccessToken)
				&& isSessionValid(accessToken) && !useWeiboToken) {
			session = new VDiskAuthSession(appKeyPair, type, accessToken);
		} else {
			session = new VDiskAuthSession(appKeyPair, type);
		}

		return session;
	}

	public void setRedirectUrl(String mRedirectUrl) {
		this.mRedirectUrl = mRedirectUrl;
	}

	public String getRedirectUrl() {
		return mRedirectUrl;
	}

	/**
	 * 开始认证 Start to authorize.
	 * 
	 * @param context
	 * @param listener
	 */
	public void authorize(final Context context,
			final VDiskDialogListener listener) {

		if (useWeiboToken) {
			if (weiboAccessToken != null
					&& !TextUtils.isEmpty(weiboAccessToken.mAccessToken)) {
				listener.onComplete(null);
			} else {
				listener.onError(new VDiskDialogError(
						"Weibo access token is null!", 0, null));
			}
		} else {
			mAuthDialogListener = listener;
			if (context
					.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(context,
						"Application requires permission to read phone state",
						Toast.LENGTH_SHORT).show();
				return;
			}
			startDialogOAuth2(context);
		}
	}

	private void startDialogOAuth2(final Context context) {

		CookieSyncManager.createInstance(context);
		dialog(context, new VDiskDialogListener() {

			public void onComplete(Bundle values) {
				// ensure any cookies set by the dialog are saved
				CookieSyncManager.getInstance().sync();
				if (null == accessToken) {
					accessToken = new AccessToken();
				}

				Log.e(TAG, "onComplete");

				accessToken = (AccessToken) values
						.getSerializable(VDiskAuthSession.OAUTH2_TOKEN);
				if (isSessionValid(accessToken)) {
					Log.e(TAG, "Login Success! access_token="
							+ accessToken.mAccessToken + " expires="
							+ accessToken.mExpiresIn);
					updateOAuth2Preference(context, accessToken);

					setAccessTokenPair(accessToken);

					mAuthDialogListener.onComplete(values);
				} else {
					mAuthDialogListener.onVDiskException(new VDiskException(
							"Failed to receive access token."));
				}
			}

			public void onError(VDiskDialogError error) {
				mAuthDialogListener.onError(error);
			}

			public void onVDiskException(VDiskException exception) {
				mAuthDialogListener.onVDiskException(exception);
			}

			public void onCancel() {
				mAuthDialogListener.onCancel();
			}
		});
	}

	/**
	 * 弹出认证对话框
	 * 
	 * Pop a dialog to authorize.
	 * 
	 * @param context
	 * @param listener
	 */
	public void dialog(Context context, final VDiskDialogListener listener) {

		AppKeyPair appKeyPair = getAppKeyPair();
		String[] params = { "client_id", appKeyPair.key, "redirect_uri",
				mRedirectUrl, "display", "mobile" };
		String url = URL_OAUTH2_ACCESS_AUTHORIZE + "?"
				+ RESTUtility.urlencode(params);
		if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(context,
					"Application requires permission to access the Internet",
					Toast.LENGTH_SHORT).show();
		} else {
			new VDiskDialog(this, context, url, listener).show();
		}
	}

	/**
	 * 验证token是否过期
	 * 
	 * Verify whether the token is expired
	 * 
	 * @param accessToken
	 * @return
	 */
	public static boolean isSessionValid(AccessToken accessTokenPair) {
		if (accessTokenPair != null) {
			long expiresIn = Long.parseLong(accessTokenPair.mExpiresIn);
			Log.e(TAG, expiresIn + "===>" + System.currentTimeMillis() / 1000);
			return (!TextUtils.isEmpty(accessTokenPair.mAccessToken) && (expiresIn == 0 || (System
					.currentTimeMillis() / 1000 < expiresIn)));
		}
		return false;
	}

	/**
	 * 使用用户名和密码直接获取AccessToken(限于某些高级权限的应用).
	 * 
	 * Use username and password to get AccessToken directly(limited to some
	 * applications with advanced permission).
	 * 
	 * @param context
	 * @param usrname
	 *            用户名
	 * @param password
	 *            密码
	 * @return
	 * @throws VDiskException
	 */
	public AccessToken getOAuth2AccessToken(Context context, String usrname,
			String password) throws VDiskException {

		AppKeyPair appKeyPair = getAppKeyPair();
		mApi = new VDiskAPI<Session>(this);
		String rlt = mApi.doOAuth2Password(usrname, password, appKeyPair,
				context);
		AccessToken accessToken = new AccessToken(rlt, mAuthDialogListener);
		this.accessToken = accessToken;
		return accessToken;
	}

	/**
	 * 访问接口，获取AccessToken.
	 * 
	 * Access the API and get the AccessToken
	 * 
	 * @param code
	 * @param context
	 * @return
	 * @throws VDiskException
	 */
	public AccessToken getOAuth2AccessToken(String code, Context context)
			throws VDiskException {

		AppKeyPair appKeyPair = getAppKeyPair();
		mApi = new VDiskAPI<Session>(this);
		String rlt = mApi.doOAuth2Authorization(appKeyPair, code, mRedirectUrl,
				context);
		AccessToken accessToken = new AccessToken(rlt, mAuthDialogListener);
		this.accessToken = accessToken;
		return accessToken;
	}

	/**
	 * 更新本地SharedPreferences中保存的数据
	 * 
	 * Update values in local SharedPreferences.
	 * 
	 * @param context
	 * @param mPreferenceToken
	 */
	public void updateOAuth2Preference(Context context, Token mPreferenceToken) {

		TelephonyManager telephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		DesEncrypt encrypt = new DesEncrypt(telephonyManager.getDeviceId(),
				getAppKeyPair());
		SharedPreferences prefs = context.getSharedPreferences(
				VDiskAuthSession.OAUTH2_PREFS, 0);
		Editor edit = prefs.edit();
		edit.putString(VDiskAuthSession.OAUTH2_PREFS_ACCESS_TOKEN,
				encrypt.getEncString(mPreferenceToken.mAccessToken));
		edit.putString(VDiskAuthSession.OAUTH2_PREFS_EXPIRES_IN,
				encrypt.getEncString(mPreferenceToken.mExpiresIn));
		edit.putString(VDiskAuthSession.OAUTH2_PREFS_REFRESH_TOKEN,
				encrypt.getEncString(mPreferenceToken.mRefreshToken));
		edit.putString(VDiskAuthSession.OAUTH2_PREFS_WEIBO_UID,
				encrypt.getEncString(mPreferenceToken.mUid));
		edit.commit();
	}

	/**
	 * 清空本地SharedPreferences中保存的数据
	 * 
	 * Clear values in local SharedPreferences.
	 * 
	 * @param context
	 */
	public void clearOAuth2Preference(Context context) {

		SharedPreferences prefs = context.getSharedPreferences(
				VDiskAuthSession.OAUTH2_PREFS, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	}

	/**
	 * 从本地的SharedPreferences中得到AccessToken。
	 * 
	 * Get AccessToken from local SharePreference.
	 * 
	 * @param ctx
	 * @param appKeyPair
	 * @return
	 */
	public static AccessToken getOAuth2Preference(Context ctx,
			AppKeyPair appKeyPair) {

		TelephonyManager telephonyManager = (TelephonyManager) ctx
				.getSystemService(Context.TELEPHONY_SERVICE);
		DesEncrypt encrypt = new DesEncrypt(telephonyManager.getDeviceId(),
				appKeyPair);
		AccessToken mPreferenceToken = new AccessToken();
		SharedPreferences prefs = ctx.getSharedPreferences(
				VDiskAuthSession.OAUTH2_PREFS, 0);
		mPreferenceToken.mAccessToken = encrypt.getDesString(prefs.getString(
				VDiskAuthSession.OAUTH2_PREFS_ACCESS_TOKEN, ""));
		mPreferenceToken.mExpiresIn = encrypt.getDesString(prefs.getString(
				VDiskAuthSession.OAUTH2_PREFS_EXPIRES_IN, ""));
		mPreferenceToken.mRefreshToken = encrypt.getDesString(prefs.getString(
				VDiskAuthSession.OAUTH2_PREFS_REFRESH_TOKEN, ""));
		mPreferenceToken.mUid = encrypt.getDesString(prefs.getString(
				VDiskAuthSession.OAUTH2_PREFS_WEIBO_UID, ""));
		return mPreferenceToken;
	}

	/**
	 * AccessToken失效后，使用RefreshToken进行刷新(限于某些高级权限的应用).
	 * 
	 * After AccessToken become invalid, use RefreshToken to do
	 * refreshing(limited to some applications with advanced permission).
	 * 
	 * @param refreshToken
	 * @param ctx
	 * @return
	 * @throws VDiskException
	 */
	public AccessToken refreshOAuth2AccessToken(String refreshToken, Context ctx)
			throws VDiskException {

		if (useWeiboToken) {
			return null;
		}
		AppKeyPair appKeyPair = getAppKeyPair();
		if (null == mApi) {
			mApi = new VDiskAPI<Session>(this);
		}
		String rlt = mApi.doOAuth2Refresh(appKeyPair, refreshToken, ctx);
		Log.d(TAG, rlt);
		AccessToken accessToken = new AccessToken(rlt, mAuthDialogListener);
		this.accessToken = accessToken;
		updateOAuth2Preference(ctx, accessToken);
		return accessToken;
	}

	/**
	 * 调用此方法可以将账户注销
	 * 
	 * Calling this method can logout the account.
	 */
	@Override
	public void unlink() {
		super.unlink();
		clearOAuth2Preference(mContext);
	}

}
