/*
 * Copyright 2020 Cyril Diagne. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.arcopypaste.research.prototypes.text;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import cz.msebera.android.httpclient.Header;

interface DesktopHTTPClientCallback {
    void onSuccess(Bitmap screenshot);
    void onError(String err);
}

public class DesktopHTTPClient {
    private static final String BASE_URL = "http://192.168.1.29:3000";
    private static final String TAG = DesktopHTTPClient.class.getSimpleName();

    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void get(String url, AsyncHttpResponseHandler responseHandler) {
        client.get(url, responseHandler);
    }

    public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.post(url, params, responseHandler);
    }

    public static void setPosition(double x, double y) {
        String url = BASE_URL + "/position?x=" + String.valueOf(x) + "&y=" + String.valueOf(y);
        get(url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                //Log.d("DEBUG", "k");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, error.toString());
            }
        });
    }

    public static void setText(String text) {
        RequestParams params = new RequestParams();
        params.put("text", text);
        post(BASE_URL + "/text", params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                //Log.d("DEBUG", "k");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, error.toString());
            }
        });
    }

    public static void paste() {
        String url = BASE_URL + "/paste";
        get(url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                //Log.d("DEBUG", "k");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, error.toString());
            }
        });
    }

    public static Bitmap getScreenshot(DesktopHTTPClientCallback cb) {
        get(BASE_URL + "/screen", new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                Log.d(TAG, "k");
                Bitmap bmp = BitmapFactory.decodeByteArray(response, 0, response.length);
                Log.d(TAG, String.valueOf(bmp.getWidth()));
                cb.onSuccess(bmp);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, error.toString());
                cb.onError(error.toString());
            }
        });
        return null;
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }
}
