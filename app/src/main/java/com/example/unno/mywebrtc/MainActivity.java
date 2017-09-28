package com.example.unno.mywebrtc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends ActionBarActivity /*implements Handler.Callback*/ {

    private String TAG = MainActivity.class.getSimpleName();
    private GLSurfaceView glview;
    private String VIDEO_TRACK_ID = TAG + "VIDEO";
    private String LOCAL_MEDIA_STREAM_ID = TAG + "STREAM";
    private MediaStream mediaStream;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private VideoRenderer remoteRender;
    private VideoRenderer localRender;
    private String roomName;

    private TextView wallClocksView;
    private static final int TICK_EVENT = 54123;

    private Handler mHandler;

    private static Activity mainActivity = null;
    private Button btnRecord;
    private Button btnCheckSize;
    private Button btnGetFileList;
    public static Capturing mCapturing;
    private Handler mRecTimerHandler;
    private Handler mDispDigitalClock;
    private static int testCnt;
    private boolean isRecStarted;
    private TextView tvDigitalClock;
    private TextView mTextView;

    private final static int HANDLE_MSG_RECORD_START = 1000;
    private final static int HANDLE_MSG_RECORD_REMOVE = 1001;
    private final static int HANDLE_MSG_DIGI_CLOCK_START = 1002;
    private final static int HANDLE_MSG_DIGI_CLOCK_REMOVE = 1003;
    //private final static int RECORD_INTERVAL = 120000; // 2min for REAL
    //private final static int RECORD_INTERVAL = 30000; // 30sec for TEST
    private final static int RECORD_INTERVAL = 300000; // 30sec for TEST
    private final static int RECORD_WIDTH = 640;
    private final static int RECORD_HEIGHT = 480;
    private final static int RECORD_FRAME_RATE = 1;
    private final static int RECORD_BIT_RATE = 10;
    private final static int RECORD_MAX_SIZE = 30; // 30GB

    private List recFileList;

    private RelativeLayout rl;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG,"onCreate");

        mainActivity = this;
        //mHandler = new Handler(this);
        /*wallClocksView = (TextView) findViewById(R.id.tvCamRecTime);
        wallClocksView.bringToFront();*/

      /*  DigitalClock.createClock(new DigitalClock.TickListener() {
            @Override
            public void tick(String dateTime) {
                mHandler.sendMessage(mHandler.obtainMessage(TICK_EVENT, dateTime));
            }
        });
        DigitalClock.applyDateTimeFormat(getString(R.string.wall_clocks_format), Locale.KOREA);*/
        //화면 꺼짐 방지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sigConnect("http://becctv.iosystem.co.kr:8081/");
        initWebRTC();

        Log.i(TAG, "VideoCapturerAndroid.getDeviceCount() = " + VideoCapturerAndroid.getDeviceCount());

        String nameOfFrontFacingDevice = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        String nameOfBackFacingDevice = VideoCapturerAndroid.getNameOfBackFacingDevice();

        Log.i(TAG, "VideoCapturerAndroid.getNameOfFrontFacingDevice() = " + nameOfFrontFacingDevice);
        Log.i(TAG, "VideoCapturerAndroid.getNameOfBackFacingDevice() = " + nameOfBackFacingDevice);

        VideoCapturerAndroid capturer = VideoCapturerAndroid.create(nameOfFrontFacingDevice);
        //VideoCapturerAndroid capturer = VideoCapturerAndroid.create(nameOfBackFacingDevice);

        final MediaConstraints videoConstraints = new MediaConstraints();

        VideoSource videoSource = peerConnectionFactory.createVideoSource(capturer, videoConstraints);
        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);

        //------------------------------------------------------------------------------------------
        // 2분 간격 녹화 파일 저장 핸들러
        // 2016-12-09 Added by GPY
        testCnt = 0; // for TEST
        mRecTimerHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case HANDLE_MSG_RECORD_START:
                        //String count = String.valueOf(testCnt);
                        //Log.d(TAG, "TimerHandler count ==> " + count);
                        Log.d(TAG, "TimerHandler count ==> " + String.valueOf(testCnt++));
                        //testCnt++;

                        // 여기서 용량체크를 해야 함.
                        String memSize = readableMemorySize(getAvailableInternalMemorySize());
                        Log.i(TAG, "Internal Memory Size = " + memSize);

                        //float recSize = Float.parseFloat(memSize) / 2; // 전제 용량 중 반을 저장공간으로 사용
                        float recSize = 5;
                        Log.i(TAG, "-->> Memory size: recSize = " + Float.toString(recSize));

                        // 저장 공간이 있으면 저장
                        if (recSize < RECORD_MAX_SIZE) {

                            startRecording();

                            mRecTimerHandler.sendEmptyMessageDelayed(HANDLE_MSG_RECORD_START, RECORD_INTERVAL);
                        } else {
                            Log.d(TAG, "Not enough memory size");
                            //this.removeMessages(HANDLE_MSG_RECORD_START);
                            // 이전 파일 삭제
                            RecordFile recFile = new RecordFile();
                            //recFile.getRecordFileList();
                            boolean ret = recFile.deleteBeforeFile();
                            Log.i(TAG, "ret = " + ret);
                            SystemClock.sleep(500);
                            //// 저장 중지, 핸들러 중지 --> 필요 없음
                            //mCapturing.stopCapturing();
                            //mRecTimerHandler.sendEmptyMessage(HANDLE_MSG_RECORD_REMOVE);
                        }
                        break;

                    case HANDLE_MSG_RECORD_REMOVE:
                        this.removeMessages(HANDLE_MSG_RECORD_START);
                        Log.d(TAG, "Record Timer Handler removed.");
                        break;

                    default:
                        Log.d(TAG, "Record Timer Handler defalut");
                        break;
                }
            }
        };
        //------------------------------------------------------------------------------------------
        // Cam 화면 시계 표시 핸들러
        mDispDigitalClock = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case HANDLE_MSG_DIGI_CLOCK_START:
                        //Log.d(TAG, "Digital clock timer handler start.");

                        dispCamRecordTime();
                        mDispDigitalClock.sendEmptyMessageDelayed(HANDLE_MSG_DIGI_CLOCK_START, 1000);
                        break;
                    case HANDLE_MSG_DIGI_CLOCK_REMOVE:
                        this.removeMessages(HANDLE_MSG_DIGI_CLOCK_START);
                        Log.d(TAG, "Digital clock timer handler removed.");
                        break;
                    default:
                        Log.d(TAG, "Digital clock timer handler default");
                        break;
                }
            }
        };
        //------------------------------------------------------------------------------------------
        mCapturing = new Capturing(this);
        //------------------------------------------------------------------------------------------

        glview = (GLSurfaceView) findViewById(R.id.glview);
        //VideoRendererGui.setView(glview, null);
        //MyVideoRendererGui.setView(glview, null);
        //------------------------------------------------------------------------------------------
        glview.setPreserveEGLContextOnPause(true);
        glview.setKeepScreenOn(true);

        MyVideoRendererGui.setView(glview, new Runnable() {
            @Override
            public void run() {
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
                init();
            }
        });
        //------------------------------------------------------------------------------------------

        try {
            //remoteRender = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
            //localRender = VideoRendererGui.createGui(72, 72, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
            remoteRender = MyVideoRendererGui.createGui(0, 0, 100, 100, MyVideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
            localRender = MyVideoRendererGui.createGui(0, 0, 100, 100, MyVideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
            localVideoTrack.addRenderer(localRender);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);
        mediaStream.addTrack(localVideoTrack);

        //------------------------------------------------------------------------------------------
        // 녹화 시작 버튼
        btnRecord = (Button) findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setSelected(!v.isSelected());
                if (v.isSelected()) {
                    Log.d(TAG, "Start button clicked.");

                    btnRecord.setText("Stop");

                    mCapturing.initCapturing(RECORD_WIDTH, RECORD_HEIGHT, RECORD_FRAME_RATE, RECORD_BIT_RATE);

                    mRecTimerHandler.sendEmptyMessage(HANDLE_MSG_RECORD_START);
                    //mDispDigitalClock.sendEmptyMessage(HANDLE_MSG_DIGI_CLOCK_START);
                } else {
                    Log.d(TAG, "Stop button clicked.");
                    btnRecord.setText("Start");
                    mCapturing.stopCapturing();
                    mRecTimerHandler.sendEmptyMessage(HANDLE_MSG_RECORD_REMOVE);
                    //mDispDigitalClock.sendEmptyMessage(HANDLE_MSG_DIGI_CLOCK_REMOVE);
                }
            }
        });
        //------------------------------------------------------------------------------------------
        // 용량 체크 버튼
        btnCheckSize = (Button) findViewById(R.id.btnCheckSize);
        btnCheckSize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setSelected(!v.isSelected());
                if (v.isSelected()) {
                    Log.d(TAG, "Check button clicked.");
                    
                    //showMemorySize();
                    String memSize = readableMemorySize(getAvailableInternalMemorySize());
                    Log.i(TAG, "Internal Memory Size = " + memSize);

                    //int iMemSize = Integer.parseInt(memSize);
                    float iMemSize = Float.parseFloat(memSize) / 2; // 전제 용량 중 반을 저장공간으로 사용
                    Log.i(TAG, "-->> Memory size: iMemSize = " + Float.toString(iMemSize));
                }
            }
        });
        //------------------------------------------------------------------------------------------
        //tvDigitalClock = (TextView) findViewById(R.id.tvCamRecTime);
        //tvDigitalClock.setText("sldkfjlsdfjsldkfjldkfj");
        //------------------------------------------------------------------------------------------
        // 디렉토리 파일 목록 가져오기
        btnGetFileList = (Button) findViewById(R.id.btnGetFileList);
        btnGetFileList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setSelected(!v.isSelected());
                if (v.isSelected()) {
                    //getRecFileList();
                    RecordFile recFile = new RecordFile();
                    //recFile.getRecordFileList();
                    boolean ret = recFile.deleteBeforeFile();
                    Log.i(TAG, "ret = " + ret);
                }

            }
        });
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
    } // End of onCreate()

    private void showMemorySize() {
        Log.i("MemoryStatus", "< MemoryStatus >");
        Log.i("MemoryStatus", "Total Internal MemorySize : " + formatSize(getTotalInternalMemorySize()));
        Log.i("MemoryStatus", "Available Internal MemorySize : " + formatSize(getAvailableInternalMemorySize()));
        Log.i("MemoryStatus", "----------------------");
        Log.i("MemoryStatus", "Available Internal MemorySize : " + readableMemorySize(getAvailableInternalMemorySize()));
        Log.i("MemoryStatus", "----------------------");
    }

    @Override
    protected void onStart() {
        super.onStart();
        DigitalClock.startClock();
    }
    public static String readableMemorySize(long size) {
        Log.d("MainActivity", "readableMemorySize");
        if (size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        //return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups));
    }

    protected static void onUiThreadJab(final Runnable runnable) {
        if (mainActivity != null)
            mainActivity.runOnUiThread(runnable);
    }

    protected static Object findView(final int id) {
        return mainActivity.findViewById(id);
    }
    public static void previewTime(final String time) {
        onUiThreadJab(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) findView(R.id.tvCamRecTime);
                if (textView != null)
                    textView.setText(time);
            }
        });

    }

    private static long getTotalInternalMemorySize() {
        Log.d("MainActivity","getTotalInternalMemorySize");
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();

        return totalBlocks * blockSize;
    }

    private static long getAvailableInternalMemorySize() {
        Log.d("MainActivity", "getAvailableInternalMemorySize");
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();

        return availableBlocks * blockSize;
    }

   /* @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == TICK_EVENT) {
            wallClocksView.setText((String) msg.obj);
        }
        return true;
    }*/
    private static String formatSize(long size) {
        Log.d("MainActivity", "formatSize");
        String suffix = null;

        if (size >= 1024) {
            suffix = "KB";
            size /= 1024;
            if (size >= 1024) {
                suffix = "MB";
                size /= 1024;
            }
        }

        StringBuilder resultBuffer = new StringBuilder(Long.toString(size));

        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }

        if (suffix != null) {
            resultBuffer.append(suffix);
        }

        return resultBuffer.toString();
    }

    private void dispCamRecordTime() {
        Log.d(TAG, "dispCamRecordTime");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date curDate = new Date();
        String curtime = formatter.format(curDate);

        tvDigitalClock.setText(curtime);

        /*mTextView = new TextView(this);
        mTextView.setText(curtime);
        mTextView.setTextColor(Color.WHITE);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        addContentView(mTextView, params);*/

        /*rl = new RelativeLayout(this);
        rl.addView(glview);
        tv = new TextView(this);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_TOP);
        tv.setLayoutParams(lp);
        tv.setText(curtime);
        tv.setBackgroundColor(0x4060ff70);
        rl.addView(tv);

        setContentView(rl);*/

        /*tv = new TextView(this);
        tv.setText(curtime);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);

        //get WindowManager from Activity
        WindowManager w = this.getWindowManager();

        //setup view LayoutParams
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSPARENT);

        //position
        lp.gravity = Gravity.CENTER | Gravity.TOP;

        //attach view
        //w.addView(tv,lp);
        addContentView(tv, lp);*/


    }

    private void startRecording() {
        Log.d(TAG, "startRecording");
        mCapturing.stopCapturing();

        SystemClock.sleep(200);

        /*// 파일명을 timestamp로 할때
        final Calendar c = Calendar.getInstance();
        long myTimeStamp = c.getTimeInMillis();
        String timeStamp = String.valueOf(myTimeStamp);
        String filePath = Environment.getExternalStorageDirectory() + File.separator
                + Environment.DIRECTORY_MOVIES + File.separator;
        String fileName = "video_" + timeStamp + ".mp4";*/

        // 파일명을 년월일시분초로 할 때
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date curDate = new Date();
        String filePath = Environment.getExternalStorageDirectory() + File.separator
                + Environment.DIRECTORY_MOVIES + File.separator;
        // 추후 여기에 video_ 앞에 MID_SCODE_CAMID를 붙여야 한다.
        String fileName = "video_" + formatter.format(curDate) + ".mp4";

        Log.i(TAG, "FileName = " + filePath + fileName);

        //mCapturing.initCapturing(1024, 768, 30, 2500);
        //mCapturing.initCapturing(640, 480, 30, 2500);
        mCapturing.startCapturing(filePath + fileName);
    }
    //==============================================================================================

    private void init() {
        Log.d(TAG, "init");
        Point dispSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(dispSize);
        Log.d(TAG, "dispSize.x : " + dispSize.x + ", dispSize.y : " + dispSize.y);
    }

    @Override
    protected void onResume() {
        super.onResume();
        glview.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glview.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        boolean ret = true;
        switch (id) {
            case R.id.action_call:
                connect();
                break;
            case R.id.action_hangup:
                hangUp();
                break;
            case R.id.action_room:
                showRoomDialog();
                break;
            default:
                ret = super.onOptionsItemSelected(item);
                break;
        }

        return ret;
    }

    private void showRoomDialog() {
        final EditText editView = new EditText(this);
        editView.setText(roomName);
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle("Enter room name")
                .setView(editView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        roomName = editView.getText().toString();
                        hangUp();
                        sigReconnect();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }

    private String getRoomName() {
        Log.d(TAG, "getRoomName");
        return (roomName == null || roomName.isEmpty())?
                "_defaultroom":
                roomName;
    }

    // webrtc
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private MediaConstraints pcConstraints;
    private MediaConstraints videoConstraints;
    private MediaConstraints audioConstraints;
    private MediaConstraints mediaConstraints;
    private SDPObserver sdpObserver = new SDPObserver();

    private Socket mSocket;
    private String wsServerUrl;
    private boolean peerStarted = false;

    private void initWebRTC() {
        Log.d(TAG, "initWebRTC");
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true, MyVideoRendererGui.getEGLContext());

        peerConnectionFactory = new PeerConnectionFactory();

        pcConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();
        mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    }

    private void connect() {
        Log.d(TAG, "connect");
        if (!peerStarted) {
            sendOffer();
            peerStarted = true;
        }
    }

    private void hangUp() {
        sendDisconnect();
        stop();
    }

    private void stop() {
        Log.d(TAG,"stop");
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
            peerStarted = false;
        }
    }


    // connection handling
    private PeerConnection prepareNewConnection() {
        Log.d(TAG,"prepareNewConnection");
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, pcConstraints, new PeerConnection.Observer() {

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG,"onSignalingChange");
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG,"onIceConnectionChange");
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG,"onIceGatheringChange");
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Log.d(TAG,"onIceCandidate");
                if (candidate != null) {
                    Log.i(TAG, "iceCandidate: " + candidate);
                    JSONObject json = new JSONObject();
                    jsonPut(json, "type", "candidate");
                    jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
                    jsonPut(json, "sdpMid", candidate.sdpMid);
                    jsonPut(json, "candidate", candidate.sdp);
                    sigSend(json);
                } else {
                    Log.i(TAG, "End of candidates. -------------------");
                }
            }

            @Override
            public void onAddStream(MediaStream stream) {
                Log.d(TAG,"onAddStream");
                if (MainActivity.this.peerConnection == null) {
                    return;
                }
                if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                    Log.e(TAG, "Weird-looking stream: " + stream);
                    return;
                }
                if (stream.videoTracks.size() == 1) {
                    remoteVideoTrack = stream.videoTracks.get(0);
                    remoteVideoTrack.addRenderer(MainActivity.this.remoteRender);
                }
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
                Log.d(TAG,"onRemoveStream");
                remoteVideoTrack = null;
                stream.videoTracks.get(0).dispose();
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }
        });

        peerConnection.addStream(mediaStream);

        return peerConnection;
    }

    private void onOffer(final SessionDescription sdp) {
        Log.d(TAG,"onOffer");
        setOffer(sdp);
        sendAnswer();
        peerStarted = true;
    }

    private void onAnswer(final SessionDescription sdp) {
        Log.d(TAG,"onAnswer");
        setAnswer(sdp);
    }

    private void onCandidate(final IceCandidate candidate) {
        Log.d(TAG,"onCandidate");
        peerConnection.addIceCandidate(candidate);
    }

    private void sendSDP(final SessionDescription sdp) {
        Log.d(TAG,"sendSDP");
        JSONObject json = new JSONObject();
        jsonPut(json, "type", sdp.type.canonicalForm());
        jsonPut(json, "sdp", sdp.description);
        sigSend(json);
    }

    private void sendOffer() {
        Log.d(TAG,"sendOffer");
        peerConnection = prepareNewConnection();
        peerConnection.createOffer(sdpObserver, mediaConstraints);
    }

    private void setOffer(final SessionDescription sdp) {
        Log.d(TAG,"setOffer");
        if (peerConnection != null) {
            Log.e(TAG, "peer connection already exists");
        }
        peerConnection = prepareNewConnection();
        peerConnection.setRemoteDescription(sdpObserver, sdp);
    }

    private void sendAnswer() {
        Log.d(TAG,"sendAnswer");
        Log.i(TAG, "sending Answer. Creating remote session description...");
        if (peerConnection == null) {
            Log.e(TAG, "peerConnection NOT exist!");
            return;
        }
        peerConnection.createAnswer(sdpObserver, mediaConstraints);
    }

    private void setAnswer(final SessionDescription sdp) {
        Log.d(TAG,"setAnswer");
        if (peerConnection == null) {
            Log.e(TAG, "peerConnection NOT exist!");
            return;
        }
        peerConnection.setRemoteDescription(sdpObserver, sdp);
    }

    private void sendDisconnect() {
        Log.d(TAG,"sendDisconnect");
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "user disconnected");
        sigSend(json);
    }

    private class SDPObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(TAG,"onCreateSuccess");
            peerConnection.setLocalDescription(sdpObserver, sessionDescription);
            Log.i(TAG, "Sending: SDP");
            Log.i(TAG, "" + sessionDescription);
            sendSDP(sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG,"onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.d(TAG,"onCreateFailure");

        }

        @Override
        public void onSetFailure(String s) {
            Log.d(TAG,"onSetFailure");
        }
    }

    // websocket related operations
    private void sigConnect(final String wsUrl) {
        Log.d(TAG,"sigConnect");
        wsServerUrl = wsUrl;

        try {
            mSocket = IO.socket(wsServerUrl);

            mSocket.on("connect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
                    sigEnter();
                }
            });
            mSocket.on("disconnect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d(TAG, "WebSocket connection closed.");
                }
            });
            mSocket.on("message", new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                    try {
                        if (args.length > 0) {
                            JSONObject json = (JSONObject)(args[0]);
                            Log.d(TAG, "WSS->C: " + json);
                            String type = json.optString("type");
                            if (type.equals("offer")) {
                                Log.i(TAG, "Received offer, set offer, sending answer....");
                                SessionDescription sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm(type),
                                        json.getString("sdp"));
                                onOffer(sdp);
                            } else if (type.equals("answer") && peerStarted) {
                                Log.i(TAG, "Received answer, setting answer SDP");
                                SessionDescription sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm(type),
                                        json.getString("sdp"));
                                onAnswer(sdp);
                            } else if (type.equals("candidate") && peerStarted) {
                                Log.i(TAG, "Received ICE candidate...");
                                IceCandidate candidate = new IceCandidate(
                                        json.getString("sdpMid"),
                                        json.getInt("sdpMLineIndex"),
                                        json.getString("candidate"));
                                onCandidate(candidate);
                            } else if (type.equals("user disconnected") && peerStarted) {
                                Log.i(TAG, "disconnected");
                                stop();
                            } else {
                                Log.e(TAG, "Unexpected WebSocket message: " + args[0]);
                            }
                        } else {
                            Log.e(TAG, "Unexpected WebSocket message: " + args[0]);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "WebSocket message JSON parsing error: " + e.toString() + " args[0]=" + args[0]);
                    }
                }
            });
            mSocket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "URI error: " + e.getMessage());
        }
    }

    private void sigReconnect() {
        mSocket.disconnect();
        mSocket.connect();
    }

    private void sigEnter() {
        mSocket.emit("enter", getRoomName());
    }

    private void sigSend(final JSONObject jsonObject) {
        mSocket.send(jsonObject);
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
