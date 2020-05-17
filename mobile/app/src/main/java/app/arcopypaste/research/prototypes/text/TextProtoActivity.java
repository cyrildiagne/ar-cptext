/*
 * Copyright 2020 Cyril Diagne. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.arcopypaste.research.prototypes.text;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.ImageHeaderParserUtils;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class TextProtoActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = TextProtoActivity.class.getSimpleName();

    private GLSurfaceView surfaceView;

    private TextView textView;

    private boolean installRequested;

    private float widthMeter;
    private float heightMeter;

    private Session session;
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

    private boolean shouldConfigureSession = false;

    private TapHelper tapHelper;

    private TextRecognition textRecognition;
    private String text;

    private Config config;

    private View btResize;

    private View cropView;

    private AugmentedImageDatabase augmentedImageDatabase;

    // Augmented image and its associated center pose anchor, keyed by index of the augmented image in
    // the database.
    private final Map<Integer, Pair<AugmentedImage, Anchor>> augmentedImageMap = new HashMap<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        Log.d(TAG, String.valueOf(surfaceView.getWidth()) + " " + String.valueOf(surfaceView.getHeight()));
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // 27" DELL screen is ~60.5cm * 34.0cm.
        // widthMeter = 0.605f;
        // heightMeter = 0.340f;

        // 15" MacBook Pro screen is ~33.0cm * 20.5cm.
        widthMeter = 0.330f;
        heightMeter = 0.205f;

        // Set up tap listener.
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        // Setup crop views.
        cropView = findViewById(R.id.crop_camera);
        btResize = findViewById(R.id.bt_resize);
        btResize.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                btResize.getViewTreeObserver().removeOnPreDrawListener(this);
                updateResizeBt();
                return true;
            }
        });

        btResize.setOnTouchListener(new View.OnTouchListener() {
            private int startX = 0;
            private int startY = 0;
            private int initW = cropView.getWidth();
            private int initH = cropView.getHeight();

            @Override
            public boolean onTouch(View v, MotionEvent e)  {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = (int)(e.getRawX() * 2);
                        startY = (int)(e.getRawY() * 2);
                        initW = cropView.getWidth();
                        initH = cropView.getHeight();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        //Log.d(TAG, String.valueOf(e.getRawX()) + " " + String.valueOf(e.getRawY()));
                        int width = initW + (int)(e.getRawX() * 2) - startX;
                        int height = initH + (int)(e.getRawY() * 2) - startY;
                        int left = (int)((surfaceView.getWidth() - width) * 0.5f);
                        int top = (int)((surfaceView.getHeight() - height) * 0.5f);
                        LayoutParams params = new LayoutParams(width, height);
                        params.setMargins(left, top, 0, 0);
                        cropView.setLayoutParams(params);
                        updateResizeBt();
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });

        // Setup up text recognition.
        textRecognition = new TextRecognition();

        // Setup text view.
        text = "";
        textView = findViewById(R.id.textview);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        // Alpha used for plane blending.
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        installRequested = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                session = new Session(/* context = */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (Exception e) {
                message = "This device does not support AR";
                exception = e;
            }

            if (message != null) {
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            shouldConfigureSession = true;
        }

        if (shouldConfigureSession) {
            configureSession();
            shouldConfigureSession = false;
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                    this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Handle one tap per frame.
            handleTap(frame, camera);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            // Update Screen tracking.
            updateTracking(frame);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null) {

            // If we already have a text in memory, we perform the paste.
            if (text != "") {
                this.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "PASTE");
                                // Paste text.
                                DesktopHTTPClient.paste();
                                // Reset & Hide text.
                                text = "";
                                textView.setText("");
                                textView.animate().alpha(0.0f);
                                // Show cropView.
                                cropView.animate().alpha(1.0f);
                                btResize.animate().alpha(1.0f);
                            }
                        });
                return;
            }

            Log.d(TAG, "COPY");

            // Perform Text Detection.
            try (Image image = frame.acquireCameraImage()) {
                if (image.getFormat() != ImageFormat.YUV_420_888) {
                    throw new IllegalArgumentException(
                            "Expected image in YUV_420_888 format, got format " + image.getFormat());
                }

                // Convert image to bitmap.
                // TODO: prevent from creating a new bmp for every picture.
                int[] cachedRgbBytes = new int[image.getHeight() * image.getWidth() * 3];
                ImageUtils.convertImageToBitmap(image, cachedRgbBytes, null);
                Bitmap bmp = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                bmp.setPixels(cachedRgbBytes,0,image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

                float ratio = (float)image.getWidth() / surfaceView.getHeight();

                // Crop Image.
                int bx = (int)(cropView.getLeft() * ratio);
                int by = (int)(cropView.getTop() * ratio);
                int bw = (int)(cropView.getWidth() * ratio);
                int bh = (int)(cropView.getHeight() * ratio);
                // Log.d(TAG, "CROPVIEW: " + String.valueOf(bx) + " " + String.valueOf(by)+ " " + String.valueOf(bw)+ " " + String.valueOf(bh));

                // Get surface offset. Screen & captured image don't have the same ratio and the
                // image is in "cover" mode, centered on screen so we must compute the offset.
                int h = (int)(image.getHeight() * ratio);
                int offsetY = (int)((image.getHeight() - h) * 0.5f);
                // Log.d(TAG, "BMP: " + String.valueOf(bmp.getWidth()) + " " + String.valueOf(bmp.getHeight()));
                // Crop and rotate to match screen + cropview.
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap cropped = Bitmap.createBitmap(bmp, by, offsetY + bx, bh, bw, matrix, true);
                // Log.d(TAG, "CROPPED: " + String.valueOf(cropped.getWidth()) + " " + String.valueOf(cropped.getHeight()));

                textRecognition.detect(cropped, 90, new TextRecognitionCallback() {
                    @Override
                    public void onSuccess(String t) {
                        if (t.equals("")) {
                            return;
                        }

                        // Hide cropView.
                        cropView.animate().alpha(0.0f);
                        btResize.animate().alpha(0.0f);

                        // Show Text.
                        text = t;
                        textView.setText(text);
                        textView.animate().alpha(1.0f);

                        // Get a screenshot of the desktop to add to ARCore's DB.
                        DesktopHTTPClient.getScreenshot(new DesktopHTTPClientCallback() {
                            @Override
                            public void onSuccess(Bitmap screenshot) {
                                Log.d(TAG, "Got Screenshot");
                                setAugmentedImage(screenshot);
                            }

                            @Override
                            public void onError(String err) {
                                Log.e(TAG, err);
                            }
                        });
                        // DesktopHTTPClient.setText(text);
                    }

                    @Override
                    public void onError(String err) {
                        Log.e(TAG, err.toString());
                    }
                });

            } catch (NotYetAvailableException e) {
                // This exception will routinely happen during startup, and is expected. cpuImageRenderer
                // will handle null image properly, and will just render the background.
            }
        }
    }

    private void updateResizeBt() {
        int w = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                56,
                this.getResources().getDisplayMetrics()
        );

        int m = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12,
                this.getResources().getDisplayMetrics()
        );

        LayoutParams params = new LayoutParams(w, w);
        int left = cropView.getLeft() + cropView.getWidth() - btResize.getWidth();
        int top = cropView.getTop() + cropView.getHeight() + (int)(w * 0.5) + m;
        params.setMargins(left, top, 0, 0);
        btResize.setLayoutParams(params);
    }

    private Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    public void hitTest(Frame frame, AugmentedImage ref) {
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean isOnScreen = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable.equals(ref)) {
                    isOnScreen = true;
                    // Send Position.
                    float[] p = ref.getCenterPose().getTranslation();
                    float[] t = hit.getHitPose().getTranslation();
                    t[0] -= p[0];
                    t[1] -= p[1];
                    t[2] -= p[2];
                    this.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    DesktopHTTPClient.setPosition(t[0] / widthMeter, t[1] / heightMeter);
                                    if (textView.getAlpha() > 0.5f) {
                                        DesktopHTTPClient.setText(text);
                                        textView.setAlpha(0.0f);
                                    }
                                }
                            });
                    break;
                }
            }
        }
        if (!isOnScreen && textView.getAlpha() < 0.5f) {
            this.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            DesktopHTTPClient.setText("");
                            textView.setAlpha(1.0f);
                        }
                    });
        }
    }

    private void configureSession() {
        config = new Config(session);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);

        // Create filter here with desired fps filters.
        CameraConfigFilter cameraConfigFilter =
                new CameraConfigFilter(session)
                        .setTargetFps(
                                EnumSet.of(
                                        CameraConfig.TargetFps.TARGET_FPS_30, CameraConfig.TargetFps.TARGET_FPS_60));
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        CameraConfig cameraConfig = getCameraConfigWithHighestResolution(cameraConfigs);
        session.setCameraConfig(cameraConfig);
    }

    private void updateTracking(Frame frame) {
        Collection<AugmentedImage> updatedAugmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);
        // Iterate to update augmentedImageMap, remove elements we cannot draw.
        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case TRACKING:
                    // Run HitTest to see if the phone is looking at the screen.
                    if (text != "") {
                        hitTest(frame, augmentedImage);
                    }

                    // Create a new anchor for newly found images.
                    if (!augmentedImageMap.containsKey(augmentedImage.getIndex())) {
                        Anchor centerPoseAnchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
                        augmentedImageMap.put(
                                augmentedImage.getIndex(), Pair.create(augmentedImage, centerPoseAnchor));
                    }
                    break;

                case STOPPED:
                    augmentedImageMap.remove(augmentedImage.getIndex());
                    break;

                default:
                    break;
            }
        }
    }

    private void setAugmentedImage(Bitmap bmp) {
        augmentedImageDatabase = new AugmentedImageDatabase(session);
        augmentedImageDatabase.addImage("img", bmp, widthMeter);
        // Set DB.
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        session.configure(config);
    }

    private CameraConfig getCameraConfigWithHighestResolution(
            List<CameraConfig> cameraConfigs) {
        CameraConfig cameraConfig = cameraConfigs.get(0);
        for (int index = 1; index < cameraConfigs.size(); index++) {
            if (cameraConfigs.get(index).getImageSize().getHeight()
                    > cameraConfig.getImageSize().getHeight()) {
                cameraConfig = cameraConfigs.get(index);
            }
        }
        return cameraConfig;
    }
}
