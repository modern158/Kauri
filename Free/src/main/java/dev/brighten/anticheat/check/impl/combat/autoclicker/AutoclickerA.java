package dev.brighten.anticheat.check.impl.combat.autoclicker;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInArmAnimationPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import dev.brighten.anticheat.check.api.*;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "Autoclicker (A)", description = "A fast click check.", checkType = CheckType.AUTOCLICKER,
        punishVL = 2)
@Cancellable(cancelType = CancelType.INTERACT)
public class AutoclickerA extends Check {

    private int flyingTicks, cps;

    @Setting(name = "cpsToFlag")
    private static int cpsToFlag = 22;

    @Setting(name = "cpsToBan")
    private static int cpsToBan = 28;

    @Packet
    public void onFlying(WrappedInFlyingPacket packet, long timeStamp) {
        flyingTicks++;
        if(flyingTicks >= 20) {
            if(cps > cpsToFlag) {
                if(cps > cpsToBan) vl++;
                flag("cps=%v", cps);
            }
            debug("cps=%v", cps);

            flyingTicks = cps = 0;
        }
    }

    @Packet
    public void onArmAnimation(WrappedInArmAnimationPacket packet) {
        if(!data.playerInfo.breakingBlock
                && data.playerInfo.lastBrokenBlock.hasPassed(5)
                && data.playerInfo.lastBlockPlace.hasPassed(2))
            cps++;
        debug("breaking=%v lastBroken=%v", data.playerInfo.breakingBlock,
                data.playerInfo.lastBrokenBlock.getPassed());
    }
}