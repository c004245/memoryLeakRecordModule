package com.example.unno.mywebrtc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.android.graphics.FullFrameTexture;

import java.io.File;
import java.io.IOException;

/**
 * Created by Administrator on 2016-11-16.
 */

public class Capturing {

    private final static String TAG = "Capturing";

    private static FullFrameTexture texture;
    private VideoCapture videoCapture;

    private IProgressListener progressListener = new IProgressListener() {
        @Override
        public void onMediaStart() {
            Log.d(TAG,"onMediaStart");
        }

        @Override
        public void onMediaProgress(float progress) {
            Log.d(TAG, "onMediaProgress");
        }

        @Override
        public void onMediaDone() {
            Log.d(TAG, "onMediaDone");
        }

        @Override
        public void onMediaPause() {
            Log.d(TAG, "onMediaPause");
        }

        @Override
        public void onMediaStop() {
            Log.d(TAG, "onMediaStop");
        }

        @Override
        public void onError(Exception exception) {
            Log.d(TAG, "onError");
        }
    };

    /**
     * 객체 기록 화면을 초기화
     *
     * @param context
     */
    public Capturing(Context context) {
        Log.d(TAG, "capturing");
        videoCapture = new VideoCapture(context, progressListener);
    }

    public static String getDirectoryDCIM() {
        Log.d(TAG, "getDirectoryDCIM");
        return Environment.getExternalStorageDirectory() + File.separator;
    }

    /**
     * 초기화 파라미터 기록 화면
     *
     * @param width     : 녹화 화면 너비
     * @param height    : 녹화 화면 높이
     * @param frameRate : 녹화 프레임 속도
     * @param bitRate   : 녹화 비트레이트
     */
    public void initCapturing(int width, int height, int frameRate, int bitRate) {
        Log.d(TAG, "init capturing:");
        Log.d(TAG, "  width=" + width);
        Log.d(TAG, "  height=" + height);
        Log.d(TAG, "  frameRate=" + frameRate);
        Log.d(TAG, "  bitRate=" + bitRate);

        VideoCapture.init(width, height, frameRate, bitRate);
    }

    /**
     * 녹화 시작
     *
     * @param videoPath : 비디오 녹화 저장 절대 경로
     */
    public void startCapturing(String videoPath) {
        Log.d(TAG, "startCapturing");
        if (videoCapture == null) {
            return;
        }
        synchronized (videoCapture) {
            try {
                videoCapture.start(videoPath);
                Log.d(TAG, "start capturing, save to " + videoPath);
            } catch (IOException e) {
            }
        }
    }

    public void captureFrame(int textureID) {
        Log.d(TAG, "captureFrame");
        if (videoCapture == null) {
            return;
        }
        synchronized (videoCapture) {
            videoCapture.beginCaptureFrame();
            texture.draw(textureID);
            videoCapture.endCaptureFrame();
        }
    }

    /**
     * 현재 프레임을 캡쳐 시작
     */
    public void beginCaptureFrame() {
        //Log.d(TAG, "beginCaptureFrame");
        if (videoCapture == null) {
            return;
        }

        videoCapture.beginCaptureFrame();
    }

    /**
     * 최종 현재 프레임 캡쳐 끝
     */
    public void endCaptureFrame() {
        //Log.d(TAG, "endCaptureFrame");
        if (videoCapture == null) {
            return;
        }

        videoCapture.endCaptureFrame();
    }

    /**
     * 녹화 정지
     */
    public void stopCapturing() {
        Log.d(TAG, "stopCapturing");
        if (videoCapture == null) {
            return;
        }
        synchronized (videoCapture) {
            if (videoCapture.isStarted()) {
                videoCapture.stop();
                Log.d(TAG, "stop capturing.");
            }
        }
    }
}
