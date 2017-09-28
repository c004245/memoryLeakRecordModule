package com.example.unno.mywebrtc;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.intel.inde.mp.GLCapture;
import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.VideoFormat;
import com.intel.inde.mp.android.AndroidMediaObjectFactory;
import com.intel.inde.mp.android.VideoFormatAndroid;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Administrator on 2016-11-16.
 */

public class VideoCapture {

    private static final String TAG = "VideoCapture";

    private static final String Codec = "video/avc";
    //public static final String Codec = "video/x-vnd.on2.vp8";
    //public static final String Codec = "video/x-vnd.on2.vp9";
    //public static final String Codec = "video/hevc";
    //public static final String Codec = "video/mp4v-es";
    //public static final String Codec = "video/3gpp";
    //public static final String Codec = "video/mpeg2";
    //public static final String Codec = "video/raw";
    private static int IFrameInterval = 1;

    private static final Object syncObject = new Object();
    private static volatile VideoCapture videoCapture;

    static String strTime = "";
    private static VideoFormat videoFormat;
    private static int videoWidth;
    private static int videoHeight;
    private GLCapture capturer;

    private boolean isConfigured;
    private boolean isStarted;
    private long framesCaptured;
    private Context context;
    private Activity activity;
    private IProgressListener progressListener;

    public VideoCapture(Context context, IProgressListener progressListener) {
        this.context = context;
        this.progressListener = progressListener;
    }

    public static void init(int width, int height, int frameRate, int bitRate) {
        Log.d(TAG, "init");
        videoWidth = width;
        videoHeight = height;

        videoFormat = new VideoFormatAndroid(Codec, videoWidth, videoHeight);
        videoFormat.setVideoFrameRate(frameRate);
        videoFormat.setVideoBitRateInKBytes(bitRate);
        videoFormat.setVideoIFrameInterval(IFrameInterval);



    }

    public void start(String videoPath) throws IOException {
        Log.d(TAG, "start");
        if (isStarted())
            throw new IllegalStateException(TAG + " already started!");

        capturer = new GLCapture(new AndroidMediaObjectFactory(context), progressListener);
        capturer.setTargetFile(videoPath);
        capturer.setTargetVideoFormat(videoFormat);

        /*AudioFormat audioFormat = new AudioFormatAndroid("audio/mp4a-latm", 44100, 2);
        capturer.setTargetAudioFormat(audioFormat);*/

        capturer.start();

        isStarted = true;
        isConfigured = false;
        framesCaptured = 0;
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (!isStarted())
            throw new IllegalStateException(TAG + " not started or already stopped!");

        try {
            capturer.stop();
            isStarted = false;
        } catch (Exception ex) {
        }

        capturer = null;
        isConfigured = false;
    }
    class GetCurrentTime extends AsyncTask<Void, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Void... params) {
            Log.d(TAG, "doInBackground");

            Long now = System.currentTimeMillis();

            Date date = new Date(now);

            SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            strTime = sdfNow.format(date);
            Log.d(TAG ,"strTime --->" +strTime);
            return strTime;

        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            MainActivity.previewTime(strTime);
        }
    }
    private void configure() {
        Log.d(TAG, "configure");
        new GetCurrentTime().execute();
/*        long now = System.currentTimeMillis();

        Date date = new Date(now);

        SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String strNow = sdfNow.format(date);

        TextView textView = (TextView)*/
        if (isConfigured())
            return;

        try {
            capturer.setSurfaceSize(videoWidth, videoHeight);
            isConfigured = true;
            Log.d(TAG, "configure capturing:");
            Log.d(TAG, "  video width=" + videoWidth);
            Log.d(TAG, "  video height=" + videoHeight);
        } catch (Exception ex) {
        }
    }

    public void beginCaptureFrame() {
        //Log.d(TAG, "beginCaptureFrame");
        /*long now = System.currentTimeMillis();

        Date date = new Date(now);

        SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String strNow = sdfNow.format(date);

        TextView textView = (TextView) activity.findViewById(R.id.tvCamRecTime);
        textView.setText(strNow);*/
        if (!isStarted())
            return;

        //g
        //clock 20170111
        new GetCurrentTime().execute();

        configure();
        if (!isConfigured())
            return;

        capturer.beginCaptureFrame();
    }

    public void endCaptureFrame() {
        //Log.d(TAG, "endCaptureFrame");
        if (!isStarted() || !isConfigured())
            return;

        capturer.endCaptureFrame();
        framesCaptured++;
    }

    public boolean isStarted() {
        //Log.d(TAG, "isStarted");
        return isStarted;
    }

    public boolean isConfigured() {
        //Log.d(TAG, "isConfigured");
        return isConfigured;
    }
}
