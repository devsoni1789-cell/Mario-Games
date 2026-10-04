package com.superbros.retrorunner;

import android.content.Context;
import android.content.SharedPreferences;

final class SaveManager {
    private final SharedPreferences p;
    SaveManager(Context c) { p = c.getSharedPreferences("retrorunner_save", Context.MODE_PRIVATE); }
    int getStars() { return p.getInt("stars", 0); }
    void addStars(int n) { p.edit().putInt("stars", Math.max(getStars(), n)).apply(); }
    boolean isMuted() { return p.getBoolean("muted", false); }
    void setMuted(boolean v) { p.edit().putBoolean("muted", v).apply(); }
}
