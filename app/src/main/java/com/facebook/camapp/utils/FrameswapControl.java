package com.facebook.camapp.utils;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import com.facebook.camapp.utils.grafika.*;
import android.graphics.SurfaceTexture;
import android.view.Surface;

public class FrameswapControl extends WindowSurface {
    private boolean mDropNext = false;

    public FrameswapControl(EglCore eglCore, Surface surface, boolean releaseSurface) {
        super(eglCore, surface, releaseSurface);
    }

    public FrameswapControl(EglCore eglCore, SurfaceTexture surfaceTexture) {
        super(eglCore, surfaceTexture);
    }

    public boolean keepFrame() {
        return !mDropNext;
    }

    public void dropNext(boolean drop) {
        mDropNext = drop;
    }
}

