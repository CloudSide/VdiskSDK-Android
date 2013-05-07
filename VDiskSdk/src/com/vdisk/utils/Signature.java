package com.vdisk.utils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.simple.JSONObject;

import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.WeiboAccessToken;

public class Signature {

    public static final String KEY_MAC = "HmacSHA1"; 
    
    public static final long TIME_STAMP = 1*60*60; 
    
    public static String getSignature(String src, AppKeyPair appKeyPair){
       
        String rlt = "";
        try {
            BASE64Encoder encoder = new BASE64Encoder();
            rlt = encoder.encode(getHmacSHA1(src, appKeyPair));
            rlt = rlt.substring(5, 15);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rlt;
    }
    
    @SuppressWarnings("unchecked")
	public static String getWeiboHeader(AppKeyPair appKeyPair, WeiboAccessToken weiboToken){
        HashMap<String, Object> data = new HashMap<String, Object>();
        Date date = new Date();
        String seconds = String.valueOf((date.getTime()) / 1000 + TIME_STAMP);
        data.put("appkey", appKeyPair.key);
        data.put("access_token", weiboToken.mAccessToken);
        data.put("expires", seconds);
        String src = appKeyPair.key+weiboToken.mAccessToken+seconds;
        data.put("sign", getSignature(src, appKeyPair));
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(data);
        
        return jsonObject.toJSONString();
    }
    
    private static byte[] getHmacSHA1(String src, AppKeyPair appKeyPair)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Mac mac = Mac.getInstance(KEY_MAC);
        SecretKeySpec secret = new SecretKeySpec(appKeyPair.secret.getBytes("UTF-8"),
                mac.getAlgorithm());
        mac.init(secret);
        return mac.doFinal(src.getBytes());
    }
    
    
}
