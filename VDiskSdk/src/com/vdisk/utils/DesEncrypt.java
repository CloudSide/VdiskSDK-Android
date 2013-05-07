package com.vdisk.utils;

import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import android.text.TextUtils;

import com.vdisk.net.session.AppKeyPair;

/**
 * DES加密解密算法，此方法用于将微盘token加密后保存到SharedPreferences中.
 * 使用时从SharedPreferences读取出来，通过此方法解密后使用.
 * 
 * DES encryption and decryption algorithm. This method is used to encrypt VDisk
 * token and save it in SharedPreferences. Read it from SharedPreferences and
 * decrypt it to use.
 * 
 * @author sina
 * 
 */
public class DesEncrypt {
	private Key key;
	private String PASSWORD;

	public DesEncrypt(String IMEI, AppKeyPair appKeyPair) {
		this.PASSWORD = "com.sina.vdisk.security.password.d7af3082d815945ff47ae58647bd9436"
				+ IMEI + appKeyPair.key + appKeyPair.secret;
	}

	protected void setKey(String strKey) {
		KeyGenerator _generator;
		try {
			_generator = KeyGenerator.getInstance("DES");
			_generator.init(new SecureRandom(strKey.getBytes()));
			KeySpec keySpec = new DESKeySpec(strKey.getBytes());
			SecretKeyFactory factory = SecretKeyFactory.getInstance("DES");
			Key key = factory.generateSecret(keySpec);
			this.key = key;
			_generator = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getEncString(String strMing) {
		String strMi = "";
		try {
			return byte2hex(getEncCode(strMing.getBytes()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return strMi;
	}

	public String getDesString(String strMi) {
		String strMing = "";
		try {
			if (!TextUtils.isEmpty(strMi)) {
				return new String(getDesCode(hex2byte(strMi.getBytes())));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return strMing;
	}

	private void validateKey() {
		if (this.key == null)
			setKey(PASSWORD);
	}

	private byte[] getEncCode(byte[] byteS) {
		Cipher cipher;
		byte[] byteFina = (byte[]) null;
		try {
			validateKey();
			cipher = Cipher.getInstance("DES");
			cipher.init(1, this.key);
			byteFina = cipher.doFinal(byteS);
		} catch (Exception e) {
			e.printStackTrace();

			cipher = null;
		} finally {
			cipher = null;
		}
		return byteFina;
	}

	private byte[] getDesCode(byte[] byteD) {
		Cipher cipher;
		byte[] byteFina = (byte[]) null;
		try {
			validateKey();
			cipher = Cipher.getInstance("DES");
			cipher.init(2, this.key);
			byteFina = cipher.doFinal(byteD);
		} catch (Exception e) {
			e.printStackTrace();

			cipher = null;
		} finally {
			cipher = null;
		}
		return byteFina;
	}

	public static String byte2hex(byte[] b) {
		String hs = "";
		String stmp = "";
		for (int n = 0; n < b.length; ++n) {
			stmp = Integer.toHexString(b[n] & 0xFF);
			if (stmp.length() == 1)
				hs = hs + "0" + stmp;
			else
				hs = hs + stmp;
		}
		return hs.toUpperCase();
	}

	public static byte[] hex2byte(byte[] b) {
		if (b.length % 2 != 0)
			throw new IllegalArgumentException("length error");
		byte[] b2 = new byte[b.length / 2];
		for (int n = 0; n < b.length; n += 2) {
			String item = new String(b, n, 2);

			b2[(n / 2)] = (byte) Integer.parseInt(item, 16);
		}
		return b2;
	}

}