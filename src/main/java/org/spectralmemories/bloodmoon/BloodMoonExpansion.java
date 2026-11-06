package org.spectralmemories.bloodmoon;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Placeholders de PlaceholderAPI. Usan el mundo del jugador; las que llevan _[mundo] al final usan ese mundo.
 *
 * %bloodmoon_active%            true / false
 * %bloodmoon_crimson%           true / false (la luna actual es Carmesi)
 * %bloodmoon_days_left%         dias hasta la proxima (0 si esta activa)
 * %bloodmoon_time_left%         tiempo real que le queda a la luna activa, mm:ss
 * %bloodmoon_kills%             kills del jugador en la luna actual
 * %bloodmoon_rank%              posicion del jugador en el ranking (0 si no tiene kills)
 * %bloodmoon_top_[n]_name%      nombre del puesto n
 * %bloodmoon_top_[n]_kills%     kills del puesto n
 * %bloodmoon_active_[mundo]%, %bloodmoon_crimson_[mundo]%, %bloodmoon_days_left_[mundo]%, %bloodmoon_time_left_[mundo]%
 */
public class BloodMoonExpansion extends PlaceholderExpansion
{
    private final Bloodmoon plugin;

    public BloodMoonExpansion (Bloodmoon plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier ()
    {
        return "bloodmoon";
    }

    @Override
    public String getAuthor ()
    {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion ()
    {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist ()
    {
        return true;
    }

    @Override
    public String onRequest (OfflinePlayer offlinePlayer, String params)
    {
        String p = params.toLowerCase(Locale.ROOT);
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        World playerWorld = player != null ? player.getWorld() : DefaultWorld();

        for (String prefix : new String[]{"days_left", "time_left", "crimson", "active"})
        {
            if (p.equals(prefix)) return WorldValue(prefix, playerWorld);
            if (p.startsWith(prefix + "_"))
            {
                World world = Bukkit.getWorld(params.substring(prefix.length() + 1));
                return world == null ? "" : WorldValue(prefix, world);
            }
        }

        BloodmoonActuator actuator = playerWorld == null ? null : BloodmoonActuator.GetActuator(playerWorld);

        if (p.equals("kills"))
        {
            return actuator == null || offlinePlayer == null ? "0" : String.valueOf(actuator.GetKills(offlinePlayer.getUniqueId()));
        }
        if (p.equals("rank"))
        {
            return actuator == null || offlinePlayer == null ? "0" : String.valueOf(actuator.GetRank(offlinePlayer.getUniqueId()));
        }
        if (p.startsWith("top_"))
        {
            // top_<n>_name / top_<n>_kills
            String[] parts = p.split("_");
            if (parts.length != 3) return null;
            int position;
            try
            {
                position = Integer.parseInt(parts[1]);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
            List<Map.Entry<UUID, Integer>> ranking = actuator == null ? null : actuator.GetRanking();
            boolean exists = ranking != null && position >= 1 && position <= ranking.size();
            if (parts[2].equals("name"))
            {
                if (!exists) return "-";
                String name = Bukkit.getOfflinePlayer(ranking.get(position - 1).getKey()).getName();
                return name == null ? "-" : name;
            }
            if (parts[2].equals("kills"))
            {
                return exists ? String.valueOf(ranking.get(position - 1).getValue()) : "0";
            }
        }
        return null;
    }

    private static World DefaultWorld ()
    {
        List<World> worlds = Bloodmoon.GetBloodMoonWorlds();
        return worlds == null || worlds.isEmpty() ? null : worlds.get(0);
    }

    private static String WorldValue (String key, World world)
    {
        BloodmoonActuator actuator = world == null ? null : BloodmoonActuator.GetActuator(world);
        boolean active = actuator != null && actuator.isInProgress();

        switch (key)
        {
            case "active":
                return String.valueOf(active);
            case "crimson":
                return String.valueOf(actuator != null && actuator.IsCrimson());
            case "days_left":
                if (actuator == null) return "-";
                if (active) return "0";
                int days = PeriodicNightCheck.GetDaysRemaining(world);
                return days < 0 ? "-" : String.valueOf(days);
            case "time_left":
                if (!active) return "-";
                ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
                if (reader != null && reader.GetPermanentBloodMoonConfig()) return "-";
                // la luna termina al amanecer (tick 24000); 20 ticks = 1 segundo
                long ticks = Math.max(0, PeriodicNightCheck.DAY - world.getTime());
                long seconds = ticks / 20;
                return String.format("%02d:%02d", seconds / 60, seconds % 60);
            default:
                return "";
        }
    }
}
