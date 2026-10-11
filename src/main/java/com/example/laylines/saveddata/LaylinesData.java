package com.example.laylines.saveddata;

import com.example.laylines.LaylinesMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Bus.FORGE)
public class LaylinesData extends SavedData {

    public final ArrayList<BlockPos> beaconPositions;

    public static SavedData.Factory<LaylinesData> factory() {
        return new SavedData.Factory<LaylinesData>(LaylinesData::new, LaylinesData::load, DataFixTypes.LEVEL);
    }

    // public LaylinesData LaylinesData() {
    // return this;
    // }

    public LaylinesData() {
        beaconPositions = new ArrayList<>();
    }

    public LaylinesData(ArrayList<BlockPos> list) {
        beaconPositions = list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, Provider p_334349_) {
        // save to tag
        int size = beaconPositions.size();
        tag.putInt("size", size);
        for (int i = 0; i < size; i++) {
            tag.put("beacon_pos_" + i, NbtUtils.writeBlockPos(beaconPositions.get(i)));
        }
        return tag;
    }

    public static LaylinesData load(CompoundTag tag, HolderLookup.Provider provider) {
        ArrayList<BlockPos> list = new ArrayList<>();
        // load from tag
        int size = tag.getInt("size");
        for (int i = 0; i < size; i++) {
            list.add(NbtUtils.readBlockPos((CompoundTag) tag.get("beacon_pos_" + i), null).get());
        }
        return new LaylinesData(list);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        int size = beaconPositions.size();
        for (int i = 0; i < size; i++) {
            builder.append("beacon_pos_");
            builder.append(i);
            builder.append(" ");
            builder.append(beaconPositions.get(i).toString());
            builder.append(System.getProperty("line.separator"));
        }
        return builder.toString();

    }

    @SubscribeEvent
    static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getEntity().getServer() != null) {

            var bob = event.getEntity().getServer().overworld().getDataStorage().computeIfAbsent(LaylinesData.factory(),
                    "laylines");

            bob.setDirty();
        }
    }

    public void update() {
        this.setDirty();
    }
}
