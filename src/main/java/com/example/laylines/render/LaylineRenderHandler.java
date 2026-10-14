package com.example.laylines.render;

import com.example.laylines.LaylinesMod;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.network.packet.ClientboundLaylineSyncPacket;
import com.example.laylines.network.packet.LaylineParticleCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LaylineRenderHandler {

    private static final float SCROLL_SPEED = 0.02F;
    private static final float COLOR_PULSE_SPEED = 0.03F;
    private static final float MAX_HEIGHT = 10.0F;
    private static final float SEGMENT_COUNT = 64.0F;
    private static final float WAVE_FREQUENCY = 6.0F;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.level == null || mc.player == null) {
                return;
            }

            float gameTime = mc.level.getGameTime() + event.getPartialTick();

            ItemStack helmetItem = mc.player.getInventory().getArmor(3);
            if (helmetItem.isEmpty() || !helmetItem.is(Items.GOLDEN_HELMET)) {
                LaylineParticleCache.clear();
                return;
            }

            var edges = LaylineParticleCache.getLocalEdges();
            if (edges.isEmpty()) {
                return;
            }

            for (ClientboundLaylineSyncPacket.EdgeData edge : edges) {
                renderLayline(event, edge.nodeA(), edge.attributeType(), edge.nodeB(), gameTime);
            }

            RenderSystem.enableDepthTest();
        }
    }

    private static void renderLayline(RenderLevelStageEvent event, BlockPos start, AttributeType type, BlockPos end,
            float gameTime) {
        Vec3 startPos = new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        Vec3 endPos = new Vec3(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5);

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lineStrip());

        Matrix4f poseMatrix = poseStack.last().pose();
        Vec3 normal = endPos.subtract(startPos).normalize();

        // Control wave clustering density (Wavelength frequency over space)

        for (int i = 0; i <= SEGMENT_COUNT; i++) {
            float t = (float) i / SEGMENT_COUNT;

            double x = startPos.x + (endPos.x - startPos.x) * t;
            double y = startPos.y + (endPos.y - startPos.y) * t;
            double z = startPos.z + (endPos.z - startPos.z) * t;

            double waveScroll = (t * Math.PI * 4) - (gameTime * SCROLL_SPEED);
            double sineOffset = Math.sin(waveScroll) * MAX_HEIGHT * Math.sin(t * Math.PI);
            y += sineOffset;

            double pulse = Math.cos((gameTime * COLOR_PULSE_SPEED) - (t * WAVE_FREQUENCY));
            float standardIntensity = (float) ((pulse + 1.0) / 2.0);

            float sharpPulseIntensity = (float) Math.pow(standardIntensity, 8);

            float r = getRed(type);
            float g = getGreen(type);
            float b = getBlue(type);

            vertexConsumer.vertex(poseMatrix, (float) x, (float) y + 10, (float) z)
                    .color(r, g, b, sharpPulseIntensity)
                    .normal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z)
                    .endVertex();
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lineStrip());
    }

    private static float getRed(AttributeType type) {
        return switch (type) {
            case FIRE -> 1.0f;
            case WATER, EARTH -> 0.0f;
            case AIR, LIGHT -> 1.0f;
            case DARK -> 0.5f;
        };
    }

    private static float getGreen(AttributeType type) {
        return switch (type) {
            case FIRE, WATER, DARK -> 0.0f;
            case EARTH, AIR, LIGHT -> 1.0f;
        };
    }

    private static float getBlue(AttributeType type) {
        return switch (type) {
            case FIRE, EARTH -> 0.0f;
            case WATER, LIGHT -> 1.0f;
            case AIR, DARK -> 0.5f;
        };
    }
}
