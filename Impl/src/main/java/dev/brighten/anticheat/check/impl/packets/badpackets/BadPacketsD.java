package dev.brighten.anticheat.check.impl.packets.badpackets;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInAbilitiesPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutAbilitiesPacket;
import cc.funkemunky.api.utils.RunUtils;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "BadPackets (D)",
        description = "Checks for clients spoofing flight permissions.",
        checkType = CheckType.BADPACKETS, punishVL = 10)
public class BadPacketsD extends Check {

    boolean serverAllowed, clientAllowed;

    @Packet
    public void server(WrappedOutAbilitiesPacket packet) {
        if(packet.isAllowedFlight()) {
            serverAllowed = true;
        } else if(!clientAllowed) {
            serverAllowed = false;
        }
    }

    @Packet
    public void client(WrappedInAbilitiesPacket packet) {
        if(packet.isAllowedFlight()) {
            clientAllowed = true;
        } else if(!serverAllowed) {
            clientAllowed = false;
        }
    }

    @Packet
    public void flying(WrappedInFlyingPacket packet, long timeStamp) {
        if(timeStamp - data.creation < 1000L) {
            serverAllowed = data.getPlayer().getAllowFlight();
            clientAllowed = data.getPlayer().getAllowFlight();
            RunUtils.task(() -> data.getPlayer().setAllowFlight(false), Kauri.INSTANCE);
        } else {
            if(!serverAllowed && clientAllowed) {
                if(vl++ > 1) {
                    flag("server=" + serverAllowed + " client=" + clientAllowed);
                }
            } else vl = 0;
        }
    }
}