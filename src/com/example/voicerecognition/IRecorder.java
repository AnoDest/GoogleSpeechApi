package com.example.voicerecognition;

/**
 * Created by skamenkovych@codeminders.com on 10/16/2015.
 */
public interface IRecorder {
    void start(String fileName);
    void stop();
    boolean isRecording();
}
