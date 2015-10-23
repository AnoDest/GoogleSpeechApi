package com.example.voicerecognition.asr;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by skamenkovych@codeminders.com on 10/22/2015.
 */
public class NuanceASRProvider extends ASRProvider {

    private static final String ROOT = "https://dictation.nuancemobility.net:443/NMDPAsrCmdServlet/dictation";
    private static final String UP_P1 = "?appId=";
    private static final String UP_P2 = "&appKey=";
    private static final String UP_P3 = "&id=";

    public static float DURATION_LIMIT = 300f;

    @Override
    protected String buildUrl() {
        return ROOT + UP_P1 + Constants.NUANCE_ASR_API_ID + UP_P2 + Constants.NUANCE_ASR_API_KEY + UP_P3 + Constants.NUANCE_ASR_ID;
    }

    @Override
    protected Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "audio/x-wav;codec=pcm;bit=16;rate=" + getSampleRate());
        headers.put("Accept-Language", getLanguage());
        headers.put("Transfer-Encoding", "chunked");
        headers.put("Accept", "text/plain");
        headers.put("Accept-Topic", "Dictation");
        return headers;
    }

    @Override
    protected String extractTranscription(String response) {
        if (null == response) {
            return null;
        }
        String[] split = response.split("\n");
        if (split.length > 0) {
            return split[0];
        } else {
            return null;
        }
    }
}
