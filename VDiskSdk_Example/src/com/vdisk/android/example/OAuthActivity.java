package com.vdisk.android.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.vdisk.android.VDiskAuthSession;
import com.vdisk.android.VDiskDialogListener;
import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.session.AccessToken;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session.AccessType;
import com.vdisk.net.session.WeiboAccessToken;

public class OAuthActivity extends Activity implements VDiskDialogListener {

	/**
	 * 替换为开发者应用的appkey，例如"16*****960";
	 * 
	 * Replace to the appkey of developer's application, such as "16*****960";
	 */
	public static final String CONSUMER_KEY = "CONSUMER_KEY";

	/**
	 * 替换为开发者应用的app secret，例如"94098*****************861f9";
	 * 
	 * Replace to the app secret of developer's application, such as
	 * "94098*****************861f9";
	 */
	public static final String CONSUMER_SECRET = "CONSUMER_SECRET";

	/**
	 * 替换为微博的access_token. 如果你想使用微博token直接访问微盘的API，这个字段不能为空。
	 * 
	 * Replace to the access_token of WEIBO. If you use weibo token to access
	 * VDisk API, this field should not be null.
	 */
	public static final String WEIBO_ACCESS_TOKEN = "WEIBO_ACCESS_TOKEN";

	/**
	 * 
	 * 此处应该替换为与appkey对应的应用回调地址，对应的应用回调地址可在开发者登陆新浪微盘开发平台之后，进入"我的应用--编辑应用信息--回调地址"
	 * 进行设置和查看，如果使用微盘token登陆的话， 应用回调页不可为空。
	 * 
	 * The content of this field should replace with the application's redirect
	 * url of corresponding appkey. Developers can login in Sina VDisk
	 * Development Platform and enter "我的应用--编辑应用信息--回调地址" to set and view the
	 * corresponding application's redirect url. If you use VDisk token, the
	 * redirect url should not be empty. should not be empty.
	 */
	private static final String REDIRECT_URL = "REDIRECT_URL";

	Button btn_oauth;
	CheckBox cbUseWeiboToken;
	VDiskAuthSession session;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/**
		 * 初始化
		 * 
		 * Init
		 */
		AppKeyPair appKeyPair = new AppKeyPair(CONSUMER_KEY, CONSUMER_SECRET);
		session = VDiskAuthSession.getInstance(this, appKeyPair,
				AccessType.APP_FOLDER);

		setContentView(R.layout.auth_main);

		cbUseWeiboToken = (CheckBox) this.findViewById(R.id.cb_use_weibo_token);
		cbUseWeiboToken
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							if (TextUtils.isEmpty(WEIBO_ACCESS_TOKEN)) {
								showAlertDialog(getString(R.string.token_cannot_empty)
										+ "！");
							}
						}
					}
				});

		btn_oauth = (Button) this.findViewById(R.id.btnOAuth);
		btn_oauth.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (cbUseWeiboToken.isChecked()) {
					// 使用微博Token认证
					// Use Weibo token for authentication
					WeiboAccessToken weiboToken = new WeiboAccessToken();
					weiboToken.mAccessToken = OAuthActivity.WEIBO_ACCESS_TOKEN;

					// 开启使用微博Token的开关,如果要使用微博Token的话，必须执行此方法
					// Use this method to turn on the switch to use Weibo token.
					session.enabledAndSetWeiboAccessToken(weiboToken);
				} else {
					// 使用微盘Token认证,需设置重定向网址
					// Need to set REDIRECT_URL if you want to use VDisk token.
					session.setRedirectUrl(REDIRECT_URL);
				}

				session.authorize(OAuthActivity.this, OAuthActivity.this);
			}
		});

		// 如果已经登录，直接跳转到测试页面
		// If you are already logged in, jump to the test page directly.
		if (session.isLinked()) {
			startActivity(new Intent(this, VDiskTestActivity.class));
			finish();
		}
	}

	/**
	 * 警告提示框
	 * 
	 * Warning AlertDialog.
	 * 
	 * @param string
	 */
	protected void showAlertDialog(String string) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.warning));
		builder.setMessage(string);
		builder.create().show();
		cbUseWeiboToken.setChecked(false);
	}

	/**
	 * 认证结束后的回调方法
	 * 
	 * Callback method after authentication.
	 */
	@Override
	public void onComplete(Bundle values) {

		if (values != null) {
			AccessToken mToken = (AccessToken) values
					.getSerializable(VDiskAuthSession.OAUTH2_TOKEN);
			session.finishAuthorize(mToken);
		}

		startActivity(new Intent(this, VDiskTestActivity.class));
		finish();
	}

	/**
	 * 认证出错的回调方法
	 * 
	 * Callback method for authentication errors.
	 */
	@Override
	public void onError(VDiskDialogError error) {
		Toast.makeText(getApplicationContext(),
				"Auth error : " + error.getMessage(), Toast.LENGTH_LONG).show();
	}

	/**
	 * 认证异常的回调方法
	 * 
	 * Callback method for authentication exceptions.
	 */
	@Override
	public void onVDiskException(VDiskException exception) {
		// TODO Auto-generated method stub
		Toast.makeText(getApplicationContext(),
				"Auth exception : " + exception.getMessage(), Toast.LENGTH_LONG)
				.show();
	}

	/**
	 * 认证被取消的回调方法
	 * 
	 * Callback method as authentication is canceled.
	 */
	@Override
	public void onCancel() {
		Toast.makeText(getApplicationContext(), "Auth cancel",
				Toast.LENGTH_LONG).show();
	}

}
