package org.spectralmemories.bloodmoon;


import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * This is the class that handles most interaction during a BloodMoon
 */
public class BloodmoonActuator implements Listener, Runnable, Closeable
{
    //Eligible mobs
    public final EntityType[] rewardedTypes = {
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CREEPER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.WITCH,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.PHANTOM,
            EntityType.ENDERMAN
    };

    private int originalMaxSpawn = 0;

    private static Map<World, BloodmoonActuator> actuators;

    private World world;
    private boolean inProgress;

    private BossBar nightBar;
    private final Set<UUID> barViewers = new HashSet<>();
    private ActuatorPeriodic actuatorPeriodic;

    private List<LivingEntity> blacklistedMobs;
    private List<IBoss> bosses;

    // 0.9
    private boolean crimson = false;
    /** Tick (getFullTime) en que termina la luna por BloodMoonDurationMinutes, o -1 si dura hasta el amanecer */
    private long endsAt = -1;
    private boolean forceCrimsonNext = false;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private int ambientTask = -1;

    private static final Set<CreatureSpawnEvent.SpawnReason> ENHANCED_REASONS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.PATROL
    );

    private void AddActuator (BloodmoonActuator instance)
    {
        if (actuators == null) actuators = new HashMap<>();

        actuators.put(instance.world, instance);
    }

    public static BloodmoonActuator GetActuator (World world)
    {
        try
        {
            return actuators.get(world);
        } catch (Exception ignored)
        {
        }
        return null;
    }


    public BloodmoonActuator (World world)
    {
        this.world = world;
        inProgress = false;
        AddActuator(this);
        blacklistedMobs = new ArrayList<>();
        bosses = new ArrayList<>();

        if (Bloodmoon.GetInstance().getConfigReader(world).GetPermanentBloodMoonConfig())
        {
            StartBloodMoon();
        }
    }

    /**
     * La proxima BloodMoon que empiece en este mundo sera una Luna Carmesi
     */
    public void ForceNextCrimson ()
    {
        forceCrimsonNext = true;
    }

    public boolean IsCrimson ()
    {
        return crimson && isInProgress();
    }

    public World GetWorld ()
    {
        return world;
    }

    /**
     * Multiplicador extra: 1.0 en una BloodMoon normal, CrimsonMoonMultiplier en una Luna Carmesi
     */
    public double GetDifficultyMultiplier ()
    {
        return crimson ? Bloodmoon.GetInstance().getConfigReader(world).GetCrimsonMultiplier() : 1.0;
    }

    /**
     * Ticks que faltan para que termine: al amanecer o al cumplirse BloodMoonDurationMinutes, lo que llegue antes
     */
    public long GetTicksLeft ()
    {
        long untilDawn = Math.max(0, PeriodicNightCheck.DAY - world.getTime());
        if (endsAt < 0) return untilDawn;
        return Math.max(0, Math.min(untilDawn, endsAt - world.getFullTime()));
    }

    /**
     * Duracion total de esta luna en ticks, para la barra de progreso
     */
    public long GetTotalTicks ()
    {
        double minutes = Bloodmoon.GetInstance().getConfigReader(world).GetDouble(ConfigReader.DURATION_MINUTES, 0);
        long night = PeriodicNightCheck.DAY - 12000;
        return endsAt < 0 || minutes <= 0 ? night : Math.max(1, Math.min(night, Math.round(minutes * 1200)));
    }

    /**
     * @return true si ya se cumplio BloodMoonDurationMinutes
     */
    public boolean IsDurationOver ()
    {
        return inProgress && endsAt >= 0 && world.getFullTime() >= endsAt;
    }

    public void StartBloodMoon ()
    {
        ConfigReader startReader = Bloodmoon.GetInstance().getConfigReader(world);
        boolean permanent = startReader.GetPermanentBloodMoonConfig();
        double crimsonChance = startReader.GetDouble(ConfigReader.CRIMSON_MOON_CHANCE, 15);
        crimson = forceCrimsonNext || (!permanent && crimsonChance > 0 && new Random().nextDouble() * 100 < crimsonChance);
        forceCrimsonNext = false;
        kills.clear();

        double minutes = startReader.GetDouble(ConfigReader.DURATION_MINUTES, 0);
        endsAt = (!permanent && minutes > 0) ? world.getFullTime() + Math.round(minutes * 1200) : -1;

        inProgress = true;
        RunPreCommand();
        if (crimson) RunCommands(startReader.GetStringList(ConfigReader.COMMANDS_ON_CRIMSON_START).toArray(new String[0]));

        ShowNightBar();
        BroadcastBloodMoonWarning();
        for (Player player : world.getPlayers()) ShowStartEffects(player);
        StartAmbience();

        actuatorPeriodic = new ActuatorPeriodic(world);
        actuatorPeriodic.run();

        SpawnBosses();
        StartSpawningOfHordes();

        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);

        originalMaxSpawn = world.getMonsterSpawnLimit();
        world.setMonsterSpawnLimit(reader.GetSpawnRateConfig());
    }

    public void StopBloodMoon ()
    {
        StopBloodMoon(true);
    }

    /**
     * @param giveRewards false al apagar el servidor, para no repartir premios de una luna a medias
     */
    public void StopBloodMoon (boolean giveRewards)
    {
        if (Bloodmoon.GetInstance().getConfigReader(world).GetPermanentBloodMoonConfig())
        {
            return;
        }
        if (giveRewards) HandleKillRewards();
        inProgress = false;
        crimson = false;
        endsAt = -1;

        StopStorm();
        HideNightBar();
        StopAmbience();

        if (actuatorPeriodic != null) actuatorPeriodic.close();
        actuatorPeriodic = null;
        blacklistedMobs.clear();
        KillBosses();
        world.setMonsterSpawnLimit(originalMaxSpawn);
        RunPostCommand();
    }

    public void KillBosses ()
    {
        KillBosses(false);
    }

    public void KillBosses (boolean giveRewards)
    {
        KillBosses(giveRewards, true);
    }

    public void KillBosses (boolean giveRewards, boolean effects)
    {
        KillBosses(giveRewards, effects, true);
    }

    public void KillBosses (boolean giveRewards, boolean effects, boolean respawn)
    {
        Iterator var2 = bosses.iterator();

        while (var2.hasNext())
        {
            IBoss IBoss = (IBoss) var2.next();
            IBoss.Kill(giveRewards, effects, respawn);
        }

        bosses.clear();
    }

    public void SpawnHorde ()
    {
        Random random = new Random();
        Player[] players = world.getPlayers().toArray(new Player[0]);
        if(players.length > 0)
        {
            SpawnHorde(players[random.nextInt(players.length)]);
        }
    }

    public void SpawnHorde (Player target)
    {
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (!reader.GetHordeEnabled()) return;

        Random random = new Random();
        Location hordeSpawnLocation = target.getLocation().clone();

        int mobAmount = Compat.randomBetween(reader.GetHordeMinPopulation(), reader.GetHordeMaxPopulation());
        int maxDistance = Math.max(1, reader.GetHordeSpawnDistance());

        String[] mobList = reader.GetHordeMobWhitelist();
        if (mobList.length == 0) return;

        int spawned = 0;
        for (int i = 0; i < mobAmount; i++)
        {
            String mobName = mobList[random.nextInt(mobList.length)];
            EntityType mobType = Compat.entityType(mobName);
            if (mobType == null || !mobType.isSpawnable() || !mobType.isAlive())
            {
                Compat.warn("Mob invalido en HordeMobWhitelist del mundo " + world.getName() + ": " + mobName);
                continue;
            }

            Location newMobLocation = hordeSpawnLocation.clone().add(
                    random.nextInt(maxDistance * 2 + 1) - maxDistance,
                    0,
                    random.nextInt(maxDistance * 2 + 1) - maxDistance);
            newMobLocation.setY(world.getHighestBlockYAt(newMobLocation) + 1);

            world.spawn(newMobLocation, mobType.getEntityClass(),
                    (java.util.function.Consumer<Entity>) mob -> LevelledMobsHook.Mark(mob, LevelledMobsHook.TYPE_HORDE));
            world.strikeLightningEffect(newMobLocation);
            spawned++;
        }
        if (spawned > 0)
            LocaleReader.MessageAllLocale("HordeArrived", new String[]{"$p"}, new Object[]{target.displayName()}, world);
    }

    public void StartSpawningOfHordes ()
    {
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (!reader.GetHordeEnabled()) return;

        int min = Math.max(20, reader.GetHordeSpawnrateBaseline() - reader.GetHordeSpawnrateVariation());
        int max = Math.max(min, reader.GetHordeSpawnrateBaseline() + reader.GetHordeSpawnrateVariation());

        Bloodmoon.GetInstance().GetScheduler().scheduleSyncDelayedTask(Bloodmoon.GetInstance(), new Runnable()
        {
            @Override
            public void run ()
            {
                if (isInProgress())
                {
                    SpawnHorde();
                    Bloodmoon.GetInstance().GetScheduler().scheduleSyncDelayedTask(Bloodmoon.GetInstance(), this, Compat.randomBetween(min, max));
                }
            }
        }, Compat.randomBetween(min, max));
    }


    private void RunPreCommand ()
    {
        RunCommands(Bloodmoon.GetInstance().getConfigReader(world).GetPreBloodMoonCommands());
    }

    private void RunPostCommand ()
    {
        RunCommands(Bloodmoon.GetInstance().getConfigReader(world).GetPostBloodMoonCommands());
    }

    /**
     * Ejecuta comandos con el formato "comando;s", "comando;f" o "comando;p"
     * s = consola una vez, f = consola una vez por jugador, p = el jugador lo ejecuta
     */
    private void RunCommands (String[] commands)
    {
        for (String command : commands)
        {
            int separator = command.lastIndexOf(';');
            if (separator < 0)
            {
                Compat.warn("Comando sin ';s', ';f' o ';p' al final: '" + command + "'");
                continue;
            }
            String base = command.substring(0, separator).replace("$w", world.getName());
            String mode = command.substring(separator + 1).trim();

            if (mode.equalsIgnoreCase("s"))
            {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), base);
            }
            else if (mode.equalsIgnoreCase("p") || mode.equalsIgnoreCase("f"))
            {
                for (Player player : new ArrayList<>(world.getPlayers()))
                {
                    String finalCommand = base.replace("$p", player.getName());
                    if (mode.equalsIgnoreCase("p")) player.performCommand(finalCommand);
                    else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                }
            }
            else
            {
                Compat.warn("No se pudo interpretar el comando '" + command + "'");
            }
        }
    }

    public void SpawnBosses ()
    {
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        Bloodmoon.GetInstance().getServer().getScheduler().scheduleSyncDelayedTask(Bloodmoon.GetInstance(), new Runnable()
        {
            public void run ()
            {
                if (reader.GetEnableZombieBossConfig()) SpawnZombieBoss();
            }
        }, (long) ((new Random()).nextInt(2000) + 400));
    }

    public void SpawnZombieBoss ()
    {
        if (world.getPlayers().size() > 0)
        {
            List<Player> players = world.getPlayers();
            Random rnd = new Random();
            int index = rnd.nextInt(players.size());
            Player chosenOne = (Player) players.get(index);
            Location spawn = chosenOne.getLocation();
            Location newLocation = spawn.clone();
            newLocation.add((double) (rnd.nextInt(10) + 10), 0.0D, (double) (rnd.nextInt(10) + 10));
            newLocation.setY((double) world.getHighestBlockYAt(newLocation) + 1);
            ZombieIBoss zombieBoss;
            MobEnhancer.suppress = true;
            try
            {
                zombieBoss = new ZombieIBoss(newLocation);
            }
            finally
            {
                MobEnhancer.suppress = false;
            }
            zombieBoss.Start();
            bosses.add(zombieBoss);
        }
    }

    public void AddToBlacklist (LivingEntity entity)
    {
        blacklistedMobs.add(entity);
    }

    private void StopStorm ()
    {
        world.setStorm(false);
        world.setThundering(false);
    }

    private void ShowNightBar ()
    {
        LocaleReader localeReader = Bloodmoon.GetInstance().getLocaleReader();
        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);

        if (configReader.GetPermanentBloodMoonConfig()) return; //disable nightbar if permanent BM

        Component barTitle = localeReader.Get(crimson ? "CrimsonMoonTitleBar" : "BloodMoonTitleBar");
        BossBar.Color barColor = crimson ? BossBar.Color.PURPLE : BossBar.Color.RED;
        Set<BossBar.Flag> flags = configReader.GetDarkenSkyConfig()
                ? EnumSet.of(BossBar.Flag.CREATE_WORLD_FOG, BossBar.Flag.DARKEN_SCREEN)
                : EnumSet.noneOf(BossBar.Flag.class);
        HideNightBar();
        nightBar = BossBar.bossBar(barTitle, 0.0f, barColor, BossBar.Overlay.NOTCHED_12, flags);
        Bloodmoon.GetInstance().GetScheduler().runTaskLater(Bloodmoon.GetInstance(), this, 0);

        List<Player> players = world.getPlayers();
        for (Player player : players)
        {
            ShowNightBarPlayer(player);
        }

        UpdateNightBar();
    }


    private void HideNightBar ()
    {
        if (nightBar != null)
        {
            for (UUID uuid : barViewers)
            {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.hideBossBar(nightBar);
            }
        }
        barViewers.clear();
        nightBar = null;
    }

    private void ShowNightBarPlayer (Player player)
    {
        if (nightBar == null) return;
        player.showBossBar(nightBar);
        barViewers.add(player.getUniqueId());
    }

    private void HideNightBarPlayer (Player player)
    {
        if (nightBar != null) player.hideBossBar(nightBar);
        barViewers.remove(player.getUniqueId());
    }

    private void UpdateNightBar ()
    {
        if (Bloodmoon.GetInstance().getConfigReader(world).GetPermanentBloodMoonConfig())
        {
            if (nightBar != null) nightBar.progress(1.0f);
            return;
        }
        double percent = (double) GetTicksLeft() / (double) GetTotalTicks();

        if (nightBar != null && percent >= 0.0 && percent <= 1.0f) nightBar.progress((float) (1.0 - percent));
    }

    private void HandleReconnectingPlayer (Player player)
    {
        if (isInProgress() && nightBar != null) ShowNightBarPlayer(player);
        BroadcastBloodMoonWarningPlayer(player);
    }

    //=================================== Ambiente ===================================

    private void ShowStartEffects (Player player)
    {
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        LocaleReader locales = Bloodmoon.GetInstance().getLocaleReader();

        if (reader.GetBool(ConfigReader.START_TITLE, true))
        {
            player.showTitle(Title.title(
                    locales.Get(crimson ? "CrimsonStartTitle" : "StartTitle"),
                    locales.Get(crimson ? "CrimsonStartSubtitle" : "StartSubtitle"),
                    Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(3500), java.time.Duration.ofSeconds(1))));
        }

        String sound = reader.GetString(ConfigReader.START_SOUND, "entity.wither.spawn").trim();
        if (!sound.isEmpty() && !sound.equalsIgnoreCase("none"))
        {
            // La version con String acepta ids de Minecraft (y de resource packs) en cualquier version
            player.playSound(player.getLocation(), sound.toLowerCase(Locale.ROOT), 1.0f, crimson ? 0.6f : 0.8f);
        }
    }

    private void StartAmbience ()
    {
        StopAmbience();
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (!reader.GetBool(ConfigReader.AMBIENT_PARTICLES, true)) return;

        int amount = Math.max(0, reader.GetInt(ConfigReader.AMBIENT_PARTICLE_AMOUNT, 6));
        if (amount == 0) return;

        Particle.DustOptions dust = new Particle.DustOptions(crimson ? Color.fromRGB(90, 0, 40) : Color.fromRGB(150, 0, 0), 1.6f);
        ambientTask = Bloodmoon.GetInstance().GetScheduler().scheduleSyncRepeatingTask(Bloodmoon.GetInstance(), () ->
        {
            if (!isInProgress())
            {
                StopAmbience();
                return;
            }
            for (Player player : world.getPlayers())
            {
                player.spawnParticle(Compat.DUST_RED, player.getLocation().add(0, 1.5, 0), amount, 6, 3, 6, 0, dust);
            }
        }, 10L, 10L);
    }

    private void StopAmbience ()
    {
        if (ambientTask != -1)
        {
            Bloodmoon.GetInstance().GetScheduler().cancelTask(ambientTask);
            ambientTask = -1;
        }
    }

    //=================================== Kills y premios ===================================

    public int GetKills (UUID player)
    {
        return kills.getOrDefault(player, 0);
    }

    /**
     * @return jugadores ordenados por kills, de mayor a menor
     */
    public List<Map.Entry<UUID, Integer>> GetRanking ()
    {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(kills.entrySet());
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /**
     * @return posicion del jugador en el ranking (1 = primero) o 0 si no tiene kills
     */
    public int GetRank (UUID player)
    {
        List<Map.Entry<UUID, Integer>> ranking = GetRanking();
        for (int i = 0; i < ranking.size(); i++)
        {
            if (ranking.get(i).getKey().equals(player)) return i + 1;
        }
        return 0;
    }

    private static String NameOf (UUID uuid)
    {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private void HandleKillRewards ()
    {
        if (!inProgress) return;
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        if (!reader.GetBool(ConfigReader.KILL_RANKING_ENABLED, true)) return;

        List<Map.Entry<UUID, Integer>> ranking = GetRanking();
        if (ranking.isEmpty())
        {
            LocaleReader.MessageAllLocale("KillRankingEmpty", null, null, world);
            return;
        }

        int shown = Math.min(ranking.size(), Math.max(0, reader.GetInt(ConfigReader.KILL_RANKING_SIZE, 3)));
        if (shown > 0)
        {
            LocaleReader.MessageAllLocale("KillRankingHeader", null, null, world);
            for (int i = 0; i < shown; i++)
            {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                LocaleReader.MessageAllLocale("KillRankingLine", new String[]{"$r", "$p", "$k"},
                        new String[]{String.valueOf(i + 1), NameOf(entry.getKey()), String.valueOf(entry.getValue())}, world);
            }
        }

        int minimum = Math.max(0, reader.GetInt(ConfigReader.KILL_REWARD_MINIMUM_KILLS, 5));

        for (String line : reader.GetStringList(ConfigReader.KILL_REWARD_TOP_COMMANDS))
        {
            int separator = line.indexOf(';');
            if (separator < 0)
            {
                Compat.warn("KillRewardTopCommands necesita el formato \"[POSICION];[COMANDO]\": '" + line + "'");
                continue;
            }
            int position;
            try
            {
                position = Integer.parseInt(line.substring(0, separator).trim());
            }
            catch (NumberFormatException e)
            {
                Compat.warn("Posicion invalida en KillRewardTopCommands: '" + line + "'");
                continue;
            }
            if (position < 1 || position > ranking.size()) continue;
            Map.Entry<UUID, Integer> entry = ranking.get(position - 1);
            if (entry.getValue() < minimum) continue;
            RunRewardCommand(line.substring(separator + 1), entry);
        }

        List<String> participation = reader.GetStringList(ConfigReader.KILL_REWARD_PARTICIPATION_COMMANDS);
        for (Map.Entry<UUID, Integer> entry : ranking)
        {
            if (entry.getValue() < minimum) continue;
            for (String command : participation) RunRewardCommand(command, entry);

            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null)
                LocaleReader.MessageLocale("KillRewardReceived", new String[]{"$k"}, new String[]{String.valueOf(entry.getValue())}, online);
        }
    }

    private void RunRewardCommand (String command, Map.Entry<UUID, Integer> entry)
    {
        String finalCommand = command.trim()
                .replace("$p", NameOf(entry.getKey()))
                .replace("$k", String.valueOf(entry.getValue()))
                .replace("$w", world.getName());
        if (finalCommand.startsWith("/")) finalCommand = finalCommand.substring(1);
        if (finalCommand.isEmpty()) return;
        if (finalCommand.toLowerCase().startsWith("item "))
        {
            GiveRewardItem(finalCommand.substring(5).trim(), entry.getKey());
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
    }

    /**
     * "item [ITEM]:[CANTIDAD]" de las recompensas: entrega el item al jugador; lo que no cabe cae a sus pies
     */
    private void GiveRewardItem (String spec, UUID playerId)
    {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null)
        {
            Compat.warn("No se entrego '" + spec + "' a " + NameOf(playerId) + ": no estaba conectado al terminar la luna");
            return;
        }
        // ParseItem espera ITEM:CANTIDAD:PESO; el peso no se usa aqui
        String[] head = spec.split(":(?=\\$(name|desc|enchant))", 2);
        String[] base = head[0].split(":");
        String normalized = base[0] + ":" + (base.length > 1 ? base[1] : "1") + ":1" + (head.length > 1 ? ":" + head[1] : "");
        ItemStack item = ParseItem(normalized);
        if (item == null) return;

        int total = 1;
        try
        {
            if (base.length > 1) total = Math.max(1, Integer.parseInt(base[1].trim()));
        }
        catch (NumberFormatException ignored) {}

        // Se reparte en stacks validos (por ejemplo 100 lingotes = 64 + 36)
        int max = Math.max(1, item.getMaxStackSize());
        while (total > 0)
        {
            ItemStack stack = item.clone();
            stack.setAmount(Math.min(max, total));
            total -= stack.getAmount();
            for (ItemStack left : player.getInventory().addItem(stack).values())
                player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private void BroadcastBloodMoonWarning ()
    {
        for (Player player : world.getPlayers())
        {
            BroadcastBloodMoonWarningPlayer(player);
        }
    }


    private void BroadcastBloodMoonWarningPlayer (Player player)
    {
        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);

        LocaleReader.MessageLocale("BloodMoonWarningTitle", null, null, player);
        LocaleReader.MessageLocale("BloodMoonWarningBody", null, null, player);
        if (crimson) LocaleReader.MessageLocale("CrimsonMoonWarning", null, null, player);

        if (configReader.GetInventoryLossConfig())
        {
            LocaleReader.MessageLocale("DyingResultsInInventoryLoss", null, null, player);
        }
        if (configReader.GetExperienceLossConfig())
        {
            LocaleReader.MessageLocale("DyingResultsInExperienceLoss", null, null, player);
        }
    }

    /**
     * Generates a random item to be used as a reward
     *
     * @return
     */
    public ItemStack GetRandomBonus ()
    {
        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);
        String[] items = configReader.GetItemListConfig();

        List<String> entries = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;

        for (String entry : items)
        {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            try
            {
                int itemWeight = Integer.parseInt(parts[2].trim());
                if (itemWeight <= 0) continue;
                entries.add(entry);
                weights.add(itemWeight);
                totalWeight += itemWeight;
            }
            catch (NumberFormatException e)
            {
                Compat.warn("Peso invalido en DropItemList: '" + entry + "'");
            }
        }

        if (totalWeight <= 0) return null;

        int rng = new Random().nextInt(totalWeight);
        for (int i = 0; i < entries.size(); i++)
        {
            rng -= weights.get(i);
            if (rng < 0) return ParseItem(entries.get(i));
        }
        return null;
    }

    /**
     * Convierte una linea "MATERIAL:CANTIDAD:PESO[:$name ...][:$desc ...][:$enchant ...]" en un ItemStack
     */
    public static ItemStack ParseItem (String entry)
    {
        // MATERIAL:CANTIDAD:PESO y despues secciones ":$name", ":$desc", ":$enchant" que pueden contener ':'
        String[] head = entry.split(":(?=\\$(name|desc|enchant))");
        String[] base = head[0].split(":", 3);
        String[] parts = new String[3 + head.length - 1];
        for (int i = 0; i < 3; i++) parts[i] = i < base.length ? base[i] : "1";
        System.arraycopy(head, 1, parts, 3, head.length - 1);
        int amount = 1;
        try
        {
            if (parts.length > 1) amount = Math.max(1, Integer.parseInt(parts[1].trim()));
        }
        catch (NumberFormatException ignored) {}

        ItemStack itemStack;
        if (NexoHook.IsNexoItem(parts[0]))
        {
            itemStack = NexoHook.Create(parts[0]);
            if (itemStack == null) return null;
            itemStack.setAmount(Math.min(amount, itemStack.getMaxStackSize()));
        }
        else
        {
            Material itemMaterial = Compat.material(parts[0]);
            if (itemMaterial == null || !itemMaterial.isItem())
            {
                Compat.warn("Material desconocido en DropItemList: '" + parts[0] + "'");
                return null;
            }
            itemStack = new ItemStack(itemMaterial, amount);
        }

        for (int i = 3; i < parts.length; i++)
        {
            String line = parts[i];
            if (line.startsWith("$name"))
            {
                line = line.substring(Math.min(line.length(), "$name".length() + 1));

                ItemMeta meta = itemStack.getItemMeta();
                if (meta == null) continue;
                meta.displayName(Text.Item(line.trim()));
                itemStack.setItemMeta(meta);
            }
            else if (line.startsWith("$desc"))
            {
                line = line.substring(Math.min(line.length(), "$desc".length() + 1));

                ItemMeta meta = itemStack.getItemMeta();
                if (meta == null) continue;
                List<Component> lore = new ArrayList<>();
                for (String loreLine : line.split("\\$n|<newline>|<br>")) lore.add(Text.Item(loreLine.trim()));
                meta.lore(lore);
                itemStack.setItemMeta(meta);
            }
            else if (line.startsWith("$enchant"))
            {
                line = line.substring(Math.min(line.length(), "$enchant".length() + 1));

                for (String enchantLine : line.split(";"))
                {
                    if (enchantLine.trim().isEmpty()) continue;
                    String[] enchant = enchantLine.split(",");
                    Enchantment enchantment = Compat.enchantment(enchant[0]);
                    if (enchantment == null)
                    {
                        Compat.warn("Encantamiento desconocido en DropItemList: '" + enchant[0] + "'");
                        continue;
                    }
                    int level = 1;
                    try
                    {
                        if (enchant.length > 1) level = Integer.parseInt(enchant[1].trim());
                    }
                    catch (NumberFormatException ignored) {}
                    // unsafe permite niveles por encima del maximo vanilla y encantar cualquier item
                    itemStack.addUnsafeEnchantment(enchantment, level);
                }
            }
        }

        return itemStack;
    }

    public boolean IsInProtectedWGRegion (Player player){
        try
        {
            RegionContainer rc = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery rq = rc.createQuery();
            ApplicableRegionSet rs = rq.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

            if (rs == null || rs.size() == 0) return false;

            boolean isProtected = !rs.testState(null, Flags.MOB_DAMAGE);
            return isProtected;
        }catch (NoClassDefFoundError e){
            //Server likely does not have WG
            return false;
        }
    }

    private void ApplySpecialEffect (Player player, LivingEntity mob)
    {
        if(IsInProtectedWGRegion(player)) return;

        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);
        String mobTypeName = mob.getType().name().toUpperCase();
        for (IBoss boss : bosses)
        {
            if (boss.GetHost() == mob)
            {
                mobTypeName += "BOSS";
                break;
            }
        }

        String[] configs = configReader.GetMobEffectConfig(mobTypeName);

        for (String str : configs)
        {
            if (str.equals("LIGHTNING"))
            {
                world.strikeLightning(player.getLocation());
                continue;
            }


            String[] parts = str.split(",");
            if (parts.length < 3)
            {
                Compat.warn("Efecto mal escrito en " + mobTypeName + "Effects: '" + str + "'");
                continue;
            }
            PotionEffectType type = Compat.potionOrWarn(parts[0], mobTypeName + "Effects");
            if (type == null) continue;
            try
            {
                int ticks = (int) (20f * Float.parseFloat(parts[1].trim()));
                int amp = Integer.parseInt(parts[2].trim());
                player.addPotionEffect(new PotionEffect(type, ticks, amp));
            }
            catch (NumberFormatException e)
            {
                Compat.warn("Numero invalido en " + mobTypeName + "Effects: '" + str + "'");
            }
        }
    }

    public boolean isInProgress ()
    {
        ConfigReader reader = Bloodmoon.GetInstance().getConfigReader(world);
        return inProgress || reader.GetPermanentBloodMoonConfig();
    }


    //Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn (CreatureSpawnEvent event)
    {
        if (event.getEntity().getWorld() != world || !isInProgress()) return;
        if (!ENHANCED_REASONS.contains(event.getSpawnReason())) return;

        LivingEntity entity = event.getEntity();
        boolean eligible = false;
        for (EntityType type : rewardedTypes)
        {
            if (entity.getType() == type)
            {
                eligible = true;
                break;
            }
        }
        if (!eligible) return;

        // Va antes que LevelledMobs (que escucha en MONITOR), asi sus reglas ya ven la marca
        LevelledMobsHook.Mark(entity, crimson ? LevelledMobsHook.TYPE_CRIMSON : LevelledMobsHook.TYPE_BLOODMOON);
        MobEnhancer.Enhance(entity, Bloodmoon.GetInstance().getConfigReader(world), GetDifficultyMultiplier());
    }

    @EventHandler
    public void onPlayerConnect (PlayerJoinEvent event)
    {
        if (isInProgress() && event.getPlayer().getWorld() == world)
        {
            HandleReconnectingPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerTeleport (PlayerTeleportEvent event)
    {
        World to = event.getTo().getWorld();
        World from = event.getFrom().getWorld();
        if (to != world && from != world) return; //None of our concern

        if (from != to)
        {
            if (to == world && isInProgress())
            {
                //Someone entered our bm world
                HandleReconnectingPlayer(event.getPlayer());
            }
            if (from == world && isInProgress())
            {
                //Someone left our bm world
                HideNightBarPlayer(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn (PlayerRespawnEvent event)
    {
        World from = event.getPlayer().getWorld();
        World to = event.getRespawnLocation().getWorld();
        if (to != world && from != world) return; //None of our concern

        if (from != to)
        {
            if (to == world && isInProgress())
            {
                //Someone respawned in our bm world
                HandleReconnectingPlayer(event.getPlayer());
            }
            if (from == world)
            {
                //Someone respawned out of our bm world
                HideNightBarPlayer(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath (PlayerDeathEvent event)
    {
        if (!isInProgress()) return; //Only during BloodMoon

        LocaleReader localeReader = Bloodmoon.GetInstance().getLocaleReader();
        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);

        Player deadplayer = event.getEntity();
        if (deadplayer.getWorld() != world) return; //Wrong world

        if (configReader.GetLightningEffectConfig()) world.strikeLightningEffect(deadplayer.getLocation());

        Component deathMessage = event.deathMessage();
        Component suffix = localeReader.Get("DeathSuffix", null, null);

        // deathMessage() es null si el servidor u otro plugin desactivo los mensajes de muerte
        if (deathMessage != null && suffix != null && !Text.Plain(deathMessage).contains(Text.Plain(suffix)))
        {
            event.deathMessage(deathMessage.append(Component.space()).append(suffix));
        }

        if (configReader.GetExperienceLossConfig())
        {
            event.setNewTotalExp(0);
            event.setDroppedExp(0);
        }


        if (configReader.GetInventoryLossConfig()) event.getDrops().clear();
    }

    @EventHandler
    public void onPlayerSleeps (PlayerBedEnterEvent event)
    {
        if (event.getPlayer().getWorld() == world)
        {
            if (isInProgress())
            {
                ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);
                if (configReader.GetPreventSleepingConfig())
                {
                    LocaleReader localeReader = Bloodmoon.GetInstance().getLocaleReader();

                    LocaleReader.MessageLocale("BedNotAllowed", null, null, event.getPlayer());
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onMobSpawn (SpawnerSpawnEvent event)
    {
        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);

        if (configReader.GetMobsFromSpawnerNoRewardConfig() && event.getEntity().getWorld() == world && isInProgress())
        {
            for (EntityType type : rewardedTypes)
            {
                if (event.getEntityType() == type)
                {
                    if (event.getEntity() instanceof LivingEntity)
                    {
                        blacklistedMobs.add((LivingEntity) event.getEntity());
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMobDeath (EntityDeathEvent event)
    {
        LivingEntity entity = event.getEntity();

        for (IBoss boss : bosses)
        {
            if (entity == boss.GetHost())
            {
                Player killer = boss.GetHost().getKiller();
                if (killer != null)
                {

                    LocaleReader.MessageAllLocale("BossSlain", new String[]{"$b", "$p"}, new Object[]{boss.GetName(), killer.getName()}, world);
                }

                boss.Kill(killer != null && isInProgress());
                bosses.remove(boss);
                return;
            }
        }

        if (!isInProgress()) return; //Only during BloodMoon

        if (event.getEntity() instanceof Player) return; //Handled in another method

        ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);


        if (entity.getWorld() != world) return; //Wrong world

        if (blacklistedMobs.contains(entity))
        {
            //This mob was explicitely blacklisted. ignore it
            blacklistedMobs.remove(entity);
            return;
        }

        boolean eligible = false;
        for (EntityType type : rewardedTypes)
        {
            if (entity.getType() == type) eligible = true;
        }

        if (!eligible) return; //Not eligible for reward

        double difficulty = GetDifficultyMultiplier();
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * configReader.GetExpMultConfig() * difficulty));

        Player killer = entity.getKiller();
        if (killer != null) kills.merge(killer.getUniqueId(), 1, Integer::sum);

        if (configReader.GetMobDeathThunderConfig())
            world.strikeLightningEffect(event.getEntity().getLocation());

        List<ItemStack> bonusDrops = new ArrayList<>();

        int min = configReader.GetMinItemsDropConfig();
        int max = configReader.GetMaxItemsDropConfig();

        int itemCount = (int) Math.round(Compat.randomBetween(min, max) * difficulty);

        for (int i = 0; i < itemCount; i++)
        {
            ItemStack bonus = GetRandomBonus();
            if (bonus != null) bonusDrops.add(bonus);
        }

        for (ItemStack item : bonusDrops)
        {
            world.dropItemNaturally(entity.getLocation(), item); //Drop items
        }
    }

    @EventHandler
    public void onEntityDamage (EntityDamageByEntityEvent event)
    {
        if (!isInProgress()) return; //Only during BloodMoon

        Entity receiver = event.getEntity();
        Entity damager = event.getDamager();

        if (receiver.getWorld() != world || damager.getWorld() != world) return; //Wrong world
        if (damager instanceof Projectile) //if its any damage dealing projectile
        {
            ProjectileSource source = ((Projectile) damager).getShooter(); //Get the shooter as Source
            if (source instanceof LivingEntity) //Source is a mob, not a block
            {
                damager = (LivingEntity) source;
            }
        }


        if (receiver instanceof LivingEntity && damager instanceof LivingEntity)
        {
            ConfigReader configReader = Bloodmoon.GetInstance().getConfigReader(world);
            if (receiver instanceof Player)
            {
                for (EntityType type : rewardedTypes)
                {
                    if (damager.getType() == type)
                    {
                        //Player is damaged by monster
                        if (event.getFinalDamage() == 0 && configReader.GetShieldPreventEffects())
                            return; //Hit was shielded. We shall not apply configs
                        event.setDamage(event.getDamage() * configReader.GetMobDamageMultConfig() * GetDifficultyMultiplier());
                        ApplySpecialEffect((Player) receiver, (LivingEntity) damager);
                        if (configReader.GetPlayerDamageSoundConfig())
                            ((Player) receiver).playSound(receiver.getLocation(), Sound.AMBIENT_CAVE, 80.0f, 1.5f);
                        if (configReader.GetPlayerHitParticleConfig())
                            world.spawnParticle(Particle.FLAME, receiver.getLocation(), 60);
                    }
                }
            } else if (damager instanceof Player)
            {
                for (EntityType type : rewardedTypes)
                {
                    if (receiver.getType() == type)
                    {
                        //Player dealt damage to monster
                        event.setDamage((int) Math.ceil(event.getDamage() / (configReader.GetMobHealthMultConfig() * GetDifficultyMultiplier())));
                        if (configReader.GetMobHitParticleConfig())
                            world.spawnParticle(Compat.CRIT_MAGIC, receiver.getLocation(), 60);

                    }
                }
            }
        }
    }

    /**
     * Runs the actuator's checkup routine. Called internally, you don't need to call it yourself
     */
    @Override
    public void run ()
    {
        if (isInProgress())
        {
            UpdateNightBar();
            Bloodmoon.GetInstance().GetScheduler().runTaskLater(Bloodmoon.GetInstance(), this, 20);
        }
    }

    /**
     * Closes the actuator. You should discard it after doing so
     */
    @Override
    public void close ()
    {
        if (bosses.isEmpty()) return; //Nothing to do

        KillBosses(false, false);
        world.save();
    }
}
