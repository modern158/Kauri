package dev.brighten.anticheat.check.impl.movement.speed;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.reflections.impl.MinecraftReflection;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInKeepAlivePacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInTransactionPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutVelocityPacket;
import cc.funkemunky.api.utils.*;
import cc.funkemunky.api.utils.world.BlockData;
import dev.brighten.anticheat.check.api.Cancellable;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.utils.Helper;
import dev.brighten.anticheat.utils.MovementUtils;
import lombok.val;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

@Cancellable
@CheckInfo(name = "Speed (C)", description = "Minecraft calculated speed check",
        punishVL = 30, vlToFlag = 5, developer = true)
public class SpeedC extends Check {

    public double previousDistance;
    private int webTicks;
    private boolean onSoul, onWeb, sprint;
    private double velocityX, velocityZ;

    @Packet
    public void onOut(WrappedOutVelocityPacket packet) {
        if(packet.getId() != data.getPlayer().getEntityId()) return;

        data.runKeepaliveAction(ka -> {
            velocityX = packet.getX();
            velocityZ = packet.getZ();
        });
    }

    @Packet
    public void onFlying(WrappedInFlyingPacket packet, long timeStamp) {
        if (!packet.isPos()
                || data.playerInfo.serverPos
                || (data.playerInfo.deltaXZ == 0 && data.playerInfo.deltaY == 0))
            return;

        double drag = 0.91;

        checkProcessing: {

            List<String> tags = new ArrayList<>();

            double moveSpeed = data.predictionService.aiMoveSpeed;
            boolean onGround = data.playerInfo.lClientGround;

            double velocityXZ = MathUtils.hypot(velocityX, velocityZ);

            if (onGround) {
                tags.add("ground");
                drag *= data.blockInfo.fromFriction;
                moveSpeed *= 1.3;

                moveSpeed *= 0.16277136 / Math.pow(drag, 3);

                if (data.playerInfo.deltaY > 0
                        && data.playerInfo.deltaY < data.playerInfo.jumpHeight * 1.5) {
                    tags.add("ascend");
                    moveSpeed += 0.2;
                }
            } else {
                tags.add("air");
                moveSpeed = sprint && !data.blockInfo.inLiquid ? 0.026 : 0.02;
                drag = 0.91;
            }

            if (data.blockInfo.inWater) {
                tags.add("water");
                drag = 0.8f;

                val depth = MovementUtils.getDepthStriderLevel(data.getPlayer());
                if (depth > 0) {
                    drag = (0.54600006F - drag) * depth / 3.0F;
                    tags.add("depthstrider");
                    moveSpeed += (data.predictionService.aiMoveSpeed * 1.0F - moveSpeed) *  3.0F;
                }

                //TODO Make a fix for this that accounts for flowing water.
                //TODO Also check to see if this fix even works with a longer chain of flowing water.
                //moveSpeed+= 0.04;

                val optional = data.blockInfo.blocks.stream()
                        .filter(block -> Materials.checkFlag(block.getType(), Materials.WATER)
                                && BlockData.getData(block.getType()).getBox(block, data.playerVersion)
                                .isCollided(data.box))
                        .map(MinecraftReflection::getBlockFlow)
                        .filter(pos -> pos.a != 0 || pos.b != 0 || pos.c != 0)
                        .findFirst();

                if(optional.isPresent()) {
                    val flow = optional.get();

                    moveSpeed+= Math.hypot(flow.a, flow.c);
                    tags.add("water-flow");
                }
            }

            if (data.blockInfo.inLava) {
                tags.add("lava");
                drag = 0.5;
            }

            if(data.playerInfo.usingItem) {
                moveSpeed*= 0.2;
            }

            data.blockInfo.handler.setOffset(0);

            moveSpeed += velocityXZ * (data.playerInfo.lastVelocity.hasNotPassed(20) ? 2 : 1);

            if(onWeb && data.playerInfo.lastBrokenBlock.hasPassed(3)) {
                tags.add("web");
                moveSpeed*=.25;
            }

            if(onSoul && onGround) {
                tags.add("soulsand");
                moveSpeed*= 0.4;
            }

            double horizontalMove = (data.playerInfo.deltaXZ - previousDistance) - moveSpeed;
            if (data.playerInfo.deltaXZ > 0.1 && !data.playerInfo.generalCancel) {
                if (horizontalMove > 0 && data.playerInfo.lastVelocity.hasPassed(10)) {
                    vl++;
                    if(horizontalMove > 0.2 || vl > 2) {
                        flag("+%v,tags=%v",
                                MathUtils.round(horizontalMove, 5), String.join(",", tags));
                    }
                } else vl-= vl > 0 ? 0.05 : 0;
            }

            debug("+%v.4,tags=%v,place=%v,dy=%v.3,jumped=%v,ai=%v", horizontalMove, String.join(",", tags),
                    data.playerInfo.lastBlockPlace.getPassed(), data.playerInfo.deltaY, data.playerInfo.jumped,
                    data.predictionService.aiMoveSpeed);


            if(velocityXZ > 0) {
                velocityX*= (drag * (onGround ? data.blockInfo.fromFriction : 1));
                velocityZ*= (drag * (onGround ? data.blockInfo.fromFriction : 1));

                if(Math.abs(velocityX) < 0.005) velocityX = 0;
                if(Math.abs(velocityZ) < 0.005) velocityZ = 0;
            }

        }
        onWeb = data.blockInfo.inWeb;
        onSoul = data.blockInfo.onSoulSand;
        sprint = data.playerInfo.sprinting;
        this.previousDistance = data.playerInfo.deltaXZ * drag;
    }
}
