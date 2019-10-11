package dev.brighten.anticheat.check.api;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.tinyprotocol.api.packets.reflections.types.WrappedClass;
import cc.funkemunky.api.utils.Color;
import cc.funkemunky.api.utils.MathUtils;
import cc.funkemunky.api.utils.MiscUtils;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.impl.combat.aim.*;
import dev.brighten.anticheat.check.impl.combat.autoclicker.AutoclickerA;
import dev.brighten.anticheat.check.impl.combat.autoclicker.AutoclickerB;
import dev.brighten.anticheat.check.impl.combat.autoclicker.AutoclickerC;
import dev.brighten.anticheat.check.impl.combat.autoclicker.AutoclickerD;
import dev.brighten.anticheat.check.impl.combat.hitbox.Hitboxes;
import dev.brighten.anticheat.check.impl.combat.reach.Reach;
import dev.brighten.anticheat.check.impl.movement.fly.FlyA;
import dev.brighten.anticheat.check.impl.movement.fly.FlyB;
import dev.brighten.anticheat.check.impl.movement.fly.FlyC;
import dev.brighten.anticheat.check.impl.movement.nofall.NoFall;
import dev.brighten.anticheat.check.impl.movement.speed.SpeedA;
import dev.brighten.anticheat.check.impl.movement.speed.SpeedB;
import dev.brighten.anticheat.check.impl.movement.speed.SpeedC;
import dev.brighten.anticheat.check.impl.movement.speed.SpeedD;
import dev.brighten.anticheat.check.impl.movement.velocity.VelocityA;
import dev.brighten.anticheat.check.impl.movement.velocity.VelocityB;
import dev.brighten.anticheat.check.impl.packets.Timer;
import dev.brighten.anticheat.check.impl.packets.badpackets.BadPacketsA;
import dev.brighten.anticheat.check.impl.packets.badpackets.BadPacketsB;
import dev.brighten.anticheat.check.impl.packets.badpackets.BadPacketsC;
import dev.brighten.anticheat.data.ObjectData;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Check {

    public static Map<WrappedClass, CheckInfo> checkClasses = Collections.synchronizedMap(new HashMap<>());
    public static Map<WrappedClass, CheckSettings> checkSettings = Collections.synchronizedMap(new HashMap<>());

    public Check() {

    }

    public ObjectData data;
    public String name, description;
    public boolean enabled, executable, developer;
    public float vl, punishVl;
    public CheckType checkType;

    private static void register(Check check) {
        if(!check.getClass().isAnnotationPresent(CheckInfo.class)) {
            MiscUtils.printToConsole("Could not register "  + check.getClass().getSimpleName() + " because @CheckInfo was not present.");
            return;
        }
        CheckInfo info = check.getClass().getAnnotation(CheckInfo.class);
        MiscUtils.printToConsole("Registered: " + info.name());
        WrappedClass checkClass = new WrappedClass(check.getClass());
        String name = info.name();
        CheckSettings settings = new CheckSettings(info.name(), info.description(), info.checkType(), info.punishVL());
        if(Kauri.INSTANCE.getConfig().get("checks." + name + ".enabled") != null) {
            settings.enabled = Kauri.INSTANCE.getConfig().getBoolean("checks." + name + ".enabled");
            settings.executable = Kauri.INSTANCE.getConfig().getBoolean("checks." + name + ".executable");
        } else {
            Kauri.INSTANCE.getConfig().set("checks." + name + ".enabled", info.enabled());
            Kauri.INSTANCE.getConfig().set("checks." + name + ".executable", info.executable());
            Kauri.INSTANCE.saveConfig();

            settings.enabled = info.enabled();
            settings.executable = info.executable();
        }
        checkSettings.put(checkClass, settings);
        checkClasses.put(checkClass, info);
    }

    public void flag(String information) {
        final String info = information
                .replace("%p", String.valueOf(data.lagInfo.transPing))
                .replace("%t", String.valueOf(MathUtils.round(Kauri.INSTANCE.tps, 2)));
        if(Kauri.INSTANCE.lastTickLag.hasPassed() && (data.lagInfo.lastPacketDrop.hasPassed(5) || data.lagInfo.lastPingDrop.hasPassed())) {
            Kauri.INSTANCE.loggerManager.addLog(data, this, info);
            Kauri.INSTANCE.dataManager.hasAlerts.forEach(data -> {
                data.getPlayer().sendMessage(Color.translate("&8[&6K&8] &f" + this.data.getPlayer().getName() + " &7flagged &f" + name + " &8(&e" + info + "&8) &8[&c" + vl + "&8]"));
            });

            if(punishVl != -1 && vl > punishVl) {
                punish();
            }

            if(Config.bungeeAlerts) {
                try {
                    Atlas.getInstance().getBungeeManager().sendObjects("ALL", data.getPlayer().getUniqueId(), name, vl, info);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void punish() {
        if(executable && punishVl != -1 && vl > punishVl) {
            Kauri.INSTANCE.loggerManager.addPunishment(data, this);
            if(!Config.broadcastMessage.equalsIgnoreCase("off")) {
                Bukkit.broadcastMessage(Color.translate(Config.broadcastMessage.replace("%name%", data.getPlayer().getName())));
            }
            ConsoleCommandSender sender = Bukkit.getConsoleSender();
            Config.punishCommands.
                    forEach(cmd -> Bukkit.dispatchCommand(
                            sender,
                            cmd.replace("%name%", data.getPlayer().getName())));
            vl = 0;
        }
    }

    public void debug(String information) {
        if(Kauri.INSTANCE.dataManager.debugging.size() == 0) return;
        Kauri.INSTANCE.dataManager.debugging.stream()
                .filter(data -> data.debugged.equals(this.data.uuid) && data.debugging.equalsIgnoreCase(name))
                .forEach(data -> {
                    data.getPlayer().sendMessage(Color.translate("&8[&c&lDEBUG&8] &7" + information));
                });
    }

    public static void registerChecks() {
        register(new AutoclickerA());
        register(new AutoclickerB());
        register(new AutoclickerC());
        register(new AutoclickerD());
        register(new FlyA());
        register(new FlyB());
        register(new FlyC());
        register(new NoFall());
        register(new Reach());
        register(new Hitboxes());
        register(new AimA());
        register(new AimB());
        register(new AimC());
        register(new AimD());
        register(new AimE());
        register(new AimF());
        register(new AimG());
        register(new AimH());
        register(new SpeedA());
        register(new SpeedB());
        register(new SpeedC());
        register(new SpeedD());
        register(new Timer());
        register(new BadPacketsA());
        register(new BadPacketsB());
        register(new BadPacketsC());
        register(new VelocityA());
        register(new VelocityB());
    }

    public static boolean isCheck(String name) {
        return checkClasses.values().stream().anyMatch(val -> val.name().equalsIgnoreCase(name));
    }

    public static CheckInfo getCheckInfo(String name) {
        return checkClasses.values().stream().filter(val -> val.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static CheckSettings getCheckSettings(String name) {
        return checkSettings.values().stream().filter(val -> val.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}