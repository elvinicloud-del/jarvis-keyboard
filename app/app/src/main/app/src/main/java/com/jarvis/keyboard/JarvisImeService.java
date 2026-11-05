package com.jarvis.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.Gravity;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;

public class JarvisImeService extends InputMethodService {

    private String language = "RU";
    private LinearLayout keyboard;
    private final String[][] layouts = {
        {"ЙЦУКЕНГШЩЗХ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮ"},
        {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"},
        {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"}
    };

    @Override
    public View onCreateInputView() {
        return createKeyboard();
    }

    private View createKeyboard() {
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(5, 8, 5, 8);
        keyboard.setBackgroundColor(Color.rgb(12, 19, 29));

        TextViewTitle();

        String[][] rows = layouts[language.equals("RU") ? 0 : 1];

        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (char c : rows[r].toCharArray()) {
                addKey(row, String.valueOf(c), 1);
            }

            if (language.equals("AZ") && r == 2) {
                for (String c : new String[]{"Ə", "Ğ", "İ", "Ö", "Ş", "Ç", "Ü"}) {
                    addKey(row, c, 1);
                }
            }

            keyboard.addView(row);
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);

        addKey(bottom, "🌐", 1);
        addKey(bottom, "?123", 1);
        addKey(bottom, "☻", 1);
        addKey(bottom, "ПРОБЕЛ", 4);
        addKey(bottom, "⌫", 1);
        addKey(bottom, "↵", 1);

        keyboard.addView(bottom);

        return keyboard;
    }

    private void TextViewTitle() {
        Button title = new Button(this);
        title.setText("J.A.R.V.I.S.  •  " + language);
        title.setTextColor(Color.rgb(0, 220, 255));
        title.setTextSize(13);
        title.setBackgroundColor(Color.TRANSPARENT);
        keyboard.addView(title);
    }

    private void addKey(LinearLayout row, String value, int weight) {
        Button key = new Button(this);
        key.setText(value);
        key.setTextSize(16);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setTextColor(Color.WHITE);
        key.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(30, 43, 58));
        bg.setCornerRadius(9);
        bg.setStroke(1, Color.rgb(0, 150, 190));
        key.setBackground(bg);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(0, dp(47), weight);
        params.setMargins(2, 3, 2, 3);
        row.addView(key, params);

        key.setOnClickListener(v -> press(value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void press(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (value) {
            case "🌐":
                language = language.equals("RU") ? "EN" :
                           language.equals("EN") ? "AZ" : "RU";
                setInputView(createKeyboard());
                break;

            case "ПРОБЕЛ":
                ic.commitText(" ", 1);
                break;

            case "⌫":
                ic.deleteSurroundingText(1, 0);
                break;

            case "↵":
                ic.commitText("\n", 1);
                break;

            case "?123":
                ic.commitText("123", 1);
                break;

            case "☻":
                ic.commitText("😊", 1);
                break;

            default:
                ic.commitText(value.toLowerCase(), 1);
                break;
        }
    }
}
