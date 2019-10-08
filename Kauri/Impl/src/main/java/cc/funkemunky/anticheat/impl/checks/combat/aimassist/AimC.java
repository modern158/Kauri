package cc.funkemunky.anticheat.impl.checks.combat.aimassist;

import cc.funkemunky.anticheat.api.checks.AlertTier;
import cc.funkemunky.anticheat.api.checks.Check;
import cc.funkemunky.anticheat.api.checks.CheckInfo;
import cc.funkemunky.anticheat.api.checks.CheckType;
import cc.funkemunky.anticheat.api.utils.MiscUtils;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.anticheat.api.utils.Setting;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import cc.funkemunky.api.utils.MathUtils;
import lombok.val;
import org.bukkit.event.Event;

@Packets(packets = {Packet.Client.POSITION_LOOK, Packet.Client.LOOK})
@cc.funkemunky.api.utils.Init
@CheckInfo(name = "Aim (Type C)",
        description = "Looks for suspicious yaw and pitch movements. Not recommended for banning.",
        type = CheckType.AIM)
public class AimC extends Check {

    private int vl;

    @Setting(name = "threshold.verbose.max")
    private static int vlMax = 40;

    @Setting(name = "threshold.verbose.subtract")
    private static int vlSub = 2;

    @Setting(name = "threshold.yawAccel")
    private static double yawAccelMax = 1E-3;

    @Setting(name = "threshold.pitchAccel")
    private static double pitchAccelMax = 1E-4;

    @Setting(name = "threshold.minYawDelta")
    private static double minYawDelta = 0.6;

    @Setting(name = "combatOnly")
    private static boolean combatOnly = true;

    @Override
    public void onPacket(Object packet, String packetType, long timeStamp) {
        val move = getData().getMovementProcessor();
        val yawDelta = move.getYawDelta();
        val pitchDelta = move.getPitchDelta();
        val yawAccel = MathUtils.getDelta(yawDelta, move.getLastYawDelta());
        val pitchAccel = MathUtils.getDelta(pitchDelta, move.getLastPitchDelta());

        if (!MiscUtils.canDoCombat(combatOnly, getData())) return;


        if (yawDelta > minYawDelta
                && getData().getPlayer().getVehicle() == null
                && Math.abs(move.getTo().getPitch()) < 80
                && (pitchAccel < pitchAccelMax || yawAccel < yawAccelMax)) {
            if (vl++ > vlMax * 1.5) {
                flag("YAW: " + MathUtils.round(yawAccel, 7)
                        + " PITCH: " + MathUtils.round(pitchAccel, 7),
                        true, true, AlertTier.HIGH);
            } else if(vl > vlMax) flag("YAW: " + MathUtils.round(yawAccel, 7)
                    + " PITCH: " + MathUtils.round(pitchAccel, 7),
                    true, true, AlertTier.LIKELY);
        } else vl -= vl > 0 ? vlSub : 0;

        debug("VL: " + vl + " YAW: " + yawAccel + " PITCH: " + pitchAccel + " YAWD: " + yawDelta);
    }

    @Override
    public void onBukkitEvent(Event event) {

    }
}