package dev.codex.felixblur;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class BlurSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout controls;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BlurSettingsProvider.PREFS, 0);
        rebuild();
    }

    private void rebuild() {
        ScrollView scroll = new ScrollView(this);
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        controls.setPadding(p, p, p, p);

        TextView title = text("Felix Blur Control", 26);
        controls.addView(title);
        TextView subtitle = text("Настройка прозрачности и силы системного размытия", 15);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        controls.addView(subtitle);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        addPreset(presets, "Stock", new int[]{100,100,100,100,100,100,100,100,100});
        addPreset(presets, "Glass", new int[]{62,58,55,58,65,60,60,80,115});
        addPreset(presets, "Liquid", BlurSettingsProvider.DEFAULTS);
        controls.addView(presets);

        addSlider("Шторка и быстрые настройки", "shade", 15, 100);
        addSlider("Карточки уведомлений", "notifications", 15, 100);
        addSlider("Панель громкости", "volume", 15, 100);
        addSlider("Меню питания", "power", 15, 100);
        addSlider("Экран блокировки", "lockscreen", 15, 100);
        addSlider("Папки Pixel Launcher", "folders", 15, 100);
        addSlider("Меню рабочего стола", "launcher_menu", 15, 100);
        addSlider("Pixel Dock Glass", "dock", 15, 100);
        addSlider("Сила размытия", "blur", 70, 180);

        TextView note = text("Меньшее значение прозрачности делает фон светлее и лучше показывает содержимое под панелью. Изменения применяются при следующем открытии элемента.", 13);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(18), 0, dp(12));
        controls.addView(note);
        scroll.addView(controls);
        setContentView(scroll);
    }

    private void addPreset(LinearLayout row, String name, final int[] values) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
        button.setOnClickListener(v -> {
            SharedPreferences.Editor e = prefs.edit();
            for (int i = 0; i < BlurSettingsProvider.KEYS.length; i++) e.putInt(BlurSettingsProvider.KEYS[i], values[i]);
            e.apply();
            rebuild();
        });
    }

    private void addSlider(String label, String key, int min, int max) {
        TextView value = text("", 16);
        value.setPadding(0, dp(18), 0, 0);
        controls.addView(value);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int def = defaultFor(key);
        bar.setProgress(prefs.getInt(key, def) - min);
        controls.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
        Runnable update = () -> value.setText(label + ": " + (bar.getProgress() + min) + "%");
        update.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                update.run();
                if (fromUser) prefs.edit().putInt(key, progress + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
    }

    private int defaultFor(String key) {
        for (int i = 0; i < BlurSettingsProvider.KEYS.length; i++)
            if (BlurSettingsProvider.KEYS[i].equals(key)) return BlurSettingsProvider.DEFAULTS[i];
        return 100;
    }

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setGravity(Gravity.START);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
