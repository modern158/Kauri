package dev.brighten.anticheat.check.impl.combat.hitbox;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.utils.BoundingBox;
import cc.funkemunky.api.utils.MiscUtils;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.CheckType;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.utils.KLocation;
import dev.brighten.anticheat.utils.RayCollision;
import dev.brighten.anticheat.utils.RayTrace;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@CheckInfo(name = "Hitboxes", description = "Ensures the player is looking at the target when attacking.",
        checkType = CheckType.HITBOX, punishVL = 20)
public class Hitboxes extends Check {

    private static List<EntityType> allowedEntities = Arrays.asList(
            EntityType.ZOMBIE,
            EntityType.VILLAGER,
            EntityType.PLAYER,
            EntityType.SKELETON,
            EntityType.PIG_ZOMBIE,
            EntityType.WITCH,
            EntityType.CREEPER,
            EntityType.ENDERMAN);

    private long lastTimeStamp;

    @Packet
    public void onFlying(WrappedInFlyingPacket packet, long timeStamp) {
        if (timeStamp - lastTimeStamp <= 4) {
            lastTimeStamp = timeStamp;
            return;
        }
        lastTimeStamp = timeStamp;

        if (checkParameters(data)) {

            List<Location> origins = data.pastLocation
                    .getEstimatedLocation(0,(data.lagInfo.transPing < 60 ? 150 : 100))
                    .stream()
                    .map(loc ->
                            loc.toLocation(data.getPlayer().getWorld()).clone()
                                    .add(0, data.getPlayer().getEyeHeight(), 0))
                    .collect(Collectors.toList());

            List<BoundingBox> entityLocations = data.targetPastLocation
                    .getEstimatedLocation(data.lagInfo.transPing, 150L)
                    .stream()
                    .map(loc -> getHitbox(data.target.getType(), loc))
                    .collect(Collectors.toList());

            long collided = origins.parallelStream().filter(loc -> {
                RayCollision ray = new RayCollision(loc.toVector(), loc.getDirection());

                for (BoundingBox box : entityLocations) {
                    if(RayCollision.intersect(ray, box)) {
                        return true;
                    }
                }
                return false;
            }).count();

            if(collided == 0) {
                if(data.lagInfo.lastPacketDrop.hasPassed(1)
                        && Kauri.INSTANCE.lastTickLag.hasPassed(4)
                        && vl++ > 7) {
                    flag("collided=" + 0);
                }
            } else vl-= vl > 0 ? 0.25 : 0;

            debug("collided=" + collided + " vl=" + vl);
        }
    }

    private static boolean checkParameters(ObjectData data) {
        return data.playerInfo.lastAttack.hasNotPassed(0)
                && data.target != null
                && Kauri.INSTANCE.lastTickLag.hasPassed(10)
                && allowedEntities.contains(data.target.getType())
                && !data.playerInfo.inCreative
                && data.playerInfo.lastTargetSwitch.hasPassed()
                && !data.getPlayer().getGameMode().equals(GameMode.CREATIVE);
    }

    private static BoundingBox getHitbox(EntityType type, KLocation loc) {
        Vector bounds = MiscUtils.entityDimensions.get(type);
        return new BoundingBox(loc.toVector(), loc.toVector())
                .grow((float)bounds.getX(), 0, (float)bounds.getZ())
                .add(0,0,0,0,(float)bounds.getY(),0)
                .grow(0.14f,0.1f,0.14f);
    }
}
