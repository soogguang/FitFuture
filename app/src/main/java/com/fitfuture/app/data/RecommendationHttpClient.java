package com.fitfuture.app.data;

import com.fitfuture.app.domain.Models.ModuleException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Called on FeatureServices' worker thread. No provider keys are stored on device. */
public final class RecommendationHttpClient {
  private final String baseUrl;

  public RecommendationHttpClient(String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  public JSONObject post(String path, JSONObject body) throws ModuleException {
    HttpURLConnection connection = null;
    try {
      if (baseUrl.isEmpty()) throw new ModuleException("CONFIGURATION", "추천 서버 주소를 설정해 주세요.");
      URL url = new URL(baseUrl + path);
      if (!url.getProtocol().equals("https") && !url.getProtocol().equals("http"))
        throw new ModuleException("CONFIGURATION", "서버 URL을 확인해 주세요.");
      connection = (HttpURLConnection) url.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(40000);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      try (OutputStream output = connection.getOutputStream()) {
        output.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      int status = connection.getResponseCode();
      InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
      if (stream == null) throw new ModuleException("SERVER", "추천 서버 응답을 확인해 주세요.");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream input = stream) {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (bytes.size() + count > 524288) throw new ModuleException("SERVER", "서버 응답이 너무 커요.");
          bytes.write(buffer, 0, count);
        }
      }
      JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
      if (status < 200 || status >= 300) {
        JSONObject error = json.optJSONObject("error");
        throw new ModuleException(error == null ? "SERVER" : error.optString("code", "SERVER"),
            error == null ? "추천 요청을 처리하지 못했어요." : error.optString("message", "서버 오류"));
      }
      return json;
    } catch (SocketTimeoutException ex) {
      throw new ModuleException("TIMEOUT", "서버 응답 시간이 초과됐어요.");
    } catch (IOException ex) {
      throw new ModuleException("NETWORK", "추천 서버에 연결할 수 없어요.");
    } catch (JSONException ex) {
      throw new ModuleException("SERVER", "추천 서버 응답 형식을 확인해 주세요.");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }
}
