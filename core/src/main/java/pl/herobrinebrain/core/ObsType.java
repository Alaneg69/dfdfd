package pl.herobrinebrain.core;

/** Typy obserwacji, które adapter Minecrafta zgłasza do mózgu. */
public enum ObsType {
    DWELL,                    // gracz przebywał w miejscu (magnitude = sekundy)
    UNDERGROUND_TIME,         // czas pod ziemią (magnitude = sekundy)
    SURFACE_TIME_NIGHT,       // czas na powierzchni nocą (magnitude = sekundy)
    SLEEP,                    // gracz poszedł spać (magnitude = godzina 0..24)
    DEATH,                    // śmierć (place = miejsce)
    COMBAT,                   // walka
    SAW_ANOMALY,              // gracz PATRZYŁ na anomalię (magnitude = intensywność 0..1)
    IGNORED_ANOMALY,          // anomalia wygasła niezauważona
    FLED_FROM_SIGHT,          // gracz uciekł po zobaczeniu sylwetki
    READ_MESSAGE,             // gracz przeczytał wiadomość/tabliczkę
    IGNORED_MESSAGE,          // wiadomość wygasła nieprzeczytana
    DESTROYED_ARTIFACT,       // gracz zniszczył coś, co zostawił Herobrine
    SEARCHED_FOR_HEROBRINE    // gracz pisze o nim na czacie / kopie "w poszukiwaniu"
}
