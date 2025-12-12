package com.denizenscript.depenizen.bukkit.properties.utilities;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.properties.Property;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Random;

public class BukkitPlayerProperties implements Property {

    // <--[property]
    // @object PlayerTag
    // @name enchant_seed
    // @plugin Depenizen
    // @description
    // Provides access to the player's internal Minecraft enchantment seed.
    //
    // This is the RNG seed used for deciding what enchantments show up
    // in the enchanting table. Vanilla only changes this on successful
    // enchants, but with this property you can:
    //
    // - Read the current seed: <player.enchant_seed>
    // - Set a specific seed: - adjust <player> enchant_seed:<int>
    // - Reroll to a new random seed: - adjust <player> reroll_enchant_seed
    //
    // Internally this uses reflection to locate the NMS int field whose
    // name contains "enchant", so it tolerates some field renames across
    // versions (1.20, 1.21, etc.).
    // -->

    private final PlayerTag player;

    public BukkitPlayerProperties(PlayerTag player) {
        this.player = player;
    }

    public static boolean describes(ObjectTag object) {
        return object instanceof PlayerTag;
    }

    public static BukkitPlayerProperties getFrom(ObjectTag object) {
        if (!describes(object)) {
            return null;
        }
        return new BukkitPlayerProperties((PlayerTag) object);
    }

    public static final String[] handledTags = new String[] {
            "enchant_seed"
    };

    public static final String[] handledMechs = new String[] {
            "enchant_seed", "reroll_enchant_seed"
    };

    @Override
    public String getPropertyId() {
        return "BukkitPlayer";
    }

    @Override
    public String getPropertyString() {
        // Not part of PlayerTag identity, no need to serialize.
        return null;
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {

        // <--[tag]
        // @attribute <PlayerTag.enchant_seed>
        // @returns ElementTag(Number)
        // @plugin Depenizen
        // @description
        // Returns the player's current internal enchantment seed as a number.
        //
        // This is the raw seed used by the enchanting table to generate
        // offered enchantments for this player.
        // -->
        if (attribute.startsWith("enchant_seed")) {
            int seed = getEnchantSeedInternal(player.getPlayerEntity());
            return new ElementTag(seed).getObjectAttribute(attribute.fulfill(1));
        }

        return null;
    }

    @Override
    public void adjust(Mechanism mechanism) {

        // <--[mechanism]
        // @object PlayerTag
        // @name enchant_seed
        // @input ElementTag(Number)
        // @plugin Depenizen
        // @description
        // Sets the player's internal enchantment seed to a specific integer.
        //
        // Usage:
        // - adjust <player> enchant_seed:123456
        //
        // This affects what enchantments are offered by the enchanting table
        // for this player on future enchants.
        // -->
        if (mechanism.matches("enchant_seed") && mechanism.requireInteger()) {
            int seed = mechanism.getValue().asInt();
            setEnchantSeedInternal(player.getPlayerEntity(), seed);
        }

        // <--[mechanism]
        // @object PlayerTag
        // @name reroll_enchant_seed
        // @input (none)
        // @plugin Depenizen
        // @description
        // Rerolls the player's internal enchantment seed to a new random value.
        //
        // Usage:
        // - adjust <player> reroll_enchant_seed
        //
        // Common use-case: treat a "fizzled" enchant (custom failure) as if an
        // enchantment happened from the RNG's perspective, so the next time
        // they enchant, the table offers are different.
        // -->
        if (mechanism.matches("reroll_enchant_seed")) {
            int newSeed = new Random().nextInt();
            setEnchantSeedInternal(player.getPlayerEntity(), newSeed);
        }
    }

    // #########################
    // #  INTERNAL NMS ACCESS  #
    // #########################

    private static volatile Field enchantSeedField;
    private static volatile boolean triedFindingField = false;

    private static Field findEnchantSeedField(Object handle) {
        if (handle == null) {
            return null;
        }
        if (enchantSeedField != null || triedFindingField) {
            return enchantSeedField;
        }
        triedFindingField = true;

        Class<?> current = handle.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                // We want: instance (non-static) int field with "enchant" in the name
                int mods = field.getModifiers();
                if (field.getType() == int.class
                        && !Modifier.isStatic(mods)
                        && field.getName().toLowerCase(Locale.ROOT).contains("enchant")) {

                    field.setAccessible(true);
                    enchantSeedField = field;
                    Debug.log("Detected enchant seed field on class "
                            + current.getName() + " as '" + field.getName() + "'.");

                    return enchantSeedField;
                }
            }
            current = current.getSuperclass();
        }

        Debug.echoError("Failed to automatically locate enchantment seed field on NMS player handle. "
                + "No non-static int field containing 'enchant' in its name was found.");
        return null;
    }

    private static Object getNmsHandle(Player bukkitPlayer) throws Exception {
        // CraftPlayer#getHandle() via reflection
        Method getHandle = bukkitPlayer.getClass().getMethod("getHandle");
        return getHandle.invoke(bukkitPlayer);
    }

    private static int getEnchantSeedInternal(Player bukkitPlayer) {
        if (bukkitPlayer == null) {
            return 0;
        }
        try {
            Object handle = getNmsHandle(bukkitPlayer);
            Field field = findEnchantSeedField(handle);
            if (field == null) {
                return 0;
            }
            return field.getInt(handle);
        }
        catch (Throwable ex) {
            Debug.echoError("Failed to read enchantment seed from NMS handle:");
            Debug.echoError(ex);
            return 0;
        }
    }

    private static void setEnchantSeedInternal(Player bukkitPlayer, int seed) {
        if (bukkitPlayer == null) {
            return;
        }
        try {
            Object handle = getNmsHandle(bukkitPlayer);
            Field field = findEnchantSeedField(handle);
            if (field == null) {
                return;
            }
            field.setInt(handle, seed);
        }
        catch (Throwable ex) {
            Debug.echoError("Failed to set enchantment seed on NMS handle:");
            Debug.echoError(ex);
        }
    }
}
