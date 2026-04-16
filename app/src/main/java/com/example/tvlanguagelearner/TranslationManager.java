package com.example.tvlanguagelearner;

import android.os.AsyncTask;
import android.util.Log;

import com.squareup.okhttp3.OkHttpClient;
import com.squareup.okhttp3.Request;
import com.squareup.okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Translation manager supporting multiple translation APIs
 */
public class TranslationManager {
    private static final String TAG = "TranslationManager";

    // Baidu Translation API credentials (you need to register at baidu.com)
    private static final String BAIDU_APP_ID = "YOUR_BAIDU_APP_ID";
    private static final String BAIDU_SECRET_KEY = "YOUR_BAIDU_SECRET_KEY";
    private static final String BAIDU_TRANSLATE_URL = "http://api.fanyi.baidu.com/api/trans/vip/translate";

    // Google Translation API key (you need to get from Google Cloud Console)
    private static final String GOOGLE_API_KEY = "YOUR_GOOGLE_API_KEY";
    private static final String GOOGLE_TRANSLATE_URL = "https://translate.googleapis.com/language/translate/v2";

    private OkHttpClient client;
    private TranslationCallback callback;

    public interface TranslationCallback {
        void onTranslationSuccess(String result);
        void onTranslationFailure(String error);
    }

    public TranslationManager(TranslationCallback callback) {
        this.callback = callback;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Translate text from source language to target language
     * @param text Text to translate
     * @param from Source language code (e.g., "en", "zh")
     * @param to Target language code (e.g., "en", "zh")
     * @param apiType Which API to use ("baidu" or "google")
     */
    public void translate(String text, String from, String to, String apiType) {
        if (apiType.equals("baidu")) {
            new BaiduTranslateTask().execute(text, from, to);
        } else if (apiType.equals("google")) {
            new GoogleTranslateTask().execute(text, from, to);
        }
    }

    /**
     * Translate English to Chinese using Baidu API
     */
    public void translateEnToZh(String text) {
        translate(text, "en", "zh", "baidu");
    }

    /**
     * Translate Chinese to English using Baidu API
     */
    public void translateZhToEn(String text) {
        translate(text, "zh", "en", "baidu");
    }

    private class BaiduTranslateTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            String from = params[1];
            String to = params[2];

            try {
                String salt = String.valueOf(System.currentTimeMillis());
                String sign = generateBaiduSign(text, salt);

                String url = BAIDU_TRANSLATE_URL + "?q=" + URLEncoder.encode(text, "UTF-8") +
                        "&from=" + from + "&to=" + to + "&appid=" + BAIDU_APP_ID +
                        "&salt=" + salt + "&sign=" + sign;

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    return parseBaiduResponse(responseBody);
                }
            } catch (Exception e) {
                Log.e(TAG, "Baidu translation error", e);
                return "Error: " + e.getMessage();
            }
            return "Translation failed";
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("Error:")) {
                callback.onTranslationFailure(result);
            } else {
                callback.onTranslationSuccess(result);
            }
        }
    }

    private class GoogleTranslateTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            String from = params[1];
            String to = params[2];

            try {
                String url = GOOGLE_TRANSLATE_URL + "?key=" + GOOGLE_API_KEY +
                        "&q=" + URLEncoder.encode(text, "UTF-8") +
                        "&source=" + from + "&target=" + to;

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    return parseGoogleResponse(responseBody);
                }
            } catch (Exception e) {
                Log.e(TAG, "Google translation error", e);
                return "Error: " + e.getMessage();
            }
            return "Translation failed";
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("Error:")) {
                callback.onTranslationFailure(result);
            } else {
                callback.onTranslationSuccess(result);
            }
        }
    }

    private String generateBaiduSign(String text, String salt) {
        String sign = BAIDU_APP_ID + text + salt + BAIDU_SECRET_KEY;
        return md5(sign);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String parseBaiduResponse(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        JSONArray resultArray = json.getJSONArray("trans_result");
        if (resultArray.length() > 0) {
            JSONObject result = resultArray.getJSONObject(0);
            return result.getString("dst");
        }
        return "No translation result";
    }

    private String parseGoogleResponse(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        JSONArray data = json.getJSONArray("data");
        if (data.length() > 0) {
            JSONArray translations = data.getJSONObject(0).getJSONArray("translations");
            if (translations.length() > 0) {
                return translations.getJSONObject(0).getString("translatedText");
            }
        }
        return "No translation result";
    }
}