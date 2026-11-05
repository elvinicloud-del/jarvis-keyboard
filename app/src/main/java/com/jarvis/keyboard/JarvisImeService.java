package com.jarvis.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.content.Context;
import java.util.Locale;

public class JarvisImeService extends InputMethodService {

    private String language = "RU";
    private boolean symbols = false;
    private boolean shifted = false;
    private LinearLayout keyboard;
    private float spaceDownX;

    private final int background = Color.rgb(12, 19, 29);
    private final int keyColor = Color.rgb(30, 43, 58);
    private final int cyan = Color.rgb(0, 210, 245);

    @Override
    public View onCreateInputView() {
        return createKeyboard();
    }

    private View createKeyboard() {
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(4), dp(5), dp(4), dp(5));
        keyboard.setBackgroundColor(background);

        addHeader();

        if (symbols) {
            addSymbolRows();
        } else {
            addLetterRows();
        }

        addBottomRow();
        return keyboard;
    }

    private void addHeader() {
        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(cyan);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(2), 0, dp(5));
        keyboard.addView(title);
    }

    private void addLetterRows() {
        String[] rows;

        if (language.equals("RU")) {
            rows = new String[]{
                "ЙЦУКЕНГШЩЗХ",
                "ФЫВАПРОЛДЖЭ",
                "ЯЧСМИТЬБЮ"
            };
        } else if (language.equals("AZ")) {
            rows = new String[]{
                "QWERTYUIOPÜĞ",
                "ASDFGHJKLÖƏ",
                "ZXCVBNMÇŞ"
            };
        } else {
            rows = new String[]{
                "QWERTYUIOP",
                "ASDFGHJKL",
                "ZXCVBNM"
            };
        }

        String[] extras;

        if (language.equals("RU")) {
            extras = new String[]{"1234567890", "", ""};
        } else {
            extras = new String[]{"1234567890", "", ""};
        }

        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = makeRow();

            if (r == 2) {
                addKey(row, "⇧", 1, "SHIFT");
            }

            for (int i = 0; i < rows[r].length(); i++) {
                String letter = String.valueOf(rows[r].charAt(i));
                String extra = "";

                if (r == 0 && i < extras[0].length()) {
                    extra = String.valueOf(extras[0].charAt(i));
                }

                addKey(row, letter, 1, extra);
            }

            if (r == 2) {
                addKey(row, "⌫", 1, "DELETE");
            }

            keyboard.addView(row);
        }
    }

    private void addSymbolRows() {
        String[][] rows = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"@","#","$","%","&","-","+","(",")","/"},
            {"*","\"","'",":",";","!","?","=","_","."}
        };

        for (String[] values : rows) {
            LinearLayout row = makeRow();
            for (String value : values) {
                addKey(row, value, 1, "");
            }
            keyboard.addView(row);
        }

        LinearLayout row = makeRow();
        addKey(row, "ABC", 1, "ABC");
        addKey(row, "€", 1, "");
        addKey(row, "£", 1, "");
        addKey(row, "⌫", 1, "DELETE");
        keyboard.addView(row);
    }

    private void addBottomRow() {
        LinearLayout row = makeRow();

        addKey(row, symbols ? "ABC" : "?123", 1, "SYMBOLS");
        addKey(row, ",", 1, "");

        TextView space = new TextView(this);
        space.setText("Пробел · " + language);
        space.setTextColor(Color.WHITE);
        space.setTextSize(13);
        space.setGravity(Gravity.CENTER);
        space.setBackground(keyBackground());

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(0, dp(48), 5);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(space, p);

        space.setOnClickListener(v -> commit(" "));
        space.setOnLongClickListener(v -> {
            chooseLanguage();
            return true;
        });

        space.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                spaceDownX = event.getX();
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - spaceDownX;

                if (Math.abs(dx) > dp(35)) {
                    changeLanguage(dx < 0 ? 1 : -1);
                    return true;
                }
            }
            return false;
        });

        addKey(row, ".", 1, "");
        addKey(row, "↵", 1, "ENTER");

        keyboard.addView(row);
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void addKey(
        LinearLayout row, String value, int weight, String extra
    ) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(keyBackground());

        TextView main = new TextView(this);
        main.setText(shifted ? value.toUpperCase() : value);
        main.setTextColor(Color.WHITE);
        main.setTextSize(17);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setGravity(Gravity.CENTER);

        TextView small = new TextView(this);
        small.setText(extra);
        small.setTextColor(cyan);
        small.setTextSize(9);
        small.setGravity(Gravity.CENTER);

        if (!extra.isEmpty() && extra.length() <= 2) {
            key.addView(small);
        }

        key.addView(main);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(0, dp(48), weight);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(key, params);

        key.setOnClickListener(v -> press(value));

        key.setOnLongClickListener(v -> {
            if (extra.equals("SHIFT")) {
                shifted = !shifted;
                refresh();
            } else if (extra.equals("DELETE")) {
                delete();
            } else if (extra.equals("SYMBOLS")) {
                symbols = !symbols;
                refresh();
            } else if (extra.equals("ABC")) {
                symbols = false;
                refresh();
            } else {
                showAlternatives(value);
            }
            return true;
        });
    }

    private GradientDrawable keyBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(keyColor);
        bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1), Color.rgb(0, 125, 165));
        return bg;
    }

    private void press(String value) {
        switch (value) {
            case "⇧":
                shifted = !shifted;
                refresh();
                return;
            case "⌫":
                delete();
                return;
            case "?123":
                symbols = true;
                refresh();
                return;
            case "ABC":
                symbols = false;
                refresh();
                return;
            case "↵":
                commit("\n");
                return;
            default:
                String output = shifted
                    ? value.toUpperCase(locale())
                    : value.toLowerCase(locale());
                commit(output);
                if (shifted) {
                    shifted = false;
                    refresh();
                }
        }
    }

    private void showAlternatives(String value) {
        String options = "";

        if (language.equals("RU")) {
            if (value.equals("Е")) options = "Ё";
            if (value.equals("Ь")) options = "Ъ";
        } else if (language.equals("AZ")) {
            if (value.equals("I")) options = "İ";
            if (value.equals("U")) options = "Ü";
            if (value.equals("O")) options = "Ö";
            if (value.equals("G")) options = "Ğ";
            if (value.equals("S")) options = "Ş";
            if (value.equals("C")) options = "Ç";
        }

        if (options.isEmpty()) return;

        final String[] choices = options.split("");
        new AlertDialog.Builder(this)
            .setItems(choices, (dialog, which) ->
                commit(shifted
                    ? choices[which].toUpperCase(locale())
                    : choices[which].toLowerCase(locale())))
            .show();
    }

    private void chooseLanguage() {
        String[] names = {"Русский", "English", "Azərbaycan"};

        new AlertDialog.Builder(this)
            .setTitle("Выбор языка")
            .setItems(names, (dialog, which) -> {
                language = new String[]{"RU", "EN", "AZ"}[which];
                refresh();
            })
            .show();
    }

    private void changeLanguage(int direction) {
        String[] languages = {"RU", "EN", "AZ"};
        int index = 0;

        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(language)) index = i;
        }

        index = (index + direction + 3) % 3;
        language = languages[index];
        refresh();
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private void delete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingText(1, 0);
    }

    private Locale locale() {
        if (language.equals("AZ")) return new Locale("az");
        if (language.equals("EN")) return Locale.ENGLISH;
        return new Locale("ru");
    }

    private void refresh() {
        setInputView(createKeyboard());
    }

    private int dp(int value) {
        return (int) (value *
            getResources().getDisplayMetrics().density);
    }
}
