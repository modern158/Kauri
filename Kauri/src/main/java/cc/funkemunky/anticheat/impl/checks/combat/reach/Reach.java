package cc.funkemunky.anticheat.impl.checks.combat.reach;

import cc.funkemunky.anticheat.api.checks.*;
import cc.funkemunky.anticheat.api.utils.CustomLocation;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.anticheat.api.utils.RayTrace;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import cc.funkemunky.api.utils.BoundingBox;
import cc.funkemunky.api.utils.Color;
import cc.funkemunky.api.utils.Init;
import cc.funkemunky.api.utils.MiscUtils;
import lombok.val;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import java.util.stream.Collectors;

@CheckInfo(name = "Reach", description = "A very accurate and fast 3.1 reach check.", type = CheckType.REACH, cancelType = CancelType.COMBAT, maxVL = 40)
@Packets(packets = {Packet.Client.USE_ENTITY})
@Init
public class Reach extends Check {

    private float vl;
    private long lastAttack;

    @Override
    public void onPacket(Object packet, String packetType, long timeStamp) {
        if(!getData().getPlayer().getGameMode().equals(GameMode.CREATIVE) && getData().getTarget() != null && getData().getLastAttack().hasNotPassed(0) && getData().getLastLogin().hasPassed(5) && !getData().isServerPos() && getData().getTransPing() < 450) {
            val move = getData().getMovementProcessor();
            long range = 200 + Math.abs(getData().getTransPing() - getData().getLastTransPing()) * 3;
            val location = getData().getEntityPastLocation().getEstimatedLocation(getData().getTransPing(), range);
            val to = move.getTo().toLocation(getData().getPlayer().getWorld()).add(0, getData().getPlayer().getEyeHeight(), 0);
            val trace = new RayTrace(to.toVector(), to.getDirection());

            val calcDistance = getData().getTarget().getLocation().distance(to);

            if(timeStamp - lastAttack <= 5) {
                lastAttack = timeStamp;
                return;
            } else if(calcDistance > 40) {
                flag(calcDistance + ">-20", true, false, AlertTier.LIKELY);
                return;
            }

            val velocity = getData().getVelocityProcessor();

            val traverse = trace.traverse(0, calcDistance * 1.25f, 0.1, 0.02, 2.8, 4);
            val collided = traverse.stream().filter((vec) -> location.stream().anyMatch((loc) -> getHitbox(getData().getTarget(), loc).collides(vec))).collect(Collectors.toList());

            float distance = (float) collided.stream().mapToDouble((vec) -> vec.distance(to.toVector()))
                    .min().orElse(0.0D);

           /* val box2 = location.stream().map(loc -> getHitbox(getData().getTarget(), loc)).max(Comparator.comparingLong(box -> traverse.stream().filter(box::collides).count())).orElse(null);

            if(box2 != null) {
                MiscUtils.createParticlesForBoundingBox(getData().getPlayer(), box2, WrappedEnumParticle.CRIT);
            }
            traverse.parallelStream().forEach(vec -> {
                WrappedPacketPlayOutWorldParticle particle = new WrappedPacketPlayOutWorldParticle(WrappedEnumParticle.CRIT, true, (float) vec.getX(), (float) vec.getY(), (float) vec.getZ(), 0.0F, 0.0F, 0.0F, 0.0F, 1, (int[])null);
                particle.sendPacket(getData().getPlayer());
            });*/

            if(collided.size() > 0) {
                if (distance > 3 && (collided.size() > 34 || distance > 3.75) && collided.size() > 8 && getData().getMovementProcessor().getLagTicks() == 0) {
                    if (vl++ > 12) {
                        flag("reach=" + distance, true, true, AlertTier.CERTAIN);
                    } else if (vl > 4) {
                        flag("reach=" + distance, true, true, AlertTier.HIGH);
                    }
                } else if(distance > 2) {
                    vl = Math.max(0, vl - 0.025f);
                }

                debug((distance > 3 && collided.size() > 34 ? Color.Green : "") + "distance=" + distance + " collided=" + collided.size() + " vl=" + vl + " range=" + range);
            }
            lastAttack = timeStamp;
        }
    }

    @Override
    public void onBukkitEvent(Event event) {

    }

    private BoundingBox getHitbox(LivingEntity entity, CustomLocation l) {
        Vector dimensions = MiscUtils.entityDimensions.getOrDefault(entity.getType(), new Vector(0.35F, 1.85F, 0.35F));
        return (new BoundingBox(l.toVector(), l.toVector())).grow(0.15F, 0.15F, 0.15F).grow((float)dimensions.getX(), 0.0F, (float)dimensions.getZ()).add(0.0F, 0.0F, 0.0F, 0.0F, (float)dimensions.getY(), 0.0F);
    }
}
