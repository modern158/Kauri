package dev.brighten.anticheat.check.impl.combat.reach;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInArmAnimationPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.utils.BoundingBox;
import cc.funkemunky.api.utils.KLocation;
import cc.funkemunky.api.utils.MathUtils;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.anticheat.utils.RayCollision;
import dev.brighten.api.check.CheckType;
import lombok.val;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@CheckInfo(name = "Reach", description = "Ensures the reach of a player is legitimate.",
        checkType = CheckType.HITBOX, punishVL = 10, executable = false)
public class Reach extends Check {

    private static List<EntityType> allowedEntities = Arrays.asList(EntityType.PLAYER, EntityType.SKELETON,
            EntityType.ZOMBIE, EntityType.PIG_ZOMBIE, EntityType.VILLAGER);

    private float verbose;

    @Packet
    public void onArm(WrappedInArmAnimationPacket packet) {
        vl-= vl > 0 ? 0.005 : 0;
    }

    @Packet
    public void onUse(WrappedInFlyingPacket packet, long timeStamp) {
        //debug("timeStamp=" + timeStamp + "ms");
        if(data.target == null || data.targetData == null || timeStamp - data.playerInfo.lastAttackTimeStamp > 55) return;

        val origins = data.pastLocation.getPreviousRange(30L)
                .stream()
                .map(loc -> {
                    debug("%1ms", timeStamp - loc.timeStamp);
                    return loc.toLocation(data.getPlayer().getWorld()).add(0, data.playerInfo.sneaking ? 1.54f : 1.62f, 0);
                })
                .collect(Collectors.toList());

        val entityLoc = data.targetData.pastLocation
                .getEstimatedLocation(data.lagInfo.transPing, Math.max(220L, Math.round(data.lagInfo.transPing / 2D)));

        List<Double> distances = new ArrayList<>();

        origins.forEach(origin -> {
            RayCollision collision = new RayCollision(origin.toVector(), origin.getDirection());
            entityLoc.forEach(loc -> {
                Vector point = collision
                        .collisionPoint(getHitbox(loc));

                if(point != null) {
                    distances.add(point.distance(origin.toVector()));
                }
            });
        });


        if(distances.size() > 0) {
            val distance = distances.stream().mapToDouble(num -> num).min().orElse(0);

            if(distance > 3.01 && (distances.size() > 4 || (distance > 3.1 && distances.size() > 3))
                    && (data.playerInfo.deltaXZ == 0 && data.targetData.playerInfo.deltaXZ == 0
                    || data.targetData.playerInfo.deltaXZ > 0 && data.playerInfo.deltaXZ > 0)
                    && data.lagInfo.lastPacketDrop.hasPassed(1)) {
                verbose+= distances.size() > 6 ? 1 : 0.5f;
                if(verbose > 3) {
                    vl++;
                    flag("distance=%1 size=%2", MathUtils.round(distance, 3), distances.size());
                }
            } else verbose-= verbose > 0 ? data.lagInfo.lagging ? 0.025f : 0.02f : 0;
            debug("distance=" + distance + ", size=" + distances.size() + ", vl=" + verbose);
        }
    }

    private static BoundingBox getHitbox(KLocation loc) {
        return new BoundingBox(loc.toVector(), loc.toVector()).grow(0.4f, 0.1f, 0.4f)
                .add(0,0,0,0,1.8f,0);
    }
}