package cc.funkemunky.anticheat.impl.checks.movement;

import cc.funkemunky.anticheat.api.checks.CancelType;
import cc.funkemunky.anticheat.api.checks.Check;
import cc.funkemunky.anticheat.api.checks.CheckType;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.anticheat.api.utils.Verbose;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import org.bukkit.event.Event;


@Packets(packets = {Packet.Client.POSITION_LOOK, Packet.Client.POSITION, Packet.Client.LEGACY_POSITION_LOOK, Packet.Client.LEGACY_POSITION})
public class FlyA extends Check {

    private float lastMotionY = 0, lastAccelerationPacket = 0;
    private int verbose, verbose2;
    private Verbose verboseLow = new Verbose();
    private long lastTimeStamp;

    public FlyA(String name, CheckType type, CancelType cancelType, int maxVL) {
        super(name, type, cancelType, maxVL);

    }

    @Override
    public Object onPacket(Object packet, String packetType, long timeStamp) {
        if (getData().getLastServerPos().hasNotPassed(1) || getData().isGeneralCancel() || getData().getMovementProcessor().getTo().toVector().distance(getData().getMovementProcessor().getFrom().toVector()) < 0.005)
            return packet;
        float motionY = (float) (getData().getMovementProcessor().getTo().getY() - getData().getMovementProcessor().getFrom().getY()), acceleration = motionY - lastMotionY;

        /* This checks for the acceleration of the player being too low. The average acceleration for a legitimate player is around 0.08.
           We check if it's less than 1E-4 for some compensation of inconsistencies that happen very often due to netty.
         */

        if (timeStamp - lastTimeStamp > 1) {
            if (getData().getMovementProcessor().getAirTicks() > 2
                    && Math.abs(acceleration) < 1E-5
                    && Math.abs(lastAccelerationPacket) < 1E-5
                    && !getData().getMovementProcessor().isServerOnGround()
                    && getData().getMovementProcessor().getDistanceToGround() > 0.25
                    && getData().getMovementProcessor().getClimbTicks() == 0
                    && !getData().getMovementProcessor().isInLiquid()
                    && !getData().getMovementProcessor().isInWeb()) {
                if (verboseLow.flag(3, 1000)) {
                    flag("t: low; " + motionY + "≈" + lastMotionY, true, true);
                }
            }

            /* This is to check for large amounts of instant acceleration to counter any fly which tries bypass in this manner  */
            if (Math.abs(acceleration) > 0.1
                    && !getData().getMovementProcessor().isBlocksOnTop()
                    && !getData().getMovementProcessor().isOnHalfBlock()
                    && !getData().getMovementProcessor().isOnSlimeBefore()
                    && getData().getMovementProcessor().getLastRiptide().hasPassed()
                    && getData().getVelocityProcessor().getLastVelocity().hasPassed(20)
                    && getData().getMovementProcessor().getClimbTicks() == 0
                    && Math.abs(lastAccelerationPacket) > 0.1) {
                //We have to add a verbose since this check isn't 100% accurate and therefore can have issues.
                //However, we can instantly flag if they are already in the air since a large delta between velocities is impossible.
                if (verbose++ > 3) {
                    flag("t: high; " + motionY + ">-" + lastMotionY, true, false);
                }
            } else {
                verbose = 0;
            }
            debug(getData().getMovementProcessor().isServerOnGround() + ", " + getData().getMovementProcessor().isBlocksOnTop() + ", " + Math.abs(getData().getMovementProcessor().getServerYAcceleration()) + ", " + getData().getMovementProcessor().getServerYVelocity() + ", " + motionY + ", " + getData().getMovementProcessor().getDistanceToGround());
        }
        lastAccelerationPacket = acceleration;
        lastMotionY = motionY;
        lastTimeStamp = timeStamp;
        return packet;
    }

    @Override
    public void onBukkitEvent(Event event) {
    }
}