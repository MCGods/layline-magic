package com.example.laylines.network.packet.locatorStick;

import com.example.laylines.api.StickMode;
import com.example.laylines.event.LaylineStickListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

public class ServerboundStickModeRequestPacket {
    private final StickMode mode;

    public ServerboundStickModeRequestPacket(StickMode mode) {
        this.mode = mode;
    }

    public ServerboundStickModeRequestPacket(FriendlyByteBuf buffer) {
        this.mode = StickMode.valueOf(buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(mode.name());
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender != null) {
                LaylineStickListener.handleModeRequest(sender, mode);
            }
        });
        context.setPacketHandled(true);
    }
}
