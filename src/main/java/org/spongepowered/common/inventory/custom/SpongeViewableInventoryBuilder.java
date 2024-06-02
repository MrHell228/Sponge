/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.inventory.custom;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.apache.commons.lang3.Validate;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Carrier;
import org.spongepowered.api.item.inventory.ContainerType;
import org.spongepowered.api.item.inventory.ContainerTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.type.ViewableInventory;
import org.spongepowered.common.inventory.lens.Lens;
import org.spongepowered.common.inventory.lens.LensCreator;
import org.spongepowered.common.inventory.lens.impl.DefaultIndexedLens;
import org.spongepowered.common.inventory.lens.impl.LensRegistrar;
import org.spongepowered.common.inventory.lens.impl.slot.SlotLensProvider;
import org.spongepowered.common.inventory.lens.slots.SlotLens;
import org.spongepowered.plugin.PluginContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class SpongeViewableInventoryBuilder implements ViewableInventory.Builder, ViewableInventory.Builder.DummyStep, ViewableInventory.Builder.EndStep {
    private ContainerType type;

    private Map<Integer, Slot> slotDefinitions;
    private Slot lastSlot;

    private Function<Player, Inventory> personalInventorySupplier;

    private PluginContainer plugin;
    private Carrier carrier;
    private UUID identity;

    private List<Inventory> finalInventories;
    private Lens finalLens;
    private SlotLensProvider finalProvider;
    private ContainerTypeInfo info;

    @Override
    public BuildingStep type(ContainerType type) {
        Validate.isTrue(SpongeViewableInventoryBuilder.containerTypeInfo().containsKey(type), "Container Type cannot be used for this: " + type);
        this.type = type;
        this.slotDefinitions = new HashMap<>();
        this.info = SpongeViewableInventoryBuilder.containerTypeInfo().get(type);
        return this;
    }

    // Helpers
    private Slot newDummySlot() {
        Container dummyInv = new net.minecraft.world.SimpleContainer(1);
        return ((Inventory) dummyInv).slot(0).get();
    }

    // Slot definition Impl:
    public BuildingStep slotsAtIndizes(List<Slot> source, List<Integer> at) {
        Validate.isTrue(source.size() == at.size(), "Source and index list sizes differ");
        for (int i = 0; i < at.size(); i++) {
            Slot slot = source.get(i);
            Integer index = at.get(i);
            this.slotDefinitions.put(index, slot);
            this.lastSlot = slot;
        }
        return this;
    }

    // complex redirects - (source/index list generation)
    public DummyStep fillDummy() {
        Slot slot = this.newDummySlot();
        List<Integer> indizes = IntStream.range(0, this.info.size).boxed().filter(idx -> !this.slotDefinitions.containsKey(idx)).collect(Collectors.toList());
        List<Slot> source = Stream.generate(() -> slot).limit(indizes.size()).collect(Collectors.toList());
        this.slotsAtIndizes(source, indizes);
        return this;
    }

    public DummyStep dummySlots(List<Integer> at) {
        Slot slot = this.newDummySlot();
        List<Slot> source = Stream.generate(() -> slot).limit(at.size()).collect(Collectors.toList());
        this.slotsAtIndizes(source, at);
        return this;
    }

    public DummyStep dummySlots(int count, int offset) {
        Slot slot = this.newDummySlot();
        List<Slot> source = Stream.generate(() -> slot).limit(count).collect(Collectors.toList());
        this.slots(source, offset);
        return this;
    }

    public BuildingStep slots(List<Slot> source, int offset) {
        List<Integer> indizes = IntStream.range(offset, offset + source.size()).boxed().collect(Collectors.toList());
        return this.slotsAtIndizes(source, indizes);
    }

    @Override
    public BuildingStep replacePersonal(Function<Player, Inventory> personalInventorySupplier) {
        this.personalInventorySupplier = personalInventorySupplier;
        return this;
    }

    // dummy
    @Override
    public BuildingStep item(ItemStackSnapshot item) {
        this.lastSlot.set(item.createStack());
        return this;
    }

    @Override
    public EndStep plugin(final PluginContainer plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        return this;
    }

    public EndStep identity(UUID uuid) {
        this.identity = uuid;
        return this;
    }
    @Override
    public EndStep carrier(Carrier carrier) {
        this.carrier = carrier;
        return this;
    }
    // Build

    @Override
    public EndStep completeStructure() {
        List<Integer> indizes = IntStream.range(0, this.info.size).boxed().filter(idx -> !this.slotDefinitions.containsKey(idx)).collect(Collectors.toList());
        List<Slot> source = ((Inventory) new net.minecraft.world.SimpleContainer(indizes.size())).slots();
        this.slotsAtIndizes(source, indizes);

//            this.finalInventories = this.slotDefinitions.values().stream().map(Inventory::parent).distinct().collect(Collectors.toList());
// TODO custom slot provider with reduces inventories
//            CustomSlotProvider slotProvider = new CustomSlotProvider();
//            for (Map.Entry<Integer, Slot> entry : this.slotDefinitions.entrySet()) {
//                Slot slot = entry.getValue();
//                int idx = slot.get(Keys.SLOT_INDEX).get();
//                int offset = 0;
//                for (int i = 0; i < this.finalInventories.indexOf(slot.parent()); i++) {
//                    offset += this.finalInventories.get(i).freeCapacity();
//                }
//                slotProvider.add(new BasicSlotLens(idx + offset));
//            }
//            this.finalProvider = slotProvider;

        if (this.personalInventorySupplier == null) {
            this.personalInventorySupplier = Player::inventory;
        }

        this.finalInventories = this.slotDefinitions.values().stream().map(Inventory.class::cast).collect(Collectors.toList());

        this.finalProvider = new LensRegistrar.BasicSlotLensProvider(this.info.size);
        this.finalLens = this.info.lensCreator.createLens(this.finalProvider);
        return this;
    }

    @Override
    public ViewableInventory.Custom build() {
        if (this.plugin == null) {
            throw new IllegalStateException("Plugin has not been set on this builder!");
        }

        final ViewableCustomInventory inventory = new ViewableCustomInventory(this.plugin, this.type, this.info,
                this.info.size, this.finalLens, this.finalProvider, this.personalInventorySupplier, this.finalInventories, this.identity, this.carrier);

        return ((ViewableInventory.Custom) inventory);
    }

    public ViewableInventory.Builder reset() {
        this.plugin = null;
        this.type = null;
        this.info = null;

        this.slotDefinitions = null;
        this.lastSlot = null;
        this.personalInventorySupplier = null;

        this.carrier = null;
        this.identity = null;

        this.finalInventories = null;
        this.finalLens = null;
        this.finalProvider = null;
        return this;
    }

    public static class CustomSlotProvider implements SlotLensProvider {

        private List<SlotLens> lenses = new ArrayList<>();

        public void add(SlotLens toAdd) {
            this.lenses.add(toAdd);
        }

        @Override
        public SlotLens getSlotLens(int index) {
            return this.lenses.get(index);
        }
    }

    private static Map<ContainerType, ContainerTypeInfo> containerTypeInfo;

    public static void register(final Supplier<ContainerType> ref, final ContainerTypeInfo info) {
        register(ref.get(), info);
    }
    public static void register(final ContainerType type, final ContainerTypeInfo info) {
        SpongeViewableInventoryBuilder.containerTypeInfo.put(type, info);
    }


    public static Map<ContainerType, ContainerTypeInfo> containerTypeInfo() {
        if (SpongeViewableInventoryBuilder.containerTypeInfo != null) {
            return SpongeViewableInventoryBuilder.containerTypeInfo;
        }

        ServerLevel level = (ServerLevel) Sponge.server().worldManager().worlds().iterator().next();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "custom-view-player");
        net.minecraft.world.entity.player.Player player = new net.minecraft.world.entity.player.Player(level, BlockPos.ZERO, 0.0F, profile) {
            public boolean isSpectator() {
                return false;
            }

            public boolean isCreative() {
                return false;
            }
        };
        net.minecraft.world.entity.player.Inventory playerInv = player.getInventory();

        SpongeViewableInventoryBuilder.containerTypeInfo = new HashMap<>();

        ContainerTypes.registry().stream().forEach(type -> {
            AbstractContainerMenu menu = ((MenuType<?>) type).create(0, playerInv);

            int size = (int) menu.slots.stream().filter(slot -> slot.container != playerInv).count();

            ContainerTypeInfo info = ContainerTypeInfo.of(size);

            int selfSize = 0, playerSize = 0, index = 0;
            for (var slot : menu.slots) {
                if (slot.container == playerInv) {
                    info.useSelfContainer.add(Pair.of(false, slot.getContainerSlot()));
                    ++playerSize;
                } else {
                    info.useSelfContainer.add(Pair.of(true, index++));
                    ++selfSize;
                }
            }

            info.selfContainerSize = selfSize;
            info.playerContainerSize = playerSize;
            info.totalSize = selfSize + playerSize;

            register(type, info);
        });

        return SpongeViewableInventoryBuilder.containerTypeInfo;
    }

    public static class ContainerTypeInfo {
        public final LensCreator lensCreator;

        public final int size;

        public int totalSize;
        public int selfContainerSize;
        public int playerContainerSize;

        public List<Pair<Boolean, Integer>> useSelfContainer = new ArrayList<>();

        public ContainerTypeInfo(LensCreator lensCreator, int size) {
            this.lensCreator = lensCreator;
            this.size = size;
        }

        public static ContainerTypeInfo of(LensCreator lensCreator, int size) {
            return new ContainerTypeInfo(lensCreator, size);
        }

        public static ContainerTypeInfo of(int size) {
            return new ContainerTypeInfo(sp -> new DefaultIndexedLens(0, size, sp),  size);
        }
    }

}
