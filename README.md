[![](http://vdisk.me/static/images/vi/logo/32x32.png)](#) VdiskSDK-Android
============

关于微盘OPENAPI、SDK使用以及技术问题请联系: [@俺是不是吐槽太多了](http://weibo.com/yinkai1205) [@王彤](http://weibo.com/lilytong) [@edgardo_赵鹏](http://weibo.com/zhaopengedgardo) [@一个开发者](http://weibo.com/smcz) [@Littlebox222](http://weibo.com/littlebox222)

微盘Android SDK开发者交流群：240235926

微盘OpenAPI、全平台SDK交流群：134719337， 162285095

邮箱: [cloudside@sina.cn](mailto:cloudside@sina.cn)

RESTful API文档:
[![](http://vdisk.me/static/images/vi/icon/16x16.png)](http://vdisk.weibo.com/developers/index.php?module=api&action=apidoc)
http://vdisk.weibo.com/developers/index.php?module=api&action=apidoc

[![](http://service.t.sina.com.cn/widget/qmd/1935603843/02781ba4/4.png)](http://weibo.com/zhaopengedgardo)

[![](http://service.t.sina.com.cn/widget/qmd/1656360925/02781ba4/4.png)](http://weibo.com/smcz)

[![](http://service.t.sina.com.cn/widget/qmd/1727404360/02781ba4/4.png)](http://weibo.com/yinkai1205)

[![](http://service.t.sina.com.cn/widget/qmd/1757517965/02781ba4/4.png)](http://weibo.com/lilytong)

---
运行示例代码
===
简要描述一下使用Eclipse运行Example的步骤：

1. 请先前往 [微盘开发者中心](http://vdisk.weibo.com/developers/) 注册为微盘开发者, 并创建应用；

2. Clone或下载Github仓库中的VDiskSdk及VDiskSdk_Example两个项目，并导入Eclipse；

3. 确保设置VDiskSdk为VDiskSdk_Example的依赖项目（Library）;

4. 进入工程VDiskSdk_Example/com.vdisk.android.example.OAuthActivity，根据应用信息修改常量CONSUMER_KEY(App Key)，CONSUMER_SECRET(App Secret)，REDIRECT_URL(应用回调地址)；

5. 编译并运行工程VDiskSdk_Example。

-----
Usage
=====

- 认证相关
----------

- 实例化VDiskAuthSession 

```java 

VDiskAuthSession session;
AppKeyPair appKeyPair = new AppKeyPair(CONSUMER_KEY, CONSUMER_SECRET);
session = VDiskAuthSession.getInstance(this,appKeyPair,AccessType.APP_FOLDER);

```

- 使用新浪微博认证登录

```java

WeiboAccessToken weiboToken = new WeiboAccessToken();
weiboToken.mAccessToken = OAuthActivity.WEIBO_ACCESS_TOKEN;
session.enabledAndSetWeiboAccessToken(weiboToken);
session.authorize(OAuthActivity.this, OAuthActivity.this);

```

- 使用微盘认证登录

```java

session.setRedirectUrl(REDIRECT_URL);
session.authorize(OAuthActivity.this, OAuthActivity.this);

```

- 登陆认证的回调方法

```java

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

```

- 用户信息相关接口
----------------

- 初始化VDiskAPI

```java

VDiskAPI<VDiskAuthSession> mApi;
mApi = new VDiskAPI<VDiskAuthSession>(session);

Account account = mApi.accountInfo();
account.quota; //用户微盘总空间
Account.consumed; //用户微盘已使用空间

```

- 上传下载模块
----------------

- 小文件上传

```java

UploadRequest mRequest;
mRequest = mApi.putFileOverwriteRequest(path, fis, mFile.length(),
  				new ProgressListener() {
						@Override
						public long progressInterval() {
							// 在这里设置进度更新间隔，缺省为0.5秒
							return super.progressInterval();
						}

						@Override
						public void onProgress(long bytes, long total) {
                        	// 在这里可以处理进度更新
 							// 参数bytes：当前已完成上传的字节数
 							// 参数total：总字节数
 							// 例如 publishProgress(bytes);
						}
					});

```

- 大文件分段上传

```java

ComplexUploadHandler handler = new ComplexUploadHandler(mContext) {
				@Override
				public void onProgress(long bytes, long total) {
					publishProgress(bytes);
				}

				@Override
				public void startedWithStatus(ComplexUploadStatus status) {
					switch (status) {
					case ComplexUploadStatusLocateHost:
						Log.d(TAG, "Getting the nearest host...");
						break;
					case ComplexUploadStatusCreateFileSHA1:
						Log.d(TAG, "Creating the sha1 of file");
						break;
					case ComplexUploadStatusInitialize:
						Log.d(TAG, "Signing each segment of file...");
						break;
					case ComplexUploadStatusCreateFileMD5s:
						Log.d(TAG, "Creating each segment's md5...");
						break;
					case ComplexUploadStatusUploading:
						Log.d(TAG, "Uploading one segment...");
						break;
					case ComplexUploadStatusMerging:
						Log.d(TAG, "File Merging...");
						break;
					default:
						break;
					}
				}

				@Override
				public void finishedWithMetadata(Entry metadata) {
					Log.d(TAG, "Upload success : " + metadata.fileName());
				}
			};

			mApi.putLargeFileOverwriteRequest(mSrcPath, desPath,
					mFile.length(), handler);

```

- 文件下载

```java

File file = mApi.createDownloadDirFile(mTargetPath);
try {
  mFos = new FileOutputStream(file, true);
	} catch (FileNotFoundException e) {
	mErrorMsg = "Couldn't create a local file to store the file";
	return false;
}

VDiskFileInfo info = mApi.getFile(mPath, null, mFos, file,
  				new ProgressListener() {

						@Override
						public long progressInterval() {
 							// 在这里设置进度更新间隔，缺省为0.5秒
							return super.progressInterval();
						}

						@Override
						public void onProgress(long bytes, long total) {
 						// 在这里可以处理进度更新
 						// 参数bytes：当前已完成下载的字节数
 						// 参数total：总字节数
 						// 例如
 								mFileLen = total;
 								publishProgress(bytes);
						}
					});

```

- 文件、目录操作相关接口
------------------------

- 下载缩略图

```java

mApi.getThumbnail(path, mFos, ThumbSize.ICON_640x480, null);

```

- 获取文件夹下的目录信息

```java

Entry metadata = mApi.metadata(path, null, true, false);
List<Entry> contents = metadata.contents;

```

- 获取文件详细信息

```java

Entry metadata = mApi.metadata(path, null, true, false);
metadata.fileName();// 文件名
metadata.size; // 文件大小
metadata.modified; //	文件修改时间
metadata.path;	//文件微盘路径

```

- 获取文件的历史版本

```java

List<Entry> contents = mApi.revisions(path, -1);

```

- 搜索

```java

List<Entry> result = mApi.search("/", keyword, -1, false);

```

- 获取文件的下载链接

```java

VDiskLink media = mApi.media(path, false);
media.url; // 文件的下载地址
media.expires; //  下载地址的过期时间

```

- 获取用户文件和目录的操作变化记录

```java

DeltaPage<Entry> deltaPage = mApi.delta(cursor);

```

- 文件编辑相关接口
------------------

- 复制

```java

Entry metadata = mApi.copy(fromPath, toPath);

```

- 新建文件夹

```java

Entry metaData = mApi.createFolder(path);

```

- 删除

```java

Entry metaData = mApi.delete(path);

```

- 移动

```java

Entry metadata = mApi.move(fromPath, toPath);

```

- 还原

```java

Entry metadata = mApi.restore(path, revision);

```

- 分享相关接口
--------------

- 分享

```java

String shareLink = mApi.share(path);

```

- 取消分享

```java

Entry metaData = mApi.cancelShare(path);

```

- 创建拷贝引用

```java

CreatedCopyRef createCopyRef = mApi.createCopyRef(sourcePath);

```

- 通过拷贝引用保存到微盘

```java

Entry entry = mApi.addFromCopyRef(sourceCopyRef, targetPath);

```

- 根据拷贝引用获取下载链接

```java

VDiskLink link = mApi.getLinkByCopyRef(sourceCopyRef);

```




