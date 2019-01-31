package cc.funkemunky.anticheat.impl.checks.combat.killaura;

import cc.funkemunky.anticheat.api.checks.CancelType;
import cc.funkemunky.anticheat.api.checks.Check;
import cc.funkemunky.anticheat.api.checks.CheckType;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.anticheat.api.utils.Setting;
import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInUseEntityPacket;
import cc.funkemunky.api.utils.BlockUtils;
import cc.funkemunky.api.utils.BoundingBox;
import cc.funkemunky.api.utils.MathUtils;
import cc.funkemunky.api.utils.MiscUtils;
import cc.funkemunky.api.utils.math.RayTrace;
import lombok.val;
import lombok.var;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import java.util.List;

@Packets(packets = {Packet.Client.USE_ENTITY})
public class KillauraG extends Check {
    public KillauraG(String name, CheckType type, CancelType cancelType, int maxVL, boolean enabled, boolean executable, boolean cancellable) {
        super(name, type, cancelType, maxVL, enabled, executable, cancellable);

        setDeveloper(true);
    }

    @Setting(name = "threshold")
    private int threshold = 2;

    private int vl = 0;

    @Override
    public Object onPacket(Object packet, String packetType, long timeStamp) {
        WrappedInUseEntityPacket use = new WrappedInUseEntityPacket(packet, getData().getPlayer());

        if(use.getEntity() instanceof LivingEntity) {

            val origin = getData().getPlayer().getLocation().clone().add(0, 1.53, 0);

            val distance = origin.distance(use.getEntity().getLocation());

            RayTrace trace = new RayTrace(origin.toVector(), origin.getDirection());

            List<Vector> vectors = trace.traverse(distance, 0.2);

            var amount = 0;

            var finalVec = vectors.get(0);

            for (Vector vec : vectors) {
                if (MiscUtils.getEntityBoundingBox((LivingEntity) use.getEntity()).grow(0.25f,0,0.25f).intersectsWithBox(vec)) {
                    finalVec = vec;
                    break;
                }
                if (!BlockUtils.getBlock(vec.toLocation(origin.getWorld())).getType().isSolid()) continue;

                List<BoundingBox> boxes = Atlas.getInstance().getBlockBoxManager().getBlockBox().getCollidingBoxes(origin.getWorld(), new BoundingBox(vec, vec).grow(0.1f, 0.1f, 0.1f));

                if (boxes.stream().anyMatch(box -> box.intersectsWithBox(vec))) {
                    amount++;
                }
            }

            if (amount > threshold) {
                flag(amount + ">-" + threshold, true, true);
            }

            debug("COLLIDED: " + amount + " AMOUNT: " + vectors.size() + " DISTANCE: " + MathUtils.round(distance, 4) + " CALC DISTANCE: " + origin.toVector().distance(finalVec));
        }
        return packet;
    }

    @Override
    public void onBukkitEvent(Event event) {

    }
}
