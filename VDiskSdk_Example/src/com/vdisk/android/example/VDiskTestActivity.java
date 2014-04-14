package com.vdisk.android.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.sina.weibo.sdk.demo.AccessTokenKeeper;
import com.vdisk.android.VDiskAuthSession;
import com.vdisk.net.VDiskAPI;
import com.vdisk.net.VDiskAPI.Account;
import com.vdisk.net.VDiskAPI.CreatedCopyRef;
import com.vdisk.net.VDiskAPI.DeltaEntry;
import com.vdisk.net.VDiskAPI.DeltaPage;
import com.vdisk.net.VDiskAPI.Entry;
import com.vdisk.net.VDiskAPI.ThumbSize;
import com.vdisk.net.VDiskAPI.VDiskLink;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskFileSizeException;
import com.vdisk.net.exception.VDiskIOException;
import com.vdisk.net.exception.VDiskLocalStorageFullException;
import com.vdisk.net.exception.VDiskParseException;
import com.vdisk.net.exception.VDiskPartialFileException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.exception.VDiskUnlinkedException;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session.AccessType;

/**
 * 例子程序的测试页面
 * 
 * Demo page for VDisk API's main functions.
 * 
 * @author sina
 * 
 */
public class VDiskTestActivity extends Activity {

	VDiskAuthSession session;

	/**
	 * 使用VDiskAPI，可以调用所有的微盘接口，非常重要。
	 * 
	 * Use VDiskAPI for calling all the API of VDisk, IMPOPTANT.
	 */
	VDiskAPI<VDiskAuthSession> mApi;

	private static final String TAG = "VDiskTestActivity";
	private static final int SUCCEED = 0;
	private static final int FAILED = -1;
	private static final int SHOW_THUMBNAIL = 2;
	final static private int NEW_PICTURE = 1;
	private final String PHOTO_DIR = "/";
	private Drawable mDrawable;
	ClipboardManager clip;

	ListView mListView;
	String[] list;

	/**
	 * 用户相关
	 * 
	 * User-related
	 */
	private static final int ACCOUNT_TITLE = 0;
	private static final int ACCOUNT_INFO = 1;
	private static final int LOGOUT = 2;

	/**
	 * 文件，目录相关
	 * 
	 * File&Directory-related
	 */
	private static final int FILES_TITLE = 3;
	private static final int UPLOAD_PICTURE = 4;
	private static final int LARGE_FILE_UPLOAD = 5;
	private static final int DOWNLOAD_FILE = 6;
	private static final int DIR_INFO = 7;
	private static final int FILE_INFO = 8;
	private static final int HISTORY_VERSIONS = 9;
	private static final int SEARCH = 10;
	private static final int THUMBNAIL = 11;
	private static final int GET_DOWNLOAD_LINK = 12;
	private static final int DELTA = 13;

	/**
	 * 文件编辑
	 * 
	 * Document edit-related
	 */
	private static final int FILE_EDIT_TITLE = 14;
	private static final int COPY = 15;
	private static final int CREATE_DIR = 16;
	private static final int DELETE = 17;
	private static final int MOVE = 18;
	private static final int RESTORE_VERSION = 19;

	/**
	 * 分享相关
	 * 
	 * Share-related
	 */
	private static final int SHARE_TITLE = 20;
	private static final int SHARE = 21;
	private static final int SHARE_CANCEL = 22;
	private static final int SAVE_TO_VDISK = 23;
	private static final int CREATE_COPYREF = 24;
	private static final int GET_DOWNLOAD_LINK_BY_COPYREF = 25;

	String mCameraFileName;
	ProgressDialog dialog;

	/**
	 * 调用接口时成功或失败的回调
	 * 
	 * Callback of calling VDisk API.
	 */
	Handler handler = new Handler() {

		public void handleMessage(android.os.Message msg) {

			switch (msg.what) {
			case SUCCEED:
				dialog.dismiss();
				showToast(msg.getData().getString("msg"));
				break;
			case SHOW_THUMBNAIL:
				dialog.dismiss();
				if (mDrawable != null)
					showThumbnailDialog();
				break;
			case FAILED:
				dialog.dismiss();
				VDiskException e = (VDiskException) msg.getData()
						.getSerializable("error");
				String errMsg = "";
				if (e instanceof VDiskServerException) {
					errMsg = ((VDiskServerException) e).toString();
					Log.d("SDK", errMsg);
				} else if (e instanceof VDiskIOException) {
					errMsg = getString(R.string.exception_vdisk_io).toString();
				} else if (e instanceof VDiskParseException) {
					errMsg = getString(R.string.exception_vdisk_parse)
							.toString();
				} else if (e instanceof VDiskLocalStorageFullException) {
					errMsg = getString(
							R.string.exception_vdisk_local_storage_full)
							.toString();
				} else if (e instanceof VDiskUnlinkedException) {
					errMsg = getString(R.string.exception_vdisk_unlinked)
							.toString();
				} else if (e instanceof VDiskFileSizeException) {
					errMsg = getString(R.string.exception_vdisk_file_size)
							.toString();
				} else if (e instanceof VDiskPartialFileException) {
					errMsg = getString(R.string.exception_vdisk_partial_file)
							.toString();
				} else {
					errMsg = getString(R.string.exception_vdisk_unknown)
							.toString();
				}
				showToast(errMsg);
				break;
			default:
				break;
			}

		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mCameraFileName = savedInstanceState.getString("mCameraFileName");
		}

		list = new String[] { getString(R.string.user_related),
				getString(R.string.user_info), getString(R.string.user_logout),
				getString(R.string.file_and_dir_related),
				getString(R.string.take_photo_upload),
				getString(R.string.bigfile_seg_upload),
				getString(R.string.download_file),
				getString(R.string.dir_info), getString(R.string.file_info),
				getString(R.string.history_version),
				getString(R.string.search_files),
				getString(R.string.thumbnail),
				getString(R.string.get_download_link),
				getString(R.string.get_file_and_dir_change_log),
				getString(R.string.file_edit), getString(R.string.copy_file),
				getString(R.string.create_dir),
				getString(R.string.del_file_or_dir),
				getString(R.string.move_file),
				getString(R.string.restore_to_aversion),
				getString(R.string.share_related),
				getString(R.string.share_files),
				getString(R.string.cancel_sharing),
				getString(R.string.save_to_vdisk),
				getString(R.string.create_copy_ref),
				getString(R.string.get_link_from_ref) };

		setContentView(R.layout.vdisk_test);
		mListView = (ListView) findViewById(R.id.lv_list);

		FunctionListAdapter adapter = new FunctionListAdapter(this);
		mListView.setAdapter(adapter);

		dialog = new ProgressDialog(this);
		dialog.setMessage(getString(R.string.wait_for_load));

		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				switch (position) {
				case ACCOUNT_INFO:
					// 获取用户信息
					// Get user's account information
					getAccountInfo();
					break;
				case UPLOAD_PICTURE:
					// 拍照上传
					// Take a photo and upload
					showInputDialog(
							getString(R.string.take_photo_upload),
							new String[] { getString(R.string.please_input_pic_name) },
							position);
					break;
				case LARGE_FILE_UPLOAD:
					// 大文件分段上传
					// Upload a large file
					showInputDialog(
							getString(R.string.bigfile_seg_upload),
							new String[] {
									getString(R.string.please_input_up_file_local_path),
									getString(R.string.please_input_up_file_vdisk_path) },
							position);
					break;
				case DOWNLOAD_FILE:
					// 下载
					// download file
					showInputDialog(
							getString(R.string.download_file),
							new String[] { getString(R.string.please_input_down_file_path) },
							position);
					break;
				case DIR_INFO:
					// 获取文件夹信息
					// Get information of directory
					showInputDialog(
							getString(R.string.dir_info),
							new String[] { getString(R.string.please_input_dir_path) },
							position);
					break;
				case FILE_INFO:
					// 获取文件信息
					// Get information of file
					showInputDialog(
							getString(R.string.file_info),
							new String[] { getString(R.string.please_input_file_path) },
							position);
					break;
				case HISTORY_VERSIONS:
					// 获取历史版本
					// Get history versions
					showInputDialog(
							getString(R.string.history_version),
							new String[] { getString(R.string.please_input_file_path) },
							position);
					break;
				case RESTORE_VERSION:
					// 还原
					// Restore to some version
					showInputDialog(
							getString(R.string.restore_to_aversion),
							new String[] {
									getString(R.string.please_input_file_path),
									getString(R.string.please_input_restore_to_version) },
							position);
					break;
				case SEARCH:
					// 搜索
					// Search files
					showInputDialog(
							getString(R.string.search_file_in_vdisk),
							new String[] { getString(R.string.please_input_keyword) },
							position);
					break;
				case SHARE:
					// 分享文件
					// Share files
					showInputDialog(
							getString(R.string.share_files),
							new String[] { getString(R.string.please_input_file_path_to_share) },
							position);
					break;
				case SHARE_CANCEL:
					// 取消文件分享
					// Cancel the sharing
					showInputDialog(
							getString(R.string.cancel_sharing),
							new String[] { getString(R.string.please_input_file_path_to_cancel_sharing) },
							position);
					break;
				case THUMBNAIL:
					// 下载缩略图
					// Download the thumbnail
					showInputDialog(
							getString(R.string.down_thumbnail),
							new String[] { getString(R.string.please_input_down_pic_path) },
							position);
					break;
				case SAVE_TO_VDISK:
					// 保存到我的微盘
					// Save the file to VDisk
					showInputDialog(
							getString(R.string.save_to_vdisk),
							new String[] {
									getString(R.string.please_input_file_copy_ref),
									getString(R.string.please_input_target_file_path) },
							position);
					break;
				case COPY:
					// 复制
					// Copy the file
					showInputDialog(
							getString(R.string.copy_file),
							new String[] {
									getString(R.string.please_input_source_file_path),
									getString(R.string.please_input_target_file_path) },
							position);
					break;
				case CREATE_DIR:
					// 新建文件夹
					// Create a directory
					showInputDialog(
							getString(R.string.create_dir),
							new String[] { getString(R.string.please_input_dir_path) },
							position);
					break;
				case DELETE:
					// 删除
					// Delete a file or a directory
					showInputDialog(
							getString(R.string.del_file_or_dir),
							new String[] { getString(R.string.please_input_file_or_dir_path) },
							position);
					break;
				case MOVE:
					// 移动文件
					// Move a file
					showInputDialog(
							getString(R.string.move_file),
							new String[] {
									getString(R.string.please_input_source_file_path),
									getString(R.string.please_input_target_file_path) },
							position);
					break;
				case GET_DOWNLOAD_LINK:
					// 获取下载链接
					// Get the download link
					showInputDialog(
							getString(R.string.get_download_link),
							new String[] { getString(R.string.please_input_file_path) },
							position);
					break;
				case CREATE_COPYREF:
					// 创建拷贝引用
					// Create a copy reference
					showInputDialog(
							getString(R.string.create_copy_ref),
							new String[] { getString(R.string.please_input_source_file_path) },
							position);
					break;
				case GET_DOWNLOAD_LINK_BY_COPYREF:
					// 根据拷贝引用获取下载链接
					// Get download link by copy reference
					showInputDialog(
							getString(R.string.get_download_link),
							new String[] { getString(R.string.please_input_file_copy_ref) },
							position);
					break;
				case DELTA:
					// 获取用户文件和目录操作变化记录
					// Get log of file&directory's changes
					showInputDialog(
							getString(R.string.get_file_and_dir_change_log),
							new String[] { getString(R.string.please_input_list_start_position) },
							position);
					break;
				case LOGOUT:
					// 调用此方法可以将账户注销
					// Calling this method can logout the account
					session.unlink();
					AccessTokenKeeper.clear(VDiskTestActivity.this);
					startActivity(new Intent(VDiskTestActivity.this,
							OAuthActivity.class));
					finish();

					break;
				default:
					break;
				}
			}
		});

		AppKeyPair appKeyPair = new AppKeyPair(OAuthActivity.CONSUMER_KEY,
				OAuthActivity.CONSUMER_SECRET);

		session = VDiskAuthSession.getInstance(this, appKeyPair,
				AccessType.APP_FOLDER);

		mApi = new VDiskAPI<VDiskAuthSession>(session);
	}

	/**
	 * 拍照上传
	 * 
	 * Take a photo and upload
	 * 
	 * @param name
	 *            上传的图片名称 Name of picture uploaded
	 */
	private void uploadPicture(String name) {

		Intent intent = new Intent(); // Picture from camera
		intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

		// This is not the right way to do this, but for some reason, having it
		// store it in MediaStore.Images.Media.EXTERNAL_CONTENT_URI isn't
		// working right.

		String outPath = Environment.getExternalStorageDirectory()
				.getAbsoluteFile() + "/VDisk_SDK_cache/" + name;

		File outFile = createDirFile(outPath);

		mCameraFileName = outFile.toString();
		Uri outuri = Uri.fromFile(outFile);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, outuri);
		Log.i(TAG, "Importing New Picture: " + mCameraFileName);
		try {
			startActivityForResult(intent, NEW_PICTURE);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, getString(R.string.find_no_camera),
					Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * 下载文件
	 * 
	 * Download a file
	 * 
	 * @param path
	 *            文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file or
	 *            directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 */
	private void downloadFile(String path) {
		String targetPath = Environment.getExternalStorageDirectory()
				.getAbsolutePath() + "/VDisk_SDK_cache/" + path;
		DownloadFile downloadFile = new DownloadFile(VDiskTestActivity.this,
				mApi, path, targetPath);
		downloadFile.execute();
	}

	/**
	 * 下载缩略图
	 * 
	 * Download the thumbnail
	 * 
	 * @param path
	 *            文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file or
	 *            directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 */
	private void downloadThumbnail(final String path) {

		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				FileOutputStream mFos = null;
				try {
					String cachePath = Environment
							.getExternalStorageDirectory().getAbsolutePath()
							+ "/vdisk.thumbnail";
					try {
						mFos = new FileOutputStream(cachePath, false);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					mApi.getThumbnail(path, mFos, ThumbSize.ICON_640x480, null);
					mDrawable = Drawable.createFromPath(cachePath);
					msg.what = SHOW_THUMBNAIL;
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 获取用户空间信息
	 * 
	 * Get user's account information
	 */
	private void getAccountInfo() {

		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Account account = mApi.accountInfo();
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.total_space) + ":"
							+ account.quota + getString(R.string.abyte) + "\n"
							+ getString(R.string.used_space) + ":"
							+ account.consumed + getString(R.string.abyte)
							+ "\n" + getString(R.string.screen_name) + ":"
							+ account.screen_name + "\n"
							+ getString(R.string.location) + ":"
							+ account.location + "\n"
							+ getString(R.string.gender) + ":" + account.gender
							+ "\n" + getString(R.string.profile_image_url)
							+ ":" + account.profile_image_url + "\n"
							+ getString(R.string.avatar_large) + ":"
							+ account.avatar_large);
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 获取文件/文件夹原始信息
	 * 
	 * Get information of file or directory
	 * 
	 * @param path
	 *            文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file or
	 *            directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 * @param type
	 *            0 表示获取该文件夹下所有文件列表信息;1 表示获取该文件/文件夹的原始信息. 0 represents to get
	 *            information of the all file list in the directory; 1
	 *            represents to get original information of the file or
	 *            directory.
	 */
	private void getMetaData(final String path, final int type) {

		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metadata = mApi.metadata(path, null, true, false);
					List<Entry> contents = metadata.contents;

					ArrayList<String> list = new ArrayList<String>();

					if (contents != null && type == 0) {
						for (Entry entry : contents) {
							if (entry.isDir) {
								list.add(entry.fileName() + "("
										+ getString(R.string.adir) + ")");
							} else {
								list.add(entry.fileName());
							}
						}
					} else {
						list.add(getString(R.string.file_name) + ": "
								+ metadata.fileName() + "\n"
								+ getString(R.string.file_size) + ": "
								+ metadata.size + "\n"
								+ getString(R.string.edit_time) + ": "
								+ metadata.modified + "\n"
								+ getString(R.string.file_path) + ": "
								+ metadata.path);
					}
					startResultActivity(list);
					dialog.dismiss();
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
					msg.setData(data);
					handler.sendMessage(msg);
				}
			}
		}.start();
	}

	/**
	 * 获取分享链接
	 * 
	 * Get the shared link
	 * 
	 * @param path
	 *            文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file, format as
	 *            "/test/1.jpg", and the first "/" represents the root directory
	 *            of VDisk.
	 */
	private void shareFile(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					String shareLink = mApi.share(path);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.share_path_is)
							+ "：" + shareLink);

					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(shareLink));
					startActivity(intent);
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 获取下载链接
	 * 
	 * Get the download link
	 * 
	 * @param path
	 *            文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file, format as
	 *            "/test/1.jpg", and the first "/" represents the root directory
	 *            of VDisk.
	 */
	private void getDownloadUrl(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					VDiskLink media = mApi.media(path, false);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.down_link_is)
							+ "：" + media.url + ";"
							+ getString(R.string.past_time_is) + "："
							+ media.expires);

					String type = getMIMEType(media.url);

					/**
					 * 如果是流媒体文件的话可以支持在线播放，否则就直接跳浏览器进行下载
					 * 
					 * If the file is a streaming media file, we can try playing
					 * online, or jump directly to the browser to download.
					 */
					Intent it = new Intent();
					it.setAction(Intent.ACTION_VIEW);
					it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					Uri uri = Uri.parse(media.url);
					it.setDataAndType(uri, type);
					startActivity(it);

				} catch (ActivityNotFoundException e) {

				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 取消对某文件的分享
	 * 
	 * Cancel the sharing of file
	 * 
	 * @param path
	 *            文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file, format as
	 *            "/test/1.jpg", and the first "/" represents the root directory
	 *            of VDisk.
	 */
	private void cancelShare(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metaData = mApi.cancelShare(path);
					msg.what = SUCCEED;
					data.putString(
							"msg",
							getString(R.string.suc_to_cancel_file)
									+ metaData.fileName()
									+ getString(R.string.sharing) + "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 获取文件历史版本列表
	 * 
	 * Get a history version list of file
	 * 
	 * @param path
	 *            文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file, format as
	 *            "/test/1.jpg", and the first "/" represents the root directory
	 *            of VDisk.
	 */
	private void getRevisions(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					List<Entry> contents = mApi.revisions(path, -1);

					ArrayList<String> list = new ArrayList<String>();

					for (Entry entry : contents) {
						list.add(entry.fileName() + ", "
								+ getString(R.string.version) + ":" + entry.rev);
					}
					startResultActivity(list);
					dialog.dismiss();
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
					msg.setData(data);
					handler.sendMessage(msg);
				}
			}
		}.start();
	}

	/**
	 * 还原指定文件到某个版本
	 * 
	 * Restore a file to a specified version
	 * 
	 * @param path
	 *            文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file, format as
	 *            "/test/1.jpg", and the first "/" represents the root directory
	 *            of VDisk.
	 * @param revision
	 *            目标版本号. Target version.
	 */
	private void restore(final String path, final String revision) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metadata = mApi.restore(path, revision);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.suc_restore_file)
							+ metadata.fileName()
							+ getString(R.string.to_version) + ":"
							+ metadata.rev);
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 搜索我的微盘文件
	 * 
	 * Search files in my VDisk
	 * 
	 * @param keyword
	 *            搜索关键词. Keyword for searching
	 */
	private void search(final String keyword) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					List<Entry> result = mApi.search("/", keyword, -1, false);

					ArrayList<String> list = new ArrayList<String>();

					for (Entry entry : result) {

						if (entry.isDir) {
							list.add(entry.fileName() + "("
									+ getString(R.string.adir) + ")");
						} else {
							list.add(entry.fileName());
						}
					}
					startResultActivity(list);
					dialog.dismiss();
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
					msg.setData(data);
					handler.sendMessage(msg);
				}
			}
		}.start();
	}

	/**
	 * 创建文件夹
	 * 
	 * Create a directory
	 * 
	 * @param path
	 *            新文件夹的路径,格式为 "/test",第一个"/"表示微盘根目录. Path of new directory,
	 *            format as "/test", and the first "/" represents the root
	 *            directory of VDisk.
	 */
	private void createFolder(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metaData = mApi.createFolder(path);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.create_dir)
							+ metaData.fileName() + getString(R.string.be_suc)
							+ "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 删除文件/文件夹
	 * 
	 * Delete a file or a directory
	 * 
	 * @param path
	 *            文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of file or
	 *            directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 */
	private void delete(final String path) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metaData = mApi.delete(path);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.del_file)
							+ metaData.fileName() + getString(R.string.be_suc)
							+ "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 复制文件/文件夹
	 * 
	 * Copy the file or directory
	 * 
	 * @param fromPath
	 *            源文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of source file
	 *            or directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 * @param toPath
	 *            目标文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of target
	 *            file or directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 */
	private void copy(final String fromPath, final String toPath) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metadata = mApi.copy(fromPath, toPath);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.suc_create_named)
							+ metadata.fileName() + getString(R.string.acopy)
							+ "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 创建拷贝引用
	 * 
	 * Create a copy reference
	 * 
	 * @param sourcePath
	 *            源文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of source file,
	 *            format as "/test/1.jpg", and the first "/" represents the root
	 *            directory of VDisk.
	 */
	private void createCopyRef(final String sourcePath) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				/**
				 * 如果不调此句，在某些手机上，由于调用系统粘贴板的原因，导致系统崩溃。
				 * 
				 * Sometimes when using clipboard, we can avoid crush by
				 * preparing the looper. If not call this method, on some
				 * phones, calling the system lipboard will cause a system
				 * crash.
				 * 
				 */
				Looper.prepare();
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					CreatedCopyRef createCopyRef = mApi
							.createCopyRef(sourcePath);
					msg.what = SUCCEED;
					data.putString("msg",
							getString(R.string.created_copy_ref_is) + "："
									+ createCopyRef.copyRef);
					clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
					/**
					 * 复制到系统粘贴板中.
					 * 
					 * Copy to clipboard.
					 */
					clip.setText(createCopyRef.copyRef);
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 根据拷贝引用获取下载链接
	 * 
	 * Get download link by copy reference
	 * 
	 * @param sourceCopyRef
	 *            拷贝引用 Source copy reference
	 */
	private void getDownloadUrlByCopyRef(final String sourceCopyRef) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					VDiskLink link = mApi.getLinkByCopyRef(sourceCopyRef);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.down_link_is)
							+ "：" + link.url + ";"
							+ getString(R.string.past_time_is) + "："
							+ link.expires);

					String type = getMIMEType(link.url);

					/**
					 * 如果是流媒体文件的话可以支持在线播放，否则就直接跳浏览器进行下载
					 * 
					 * If the file is a streaming media file, we can try playing
					 * online, or jump directly to the browser to download.
					 */
					Intent it = new Intent();
					it.setAction(Intent.ACTION_VIEW);
					it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					Uri uri = Uri.parse(link.url);
					it.setDataAndType(uri, type);
					startActivity(it);
				} catch (ActivityNotFoundException e) {

				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 保存到我的微盘
	 * 
	 * Save the file to my VDisk
	 * 
	 * @param sourceCopyRef
	 *            拷贝引用 Source copy reference
	 * @param targetPath
	 *            目标文件路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of target file,
	 *            format as "/test/1.jpg", and the first "/" represents the root
	 *            directory of VDisk.
	 */
	private void addFromCopyRef(final String sourceCopyRef,
			final String targetPath) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry entry = mApi
							.addFromCopyRef(sourceCopyRef, targetPath);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.suc_save_file)
							+ entry.fileName()
							+ getString(R.string.to_my_vdisk) + "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 移动文件
	 * 
	 * Move a file
	 * 
	 * @param fromPath
	 *            源文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of source file
	 *            or directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 * @param toPath
	 *            目标文件/文件夹路径,格式为 "/test/1.jpg",第一个"/"表示微盘根目录. Path of target
	 *            file or directory, format as "/test/1.jpg", and the first "/"
	 *            represents the root directory of VDisk.
	 */
	private void move(final String fromPath, final String toPath) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					Entry metadata = mApi.move(fromPath, toPath);
					msg.what = SUCCEED;
					data.putString("msg", getString(R.string.move_file)
							+ metadata.fileName() + getString(R.string.be_suc)
							+ "！");
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
				}
				msg.setData(data);
				handler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * 获取用户文件和目录操作变化记录
	 * 
	 * Get change records of file&directory operation
	 * 
	 * @param cursor
	 *            可选，整数，仅取列表的起始位置。传递此参数时，接口返回的列表是此位置之后的操作变化记录. Optional, is an
	 *            integer, the start position of the operation list. When
	 *            passing this parameter, returns the change records of
	 *            file&directory operation after this position.
	 */
	private void getDelta(final String cursor) {
		dialog.show();
		new Thread() {
			@Override
			public void run() {
				Message msg = new Message();
				Bundle data = new Bundle();
				try {
					DeltaPage<Entry> deltaPage = mApi.delta(TextUtils
							.isEmpty(cursor) ? null : cursor);

					ArrayList<String> list = new ArrayList<String>();
					list.add("cursor: " + deltaPage.cursor);
					list.add("hasMore: " + deltaPage.hasMore);
					list.add("reset: " + deltaPage.reset);
					List<DeltaEntry<Entry>> entries = deltaPage.entries;

					if (entries != null) {
						for (DeltaEntry<Entry> entry : entries) {
							String entryInfo = entry.metadata == null ? "null"
									: entry.metadata.modified;
							list.add(entry.lcPath + ": " + entryInfo);
						}
					}

					startResultActivity(list);
					dialog.dismiss();
				} catch (VDiskException e) {
					e.printStackTrace();
					msg.what = FAILED;
					data.putSerializable("error", e);
					msg.setData(data);
					handler.sendMessage(msg);
				}
			}
		}.start();
	}

	/**
	 * 分段上传大文件
	 * 
	 * Upload a large file
	 * 
	 * @param srcPath
	 *            本地文件的路径 Source path of local file
	 * @param desPath
	 *            云端目标文件的文件夹路径 Target directory path of file in the cloud
	 */
	private void uploadLargeFile(String srcPath, String desPath) {
		File file = new File(srcPath);
		LargeFileUpload upload = new LargeFileUpload(this, mApi, desPath, file);
		upload.execute();
	}

	// This is what gets called on finishing a media piece to import
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == NEW_PICTURE) {
			// return from file upload
			if (resultCode == Activity.RESULT_OK) {
				Uri uri = null;
				if (data != null) {
					uri = data.getData();
				}
				if (uri == null && mCameraFileName != null) {
					uri = Uri.fromFile(new File(mCameraFileName));
				}
				File file = new File(mCameraFileName);

				if (uri != null) {
					UploadPicture upload = new UploadPicture(this, mApi,
							PHOTO_DIR, file);
					upload.execute();
				}
			} else {
				Log.w(TAG, "Unknown Activity Result from mediaImport: "
						+ resultCode);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mCameraFileName", mCameraFileName);
		super.onSaveInstanceState(outState);
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}

	private void startResultActivity(ArrayList<String> list) {
		Intent intent = new Intent();
		intent.putExtra("result", list);
		intent.setClass(this, VDiskResultActivity.class);
		startActivity(intent);
	}

	/**
	 * 输入框
	 * 
	 * InputDialog
	 */
	private void showInputDialog(String title, String[] hint, final int type) {
		LayoutInflater inflater = LayoutInflater.from(this);
		final View layout = inflater.inflate(R.layout.input_dialog, null);
		final EditText edt = (EditText) layout.findViewById(R.id.et_input);
		final EditText edt2 = (EditText) layout.findViewById(R.id.et_input2);

		if (type == RESTORE_VERSION || type == SAVE_TO_VDISK || type == COPY
				|| type == MOVE || type == LARGE_FILE_UPLOAD) {
			edt2.setVisibility(View.VISIBLE);
		}

		if (type == SAVE_TO_VDISK || type == GET_DOWNLOAD_LINK_BY_COPYREF) {
			if (clip != null) {
				String clipStr = clip.getText().toString();
				if (!TextUtils.isEmpty(clipStr)) {
					edt.setText(clipStr);
				}
			}
		}

		if (type == LARGE_FILE_UPLOAD) {
			edt.setText(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/");
		}

		if (hint != null) {
			edt.setHint(hint[0]);
			if (hint.length == 2) {
				edt2.setHint(hint[1]);
			}
		}

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		builder.setTitle(title);
		if (layout != null)
			builder.setView(layout);
		builder.setPositiveButton(getString(R.string.be_sure),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {

						String content = edt.getText().toString();
						String content2 = edt2.getText().toString();
						switch (type) {
						case UPLOAD_PICTURE:
							// 拍照上传
							// Take a photo and upload
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								uploadPicture(content + ".jpg");
							}
							break;

						case DOWNLOAD_FILE:
							// 下载文件
							// Download a file
							if (!TextUtils.isEmpty(content)) {
								downloadFile(content);
							} else {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							}
							break;
						case DIR_INFO:
							// 获取文件夹信息
							// Get information of directory
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								getMetaData(content, 0);
							}
							break;
						case FILE_INFO:
							// 文件信息
							// Get information of file
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								getMetaData(content, 1);
							}
							break;
						case HISTORY_VERSIONS:
							// 历史版本
							// Get history versions
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								getRevisions(content);
							}
							break;
						case RESTORE_VERSION:
							// 还原
							// Restore
							if (TextUtils.isEmpty(content)
									|| TextUtils.isEmpty(content2)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								restore(content, content2);
							}
							break;
						case SEARCH:
							// 搜索
							// Search files
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								search(content);
							}
							break;

						case SHARE:
							// 分享
							// Share files
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								shareFile(content);
							}
							break;
						case SHARE_CANCEL:
							// 取消分享
							// Cancel the sharing
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								cancelShare(content);
							}
							break;
						case THUMBNAIL:
							// 下载缩略图
							// Download the thumbnail
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								downloadThumbnail(content);
							}
							break;
						case SAVE_TO_VDISK:
							// 保存到我的微盘
							// Save the file to VDisk
							if (TextUtils.isEmpty(content)
									|| TextUtils.isEmpty(content2)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								addFromCopyRef(content, content2);
							}
							break;
						case COPY:
							// 复制
							// Copy the file
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								copy(content, content2);
							}
							break;
						case CREATE_DIR:
							// 新建文件夹
							// Create a directory
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								createFolder(content);
							}
							break;
						case DELETE:
							// 删除
							// Delete a file or a directory
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								delete(content);
							}
							break;
						case MOVE:
							// 移动
							// Move a file
							if (TextUtils.isEmpty(content)
									|| TextUtils.isEmpty(content2)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								move(content, content2);
							}
							break;
						case GET_DOWNLOAD_LINK:
							// 获取下载链接
							// Get the download link
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								getDownloadUrl(content);
							}
							break;
						case CREATE_COPYREF:
							// 创建拷贝引用
							// Create a copy reference
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								createCopyRef(content);
							}
							break;
						case GET_DOWNLOAD_LINK_BY_COPYREF:
							// 根据拷贝引用获取下载链接
							// Get download link by copy reference
							if (TextUtils.isEmpty(content)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								getDownloadUrlByCopyRef(content);
							}
							break;
						case DELTA:
							// 获取用户文件和目录操作变化记录
							// Get change records of file&directory operation
							getDelta(content);
							break;
						case LARGE_FILE_UPLOAD:
							// 大文件分段上传
							// Upload the large segmented file
							if (TextUtils.isEmpty(content)
									|| TextUtils.isEmpty(content2)) {
								showToast(getString(R.string.input_cannot_enmpty)
										+ "！");
							} else {
								uploadLargeFile(content, content2);
							}
							break;
						default:
							break;
						}
					}
				});
		builder.setNegativeButton(getString(R.string.be_cancel), null);
		final AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * 展示缩略图的对话框
	 * 
	 * AlertDialog to show the thumbnail
	 */
	private void showThumbnailDialog() {

		LayoutInflater inflater = LayoutInflater.from(this);
		View layout = inflater.inflate(R.layout.thumb_dialog, null);

		ImageView imageView = (ImageView) layout.findViewById(R.id.iv_image);
		imageView.setImageDrawable(mDrawable);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		if (layout != null)
			builder.setView(layout);

		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * 获取文件类型，判断是否是音频或者视频文件
	 * 
	 * Get file type to determine whether it is a audio or video file
	 * 
	 * @param fileName
	 * @return
	 */
	public static String getMIMEType(String fileName) {
		String type = "";
		String end = fileName.substring(fileName.lastIndexOf(".") + 1,
				fileName.length()).toLowerCase();
		if (end.equals("avi") || end.equals("mp4") || end.equals("mov")
				|| end.equals("flv") || end.equals("3gp") || end.equals("m4v")
				|| end.equals("wmv") || end.equals("rm") || end.equals("rmvb")
				|| end.equals("mkv") || end.equals("ts") || end.equals("webm")) {
			// video
			type = "video/*";
		} else if (end.equals("mid") || end.equals("midi") || end.equals("mp3")
				|| end.equals("wav") || end.equals("wma") || end.equals("amr")
				|| end.equals("ogg") || end.equals("m4a")) {
			// audio
			type = "audio/*";
		} else {
			type = "*/*";
		}
		return type;
	}

	class FunctionListAdapter extends BaseAdapter {

		private LayoutInflater mInflater;

		public FunctionListAdapter(Context context) {
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {

			return list.length;
		}

		@Override
		public String getItem(int position) {
			return list[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.function_item, null);
				holder = new ViewHolder();
				holder.name = (TextView) convertView.findViewById(R.id.tv_name);
				holder.layout = (LinearLayout) convertView
						.findViewById(R.id.ll_function);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			if (position == ACCOUNT_TITLE || position == FILES_TITLE
					|| position == FILE_EDIT_TITLE || position == SHARE_TITLE) {
				holder.layout.setBackgroundColor(getResources().getColor(
						R.color.gray));
				holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
				holder.name
						.setTextColor(getResources().getColor(R.color.white));
				convertView.setEnabled(false);
			} else {
				holder.layout.setBackgroundDrawable(null);
				holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
				holder.name
						.setTextColor(getResources().getColor(R.color.black));
				convertView.setEnabled(true);
			}

			holder.name.setText(list[position]);

			return convertView;
		}
	}

	private class ViewHolder {

		public TextView name;
		private LinearLayout layout;
	}

	public File createDirFile(String path) {
		int pos = path.lastIndexOf("/");
		String dirpath = path.substring(0, pos + 1);
		if (!dirpath.startsWith("/"))
			dirpath = "/" + dirpath;
		File f = new File(dirpath);
		if (!f.exists())
			f.mkdirs();
		return new File(path);
	}
}
