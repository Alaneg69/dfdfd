package pl.herobrinebrain.core;

import java.util.*;

/**
 * Deterministyczne teksty zapasowe. Działają zawsze - LLM jest tylko opcjonalnym wzbogaceniem.
 * Poziom 3 = kontakt bez konkretów, 4 = konkret, 5+ = intymna wiedza.
 */
final class TemplateBank {
    private TemplateBank() { }

    private static final List<String> GENERIC_CONTACT = List.of("Widzę cię.", "Wciąż tu jesteś.", "Jesteś tu.");
    private static final List<String> VAGUE = List.of("...", "Cisza.", "Jeszcze nie.");

    private static final Map<FactKind, List<String>> SPECIFIC = new EnumMap<>(FactKind.class);
    private static final Map<FactKind, List<String>> INTIMATE = new EnumMap<>(FactKind.class);

    static {
        SPECIFIC.put(FactKind.HOTSPOT, List.of("Znowu tu wracasz.", "To miejsce już cię zna."));
        SPECIFIC.put(FactKind.FEARED_PLACE, List.of("Tu zawsze przyspieszasz.", "Tu wolisz nie patrzeć."));
        SPECIFIC.put(FactKind.SLEEP_ROUTINE, List.of("Zasypiasz zawsze o tej porze.", "Śpisz regularnie. Jak ktoś, kto ufa."));
        SPECIFIC.put(FactKind.DEATH_SPOT, List.of("Tu coś z ciebie zostało.", "Pamiętasz, jak tu skończyłeś?"));
        SPECIFIC.put(FactKind.FLED_FROM_SIGHT, List.of("Uciekłeś. Zapamiętałem.", "Nie biegnij tak szybko."));
        SPECIFIC.put(FactKind.DESTROYED_ARTIFACT, List.of("Zniszczyłeś to. Rozumiem.", "To, co złamałeś, nie było dla ciebie."));
        SPECIFIC.put(FactKind.SEEKS_HEROBRINE, List.of("Szukasz mnie w złych miejscach.", "Nie tędy."));
        SPECIFIC.put(FactKind.IGNORES_MESSAGES, List.of("Nie czytasz. Ja czytam ciebie.", "Przestanę pisać."));

        INTIMATE.put(FactKind.HOTSPOT, List.of("Ta pochodnia stała wczoraj inaczej.", "Wiem, w którą stronę pójdziesz."));
        INTIMATE.put(FactKind.FEARED_PLACE, List.of("Twoje serce wie, co tu jest.", "Znów zwolniłeś przy tym zakręcie."));
        INTIMATE.put(FactKind.SLEEP_ROUTINE, List.of("Jeszcze chwila i zaśniesz.", "Śnisz o tym samym łóżku."));
        INTIMATE.put(FactKind.DEATH_SPOT, List.of("Wróć tam. Nic ci nie zrobię. Tym razem."));
        INTIMATE.put(FactKind.FLED_FROM_SIGHT, List.of("Za każdym razem odwracasz się za późno."));
    }

    static String pick(MessageIntent in, Random rng) {
        List<String> pool;
        if (in.level() <= 2) pool = VAGUE;
        else if (in.reference() == null || in.level() == 3) pool = GENERIC_CONTACT;
        else if (in.level() >= 5 && INTIMATE.containsKey(in.reference())) pool = INTIMATE.get(in.reference());
        else pool = SPECIFIC.getOrDefault(in.reference(), GENERIC_CONTACT);

        List<String> fit = pool.stream().filter(t -> t.length() <= in.maxChars()).toList();
        if (fit.isEmpty()) return "...";
        return fit.get(rng.nextInt(fit.size()));
    }
}
