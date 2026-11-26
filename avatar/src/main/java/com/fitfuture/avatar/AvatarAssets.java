package com.fitfuture.avatar;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;

/**
 * assets/avatar/ 의 파츠 PNG를 읽고 메모리에 캐시한다.
 *
 * <p>assets는 기기 밀도에 따라 자동 확대·축소되지 않으므로(drawable-nodpi와 같은 효과)
 * Figma에서 내보낸 크기 그대로 읽힌다. 파일 이름을 코드에서 조합하기 쉬운 것도 assets를 쓴 이유다.
 */
public final class AvatarAssets {

    private static final String TAG = "AvatarAssets";
    private static final int CACHE_BYTES = 24 * 1024 * 1024;

    private static AvatarAssets instance;

    private final AssetManager assets;
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private AvatarAssets(Context context) {
        assets = context.getApplicationContext().getAssets();
    }

    public static synchronized AvatarAssets get(Context context) {
        if (instance == null) instance = new AvatarAssets(context);
        return instance;
    }

    /** 없으면 null (로그를 남기고 그 레이어만 건너뜀). */
    public synchronized Bitmap load(String path) {
        Bitmap cached = cache.get(path);
        if (cached != null) return cached;
        try (InputStream in = assets.open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            if (bmp != null) cache.put(path, bmp);
            return bmp;
        } catch (IOException e) {
            Log.w(TAG, "파츠 PNG가 없어요: " + path);
            return null;
        }
    }
}
