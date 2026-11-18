package com.fitfuture.app.data;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.*;

/** Real recognition adapter. Nutrition remains the existing demo until DB integration. */
public final class RemoteFoodAnalysis implements FeatureServices.FoodAnalysisService {
  private final Context context;
  private final String origin;
  private final FeatureServices.FoodAnalysisService nutrition;

  public RemoteFoodAnalysis(Context context, String origin, FeatureServices.FoodAnalysisService nutrition) {
    this.context = context.getApplicationContext();
    this.origin = origin.replaceAll("/+$", "");
    this.nutrition = nutrition;
  }

  @Override
  public Meal recognize(String text, String localPhotoUri) throws ModuleException {
    HttpURLConnection connection = null;
    try {
      JSONObject request = new JSONObject();
      request.put("text", text == null ? "" : text.trim());
      if (localPhotoUri != null && !localPhotoUri.isEmpty()) {
        request.put("image", new JSONObject().put("mimeType", "image/jpeg")
            .put("data", Base64.encodeToString(photo(Uri.parse(localPhotoUri)), Base64.NO_WRAP)));
      }
      connection = (HttpURLConnection) new URL(origin + "/food/recognize").openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(55000);
      connection.setInstanceFollowRedirects(false);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(body.length);
      try (OutputStream stream = connection.getOutputStream()) { stream.write(body); }
      int status = connection.getResponseCode();
      if (status == 422) throw new ModuleException("RECOGNITION", "음식을 인식하지 못했어요.");
      if (status != 200) throw new ModuleException("NETWORK", "음식 인식 서버에 연결하지 못했어요.");
      JSONObject response;
      try (InputStream stream = connection.getInputStream()) {
        response = new JSONObject(new String(readLimited(stream, 65536), StandardCharsets.UTF_8));
      }
      Meal meal = parse(response);
      meal.photoUri = localPhotoUri == null ? "" : localPhotoUri;
      return meal;
    } catch (ModuleException e) {
      throw e;
    } catch (JSONException e) {
      throw new ModuleException("RECOGNITION", "인식 결과를 확인하지 못했어요.");
    } catch (IOException | SecurityException e) {
      throw new ModuleException("NETWORK", "사진 또는 서버 연결을 확인해 주세요.");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  public static Meal parse(JSONObject response) throws JSONException, ModuleException {
    String name = response.getString("name").trim();
    JSONArray foods = response.getJSONArray("foods");
    if (name.isEmpty() || name.length() > 100 || foods.length() == 0 || foods.length() > 20)
      throw new ModuleException("RECOGNITION", "인식된 음식이 없어요.");
    Meal meal = new Meal(name);
    for (int i = 0; i < foods.length(); i++) {
      JSONObject food = foods.getJSONObject(i);
      String foodName = food.getString("name").trim();
      Object value = food.get("grams");
      if (!(value instanceof Number)) throw new JSONException("grams must be numeric");
      double grams = ((Number) value).doubleValue();
      if (foodName.isEmpty() || foodName.length() > 100 || !Double.isFinite(grams) || grams <= 0 || grams > 5000)
        throw new ModuleException("RECOGNITION", "음식 이름과 추정량을 확인하지 못했어요.");
      meal.foods.add(new FoodPortion(foodName, grams));
    }
    return meal;
  }

  @Override
  public Nutrition analyze(List<FoodPortion> foods) throws ModuleException {
    return nutrition.analyze(foods);
  }

  private byte[] photo(Uri uri) throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(stream, null, options);
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap;
    try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
      bitmap = BitmapFactory.decodeStream(stream, null, options);
    }
    if (bitmap == null) throw new IOException("Invalid image");
    try {
      Matrix matrix = new Matrix();
      try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
        int orientation = new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        switch (orientation) {
          case 2 -> matrix.setScale(-1, 1);
          case 3 -> matrix.setRotate(180);
          case 4 -> matrix.setScale(1, -1);
          case 5 -> { matrix.setRotate(90); matrix.postScale(-1, 1); }
          case 6 -> matrix.setRotate(90);
          case 7 -> { matrix.setRotate(270); matrix.postScale(-1, 1); }
          case 8 -> matrix.setRotate(270);
        }
      } catch (IOException ignored) { /* Images without EXIF still decode. */ }
      if (!matrix.isIdentity()) {
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; }
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output) || output.size() > 4 * 1024 * 1024)
        throw new IOException("Image too large");
      return output.toByteArray();
    } finally { bitmap.recycle(); }
  }

  private static byte[] readLimited(InputStream stream, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int count;
    while ((count = stream.read(buffer)) != -1) {
      if (out.size() + count > limit) throw new IOException("Response too large");
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }
}
