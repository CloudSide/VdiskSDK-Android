package com.vdisk.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 此数据库表中有两个字段，file_id 和 file_obj. file_id = MD5(上传文件本地路径
 * +上传文件的微盘目标路径)，用来标识待上传的文件; file_obj表示文件对象序列化后的形式
 * 
 * There are two columns in this table in database: file_id & file_obj. file_id
 * = MD5(Local path of file to upload + target VDisk path of file to upload), to
 * identify the file to upload; file_obj, the form of serialized file object.
 * 
 * @author Kevin
 * 
 */
public class VDiskDB extends SQLiteOpenHelper {

	@SuppressWarnings("unused")
	private static Context sContext;

	private final static String DB_NAME = "vdiskdb";

	private final static int VERSION = 1;

	private static VDiskDB instance = null;

	private SQLiteDatabase db = null;

	private final String UPLOAD_TABLE = "vdisk_upload";
	private final String UPLOAD_FILE_ID = "file_id";
	private final String UPLOAD_FILE_OBJECT = "file_obj";

	@SuppressWarnings("static-access")
	private VDiskDB(Context context) {
		super(context, DB_NAME, null, VERSION);
		this.sContext = context;

		try {
			db = this.getWritableDatabase();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static VDiskDB getInstance(Context ctx) {
		if (instance == null) {
			return instance = new VDiskDB(ctx);
		}
		return instance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String sql = "CREATE TABLE IF NOT EXISTS " + UPLOAD_TABLE + " ("
				+ "_id" + " Integer primary key autoincrement, "
				+ UPLOAD_FILE_ID + " TEXT UNIQUE, " + UPLOAD_FILE_OBJECT
				+ " TEXT )";
		db.execSQL(sql);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		String sql = "DROP TABLE IF EXISTS " + UPLOAD_TABLE;
		db.execSQL(sql);
		onCreate(db);
	}

	/**
	 * 更新上传文件分段信息
	 * 
	 * Update segment information of upload file.
	 * 
	 * @param fileId
	 * @param serStr
	 * @return
	 */
	public boolean updateUploadFileInfo(String fileId, String serStr) {
		if (readUploadFileInfo(fileId) == null) {
			ContentValues c = new ContentValues();
			c.put(UPLOAD_FILE_ID, fileId);
			c.put(UPLOAD_FILE_OBJECT, serStr);

			long rownum = db.insert(UPLOAD_TABLE, null, c);
			if (rownum == -1) {
				return false;
			}

			return true;
		} else {
			ContentValues c = new ContentValues();
			c.put(UPLOAD_FILE_OBJECT, serStr);

			db.update(UPLOAD_TABLE, c, UPLOAD_FILE_ID + " =? ",
					new String[] { fileId });
			return true;
		}
	}

	/**
	 * 删除上传文件分段信息
	 * 
	 * Delete segment information of upload file.
	 * 
	 * @param fileId
	 */
	public void deleteUploadFileInfo(String fileId) {
		String sql = "DELETE FROM " + UPLOAD_TABLE + " WHERE " + UPLOAD_FILE_ID
				+ " = ?";

		String[] args = new String[] { fileId };

		db.execSQL(sql, args);
	}

	/**
	 * 读取上传文件分段信息
	 * 
	 * Read segment information of upload file.
	 * 
	 * @param fileId
	 * @return
	 */
	public String readUploadFileInfo(String fileId) {
		Cursor cursor = db.query(UPLOAD_TABLE,
				new String[] { UPLOAD_FILE_OBJECT }, UPLOAD_FILE_ID + " = ?",
				new String[] { fileId }, null, null, null);

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				String serStr = cursor.getString(0);
				cursor.close();
				return serStr;
			}

			cursor.close();
		}

		return null;
	}

}
