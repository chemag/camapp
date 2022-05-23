package com.facebook.camapp;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;

import com.facebook.camapp.utils.CameraSource;
import com.facebook.camapp.utils.FpsMeasure;
import com.facebook.camapp.utils.OutputMultiplier;

import java.util.ArrayList;
import java.util.Formatter;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    final static String TAG = "camapp";

    TextureView mTextureView;
    SurfaceTexture mSurfaceTexture;
    Surface mSurface;
    TextView mDataText;

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
        //mTextureView.setRotation(-90);
        int tmp = width;
        Size previewSize = new Size(width, height);
        configureTextureViewTransform(previewSize, width, height);
        final int rHeight = height;
        final int rWidth = width;
        OutputMultiplier mOutputMult;

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

                CameraSource camera = CameraSource.getCamera(context);
                mOutputMult.addSurfaceTexture(mSurfaceTexture);
                mOutputMult.confirmSize(rWidth, rHeight);
                while(mOutputMult.getInputSurface() == null) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mSurface = mOutputMult.getInputSurface();
                camera.registerSurface(mSurface, rWidth, rHeight);
                camera.start();
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
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }
}
