package com.example.laylines.datagen;

import com.example.laylines.LaylinesMod;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, LaylinesMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Generates the JSON for a standard flat item using "item/generated" as the
        // parent
        // and assigns its texture layer 0 to
        // assets/laylines/textures/item/example_item.png
        basicItem(LaylinesMod.EXAMPLE_ITEM.get());
    }
}
