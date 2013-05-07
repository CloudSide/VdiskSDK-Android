更新日志：

2013-03-01
将上传的取消操作放在异步执行，避免4.0以上系统抛出NetworkOnMainThreadException异常

#############################################
2013-03-29
1. I add a switch to control using https or http to upload a file. You can see the switch in "com.vdisk.net.VDiskApi.java":
// If true, use https to upload a file in the api of "/files_put/"
private static final boolean NEED_HTTPS_UPLOAD = false;
Default value is false.

2. Change the socket timeout to 1 minutes instead of 3 minutes when uploading, so that if there's some net error when uploading a file, it will response exception quickly.

3. When uploading a file in 3G, the upload process may get stuck because of the unstabitily of 3G network, so you may wait a very long time to get an exception.
I have changed the strategy of uploading in sdk. If upload process get stuck for 20 seconds, sdk will throw an IOException, and my sdk also will retry 3 times, so you needn't to wait a long time and will get an exception faster than before.

4. I added a switch in my sdk, you can see it in "com.vdisk.utils.Logger.java".
public static boolean DEBUG_MODE = true; 
If DEBUG_MODE == true, every api request information will be wrote to local file(sdcard/vdisk/api.log), else, we won't write to the local file.
If you meet any server exceptions when accessing vdisk apis, you can send the "api.log" to me, our api colleagues will analyze the log file and give you a reply.

##############################################
2013-04-01
1. I added a RetryHandler to control the retry strategy.
2. If request method is "Post" or "Put", my RetryHandler won't retry.
3. Optimize logs output.

##############################################
2013-04-10
将代码中的汉语注释翻译成英文

##############################################
2013-04-16
1. Change the extension of the temporary download file from ".temp" to ".vdisktemp", so that we can distinguish vdisk temp file from some file originally named with "*.temp".
2. Delete the temporary download file when the local file length does not equal with the file length on server side after download complete. 

##############################################
2013-04-17
1. Add "Config.java" to global control the upload setting and debug mode.
2. Add "SettingActivity.java" in sdk example to show an UI for testers to use.

##############################################
2013-05-06
上传过程中，对Long time no response的条件进行了细分
