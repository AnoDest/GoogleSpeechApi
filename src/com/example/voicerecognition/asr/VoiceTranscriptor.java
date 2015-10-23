package com.example.voicerecognition.asr;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import com.example.voicerecognition.asr.dsp.Resampler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by skamenkovych@codeminders.com on 10/22/2015.
 */
public final class VoiceTranscriptor {

    private static final String TAG = VoiceTranscriptor.class.getSimpleName();

    public interface ExtractionCallbacks {
        /**
         *
         * @param transcriptor
         * @param path path to raw PCM file
         */
        void onResult(VoiceTranscriptor transcriptor, String path);

        /**
         *
         * @param error error message
         */
        void onError(String error);

        /**
         *
         * @param progress progress state [0; 100]
         */
        void onProgressChanged(int progress);
    }

    /**
     * All work will be executed asynchronously with results in callback thread
     * @param videoPath path to video file
     * @param secondsLimit specifies the limit of last sample time that will be extracted in seconds, -1 to extract all
     *                     min value 0.5 s.
     * @param callbacks extraction callbacks where results will be provided to
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void extractVoiceFromVideo(final String videoPath, float secondsLimit, final ExtractionCallbacks callbacks) {
        callbacks.onProgressChanged(0);
        final MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(videoPath);
            Log.i(TAG, videoPath);
        } catch (IOException e) {
            e.printStackTrace();
            callbacks.onError("Couldn't set data source");
            return;
        }
        int numTracks = extractor.getTrackCount();
        MediaCodec codec = null;
        MediaFormat audioFormat = null;
        long duration = 0;
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            StringBuilder builder = new StringBuilder();
            builder.append(mime);
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                builder.append(" ").append(MediaFormat.KEY_SAMPLE_RATE);
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                builder.append(sampleRate);
                if (mime.startsWith("audio")) {
                    extractor.selectTrack(i);

                    try {
                        audioFormat = format;
                        duration = format.getLong(MediaFormat.KEY_DURATION);
                        codec = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                builder.append(" ").append(MediaFormat.KEY_DURATION);
                builder.append(format.getLong(MediaFormat.KEY_DURATION));
            }
            Log.i(TAG, builder.toString());
        }
        if (codec == null) {
            callbacks.onError("Couldn't find audio track");
            return;
        }
        callbacks.onProgressChanged(5);
        FileOutputStream os = null;
        final String name = videoPath.substring(0, videoPath.lastIndexOf('.'));
        final String pcmFileName = name + "_22000.pcm";
        try {
            os = new FileOutputStream(pcmFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            callbacks.onError("Couldn't open file for writing");
            return;
        }
        final FileOutputStream finalOs = os;
        final long finalDuration = duration;
        final long limit = (secondsLimit < 0 || duration < 0.5) ? Long.MAX_VALUE : (long)(1000000L*secondsLimit);
        codec.setCallback(new MediaCodec.Callback() {
            MediaFormat mOutputFormat;
            long sampleDuration = 0;
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                int size = extractor.readSampleData(inputBuffer, 0);
                if (sampleDuration == 0) {
                    sampleDuration = extractor.getSampleTime();
                }
                if (size >= 0 && extractor.getSampleTime() + sampleDuration < limit) {
                    codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                    extractor.advance();
                } else {
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                callbacks.onProgressChanged((int) (10 + 90*info.presentationTimeUs / finalDuration));
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                int sampleRate = mOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                byte[] array = new byte[info.size];
                outputBuffer.get(array);
                Resampler resampler = new Resampler();
                try {
                    byte[] resampled = resampler.reSample(array, 16, sampleRate, 22000);
                    finalOs.write(resampled, 0, resampled.length);
                } catch (IOException e) {
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(TAG, "Finished for " + name);
                    try {
                        finalOs.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        finalOs.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    codec.stop();
                    codec.release();
                    extractor.release();
                    callbacks.onProgressChanged(100);
                    callbacks.onResult(VoiceTranscriptor.this, pcmFileName);
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                callbacks.onError(e.getDiagnosticInfo());
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                mOutputFormat = format;
                Log.i(TAG, name + "->" + format.toString());
            }
        });
        codec.configure(
                audioFormat,//format of input data ::decoder OR desired format of the output data:encoder
                null,//Specify a surface on which to render the output of this decoder
                null,//Specify a crypto object to facilitate secure decryption
                0 //For Decoding, encoding use: CONFIGURE_FLAG_ENCODE
        );
        codec.start();
        callbacks.onProgressChanged(10);
    }

    public void requestTranscription(String path, ASRProvider asrProvider, final ASRProvider.Callback callback) {
        File myfil = new File(path);
        if (!myfil.canRead())
            Log.d("ParseStarter", "FATAL no read access");

        // UP chan, process the audio byteStream for interface to UrlConnection
        // using 'chunked-encoding'
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(myfil);
            FileChannel fc = fis.getChannel(); // Get the file's size and then
            // map it into memory
            int sz = (int) fc.size();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
            byte[] data2 = new byte[bb.remaining()];
            bb.get(data2);
            // conform to the interface from the curl examples on full-duplex
            // calls
            // see curl examples full-duplex for more on 'PAIR'. Just a globally
            // uniq value typ=long->String.
            // API KEY value is part of value in UP_URL_p2
            asrProvider.requestTrascription(data2, callback);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
