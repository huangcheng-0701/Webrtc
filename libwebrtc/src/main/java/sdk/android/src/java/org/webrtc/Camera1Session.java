/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioRecord;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.Preference;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import org.webrtc.CameraEnumerationAndroid.CaptureFormat;
import org.webrtc.Media.MediaMuxerThread;
import org.webrtc.Media.NV21ToBitmap;
import org.webrtc.Media.VideoEncoderThread;
import org.webrtc.voiceengine.WebRtcAudioRecord;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Policy;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static android.media.MediaCodec.createPersistentInputSurface;
import static androidx.core.math.MathUtils.clamp;

//import com.serenegiant.usb.UVCCamera;
@SuppressWarnings("deprecation")
class Camera1Session implements CameraSession {
    private static final String TAG = "Camera1Session";
    private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;

    private static final Histogram camera1StartTimeMsHistogram =
            Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
    private static final Histogram camera1StopTimeMsHistogram =
            Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
    private static final Histogram camera1ResolutionHistogram = Histogram.createEnumeration(
            "WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());


    private static enum SessionState {RUNNING, STOPPED}

    private final Handler cameraThreadHandler;
    private final Events events;
    private final boolean captureToTexture;
    private final Context applicationContext;
    private final SurfaceTextureHelper surfaceTextureHelper;
    private final int cameraId;
    private final android.hardware.Camera camera;
    //private UVCCamera mCamera;
    private final android.hardware.Camera.CameraInfo info;
    private final CaptureFormat captureFormat;
    // Used only for stats. Only used on the camera thread.
    private final long constructionTimeNs; // Construction time of this class.

    private SessionState state;
    private boolean firstFrameReported;

    private static String srcPath = "/storage/emulated/0/Download/Webrtc_Recorder";
    private static MediaRecorder mr;

    // TODO(titovartem) make correct fix during webrtc:9175
    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressWarnings("ByteBufferBackingArray")
    public static void create(final CreateSessionCallback callback, final Events events,
                              final boolean captureToTexture, final Context applicationContext,
                              final SurfaceTextureHelper surfaceTextureHelper, final int cameraId, final int width,
                              final int height, final int framerate) {
        final long constructionTimeNs = System.nanoTime();
        Logging.d(TAG, "Open camera " + cameraId);
        events.onCameraOpening();
        final android.hardware.Camera camera;
        try {
            camera = android.hardware.Camera.open(cameraId);
        } catch (RuntimeException e) {
            callback.onFailure(FailureType.ERROR, e.getMessage());
            return;
        }

        if (camera == null) {
            callback.onFailure(FailureType.ERROR,
                    "android.hardware.Camera.open returned null for camera id = " + cameraId);
            return;
        }

        try {
            camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
        } catch (IOException | RuntimeException e) {
            camera.release();
            callback.onFailure(FailureType.ERROR, e.getMessage());
            return;
        }

        final android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        final CaptureFormat captureFormat;
        try {
            final android.hardware.Camera.Parameters parameters = camera.getParameters();
            captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
            final Size pictureSize = findClosestPictureSize(parameters, width, height);
            updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
        } catch (RuntimeException e) {
            camera.release();
            callback.onFailure(FailureType.ERROR, e.getMessage());
            return;
        }

        if (!captureToTexture) {
            final int frameSize = captureFormat.frameSize();
            for (int i = 0; i < NUMBER_OF_CAPTURE_BUFFERS; ++i) {
                final ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
                camera.addCallbackBuffer(buffer.array());
            }
        }

        // Calculate orientation manually and send it as CVO insted.
        camera.setDisplayOrientation(0 /* degrees */);

        callback.onDone(new Camera1Session(events, captureToTexture, applicationContext,
                surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
    }

    private static void updateCameraParameters(android.hardware.Camera camera,
                                               android.hardware.Camera.Parameters parameters, CaptureFormat captureFormat, Size pictureSize,
                                               boolean captureToTexture) {
        final List<String> focusModes = parameters.getSupportedFocusModes();

        parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
        parameters.setPreviewSize(captureFormat.width, captureFormat.height);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);

        if (PrefSingleton.getInstance().getBoolean("flow_mode")
                && parameters.getSupportedColorEffects().contains(android.hardware.Camera.Parameters.EFFECT_MONO)) {
            parameters.setColorEffect(android.hardware.Camera.Parameters.EFFECT_MONO);
        }


        if (!captureToTexture) {
            parameters.setPreviewFormat(captureFormat.imageFormat);
        }

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        if (PrefSingleton.getInstance().getBoolean("focus_mode")) { // 自动对焦
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else {
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);
        }
        PrefSingleton.getInstance().putInt("width",parameters.getPreviewSize().width);
        PrefSingleton.getInstance().putInt("height",parameters.getPreviewSize().height);
        //parameters.setPreviewFrameRate(60);
        camera.setParameters(parameters);

        // 音视频录制
        boolean Recoder = PrefSingleton.getInstance().getBoolean("recorder_mode");
        boolean Water = PrefSingleton.getInstance().getBoolean("water_mode");
        if (Recoder && !Water) {
            mr = new MediaRecorder();
            if (!new File(srcPath).exists()){
              new File(srcPath).mkdirs();
            }
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            Date date = new Date(System.currentTimeMillis());
            String Recorder_name = simpleDateFormat.format(date) + ".mp4";
            //call methods in this order!
            camera.lock();
            camera.unlock();
            //1st. Initial state:
            mr.setCamera(camera);
            //2st. Initialized source:
            mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mr.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            //3st. config from profile:
            CamcorderProfile profile = CamcorderProfile.get(Camera.CameraInfo.CAMERA_FACING_BACK, CamcorderProfile.QUALITY_HIGH);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //Audio
            mr.setAudioEncoder(profile.audioCodec);
            mr.setAudioEncodingBitRate(profile.audioBitRate);
            mr.setAudioChannels(profile.audioChannels);
            mr.setAudioSamplingRate(profile.audioSampleRate);
            //Video
            mr.setVideoEncoder(profile.videoCodec);
            mr.setVideoSize(captureFormat.width, captureFormat.height);
            mr.setVideoFrameRate(profile.videoFrameRate);
            mr.setVideoEncodingBitRate(profile.videoBitRate);
            mr.setOrientationHint(90);
            mr.setOutputFile(new File(srcPath + "/" + Recorder_name).getAbsolutePath());
            try {
              mr.prepare();
            } catch (IOException e) {
              e.printStackTrace();
            }
            mr.start();
        }
    }

    private static CaptureFormat findClosestCaptureFormat(
            android.hardware.Camera.Parameters parameters, int width, int height, int framerate) {
        // Find closest supported format for |width| x |height| @ |framerate|.
        final List<CaptureFormat.FramerateRange> supportedFramerates =
                Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
        Logging.d(TAG, "Available fps ranges: " + supportedFramerates);

        final CaptureFormat.FramerateRange fpsRange =
                CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);

        final Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(
                Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
        CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);

        return new CaptureFormat(previewSize.width, previewSize.height, fpsRange);
    }

    private static Size findClosestPictureSize(
            android.hardware.Camera.Parameters parameters, int width, int height) {
        return CameraEnumerationAndroid.getClosestSupportedSize(
                Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
    }

    private Camera1Session(Events events, boolean captureToTexture, Context applicationContext,
                           SurfaceTextureHelper surfaceTextureHelper, int cameraId, android.hardware.Camera camera,
                           android.hardware.Camera.CameraInfo info, CaptureFormat captureFormat,
                           long constructionTimeNs) {
        Logging.d(TAG, "Create new camera1 session on camera " + cameraId);

        this.cameraThreadHandler = new Handler();
        this.events = events;
        this.captureToTexture = captureToTexture;
        this.applicationContext = applicationContext;
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.cameraId = cameraId;
        this.camera = camera;
        this.info = info;
        this.captureFormat = captureFormat;
        this.constructionTimeNs = constructionTimeNs;

        surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);

        startCapturing();
    }

    @Override
    public void stop() {
        boolean Recoder = PrefSingleton.getInstance().getBoolean("recorder_mode");
        boolean Water = PrefSingleton.getInstance().getBoolean("water_mode");
        if (Recoder && !Water) {
            mr.stop();
            mr.release();
        }
        if (Recoder && Water) MediaMuxerThread.stopMuxer();

        Logging.d(TAG, "Stop camera1 session on camera " + cameraId);
        checkIsOnCameraThread();
        if (state != SessionState.STOPPED) {
            final long stopStartTime = System.nanoTime();
            stopInternal();
            final int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
            camera1StopTimeMsHistogram.addSample(stopTimeMs);
        }
    }

    private void startCapturing() {
        Logging.d(TAG, "Start capturing");
        checkIsOnCameraThread();

        state = SessionState.RUNNING;

        camera.setErrorCallback(new android.hardware.Camera.ErrorCallback() {
            @Override
            public void onError(int error, android.hardware.Camera camera) {
                String errorMessage;
                if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
                    errorMessage = "Camera server died!";
                } else {
                    errorMessage = "Camera error: " + error;
                }
                Logging.e(TAG, errorMessage);
                stopInternal();
                if (error == android.hardware.Camera.CAMERA_ERROR_EVICTED) {
                    events.onCameraDisconnected(Camera1Session.this);
                } else {
                    events.onCameraError(Camera1Session.this, errorMessage);
                }
            }
        });

        if (captureToTexture) {
            listenForTextureFrames();
        } else {
            PrefSingleton.getInstance().putString("MediaMuxer",
                    new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis()));
            boolean Recoder = PrefSingleton.getInstance().getBoolean("recorder_mode");
            boolean Water = PrefSingleton.getInstance().getBoolean("water_mode");
            if (Recoder && Water) MediaMuxerThread.startMuxer();
            listenForBytebufferFrames();
        }
        try {
            camera.startPreview();
        } catch (RuntimeException e) {
            stopInternal();
            events.onCameraError(this, e.getMessage());
        }
    }

    private void stopInternal() {
        Logging.d(TAG, "Stop internal");
        checkIsOnCameraThread();
        if (state == SessionState.STOPPED) {
            Logging.d(TAG, "Camera is already stopped");
            return;
        }

        state = SessionState.STOPPED;
        surfaceTextureHelper.stopListening();
        // Note: stopPreview or other driver code might deadlock. Deadlock in
        // android.hardware.Camera._stopPreview(Native Method) has been observed on
        // Nexus 5 (hammerhead), OS version LMY48I.
        camera.stopPreview();
        camera.release();
        events.onCameraClosed(this);
        Logging.d(TAG, "Stop done");
    }

    private void listenForTextureFrames() {
        surfaceTextureHelper.startListening((VideoFrame frame) -> {
            checkIsOnCameraThread();

            if (state != SessionState.RUNNING) {
                Logging.d(TAG, "Texture frame captured but camera is no longer running.");
                return;
            }

            if (!firstFrameReported) {
                final int startTimeMs =
                        (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructionTimeNs);
                camera1StartTimeMsHistogram.addSample(startTimeMs);
                firstFrameReported = true;
            }

            Camera.Parameters parameters = camera.getParameters();
            parameters.setZoom(PrefSingleton.getInstance().getInt("Seek"));
            camera.setParameters(parameters);

            // Undo the mirror that the OS "helps" us with.
            // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
            final VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix(
                            (TextureBufferImpl) frame.getBuffer(),
                    info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT, 0),
                    getFrameOrientation(), frame.getTimestampNs());
            events.onFrameCaptured(Camera1Session.this, modifiedFrame);
            modifiedFrame.release();
        });
    }

    private void listenForBytebufferFrames() {
        boolean Recoder = PrefSingleton.getInstance().getBoolean("recorder_mode");
        boolean Water = PrefSingleton.getInstance().getBoolean("water_mode");
        camera.setPreviewCallbackWithBuffer(new android.hardware.Camera.PreviewCallback() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onPreviewFrame(byte[] data, android.hardware.Camera callbackCamera) {
                checkIsOnCameraThread();
                if (PrefSingleton.getInstance().getBoolean("water_mode")) {
                    data = new Myclass().dealByte(data,captureFormat.width,captureFormat.height,applicationContext);
                }
                /*synchronized (data) {
                    if (Recoder && Water) MediaMuxerThread.addVideoFrameData(data);
                }*/
                if (Recoder && Water) MediaMuxerThread.addVideoFrameData(data);

                if (callbackCamera != camera) {
                    Logging.e(TAG, "Callback from a different camera. This should never happen.");
                    return;
                }

                if (state != SessionState.RUNNING) {
                    Logging.d(TAG, "Bytebuffer frame captured but camera is no longer running.");
                    return;
                }

                final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

                if (!firstFrameReported) {
                    final int startTimeMs =
                            (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructionTimeNs);
                    camera1StartTimeMsHistogram.addSample(startTimeMs);
                    firstFrameReported = true;
                }

                Camera.Parameters parameters = camera.getParameters();
                parameters.setZoom(PrefSingleton.getInstance().getInt("Seek"));
                camera.setParameters(parameters);

                VideoFrame.Buffer frameBuffer;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        PrefSingleton.getInstance().getBoolean("flow_mode")) {
                    byte[] Data = fetch(btoi(data, captureFormat.width, captureFormat.height), captureFormat.width, captureFormat.height);
                    frameBuffer = new NV21Buffer(Data, captureFormat.width, captureFormat.height, null);
                } else {
                    frameBuffer = new NV21Buffer(data, captureFormat.width, captureFormat.height, null);
                    //frameBuffer = new NV21Buffer(VideoEncoderThread.Data(), captureFormat.width, captureFormat.height, null);
                }
                final VideoFrame frame = new VideoFrame(frameBuffer, getFrameOrientation(), captureTimeNs);
                events.onFrameCaptured(Camera1Session.this, frame);
                frame.release();
                if (state == SessionState.RUNNING) {
                    camera.addCallbackBuffer(data);
                }
            }
        });
    }


    //Android P data YUV 灰度处理
  public static int[] btoi(byte[] values, int picW, int picH) {
      if (values == null || picW <= 0 || picH <= 0)
          return null;
      int pixels[] = new int[picW * picH];
      int size = pixels.length;
      for (int i = 0; i < size; i++) {
          pixels[i] = values[i] * 256 * 256 + values[i] * 256 + values[i] + 0xFF000000;
      }
      return pixels;
  }

  public byte[] fetch(int[] pixels, int w, int h) {
    int size = w * h;
    Bitmap bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    byte[] nv21 = new byte[size * 3 / 2];
    int i, j;
    for (i = 0; i < h; i++) {
      for (j = 0; j < w; j++) {
        int yIndex = i * w + j;

        int argb = pixels[yIndex];
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;

        int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
        y = clamp(y, 16, 255);
        nv21[yIndex] = (byte)y;

        if (i % 2 == 0 && j % 2 == 0) {
          int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
          int v = ((112 * r - 94 * g -18 * b + 128) >> 8) + 128;
          u = clamp(u, 0, 255);
          v = clamp(v, 0, 255);
          nv21[size + i / 2 * w + j] = (byte) v;
          nv21[size + i / 2 * w + j + 1] = (byte) u;
        }
      }
    }
    return nv21;
  }


  private int getFrameOrientation() {
    int rotation = CameraSession.getDeviceOrientation(applicationContext);
    if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
      rotation = 360 - rotation;
    }
    return (info.orientation + rotation) % 360;
  }

  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
      throw new IllegalStateException("Wrong thread");
    }
  }


  //NV21 to Bitmap
  public static Bitmap createBitmap(byte[] values, int picW, int picH) {
    if(values == null || picW <= 0 || picH <= 0)
      return null;
    Bitmap bitmap = Bitmap.createBitmap(picW, picH, Bitmap.Config.ARGB_8888);
    int pixels[] = new int[picW * picH];
    int size = pixels.length;
    for (int i = 0; i < size; i++) {
      pixels[i] = values[i] * 256 * 256 + values[i] * 256 + values[i] + 0xFF000000;
    }
    bitmap.setPixels(pixels, 0, picW, 0, 0, picW, picH);
    //values = null;
    //pixels = null;
    return bitmap;
  }

  public byte[] fetchNV21(Bitmap bitmap) {
    int w = bitmap.getWidth();
    int h = bitmap.getHeight();
    int size = w * h;
    int[] pixels = new int[size];
    bitmap.getPixels(pixels,0, w,0,0, w, h);
    byte[] nv21 = new byte[size * 3 / 2];
    //w &= ~1; h &= ~1;
    int i, j;
    for (i = 0; i < h; i++) {
      for (j = 0; j < w; j++) {
        int yIndex = i * w + j;

        int argb = pixels[yIndex];
        //int a = (argb >> 24) & 0xff;
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;

        int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
        y = clamp(y, 16, 255);
        nv21[yIndex] = (byte)y;

        if (i % 2 == 0 && j % 2 == 0) {
          int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
          int v = ((112 * r - 94 * g -18 * b + 128) >> 8) + 128;
          u = clamp(u, 0, 255);
          v = clamp(v, 0, 255);
          nv21[size + i / 2 * w + j] = (byte) v;
          nv21[size + i / 2 * w + j + 1] = (byte) u;
        }
      }
    }
    return nv21;
  }

}
