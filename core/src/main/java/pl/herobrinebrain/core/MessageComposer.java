package pl.herobrinebrain.core;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Hybryda: Director decyduje CO i KIEDY (MessageIntent), a treść pochodzi z LLM (opcjonalnie) lub z szablonów.
 * Nigdy nie blokuje wątku gry: wynik to CompletableFuture z twardym timeoutem i zawsze istniejącym fallbackiem.
 */
public final class MessageComposer {
    private final Function<String, String> llm;    // prompt -> surowy tekst (blokujące; wywoływane na executorze); null = tryb offline
    private final Executor executor;
    private final Duration timeout;
    private final Random rng;

    public MessageComposer(Function<String, String> llm, Executor executor, Duration timeout, long seed) {
        this.llm = llm; this.executor = executor; this.timeout = timeout; this.rng = new Random(seed);
    }

    public CompletableFuture<String> compose(MessageIntent in) {
        String fallback = TemplateBank.pick(in, rng);
        if (llm == null) return CompletableFuture.completedFuture(fallback);
        return CompletableFuture
                .supplyAsync(() -> llm.apply(buildPrompt(in)), executor)
                .thenApply(raw -> MessageGate.sanitize(raw, in).orElse(fallback))
                .completeOnTimeout(fallback, timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> fallback);
    }

    private static final Map<FactKind, String> FACT_DESC = Map.of(
            FactKind.HOTSPOT, "ta osoba często wraca w to miejsce",
            FactKind.FEARED_PLACE, "ta osoba boi się tego miejsca",
            FactKind.SLEEP_ROUTINE, "ta osoba zasypia o stałej porze",
            FactKind.DEATH_SPOT, "ta osoba kiedyś tu zginęła",
            FactKind.FLED_FROM_SIGHT, "ta osoba uciekła, gdy cię zobaczyła",
            FactKind.DESTROYED_ARTIFACT, "ta osoba zniszczyła coś, co zostawiłeś",
            FactKind.SEEKS_HEROBRINE, "ta osoba cię szuka",
            FactKind.IGNORES_MESSAGES, "ta osoba ignoruje twoje wiadomości");

    /** Prompt zawiera wyłącznie ABSTRAKCYJNY opis faktu - bez współrzędnych, nicku i nazw przedmiotów. */
    static String buildPrompt(MessageIntent in) {
        String fact = in.reference() == null ? "brak konkretnego faktu" : FACT_DESC.getOrDefault(in.reference(), "brak");
        String specificity = in.level() <= 3 ? "ogólnie, bez konkretów"
                : in.level() == 4 ? "odnieś się do faktu wprost, ale oszczędnie"
                : "daj do zrozumienia, że wiesz o tej osobie więcej, niż powinieneś";
        return """
                Piszesz jedną krótką wiadomość od bytu, który obserwuje graczkę/gracza w grze grozy.
                Zasady: JEDNA linia, maksymalnie %d znaków, po polsku, bez cudzysłowów, bez emotikon,
                bez liczb i współrzędnych, bez odniesień do gry, modów, AI ani prawdziwego świata.
                Ton: spokojny, oszczędny, nieludzki. Nie groź wprost. Nie wykrzykuj.
                Fakt: %s. Sposób odniesienia: %s.
                """.formatted(in.maxChars(), fact, specificity);
    }
}
