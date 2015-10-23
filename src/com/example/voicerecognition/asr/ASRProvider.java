package com.example.voicerecognition.asr;

import android.os.AsyncTask;
import android.util.Log;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by skamenkovych@codeminders.com on 10/22/2015.
 */
public abstract class ASRProvider {
    private int sampleRate = 22000;
    private String language = "en_us";

    public interface Callback {
        void onResult(String transcription);
    }

    protected abstract String buildUrl();

    protected abstract Map<String, String> buildHeaders();

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public String getLanguage() {
        return language;
    }

    protected abstract String extractTranscription(String response);

    // GET for DOWNSTREAM
    private Scanner openHttpsConnection(String urlStr) {
        InputStream in = null;
        int resCode = -1;
        Log.d("ParseStarter", "dwnURL " + urlStr);

        try {
            URL url = new URL(urlStr);
            URLConnection urlConn = url.openConnection();

            if (!(urlConn instanceof HttpsURLConnection)) {
                throw new IOException("URL is not an Https URL");
            }

            HttpsURLConnection httpConn = (HttpsURLConnection) urlConn;
            httpConn.setAllowUserInteraction(false);
            // TIMEOUT is required
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod("GET");

            httpConn.connect();

            resCode = httpConn.getResponseCode();
            if (resCode == HttpsURLConnection.HTTP_OK) {
                return new Scanner(httpConn.getInputStream());
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // GET for UPSTREAM
    private Scanner openHttpsPostConnection(String urlStr, byte[] data) {
        InputStream in = null;
        int resCode = -1;
        OutputStream out = null;
        // int http_status;
        try {
            URL url = new URL(urlStr);
            URLConnection urlConn = url.openConnection();

            if (!(urlConn instanceof HttpsURLConnection)) {
                throw new IOException("URL is not an Https URL");
            }

            HttpsURLConnection httpConn = (HttpsURLConnection) urlConn;
            httpConn.setAllowUserInteraction(false);
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            httpConn.setChunkedStreamingMode(0);
            for (Map.Entry<String, String> entry : buildHeaders().entrySet()) {
                httpConn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            httpConn.connect();

            try {
                // this opens a connection, then sends POST & headers.
                out = httpConn.getOutputStream();
                // Note : if the audio is more than 15 seconds
                // dont write it to UrlConnInputStream all in one block as this
                // sample does.
                // Rather, segment the byteArray and on intermittently, sleeping
                // thread
                // supply bytes to the urlConn Stream at a rate that approaches
                // the bitrate ( =30K per sec. in this instance ).
                Log.d("ParseStarter", "IO beg on data");
                out.write(data); // one big block supplied instantly to the
                // underlying chunker wont work for duration
                // > 15 s.
                Log.d("ParseStarter", "IO fin on data");
                // do you need the trailer?
                // NOW you can look at the status.
                resCode = httpConn.getResponseCode();

                Log.d("ParseStarter", "POST OK resp "
                        + new String(httpConn.getResponseMessage().getBytes()));

                if (resCode / 100 != 2) {
                    Log.d("ParseStarter", "POST bad io ");
                }

            } catch (IOException e) {
                Log.d("ParseStarter", "FATAL " + e);

            }

            if (resCode == HttpsURLConnection.HTTP_OK) {
                Log.d("ParseStarter", "OK RESP to POST return scanner ");
                return new Scanner(httpConn.getInputStream());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void requestTrascription(final byte[] data, final Callback callback) {
        Log.d("ParseStarter", "upChan " + data.length);
        AsyncTask<Void, Integer, String> request = new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String response = "";
                Scanner inStream = openHttpsPostConnection(buildUrl(), data);
                if (inStream == null) {
                    Log.d("MainActivity", "wrong scanner");
                    return null;
                }
                inStream.hasNext();
                // process the stream and store it in StringBuilder
                String temp;
                while (inStream.hasNextLine()) {
                    temp = inStream.nextLine();
                    response += temp + "\n";
                }
                return response;
            }

            @Override
            protected void onPostExecute(String s) {
                callback.onResult(extractTranscription(s));
            }
        };
        request.execute();
    }
}
