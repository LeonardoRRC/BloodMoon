package org.spectralmemories.bloodmoon;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee locales.yml. Los textos usan MiniMessage (https://docs.advntr.dev/minimessage/format.html);
 * los codigos legacy (&a, &l, &#RRGGBB) de archivos antiguos se siguen aceptando. Ver {@link Text}
 */
public class LocaleReader implements Closeable
{
    public static final String[] LOCALES_IDS = {
            "BloodMoonTitleBar",
            "DeathSuffix",
            "DaysBeforeBloodMoon",
            "BloodMoonRightNow",
            "BloodMoonTomorrow",
            "BloodMoonTonight",
            "BloodMoonWarningTitle",
            "BloodMoonWarningBody",
            "BloodMoonEndingMessage",
            "DyingResultsInInventoryLoss",
            "DyingResultsInExperienceLoss",
            "PluginReloaded",
            "NoBloodMoonInWorld",
            "CommandNotFound",
            "NoPermission",
            "AllowedCommands",
            "BedNotAllowed",
            "WorldIsPermanentBloodMoon",
            "CannotStopBloodMoon",
            "GeneralError",
            "ZombieBossSpawned",
            "ZombieBossName",
            "BossSlain",
            "HordeArrived",
            "NoPlayerOfName",
            "CrimsonMoonTitleBar",
            "CrimsonMoonWarning",
            "StartTitle",
            "StartSubtitle",
            "CrimsonStartTitle",
            "CrimsonStartSubtitle",
            "KillRankingHeader",
            "KillRankingLine",
            "KillRankingEmpty",
            "KillRewardReceived",
            "YourKills",
            "NoKillsYet",
            "HelpHeader",
            "HelpLine",
            "HelpHover",
            "HelpShow",
            "HelpTop",
            "HelpStart",
            "HelpStop",
            "HelpReload",
            "HelpSpawnZombieBoss",
            "HelpSpawnHorde",
            "HelpKillBosses"
    };
    public static final String[] DEFAULT_LOCALES = {
            "<dark_red><bold>BloodMoon",
            "<dark_red>during a BloodMoon!",
            "<green>There are <white>$d</white> days until the next BloodMoon",
            "<red>A BloodMoon is taking place right now!",
            "<gold>A BloodMoon is on its way tomorrow",
            "<dark_purple>The sky is darker than usual tonight",
            "<gradient:#7a0000:#ff2a2a><bold>The BloodMoon is upon us</bold></gradient>",
            "<red>Experience is multiplied, and mobs have bonus drops$n" +
                    "<red>Mobs are stronger and apply special debuffs",
            "<green><bold>The BloodMoon fades away... for now",
            "<dark_red><bold>Dying during a BloodMoon results in complete deletion of inventory",
            "<dark_red><bold>Dying during a BloodMoon resets your experience to zero",
            "<green>Plugin has been reloaded successfully",
            "<gray><italic>There is no BloodMoon in your current world",
            "<red>Command <white><italic>$d</italic></white> does not exist!",
            "<red>You do not have the permission to do that!",
            "<gold>Supported commands are: <white><italic>$d",
            "<red>You cannot sleep during a <dark_red>BloodMoon",
            "<red>This whole world is permanently in a BloodMoon!",
            "<red>You cannot stop the BloodMoon in this world!",
            "<red><italic>There was an error processing your request",
            "<white><bold>$b</bold> has arrived!",
            "<gradient:#3a7d2c:#9be15d><bold>The Tough One</bold></gradient>",
            "<white><bold>$p</bold> <dark_green>has slain <white>$b<dark_green>!",
            "<red>A horde has descended upon <white>$p</white>!",
            "<red>No player of name <white>$p</white> could be found in world <white>$w",
            "<gradient:#4b0012:#c2185b><bold>Crimson Moon</bold></gradient>",
            "<dark_red><bold>A CRIMSON MOON rises... monsters are far deadlier tonight",
            "<gradient:#7a0000:#ff2a2a><bold>BloodMoon</bold></gradient>",
            "<red>Survive the night",
            "<gradient:#4b0012:#c2185b><bold>Crimson Moon</bold></gradient>",
            "<red>No one is safe tonight",
            "<gold><bold>BloodMoon ranking:",
            "<yellow>#$r <white>$p <gray>- <red>$k kills",
            "<gray>Nobody slayed a single monster this BloodMoon",
            "<green>You got your BloodMoon reward for <white>$k</white> kills",
            "<gray>Your kills this BloodMoon: <red>$k</red> (rank <white>#$r</white>)",
            "<gray>No kills recorded in this BloodMoon yet",
            "<dark_red><bold>BloodMoon</bold> <gray>commands:",
            "<red>/bm $c <gray>- $d",
            "<gray>Click to type <white>/bm $c",
            "days until the next BloodMoon",
            "kill ranking of the current BloodMoon",
            "start a BloodMoon tonight (add 'crimson' for a Crimson Moon)",
            "end the current BloodMoon",
            "reload configs and locales",
            "spawn the zombie boss",
            "send a horde to a player",
            "remove every boss (true to give rewards)"
    };
    public static final String STRING_NOT_FOUND = "[String not found]";
    public static final String VERSION_CONFIG = "LocalesVersion";

    public static final String NULL_LOCALE = "null";
    public static final String VOID_STRING = "%void%";
    private File localeFile;
    private Map<String, Object> cache;

    public LocaleReader (File file)
    {
        localeFile = file;
    }

    @Override
    public void close() throws IOException
    {

    }


    //===========================================================

    /**
     * Envia un locale a todos los jugadores del servidor y a la consola
     */
    public static void BroadcastLocale (String id, String[] args, Object[] replacements)
    {
        Component message = Bloodmoon.GetInstance().getLocaleReader().Get(id, args, replacements);
        if (message != null) Bukkit.getServer().sendMessage(message);
    }

    /**
     * Envia un locale a todos los jugadores de un mundo
     */
    public static void MessageAllLocale (String id, String[] args, Object[] replacements, World world)
    {
        Component message = Bloodmoon.GetInstance().getLocaleReader().Get(id, args, replacements);
        if (message == null) return;
        for (Player player : world.getPlayers())
        {
            player.sendMessage(message);
        }
    }

    /**
     * Envia un locale a un jugador o a la consola
     */
    public static void MessageLocale (String id, String[] args, Object[] replacements, CommandSender sender)
    {
        Component message = Bloodmoon.GetInstance().getLocaleReader().Get(id, args, replacements);
        if (message != null) sender.sendMessage(message);
    }

    /**
     * @return el locale parseado, o null si esta silenciado con %void%
     */
    public Component Get (String id, String[] args, Object[] replacements)
    {
        String raw = GetRaw(id);
        if (raw.isEmpty()) return null;
        return Text.Parse(raw, args, replacements);
    }

    /**
     * @return el locale parseado; un texto vacio si esta silenciado
     */
    public Component Get (String id)
    {
        Component component = Get(id, null, null);
        return component == null ? Component.empty() : component;
    }

    /**
     * @return el texto tal como esta en locales.yml, o "" si esta silenciado con %void%
     */
    public String GetRaw (String id)
    {
        try
        {
            Object locale = GetLocale(id);

            String value = String.valueOf(locale);
            if (locale != null && !value.equals(NULL_LOCALE))
            {
                return value.equals(VOID_STRING) ? "" : value;
            }
        }
        catch (Exception ignored) {}

        for (int i = 0; i < LOCALES_IDS.length; i++)
        {
            if (LOCALES_IDS[i].equals(id))
            {
                AppendDefault(id, DEFAULT_LOCALES[i]);
                return DEFAULT_LOCALES[i];
            }
        }

        Compat.warn("There was a problem loading the locale '" + id + "'. Please add it to the " + Bloodmoon.LOCALES_YML + " file");
        return STRING_NOT_FOUND;
    }

    private static String YamlQuote (String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void AppendDefault (String id, String value)
    {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(localeFile, true), StandardCharsets.UTF_8))
        {
            writer.write(id + ": " + YamlQuote(value) + "\n");
        }
        catch (IOException e)
        {
            Compat.warn("could not add locale '" + id + "' to " + Bloodmoon.LOCALES_YML);
        }
        RefreshLocales();
    }

    public String GetFileVersion ()
    {
        try
        {
            Object interval = GetLocale(VERSION_CONFIG);
            if (interval == null)
            {
                interval = "NaN";
            }
            return String.valueOf(interval);
        }
        catch (FileNotFoundException e)
        {
            return "NaN";
        }
    }

    //===========================================================

    public void RefreshLocales ()
    {
        cache = null;
    }

    private Object GetLocale (String locale) throws FileNotFoundException
    {
        if (cache == null)
        {
            Yaml yaml = new Yaml();
            try (Reader reader = new InputStreamReader(new FileInputStream(localeFile), StandardCharsets.UTF_8))
            {
                cache = yaml.load(reader);
            }
            catch (FileNotFoundException e)
            {
                throw e;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            if (cache == null) cache = new HashMap<>();
        }

        return cache.get(locale);
    }

    public void ReadAllEntries()
    {
        for (String str : LOCALES_IDS)
        {
            try
            {
                GetLocale(str);
            }
            catch (FileNotFoundException ignored){}
        }
    }

    public void GenerateDefaultFile()
    {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(localeFile, true), StandardCharsets.UTF_8))
        {
            writer.write("#Plugin version. Please do not tamper\n");
            writer.write("LocalesVersion: " + Bloodmoon.GetInstance().getDescription().getVersion() + "\n\n");
            writer.write("#Locales file. Texts use MiniMessage: https://docs.advntr.dev/minimessage/format.html\n");
            writer.write("#  colors <red>, <#ff5555>, <gradient:#7a0000:#ff2a2a>, <rainbow>, styles <bold>, <italic>,\n");
            writer.write("#  <hover:show_text:'...'>, <click:run_command:'/bm top'>, <newline> and every other MiniMessage tag\n");
            writer.write("#Old color codes (&a, &l, &#RRGGBB) still work and can be mixed with tags\n");
            writer.write("#Use $n to create a new line\n");
            writer.write("#Some entries accept parameters such as $d, $p, $k, $b or $w. Player names are inserted as plain text\n");
            writer.write("#You can completely silence a line by assigning \"%void%\"\n\n");
            for (int i = 0; i < LOCALES_IDS.length; i++)
            {
                writer.write(LOCALES_IDS[i] + ": " + YamlQuote(DEFAULT_LOCALES[i]) + "\n");
            }
        }
        catch (IOException e)
        {
            Compat.warn("could not generate " + Bloodmoon.LOCALES_YML);
        }
    }
}
