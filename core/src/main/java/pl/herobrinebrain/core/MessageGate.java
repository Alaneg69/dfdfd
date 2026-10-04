package pl.herobrinebrain.core;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bezpieczna warstwa pośrednia między LLM a grą. Tekst z LLM jest NIEZAUFANYM wejściem:
 * przechodzi walidację albo jest odrzucany (wtedy używamy szablonu). LLM nigdy nie dostaje
 * współrzędnych, nicku ani nie może wywołać komendy.
 */
public final class MessageGate {
    private MessageGate() { }

    private static final Pattern LINKS_AND_FORMATTING =
            Pattern.compile("(https?:|www\\.|@|§|\\\\|<|>|`|\\{|\\})");
    private static final Pattern LEADING_SLASH = Pattern.compile("(?m)^\\s*/");
    private static final Pattern META = Pattern.compile(
            "(?iu)(jako model|model językowy|sztuczn\\w+ inteligencj|as an ai|language model|assistant|minecraft|\\bmod\\b|herobrine)");
    private static final Pattern DIGITS = Pattern.compile("\\d{3,}");   // blokuje współrzędne / liczby

    public static Optional<String> sanitize(String raw, MessageIntent in) {
        if (raw == null) return Optional.empty();
        String t = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        t = t.replaceAll("^[\"'„”«»]+|[\"'„”«»]+$", "").strip();
        if (t.isEmpty() || t.length() > in.maxChars()) return Optional.empty();
        if (LINKS_AND_FORMATTING.matcher(t).find()) return Optional.empty();
        if (LEADING_SLASH.matcher(t).find()) return Optional.empty();
        if (META.matcher(t).find()) return Optional.empty();
        if (DIGITS.matcher(t).find()) return Optional.empty();
        return Optional.of(t);
    }
}
