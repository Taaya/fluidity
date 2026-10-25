package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative local duplicate check. Semantic suitability is reviewed by the generator. */
public final class PromptSimilarity {
    private PromptSimilarity() { }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public static boolean repeats(TrainingQuestion candidate, TrainingQuestion previous) {
        String first = normalize(candidate.text());
        String second = normalize(previous.text());
        if (first.equals(second)) return true;
        if (!candidate.context().isBlank() && !previous.context().isBlank()
                && normalize(candidate.context()).equals(normalize(previous.context()))) return true;

        Set<String> a = new HashSet<>(Arrays.asList(first.split(" +")));
        Set<String> b = new HashSet<>(Arrays.asList(second.split(" +")));
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return !union.isEmpty() && (double) common.size() / union.size() >= 0.80;
    }

    public static boolean containsTarget(String text, String word) {
        String target = normalize(word);
        if (target.isBlank()) return false;
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(target)
                + "(?![\\p{L}\\p{N}])").matcher(normalize(text)).find();
    }
}
