package com.example.voicerecognition;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
    public static final String language = "en_us";

    // Name of the sound file (.flac)
    public static final String fileName = Environment.getExternalStorageDirectory() + "/recording.flac";

    // Constants
    private int mErrorCode = -1;
    private static final int DIALOG_RECORDING_ERROR = 0;

    private static final String TAG = "VoiceRecognizer";
    // Rate of the recorded sound file
    int sampleRate;
    // Recorder instance
    private FLACRecorder mRecorder;

    // Output for Google answer
    TextView txtView;
    Button recordButton, stopButton, listenButton;

    // Handler used for sending request to Google API
    Handler handler = new Handler();

    // Recording callbacks
    private Handler mRecordingHandler = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message m) {
            switch (m.what) {
            case FLACRecorderImpl.MSG_AMPLITUDES:
                FLACRecorderImpl.Amplitudes amp = (FLACRecorderImpl.Amplitudes) m.obj;

                break;

            case FLACRecorderImpl.MSG_OK:
                // Ignore
                break;

            case FLACRecorder.MSG_END_OF_RECORDING:

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

        mRecorder = new FLACRecorder(this, mRecordingHandler);

    }

    /***************************************************************************************************************
     * Method related to recording in FLAC file
     */

    public void recordButton(View v) {

        //mRecorder.start(fileName);
        //
        //txtView.setText("");
        //recordButton.setEnabled(false);
        //stopButton.setEnabled(true);
        //Toast.makeText(getApplicationContext(), "Recording...",
        //        Toast.LENGTH_LONG).show();

    }

    /***************************************************************************************************************
     * Method that stops recording
     */

    public void stopRecording(View v) {
        //
        //Toast.makeText(getApplicationContext(), "Loading...", Toast.LENGTH_LONG)
        //        .show();
        //recordButton.setEnabled(true);
        //listenButton.setEnabled(true);
        //
        //sampleRate = mRecorder.mFLACRecorder.getSampleRate();
        //
        //getTranscription(sampleRate);
        //mRecorder.stop();

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

    public void run1(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            VoiceTranscriptor transcriptor = new VoiceTranscriptor();
            transcriptor.extractVoiceFromVideo(Environment.getExternalStorageDirectory() + "/" + "rec.mp4", 1.5f, new VoiceTranscriptor.ExtractionCallbacks() {
                @Override
                public void onResult(VoiceTranscriptor transcriptor, String path) {
                    transcriptor.requestTranscription(path, new NuanceASRProvider(), new ASRProvider.Callback() {
                        @Override
                        public void onResult(String transcription) {
                            Log.i(TAG, "rec nuance: " + transcription);
                        }
                    });
                    //transcriptor.requestTranscription(path, new GoogleASRProvider(), new ASRProvider.Callback() {
                    //    @Override
                    //    public void onResult(String transcription) {
                    //        Log.i(TAG, "rec google: " + transcription);
                    //    }
                    //});
                }

                @Override
                public void onError(String error) {
                    Log.i(TAG, "Error to extract: rec. " + error);
                }

                @Override
                public void onProgressChanged(int progress) {
                    //Log.i(TAG, "rec extracted: " + progress);
                }
            });
            transcriptor.extractVoiceFromVideo(Environment.getExternalStorageDirectory() + "/" + "rec2.mp4", -1, new VoiceTranscriptor.ExtractionCallbacks() {
                @Override
                public void onResult(VoiceTranscriptor transcriptor, String path) {
                    transcriptor.requestTranscription(path, new NuanceASRProvider(), new ASRProvider.Callback() {
                        @Override
                        public void onResult(String transcription) {
                            Log.i(TAG, "rec2 nuance: " + transcription);
                        }
                    });
                    //transcriptor.requestTranscription(path, new GoogleASRProvider(), new ASRProvider.Callback() {
                    //    @Override
                    //    public void onResult(String transcription) {
                    //        Log.i(TAG, "rec2 google: " + transcription);
                    //    }
                    //});
                }

                @Override
                public void onError(String error) {
                    Log.i(TAG, "Error to extract: rec2. " + error);
                }

                @Override
                public void onProgressChanged(int progress) {
                    //Log.i(TAG, "rec2 extracted: " + progress);
                }
            });
        }
    }

}
