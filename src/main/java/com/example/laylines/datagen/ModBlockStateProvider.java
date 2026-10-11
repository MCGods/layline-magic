package com.example.laylines.datagen;

import com.example.laylines.LaylinesMod;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        // Pass your PackOutput, MODID, and ExistingFileHelper to the super constructor
        super(output, LaylinesMod.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 1. Generates the blockstate JSON and a standard 6-sided cube model JSON
        // Using .get() because EXAMPLE_BLOCK is a RegistryObject
        simpleBlock(LaylinesMod.EXAMPLE_BLOCK.get());

        // 2. Generates the corresponding item model JSON so it looks right in your
        // inventory
        // This tells the item model to use the block model we just generated as its
        // parent
        simpleBlockItem(LaylinesMod.EXAMPLE_BLOCK.get(), cubeAll(LaylinesMod.EXAMPLE_BLOCK.get()));
    }
}
