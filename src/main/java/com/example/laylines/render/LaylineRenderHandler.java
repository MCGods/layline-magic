package com.example.laylines.render;

import com.example.laylines.LaylinesMod;
import com.example.laylines.MainConfig;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.clientdata.ClientLaylineEdge;
import com.example.laylines.network.packet.laylineParticles.LaylineParticleCache;
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

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LaylineRenderHandler {

    private static final float SCROLL_SPEED = 0.02F;
    private static final float COLOR_PULSE_SPEED = 0.03F;
    private static final float MAX_HEIGHT = 10.0F;
    private static final float WAVE_FREQUENCY = 6.0F;

    // Multi-line rendering constants
    private static final float THRESHOLD_FOR_LINES = 30.0f;
    private static final int LINES_PER_ATTRIBUTE = 3;
    private static final float BASE_ALPHA = 0.15f;
    private static final float MAX_ALPHA = 1.0f;
    private static final float LINE_HEIGHT_OFFSET = 1.0f;
    private static final float LINE_ANGLE_OFFSET = 20f;
    private static final double MAX_RENDER_DISTANCE = 512.0; // Cull edges beyond this distance

    /**
     * Parameters for rendering a single layline strand.
     */
    private static record LineStrand(
            AttributeType attributeType,
            int lineIndex,
            float alpha,
            float heightOffset,
            float phaseOffset) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.level == null || mc.player == null) {
                return;
            }

            float gameTime = mc.level.getGameTime() + event.getPartialTick();
            float segmentCount = MainConfig.getLaylineRenderQuality();

            ItemStack helmetItem = mc.player.getInventory().getArmor(3);
            if (helmetItem.isEmpty() || !helmetItem.is(Items.GOLDEN_HELMET)) {
                LaylineParticleCache.clear();
                return;
            }

            var edges = LaylineParticleCache.getLocalEdges();
            if (edges.isEmpty()) {
                return;
            }

            Vec3 playerPos = mc.player.position();

            for (ClientLaylineEdge edge : edges) {
                // Distance culling: skip edges far from player
                Vec3 edgeStart = new Vec3(edge.nodeA().getPosition().getX(), edge.nodeA().getPosition().getY(),
                        edge.nodeA().getPosition().getZ());
                double dist = playerPos.distanceToSqr(edgeStart);
                if (dist > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                    continue;
                }

                renderLayline(event, edge, gameTime, segmentCount);
            }

            RenderSystem.enableDepthTest();
        }
    }

    private static void renderLayline(RenderLevelStageEvent event, ClientLaylineEdge edge, float gameTime,
            float segmentCount) {
        BlockPos start = edge.nodeA().getPosition();
        BlockPos end = edge.nodeB();

        Vec3 startPos = new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        Vec3 endPos = new Vec3(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5);

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        Matrix4f poseMatrix = poseStack.last().pose();
        Vec3 edgeDir = endPos.subtract(startPos).normalize();

        // Calculate perpendicular vector for angle offsets
        Vec3 perpDir = new Vec3(-edgeDir.z, 0, edgeDir.x).normalize();

        // Determine all strands to render for all attributes
        List<LineStrand> strands = calculateLineStrands(edge.nodeA());

        // Render each strand
        for (LineStrand strand : strands) {
            renderLineStrand(bufferSource, poseMatrix, poseStack, startPos, endPos, edgeDir, perpDir,
                    strand, gameTime, segmentCount);
        }

        poseStack.popPose();
    }

    /**
     * Determines which line strands should be rendered based on energy level for
     * all attributes.
     */
    private static List<LineStrand> calculateLineStrands(LaylineNode node) {
        List<LineStrand> strands = new ArrayList<>();

        // Calculate strands for each attribute
        for (AttributeType attrType : AttributeType.values()) {
            float energy = node.getCurrentValue(attrType);
            boolean isGenerating = node.isGenerating(attrType);

            // Calculate number of lines based on energy for this attribute
            int lineCount = (int) (energy / THRESHOLD_FOR_LINES);

            // For non-generating attributes, skip if below threshold (no line 0 fallback)
            if (!isGenerating && lineCount == 0) {
                continue;
            }

            // For generating attribute, if below threshold, show line 0 as fallback
            if (isGenerating && lineCount == 0) {
                lineCount = 1;
            }

            // Cap at maximum lines per attribute
            lineCount = Math.min(LINES_PER_ATTRIBUTE, lineCount);

            for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
                // Calculate alpha for this line
                float alpha;
                if (lineIndex == 0 && isGenerating && energy < THRESHOLD_FOR_LINES) {
                    // Line 0 fallback for generating attribute with low energy
                    alpha = Math.min(MAX_ALPHA, BASE_ALPHA + (energy / THRESHOLD_FOR_LINES) * (MAX_ALPHA - BASE_ALPHA));
                } else {
                    // Normal lines: fade in as energy crosses thresholds
                    float energyAboveThreshold = energy - lineIndex * THRESHOLD_FOR_LINES;
                    alpha = Math.min(MAX_ALPHA, Math.max(0, energyAboveThreshold / THRESHOLD_FOR_LINES));
                }

                // Skip if alpha is too low
                if (alpha < 0.01f) {
                    continue;
                }

                // Calculate unique offsets based on attribute type and line index
                // This ensures different attributes have different phases and heights
                int attrIndex = attrType.ordinal();
                float heightOffset = (attrIndex * LINES_PER_ATTRIBUTE + lineIndex) * LINE_HEIGHT_OFFSET;
                float phaseOffset = (attrIndex * LINES_PER_ATTRIBUTE + lineIndex) * 0.5f;

                strands.add(new LineStrand(attrType, lineIndex, alpha, heightOffset, phaseOffset));
            }
        }

        return strands;
    }

    /**
     * Renders a single line strand with the given parameters.
     */
    private static void renderLineStrand(MultiBufferSource.BufferSource bufferSource, Matrix4f poseMatrix,
            PoseStack poseStack,
            Vec3 startPos, Vec3 endPos, Vec3 edgeDir, Vec3 perpDir,
            LineStrand strand, float gameTime, float segmentCount) {
        AttributeType type = strand.attributeType();
        float r = getRed(type);
        float g = getGreen(type);
        float b = getBlue(type);

        // Get a fresh vertex consumer for this strand to prevent connection to previous
        // strands
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lineStrip());

        for (int i = 0; i <= segmentCount; i++) {
            float t = (float) i / segmentCount;

            double x = startPos.x + (endPos.x - startPos.x) * t;
            double y = startPos.y + (endPos.y - startPos.y) * t;
            double z = startPos.z + (endPos.z - startPos.z) * t;

            // Add height offset
            y += strand.heightOffset();

            // Add perpendicular angle offset
            double angleWave = Math.sin(t * Math.PI + strand.lineIndex());
            x += perpDir.x * LINE_ANGLE_OFFSET * angleWave;
            z += perpDir.z * LINE_ANGLE_OFFSET * angleWave;

            // Add phase offset to wave scroll
            double waveScroll = (t * Math.PI * 4) - (gameTime * SCROLL_SPEED) + strand.phaseOffset();
            double sineOffset = Math.sin(waveScroll) * MAX_HEIGHT * Math.sin(t * Math.PI);
            y += sineOffset;

            double pulse = Math.cos((gameTime * COLOR_PULSE_SPEED) - (t * WAVE_FREQUENCY) + strand.phaseOffset());
            float standardIntensity = (float) ((pulse + 1.0) / 2.0);
            float sharpPulseIntensity = (float) Math.pow(standardIntensity, 8);

            float alpha = sharpPulseIntensity * strand.alpha();

            // Skip vertex if opacity is 0 to avoid masking issues
            if (alpha < 0.001f) {
                continue;
            }

            vertexConsumer.vertex(poseMatrix, (float) x, (float) y + 10, (float) z)
                    .color(r, g, b, alpha)
                    .normal(poseStack.last(), (float) edgeDir.x, (float) edgeDir.y, (float) edgeDir.z)
                    .endVertex();
        }

        // End batch after this strand to prevent connection to next strand
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
