package com.example.voicerecognition;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by skamenkovych@codeminders.com on 10/19/2015.
 */
public class GoogleASRProvider extends ASRProvider {

    private static final String ROOT = "https://www.google.com/speech-api/v2/recognize";
    private static final String UP_P1 = "?output=json&lang=";
    private static final String UP_P2 = "&key=";

    @Override
    protected String buildUrl() {
        return ROOT + UP_P1 + getLanguage() + UP_P2 + Constants.GOOGLE_ASR_API_KEY;
    }

    @Override
    protected Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "audio/l16; rate=" + getSampleRate() + ";");
        return headers;
    }

    @Override
    protected String extractTranscription(String response) {
        if (null == response) {
            return null;
        }
        Gson gson = new Gson();
        Response alternatives;
        try {
            alternatives = gson.fromJson(response, Response.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
        return Response.getTranscription(alternatives);
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
