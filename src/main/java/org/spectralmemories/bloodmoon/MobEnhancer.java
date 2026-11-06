package org.spectralmemories.bloodmoon;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hace mas fuertes a los mobs que aparecen durante una BloodMoon: vida, velocidad, armadura y armas
 */
public final class MobEnhancer
{
    private MobEnhancer() {}

    /**
     * Mientras es true no se refuerza ningun mob. Se usa al crear jefes y sus esbirros
     */
    static boolean suppress = false;

    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    // La clave cambio en 1.21.3 (generic.max_health -> max_health); se prueban las dos
    private static final Attribute MAX_HEALTH = attribute("max_health", "generic.max_health");
    private static final Attribute MOVEMENT_SPEED = attribute("movement_speed", "generic.movement_speed");

    private static final Enchantment PROTECTION = Compat.enchantment("PROTECTION");
    private static final Enchantment SHARPNESS = Compat.enchantment("SHARPNESS");
    private static final Enchantment POWER = Compat.enchantment("POWER");

    private static Attribute attribute (String... keys)
    {
        for (String key : keys)
        {
            try
            {
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attribute != null) return attribute;
            }
            catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /**
     * @param multiplier 1.0 en una BloodMoon normal, CrimsonMoonMultiplier en una Luna Carmesi
     */
    public static void Enhance (LivingEntity mob, ConfigReader reader, double multiplier)
    {
        if (suppress || !reader.GetBool(ConfigReader.ENHANCED_MOBS_ENABLED, true)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        double healthMult = reader.GetDouble(ConfigReader.ENHANCED_MOBS_HEALTH, 1.0);
        if (healthMult > 0 && healthMult != 1.0)
        {
            AttributeInstance health = MAX_HEALTH == null ? null : mob.getAttribute(MAX_HEALTH);
            if (health != null)
            {
                health.setBaseValue(health.getBaseValue() * healthMult);
                mob.setHealth(health.getValue());
            }
        }

        double speedMult = reader.GetDouble(ConfigReader.ENHANCED_MOBS_SPEED, 1.15);
        if (speedMult > 0 && speedMult != 1.0)
        {
            AttributeInstance speed = MOVEMENT_SPEED == null ? null : mob.getAttribute(MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(speed.getBaseValue() * speedMult);
        }

        // Solo los mobs con forma humana muestran armadura y armas
        boolean isZombie = mob instanceof Zombie;
        boolean isSkeleton = mob instanceof AbstractSkeleton;
        if (!isZombie && !isSkeleton) return;

        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return;

        double armorChance = Math.min(1.0, reader.GetDouble(ConfigReader.ENHANCED_MOBS_ARMOR_CHANCE, 0.35) * multiplier);
        double weaponChance = Math.min(1.0, reader.GetDouble(ConfigReader.ENHANCED_MOBS_WEAPON_CHANCE, 0.25) * multiplier);
        double enchantChance = Math.min(1.0, reader.GetDouble(ConfigReader.ENHANCED_MOBS_ENCHANT_CHANCE, 0.25) * multiplier);
        float dropChance = (float) Math.max(0.0, Math.min(1.0, reader.GetDouble(ConfigReader.ENHANCED_MOBS_GEAR_DROP_CHANCE, 0.0)));

        if (random.nextDouble() < armorChance)
        {
            String tier = PickWeighted(reader.GetStringList(ConfigReader.ENHANCED_MOBS_ARMOR_TIERS));
            if (tier != null)
            {
                ItemStack[] armor = new ItemStack[4];
                for (int i = 0; i < ARMOR_PIECES.length; i++)
                {
                    // El casco siempre; el resto con 75 % cada pieza para que no todos vayan iguales
                    if (i > 0 && random.nextDouble() >= 0.75) continue;
                    Material material = Compat.material(tier.toUpperCase() + "_" + ARMOR_PIECES[i]);
                    if (material == null) continue;
                    armor[i] = new ItemStack(material);
                    if (PROTECTION != null && random.nextDouble() < enchantChance)
                        armor[i].addUnsafeEnchantment(PROTECTION, random.nextInt(1, 4));
                }
                if (armor[0] != null) { equipment.setHelmet(armor[0]); equipment.setHelmetDropChance(dropChance); }
                if (armor[1] != null) { equipment.setChestplate(armor[1]); equipment.setChestplateDropChance(dropChance); }
                if (armor[2] != null) { equipment.setLeggings(armor[2]); equipment.setLeggingsDropChance(dropChance); }
                if (armor[3] != null) { equipment.setBoots(armor[3]); equipment.setBootsDropChance(dropChance); }
            }
        }

        if (random.nextDouble() < weaponChance)
        {
            ItemStack weapon = null;
            if (isSkeleton)
            {
                ItemStack hand = equipment.getItemInMainHand();
                if (hand != null && hand.getType() == Material.BOW && POWER != null)
                {
                    weapon = hand.clone();
                    weapon.addUnsafeEnchantment(POWER, random.nextInt(1, 4));
                }
            }
            else
            {
                String name = PickWeighted(reader.GetStringList(ConfigReader.ENHANCED_MOBS_WEAPONS));
                Material material = Compat.material(name);
                if (material != null && material.isItem())
                {
                    weapon = new ItemStack(material);
                    if (SHARPNESS != null && random.nextDouble() < enchantChance)
                        weapon.addUnsafeEnchantment(SHARPNESS, random.nextInt(1, 4));
                }
            }
            if (weapon != null)
            {
                equipment.setItemInMainHand(weapon);
                equipment.setItemInMainHandDropChance(dropChance);
            }
        }
    }

    /**
     * Elige al azar una entrada "NOMBRE:PESO" segun su peso. Sin peso vale 1
     */
    static String PickWeighted (List<String> entries)
    {
        int total = 0;
        int[] weights = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++)
        {
            String[] parts = entries.get(i).split(":");
            int weight = 1;
            if (parts.length > 1)
            {
                try
                {
                    weight = Integer.parseInt(parts[1].trim());
                }
                catch (NumberFormatException ignored) {}
            }
            weights[i] = Math.max(0, weight);
            total += weights[i];
        }
        if (total <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < entries.size(); i++)
        {
            roll -= weights[i];
            if (roll < 0) return entries.get(i).split(":")[0].trim();
        }
        return null;
    }
}
