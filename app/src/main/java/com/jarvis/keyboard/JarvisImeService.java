
package com.jarvis.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.PopupWindow;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Vibrator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class JarvisImeService extends InputMethodService {

    private String language = "RU";
    private boolean symbols = false;

    // Управление регистром
    private boolean capsLock = false;
    private boolean manualLowerCase = false;
    private long lastShiftTap = 0;

    private boolean vibrationEnabled = true;
    private int vibrationDuration = 25;

    private int keyboardHeight = 340;
    private int keyHeight = 58;
    private int letterSize = 20;

    private LinearLayout keyboard;
    private LinearLayout suggestionsRow;
    private SharedPreferences prefs;

    private final ArrayList<TextView> letterKeyViews =
        new ArrayList<>();

    private final ArrayList<String> letterKeyValues =
        new ArrayList<>();

    private final TreeMap<String, Integer> russianWords =
        new TreeMap<>();

    private volatile boolean dictionaryLoaded = false;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private boolean deletePressed = false;
    private boolean deleteHeld = false;

    private int deleteRepeatInterval = 180;

    private final int background = Color.rgb(12, 19, 29);
    private final int keyColor = Color.rgb(30, 43, 58);
    private final int cyan = Color.rgb(0, 210, 245);

    // Выбор символов движением пальца
    private PopupWindow symbolPopup;
    private final ArrayList<TextView> symbolViews =
        new ArrayList<>();
    private final ArrayList<String> symbolValues =
        new ArrayList<>();

    private boolean dotGestureActive = false;
    private boolean symbolPopupShown = false;
    private int selectedSymbol = -1;

    private float lastTouchX = 0;
    private float lastTouchY = 0;

    private final Runnable deleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (!deletePressed) return;

            deleteHeld = true;
            deleteOne(false);

            handler.postDelayed(this, deleteRepeatInterval);

            deleteRepeatInterval = Math.max(
                45,
                deleteRepeatInterval - 20
            );
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(
            "jarvis_settings", MODE_PRIVATE
        );

        language = prefs.getString("language", "RU");
        vibrationEnabled = prefs.getBoolean("vibration", true);
        vibrationDuration = prefs.getInt("vibration_duration", 25);

        keyboardHeight = prefs.getInt("keyboard_height", 340);
        keyHeight = prefs.getInt("key_height", 58);
        letterSize = prefs.getInt("letter_size", 20);

        loadRussianDictionaryInBackground();
    }

    private void loadRussianDictionaryInBackground() {
        new Thread(() -> {
            TreeMap<String, Integer> loaded = new TreeMap<>();

            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        getAssets().open("ru_50k.txt"),
                        "UTF-8"
                    )
                );

                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");

                    String word = parts[0].toLowerCase(
                        new Locale("ru")
                    );

                    int frequency = 1;

                    if (parts.length > 1) {
                        try {
                            frequency = Integer.parseInt(parts[1]);
                        } catch (Exception ignored) {}
                    }

                    if (word.matches("[а-яё-]+")) {
                        loaded.put(word, frequency);
                    }
                }

                reader.close();

            } catch (Exception e) {
                Log.e("JARVIS", "Dictionary loading error", e);
            }

            synchronized (russianWords) {
                russianWords.putAll(loaded);
            }

            dictionaryLoaded = true;

            handler.post(() -> updateSuggestions());

        }).start();
    }

    @Override
    public View onCreateInputView() {
        return createKeyboard();
    }

    private View createKeyboard() {
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);

        letterKeyViews.clear();
        letterKeyValues.clear();

        keyboard.setPadding(
            dp(4), dp(5), dp(4), dp(14)
        );

        keyboard.setBackgroundColor(background);

        keyboard.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(keyboardHeight)
        ));

        addHeader();
        addSuggestions();

        if (symbols) {
            addSymbolRows();
        } else {
            addLetterRows();
        }

        addBottomRow();

        updateLetterKeyLabels();

        return keyboard;
    }

    private void addHeader() {
        LinearLayout row = makeRow();

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(cyan);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);

        row.addView(title,
            new LinearLayout.LayoutParams(0, dp(30), 1));

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setTextColor(cyan);
        settings.setTextSize(19);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> showSettings());

        row.addView(settings,
            new LinearLayout.LayoutParams(dp(42), dp(30)));

        keyboard.addView(row);
    }

    private void addSuggestions() {
        suggestionsRow = makeRow();
        keyboard.addView(suggestionsRow);
        updateSuggestions();
    }

    private void updateSuggestions() {
        if (suggestionsRow == null) return;

        suggestionsRow.removeAllViews();

        String[] suggestions = getSuggestions();

        for (String word : suggestions) {
            TextView item = new TextView(this);
            item.setText(word);
            item.setTextColor(Color.WHITE);
            item.setTextSize(15);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(4), dp(8), dp(4));

            suggestionsRow.addView(item,
                new LinearLayout.LayoutParams(0, dp(34), 1));

            item.setOnClickListener(v -> {
                if (word.isEmpty()) return;

                vibrate();
                replaceCurrentWord(word);
                updateSuggestions();
            });
        }
    }

    private String getCurrentWord() {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return "";

        CharSequence before = ic.getTextBeforeCursor(100, 0);

        if (before == null || before.length() == 0) return "";

        String text = before.toString();
        int start = text.length();

        while (start > 0) {
            char c = text.charAt(start - 1);

            if (Character.isLetter(c) || c == '\'' || c == '’') {
                start--;
            } else {
                break;
            }
        }

        return text.substring(start).toLowerCase(locale());
    }

    private String[] getSuggestions() {
        String prefix = getCurrentWord();

        if (prefix.isEmpty()) {
            if (language.equals("RU")) {
                return new String[]{"Привет", "Спасибо", "Хорошо"};
            }

            if (language.equals("AZ")) {
                return new String[]{"Salam", "Sağ ol", "Bəli"};
            }

            return new String[]{"Hello", "Thanks", "Yes"};
        }

        if (language.equals("RU")) {
            return getRussianSuggestions(prefix);
        }

        if (language.equals("AZ")) {
            if (prefix.startsWith("sa")) {
                return new String[]{"salam", "sabah", "sağ"};
            }

            if (prefix.startsWith("ya")) {
                return new String[]{"yaxşı", "yardım", "yaşamaq"};
            }

            return new String[]{prefix, prefix + "a", prefix + "ı"};
        }

        if (prefix.startsWith("he")) {
            return new String[]{"hello", "help", "here"};
        }

        if (prefix.startsWith("th")) {
            return new String[]{"thanks", "there", "think"};
        }

        return new String[]{prefix, prefix + "s", prefix + "ing"};
    }

    private String[] getRussianSuggestions(String prefix) {
        ArrayList<Map.Entry<String, Integer>> matches =
            new ArrayList<>();

        synchronized (russianWords) {
            if (dictionaryLoaded) {
                for (Map.Entry<String, Integer> entry :
                        russianWords.tailMap(prefix).entrySet()) {

                    if (!entry.getKey().startsWith(prefix)) break;

                    matches.add(
                        new java.util.AbstractMap.SimpleEntry<>(
                            entry.getKey(),
                            entry.getValue()
                        )
                    );
                }
            }
        }

        Collections.sort(matches,
            (a, b) -> Integer.compare(
                b.getValue(), a.getValue()
            )
        );

        ArrayList<String> result = new ArrayList<>();

        for (int i = 0; i < matches.size() && i < 3; i++) {
            result.add(matches.get(i).getKey());
        }

        if (result.isEmpty()) {
            result.add(prefix);
        }

        while (result.size() < 3) {
            result.add("");
        }

        return result.toArray(new String[0]);
    }

    private boolean shouldUseUppercase() {
        if (capsLock) return true;
        if (manualLowerCase) return false;
        return shouldCapitalize();
    }

    private boolean shouldUseUppercaseForCurrentWord() {
        if (capsLock) return true;
        if (manualLowerCase) return false;
        return shouldCapitalizeCurrentWord();
    }

    private void replaceCurrentWord(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        boolean uppercase = shouldUseUppercaseForCurrentWord();

        CharSequence before = ic.getTextBeforeCursor(100, 0);
        int length = 0;

        if (before != null) {
            String text = before.toString();

            for (int i = text.length() - 1; i >= 0; i--) {
                char ch = text.charAt(i);

                if (Character.isLetterOrDigit(ch)
                        || ch == '\'' || ch == '’') {
                    length++;
                } else {
                    break;
                }
            }
        }

        if (length > 0) {
            ic.deleteSurroundingText(length, 0);
        }

        String output = uppercase
            ? word.toUpperCase(locale())
            : word.toLowerCase(locale());

        ic.commitText(output, 1);

        updateLetterKeyLabels();
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

        String numbers = "1234567890";

        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = makeRow();

            if (r == 2) {
                addKey(row, "⇧", 1, "SHIFT");
            }

            for (int i = 0; i < rows[r].length(); i++) {
                String letter = String.valueOf(rows[r].charAt(i));
                String extra = "";

                if (r == 0 && i < numbers.length()) {
                    extra = String.valueOf(numbers.charAt(i));
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
            new LinearLayout.LayoutParams(0, dp(keyHeight), 5);

        p.setMargins(0, dp(1), 0, dp(1));
        row.addView(space, p);

        space.setOnClickListener(v -> {
            vibrate();
            commit(" ");
            updateSuggestions();
            updateLetterKeyLabels();
        });

        space.setOnLongClickListener(v -> {
            chooseLanguage();
            return true;
        });

        space.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                space.setTag(event.getX());
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                Object tag = space.getTag();

                if (tag instanceof Float) {
                    float start = (Float) tag;
                    float dx = event.getX() - start;

                    if (Math.abs(dx) > dp(35)) {
                        changeLanguage(dx < 0 ? 1 : -1);
                        return true;
                    }
                }
            }

            return false;
        });

        addKey(row, ".", 1, "DOT");
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
        LinearLayout row,
        String value,
        int weight,
        String extra
    ) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(keyBackground());

        TextView main = new TextView(this);

        String displayed = value;

        if (value.length() == 1 &&
                Character.isLetter(value.charAt(0))) {
            displayed = shouldUseUppercase()
                ? value.toUpperCase(locale())
                : value.toLowerCase(locale());

            letterKeyViews.add(main);
            letterKeyValues.add(value);
        }

        main.setText(displayed);
        main.setTextColor(Color.WHITE);
        main.setTextSize(letterSize);
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
            new LinearLayout.LayoutParams(0, dp(keyHeight), weight);

        params.setMargins(0, dp(1), 0, dp(1));
        row.addView(key, params);

        if (extra.equals("DELETE")) {
            key.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        deletePressed = true;
                        deleteHeld = false;
                        deleteRepeatInterval = 180;
                        vibrate();

                        handler.removeCallbacks(deleteRunnable);
                        handler.postDelayed(deleteRunnable, 450);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        deletePressed = false;
                        handler.removeCallbacks(deleteRunnable);

                        if (!deleteHeld &&
                                event.getAction() == MotionEvent.ACTION_UP) {
                            deleteOne(false);
                        }

                        deleteHeld = false;
                        return true;
                }

                return true;
            });

        } else if (extra.equals("DOT")) {
            key.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dotGestureActive = true;
                        symbolPopupShown = false;
                        selectedSymbol = -1;

                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();

                        handler.postDelayed(() -> {
                            if (dotGestureActive &&
                                    !symbolPopupShown) {
                                showSymbolPopup(key);
                                updateSymbolSelection(
                                    lastTouchX, lastTouchY
                                );
                            }
                        }, 250);

                        return true;

                    case MotionEvent.ACTION_MOVE:
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();

                        if (symbolPopupShown) {
                            updateSymbolSelection(
                                lastTouchX, lastTouchY
                            );
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacksAndMessages(null);
                        dotGestureActive = false;

                        if (symbolPopupShown) {
                            updateSymbolSelection(
                                event.getRawX(), event.getRawY()
                            );

                            if (selectedSymbol >= 0 &&
                                    selectedSymbol < symbolValues.size()) {
                                String chosen =
                                    symbolValues.get(selectedSymbol);

                                dismissSymbolPopup();
                                press(chosen);
                            } else {
                                dismissSymbolPopup();
                            }

                        } else {
                            press(".");
                        }

                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacksAndMessages(null);
                        dotGestureActive = false;
                        dismissSymbolPopup();
                        return true;
                }

                return true;
            });

        } else {
            key.setOnClickListener(v -> press(value));

            key.setOnLongClickListener(v -> {
                if (extra.equals("SHIFT")) {
                    capsLock = !capsLock;
                    manualLowerCase = false;
                    lastShiftTap = 0;
                    updateLetterKeyLabels();

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
    }

    private void showSymbolPopup(View anchor) {
        String[] values = {
            "-", "(", ")", "!", "?", ",", ":", ";"
        };

        symbolViews.clear();
        symbolValues.clear();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(5), dp(5), dp(5), dp(5));
        panel.setBackground(keyBackground());

        symbolPopup = new PopupWindow(
            panel,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        );

        symbolPopup.setBackgroundDrawable(keyBackground());
        symbolPopup.setOutsideTouchable(false);
        symbolPopup.setTouchable(false);

        if (Build.VERSION.SDK_INT >= 21) {
            symbolPopup.setElevation(dp(8));
        }

        for (String symbol : values) {
            TextView item = new TextView(this);
            item.setText(symbol);
            item.setTextColor(Color.WHITE);
            item.setTextSize(21);
            item.setTypeface(Typeface.DEFAULT_BOLD);
            item.setGravity(Gravity.CENTER);
            item.setBackground(keyBackground());

            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(39), dp(48));

            params.setMargins(dp(2), 0, dp(2), 0);
            panel.addView(item, params);

            symbolViews.add(item);
            symbolValues.add(symbol);
        }

        symbolPopup.showAsDropDown(anchor, 0, -dp(100));
        symbolPopupShown = true;
    }

    private void updateSymbolSelection(float rawX, float rawY) {
        selectedSymbol = -1;

        for (int i = 0; i < symbolViews.size(); i++) {
            TextView item = symbolViews.get(i);

            int[] location = new int[2];
            item.getLocationOnScreen(location);

            int left = location[0];
            int top = location[1];
            int right = left + item.getWidth();
            int bottom = top + item.getHeight();

            boolean inside =
                rawX >= left && rawX <= right
                && rawY >= top && rawY <= bottom;

            if (inside) {
                selectedSymbol = i;
            }

            if (inside) {
                GradientDrawable selected = new GradientDrawable();
                selected.setColor(Color.rgb(0, 105, 135));
                selected.setCornerRadius(dp(9));
                selected.setStroke(dp(2), cyan);
                item.setBackground(selected);
            } else {
                item.setBackground(keyBackground());
            }
        }
    }

    private void dismissSymbolPopup() {
        if (symbolPopup != null) {
            try {
                symbolPopup.dismiss();
            } catch (Exception ignored) {}
        }

        symbolPopup = null;
        symbolPopupShown = false;
        selectedSymbol = -1;
        symbolViews.clear();
        symbolValues.clear();
    }

    private GradientDrawable keyBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(keyColor);
        bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1), Color.rgb(0, 125, 165));
        return bg;
    }

    private boolean isSymbol(char c) {
        return !Character.isLetterOrDigit(c)
            && !Character.isWhitespace(c);
    }

    private boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?';
    }

    private boolean shouldCapitalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);

            if (c == '\n' || c == '\r') {
                return true;
            }

            if (Character.isWhitespace(c)) {
                continue;
            }

            if (c == '"' || c == '\'' || c == '»'
                    || c == ')' || c == ']' || c == '}'
                    || c == '”' || c == '’') {
                continue;
            }

            return isSentenceEnd(c);
        }

        return true;
    }

    private boolean shouldCapitalize() {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return false;

        CharSequence before = ic.getTextBeforeCursor(100, 0);

        if (before == null) return true;

        return shouldCapitalizeText(before.toString());
    }

    private boolean shouldCapitalizeCurrentWord() {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return false;

        CharSequence before = ic.getTextBeforeCursor(100, 0);

        if (before == null) return true;

        String text = before.toString();
        int start = text.length();

        while (start > 0) {
            char c = text.charAt(start - 1);

            if (Character.isLetter(c) || c == '\'' || c == '’') {
                start--;
            } else {
                break;
            }
        }

        return shouldCapitalizeText(text.substring(0, start));
    }

    private void updateLetterKeyLabels() {
        boolean uppercase = shouldUseUppercase();

        for (int i = 0; i < letterKeyViews.size(); i++) {
            TextView view = letterKeyViews.get(i);

            String value = letterKeyValues.get(i);

            view.setText(
                uppercase
                    ? value.toUpperCase(locale())
                    : value.toLowerCase(locale())
            );
        }
    }

    private void handleShiftTap() {
        long now = System.currentTimeMillis();

        if (now - lastShiftTap < 350) {
            capsLock = !capsLock;
            manualLowerCase = false;
            lastShiftTap = 0;

        } else {
            capsLock = false;
            manualLowerCase = !manualLowerCase;
            lastShiftTap = now;
        }

        updateLetterKeyLabels();
    }

    private void press(String value) {
        vibrate();

        switch (value) {
            case "⇧":
                handleShiftTap();
                return;

            case "⌫":
                deleteOne(false);
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
                updateSuggestions();
                updateLetterKeyLabels();
                return;

            default:
                char first = value.charAt(0);

                if (isSymbol(first)) {
                    InputConnection ic = getCurrentInputConnection();

                    if (ic != null) {
                        CharSequence before =
                            ic.getTextBeforeCursor(2, 0);

                        String previous =
                            before == null ? "" : before.toString();

                        boolean previousIsSymbol = false;

                        if (!previous.isEmpty()) {
                            char last = previous.charAt(
                                previous.length() - 1
                            );

                            previousIsSymbol = isSymbol(last);
                        }

                        boolean symbolBeforeSpace =
                            previous.length() >= 2
                            && previous.endsWith(" ")
                            && isSymbol(
                                previous.charAt(previous.length() - 2)
                            );

                        if (symbolBeforeSpace) {
                            ic.deleteSurroundingText(1, 0);
                            ic.commitText(value, 1);

                        } else if (previousIsSymbol) {
                            ic.commitText(value, 1);

                        } else if (",.!?:;".indexOf(first) >= 0) {
                            ic.commitText(value + " ", 1);

                        } else {
                            ic.commitText(value, 1);
                        }
                    }

                    updateSuggestions();
                    updateLetterKeyLabels();
                    return;
                }

                boolean uppercase = shouldUseUppercase();

                String output = uppercase
                    ? value.toUpperCase(locale())
                    : value.toLowerCase(locale());

                if (Character.isLetter(first)) {
                    InputConnection ic = getCurrentInputConnection();

                    if (ic != null) {
                        CharSequence before =
                            ic.getTextBeforeCursor(1, 0);

                        if (before != null && before.length() > 0
                                && isSymbol(before.charAt(0))) {
                            output = " " + output;
                        }
                    }
                }

                commit(output);

                updateSuggestions();
                updateLetterKeyLabels();
        }
    }

    private void showSettings() {
        String[] options = {
            "Высота клавиатуры: " + keyboardHeight,
            "Размер клавиш: " + keyHeight,
            "Размер букв: " + letterSize,
            "Вибрация: " + (vibrationEnabled ? "Включена" : "Выключена"),
            "Длительность вибрации: " + vibrationDuration + " мс"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Настройки JARVIS")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        showSlider("Высота клавиатуры",
                            keyboardHeight, 230, 400, " px", value -> {
                                keyboardHeight = value;
                                saveInt("keyboard_height", value);
                                refresh();
                            });
                        break;

                    case 1:
                        showSlider("Размер клавиш",
                            keyHeight, 38, 65, " px", value -> {
                                keyHeight = value;
                                saveInt("key_height", value);
                                refresh();
                            });
                        break;

                    case 2:
                        showSlider("Размер букв",
                            letterSize, 13, 28, "", value -> {
                                letterSize = value;
                                saveInt("letter_size", value);
                                refresh();
                            });
                        break;

                    case 3:
                        vibrationEnabled = !vibrationEnabled;
                        prefs.edit().putBoolean(
                            "vibration", vibrationEnabled
                        ).apply();
                        refresh();
                        break;

                    case 4:
                        showSlider("Длительность вибрации",
                            vibrationDuration, 10, 80, " мс", value -> {
                                vibrationDuration = value;
                                saveInt("vibration_duration", value);
                            });
                        break;
                }
            })
            .setNegativeButton("Закрыть", null)
            .create();

        showImeDialog(dialog);
    }

    private void showImeDialog(AlertDialog dialog) {
        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            );
        }
    }

    private void saveInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    private interface ValueChanged {
        void onChange(int value);
    }

    private void showSlider(
        String title,
        int current,
        int min,
        int max,
        String unit,
        ValueChanged callback
    ) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(15), dp(22), dp(5));

        TextView valueText = new TextView(this);
        valueText.setText(current + unit);
        valueText.setTextSize(18);
        valueText.setGravity(Gravity.CENTER);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(current - min);

        layout.addView(valueText);
        layout.addView(seekBar);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Сохранить", (d, w) -> {
                callback.onChange(seekBar.getProgress() + min);
            })
            .setNegativeButton("Отмена", null)
            .create();

        seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser
                ) {
                    valueText.setText((progress + min) + unit);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            }
        );

        showImeDialog(dialog);
    }

    private void vibrate() {
        if (!vibrationEnabled) return;

        try {
            Vibrator vibrator =
                (Vibrator) getSystemService(VIBRATOR_SERVICE);

            if (vibrator == null || !vibrator.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        vibrationDuration,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                );
            } else {
                vibrator.vibrate(vibrationDuration);
            }
        } catch (Exception ignored) {}
    }

    private void showAlternatives(String value) {
        String option = "";

        if (language.equals("RU")) {
            if (value.equals("Е")) option = "Ё";
            if (value.equals("Ь")) option = "Ъ";
        }

        if (language.equals("AZ")) {
            if (value.equals("I")) option = "İ";
            if (value.equals("U")) option = "Ü";
            if (value.equals("O")) option = "Ö";
            if (value.equals("G")) option = "Ğ";
            if (value.equals("S")) option = "Ş";
            if (value.equals("C")) option = "Ç";
        }

        if (option.isEmpty()) return;

        String selected = option;

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setItems(new String[]{selected}, (d, which) -> {
                boolean uppercase = shouldUseUppercase();

                commit(
                    uppercase
                        ? selected.toUpperCase(locale())
                        : selected.toLowerCase(locale())
                );

                updateSuggestions();
                updateLetterKeyLabels();
            })
            .create();

        showImeDialog(dialog);
    }

    private void chooseLanguage() {
        String[] names = {
            "Русский",
            "English",
            "Azərbaycan"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Выбор языка")
            .setItems(names, (d, which) -> {
                language = new String[]{"RU", "EN", "AZ"}[which];

                prefs.edit()
                    .putString("language", language)
                    .apply();

                refresh();
            })
            .create();

        showImeDialog(dialog);
    }

    private void changeLanguage(int direction) {
        String[] languages = {"RU", "EN", "AZ"};
        int index = 0;

        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(language)) index = i;
        }

        index = (index + direction + 3) % 3;
        language = languages[index];

        prefs.edit().putString("language", language).apply();
        refresh();
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();

        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void deleteOne(boolean withVibration) {
        if (withVibration) {
            vibrate();
        }

        InputConnection ic = getCurrentInputConnection();

        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }

        updateSuggestions();
        updateLetterKeyLabels();
    }

    private Locale locale() {
        if (language.equals("AZ")) return new Locale("az");
        if (language.equals("EN")) return Locale.ENGLISH;
        return new Locale("ru");
    }

    private void refresh() {
        dismissSymbolPopup();
        setInputView(createKeyboard());
    }

    @Override
    public void onDestroy() {
        deletePressed = false;
        dotGestureActive = false;
        dismissSymbolPopup();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density
        );
    }
}
