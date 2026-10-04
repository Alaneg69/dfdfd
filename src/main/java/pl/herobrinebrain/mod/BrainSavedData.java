package pl.herobrinebrain.mod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Magazyn stanu mózgów w świecie (Overworld): UUID gracza -> JSON ze stanem. */
public final class BrainSavedData extends SavedData {
    private static final String NAME = "herobrinebrain_brains";
    private final Map<String, String> json = new HashMap<>();

    public static BrainSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BrainSavedData::new, BrainSavedData::load, null), NAME);
    }

    public String get(String uuid) { return json.get(uuid); }

    public void put(String uuid, String value) {
        json.put(uuid, value);
        setDirty();
    }

    public void remove(String uuid) {
        if (json.remove(uuid) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag brains = new CompoundTag();
        json.forEach(brains::putString);
        tag.put("brains", brains);
        return tag;
    }

    static BrainSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BrainSavedData d = new BrainSavedData();
        CompoundTag brains = tag.getCompound("brains");
        for (String k : brains.getAllKeys()) d.json.put(k, brains.getString(k));
        return d;
    }
}
