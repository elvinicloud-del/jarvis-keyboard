
package com.jarvis.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RussianT9Engine {

    private final Map<String, Integer> frequencies =
            new HashMap<>();

    private final Map<String, Set<String>> deleteIndex =
            new HashMap<>();

    private final Map<String, Integer> learnedWords =
            new HashMap<>();

    private final Map<String, Map<String, Integer>> nextWords =
            new HashMap<>();

    private boolean ready = false;

    public synchronized void load(
            Map<String, Integer> dictionary) {

        frequencies.clear();
        deleteIndex.clear();

        for (Map.Entry<String, Integer> entry :
                dictionary.entrySet()) {

            String word = normalize(entry.getKey());

            if (!valid(word)) continue;

            int frequency = Math.max(1, entry.getValue());
            frequencies.put(word, frequency);

            for (String key : deleteVariants(word)) {
                Set<String> values = deleteIndex.get(key);

                if (values == null) {
                    values = new HashSet<>();
                    deleteIndex.put(key, values);
                }

                values.add(word);
            }
        }

        ready = true;
    }

    public synchronized boolean isReady() {
        return ready;
    }

    public synchronized List<String> suggest(
            String input,
            String previousWord,
            int limit) {

        ArrayList<String> result = new ArrayList<>();

        String query = normalize(input);

        if (query.isEmpty() || !ready) {
            return result;
        }

        Map<String, Double> scores = new HashMap<>();

        // Точные совпадения по началу слова.
        for (Map.Entry<String, Integer> entry :
                frequencies.entrySet()) {

            String word = entry.getKey();

            if (word.startsWith(query)) {
                scores.put(
                        word,
                        score(word, query, previousWord, 0)
                );
            }
        }

        // Если точных совпадений мало,
        // ищем слова с одной ошибкой.
        if (scores.size() < limit) {

            Set<String> candidates = new HashSet<>();

            for (String key : deleteVariants(query)) {
                Set<String> found = deleteIndex.get(key);

                if (found != null) {
                    candidates.addAll(found);
                }
            }

            for (String word : candidates) {

                if (scores.containsKey(word)) continue;

                int distance =
                        damerauDistanceAtMostOne(query, word);

                if (distance < 0) continue;

                scores.put(
                        word,
                        score(word, query, previousWord, distance)
                );
            }
        }

        // Учитываем слова, которым пользователь обучил клавиатуру.
        for (Map.Entry<String, Integer> entry :
                learnedWords.entrySet()) {

            String word = entry.getKey();

            if (word.startsWith(query)) {
                scores.put(
                        word,
                        score(word, query, previousWord, 0)
                );
            }
        }

        ArrayList<Map.Entry<String, Double>> sorted =
                new ArrayList<>(scores.entrySet());

        Collections.sort(sorted,
                (a, b) -> Double.compare(
                        b.getValue(),
                        a.getValue()
                )
        );

        for (Map.Entry<String, Double> entry : sorted) {
            if (result.size() >= limit) break;
            result.add(entry.getKey());
        }

        return result;
    }

    private double score(
            String word,
            String query,
            String previousWord,
            int distance) {

        int frequency = frequencies.containsKey(word)
                ? frequencies.get(word) : 1;

        int learned = learnedWords.containsKey(word)
                ? learnedWords.get(word) : 0;

        double value = Math.log(frequency + 1) * 10;

        value += Math.log(learned + 1) * 18;

        value -= distance * 35;

        if (word.equals(query)) {
            value += 100;
        }

        if (previousWord != null &&
                !previousWord.isEmpty()) {

            Map<String, Integer> following =
                    nextWords.get(normalize(previousWord));

            if (following != null) {
                Integer count = following.get(word);

                if (count != null) {
                    value += Math.log(count + 1) * 20;
                }
            }
        }

        return value;
    }

    public synchronized void learnWord(String word) {

        String normalized = normalize(word);

        if (!valid(normalized)) return;

        int count = learnedWords.containsKey(normalized)
                ? learnedWords.get(normalized) : 0;

        learnedWords.put(normalized, count + 1);
    }

    public synchronized void learnNextWord(
            String previous,
            String next) {

        String first = normalize(previous);
        String second = normalize(next);

        if (!valid(first) || !valid(second)) return;

        Map<String, Integer> following =
                nextWords.get(first);

        if (following == null) {
            following = new HashMap<>();
            nextWords.put(first, following);
        }

        int count = following.containsKey(second)
                ? following.get(second) : 0;

        following.put(second, count + 1);
    }

    public synchronized Map<String, Integer>
    getLearnedWords() {

        return new HashMap<>(learnedWords);
    }

    public synchronized void restoreLearnedWords(
            Map<String, Integer> saved) {

        learnedWords.clear();

        if (saved == null) return;

        for (Map.Entry<String, Integer> entry :
                saved.entrySet()) {

            String word = normalize(entry.getKey());

            if (valid(word)) {
                learnedWords.put(
                        word,
                        Math.max(0, entry.getValue())
                );
            }
        }
    }

    public synchronized Map<String, Map<String, Integer>>
    getNextWords() {

        Map<String, Map<String, Integer>> copy =
                new HashMap<>();

        for (Map.Entry<String, Map<String, Integer>> entry :
                nextWords.entrySet()) {

            copy.put(
                    entry.getKey(),
                    new HashMap<>(entry.getValue())
            );
        }

        return copy;
    }

    public synchronized void restoreNextWords(
            Map<String, Map<String, Integer>> saved) {

        nextWords.clear();

        if (saved == null) return;

        for (Map.Entry<String, Map<String, Integer>> entry :
                saved.entrySet()) {

            String previous = normalize(entry.getKey());

            if (!valid(previous)) continue;

            Map<String, Integer> values = new HashMap<>();

            for (Map.Entry<String, Integer> next :
                    entry.getValue().entrySet()) {

                String word = normalize(next.getKey());

                if (valid(word)) {
                    values.put(
                            word,
                            Math.max(0, next.getValue())
                    );
                }
            }

            nextWords.put(previous, values);
        }
    }

    private Set<String> deleteVariants(String word) {

        Set<String> variants = new HashSet<>();

        variants.add(word);

        for (int i = 0; i < word.length(); i++) {
            variants.add(
                    word.substring(0, i) +
                    word.substring(i + 1)
            );
        }

        return variants;
    }

    private int damerauDistanceAtMostOne(
            String first,
            String second) {

        if (first.equals(second)) return 0;

        int a = first.length();
        int b = second.length();

        if (Math.abs(a - b) > 1) return -1;

        if (a == b) {

            int differences = 0;
            int firstDifference = -1;
            int secondDifference = -1;

            for (int i = 0; i < a; i++) {
                if (first.charAt(i) != second.charAt(i)) {
                    differences++;

                    if (firstDifference < 0) {
                        firstDifference = i;
                    } else {
                        secondDifference = i;
                    }

                    if (differences > 2) return -1;
                }
            }

            if (differences == 1) return 1;

            if (differences == 2 &&
                    secondDifference == firstDifference + 1) {

                if (first.charAt(firstDifference) ==
                        second.charAt(secondDifference) &&
                    first.charAt(secondDifference) ==
                        second.charAt(firstDifference)) {
                    return 1;
                }
            }

            return -1;
        }

        String shorter = a < b ? first : second;
        String longer = a < b ? second : first;

        int i = 0;
        int j = 0;
        int differences = 0;

        while (i < shorter.length() &&
                j < longer.length()) {

            if (shorter.charAt(i) == longer.charAt(j)) {
                i++;
                j++;
            } else {
                differences++;
                j++;

                if (differences > 1) return -1;
            }
        }

        return 1;
    }

    private boolean valid(String word) {
        return word != null &&
                word.length() >= 2 &&
                word.matches("[а-яё]+");
    }

    private String normalize(String word) {
        if (word == null) return "";

        return word.trim().toLowerCase(
                new Locale("ru")
        );
    }
}
