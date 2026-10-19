package com.example.laylines.network.packet.locatorStick;

import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.StickMode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.clientdata.ClientLaylineEdge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.event.network.CustomPayloadEvent;

import javax.annotation.Nullable;

/**
 * Server-to-client packet sent when the player uses the Layline Stick.
 * Delivers structured ClientLaylineNode and ClientLaylineEdge data to the
 * client.
 */
public class ClientboundStickReportPacket {

    @Nullable
    private final LaylineNode closestNode;
    private final double nodeDistance;
    @Nullable
    private final ClientLaylineEdge closestEdge;
    private final double edgeDistance;
    private final StickMode mode;

    public ClientboundStickReportPacket(@Nullable LaylineNode closestNode, double nodeDistance,
            @Nullable ClientLaylineEdge closestEdge, double edgeDistance,
            StickMode mode) {
        this.closestNode = closestNode;
        this.nodeDistance = nodeDistance;
        this.closestEdge = closestEdge;
        this.edgeDistance = edgeDistance;
        this.mode = mode;
    }

    public ClientboundStickReportPacket(FriendlyByteBuf buffer) {
        this.mode = StickMode.valueOf(buffer.readUtf());
        this.nodeDistance = buffer.readDouble();
        boolean hasNode = buffer.readBoolean();
        this.closestNode = hasNode ? LaylineNode.decode(buffer) : null;

        this.edgeDistance = buffer.readDouble();
        boolean hasEdge = buffer.readBoolean();
        this.closestEdge = hasEdge ? ClientLaylineEdge.decode(buffer) : null;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(mode.toString());
        buffer.writeDouble(nodeDistance);
        if (closestNode != null) {
            buffer.writeBoolean(true);
            closestNode.encode(buffer);
        } else {
            buffer.writeBoolean(false);
        }

        buffer.writeDouble(edgeDistance);
        if (closestEdge != null) {
            buffer.writeBoolean(true);
            closestEdge.encode(buffer);
        } else {
            buffer.writeBoolean(false);
        }
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {

            // Build a single multiline chat component
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                StickMode stickMode = mode;
                switch (stickMode) {
                    case READ_NODE:
                        for (String line : formatNodeChatDump(closestNode)) {
                            mc.player.sendSystemMessage(Component.literal(line));
                        }
                        break;
                    case READ_EDGE:
                        MutableComponent message = Component.empty();
                        if (closestEdge != null) {
                            message.append(Component.translatable("laylines.stick.read.edge",
                                    MessageFormat.format("{0,number,#.##}", edgeDistance)));
                        } else {
                            message.append(Component.translatable("laylines.stick.read.edge.none"));
                        }
                        mc.player.displayClientMessage(message, false);

                        break;
                    default:
                        // DEBUG_PLANT mode is server side only
                        return;
                }
            }
        });
        context.setPacketHandled(true);
    }

    /** One chat line per field so the dump stays readable in the history. */
    private static List<String> formatNodeChatDump(LaylineNode node) {
        BlockPos pos = node.getPosition();
        AttributeType generating = node.getGeneratingType();
        List<String> lines = new ArrayList<>();
        lines.add("=== Layline Node ===");
        // lines.add("UUID: " + node.getId());
        // lines.add("Pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        lines.add("Pools:");
        for (AttributeType type : AttributeType.values()) {
            boolean active = type == generating;
            String line = String.format(Locale.ROOT, "%s%s: %.1f / %.1f%s",
                    active ? "* " : "  ",
                    type.name(),
                    node.getCurrentValue(type),
                    node.getCapacity(type),
                    active ? "  [generating]" : "");
            lines.add(line);
        }
        return lines;
    }

    @Nullable
    public LaylineNode getClosestNode() {
        return closestNode;
    }

    public double getNodeDistance() {
        return nodeDistance;
    }

    @Nullable
    public ClientLaylineEdge getClosestEdge() {
        return closestEdge;
    }

    public double getEdgeDistance() {
        return edgeDistance;
    }

    public StickMode getMode() {
        return mode;
    }
}
