package com.vdisk.net.session;

import org.json.JSONException;
import org.json.JSONObject;

import com.vdisk.android.VDiskDialogListener;
import com.vdisk.net.exception.VDiskException;

/**
 * <p>
 * Holds a user's access token.
 * </p>
 */
public class AccessToken extends Token {

	private static final long serialVersionUID = 1L;

	public AccessToken() {
		
	}
	
    public AccessToken(String rltString, VDiskDialogListener listener) {
		if (rltString != null) {
			if (rltString.indexOf("{") >= 0) {
				try {
					JSONObject json = new JSONObject(rltString);
					mAccessToken = json.getString("access_token");
					mExpiresIn = json.getString("expires_in");
					mRefreshToken = json.optString("refresh_token");
					mUid = json.optString("uid");
				} catch (JSONException e) {
					listener.onVDiskException(new VDiskException(
							"Fail get Access Token,Refresh token valid"));
				}
			}
		}
	}
   
}
