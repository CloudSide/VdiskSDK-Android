package com.vdisk.android;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.vdisk.net.RESTUtility;
import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.session.AccessToken;

/**
 * 登录认证时弹出的登录对话框
 * 
 * Pop a dialog when there is a authenticating.
 * 
 * @author sina
 * 
 */
public class VDiskDialog extends Dialog {

	static final FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.FILL_PARENT,
			ViewGroup.LayoutParams.FILL_PARENT);

	private final VDiskAuthSession mSession;
	private String mUrl;
	private VDiskDialogListener mListener;
	private ProgressDialog mSpinner;
	private WebView mWebView;
	private RelativeLayout webViewContainer;
	private RelativeLayout mContent;
	private AccessToken mOAuth2AccessToken;

	private static final int SUCCESS = 0;
	private static final int FAILED = -1;
	private Handler handler = new Handler() {

		public void handleMessage(android.os.Message msg) {

			Bundle values = msg.getData();
			switch (msg.what) {
			case SUCCESS:
				Log.i(TAG, "success");
				String error = values.getString("error");
				String error_code = values.getString("error_code");

				if (error == null && error_code == null) {
					mListener.onComplete(values);
				} else if (error.equals("access_denied")) {
					// 用户或授权服务器拒绝授予数据访问权限
					// User or authorization server refuses to grant data access
					// permission
					mListener.onCancel();
				} else {
					mListener.onVDiskException(new VDiskServerException(error,
							Integer.parseInt(error_code)));
				}
				VDiskDialog.this.dismiss();
				break;
			case FAILED:
				VDiskException e = (VDiskException) values
						.getSerializable("error");
				mListener.onVDiskException(e);
				VDiskDialog.this.dismiss();
				break;
			default:
				break;
			}

		};
	};

	private final static String TAG = VDiskDialog.class.getSimpleName();

	public VDiskDialog(final VDiskAuthSession session, Context context,
			String url, VDiskDialogListener listener) {
		super(context, R.style.ContentOverlay);
		mSession = session;
		mUrl = url;
		mListener = listener;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSpinner = new ProgressDialog(getContext());
		mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSpinner.setMessage("Loading...");

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mContent = new RelativeLayout(getContext());

		setUpWebView();

		addContentView(mContent, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
	}

	private void setUpWebView() {
		webViewContainer = new RelativeLayout(getContext());
		// webViewContainer.setOrientation(LinearLayout.VERTICAL);

		// webViewContainer.addView(title, new
		// LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
		// getContext().getResources().getDimensionPixelSize(R.dimen.dialog_title_height)));

		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.getSettings().setJavaScriptEnabled(true);
		// mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		mWebView.setWebViewClient(new VDiskDialog.WeiboWebViewClient());
		mWebView.loadUrl(mUrl);
		mWebView.setLayoutParams(FILL);
		mWebView.setVisibility(View.INVISIBLE);

		webViewContainer.addView(mWebView);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		Resources resources = getContext().getResources();

		// 设置认证对话框的边距
		// Set the margin of authentication dialog
		lp.leftMargin = resources
				.getDimensionPixelSize(R.dimen.dialog_left_margin);
		lp.topMargin = resources
				.getDimensionPixelSize(R.dimen.dialog_top_margin);
		lp.rightMargin = resources
				.getDimensionPixelSize(R.dimen.dialog_right_margin);
		lp.bottomMargin = resources
				.getDimensionPixelSize(R.dimen.dialog_bottom_margin);

		mContent.addView(webViewContainer, lp);
	}

	private class WeiboWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			// 4.0以上的手机，重定向会在这个方法里处理
			// OS after Android 4.0, this method will handle the redirect url.
			Log.d(TAG, "Redirect URL: " + url);
			// 待后台增加对默认重定向地址的支持后修改下面的逻辑
			if (url.startsWith(mSession.getRedirectUrl())) {
				Log.d(TAG, "Redirect URL: --" + url);
				handleRedirectUrl(view, url);

				VDiskDialog.this.dismiss();
				return true;
			}
			// launch non-dialog URLs in a full browser
			// getContext().startActivity(new Intent(Intent.ACTION_VIEW,
			// Uri.parse(url)));
			view.loadUrl(url);
			return true;
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new VDiskDialogError(description, errorCode,
					failingUrl));
			VDiskDialog.this.dismiss();
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			Log.d(TAG, "onPageStarted URL: " + url);
			// 4.0以下的手机，重定向会在这个方法里处理
			// OS before Android 4.0, this method will handle the redirect url.
			// google issue. shouldOverrideUrlLoading not executed
			if (url.startsWith(mSession.getRedirectUrl())) {
				view.stopLoading();
				handleRedirectUrl(view, url);
				VDiskDialog.this.dismiss();
				return;
			}
			mSpinner.show();
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			Log.d(TAG, "onPageFinished URL: " + url);
			mSpinner.dismiss();
			mContent.setBackgroundColor(Color.TRANSPARENT);
			webViewContainer.setBackgroundResource(R.drawable.dialog_bg);
			mWebView.setVisibility(View.VISIBLE);

			if (url.startsWith(mSession.getRedirectUrl())) {
				view.stopLoading();
				return;
			}
			super.onPageFinished(view, url);
		}

	}

	private void handleRedirectUrl(WebView view, String url) {
		Log.d(TAG, "handleRedirectUrl");
		final Bundle values = RESTUtility.parseRedirectUrl(url);

		final String code = values.getString("code");

		if (code != null) {
			new Thread() {

				public void run() {
					Message msg = new Message();
					try {
						mOAuth2AccessToken = mSession.getOAuth2AccessToken(
								code, getContext());
					} catch (VDiskException e) {
						msg.what = FAILED;
						Bundle error = new Bundle();
						error.putSerializable("error", e);
						msg.setData(error);
						handler.sendMessage(msg);
						return;
					}
					values.putSerializable(VDiskAuthSession.OAUTH2_TOKEN,
							mOAuth2AccessToken);
					msg.setData(values);
					msg.what = SUCCESS;
					handler.sendMessage(msg);
				};
			}.start();
		}

	}

}
