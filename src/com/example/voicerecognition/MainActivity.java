package com.example.voicerecognition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

import com.example.voicerecognition.R;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;

import android.app.Activity;
import android.content.Context;
import android.database.CursorJoiner.Result;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Serhii Kamenkovych Speech API v2
 * @author Lucas Santana Speech API v1
 * 
 *         Activity responsible for recording and sending sound to Google API
 * 
 */

public class MainActivity extends Activity {

    // Language spoken
    // Obs: It requires Google codes: English(en_us), Portuguese(pt_br), Spanish
    // (es_es), etc
    String language = "en_us";

    // Name of the sound file (.flac)
    String fileName = Environment.getExternalStorageDirectory()
            + "/recording.flac";

    // URL for Google API
    String root = "https://www.google.com/speech-api/v2/recognize";
    String up_p1 = "?output=json&lang=" + language + "&key=" + Constants.API_KEY;

    // Constants
    private int mErrorCode = -1;
    private static final int DIALOG_RECORDING_ERROR = 0;

    private static final String TAG = "VoiceRecognizer";
    // Rate of the recorded sound file
    int sampleRate;
    // Recorder instance
    private Recorder mRecorder;

    // Output for Google answer
    TextView txtView;
    Button recordButton, stopButton, listenButton;

    // Handler used for sending request to Google API
    Handler handler = new Handler();

    // Recording callbacks
    private Handler mRecordingHandler = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message m) {
            switch (m.what) {
            case FLACRecorder.MSG_AMPLITUDES:
                FLACRecorder.Amplitudes amp = (FLACRecorder.Amplitudes) m.obj;

                break;

            case FLACRecorder.MSG_OK:
                // Ignore
                break;

            case Recorder.MSG_END_OF_RECORDING:

                break;

            default:
                mRecorder.stop();
                mErrorCode = m.what;
                showDialog(DIALOG_RECORDING_ERROR);
                break;
            }

            return true;
        }
    });

    // UPSTREAM channel. its servicing a thread and should have its own handler
    Handler messageHandler2 = new Handler() {

        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case 1: // GET DOWNSTREAM json
                Log.d("ParseStarter", msg.getData().getString("post"));
                break;
            case 2:
                Log.d("ParseStarter", msg.getData().getString("post"));
                final String f_msg = msg.getData().getString("post");
                final StringBuilder builder = new StringBuilder(f_msg);
                Gson gson = new Gson();
                try {
                    Response response = gson.fromJson(f_msg, Response.class);
                    String best = Response.getTranscription(response);
                    if (best != null) {
                        builder.append("\n\nBest: ").append(best);
                    }
                } catch (JsonSyntaxException e) {
                    Log.d(TAG, "Couldn't parse response");
                }
                handler.post(new Runnable() { // This thread runs in the UI
                    // TREATMENT FOR GOOGLE RESPONSE
                    @Override
                    public void run() {
                        txtView.setText(builder.toString());
                    }
                });
                break;
            }

        }
    }; // UPstream handler end

    /**************************************************************************************************************
     * Implementation
     **/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtView = (TextView) this.findViewById(R.id.txtView);
        recordButton = (Button) this.findViewById(R.id.record);
        stopButton = (Button) this.findViewById(R.id.stop);
        stopButton.setEnabled(false);
        listenButton = (Button) this.findViewById(R.id.listen);
        listenButton.setEnabled(false);

        mRecorder = new Recorder(this, mRecordingHandler);

    }

    /***************************************************************************************************************
     * Method related to recording in FLAC file
     */

    public void recordButton(View v) {

        mRecorder.start(fileName);

        txtView.setText("");
        recordButton.setEnabled(false);
        stopButton.setEnabled(true);
        Toast.makeText(getApplicationContext(), "Recording...",
                Toast.LENGTH_LONG).show();

    }

    /***************************************************************************************************************
     * Method that stops recording
     */

    public void stopRecording(View v) {

        Toast.makeText(getApplicationContext(), "Loading...", Toast.LENGTH_LONG)
                .show();
        recordButton.setEnabled(true);
        listenButton.setEnabled(true);

        sampleRate = mRecorder.mFLACRecorder.getSampleRate();
        getTranscription(sampleRate);
        mRecorder.stop();

    }

    /***************************************************************************************************************
     * Method that listens to recording
     */
    public void listenRecord(View v) {
        Context context = this;

        FLACPlayer mFlacPlayer = new FLACPlayer(context, fileName);
        mFlacPlayer.start();

    }

    /**************************************************************************************************************
     * Method related to Google Voice Recognition
     **/

    public void getTranscription(int sampleRate) {

        File myfil = new File(fileName);
        if (!myfil.canRead())
            Log.d("ParseStarter", "FATAL no read access");

        // UP chan, process the audio byteStream for interface to UrlConnection
        // using 'chunked-encoding'
        FileInputStream fis;
        try {
            fis = new FileInputStream(myfil);
            FileChannel fc = fis.getChannel(); // Get the file's size and then
                                               // map it into memory
            int sz = (int) fc.size();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
            byte[] data2 = new byte[bb.remaining()];
            Log.d("ParseStarter", "mapfil " + sz + " " + bb.remaining());
            bb.get(data2);
            // conform to the interface from the curl examples on full-duplex
            // calls
            // see curl examples full-duplex for more on 'PAIR'. Just a globally
            // uniq value typ=long->String.
            // API KEY value is part of value in UP_URL_p2
            upChannel(root + up_p1, messageHandler2, data2);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void upChannel(String urlStr, final Handler messageHandler,
            byte[] arg3) {

        final String murl = urlStr;
        final byte[] mdata = arg3;
        Log.d("ParseStarter", "upChan " + mdata.length);
        new Thread() {
            public void run() {
                String response = "";
                Message msg = Message.obtain();
                msg.what = 2;
                Scanner inStream = openHttpsPostConnection(murl, mdata);
                if (inStream == null) {
                    Log.d("MainActivity", "wrong scanner");
                    return;
                }
                inStream.hasNext();
                // process the stream and store it in StringBuilder
                String temp;
                while (inStream.hasNextLine()) {
                    temp = inStream.nextLine();
                    if (temp.length() > 20) { // filter empty results
                        response += temp;
                    }
                }
                Log.d("ParseStarter", "POST resp " + response.length());
                Bundle b = new Bundle();
                b.putString("post", response);
                msg.setData(b);
                // in.close(); // mind the resources
                messageHandler.sendMessage(msg);

            }
        }.start();

    }

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
        byte[] mextrad = data;
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
            httpConn.setRequestProperty("Content-Type", "audio/x-flac; rate="
                    + sampleRate + ";");
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
                out.write(mextrad); // one big block supplied instantly to the
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

    static class Response {
        public ArrayList<LinkedTreeMap<String, Object>> result;
        
        public static String getTranscription(Response response) {
            String transcription = null;
            try {
                if (!response.result.isEmpty()) {
                    ArrayList<LinkedTreeMap<String, Object>> transcriptions = (ArrayList<LinkedTreeMap<String, Object>>) response.result.get(0).get("alternative");
                    if (!transcriptions.isEmpty()) {
                        transcription = (String) transcriptions.get(0).get("transcript");
                    }
                }
            } catch (Exception e) {
            }
            return transcription;
        }
    }
}
