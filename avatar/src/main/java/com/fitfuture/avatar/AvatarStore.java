package com.fitfuture.avatar;

import android.content.Context;
import android.content.SharedPreferences;

import com.fitfuture.avatar.core.AvatarCatalog.Category;
import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.BodyPreset;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * 아바타 구성 저장·불러오기. 원본 사진은 저장하지 않는다.
 * Firestore에 올릴 때도 toJson() 결과를 users/{uid} 문서의 avatar 필드에 넣으면 된다.
 *
 * <pre>
 * { "version": 3, "bodyPreset": "female", "bodyCell": 3,
 *   "face": "round", "eyes": "a_m", "brows": "05", "nose": "01", "mouth": "e02",
 *   "hair": "f05", "hairBack": "b03", "outfit": "hoodie_mint", "accessory": "none",
 *   "skin": "original", "hairColor": "original" }
 * </pre>
 * bodyCell 0은 "아직 확정 안 함 → BMI 초안 사용".
 * 필드가 없거나 카탈로그에 없는 값이면 기본값을 쓴다 (예전 저장 데이터 호환).
 */
public final class AvatarStore {

    public static final int VERSION = 3;
    private static final String PREFS = "fitfuture_avatar";
    private static final String KEY = "spec";

    private AvatarStore() {}

    public static boolean exists(Context context) {
        return prefs(context).contains(KEY);
    }

    public static void save(Context context, AvatarSpec spec) {
        prefs(context).edit().putString(KEY, toJson(spec).toString()).apply();
    }

    /** 저장된 값이 없거나 깨졌으면 fallbackPreset의 기본 아바타. */
    public static AvatarSpec load(Context context, BodyPreset fallbackPreset) {
        String json = prefs(context).getString(KEY, null);
        if (json == null) return AvatarSpec.defaults(fallbackPreset);
        try {
            return fromJson(new JSONObject(json), fallbackPreset);
        } catch (JSONException e) {
            return AvatarSpec.defaults(fallbackPreset);
        }
    }

    /** JSON 키 이름 (Firestore 필드 이름과 같게 유지). */
    static String key(Category c) {
        switch (c) {
            case FACE: return "face";
            case EYES: return "eyes";
            case BROWS: return "brows";
            case NOSE: return "nose";
            case MOUTH: return "mouth";
            case HAIR: return "hair";
            case HAIR_BACK: return "hairBack";
            case OUTFIT: return "outfit";
            case ACCESSORY: return "accessory";
            default: throw new IllegalArgumentException(String.valueOf(c));
        }
    }

    public static JSONObject toJson(AvatarSpec s) {
        JSONObject o = new JSONObject();
        try {
            o.put("version", VERSION);
            o.put("bodyPreset", s.preset.folder);
            o.put("bodyCell", s.bodyCell);
            for (Category c : Category.values()) o.put(key(c), s.get(c));
            o.put("skin", s.skin);
            o.put("hairColor", s.hairColor);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    public static AvatarSpec fromJson(JSONObject o, BodyPreset fallbackPreset) {
        BodyPreset preset = o.has("bodyPreset")
                ? BodyPreset.fromFolder(o.optString("bodyPreset")) : fallbackPreset;
        Map<Category, String> parts = new EnumMap<>(Category.class);
        for (Category c : Category.values()) parts.put(c, str(o, key(c)));
        return new AvatarSpec(preset, o.optInt("bodyCell", AvatarSpec.CELL_AUTO), parts,
                str(o, "skin"), str(o, "hairColor"));
    }

    private static String str(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optString(key) : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
