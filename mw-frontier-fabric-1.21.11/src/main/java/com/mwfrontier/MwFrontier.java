package com.mwfrontier;

import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;

/** MW Frontier - Module 1: space suit, oxygen, low gravity in The End (our "space" zone). */
public class MwFrontier implements ModInitializer {
    public static final String ID = "mw_frontier";
    private static final int CANISTER_REFILL = 150; // durability points (= seconds of air)

    public static Item SPACE_HELMET;
    public static Item OXYGEN_TANK;
    public static Item OXYGEN_CANISTER;

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }

    @Override
    public void onInitialize() {
        SPACE_HELMET = register("space_helmet", Item::new, new Item.Properties()
                .durability(300)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
        OXYGEN_TANK = register("oxygen_tank", Item::new, new Item.Properties()
                .durability(600)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build()));
        OXYGEN_CANISTER = register("oxygen_canister", Item::new, new Item.Properties().stacksTo(16));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(SPACE_HELMET);
            entries.accept(OXYGEN_TANK);
            entries.accept(OXYGEN_CANISTER);
        });

        // once per second
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
        });

        // right-click a canister to refill the worn tank
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(OXYGEN_CANISTER)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack tank = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!tank.is(OXYGEN_TANK)) {
                player.displayClientMessage(Component.literal("Wear an Oxygen Tank on your chest first."), true);
                return InteractionResult.FAIL;
            }
            if (tank.getDamageValue() == 0) return InteractionResult.FAIL;
            tank.setDamageValue(Math.max(0, tank.getDamageValue() - CANISTER_REFILL));
            held.shrink(1);
            player.displayClientMessage(Component.literal("Tank refilled."), true);
            return InteractionResult.SUCCESS;
        });
    }

    private static void tickPlayer(ServerPlayer p) {
        if (p.level().dimension() != Level.END) return; // vacuum zone
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack tank = p.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasAir = false;
        if (head.is(SPACE_HELMET) && tank.is(OXYGEN_TANK) && tank.getDamageValue() < tank.getMaxDamage() - 1) {
            tank.setDamageValue(tank.getDamageValue() + 1);
            hasAir = true;
            int pct = Math.round(100f * (1f - (float) tank.getDamageValue() / tank.getMaxDamage()));
            p.displayClientMessage(Component.literal("O2: " + pct + "%"), true);
        }
        if (!hasAir) {
            p.displayClientMessage(Component.literal("NO OXYGEN"), true);
            p.hurtServer((ServerLevel) p.level(), p.damageSources().drown(), 2.0f);
        }
        // low gravity: closest vanilla-friendly approximation
        p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 3, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
    }
}
