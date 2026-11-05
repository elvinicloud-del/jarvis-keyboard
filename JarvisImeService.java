package com.jarvis.keyboard;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class JarvisImeService extends InputMethodService {
    private String language = "RU";
    private boolean shifted = false;
    private LinearLayout root;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(7), dp(5), dp(7));
        root.setBackgroundColor(Color.rgb(10, 18, 30));
        buildKeyboard();
        return root;
    }

    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(7));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void addRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(49));
        rp.bottomMargin = dp(5);
        root.addView(row, rp);

        for (String key : keys) {
            Button b = new Button(this);
            b.setText(shifted ? key.toUpperCase() : key);
            b.setTextColor(Color.WHITE);
            b.setTextSize(17);
            b.setAllCaps(false);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setPadding(0, 0, 0, 0);
            b.setBackground(bg(Color.rgb(25, 39, 58), Color.rgb(0, 190, 220)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, key.equals("⌫") || key.equals("⇧") ? 1.35f : 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            row.addView(b, p);
            b.setOnClickListener(v -> onKey(key));
        }
    }

    private String[] letters(String row) {
        if ("AZ".equals(language)) {
            if (row.equals("qwertyuiop")) return new String[]{"q","w","e","r","t","y","u","ı","o","p"};
            if (row.equals("asdfghjkl")) return new String[]{"a","s","d","f","g","h","j","k","l","ə"};
            return new String[]{"⇧","z","x","c","ç","v","b","n","m","ş","⌫"};
        }
        if ("RU".equals(language)) {
            if (row.equals("qwertyuiop")) return new String[]{"й","ц","у","к","е","н","г","ш","щ","з","х"};
            if (row.equals("asdfghjkl")) return new String[]{"ф","ы","в","а","п","р","о","л","д","ж","э"};
            return new String[]{"⇧","я","ч","с","м","и","т","ь","б","ю","⌫"};
        }
        if (row.equals("qwertyuiop")) return new String[]{"q","w","e","r","t","y","u","i","o","p"};
        if (row.equals("asdfghjkl")) return new String[]{"a","s","d","f","g","h","j","k","l"};
        return new String[]{"⇧","z","x","c","v","b","n","m","⌫"};
    }

    private void buildKeyboard() {
        root.removeAllViews();
        TextView brand = new TextView(this);
        brand.setText("J A R V I S   •   " + language);
        brand.setTextColor(Color.rgb(0, 220, 240));
        brand.setTextSize(12);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(24)));

        addRow(letters("qwertyuiop"));
        addRow(letters("asdfghjkl"));
        addRow(letters("zxcvbnm"));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(48)));
        addBottomButton(bottom, language, 1.2f, () -> {
            language = "RU".equals(language) ? "EN" : "RU".equals(language) ? "AZ" : "RU";
            buildKeyboard();
        });
        addBottomButton(bottom, "😊", 0.9f, () -> commit("😊"));
        addBottomButton(bottom, ",", 0.7f, () -> commit(","));
        addBottomButton(bottom, "Пробел", 4f, () -> commit(" "));
        addBottomButton(bottom, ".", 0.7f, () -> commit("."));
        addBottomButton(bottom, "↵", 1f, this::enter);
        addBottomButton(bottom, "⌄", 0.8f, this::requestHideSelf);
    }

    private void addBottomButton(LinearLayout row, String label, float weight, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(bg(Color.rgb(18, 55, 72), Color.rgb(0, 210, 230)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight);
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private void commit(String value) {
        if (getCurrentInputConnection() != null) getCurrentInputConnection().commitText(value, 1);
    }

    private void onKey(String key) {
        if ("⌫".equals(key)) {
            if (getCurrentInputConnection() != null)
                getCurrentInputConnection().deleteSurroundingText(1, 0);
        } else if ("⇧".equals(key)) {
            shifted = !shifted;
            buildKeyboard();
        } else {
            commit(shifted ? key.toUpperCase() : key);
            shifted = false;
        }
    }

    private void enter() {
        if (getCurrentInputConnection() == null) return;
        int action = EditorInfo.IME_ACTION_NONE;
        if (getCurrentInputEditorInfo() != null)
            action = getCurrentInputEditorInfo().imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE)
            getCurrentInputConnection().performEditorAction(action);
        else
            getCurrentInputConnection().commitText("\n", 1);
    }
}
