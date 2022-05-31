package com.facebook.camapp;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.facebook.camapp.utils.CameraSource;
import com.facebook.camapp.utils.FpsMeasure;
import com.facebook.camapp.utils.OutputMultiplier;

import java.util.ArrayList;
import java.util.Formatter;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, SeekBar.OnSeekBarChangeListener {
    final static String TAG = "camapp.main";

    TextureView mTextureView;
    SurfaceTexture mSurfaceTexture;
    Surface mSurface;
    TextView mDataText;
    OutputMultiplier mOutputMult;
    CameraSource mCamera;
    float mFps = 30.0f;
    int mSensitivityTarget = -1;
    int mFrameDurationTargetUsec = -1;
    int mFrameExposureTimeTargetUsec = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.cameraView);
        mDataText = findViewById(R.id.dataText);
        String[] permissions = retrieveNotGrantedPermissions(this);

        if (permissions != null && permissions.length > 0) {
            int REQUEST_ALL_PERMISSIONS = 0x4562;
            ActivityCompat.requestPermissions(this, permissions, REQUEST_ALL_PERMISSIONS);
        }
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Log.d(TAG, "Have extra settings");
            if (bundle.containsKey("fps")) {
                mFps = Float.parseFloat(bundle.getString("fps"));
            }
            if (bundle.containsKey("iso")) {
                mSensitivityTarget = Integer.parseInt(bundle.getString("iso"));
            }
            if (bundle.containsKey("exp_usec")) {
                mFrameExposureTimeTargetUsec = Integer.parseInt(bundle.getString("exp_usec"));
            }
            if (bundle.containsKey("dur_usec")) {
                mFrameDurationTargetUsec = Integer.parseInt(bundle.getString("dur_usec"));
            }
        }
        final  TextureView.SurfaceTextureListener listener = this;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                mTextureView.setSurfaceTextureListener(listener);
            }
        });
        t.start();

    }


    private static String[] retrieveNotGrantedPermissions(Context context) {
        ArrayList<String> nonGrantedPerms = new ArrayList<>();
        try {
            String[] manifestPerms = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            if (manifestPerms == null || manifestPerms.length == 0) {
                return null;
            }

            for (String permName : manifestPerms) {
                int permission = ActivityCompat.checkSelfPermission(context, permName);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    nonGrantedPerms.add(permName);
                    Log.d(TAG, "Permission NOT granted: " + permName);
                } else {
                    Log.d(TAG, "Permission granted: " + permName);
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        return nonGrantedPerms.toArray(new String[nonGrantedPerms.size()]);
    }

    private void configureTextureViewTransform(Size previewSize, int viewWidth, int viewHeight) {
        if (null == mTextureView) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "Rotation = " + rotation);
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }


    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable w,h = " + width + ", " + height);
        Size previewSize = new Size(width, height);
        configureTextureViewTransform(previewSize, width, height);
        final int rHeight = height;
        final int rWidth = width;

        mSurfaceTexture = surface;
        final Context context = this;
        mOutputMult = new OutputMultiplier();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Missing permission");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mCamera = CameraSource.getCamera(context);
                mSurfaceTexture.setDefaultBufferSize(width, height);
                mOutputMult.addSurfaceTexture(mSurfaceTexture);
                mOutputMult.confirmSize(rWidth, rHeight);
                if (mFps > 0) {
                    mCamera.setFps(mFps);
                }
                if (mSensitivityTarget > 0) {
                    mCamera.setSensitivity(mSensitivityTarget);
                }
                if (mFrameDurationTargetUsec > 0) {
                    mCamera.setFrameDurationUsec(mFrameDurationTargetUsec);
                }
                if (mFrameExposureTimeTargetUsec > 0) {
                    mCamera.setFrameExposureTimeTargetUsec(mFrameExposureTimeTargetUsec);
                }
                while(mOutputMult.getInputSurface() == null) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mSurface = mOutputMult.getInputSurface();
                mCamera.registerSurface(mSurface, rWidth, rHeight);
                CameraSource.start();
                FpsMeasure fpsMeasure= new FpsMeasure(30.0f, "Camera");
                fpsMeasure.start();
                while(true) {
                    long tsNs = mOutputMult.awaitNewImage();
                    fpsMeasure.addPtsNsec(tsNs);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            String text =
                                    (new Formatter()).format("Camera rate: %.1f fps (1 sec average: %.1f fps)",
                                        fpsMeasure.getFps(), fpsMeasure.getAverageFps()).toString();
                            mDataText.setText(text);
                        }
                    });
                }
            }});
        t.start();

    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        if (mSurface != null) {
            mSurface.release();
        }
        if (mCamera != null) {
            mCamera.closeCamera();
        }
        if (mOutputMult != null) {
            mOutputMult.stopAndRelease();
        }

        System.exit(0);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
