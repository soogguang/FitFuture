package com.fitfuture.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.fitfuture.app.MainActivity;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;

/** Camera belongs to S10; leaving the screen invalidates pending binding/capture callbacks. */
public final class MealCamera {
  private final MainActivity activity;
  private final ActivityResultLauncher<String> permission;
  private PreviewView view;
  private TextView status;
  private TextView shutter;
  private ProcessCameraProvider provider;
  private Preview preview;
  private ImageCapture capture;
  private Camera activeCamera;
  private int generation;
  private boolean ready, saving, settingsRequired, starting;

  public MealCamera(MainActivity activity) {
    this.activity = activity;
    permission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
      if (view == null) return;
      if (granted) start();
      else {
        settingsRequired = !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        message("음식을 촬영하려면 카메라 권한을 허용해 주세요.", "카메라 권한 허용", true);
      }
    });
  }

  public void attach(PreviewView view, TextView status, TextView shutter) {
    this.view = view;
    this.status = status;
    this.shutter = shutter;
    view.getPreviewStreamState().observe(activity, stream -> {
      if (this.view != view || saving) return;
      ready = stream == PreviewView.StreamState.STREAMING && capture != null;
      if (ready) {
        status.setVisibility(View.GONE);
        shutter.setText("사진 촬영");
        shutter.setEnabled(true);
      } else if (capture != null) {
        message("카메라를 준비하고 있어요…", "카메라 준비 중", false);
      }
    });
    if (allowed()) view.post(this::start);
    else {
      message("카메라 권한을 허용하면 음식이 여기에 보여요.", "카메라 권한 허용", true);
      permission.launch(Manifest.permission.CAMERA);
    }
  }

  private boolean allowed() {
    return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  private void message(String text, String label, boolean enabled) {
    if (status == null) return;
    status.setText(text);
    status.setVisibility(View.VISIBLE);
    shutter.setText(label);
    shutter.setEnabled(enabled);
  }

  private void start() {
    if (view == null || !allowed() || starting) return;
    starting = true;
    int token = generation;
    PreviewView target = view;
    message("카메라를 준비하고 있어요…", "카메라 준비 중", false);
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
    future.addListener(() -> {
      if (token != generation || target != view || activity.isDestroyed()) return;
      try {
        provider = future.get();
        if (activeCamera != null) activeCamera.getCameraInfo().getCameraState().removeObservers(activity);
        if (preview != null) provider.unbind(preview);
        if (capture != null) provider.unbind(capture);
        CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        if (!provider.hasCamera(selector)) throw new IllegalStateException("No camera");
        preview = new Preview.Builder().build();
        capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(target.getDisplay() == null ? android.view.Surface.ROTATION_0 : target.getDisplay().getRotation()).build();
        preview.setSurfaceProvider(target.getSurfaceProvider());
        UseCaseGroup.Builder group = new UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture);
        if (target.getViewPort() != null) group.setViewPort(target.getViewPort());
        Camera camera = provider.bindToLifecycle(activity, selector, group.build());
        activeCamera = camera;
        camera.getCameraInfo().getCameraState().observe(activity, state -> {
          if (token != generation || target != view || state.getError() == null) return;
          ready = false;
          message("카메라를 사용할 수 없어요. 다른 카메라 앱을 닫고 다시 시도해 주세요.", "카메라 다시 연결", true);
        });
        starting = false;
      } catch (Exception error) {
        starting = false;
        ready = false;
        capture = null;
        message("카메라를 열지 못했어요. 기기의 카메라 설정을 확인해 주세요.", "카메라 다시 연결", true);
      }
    }, ContextCompat.getMainExecutor(activity));
  }

  public void takePhoto() {
    if (view == null || saving) return;
    if (!allowed()) {
      if (settingsRequired) {
        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + activity.getPackageName())));
      } else permission.launch(Manifest.permission.CAMERA);
      return;
    }
    if (!ready || capture == null) { start(); return; }
    final File file;
    try {
      File directory = new File(activity.getFilesDir(), "photos");
      if (!directory.exists() && !directory.mkdirs()) throw new IOException("Photo directory");
      file = File.createTempFile("meal-", ".jpg", directory);
    } catch (IOException error) {
      message("사진을 저장할 공간을 확인해 주세요.", "다시 촬영", true);
      return;
    }
    int token = generation;
    saving = true;
    message("사진을 촬영하고 있어요…", "촬영 중", false);
    capture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(activity), new ImageCapture.OnImageSavedCallback() {
          @Override public void onImageSaved(ImageCapture.OutputFileResults result) {
            if (token != generation || view == null || !activity.screen.equals("s10")) {
              file.delete();
              return;
            }
            saving = false;
            Uri uri = Uri.parse("content://" + activity.getPackageName() + ".photos/" + file.getName());
            activity.acceptMealPhoto(uri);
          }
          @Override public void onError(ImageCaptureException error) {
            file.delete();
            if (token != generation || view == null) return;
            saving = false;
            message("사진을 찍지 못했어요. 다시 촬영해 주세요.", "다시 촬영", true);
          }
        });
  }

  public void close() {
    generation++;
    ready = false;
    saving = false;
    starting = false;
    if (view != null) view.getPreviewStreamState().removeObservers(activity);
    if (activeCamera != null) activeCamera.getCameraInfo().getCameraState().removeObservers(activity);
    if (provider != null) {
      if (preview != null) provider.unbind(preview);
      if (capture != null) provider.unbind(capture);
    }
    view = null;
    status = null;
    shutter = null;
    preview = null;
    capture = null;
    activeCamera = null;
  }

  public void resume() {
    if (view != null && allowed() && capture == null && !saving) view.post(this::start);
  }
}
