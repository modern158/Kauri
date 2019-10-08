package cc.funkemunky.anticheat;

import cc.funkemunky.anticheat.api.checks.CheckInfo;
import cc.funkemunky.anticheat.api.checks.CheckManager;
import cc.funkemunky.anticheat.api.data.DataManager;
import cc.funkemunky.anticheat.api.data.banwave.BanwaveManager;
import cc.funkemunky.anticheat.api.data.logging.LoggerManager;
import cc.funkemunky.anticheat.api.data.stats.StatsManager;
import cc.funkemunky.anticheat.api.pup.AntiPUPManager;
import cc.funkemunky.anticheat.api.utils.Message;
import cc.funkemunky.anticheat.api.utils.VPNUtils;
import cc.funkemunky.anticheat.impl.commands.kauri.KauriCommand;
import cc.funkemunky.anticheat.impl.listeners.CustomListeners;
import cc.funkemunky.anticheat.impl.listeners.ImportantListeners;
import cc.funkemunky.anticheat.impl.listeners.LegacyListeners;
import cc.funkemunky.anticheat.impl.menu.InputHandler;
import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.events.AtlasListener;
import cc.funkemunky.api.profiling.BaseProfiler;
import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.updater.UpdaterUtils;
import cc.funkemunky.api.utils.*;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.lang.reflect.Modifier;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

@Getter
public class Kauri extends JavaPlugin {
    @Getter
    private static Kauri instance;
    public ExecutorService dedicatedVPN = Executors.newSingleThreadExecutor();
    public long lastLogin;
    private DataManager dataManager;
    private CheckManager checkManager;
    private StatsManager statsManager;
    private AntiPUPManager antiPUPManager;
    private LoggerManager loggerManager;
    private BanwaveManager banwaveManager;
    private int ticks;
    private long profileStart, lastTimeStamp, lastTPS, tpsMilliseconds;
    private double tps;
    private ScheduledExecutorService executorService, vpnSchedular = Executors.newSingleThreadScheduledExecutor();
    private BaseProfiler profiler;
    private VPNUtils vpnUtils;
    private String requiredVersionOfAtlas = "1.3.9";
    private List<String> usableVersionsOfAtlas = Arrays.asList("1.3.4", "1.3.5", "1.3.6", "1.3.7", "1.3.8", "1.3.9");
    private FileConfiguration messages;
    private File messagesFile;
    private boolean runningPaperSpigot;

    @Override
    public void onEnable() {
        //This allows us to access this class's contents from others places.
        MiscUtils.printToConsole("&cStarting Kauri...");
        instance = this;
        saveDefaultConfig();
        saveDefaultMessages();

        if (InputHandler.testMode == -69 && (Bukkit.getPluginManager().getPlugin("KauriLoader") == null || !Bukkit.getPluginManager().getPlugin("KauriLoader").isEnabled())) return;


        if(Bukkit.getVersion().contains("Paper")) {
            runningPaperSpigot = true;
        }

        profiler = new BaseProfiler();
        profileStart = System.currentTimeMillis();

        executorService = Executors.newScheduledThreadPool(2);

        dataManager = new DataManager();
        checkManager = new CheckManager();

        startScanner(false);

        antiPUPManager = new AntiPUPManager();
        dataManager.registerAllPlayers();

        //Starting up our utilities, managers, and tasks.

        loggerManager = new LoggerManager(Atlas.getInstance().getCarbon());
        loggerManager.loadFromDatabase();
        statsManager = new StatsManager();
        banwaveManager = new BanwaveManager();

        vpnUtils = new VPNUtils();
        new KauriAPI();

        runTasks();
        registerListeners();
        registerCommands();
    }

    public void onDisable() {
        statsManager.saveStats();
        loggerManager.saveToDatabase();
        loggerManager.getViolations().clear();
        Atlas.getInstance().getCommandManager().getMap().getCommand(ImportantListeners.mainCommand).unregister(Atlas.getInstance().getCommandManager().getMap());
        org.bukkit.event.HandlerList.unregisterAll(this);
        dataManager.getDataObjects().clear();
        checkManager.getChecks().clear();
        Atlas.getInstance().getCommandManager().unregisterCommands();
        Atlas.getInstance().getCommandManager().unregisterBukkitCommand(Atlas.getInstance().getCommandManager().getMap().getCommand(CustomListeners.isAllowed ? ImportantListeners.mainCommand : "kauri"));
        executorService.shutdownNow();
        //Bukkit.getMessenger().unregisterIncomingPluginChannel(this, "Lunar-Client");
        //Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, "Lunar-Client");
    }

    private void runTasks() {
        //This allows us to use ticks for intervalTime comparisons to allow for more parrallel calculations to actual Minecraft
        //and it also has the added benefit of being lighter than using System.currentTimeMillis.
        new BukkitRunnable() {

            public void run() {
                if(ticks++ >= 39) {
                    long timeStamp = System.currentTimeMillis();
                    tpsMilliseconds = timeStamp - lastTimeStamp;
                    tps = 1000D / tpsMilliseconds * 40;
                    lastTimeStamp = timeStamp;
                    ticks = 0;
                }
                lastTPS = System.currentTimeMillis();
            }
        }.runTaskTimer(this, 0L, 0L);
    }

    public void startScanner(boolean configOnly) {
        initializeScanner(getClass(), this, configOnly);
    }

    private void registerCommands() {
        Atlas.getInstance().getFunkeCommandManager().addCommand(this, new KauriCommand());
    }

    public double getTPS(RoundingMode mode, int places) {
        return MathUtils.round(tps, places, mode);
    }

    public double getTpsMS() {
        return 50 / (2000D / tpsMilliseconds);
    }

    public void reloadKauri() {
        if(InputHandler.testMode == -69) {
            MiscUtils.printToConsole("&cReloading Kauri...");
            long start = System.currentTimeMillis();
            MiscUtils.unloadPlugin("KauriLoader");
            MiscUtils.unloadPlugin("Atlas");
            MiscUtils.loadPlugin("Atlas");
            MiscUtils.loadPlugin("KauriLoader");
            MiscUtils.printToConsole("&aCompleted reload in " + (System.currentTimeMillis() - start) + " milliseconds!");
        } else {
            reloadConfig();
            reloadMessages();
            profiler.reset();
            HandlerList.unregisterAll(this);
            Atlas.getInstance().getEventManager().unregisterAll(this);
            checkManager = new CheckManager();
            antiPUPManager = new AntiPUPManager();
            dataManager = new DataManager();
            startScanner(false);
            dataManager.registerAllPlayers();
        }
    }

    private void registerListeners() {
        if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_12)) {
            getServer().getPluginManager().registerEvents(new LegacyListeners(), this);
        }
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(UpdaterUtils.getPluginDirectory(), "messages.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Look for defaults in the jar
        try {
            Reader defConfigStream = new InputStreamReader(this.getResource("messages.yml"), "UTF8");
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            messages.setDefaults(defConfig);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            reloadMessages();
        }
        return messages;
    }

    public void saveMessages() {
        if (messages == null || messagesFile == null) {
            return;
        }
        try {
            getMessages().save(messagesFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save messages file to " + messagesFile, ex);
        }
    }

    public void saveDefaultMessages() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            this.saveResource("messages.yml", false);
        }
    }
    //Credits: Luke.

    private void initializeScanner(Class<?> mainClass, Plugin plugin, boolean configOnly) {
        ClassScanner.scanFile(null, mainClass).stream().filter(c -> {
            try {
                Class clazz = Class.forName(c);

                return clazz.isAnnotationPresent(Init.class) || clazz.isAnnotationPresent(CheckInfo.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }).sorted(Comparator.comparingInt(c -> {
            try {
                Class clazz = Class.forName(c);

                if (clazz.isAnnotationPresent(Init.class)) {
                    Init annotation = (Init) clazz.getAnnotation(Init.class);

                    return annotation.priority().getPriority();
                } else {
                    return 3;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 3;
        })).forEachOrdered(c -> {
            try {
                Class clazz = Class.forName(c);

                Object obj = clazz.equals(this.getClass()) ? this : clazz.newInstance();

                if (clazz.isAnnotationPresent(Init.class)) {
                    Init init = (Init) clazz.getAnnotation(Init.class);

                    if (obj instanceof Listener) {
                        MiscUtils.printToConsole("&eFound " + clazz.getSimpleName() + " Bukkit listener. Registering...");
                        plugin.getServer().getPluginManager().registerEvents((Listener) obj, plugin);
                    }
                    if (obj instanceof AtlasListener) {
                        MiscUtils.printToConsole("&eFound " + clazz.getSimpleName() + "Atlas listener. Registering...");
                        Atlas.getInstance().getEventManager().registerListeners((AtlasListener) obj, plugin);
                    }

                    if (init.commands()) {
                        Atlas.getInstance().getCommandManager().registerCommands(plugin, obj);
                    }


                    Arrays.stream(clazz.getDeclaredFields()).filter(field -> field.getAnnotations().length > 0).forEach(field -> {
                        try {
                            field.setAccessible(true);
                            if (field.isAnnotationPresent(ConfigSetting.class)) {
                                String name = field.getAnnotation(ConfigSetting.class).name();
                                String path = field.getAnnotation(ConfigSetting.class).path() + "." + (name.length() > 0 ? name : field.getName());
                                try {
                                    MiscUtils.printToConsole("&eFound " + field.getName() + " ConfigSetting (default=" + field.get(obj) + ").");
                                    if (plugin.getConfig().get(path) == null) {
                                        MiscUtils.printToConsole("&eValue not found in configuration! Setting default into config...");
                                        plugin.getConfig().set(path, field.get(obj));
                                        plugin.saveConfig();
                                    } else {
                                        MiscUtils.printToConsole("&eValue found in configuration! Set value to &a" + plugin.getConfig().get(path));
                                        field.set(Modifier.isStatic(field.getModifiers()) ? null : obj, plugin.getConfig().get(path));
                                    }
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            } else if (!configOnly && field.isAnnotationPresent(Message.class)) {
                                Message msg = field.getAnnotation(Message.class);

                                MiscUtils.printToConsole("&eFound " + field.getName() + " Message (default=" + field.get(obj) + ").");
                                if (getMessages().get(msg.name()) != null) {
                                    MiscUtils.printToConsole("&eValue not found in message configuration! Setting default into messages.yml...");
                                    field.set(Modifier.isStatic(field.getModifiers()) ? null : obj, getMessages().getString(msg.name()));
                                } else {
                                    getMessages().set(msg.name(), field.get(obj));
                                    saveMessages();
                                    MiscUtils.printToConsole("&eValue found in message configuration! Set value to &a" + plugin.getConfig().get(msg.name()));
                                }
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });

                }

                if (clazz.isAnnotationPresent(CheckInfo.class)) {
                    getCheckManager().getCheckClasses().add(clazz);

                    MiscUtils.printToConsole("&eFound check &a" + ((CheckInfo) clazz.getAnnotation(CheckInfo.class)).name() + "&e! Registering...");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        getCheckManager().getCheckClasses().forEach(clazz -> getCheckManager().registerCheck(clazz, getCheckManager().getChecks()));
        MiscUtils.printToConsole("&aCompleted!");
    }
}