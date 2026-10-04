package pl.herobrinebrain.core;

import java.util.*;

/**
 * Płaski POJO ze stanem mózgu gracza - bezpieczny do serializacji Gsonem (bez Random, EnumMap, rekordów).
 * Pola publiczne celowo: Gson i tak ich używa, a to jest tylko kontener danych.
 */
public final class BrainState {
    public int version = 1;
    public double[] traits;
    public double[] sleepHist;
    public Map<String, Double> dwell = new HashMap<>();
    public List<FactState> facts = new ArrayList<>();
    public double tension;
    public double presence = 0.5;
    public long playTicks;
    public Map<String, double[]> beta = new HashMap<>();

    public static final class FactState {
        public String kind;
        public String place;
        public double strength;
        public long lastTick;
        public int count;
    }
}
