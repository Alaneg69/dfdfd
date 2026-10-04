package pl.herobrinebrain.core;

/** Co Director CHCE powiedzieć (nie JAK). To jedyne, co może trafić do warstwy LLM. */
public record MessageIntent(Channel channel, FactKind reference, String place, int level, int maxChars) {
    public enum Channel { CHAT, SIGN, BOOK, ITEM_NAME, DREAM_VOICE }
}
