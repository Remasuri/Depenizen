package com.denizenscript.depenizen.bukkit.objects.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.flags.AbstractFlagTracker;
import com.denizenscript.denizencore.flags.FlaggableObject;
import com.denizenscript.denizencore.flags.RedirectionFlagTracker;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Location;
import org.bukkit.World;

@SuppressWarnings("unused")
public class WorldCoordTag implements ObjectTag, FlaggableObject {

    // <--[ObjectType]
    // @name WorldCoordTag
    // @prefix worldcoord
    // @base ElementTag
    // @implements FlaggableObject
    // @format
    // The identity format for worldcoords is <world>,<x>,<z>
    // For example, 'worldcoord@world;12;34'.
    //
    // @plugin Depenizen, Towny
    // @description
    // A WorldCoordTag represents a Towny grid coordinate, regardless of whether it is claimed.
    //
    // This object type is flaggable.
    // Flags on this object type will be stored in the server saves file,
    // under "__depenizen_towny_worldcoords.<world>,<x>,<z>"
    // -->

    ////////////////
    //  OBJECT FETCHER
    ////////////////

    @Fetchable("worldcoord")
    public static WorldCoordTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        String lower = CoreUtilities.toLowerCase(string);
        if (lower.startsWith("worldcoord@")) {
            string = string.substring("worldcoord@".length());
        }

        String[] parts = null;

        // Try list format first (world|x|z)
        try {
            ListTag list = ListTag.valueOf(string, context);
            if (list.size() == 3) {
                parts = new String[] { list.get(0), list.get(1), list.get(2) };
            }
        }
        catch (Exception ignored) {
            // fall through
        }

        // Fallback: split by ',' if present (matches identify())
        if (parts == null && string.contains(",")) {
            String[] split = string.split(",", 3);
            if (split.length == 3) {
                parts = split;
            }
        }

        if (parts == null) {
            return null;
        }

        String worldName = parts[0];
        int x;
        int z;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        }
        catch (NumberFormatException ex) {
            return null;
        }

        return new WorldCoordTag(new WorldCoord(worldName, x, z));
    }

    public static boolean matches(String arg) {
        if (CoreUtilities.toLowerCase(arg).startsWith("worldcoord@")) {
            return true;
        }
        return valueOf(arg, CoreUtilities.noDebugContext) != null;
    }

    ////////////////
    //  FIELDS
    ////////////////

    public WorldCoord worldCoord;

    public WorldCoordTag(WorldCoord worldCoord) {
        this.worldCoord = worldCoord;
    }

    public WorldCoord getWorldCoord() {
        return worldCoord;
    }

    ////////////////
    //  ObjectTag impl
    ////////////////

    private String prefix = "WorldCoord";

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public WorldCoordTag setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public String identify() {
        return "worldcoord@" + worldCoord.getWorldName() + "," + worldCoord.getX() + "," + worldCoord.getZ();
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    @Override
    public String toString() {
        return identify();
    }

    ////////////////
    //  Flags
    ////////////////

    @Override
    public AbstractFlagTracker getFlagTracker() {
        String key = worldCoord.getWorldName() + "," + worldCoord.getX() + "," + worldCoord.getZ();
        return new RedirectionFlagTracker(DenizenCore.serverFlagMap,
                "__depenizen_towny_worldcoords." + key);
    }

    @Override
    public void reapplyTracker(AbstractFlagTracker tracker) {
        // nothing needed
    }

    ////////////////
    //  Tags
    ////////////////

    public static ObjectTagProcessor<WorldCoordTag> tagProcessor = new ObjectTagProcessor<>();

    public static void register() {
        AbstractFlagTracker.registerFlagHandlers(tagProcessor);

        // <--[tag]
        // @attribute <WorldCoordTag.world>
        // @returns WorldTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the world for this Towny coordinate, if loaded.
        // -->
        tagProcessor.registerTag(WorldTag.class, "world", (attribute, object) -> {
            World bukkitWorld = object.worldCoord.getBukkitWorld();
            return bukkitWorld != null ? new WorldTag(bukkitWorld) : null;
        });

        // <--[tag]
        // @attribute <WorldCoordTag.x>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the X value of this Towny coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "x", (attribute, object) ->
                new ElementTag(object.worldCoord.getX()));

        // <--[tag]
        // @attribute <WorldCoordTag.z>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the Z value of this Towny coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "z", (attribute, object) ->
                new ElementTag(object.worldCoord.getZ()));

        // <--[tag]
        // @attribute <WorldCoordTag.townblock>
        // @returns TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the TownBlock at this coordinate, if it exists.
        // -->
        tagProcessor.registerTag(TownBlockTag.class, "townblock", (attribute, object) -> {
            TownBlock block = TownyAPI.getInstance().getTownBlock(object.worldCoord);
            return block != null ? new TownBlockTag(block) : null;
        });

        // <--[tag]
        // @attribute <WorldCoordTag.town>
        // @returns TownTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the owning town of this coordinate, if claimed.
        // -->
        tagProcessor.registerTag(TownTag.class, "town", (attribute, object) -> {
            TownBlock block = TownyAPI.getInstance().getTownBlock(object.worldCoord);
            if (block == null) {
                return null;
            }
            Town town = block.getTownOrNull();
            return town != null ? new TownTag(town) : null;
        });

        // <--[tag]
        // @attribute <WorldCoordTag.is_claimed>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether this coordinate currently has a Towny claim.
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_claimed", (attribute, object) ->
                new ElementTag(TownyAPI.getInstance().getTownBlock(object.worldCoord) != null));

        // <--[tag]
        // @attribute <WorldCoordTag.world_location>
        // @returns LocationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the lower-most corner location for this Towny coordinate.
        // -->
        tagProcessor.registerTag(LocationTag.class, "world_location", (attribute, object) -> {
            Location location = object.worldCoord.getLowerMostCornerLocation();
            return location != null ? new LocationTag(location) : null;
        });

        // <--[tag]
        // @attribute <WorldCoordTag.center>
        // @returns LocationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the center location of this Towny coordinate.
        // -->
        tagProcessor.registerTag(LocationTag.class, "center", (attribute, object) -> {
            Location base = object.worldCoord.getLowerMostCornerLocation();
            if (base == null) {
                return null;
            }
            World world = base.getWorld();
            if (world == null) {
                return null;
            }
            int size = TownySettings.getTownBlockSize();
            double centerX = base.getX() + (size - 1) / 2.0;
            double centerZ = base.getZ() + (size - 1) / 2.0;
            return new LocationTag(new Location(world, centerX, base.getY(), centerZ));
        });

        // <--[tag]
        // @attribute <WorldCoordTag.at_offset[<x>,<z>]>
        // @returns WorldCoordTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the coordinate offset by the specified Towny-grid delta.
        // -->
        tagProcessor.registerTag(WorldCoordTag.class, "at_offset", (attribute, object) -> {
            if (!attribute.hasParam()) {
                attribute.echoError("WorldCoordTag.at_offset[...] requires input like 'x,z'.");
                return null;
            }
            String[] parts = attribute.getParam().split("\\s*,\\s*", 2);
            if (parts.length != 2) {
                attribute.echoError("WorldCoordTag.at_offset[...] input must be 'x,z'.");
                return null;
            }
            try {
                int offX = Integer.parseInt(parts[0]);
                int offZ = Integer.parseInt(parts[1]);
                return new WorldCoordTag(object.worldCoord.add(offX, offZ));
            }
            catch (NumberFormatException ex) {
                attribute.echoError("WorldCoordTag.at_offset[...] values must be integers.");
                return null;
            }
        });

        // <--[tag]
        // @attribute <WorldCoordTag.neighbors>
        // @returns ListTag(WorldCoordTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns up to four neighboring Towny coordinates (N/S/E/W).
        // -->
        tagProcessor.registerTag(ListTag.class, "neighbors", (attribute, object) -> {
            ListTag list = new ListTag();
            int[][] dirs = new int[][] { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
            for (int[] dir : dirs) {
                list.addObject(new WorldCoordTag(object.worldCoord.add(dir[0], dir[1])));
            }
            return list;
        });
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }
}

