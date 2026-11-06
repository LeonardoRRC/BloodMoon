package org.spectralmemories.bloodmoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /bloodmoon (alias /bm). Los jugadores actuan sobre su mundo; la consola debe indicar el mundo
 * como segundo argumento: "bm start world", "bm spawnhorde world Steve"
 */
public class BloodmoonCommandExecutor implements CommandExecutor, TabCompleter
{
    // subcomando, permiso, clave de la ayuda en locales.yml
    private static final String[][] SUBCOMMANDS = {
            {"show", "show", "HelpShow"},
            {"top", "top", "HelpTop"},
            {"start", "start", "HelpStart"},
            {"stop", "stop", "HelpStop"},
            {"reload", "reload", "HelpReload"},
            {"spawnzombieboss", "spawnzombieboss", "HelpSpawnZombieBoss"},
            {"spawnhorde", "spawnhorde", "HelpSpawnHorde"},
            {"killbosses", "killbosses", "HelpKillBosses"},
    };

    @Override
    public boolean onCommand (CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0 || args[0].equalsIgnoreCase("help"))
        {
            SendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        if (!IsKnown(sub))
        {
            LocaleReader.MessageLocale("CommandNotFound", new String[]{"$d"}, new String[]{args[0]}, sender);
            SendHelp(sender);
            return true;
        }

        if (sub.equals("reload"))
        {
            return Confirm(sender, ExecuteReload(sender), sub, "-");
        }

        World world;
        if (sender instanceof Player)
        {
            world = ((Player) sender).getWorld();
        }
        else
        {
            if (rest.isEmpty())
            {
                sender.sendMessage("Please suffix your command with the world name: bloodmoon " + sub + " <world>");
                return true;
            }
            world = Bukkit.getWorld(rest.remove(0));
            if (world == null)
            {
                sender.sendMessage("[Error] No world named " + args[1] + " could be found!");
                return true;
            }
        }

        String param = rest.isEmpty() ? "" : rest.get(0);
        switch (sub)
        {
            case "show":
                return ExecuteShow(sender, world);
            case "top":
                return ExecuteTop(sender, world);
            case "start":
                return Confirm(sender, ExecuteStart(sender, world, param.equalsIgnoreCase("crimson")), sub, world.getName());
            case "stop":
                return Confirm(sender, ExecuteStop(sender, world), sub, world.getName());
            case "spawnzombieboss":
                return Confirm(sender, ExecuteSpawnZombieBoss(sender, world), sub, world.getName());
            case "killbosses":
                return Confirm(sender, ExecuteKillBosses(sender, world, Boolean.parseBoolean(param)), sub, world.getName());
            case "spawnhorde":
                return Confirm(sender, ExecuteSpawnHorde(sender, world, param), sub, world.getName());
            default:
                return true;
        }
    }

    private static boolean IsKnown (String sub)
    {
        for (String[] entry : SUBCOMMANDS)
        {
            if (entry[0].equals(sub)) return true;
        }
        return false;
    }

    private void SendHelp (CommandSender sender)
    {
        LocaleReader locales = Bloodmoon.GetInstance().getLocaleReader();
        LocaleReader.MessageLocale("HelpHeader", null, null, sender);
        for (String[] entry : SUBCOMMANDS)
        {
            if (!CheckPermission(sender, entry[1])) continue;
            String usage = entry[0];
            if (entry[0].equals("start")) usage += " [crimson]";
            if (entry[0].equals("spawnhorde")) usage += " [player]";
            if (entry[0].equals("killbosses")) usage += " [true|false]";
            if (!(sender instanceof Player) && !entry[0].equals("reload"))
                usage = entry[0] + " <world>" + usage.substring(entry[0].length());
            Component line = locales.Get("HelpLine", new String[]{"$c", "$d"}, new Object[]{usage, locales.Get(entry[2])});
            if (line == null) continue;
            if (sender instanceof Player)
            {
                // Al hacer clic se escribe el comando en el chat, listo para completar
                line = line.clickEvent(ClickEvent.suggestCommand("/bm " + entry[0] + " "));
                Component hover = locales.Get("HelpHover", new String[]{"$c"}, new Object[]{entry[0]});
                if (hover != null) line = line.hoverEvent(HoverEvent.showText(hover));
            }
            sender.sendMessage(line);
        }
    }

    //=================================== Tab complete ===================================

    @Override
    public List<String> onTabComplete (CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> options = new ArrayList<>();
        boolean isPlayer = sender instanceof Player;

        if (args.length == 1)
        {
            options.add("help");
            for (String[] entry : SUBCOMMANDS)
            {
                if (CheckPermission(sender, entry[1])) options.add(entry[0]);
            }
            return Filter(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!IsKnown(sub) || sub.equals("reload") || !CheckPermission(sender, sub)) return Collections.emptyList();

        // La consola escribe primero el mundo
        int index = args.length - 2;
        World world = isPlayer ? ((Player) sender).getWorld() : null;
        if (!isPlayer)
        {
            if (index == 0)
            {
                for (World w : Bloodmoon.GetBloodMoonWorlds()) options.add(w.getName());
                return Filter(options, args[1]);
            }
            world = Bukkit.getWorld(args[1]);
            index--;
        }

        if (index != 0) return Collections.emptyList();
        String current = args[args.length - 1];

        switch (sub)
        {
            case "start":
                options.add("crimson");
                break;
            case "killbosses":
                options.add("true");
                options.add("false");
                break;
            case "spawnhorde":
                if (world != null)
                {
                    for (Player player : world.getPlayers()) options.add(player.getName());
                }
                break;
            default:
                break;
        }
        return Filter(options, current);
    }

    private static List<String> Filter (List<String> options, String prefix)
    {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options)
        {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        }
        return result;
    }

    //=================================== Subcomandos ===================================

    private boolean ExecuteShow (CommandSender playerSender, World world)
    {
        if (! CheckPermission(playerSender, "show"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, playerSender);
            return false;
        }

        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);

        if (configReader == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, playerSender);
            return true;
        }

        if (configReader.GetPermanentBloodMoonConfig())
        {
            LocaleReader.MessageLocale("WorldIsPermanentBloodMoon", null, null, playerSender);
            return true;
        }

        BloodmoonActuator worldActuator = BloodmoonActuator.GetActuator(world);

        if (worldActuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, playerSender);
            return true;
        }

        if (worldActuator.isInProgress())
        {
            LocaleReader.MessageLocale("BloodMoonRightNow", null, null, playerSender);
            return true;
        }

        int remainingDays = PeriodicNightCheck.GetDaysRemaining(world);
        if (remainingDays < 0)
        {
            Compat.warn("remainingDays was inferior to 0. Please regenerate both the cache and the config for world " + world.getName());
            LocaleReader.MessageLocale("GeneralError", null, null, playerSender);
        }
        else
        {
            LocaleReader.MessageLocale("DaysBeforeBloodMoon", new String[]{"$d"}, new String[]{String.valueOf(remainingDays)}, playerSender);
        }

        return true;
    }

    private boolean ExecuteTop (CommandSender sender, World world)
    {
        if (!CheckPermission(sender, "top"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }

        BloodmoonActuator actuator = BloodmoonActuator.GetActuator(world);
        if (actuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }

        List<Map.Entry<UUID, Integer>> ranking = actuator.GetRanking();
        if (ranking.isEmpty())
        {
            LocaleReader.MessageLocale("NoKillsYet", null, null, sender);
            return true;
        }

        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        int shown = Math.min(ranking.size(), Math.max(1, reader.GetInt(ConfigReader.KILL_RANKING_SIZE, 3)));
        LocaleReader.MessageLocale("KillRankingHeader", null, null, sender);
        for (int i = 0; i < shown; i++)
        {
            Map.Entry<UUID, Integer> entry = ranking.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            LocaleReader.MessageLocale("KillRankingLine", new String[]{"$r", "$p", "$k"},
                    new String[]{String.valueOf(i + 1), name == null ? "?" : name, String.valueOf(entry.getValue())}, sender);
        }

        if (sender instanceof Player)
        {
            UUID uuid = ((Player) sender).getUniqueId();
            LocaleReader.MessageLocale("YourKills", new String[]{"$k", "$r"},
                    new String[]{String.valueOf(actuator.GetKills(uuid)), String.valueOf(actuator.GetRank(uuid))}, sender);
        }
        return true;
    }

    private boolean ExecuteStart (CommandSender sender, World world, boolean crimson)
    {
        if (! CheckPermission(sender, "start"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }

        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (reader == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }

        if (reader.GetPermanentBloodMoonConfig())
        {
            LocaleReader.MessageLocale("WorldIsPermanentBloodMoon", null, null, sender);
            return true;
        }

        PeriodicNightCheck nightCheck = PeriodicNightCheck.GetPeriodicNightCheck(world);
        BloodmoonActuator actuator = BloodmoonActuator.GetActuator(world);
        if (nightCheck == null || actuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }
        if (crimson) actuator.ForceNextCrimson();
        nightCheck.SetCheckAfter(0);
        nightCheck.SetDaysRemaining(0);
        world.setTime(12001);
        return true;
    }

    private boolean ExecuteStop (CommandSender sender, World world)
    {
        if (! CheckPermission(sender, "stop"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }

        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (reader == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }

        if (reader.GetPermanentBloodMoonConfig())
        {
            LocaleReader.MessageLocale("CannotStopBloodMoon", null, null, sender);
            return true;
        }

        PeriodicNightCheck nightCheck = PeriodicNightCheck.GetPeriodicNightCheck(world);
        if (nightCheck == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }

        nightCheck.SetCheckAfter(0);
        nightCheck.SetDaysRemaining(nightCheck.GetBloodMoonInterval() - 1);
        world.setTime(0);
        return true;
    }

    private boolean ExecuteSpawnZombieBoss (CommandSender sender, World world)
    {
        if (!CheckPermission(sender, "spawnzombieboss"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }
        BloodmoonActuator actuator = BloodmoonActuator.GetActuator(world);
        if (actuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }
        actuator.SpawnZombieBoss();
        return true;
    }

    private boolean ExecuteKillBosses (CommandSender sender, World world, boolean giveRewards)
    {
        if (!CheckPermission(sender, "killbosses"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }
        BloodmoonActuator actuator = BloodmoonActuator.GetActuator(world);
        if (actuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }
        actuator.KillBosses(giveRewards, true, false);
        return true;
    }

    private boolean ExecuteSpawnHorde (CommandSender sender, World world, String playerName)
    {
        if (!CheckPermission(sender, "spawnhorde"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }
        BloodmoonActuator actuator = BloodmoonActuator.GetActuator(world);
        if (actuator == null)
        {
            LocaleReader.MessageLocale("NoBloodMoonInWorld", null, null, sender);
            return true;
        }
        if (playerName.isEmpty())
        {
            actuator.SpawnHorde();
            return true;
        }

        Player player = null;
        for (Player prospect : world.getPlayers())
        {
            if (prospect.getName().equalsIgnoreCase(playerName) || Text.Plain(prospect.displayName()).equalsIgnoreCase(playerName))
            {
                player = prospect;
                break;
            }
        }

        if (player == null)
        {
            LocaleReader.MessageLocale("NoPlayerOfName", new String[] {"$p","$w"}, new String[] {playerName, world.getName()}, sender);
            return false;
        }

        actuator.SpawnHorde(player);
        return true;
    }

    private boolean ExecuteReload (CommandSender sender)
    {
        if (! CheckPermission(sender, "reload"))
        {
            LocaleReader.MessageLocale("NoPermission", null, null, sender);
            return false;
        }

        LocaleReader localeReader = Bloodmoon.GetInstance().getLocaleReader();
        localeReader.RefreshLocales();

        for (ConfigReader configReader : Bloodmoon.GetInstance().getAllConfigReaders())
        {
            configReader.RefreshConfigs();
        }

        if (sender instanceof Player) LocaleReader.MessageLocale("PluginReloaded", null, null, sender);
        return true;
    }

    private boolean CheckPermission (CommandSender sender, String node)
    {
        // La consola y RCON no son Player y tienen todos los permisos
        return !(sender instanceof Player) || sender.hasPermission("bloodmoon." + node);
    }

    private boolean Confirm (CommandSender sender, boolean success, String command, String worldName)
    {
        if (sender instanceof Player) return true;
        if (success)
        {
            sender.sendMessage("Command " + command + " has been successfully ran in world " + worldName);
        }
        else
        {
            sender.sendMessage("There was a problem running command " + command);
        }
        return true;
    }
}
