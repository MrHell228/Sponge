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
package org.spongepowered.common.event.tracking.context.transaction.effect;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.context.transaction.pipeline.BlockPipeline;
import org.spongepowered.common.event.tracking.context.transaction.pipeline.PipelineCursor;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.SpongeBlockChangeFlag;

public final class AddBlockLootDropsEffect implements ProcessingSideEffect {

    private static final class Holder {
        static final AddBlockLootDropsEffect INSTANCE = new AddBlockLootDropsEffect();
    }

    public static AddBlockLootDropsEffect getInstance() {
        return Holder.INSTANCE;
    }

    AddBlockLootDropsEffect() {}

    @Override
    public EffectResult processSideEffect(
        final BlockPipeline pipeline, final PipelineCursor oldState, final BlockState newState, final SpongeBlockChangeFlag flag,
        final int limit
    ) {
        final PhaseContext<@NonNull ?> phaseContext = PhaseTracker.getInstance().getPhaseContext();

        final ServerWorld world = pipeline.getServerWorld();
        @Nullable final TileEntity existingTile = oldState.tileEntity;
        final BlockPos pos = oldState.pos;

        final LootContext.Builder lootBuilder = (new LootContext.Builder(world))
            .withRandom(world.random)
            .withParameter(LootParameters.ORIGIN, VecHelper.toVanillaVector3d(pos))
            .withParameter(LootParameters.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootParameters.BLOCK_ENTITY, existingTile);

        phaseContext.populateLootContext(lootBuilder);

        return new EffectResult(newState, oldState.state.getDrops(lootBuilder), false);
    }
}
