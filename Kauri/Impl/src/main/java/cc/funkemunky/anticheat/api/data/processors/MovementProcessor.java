package cc.funkemunky.anticheat.api.data.processors;

import cc.funkemunky.anticheat.api.data.PlayerData;
import cc.funkemunky.anticheat.api.utils.MiscUtils;
import cc.funkemunky.anticheat.api.utils.*;
import cc.funkemunky.anticheat.impl.config.MiscSettings;
import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.utils.MathUtils;
import cc.funkemunky.api.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class MovementProcessor {
    private boolean cancelFlight, inLiquid, liquidBelow, clientOnGround, serverOnGround,
            fullyInAir, inAir, hasJumped, nearLiquid, blocksOnTop, pistonsNear, onHalfBlock,
            onClimbable, onIce, collidesHorizontally, tookVelocity, inWeb, onSlime, onSlimeBefore,
            onSoulSand, isRiptiding, halfBlocksAround, isNearGround, isInsideBlock, blocksNear, blocksAround;
    private int airTicks, groundTicks, iceTicks, climbTicks, halfBlockTicks, soulSandTicks, blockAboveTicks,
            optifineTicks, liquidTicks, webTicks, yawZeroTicks, pitchZeroTicks;
    private float cinematicYaw, baseSpeed, cinematicPitch, deltaY, lastDeltaXZ, slimeHeight,
            fallDistance, yawDelta, pitchDelta, lastYawDelta, lastPitchDelta, lastDeltaY,
            deltaXZ, deltaX, lastDeltaX, deltaZ, lastDeltaZ, lastServerYVelocity, serverYAcceleration,
            clientYAcceleration, lastClientYAcceleration, lastServerYAcceleration;
    private CustomLocation from, to;
    @Setter
    private float serverYVelocity;
    @Setter
    private boolean serverPos;
    private PastLocation pastLocation = new PastLocation();
    private TickTimer
            lastRiptide = new TickTimer(6),
            lastVehicle = new TickTimer(4),
            lastFlightToggle = new TickTimer(10);
    private List<BoundingBox> boxes = new ArrayList<>();
    private List<Entity> entitiesAround = new CopyOnWriteArrayList<>();
    private long lastTimeStamp, lagTicks, pitchGCD, lagTime, yawGCD, lastPitchGCD, lastYawGCD, offset = 16777216L;
    private MCSmooth mouseFilterX = new MCSmooth(), mouseFilterY = new MCSmooth();

    private static List<Float> sensitivities = Arrays.asList(.50f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f);

    public void update(PlayerData data, WrappedInFlyingPacket packet) {
        val player = packet.getPlayer();

        if(player == null) return;
        val timeStamp = System.currentTimeMillis();
        boolean chunkLoaded = Atlas.getInstance().getBlockBoxManager()
                .getBlockBox()
                .isChunkLoaded(player.getLocation());
        if (from == null || to == null) {
            from = new CustomLocation(0, 0, 0, 0, 0);
            to = new CustomLocation(0, 0, 0, 0, 0);
        }

        from = to.clone();
        clientOnGround = packet.isGround();

        if (packet.isPos()) {
            to.setX(packet.getX());
            to.setY(packet.getY());
            to.setZ(packet.getZ());

            data.setBoundingBox(new BoundingBox(to.toVector(), to.toVector())
                    .grow(0.3f, 0, 0.3f)
                    .add(0,0,0,0,1.84f,0));

            if (chunkLoaded) {
                //Here we get the colliding boundingboxes surrounding the player.
                List<BoundingBox> box = boxes = Atlas.getInstance().getBlockBoxManager()
                        .getBlockBox()
                        .getCollidingBoxes(player.getWorld(), data.getBoundingBox().grow(1f, 1f, 1f));

                CollisionAssessment assessment = new CollisionAssessment(data.getBoundingBox(), data);

                //There are some entities that are collide-able like boats but are not considered blocks.

                if(Atlas.getInstance().getCurrentTicks() % 2 == 0) {
                    entitiesAround.clear();
                    for (Entity entity : player.getNearbyEntities(1, 1, 1)) {
                        if(!(entity instanceof Vehicle
                                && !entity.getType().toString().contains("MINECART")
                                && !entity.getType().toString().contains("SHULKER"))) continue;

                        entitiesAround.add(entity);
                    }
                }

                for (Entity entity : entitiesAround) {
                    assessment.assessBox(
                            ReflectionsUtil
                                    .toBoundingBox(
                                            ReflectionsUtil.getBoundingBox(entity)),
                            player.getWorld(),
                            true);
                }

                //Now we scrub through the colliding boxes for any important information
                // that could be fed into detections.
                box.parallelStream().forEach(bb -> assessment.assessBox(bb, player.getWorld(), false));


                serverOnGround = assessment.isOnGround();
                blocksOnTop = assessment.isBlocksOnTop();
                collidesHorizontally = assessment.isCollidesHorizontally();
                nearLiquid = assessment.isNearLiquid();
                inLiquid = assessment.isInLiquid();
                liquidBelow = assessment.isLiquidBelow();
                onHalfBlock = assessment.isOnHalfBlock();
                onIce = assessment.isOnIce();
                pistonsNear = assessment.isPistonsNear();
                inWeb = assessment.isInWeb();
                onClimbable = assessment.isOnClimbable();
                fullyInAir = assessment.isFullyInAir();
                onSoulSand = assessment.isOnSoulSand();
                blocksAround = assessment.isBlocksAround();
                blocksNear = assessment.isBlocksNear();
                halfBlocksAround = assessment.isHalfBlocksAround();
                isNearGround = assessment.isNearGround();
                onSlime = assessment.isOnSlime();

                if (serverOnGround) {
                    groundTicks++;
                    airTicks = 0;

                    onSlimeBefore = onSlime;
                } else {
                    airTicks++;
                    groundTicks = 0;
                }

                if(onSlime) {
                    slimeHeight = fallDistance * 0.75f;
                }
            }
            baseSpeed = MiscUtils.getBaseSpeed(data);
            tookVelocity = timeStamp - data.getVelocityProcessor().getLastVelocityTimestamp() < 1500L;

            lastClientYAcceleration = clientYAcceleration;
            clientYAcceleration = deltaY - lastDeltaY;

            //Hear we use the client's ground packet being sent since whatever motion the client says it has
            //will line up with this since ground is sent along with positional packets (flying, poslook, pos, look)
            if (hasJumped) {
                hasJumped = false;
                inAir = true;
            } else if (clientOnGround) {
                inAir = false;
            } else if (!inAir) {
                hasJumped = true;
                //bF(data);
            }

            if(serverOnGround) {
                fallDistance = 0;
            } else fallDistance = Math.max(0, fallDistance - deltaY);


            if(timeStamp - lastTimeStamp >= 100) {
                lagTicks = MathUtils.millisToTicks(timeStamp - lastTimeStamp - 50);
                data.setLastPacketDrop(timeStamp);
                lagTime = timeStamp - lastTimeStamp - 50;
            } else lagTicks-= lagTicks > 0 ? 1 : 0;

            if(lagTicks > 0) {
                data.getLastPacketSkip().reset();
            } else lagTime = 0;
            val block = BlockUtils.getBlock(to.toLocation(data.getPlayer().getWorld()));
            val blockAbove = BlockUtils.getBlock(to.toLocation(data.getPlayer().getWorld()).clone()
                    .add(0, 1, 0));
            val blockBelow = BlockUtils.getBlock(to.toLocation(data.getPlayer().getWorld()).clone()
                    .subtract(0, 1,0));

            isInsideBlock = block == null
                    || blockAbove == null
                    || BlockUtils.isSolid(block)
                    || BlockUtils.isSolid(blockAbove);

            data.setBlockAbove(blockAbove);
            data.setBlockBelow(blockBelow);
            data.setBlockInside(block);

            if (isRiptiding = Atlas.getInstance().getBlockBoxManager().getBlockBox().isRiptiding(packet.getPlayer()))
                lastRiptide.reset();

            lastServerYVelocity = serverYVelocity;

            if (hasJumped) {
                serverYVelocity = deltaY;
            } else if (inAir) {
                serverYVelocity = (serverYVelocity - 0.08f) * 0.98f;

                if(Math.abs(serverYVelocity) < 0.005) {
                    serverYVelocity = 0;
                }

                if(MathUtils.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()) == 0 & serverYVelocity == 0) {
                    serverYVelocity = (serverYVelocity - 0.08f) * 0.98f;
                }
            } else {
                serverYVelocity = 0;
            }

            if(onClimbable
                    || inLiquid
                    || inWeb
                    || (serverPos && serverOnGround)
                    || lastVehicle.hasNotPassed(2 + MathUtils.floor(data.getTransPing() / 50D))
                    || getLastFlightToggle().hasNotPassed(3)
                    || timeStamp -
                    data.getVelocityProcessor().getLastVelocityTimestamp() <= 100L + data.getTransPing()) {
                serverYVelocity = deltaY;
            }

            lastServerYAcceleration = serverYAcceleration;
            serverYAcceleration = serverYVelocity - lastServerYVelocity;

            //The MiscUtils#getDistanceToGround method is kind of heavy, so we only run it 4 times a second instead of 20.
            //We compensate for the loss of data by using the yDelta of the player to guess the distance.
            //This method should and won't be used for anything sensitive requiring precise data.
            //This is just used for preventing any false positives

            lastTimeStamp = timeStamp;



            iceTicks = onIce ? Math.min(40, iceTicks + 2) : Math.max(0, iceTicks - 1);
            climbTicks = onClimbable ? Math.min(40, climbTicks + 1) : Math.max(0, climbTicks - 1);
            halfBlockTicks = onHalfBlock ? Math.min(40, halfBlockTicks + 2) : Math.max(0, halfBlockTicks - 1);
            blockAboveTicks = blocksOnTop ? Math.min(40, blockAboveTicks + 2) : Math.max(0, blockAboveTicks - 1);
            liquidTicks = nearLiquid ? Math.min(50, liquidTicks + 1) : Math.max(0, liquidTicks - 1);
            soulSandTicks = onSoulSand ? Math.min(40, soulSandTicks + 1) : Math.max(0, soulSandTicks - 1);
            webTicks = inWeb ? Math.min(30, webTicks + 1) : Math.max(webTicks, webTicks - 1);

            serverPos = timeStamp - data.getLastServerPosStamp() < 55L;
        } else if(!packet.isLook() && data.isLoggedIn()) {
            data.getLastLogin().reset();
        }

        if(data.isLoggedIn()) data.setLoggedIn(false);

        lastDeltaY = deltaY;
        lastDeltaXZ = deltaXZ;
        lastDeltaX = deltaX;
        lastDeltaZ = deltaZ;
        deltaY = (float) (to.getY() - from.getY());
        deltaX = (float) (to.getX() - from.getX());
        deltaZ = (float) (to.getZ() - from.getZ());
        deltaXZ = (float) (MiscUtils.hypot(deltaX, deltaZ));

        boolean hasLevi = packet.getPlayer().getActivePotionEffects()
                .stream()
                .anyMatch(effect -> effect.getType().getName().toLowerCase().contains("levi"));
        
        cancelFlight = player.getAllowFlight()
                || serverPos
                || getLastVehicle().hasNotPassed(10)
                || getLastFlightToggle().hasNotPassed(10)
                || getLiquidTicks() > 0
                || getWebTicks() > 0
                || isTookVelocity()
                || data.getLastLogin().hasNotPassed(50)
                || getClimbTicks() > 0
                || data.getLastBlockPlace().hasNotPassed(15)
                || hasLevi
                || (timeStamp - data.getLastRespawn()) < 50 + data.getTransPing() * 2
                || (isServerOnGround() && isOnHalfBlock())
                || isRiptiding()
                || getHalfBlockTicks() > 0
                || isBlocksOnTop()
                || isOnSlimeBefore()
                || getLastRiptide().hasNotPassed(8)
                || isPistonsNear()
                || data.getVelocityProcessor().getLastVelocity()
                .hasNotPassed(10 + MiscUtils.millisToTicks(data.getPing()));

        if (player.getVehicle() != null || PlayerUtils.isGliding(player)) lastVehicle.reset();

        if (packet.isLook()) {
            to.setYaw(packet.getYaw());
            to.setPitch(packet.getPitch());

            //Algorithm stripped from the MC client which calculates the deceleration of rotation
            //when using cinematic/optifine zoom.
            //Used to separate a legitimate aimbot-like rotation from a cheat.
            lastYawDelta = yawDelta;
            lastPitchDelta = pitchDelta;
            float yawDelta
                    = this.yawDelta
                    = cc.funkemunky.anticheat.api.utils.MathUtils
                    .getDistanceBetweenAngles(
                            to.getYaw(),
                            from.getYaw()),
                    pitchDelta
                            = this.pitchDelta
                            = MathUtils.getDelta(to.getPitch(), from.getPitch());
            if(data.isLoggedIn()) data.setLoggedIn(false);

            cinematicYaw =  findClosestCinematicYaw(data, to.getYaw(), from.getYaw());
            cinematicPitch = findClosestCinematicPitch(data, to.getPitch(), from.getPitch());

            if (Float.isNaN(cinematicPitch) || Float.isNaN(cinematicYaw)) {
                data.getYawSmooth().reset();
                data.getPitchSmooth().reset();
            }

            data.setCinematicMode(MathUtils.getDelta(cinematicPitch, to.getPitch()) < 0.4 && pitchDelta > 0.01);
            lastYawGCD = yawGCD;
            yawGCD = MiscUtils.gcd((long) (yawDelta * offset), (long) (lastYawDelta * offset));
            lastPitchGCD = pitchGCD;
            pitchGCD = MiscUtils.gcd((long) (pitchDelta * offset), (long) (lastPitchDelta * offset));

            if (data.isCinematicMode()) {
                optifineTicks += optifineTicks < 60 ? 1 : 0;
            } else if (optifineTicks > 0) {
                optifineTicks -= 3;
            }
        } else {
            data.getPitchSmooth().reset();
            data.getYawSmooth().reset();
        }

        if (to.getYaw() == from.getYaw()) {
            yawZeroTicks = Math.min(20, yawZeroTicks + 1);
        } else yawZeroTicks -= yawZeroTicks > 0 ? 1 : 0;

        if (to.getPitch() == from.getPitch()) {
            pitchZeroTicks = Math.min(20, pitchZeroTicks + 1);
        } else pitchZeroTicks -= pitchZeroTicks > 0 ? 1 : 0;

        pastLocation.addLocation(new CustomLocation(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch()));
        data.setGeneralCancel(serverPos || ((timeStamp - data.getLastRespawn()) < 150 + data.getTransPing() * 2) || data.isLagging() || lastVehicle.hasNotPassed(10) || getLastFlightToggle().hasNotPassed(15) || !chunkLoaded || packet.getPlayer().getAllowFlight() || hasLevi || packet.getPlayer().getGameMode().toString().contains("CREATIVE") || packet.getPlayer().getGameMode().toString().contains("SPEC") || lastVehicle.hasNotPassed() || getLastRiptide().hasNotPassed(10) || data.getLastLogin().hasNotPassed(50));
    }

    private float findClosestCinematicYaw(PlayerData data, float yaw, float lastYaw) {
        float value = sensitivities.stream().min(Comparator.comparing(val -> {
            float f = val * 0.6f + .2f;
            float f1 = (f * f * f) * 8f;
            float smooth = mouseFilterX.smooth(lastYaw, 0.05f * f1);
            mouseFilterX.reset();
            return cc.funkemunky.anticheat.api.utils.MathUtils.getDistanceBetweenAngles(yaw, smooth);
        }, Comparator.naturalOrder())).orElse(1f);

        float f = value * 0.6f + .2f;
        float f1 = (f * f * f) * 8f;
        return data.getYawSmooth().smooth(lastYaw, 0.05f * f1);
    }

    private float findClosestCinematicPitch(PlayerData data, float pitch, float lastPitch) {
        float value = sensitivities.stream().min(Comparator.comparing(val -> {
            float f = val * 0.6f + .2f;
            float f1 = (f * f * f) * 8f;
            float smooth = mouseFilterY.smooth(lastPitch, 0.05f * f1);
            mouseFilterY.reset();
            return MathUtils.getDelta(pitch, smooth);
        }, Comparator.naturalOrder())).orElse(1f);

        float f = value * 0.6f + .2f;
        float f1 = (f * f * f) * 8f;
        return data.getPitchSmooth().smooth(lastPitch, 0.05f * f1);
    }
}
