package cc.funkemunky.anticheat.impl.checks.combat.autoclicker;

import cc.funkemunky.anticheat.Kauri;
import cc.funkemunky.anticheat.api.checks.*;
import cc.funkemunky.anticheat.api.utils.MiscUtils;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.anticheat.api.utils.Setting;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import org.bukkit.event.Event;

@Packets(packets = {Packet.Client.ARM_ANIMATION})
@cc.funkemunky.api.utils.Init
@CheckInfo(name = "Autoclicker (Type A)",
        description = "Calculates how many clicks per second is received to the server.",
        type = CheckType.AUTOCLICKER, cancelType = CancelType.INTERACT, executable = true, maxVL = 12)
public class AutoclickerA extends Check {

    @Setting(name = "maxCPS")
    private static int maxCPS = 18;

    @Setting(name = "banCPS")
    private static int banCPS = 30;

    private int ticks;
    private long lastTimeStamp;
    private double vl;

    @Override
    public void onPacket(Object packet, String packetType, long timeStamp) {
        if(MiscUtils.shouldReturnArmAnimation(getData())
                || getData().isLagging()
                || Kauri.getInstance().getTps() < 15) {
            ticks = 0;
            return;
        }
        if(System.currentTimeMillis() - lastTimeStamp >= 1000L) {
            if(ticks > banCPS) {
                if(vl++ > 2) {
                    flag("cps: " + ticks, true, true, AlertTier.CERTAIN);
                } else flag("cps: " + ticks, true, true, AlertTier.HIGH);
            } else if(ticks > maxCPS) {
                flag("cps: " + ticks, true, false, AlertTier.LIKELY);
            }
            debug("cps=" + ticks);
            ticks = 0;
            lastTimeStamp = System.currentTimeMillis();
        } else ticks++;
    }

    @Override
    public void onBukkitEvent(Event event) {

    }


}