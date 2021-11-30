package dev.brighten.anticheat.check.impl.premium.hitboxes;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInTransactionPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInUseEntityPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutEntityTeleportPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutRelativePosition;
import cc.funkemunky.api.utils.KLocation;
import cc.funkemunky.api.utils.MathUtils;
import cc.funkemunky.api.utils.world.EntityData;
import cc.funkemunky.api.utils.world.types.SimpleCollisionBox;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.anticheat.check.impl.premium.AimG;
import dev.brighten.anticheat.check.impl.premium.KillauraH;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.utils.AxisAlignedBB;
import dev.brighten.anticheat.utils.EntityLocation;
import dev.brighten.anticheat.utils.Vec3D;
import dev.brighten.anticheat.utils.timer.Timer;
import dev.brighten.anticheat.utils.timer.impl.MillisTimer;
import dev.brighten.anticheat.utils.timer.impl.PlayerTimer;
import dev.brighten.anticheat.utils.timer.impl.TickTimer;
import dev.brighten.api.KauriVersion;
import dev.brighten.api.check.CheckType;
import dev.brighten.api.check.DevStage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.*;

@CheckInfo(name = "Reach (B)", planVersion = KauriVersion.ARA, punishVL = 20, executable = true,
        checkType = CheckType.HITBOX)
public class ReachB extends Check {

    private final Map<UUID, EntityLocation> entityLocationMap = new HashMap<>(),
            secondEntityLocationMap = new HashMap<>();
    private Timer lastFlying;
    public int streak;
    private float buffer;
    private int hbuffer;
    public boolean sentTeleport;
    private boolean attacked, flying;
    private AimG aimDetection;
    private KillauraH killauraHDetection;

    public Timer lastAimOnTarget = new TickTimer();
    private Timer lastTransProblem = new MillisTimer(20);
    private List<KLocation> targetLocs = new ArrayList<>();
    private int addTicks;

    private final boolean debugBoxes = true;

    private static final EnumSet<EntityType> allowedEntityTypes = EnumSet.of(EntityType.ZOMBIE, EntityType.SHEEP,
            EntityType.BLAZE, EntityType.SKELETON, EntityType.PLAYER, EntityType.VILLAGER, EntityType.IRON_GOLEM,
            EntityType.WITCH, EntityType.COW, EntityType.CREEPER);

    @Override
    public void setData(ObjectData data) {
        lastFlying = new PlayerTimer(data);
        super.setData(data);
    }

    @Packet
    public void onUse(WrappedInUseEntityPacket packet) {
        if(data.target == null || !allowedEntityTypes.contains(data.target.getType())
                || packet.getAction() != WrappedInUseEntityPacket.EnumEntityUseAction.ATTACK)
            return;

        //Updating new entity loc
        EntityLocation eloc = entityLocationMap.get(data.target.getUniqueId());

        if(eloc == null) {
            debug("eloc is null");
            return;
        }

        final KLocation eyeLoc = data.playerInfo.to.clone();

        eyeLoc.y+= data.playerInfo.sneaking ? 1.54f : 1.62f;

        if(eloc.x == 0 && eloc.y == 0 & eloc.z == 0) return;

        SimpleCollisionBox targetBox = (SimpleCollisionBox) EntityData
                .getEntityBox(new Vector(eloc.x, eloc.y, eloc.z), data.target);

        if(data.playerVersion.isBelow(ProtocolVersion.V1_9)) {
            targetBox = targetBox.expand(0.1, 0.1, 0.1);
        }

        final AxisAlignedBB vanillaBox = new AxisAlignedBB(targetBox);

        Vec3D intersect = vanillaBox.rayTrace(eyeLoc.toVector(), MathUtils.getDirection(eyeLoc), 10);

        double distance = Double.MAX_VALUE;
        boolean collided = false; //Using this to compare smaller numbers than Double.MAX_VALUE. Slightly faster

        if(intersect != null) {
            lastAimOnTarget.reset();
            distance = intersect.distanceSquared(new Vec3D(eyeLoc.x, eyeLoc.y, eyeLoc.z));
            collided = true;
        }

        if(collided) {
            hbuffer = 0;
            distance = Math.sqrt(distance);
            if(distance > 3.02 && lastTransProblem.isPassed(52) && streak > 3 && eloc.sentTeleport
                    && lastFlying.isNotPassed(1)) {
                if(++buffer > 2) {
                    vl++;
                    flag("d=%.4f", distance);
                    buffer = 2;
                }
            } else if(buffer > 0) buffer-= 0.1f;
            debug("dist=%.2f b=%s s=%s st=%s lf=%s ld=%s lti=%s",
                    distance, buffer, streak, sentTeleport, lastFlying.getPassed(),
                    data.lagInfo.lastPingDrop.getPassed(), lastTransProblem.getPassed());
        } else {
            if(++hbuffer > 5) {
                find(HitboxesB.class).vl++;
                find(HitboxesB.class).flag(120, "%.1f;%.1f;%.1f", eloc.x, eloc.y, eloc.z);
            }
            debug("didnt hit box: x=%.1f y=%.1f z=%.1f lti=%s", eloc.x, eloc.y, eloc.z,
                    lastTransProblem.getPassed());
        }
    }

    private AimG getAimDetection() {
        if(aimDetection == null) aimDetection = (AimG) data.checkManager.checks.get("Aim (G)");

        return aimDetection;
    }

    private KillauraH getKillauraDetection() {
        if(killauraHDetection == null) killauraHDetection = (KillauraH) data.checkManager.checks.get("Killaura (H)");

        return killauraHDetection;
    }

    @Packet
    public void onFlying(WrappedInFlyingPacket packet) {
        flying = true;
        if(lastFlying.isNotPassed(1)) streak++;
        else {
            streak = 1;
            sentTeleport = false;
        }

        for (Iterator<Map.Entry<UUID, EntityLocation>> it = entityLocationMap.entrySet().iterator();
             it.hasNext();) {
            Map.Entry<UUID, EntityLocation> entry = it.next();

            EntityLocation eloc = entry.getValue();

            if(eloc.entity == null) {
                it.remove();
                continue;
            }

            eloc.interpolateLocation();
        }

        lastFlying.reset();
    }

    @Packet
    public void onTrans(WrappedInTransactionPacket packet) {
        if(lastFlying.isPassed(1)
                && Kauri.INSTANCE.keepaliveProcessor.getKeepById(packet.getAction()).isPresent()) {

            for (Iterator<Map.Entry<UUID, EntityLocation>> it = entityLocationMap.entrySet().iterator();
                                                           it.hasNext();) {
                    Map.Entry<UUID, EntityLocation> entry = it.next();

                    EntityLocation eloc = entry.getValue();

                    if(eloc.entity == null) {
                        it.remove();
                        continue;
                    }

                    eloc.interpolateLocation();
                    if(data.target != null && eloc.entity.getUniqueId() == data.target.getUniqueId()) {
                    getAimDetection().setTargetLocation(new KLocation(eloc.x, eloc.y, eloc.z, eloc.yaw, eloc.pitch));
                    attacked = false;
                }
            }
        }
    }

    @Packet
    public void onEntity(WrappedOutRelativePosition packet) {
        Atlas.getInstance().getWorldInfo(data.getPlayer().getWorld()).getEntity(packet.getId())
                .ifPresent(entity -> {
                    if(!allowedEntityTypes.contains(entity.getType())) return;

                    data.runKeepaliveAction(ka -> {
                        EntityLocation eloc = secondEntityLocationMap.computeIfAbsent(entity.getUniqueId(),
                                key -> new EntityLocation(entity));

                        if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_9)) {
                            eloc.newX += (byte)packet.getX() / 32D;
                            eloc.newY += (byte)packet.getY() / 32D;
                            eloc.newZ += (byte)packet.getZ() / 32D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        } else if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_14)) {
                            eloc.newX += (int)packet.getX() / 4096D;
                            eloc.newY += (int)packet.getY() / 4096D;
                            eloc.newZ += (int)packet.getZ() / 4096D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        } else {
                            eloc.newX += (short)packet.getX() / 4096D;
                            eloc.newY += (short)packet.getY() / 4096D;
                            eloc.newZ += (short)packet.getZ() / 4096D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        }
                        eloc.increment = 3;
                        eloc.interpolateLocations();
                        if(data.target != null && entity.getEntityId() == data.target.getEntityId()) {
                            final double toShrink = data.playerVersion.isOrAbove(ProtocolVersion.V1_9) ? 0.12 : 0.02;
                            eloc.interpolatedLocations.stream()
                                    .map(kloc -> ((SimpleCollisionBox)EntityData.getEntityBox(kloc, entity))
                                            .shrink(toShrink, toShrink, toShrink))
                                    .forEach(box -> getKillauraDetection().getTargetLocations().add(box));

                            //Clearing to ensure garbage collections clears this object.
                            eloc.interpolatedLocations.clear();
                        }
                    }, -1);

                    EntityLocation eloc = entityLocationMap.computeIfAbsent(entity.getUniqueId(),
                            key -> new EntityLocation(entity));
                    runAction(entity, () -> {
                        //We don't need to do version checking here. Atlas handles this for us.
                        if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_9)) {
                            eloc.newX += (byte)packet.getX() / 32D;
                            eloc.newY += (byte)packet.getY() / 32D;
                            eloc.newZ += (byte)packet.getZ() / 32D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        } else if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_14)) {
                            eloc.newX += (int)packet.getX() / 4096D;
                            eloc.newY += (int)packet.getY() / 4096D;
                            eloc.newZ += (int)packet.getZ() / 4096D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        } else {
                            eloc.newX += (short)packet.getX() / 4096D;
                            eloc.newY += (short)packet.getY() / 4096D;
                            eloc.newZ += (short)packet.getZ() / 4096D;
                            eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                            eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                        }

                        eloc.increment = 3;
                    });
        });
    }

    @Packet
    public void onTeleportSent(WrappedOutEntityTeleportPacket packet) {
        Atlas.getInstance().getWorldInfo(data.getPlayer().getWorld()).getEntity(packet.entityId)
                .ifPresent(entity -> {
                    if(!allowedEntityTypes.contains(entity.getType())) return;
                    data.runKeepaliveAction(ka -> {
                        EntityLocation eloc = secondEntityLocationMap.computeIfAbsent(entity.getUniqueId(),
                                key -> new EntityLocation(entity));

                        if(data.playerVersion.isOrAbove(ProtocolVersion.V1_9)) {
                            if (!(Math.abs(eloc.x - packet.x) >= 0.03125D)
                                    && !(Math.abs(eloc.y - packet.y) >= 0.015625D)
                                    && !(Math.abs(eloc.z - packet.z) >= 0.03125D)) {
                                eloc.increment = 0;
                                //We don't need to do version checking here. Atlas handles this for us.
                                eloc.newX = eloc.x = packet.x;
                                eloc.newY = eloc.y = packet.y;
                                eloc.newZ = eloc.z = packet.z;
                                eloc.newYaw = eloc.yaw = packet.yaw;
                                eloc.newPitch = eloc.pitch = packet.pitch;
                            } else {
                                eloc.newX = packet.x;
                                eloc.newY = packet.y;
                                eloc.newZ = packet.z;
                                eloc.newYaw = packet.yaw;
                                eloc.newPitch = packet.pitch ;

                                eloc.increment = 3;
                                eloc.interpolateLocations();
                            }
                        } else {
                            //We don't need to do version checking here. Atlas handles this for us.
                            eloc.newX = packet.x;
                            eloc.newY = packet.y;
                            eloc.newZ = packet.z;
                            eloc.newYaw = packet.yaw;
                            eloc.newPitch = packet.pitch;

                            eloc.increment = 3;
                            eloc.interpolateLocations();
                        }
                        if(data.target != null && entity.getEntityId() == data.target.getEntityId()) {

                            final double toShrink = data.playerVersion.isOrAbove(ProtocolVersion.V1_9) ? 0.12 : 0.02;
                            if(eloc.interpolatedLocations.size() > 0) {
                                eloc.interpolatedLocations.stream()
                                        .map(kloc -> ((SimpleCollisionBox)EntityData.getEntityBox(kloc, entity))
                                                .shrink(toShrink, toShrink, toShrink))
                                        .forEach(box -> getKillauraDetection().getTargetLocations().add(box));

                            } else {
                                getKillauraDetection().getTargetLocations()
                                        .add((SimpleCollisionBox)EntityData
                                                .getEntityBox(new KLocation(eloc.x, eloc.y, eloc.z), entity));
                            }
                            //Clearing to ensure garbage collections clears this object.
                            eloc.interpolatedLocations.clear();
                        }
                    }, -1);
                    EntityLocation eloc = entityLocationMap.computeIfAbsent(entity.getUniqueId(),
                            key -> new EntityLocation(entity));
                    runAction(entity, () -> {
                        if(data.playerVersion.isOrAbove(ProtocolVersion.V1_9)) {
                            if (!(Math.abs(eloc.x - packet.x) >= 0.03125D)
                                    && !(Math.abs(eloc.y - packet.y) >= 0.015625D)
                                    && !(Math.abs(eloc.z - packet.z) >= 0.03125D)) {
                                eloc.increment = 0;
                                //We don't need to do version checking here. Atlas handles this for us.
                                eloc.newX = eloc.x = packet.x;
                                eloc.newY = eloc.y = packet.y;
                                eloc.newZ = eloc.z = packet.z;
                                eloc.newYaw = eloc.yaw = packet.yaw;
                                eloc.newPitch = eloc.pitch = packet.pitch;
                            } else {
                                eloc.newX = packet.x;
                                eloc.newY = packet.y;
                                eloc.newZ = packet.z;
                                eloc.newYaw = packet.yaw;
                                eloc.newPitch = packet.pitch ;

                                eloc.increment = 3;
                            }
                        } else {
                            //We don't need to do version checking here. Atlas handles this for us.
                            eloc.newX = packet.x;
                            eloc.newY = packet.y;
                            eloc.newZ = packet.z;
                            eloc.newYaw = packet.yaw;
                            eloc.newPitch = packet.pitch;

                            eloc.increment = 3;
                        }
                        sentTeleport = eloc.sentTeleport = true;
                    });
                });
    }

    private void runAction(Entity entity, Runnable action) {
        if(data.target != null && data.target.getUniqueId().equals(entity.getUniqueId())) {
            data.runInstantAction(ia -> {
                if(!ia.isEnd()) {
                    action.run();
                    flying = false;
                } else if(flying) {
                    lastTransProblem.reset();
                }
            });
        } else data.runKeepaliveAction(keepalive -> action.run());
    }

}