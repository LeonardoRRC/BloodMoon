package org.spectralmemories.bloodmoon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Busca particulas, efectos, encantamientos y materiales por nombre en lugar de usar las constantes
 * del enum, porque Minecraft 1.20.5 renombro varias (EXPLOSION_HUGE, INCREASE_DAMAGE, DAMAGE_ALL...).
 * Asi el mismo jar funciona en 1.20.4 y en 1.21.x, y los config viejos con nombres antiguos siguen valiendo.
 */
public final class Compat
{
    private Compat() {}

    private static final Map<String, String> POTION_ALIASES = new HashMap<>();
    private static final Map<String, String> ENCHANT_ALIASES = new HashMap<>();

    static
    {
        // Nombres antiguos de Bukkit -> clave de Minecraft
        POTION_ALIASES.put("SLOW", "slowness");
        POTION_ALIASES.put("FAST_DIGGING", "haste");
        POTION_ALIASES.put("SLOW_DIGGING", "mining_fatigue");
        POTION_ALIASES.put("INCREASE_DAMAGE", "strength");
        POTION_ALIASES.put("HEAL", "instant_health");
        POTION_ALIASES.put("HARM", "instant_damage");
        POTION_ALIASES.put("JUMP", "jump_boost");
        POTION_ALIASES.put("CONFUSION", "nausea");
        POTION_ALIASES.put("DAMAGE_RESISTANCE", "resistance");

        ENCHANT_ALIASES.put("PROTECTION_ENVIRONMENTAL", "protection");
        ENCHANT_ALIASES.put("PROTECTION_FIRE", "fire_protection");
        ENCHANT_ALIASES.put("PROTECTION_FALL", "feather_falling");
        ENCHANT_ALIASES.put("PROTECTION_EXPLOSIONS", "blast_protection");
        ENCHANT_ALIASES.put("PROTECTION_PROJECTILE", "projectile_protection");
        ENCHANT_ALIASES.put("OXYGEN", "respiration");
        ENCHANT_ALIASES.put("WATER_WORKER", "aqua_affinity");
        ENCHANT_ALIASES.put("DAMAGE_ALL", "sharpness");
        ENCHANT_ALIASES.put("DAMAGE_UNDEAD", "smite");
        ENCHANT_ALIASES.put("DAMAGE_ARTHROPODS", "bane_of_arthropods");
        ENCHANT_ALIASES.put("LOOT_BONUS_MOBS", "looting");
        ENCHANT_ALIASES.put("DIG_SPEED", "efficiency");
        ENCHANT_ALIASES.put("DURABILITY", "unbreaking");
        ENCHANT_ALIASES.put("LOOT_BONUS_BLOCKS", "fortune");
        ENCHANT_ALIASES.put("ARROW_DAMAGE", "power");
        ENCHANT_ALIASES.put("ARROW_KNOCKBACK", "punch");
        ENCHANT_ALIASES.put("ARROW_FIRE", "flame");
        ENCHANT_ALIASES.put("ARROW_INFINITE", "infinity");
        ENCHANT_ALIASES.put("LUCK", "luck_of_the_sea");
        ENCHANT_ALIASES.put("SWEEPING_EDGE", "sweeping_edge");
    }

    /**
     * Devuelve la primera particula que exista entre los nombres dados (nuevo primero, antiguo despues)
     */
    public static Particle particle (String... names)
    {
        for (String name : names)
        {
            try
            {
                return Particle.valueOf(name);
            }
            catch (IllegalArgumentException ignored) {}
        }
        return Particle.FLAME;
    }

    // Particulas usadas por el plugin, resueltas una sola vez
    public static final Particle EXPLOSION_HUGE = particle("EXPLOSION_EMITTER", "EXPLOSION_HUGE");
    public static final Particle EXPLOSION_NORMAL = particle("POOF", "EXPLOSION_NORMAL");
    public static final Particle CRIT_MAGIC = particle("ENCHANTED_HIT", "CRIT_MAGIC");
    public static final Particle FIREWORK = particle("FIREWORK", "FIREWORKS_SPARK");
    public static final Particle BUBBLE = particle("BUBBLE", "WATER_BUBBLE");
    public static final Particle DUST_RED = particle("DUST", "REDSTONE");

    /**
     * Busca un efecto de pocion por nombre, aceptando el nombre de Bukkit antiguo (SLOW) o la clave nueva (slowness)
     */
    @SuppressWarnings("deprecation")
    public static PotionEffectType potion (String name)
    {
        if (name == null) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        String key = POTION_ALIASES.getOrDefault(upper, upper.toLowerCase(Locale.ROOT));

        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(key));
        if (type == null) type = PotionEffectType.getByName(upper);
        return type;
    }

    /**
     * Igual que potion() pero avisa en consola si el nombre no existe
     */
    public static PotionEffectType potionOrWarn (String name, String context)
    {
        PotionEffectType type = potion(name);
        if (type == null) warn("Efecto de pocion desconocido '" + name + "' en " + context);
        return type;
    }

    public static final PotionEffectType STRENGTH = potion("STRENGTH");
    public static final PotionEffectType ABSORPTION = potion("ABSORPTION");
    public static final PotionEffectType BLINDNESS = potion("BLINDNESS");
    public static final PotionEffectType SPEED = potion("SPEED");
    public static final PotionEffectType POISON = potion("POISON");
    public static final PotionEffectType WITHER = potion("WITHER");
    public static final PotionEffectType SLOWNESS = potion("SLOWNESS");
    public static final PotionEffectType RESISTANCE = potion("RESISTANCE");
    public static final PotionEffectType FIRE_RESISTANCE = potion("FIRE_RESISTANCE");
    public static final PotionEffectType INVISIBILITY = potion("INVISIBILITY");

    @SuppressWarnings("deprecation")
    public static Enchantment enchantment (String name)
    {
        if (name == null) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        String key = ENCHANT_ALIASES.getOrDefault(upper, upper.toLowerCase(Locale.ROOT));
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment == null) enchantment = Enchantment.getByName(upper);
        return enchantment;
    }

    public static Material material (String name)
    {
        if (name == null) return null;
        return Material.matchMaterial(name.trim());
    }

    public static EntityType entityType (String name)
    {
        if (name == null) return null;
        try
        {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    /**
     * Entero aleatorio en [min, max] (ambos incluidos). Tolera min == max y min > max
     */
    public static int randomBetween (int min, int max)
    {
        if (max < min)
        {
            int t = min;
            min = max;
            max = t;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static void warn (String message)
    {
        Bukkit.getLogger().log(Level.WARNING, "[BloodMoon] " + message);
    }
}
