package org.spectralmemories.bloodmoon;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Compatibilidad con LevelledMobs sin depender de su API: todo va por el PersistentDataContainer.
 *
 * - Cada mob de BloodMoon lleva la clave bloodmoon:mob (bloodmoon, crimson, horde, boss o minion), que
 *   LevelledMobs puede leer desde externalplugins.yml para aplicarles reglas propias.
 * - Los jefes y sus esbirros pueden llevar levelledmobs:nolevel, la clave con la que LevelledMobs
 *   salta un mob. Se pone antes de que el mob aparezca, porque LevelledMobs lo procesa al aparecer.
 */
public final class LevelledMobsHook
{
    private LevelledMobsHook() {}

    public static final String TYPE_BLOODMOON = "bloodmoon";
    public static final String TYPE_CRIMSON = "crimson";
    public static final String TYPE_HORDE = "horde";
    public static final String TYPE_BOSS = "boss";
    public static final String TYPE_MINION = "minion";

    private static NamespacedKey mobKey;

    public static NamespacedKey MobKey ()
    {
        if (mobKey == null) mobKey = new NamespacedKey(Bloodmoon.GetInstance(), "mob");
        return mobKey;
    }

    private static Plugin LevelledMobs ()
    {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LevelledMobs");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    public static boolean IsInstalled ()
    {
        return LevelledMobs() != null;
    }

    /**
     * Marca el mob como de BloodMoon. No pisa una marca anterior (un mob de horda sigue siendo horde)
     */
    public static void Mark (Entity entity, String type)
    {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(MobKey(), PersistentDataType.STRING)) pdc.set(MobKey(), PersistentDataType.STRING, type);
    }

    public static String GetType (Entity entity)
    {
        return entity.getPersistentDataContainer().get(MobKey(), PersistentDataType.STRING);
    }

    /**
     * Marca un jefe o esbirro y, si la config lo pide, hace que LevelledMobs no le ponga nivel
     */
    public static void MarkBoss (Entity entity, String type, ConfigReader reader)
    {
        Mark(entity, type);
        Plugin levelledMobs = LevelledMobs();
        if (levelledMobs == null || reader.GetBool(ConfigReader.LEVELLED_MOBS_LEVEL_BOSSES, false)) return;
        entity.getPersistentDataContainer().set(new NamespacedKey(levelledMobs, "noLevel"), PersistentDataType.STRING, "BloodMoon");
    }

    /**
     * @return true si LevelledMobs se encarga de la vida y la velocidad y BloodMoon no debe tocarlas
     */
    public static boolean HandlesStats (ConfigReader reader)
    {
        return reader.GetBool(ConfigReader.LEVELLED_MOBS_STATS, true) && IsInstalled();
    }
}
