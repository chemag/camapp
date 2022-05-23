package com.facebook.camapp.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.RecommendedStreamConfigurationMap;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import static android.content.Context.CAMERA_SERVICE;


public class CameraSource {
    private static final String TAG = "camapp.camera";
    Context mContext;
    CameraManager mCameraManager;
    CameraDevice mCameraDevice;
    Handler mHandler;
    Vector<OutputConfiguration> mOutputConfigs;
    CameraCaptureSession mSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private static CameraSource me = null;
    private static int mClients = 0;
    final static Object lock = new Object();
    Vector<SurfaceData> mSurfaces = new Vector<>();

    int mHwLevel = -1;
    
    public static CameraSource getCamera(Context theContext) {
        synchronized (lock) {
            if (me == null) {
                me = new CameraSource(theContext);
            }
            mClients += 1;
        }
        return me;
    }

    private CameraSource(Context context) {
        mContext = context;
    }

    public void closeCamera() {
        synchronized (me) {
            mClients -= 1;
            Log.d(TAG, "Clients is: " + mClients);

            try {
                if (mSession != null) {
                    mSession.abortCaptures();
                }
                mSession = null;
                if (mOutputConfigs != null)
                    mOutputConfigs.clear();
                if (mSurfaces != null)
                    mSurfaces.clear();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            if (mClients == 0) {
                mCameraDevice.close();
            }
        }
    }


    private boolean openCamera() {
        mCameraManager = (CameraManager) mContext.getSystemService(CAMERA_SERVICE);
        if (mCameraManager == null) {
            Log.e(TAG, "No camera");
            return false;
        }

        try {
            String[] cams = mCameraManager.getCameraIdList();
            for (String cam : cams) {
                Log.d(TAG, "Camera: " + cam);
                CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cam);
                List<CameraCharacteristics.Key<?>> keys = chars.getKeys();
                Iterator<CameraCharacteristics.Key<?>> iter = keys.iterator();
                while (iter.hasNext()) {
                    CameraCharacteristics.Key<?> key = iter.next();

                    Log.d(TAG, "Key: " + key.getName());
                }
            }

            HandlerThread handlerThread = new HandlerThread("camera");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
            ///Choose first
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            CameraCharacteristics characs = mCameraManager.getCameraCharacteristics(cams[0]);
            StreamConfigurationMap streamConfigurationMap = characs.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int[] formats = streamConfigurationMap.getOutputFormats();
            for (int format:formats) {
                Log.d(TAG, "Pixel format: " + format);
                Size[] previewSizes = streamConfigurationMap.getOutputSizes(format);
                for (Size previewSize:previewSizes) {
                    Log.d(TAG, "Preview size for the format: " + previewSize);
                }
            }
            Size[] previewSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
            for (Size previewSize:previewSizes) {
                Log.d(TAG, "Preview size for surface class: " + previewSize);
            }

            int [] inputFormats = streamConfigurationMap.getInputFormats();
            for (int format:inputFormats) {
                Log.d(TAG, "Input format: " + format);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RecommendedStreamConfigurationMap recomended = characs.getRecommendedStreamConfigurationMap(RecommendedStreamConfigurationMap.USECASE_PREVIEW);
            }

            Range<Integer>[] fpsRanges = characs.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            for (Range range: fpsRanges) {
                Log.d(TAG, "fps range: " + range.getLower() + " -> " + range.getUpper());
            }

            Range<Integer>  sensorRange = characs.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (sensorRange != null)
                Log.d(TAG, "Sensor range: " + sensorRange.getLower() + " -> " + sensorRange.getUpper());

            Range<Long> exposureRange = characs.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposureRange != null)
                Log.d(TAG, "Exposure range: " + exposureRange.getLower() + " -> " + exposureRange.getUpper());

            Long maxDuration = characs.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
            Log.d(TAG, "Max frame duration: " + maxDuration);
            mCameraManager.openCamera(cams[0],
                    new StateHolder(),
                    mHandler);

            int orientation = characs.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Log.d(TAG,"Sensor orientation: " + orientation);

            mHwLevel = characs.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            Log.d(TAG, "Hw level: " + hwLevelToText(mHwLevel));
        } catch (CameraAccessException cameraAccessException) {
            cameraAccessException.printStackTrace();
        }
        return true;
    }


    public void registerSurface(Surface output, int width,  int height) {
        mSurfaces.add(new SurfaceData(output, width, height));
    }

    public static void start() {
        me.openCamera();
    }

    private boolean startCapture() {
        try {
            mOutputConfigs = new Vector<>();


            for (SurfaceData data: mSurfaces) {
                Log.d(TAG, "Add config surface: " + data.mSurface + ", " + data.mHeight);
                OutputConfiguration outconfig = new OutputConfiguration(data.mSurface);
                mOutputConfigs.add(outconfig);
            }
            SessionConfiguration config = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    mOutputConfigs,
                    new CamExec(),
                    new CamState());

            mCameraDevice.createCaptureSession(config);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return true;
    }
    class StateHolder extends CameraDevice.StateCallback {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera opened: "+camera.getId());
            mCameraDevice = camera;
            startCapture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera disconnected: "+camera.getId());
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "Camera error: "+camera.getId() + ", Error: " + error);
        }
    }


    class CamExec implements Executor {

        @Override
        public void execute(Runnable command) {
         //   Log.d(TAG, "Exec: " + command);
            command.run();
        }
    }

    
    
    class CamState extends CameraCaptureSession.StateCallback {
        public CamState() {
            super();
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "CameraCapture configured: " + session.toString());

            try {
                Log.d(TAG, "Create request");
                CameraCharacteristics characs =mCameraManager.getCameraCharacteristics(mCameraDevice.getId());
                Range<Integer>[] fpsRanges = characs.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Range<Integer>  sensorRange = characs.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);

                mPreviewRequestBuilder
                        = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                for (SurfaceData data: mSurfaces) {
                    Log.d(TAG, "Add target surface: " + data.mSurface + ", " + data.mHeight);
                    mPreviewRequestBuilder.addTarget(data.mSurface);
                }

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
                Log.d(TAG, "Capture!");
                int capture = session.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mHandler);
                mSession = session;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "CameraCapture config failed: " + session.toString());

        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            super.onReady(session);
            //session.getInputSurface().setFrameRate(30, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
//            session.switchToOffline(Arrays.asList(mSurface), new CamExec(), new Offline());
        }


        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
            Log.d(TAG, "onActive" + session);
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
            super.onCaptureQueueEmpty(session);
            Log.d(TAG, "onCaptureQueueEmpty");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            Log.d(TAG, "onClosed");
        }


        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            super.onSurfacePrepared(session, surface);
            Log.d(TAG, "onSurfacePrepared");
        }

    }

    String hwLevelToText( int deviceLevel) {

        switch (deviceLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY";

            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL";

            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED";

            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_FULL";

            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_3";

            default:
                return "Whoops, what is this?";
        }
    }
    // Returns true if the device supports the required hardware level, or better.
    boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        final int[] sortedHwLevels = {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        };
        int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (requiredLevel == deviceLevel) {
            return true;
        }

        for (int sortedlevel : sortedHwLevels) {
            if (sortedlevel == requiredLevel) {
                return true;
            } else if (sortedlevel == deviceLevel) {
                return false;
            }
        }
        return false; // Should never reach here
    }

    class SurfaceData {
        Surface mSurface;
        int mWidth, mHeight;
        public SurfaceData(Surface surface, int width, int height) {
            mSurface = surface;
            mWidth = width;
            mHeight = height;
        }
    }

    public static int getClientCount() {
        return mClients;
    }
}
