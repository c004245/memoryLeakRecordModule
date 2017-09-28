package com.example.unno.mywebrtc;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;

import org.webrtc.VideoRenderer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Administrator on 2016-11-23.
 */

public class MyVideoRendererGui implements GLSurfaceView.Renderer {

    private static MyVideoRendererGui instance = null;
    private static Runnable eglContextReady = null;
    private static final String TAG = "MyVideoRendererGui";
    private GLSurfaceView surface;
    private static EGLContext eglContext = null;
    private boolean onSurfaceCreatedCalled;
    private int screenWidth;
    private int screenHeight;
    //private ArrayList<VideoRendererGui.YuvImageRenderer> yuvImageRenderers;
    private ArrayList<MyVideoRendererGui.YuvImageRenderer> yuvImageRenderers;
    private int yuvProgram;
    private int oesProgram;
    private static final int EGL14_SDK_VERSION = 17;
    private static final int CURRENT_SDK_VERSION;
    private final String VERTEX_SHADER_STRING = "varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec2 in_tc;\n\nvoid main() {\n  gl_Position = in_pos;\n  interp_tc = in_tc;\n}\n";
    private final String YUV_FRAGMENT_SHADER_STRING = "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D y_tex;\nuniform sampler2D u_tex;\nuniform sampler2D v_tex;\n\nvoid main() {\n  float y = texture2D(y_tex, interp_tc).r;\n  float u = texture2D(u_tex, interp_tc).r - 0.5;\n  float v = texture2D(v_tex, interp_tc).r - 0.5;\n  gl_FragColor = vec4(y + 1.403 * v,                       y - 0.344 * u - 0.714 * v,                       y + 1.77 * u, 1);\n}\n";
    private static final String OES_FRAGMENT_SHADER_STRING = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oes_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(oes_tex, interp_tc);\n}\n";
    private android.os.Environment Environment;

    private static Matrix mMatrix;
    private final float[] projectionMatrix = new float[16];

    private MyVideoRendererGui(GLSurfaceView surface) {
        Log.d(TAG,"MyVideoRendererGui surface");
        this.surface = surface;
        surface.setPreserveEGLContextOnPause(true);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(this);
        surface.setRenderMode(0);
        this.yuvImageRenderers = new ArrayList();
    }

    private static void abortUnless(boolean condition, String msg) {
        //Log.d(TAG,"abortUnless");
        if(!condition) {
            throw new RuntimeException(msg);
        }
    }

    private static void checkNoGLES2Error() {
        //Log.d(TAG,"checkNoGLES2Error");
        int error = GLES20.glGetError();
        abortUnless(error == 0, "GLES20 error: " + error);
    }

    private static FloatBuffer directNativeFloatBuffer(float[] array) {
        Log.d(TAG,"directNativeFloatBuffer");
        FloatBuffer buffer = ByteBuffer.allocateDirect(array.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(array);
        buffer.flip();
        return buffer;
    }

    private int loadShader(int shaderType, String source) {
        Log.d(TAG,"loadShader");
        int[] result = new int[]{0};
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        GLES20.glGetShaderiv(shader, '讁', result, 0);
        if(result[0] != 1) {
            Log.e("MyVideoRendererGui", "Could not compile shader " + shaderType + ":" + GLES20.glGetShaderInfoLog(shader));
            throw new RuntimeException(GLES20.glGetShaderInfoLog(shader));
        } else {
            checkNoGLES2Error();
            return shader;
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        Log.d(TAG,"createProgram");
        int vertexShader = this.loadShader('謱', vertexSource);
        int fragmentShader = this.loadShader('謰', fragmentSource);
        int program = GLES20.glCreateProgram();
        if(program == 0) {
            throw new RuntimeException("Could not create program");
        } else {
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[]{0};
            GLES20.glGetProgramiv(program, '讂', linkStatus, 0);
            if(linkStatus[0] != 1) {
                Log.e("MyVideoRendererGui", "Could not link program: " + GLES20.glGetProgramInfoLog(program));
                throw new RuntimeException(GLES20.glGetProgramInfoLog(program));
            } else {
                checkNoGLES2Error();
                return program;
            }
        }
    }

    public static void setView(GLSurfaceView surface, Runnable eglContextReadyCallback) {
        Log.d(TAG,"setView");
        Log.d("MyVideoRendererGui", "MyVideoRendererGui.setView");
        //instance = new VideoRendererGui(surface);
        //instance = new org.webrtc.VideoRendererGui(surface);
        instance = new MyVideoRendererGui(surface);
        //instance = new com.example.unno.mywebrtc.MyVideoRendererGui(surface);
        eglContextReady = eglContextReadyCallback;
    }

    public static EGLContext getEGLContext() {
        Log.d(TAG,"getEGLContext");
        return eglContext;
    }

    public static VideoRenderer createGui(int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) throws Exception {
        Log.d(TAG,"createGui");
        MyVideoRendererGui.YuvImageRenderer javaGuiRenderer = create(x, y, width, height, scalingType, mirror);
        return new VideoRenderer(javaGuiRenderer);
    }

    public static VideoRenderer.Callbacks createGuiRenderer(int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) {
        Log.d(TAG,"createGuiRenderer");
        return create(x, y, width, height, scalingType, mirror);
    }

    public static MyVideoRendererGui.YuvImageRenderer create(int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) {
        Log.d(TAG,"create");
        if(x >= 0 && x <= 100 && y >= 0 && y <= 100 && width >= 0 && width <= 100 && height >= 0 && height <= 100 && x + width <= 100 && y + height <= 100) {
            if(instance == null) {
                throw new RuntimeException("Attempt to create yuv renderer before setting GLSurfaceView");
            } else {
                //final MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = new MyVideoRendererGui.YuvImageRenderer(instance.surface, instance.yuvImageRenderers.size(), x, y, width, height, scalingType, mirror, null);
                final MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = new MyVideoRendererGui.YuvImageRenderer(
                        instance.surface, instance.yuvImageRenderers.size(), x, y, width, height, scalingType, mirror);
                ArrayList var7 = instance.yuvImageRenderers;
                synchronized(instance.yuvImageRenderers) {
                    if(instance.onSurfaceCreatedCalled) {
                        final CountDownLatch countDownLatch = new CountDownLatch(1);
                        instance.surface.queueEvent(new Runnable() {
                            public void run() {
                                yuvImageRenderer.createTextures(MyVideoRendererGui.instance.yuvProgram, MyVideoRendererGui.instance.oesProgram);
                                yuvImageRenderer.setScreenSize(MyVideoRendererGui.instance.screenWidth, MyVideoRendererGui.instance.screenHeight);
                                countDownLatch.countDown();
                            }
                        });

                        try {
                            countDownLatch.await();
                        } catch (InterruptedException var11) {
                            throw new RuntimeException(var11);
                        }
                    }

                    instance.yuvImageRenderers.add(yuvImageRenderer);
                    return yuvImageRenderer;
                }
            }
        } else {
            throw new RuntimeException("Incorrect window parameters.");
        }
    }

    public static void update(VideoRenderer.Callbacks renderer, int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) {
        Log.d(TAG,"update");
        Log.d("MyVideoRendererGui", "MyVideoRendererGui.update");
        if(instance == null) {
            throw new RuntimeException("Attempt to update yuv renderer before setting GLSurfaceView");
        } else {
            ArrayList var7 = instance.yuvImageRenderers;
            synchronized(instance.yuvImageRenderers) {
                Iterator i$ = instance.yuvImageRenderers.iterator();

                while(i$.hasNext()) {
                    MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = (MyVideoRendererGui.YuvImageRenderer)i$.next();
                    if(yuvImageRenderer == renderer) {
                        yuvImageRenderer.setPosition(x, y, width, height, scalingType, mirror);
                    }
                }

            }
        }
    }

    public static void remove(VideoRenderer.Callbacks renderer) {
        Log.d("MyVideoRendererGui", "MyVideoRendererGui.remove");
        if(instance == null) {
            throw new RuntimeException("Attempt to remove yuv renderer before setting GLSurfaceView");
        } else {
            ArrayList var1 = instance.yuvImageRenderers;
            synchronized(instance.yuvImageRenderers) {
                if(!instance.yuvImageRenderers.remove(renderer)) {
                    Log.w("MyVideoRendererGui", "Couldn\'t remove renderer (not present in current list)");
                }

            }
        }
    }

    @SuppressLint({"NewApi"})
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Log.d("MyVideoRendererGui", "MyVideoRendererGui.onSurfaceCreated");
        if(CURRENT_SDK_VERSION >= 17) {
            eglContext = EGL14.eglGetCurrentContext();
            Log.d("MyVideoRendererGui", "MyVideoRendererGui EGL Context: " + eglContext);
        }

        this.yuvProgram = this.createProgram("varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec2 in_tc;\n\nvoid main() {\n  gl_Position = in_pos;\n  interp_tc = in_tc;\n}\n", "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D y_tex;\nuniform sampler2D u_tex;\nuniform sampler2D v_tex;\n\nvoid main() {\n  float y = texture2D(y_tex, interp_tc).r;\n  float u = texture2D(u_tex, interp_tc).r - 0.5;\n  float v = texture2D(v_tex, interp_tc).r - 0.5;\n  gl_FragColor = vec4(y + 1.403 * v,                       y - 0.344 * u - 0.714 * v,                       y + 1.77 * u, 1);\n}\n");
        this.oesProgram = this.createProgram("varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec2 in_tc;\n\nvoid main() {\n  gl_Position = in_pos;\n  interp_tc = in_tc;\n}\n", "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oes_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(oes_tex, interp_tc);\n}\n");
        ArrayList var3 = this.yuvImageRenderers;
        synchronized(this.yuvImageRenderers) {
            Iterator i$ = this.yuvImageRenderers.iterator();

            while(true) {
                if(!i$.hasNext()) {
                    this.onSurfaceCreatedCalled = true;
                    break;
                }

                MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = (MyVideoRendererGui.YuvImageRenderer)i$.next();
                yuvImageRenderer.createTextures(this.yuvProgram, this.oesProgram);
            }
        }

        checkNoGLES2Error();
        GLES20.glClearColor(0.15F, 0.15F, 0.15F, 1.0F);
        if(eglContextReady != null) {
            eglContextReady.run();
        }
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.d("MyVideoRendererGui", "MyVideoRendererGui.onSurfaceChanged: " + width + " x " + height + "  ");
        this.screenWidth = width;
        this.screenHeight = height;
        GLES20.glViewport(0, 0, width, height);
        ArrayList var4 = this.yuvImageRenderers;
        synchronized(this.yuvImageRenderers) {
            Iterator i$ = this.yuvImageRenderers.iterator();

            while(i$.hasNext()) {
                MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = (MyVideoRendererGui.YuvImageRenderer)i$.next();
                yuvImageRenderer.setScreenSize(this.screenWidth, this.screenHeight);
            }
        }
    }

    public void onDrawFrame(GL10 unused) {
        //------------------------------------------------------------------------------------------
        GLES20.glViewport(0, 0, this.screenWidth, this.screenHeight);
        //------------------------------------------------------------------------------------------

        GLES20.glClear(16384);
        ArrayList var2 = this.yuvImageRenderers;
        synchronized(this.yuvImageRenderers) {
            Iterator i$ = this.yuvImageRenderers.iterator();

            while(i$.hasNext()) {
                MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = (MyVideoRendererGui.YuvImageRenderer)i$.next();
                yuvImageRenderer.draw();
            }
        }

        //------------------------------------------------------------------------------------------
        if (true) {
            MainActivity.mCapturing.beginCaptureFrame();
            Iterator i$ = this.yuvImageRenderers.iterator();
            while (i$.hasNext()) {
                MyVideoRendererGui.YuvImageRenderer yuvImageRenderer = (MyVideoRendererGui.YuvImageRenderer)i$.next();
                yuvImageRenderer.draw();
            }
            MainActivity.mCapturing.endCaptureFrame();
        }
        //------------------------------------------------------------------------------------------
    }

    static {
        CURRENT_SDK_VERSION = Build.VERSION.SDK_INT;
    }

    private static class YuvImageRenderer implements VideoRenderer.Callbacks {
        private GLSurfaceView surface;
        private int id;
        private int yuvProgram;
        private int oesProgram;
        private int[] yuvTextures;
        private int oesTexture;
        private float[] stMatrix;
        LinkedBlockingQueue<VideoRenderer.I420Frame> frameToRenderQueue;
        private VideoRenderer.I420Frame yuvFrameToRender;
        private VideoRenderer.I420Frame textureFrameToRender;
        private MyVideoRendererGui.YuvImageRenderer.RendererType rendererType;
        private MyVideoRendererGui.ScalingType scalingType;
        private boolean mirror;
        boolean seenFrame;
        private int framesReceived;
        private int framesDropped;
        private int framesRendered;
        private long startTimeNs;
        private long drawTimeNs;
        private long copyTimeNs;
        private float texLeft;
        private float texRight;
        private float texTop;
        private float texBottom;
        private FloatBuffer textureVertices;
        private FloatBuffer textureCoords;
        private boolean updateTextureProperties;
        private final Object updateTextureLock;
        private int screenWidth;
        private int screenHeight;
        private int videoWidth;
        private int videoHeight;
        private int rotationDegree;
        private static int[][] rotation_matrix = new int[][]{{4, 5, 0, 1, 6, 7, 2, 3}, {6, 7, 4, 5, 2, 3, 0, 1}, {2, 3, 6, 7, 0, 1, 4, 5}};
        private static int[][] mirror_matrix = new int[][]{{4, 1, 6, 3, 0, 5, 2, 7}, {0, 5, 2, 7, 4, 1, 6, 3}, {4, 1, 6, 3, 0, 5, 2, 7}, {0, 5, 2, 7, 4, 1, 6, 3}};

        private YuvImageRenderer(GLSurfaceView surface, int id, int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) {
            this.yuvTextures = new int[]{-1, -1, -1};
            this.oesTexture = -1;
            this.stMatrix = new float[16];
            this.startTimeNs = -1L;
            this.updateTextureLock = new Object();
            Log.d("MyVideoRendererGui", "YuvImageRenderer.Create id: " + id);
            this.surface = surface;
            this.id = id;
            this.scalingType = scalingType;
            this.mirror = mirror;
            this.frameToRenderQueue = new LinkedBlockingQueue(1);
            this.texLeft = (float)(x - 50) / 50.0F;
            this.texTop = (float)(50 - y) / 50.0F;
            this.texRight = Math.min(1.0F, (float)(x + width - 50) / 50.0F);
            this.texBottom = Math.max(-1.0F, (float)(50 - y - height) / 50.0F);
            float[] textureVeticesFloat = new float[]{this.texLeft, this.texTop, this.texLeft, this.texBottom, this.texRight, this.texTop, this.texRight, this.texBottom};
            this.textureVertices = MyVideoRendererGui.directNativeFloatBuffer(textureVeticesFloat);
            float[] textureCoordinatesFloat = new float[]{0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F};
            this.textureCoords = MyVideoRendererGui.directNativeFloatBuffer(textureCoordinatesFloat);
            this.updateTextureProperties = false;
            this.rotationDegree = 0;
        }

        private void createTextures(int yuvProgram, int oesProgram) {
            Log.d("MyVideoRendererGui", "  YuvImageRenderer.createTextures " + this.id + " on GL thread:" + Thread.currentThread().getId());
            this.yuvProgram = yuvProgram;
            this.oesProgram = oesProgram;
            GLES20.glGenTextures(3, this.yuvTextures, 0);

            for(int i = 0; i < 3; ++i) {
                GLES20.glActiveTexture('蓀' + i);
                GLES20.glBindTexture(3553, this.yuvTextures[i]);
                GLES20.glTexImage2D(3553, 0, 6409, 128, 128, 0, 6409, 5121, (Buffer)null);
                GLES20.glTexParameterf(3553, 10241, 9729.0F);
                GLES20.glTexParameterf(3553, 10240, 9729.0F);
                GLES20.glTexParameterf(3553, 10242, 33071.0F);
                GLES20.glTexParameterf(3553, 10243, 33071.0F);
            }

            MyVideoRendererGui.checkNoGLES2Error();
        }

        private void checkAdjustTextureCoords() {
            //Log.d(TAG,"checkAdjustTextureCoords");
            Object var1 = this.updateTextureLock;
            synchronized(this.updateTextureLock) {
                if(this.updateTextureProperties && this.scalingType != MyVideoRendererGui.ScalingType.SCALE_FILL) {
                    float texRight = this.texRight;
                    float texLeft = this.texLeft;
                    float texTop = this.texTop;
                    float texBottom = this.texBottom;
                    float texOffsetU = 0.0F;
                    float texOffsetV = 0.0F;
                    float displayWidth = (texRight - texLeft) * (float)this.screenWidth / 2.0F;
                    float displayHeight = (texTop - texBottom) * (float)this.screenHeight / 2.0F;
                    Log.d("MyVideoRendererGui", "ID: " + this.id + ". AdjustTextureCoords. Display: " + displayWidth + " x " + displayHeight + ". Video: " + this.videoWidth + " x " + this.videoHeight + ". Rotation: " + this.rotationDegree + ". Mirror: " + this.mirror);
                    if(displayWidth > 1.0F && displayHeight > 1.0F && this.videoWidth > 1 && this.videoHeight > 1) {
                        float displayAspectRatio = displayWidth / displayHeight;
                        float videoAspectRatio = 0.0F;
                        if(this.rotationDegree != 90 && this.rotationDegree != 270) {
                            videoAspectRatio = (float)this.videoWidth / (float)this.videoHeight;
                        } else {
                            videoAspectRatio = (float)this.videoHeight / (float)this.videoWidth;
                        }

                        if(this.scalingType == MyVideoRendererGui.ScalingType.SCALE_ASPECT_FIT) {
                            float textureVeticesFloat;
                            if(displayAspectRatio > videoAspectRatio) {
                                textureVeticesFloat = (displayWidth - videoAspectRatio * displayHeight) / (float)MyVideoRendererGui.instance.screenWidth;
                                texRight -= textureVeticesFloat;
                                texLeft += textureVeticesFloat;
                            } else {
                                textureVeticesFloat = (displayHeight - displayWidth / videoAspectRatio) / (float)MyVideoRendererGui.instance.screenHeight;
                                texTop -= textureVeticesFloat;
                                texBottom += textureVeticesFloat;
                            }
                        }

                        if(this.scalingType == MyVideoRendererGui.ScalingType.SCALE_ASPECT_FILL) {
                            boolean textureVeticesFloat1 = true;
                            float uLeft = 0.0F;
                            if(displayAspectRatio > videoAspectRatio) {
                                uLeft = (1.0F - videoAspectRatio / displayAspectRatio) / 2.0F;
                                textureVeticesFloat1 = this.rotationDegree == 90 || this.rotationDegree == 270;
                            } else {
                                uLeft = (1.0F - displayAspectRatio / videoAspectRatio) / 2.0F;
                                textureVeticesFloat1 = this.rotationDegree == 0 || this.rotationDegree == 180;
                            }

                            if(textureVeticesFloat1) {
                                texOffsetU = uLeft;
                            } else {
                                texOffsetV = uLeft;
                            }
                        }

                        Log.d("MyVideoRendererGui", "  Texture vertices: (" + texLeft + "," + texBottom + ") - (" + texRight + "," + texTop + ")");
                        float[] textureVeticesFloat2 = new float[]{texLeft, texTop, texLeft, texBottom, texRight, texTop, texRight, texBottom};
                        this.textureVertices = MyVideoRendererGui.directNativeFloatBuffer(textureVeticesFloat2);
                        float uRight = 1.0F - texOffsetU;
                        float vBottom = 1.0F - texOffsetV;
                        Log.d("MyVideoRendererGui", "  Texture UV: (" + texOffsetU + "," + texOffsetV + ") - (" + uRight + "," + vBottom + ")");
                        float[] textureCoordinatesFloat = new float[]{texOffsetU, texOffsetV, texOffsetU, vBottom, uRight, texOffsetV, uRight, vBottom};
                        textureCoordinatesFloat = this.applyRotation(textureCoordinatesFloat, this.rotationDegree);
                        textureCoordinatesFloat = this.applyMirror(textureCoordinatesFloat, this.mirror);
                        this.textureCoords = MyVideoRendererGui.directNativeFloatBuffer(textureCoordinatesFloat);
                    }

                    this.updateTextureProperties = false;
                    Log.d("MyVideoRendererGui", "  AdjustTextureCoords done");
                }
            }
        }

        private float[] applyMirror(float[] textureCoordinatesFloat, boolean mirror) {
            Log.d(TAG,"applyMirror");
            if(!mirror) {
                return textureCoordinatesFloat;
            } else {
                int index = this.rotationDegree / 90;
                return this.applyMatrixOperation(textureCoordinatesFloat, mirror_matrix[index]);
            }
        }

        private float[] applyRotation(float[] textureCoordinatesFloat, int rotationDegree) {
            Log.d(TAG,"applyRotation");
            if(rotationDegree == 0) {
                return textureCoordinatesFloat;
            } else {
                int index = rotationDegree / 90 - 1;
                return this.applyMatrixOperation(textureCoordinatesFloat, rotation_matrix[index]);
            }
        }

        private float[] applyMatrixOperation(float[] textureCoordinatesFloat, int[] matrix_operation) {
            Log.d(TAG,"applyMatrixOperation");
            float[] textureCoordinatesModifiedFloat = new float[textureCoordinatesFloat.length];

            for(int i = 0; i < textureCoordinatesFloat.length; ++i) {
                textureCoordinatesModifiedFloat[matrix_operation[i]] = textureCoordinatesFloat[i];
            }

            return textureCoordinatesModifiedFloat;
        }

        private void draw() {
            //Log.d(TAG,"draw");
            if(this.seenFrame) {
                long now = System.nanoTime();
                boolean currentProgram = false;
                LinkedBlockingQueue posLocation = this.frameToRenderQueue;
                VideoRenderer.I420Frame frameFromQueue;
                int texLocation;
                int var11;
                synchronized(this.frameToRenderQueue) {
                    this.checkAdjustTextureCoords();
                    frameFromQueue = (VideoRenderer.I420Frame)this.frameToRenderQueue.peek();
                    if(frameFromQueue != null && this.startTimeNs == -1L) {
                        this.startTimeNs = now;
                    }

                    if(this.rendererType == MyVideoRendererGui.YuvImageRenderer.RendererType.RENDERER_YUV) {
                        GLES20.glUseProgram(this.yuvProgram);
                        var11 = this.yuvProgram;

                        for(texLocation = 0; texLocation < 3; ++texLocation) {
                            GLES20.glActiveTexture('蓀' + texLocation);
                            GLES20.glBindTexture(3553, this.yuvTextures[texLocation]);
                            if(frameFromQueue != null) {
                                int w = texLocation == 0?frameFromQueue.width:frameFromQueue.width / 2;
                                int h = texLocation == 0?frameFromQueue.height:frameFromQueue.height / 2;
                                GLES20.glTexImage2D(3553, 0, 6409, w, h, 0, 6409, 5121, frameFromQueue.yuvPlanes[texLocation]);
                            }
                        }

                        GLES20.glUniform1i(GLES20.glGetUniformLocation(this.yuvProgram, "y_tex"), 0);
                        GLES20.glUniform1i(GLES20.glGetUniformLocation(this.yuvProgram, "u_tex"), 1);
                        GLES20.glUniform1i(GLES20.glGetUniformLocation(this.yuvProgram, "v_tex"), 2);
                    } else {
                        GLES20.glUseProgram(this.oesProgram);
                        var11 = this.oesProgram;
                        if(frameFromQueue != null) {
                            this.oesTexture = frameFromQueue.textureId;
                            if(frameFromQueue.textureObject instanceof SurfaceTexture) {
                                SurfaceTexture var13 = (SurfaceTexture)frameFromQueue.textureObject;
                                var13.updateTexImage();
                                var13.getTransformMatrix(this.stMatrix);
                            }
                        }

                        GLES20.glActiveTexture('蓀');
                        GLES20.glBindTexture('赥', this.oesTexture);
                    }

                    if(frameFromQueue != null) {
                        this.frameToRenderQueue.poll();
                    }
                }

                int var12 = GLES20.glGetAttribLocation(var11, "in_pos");
                if(var12 == -1) {
                    throw new RuntimeException("Could not get attrib location for in_pos");
                } else {
                    GLES20.glEnableVertexAttribArray(var12);
                    GLES20.glVertexAttribPointer(var12, 2, 5126, false, 0, this.textureVertices);
                    texLocation = GLES20.glGetAttribLocation(var11, "in_tc");
                    if(texLocation == -1) {
                        throw new RuntimeException("Could not get attrib location for in_tc");
                    } else {
                        GLES20.glEnableVertexAttribArray(texLocation);
                        GLES20.glVertexAttribPointer(texLocation, 2, 5126, false, 0, this.textureCoords);
                        GLES20.glDrawArrays(5, 0, 4);
                        GLES20.glDisableVertexAttribArray(var12);
                        GLES20.glDisableVertexAttribArray(texLocation);
                        MyVideoRendererGui.checkNoGLES2Error();
                        if(frameFromQueue != null) {
                            ++this.framesRendered;
                            this.drawTimeNs += System.nanoTime() - now;
                            if(this.framesRendered % 300 == 0) {
                                this.logStatistics();
                            }
                        }

                    }
                }
            }
        }

        private void logStatistics() {
            Log.d(TAG,"logStatistics");
            long timeSinceFirstFrameNs = System.nanoTime() - this.startTimeNs;
            Log.d("MyVideoRendererGui", "ID: " + this.id + ". Type: " + this.rendererType + ". Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered);
            if(this.framesReceived > 0 && this.framesRendered > 0) {
                Log.d("MyVideoRendererGui", "Duration: " + (int)((double)timeSinceFirstFrameNs / 1000000.0D) + " ms. FPS: " + (double)((float)this.framesRendered) * 1.0E9D / (double)timeSinceFirstFrameNs);
                Log.d("MyVideoRendererGui", "Draw time: " + (int)(this.drawTimeNs / (long)(1000 * this.framesRendered)) + " us. Copy time: " + (int)(this.copyTimeNs / (long)(1000 * this.framesReceived)) + " us");
            }

        }

        public void setScreenSize(int screenWidth, int screenHeight) {
            Log.d(TAG,"setScreenSize");
            Object var3 = this.updateTextureLock;
            synchronized(this.updateTextureLock) {
                if(screenWidth != this.screenWidth || screenHeight != this.screenHeight) {
                    Log.d("MyVideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setScreenSize: " + screenWidth + " x " + screenHeight);
                    this.screenWidth = screenWidth;
                    this.screenHeight = screenHeight;
                    this.updateTextureProperties = true;
                }
            }
        }

        public void setPosition(int x, int y, int width, int height, MyVideoRendererGui.ScalingType scalingType, boolean mirror) {
            Log.d(TAG,"setPosition");
            float texLeft = (float)(x - 50) / 50.0F;
            float texTop = (float)(50 - y) / 50.0F;
            float texRight = Math.min(1.0F, (float)(x + width - 50) / 50.0F);
            float texBottom = Math.max(-1.0F, (float)(50 - y - height) / 50.0F);
            Object var11 = this.updateTextureLock;
            synchronized(this.updateTextureLock) {
                if(texLeft != this.texLeft || texTop != this.texTop || texRight != this.texRight || texBottom != this.texBottom || scalingType != this.scalingType || mirror != this.mirror) {
                    Log.d("MyVideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setPosition: (" + x + ", " + y + ") " + width + " x " + height + ". Scaling: " + scalingType + ". Mirror: " + mirror);
                    this.texLeft = texLeft;
                    this.texTop = texTop;
                    this.texRight = texRight;
                    this.texBottom = texBottom;
                    this.scalingType = scalingType;
                    this.mirror = mirror;
                    this.updateTextureProperties = true;
                }
            }
        }

        private void setSize(int videoWidth, int videoHeight, int rotation) {
            //Log.d(TAG,"setSize");
            if(videoWidth != this.videoWidth || videoHeight != this.videoHeight || rotation != this.rotationDegree) {
                LinkedBlockingQueue var4 = this.frameToRenderQueue;
                synchronized(this.frameToRenderQueue) {
                    Log.d("MyVideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setSize: " + videoWidth + " x " + videoHeight + " rotation " + rotation);
                    this.videoWidth = videoWidth;
                    this.videoHeight = videoHeight;
                    this.rotationDegree = rotation;
                    int[] strides = new int[]{videoWidth, videoWidth / 2, videoWidth / 2};
                    this.frameToRenderQueue.poll();
                    this.yuvFrameToRender = new VideoRenderer.I420Frame(videoWidth, videoHeight, this.rotationDegree, strides, (ByteBuffer[])null);
                    this.textureFrameToRender = new VideoRenderer.I420Frame(videoWidth, videoHeight, this.rotationDegree, (Object)null, -1);
                    this.updateTextureProperties = true;
                    Log.d("MyVideoRendererGui", "  YuvImageRenderer.setSize done.");
                }
            }
        }

        public synchronized void renderFrame(VideoRenderer.I420Frame frame) {
            //Log.d(TAG,"renderFrame");

            this.setSize(frame.width, frame.height, frame.rotationDegree);
            long now = System.nanoTime();
            ++this.framesReceived;
            if(this.yuvFrameToRender != null && this.textureFrameToRender != null) {
                if(frame.yuvFrame) {
                    if(frame.yuvStrides[0] < frame.width || frame.yuvStrides[1] < frame.width / 2 || frame.yuvStrides[2] < frame.width / 2) {
                        Log.e("MyVideoRendererGui", "Incorrect strides " + frame.yuvStrides[0] + ", " + frame.yuvStrides[1] + ", " + frame.yuvStrides[2]);
                        return;
                    }

                    if(frame.width != this.yuvFrameToRender.width || frame.height != this.yuvFrameToRender.height) {
                        throw new RuntimeException("Wrong frame size " + frame.width + " x " + frame.height);
                    }
                }

                if(this.frameToRenderQueue.size() > 0) {
                    ++this.framesDropped;
                } else {
                    if(frame.yuvFrame) {
                        this.yuvFrameToRender.copyFrom(frame);
                        this.rendererType = MyVideoRendererGui.YuvImageRenderer.RendererType.RENDERER_YUV;
                        this.frameToRenderQueue.offer(this.yuvFrameToRender);
                    } else {
                        this.textureFrameToRender.copyFrom(frame);
                        this.rendererType = MyVideoRendererGui.YuvImageRenderer.RendererType.RENDERER_TEXTURE;
                        this.frameToRenderQueue.offer(this.textureFrameToRender);
                    }

                    this.copyTimeNs += System.nanoTime() - now;
                    this.seenFrame = true;
                    this.surface.requestRender();
                }
            } else {
                ++this.framesDropped;
            }
        }

        public boolean canApplyRotation() {
            Log.d(TAG,"canApplyRotation");
            return true;
        }

        private static enum RendererType {
            RENDERER_YUV,
            RENDERER_TEXTURE;

            private RendererType() {
            }
        }
    }

    public static enum ScalingType {
        SCALE_ASPECT_FIT,
        SCALE_ASPECT_FILL,
        SCALE_FILL;

        private ScalingType() {
        }
    }
}
