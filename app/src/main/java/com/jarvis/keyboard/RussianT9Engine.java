
package com.jarvis.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RussianT9Engine {

    private final TreeMap<String, Integer> frequencies = new TreeMap<>();
    private final Map<String, Set<String>> deleteIndex = new HashMap<>();
    private final Set<String> learnedWords = new HashSet<>();
    private final Map<String, Set<String>> nextWords = new HashMap<>();

    private static final int MAX_WORD_LENGTH = 32;

    public synchronized void load(Map<String, Integer> dictionary) {
        frequencies.clear();
        deleteIndex.clear();

        if (dictionary == null) return;

        for (Map.Entry<String, Integer> entry : dictionary.entrySet()) {
            String word = normalize(entry.getKey());

            if (!valid(word)) continue;

            int frequency = entry.getValue() == null
                    ? 1 : Math.max(1, entry.getValue());

            frequencies.put(word, frequency);
        }

        for (String word : frequencies.keySet()) {
            addToDeleteIndex(word);
        }

        for (String word : learnedWords) {
            addToDeleteIndex(word);
        }
    }

    public synchronized List<String> suggest(
            String input,
            String previousWord,
            int limit
    ) {
        String query = normalize(input);

        if (!valid(query) || limit <= 0) {
            return Collections.emptyList();
        }

        Map<String, Integer> scores = new HashMap<>();

        // Быстрый поиск по префиксу через TreeMap.
        for (Map.Entry<String, Integer> entry :
                frequencies.tailMap(query, true).entrySet()) {

            String word = entry.getKey();

            if (!word.startsWith(query)) break;

            scores.put(word, score(word, previousWord, true));
        }

        for (String word : learnedWords) {
            if (word.startsWith(query)) {
                scores.put(word, score(word, previousWord, true));
            }
        }

        // Поиск слов с одной опечаткой через индекс удалённых букв.
        if (query.length() >= 3) {
            for (String word : findCandidates(query)) {
                if (oneEditAway(query, word)) {
                    scores.put(
                            word,
                            score(word, previousWord, false)
                    );
                }
            }
        }

        List<String> result = new ArrayList<>(scores.keySet());

        result.sort((a, b) -> {
            int comparison = Integer.compare(
                    scores.get(b), scores.get(a)
            );

            return comparison != 0
                    ? comparison : a.compareTo(b);
        });

        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }

        return result;
    }

    // Отдельный поиск именно исправлений, без обычных слов-продолжений.
    public synchronized List<String> suggestCorrections(
            String input,
            String previousWord,
            int limit
    ) {
        String query = normalize(input);

        if (!valid(query) || query.length() < 3 || limit <= 0) {
            return Collections.emptyList();
        }

        Set<String> candidates = findCandidates(query);
        List<String> result = new ArrayList<>();

        for (String word : candidates) {
            if (oneEditAway(query, word)) {
                result.add(word);
            }
        }

        result.sort((a, b) -> {
            int comparison = Integer.compare(
                    score(b, previousWord, false),
                    score(a, previousWord, false)
            );

            return comparison != 0
                    ? comparison : a.compareTo(b);
        });

        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }

        return result;
    }

    private int score(
            String word,
            String previousWord,
            boolean prefixMatch
    ) {
        int frequency = frequencies.containsKey(word)
                ? frequencies.get(word) : 1;

        int result = Math.min(100000, frequency);

        if (prefixMatch) result += 1000000;
        if (learnedWords.contains(word)) result += 500000;

        String previous = normalize(previousWord);

        if (!previous.isEmpty()) {
            Set<String> contextual = nextWords.get(previous);

            if (contextual != null && contextual.contains(word)) {
                result += 2000000;
            }
        }

        return result;
    }

    private Set<String> findCandidates(String input) {
        Set<String> result = new HashSet<>();

        for (String variant : deleteVariants(input)) {
            Set<String> words = deleteIndex.get(variant);

            if (words != null) {
                result.addAll(words);
            }
        }

        return result;
    }

    private void addToDeleteIndex(String word) {
        for (String variant : deleteVariants(word)) {
            deleteIndex.computeIfAbsent(
                    variant, key -> new HashSet<>()
            ).add(word);
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

    private boolean oneEditAway(String first, String second) {
        if (first.equals(second)) return false;

        if (Math.abs(first.length() - second.length()) > 1) {
            return false;
        }

        int i = 0;
        int j = 0;
        int errors = 0;

        while (i < first.length() && j < second.length()) {
            if (first.charAt(i) == second.charAt(j)) {
                i++;
                j++;
                continue;
            }

            errors++;

            if (errors > 1) return false;

            if (i + 1 < first.length()
                    && j + 1 < second.length()
                    && first.charAt(i) == second.charAt(j + 1)
                    && first.charAt(i + 1) == second.charAt(j)) {

                i += 2;
                j += 2;

            } else if (first.length() > second.length()) {
                i++;

            } else if (second.length() > first.length()) {
                j++;

            } else {
                i++;
                j++;
            }
        }

        if (i < first.length() || j < second.length()) {
            errors++;
        }

        return errors == 1;
    }

    public synchronized void learnWord(String word) {
        String normalized = normalize(word);

        if (!valid(normalized)) return;

        if (learnedWords.add(normalized)) {
            addToDeleteIndex(normalized);
        }
    }

    public synchronized void learnNextWord(
            String previousWord,
            String nextWord
    ) {
        String previous = normalize(previousWord);
        String next = normalize(nextWord);

        if (!valid(previous) || !valid(next)) return;

        nextWords.computeIfAbsent(
                previous, key -> new HashSet<>()
        ).add(next);

        learnWord(next);
    }

    public synchronized Set<String> getLearnedWords() {
        return new HashSet<>(learnedWords);
    }

    public synchronized void restoreLearnedWords(Set<String> words) {
        learnedWords.clear();

        if (words == null) return;

        for (String word : words) {
            String normalized = normalize(word);

            if (valid(normalized)) {
                learnedWords.add(normalized);
                addToDeleteIndex(normalized);
            }
        }
    }

    public synchronized Map<String, Set<String>> getNextWords() {
        Map<String, Set<String>> copy = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry :
                nextWords.entrySet()) {
            copy.put(
                    entry.getKey(),
                    new HashSet<>(entry.getValue())
            );
        }

        return copy;
    }

    public synchronized void restoreNextWords(
            Map<String, Set<String>> savedWords
    ) {
        nextWords.clear();

        if (savedWords == null) return;

        for (Map.Entry<String, Set<String>> entry :
                savedWords.entrySet()) {

            String previous = normalize(entry.getKey());

            if (!valid(previous) || entry.getValue() == null) {
                continue;
            }

            Set<String> words = new HashSet<>();

            for (String word : entry.getValue()) {
                String normalized = normalize(word);

                if (valid(normalized)) {
                    words.add(normalized);
                }
            }

            if (!words.isEmpty()) {
                nextWords.put(previous, words);
            }
        }
    }

    public synchronized int getDictionarySize() {
        return frequencies.size();
    }

    private String normalize(String word) {
        if (word == null) return "";

        return word.trim().toLowerCase(Locale.ROOT);
    }

    private boolean valid(String word) {
        if (word == null || word.length() < 2
                || word.length() > MAX_WORD_LENGTH) {
            return false;
        }

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);

            boolean russian =
                    (c >= 'а' && c <= 'я') || c == 'ё';

            if (!russian) return false;
        }

        return true;
    }
}
