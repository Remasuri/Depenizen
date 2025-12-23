package com.denizenscript.depenizen.bukkit.properties.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.denizenscript.depenizen.bukkit.objects.towny.WorldCoordTag;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import com.denizenscript.depenizen.bukkit.utilities.towny.TownyVisualizerUtils;
import java.util.*;

public class TownyLocationProperties {

    public static void register() {

        // <--[tag]
        // @attribute <LocationTag.towny_allows_pvp>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether Towny would allow PVP here.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_allows_pvp", (attribute, location) -> {
            return new ElementTag(TownyAPI.getInstance().isPVP(location));
        });

        // <--[tag]
        // @attribute <LocationTag.towny_resident>
        // @returns PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the resident of a Towny plot at the location, if any.
        // -->
        LocationTag.tagProcessor.registerTag(PlayerTag.class, "towny_resident", (attribute, location) -> {
            try {
                return getResidentAtLocation(location);
            } catch (NotRegisteredException ex) {
                if (!attribute.hasAlternative()) {
                    attribute.echoError("Towny tag NotRegisteredException: " + ex.getMessage());
                }
            }
            return null;
        });

        LocationTag.tagProcessor.registerTag(PlayerTag.class, "towny", (attribute, location) -> {
            try {
                // <--[tag]
                // @attribute <LocationTag.towny.resident>
                // @returns PlayerTag
                // @plugin Depenizen, Towny
                // @deprecated use 'towny_resident'
                // @description
                // Returns the resident of a Towny plot at the location, if any.
                // Deprecated in favor of <@link tag LocationTag.towny_resident>.
                // -->
                if (attribute.startsWith("resident", 2)) {
                    attribute.fulfill(1);
                    return getResidentAtLocation(location);
                }
            } catch (NotRegisteredException ex) {
                if (!attribute.hasAlternative()) {
                    attribute.echoError("Towny tag NotRegisteredException: " + ex.getMessage());
                }
            }
            return null;
        });

        // <--[tag]
        // @attribute <LocationTag.towny_type>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the type of the Towny area this location is in.
        // Can be Default, Shop, Arena, Embassy, Wilds, Inn, Jail, Farm, Bank
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_type", (attribute, location) -> {
            TownBlock block = TownyAPI.getInstance().getTownBlock(location);
            if (block != null) {
                return new ElementTag(block.getType().getName());
            }
            return null;
        });

        // <--[tag]
        // @attribute <LocationTag.towny_worldcoord>
        // @returns WorldCoordTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny grid coordinates (world;X;Z) of this location as a WorldCoordTag.
        // -->
        LocationTag.tagProcessor.registerTag(WorldCoordTag.class, "towny_worldcoord", (attribute, location) -> {
            WorldCoord coord = WorldCoord.parseWorldCoord(location); // respects town_block_size
            return new WorldCoordTag(coord);
        });

        // <--[tag]
        // @attribute <LocationTag.towny_is_town_allowed>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether a new Towny town *may be founded* at this location according to Towny's rules.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_is_town_allowed", ((attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();
            WorldCoord wc;
            try {
                wc = WorldCoord.parseWorldCoord(location);
            } catch (Exception ex) {
                attribute.echoError("Towny: cannot convert location to WorldCoord: " + ex.getMessage());
                return new ElementTag(false);
            }
            TownyWorld townyWorld = wc.getTownyWorld();
            if (townyWorld == null || !townyWorld.isUsingTowny()) {
                return new ElementTag(false);
            }
            if (api.getTownBlock(wc) != null) {
                return new ElementTag(false);
            }
            int requiredHomeBlocks = TownySettings.getMinDistanceFromTownHomeblocks();
            int requiredTownPlots = TownySettings.getMinDistanceFromTownPlotblocks();
            Coord coord = new Coord(wc.getX(), wc.getZ());
            boolean distanceHomeBlocksPassed;
            boolean distanceTownPlotsPassed;
            if (requiredHomeBlocks > 0) {
                int distanceHomeBlocks = townyWorld.getMinDistanceFromOtherTownsHomeBlocks(coord);
                distanceHomeBlocksPassed = distanceHomeBlocks >= requiredHomeBlocks;
            } else {
                distanceHomeBlocksPassed = true;
            }
            if (requiredTownPlots > 0) {
                int distanceTownPlots = townyWorld.getMinDistanceFromOtherTownsPlots(coord);
                distanceTownPlotsPassed = distanceTownPlots >= requiredTownPlots;
            } else {
                distanceTownPlotsPassed = true;
            }
            return new ElementTag(distanceHomeBlocksPassed && distanceTownPlotsPassed);
        }));
        // <--[tag]
        // @attribute <LocationTag.towny_nearest_town>
        // @returns TownTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the nearest Towny town to this location, limited to the same world.
        //
        // Behavior:
        // - If the location is already within a town, returns that town.
        // - Otherwise, scans all Towny towns in the same world and returns the closest one.
        //   Distance is measured horizontally (X/Z) from the location to the town's spawn.
        //   If a town has no spawn, its homeblock center is used instead.
        // - If no town exists in that world, returns null.
        // -->
        LocationTag.tagProcessor.registerTag(TownTag.class, "towny_nearest_town", (attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();
            org.bukkit.World world = location.getWorld();
            if (world == null) {
                return null;
            }

            // 1) If we're already in a town, just return that.
            Town directTown = api.getTown(location);
            if (directTown != null) {
                return new TownTag(directTown);
            }

            TownyUniverse universe = TownyUniverse.getInstance();
            Town nearest = null;
            double bestDistSq = Double.MAX_VALUE;

            for (Town town : universe.getTowns()) {
                Location referenceLoc = null;

                // Prefer the town spawn if it exists and is in the same world
                try {
                    Location spawn = town.getSpawn();
                    if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(world)) {
                        referenceLoc = spawn;
                    }
                }
                catch (TownyException ignored) {
                    // no spawn set, we'll try homeblock below
                }

                // Fallback: homeblock center, if in same world
                if (referenceLoc == null) {
                    TownBlock home = town.getHomeBlockOrNull();
                    if (home != null) {
                        WorldCoord wc = home.getWorldCoord();
                        org.bukkit.World hbWorld = wc.getBukkitWorld();
                        if (hbWorld != null && hbWorld.equals(world)) {
                            int size = TownySettings.getTownBlockSize();
                            double centerX = wc.getX() * size + (size / 2.0);
                            double centerZ = wc.getZ() * size + (size / 2.0);
                            referenceLoc = new Location(hbWorld, centerX, location.getY(), centerZ);
                        }
                    }
                }

                if (referenceLoc == null || !referenceLoc.getWorld().equals(world)) {
                    continue; // town is in another world or has no usable reference
                }

                double dx = referenceLoc.getX() - location.getX();
                double dz = referenceLoc.getZ() - location.getZ();
                double distSq = dx * dx + dz * dz;

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    nearest = town;
                }
            }

            if (nearest == null) {
                return null;
            }
            return new TownTag(nearest);
        });


        // <--[tag]
        // @attribute <LocationTag.towny_is_new_homeblock_allowed>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether this location may be assigned as the *new homeblock* of its town.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_is_new_homeblock_allowed", ((attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();
            WorldCoord wc;
            try {
                wc = WorldCoord.parseWorldCoord(location);
            } catch (Exception ex) {
                attribute.echoError("Towny: cannot convert location to WorldCoord: " + ex.getMessage());
                return new ElementTag(false);
            }

            TownyWorld tWorld = wc.getTownyWorld();
            if (tWorld == null || !tWorld.isUsingTowny()) {
                return new ElementTag(false);
            }

            TownBlock block = api.getTownBlock(wc);
            if (block == null) return new ElementTag(false);

            Town town = block.getTownOrNull();
            if (town == null) return new ElementTag(false);

            int requiredHomeBlocks = TownySettings.getMinDistanceFromTownHomeblocks();
            int requiredTownPlots = TownySettings.getMinDistanceFromTownPlotblocks();

            Coord coord = new Coord(wc.getX(), wc.getZ());
            boolean distanceHomeBlocksPassed;
            boolean distanceTownPlotsPassed;

            if (requiredHomeBlocks > 0) {
                int distanceHomeBlocks = tWorld.getMinDistanceFromOtherTownsHomeBlocks(coord, town);
                distanceHomeBlocksPassed = distanceHomeBlocks >= requiredHomeBlocks;
            } else {
                distanceHomeBlocksPassed = true;
            }

            if (requiredTownPlots > 0) {
                int distanceTownPlots = tWorld.getMinDistanceFromOtherTownsPlots(coord, town);
                distanceTownPlotsPassed = distanceTownPlots >= requiredTownPlots;
            } else {
                distanceTownPlotsPassed = true;
            }

            return new ElementTag(distanceHomeBlocksPassed && distanceTownPlotsPassed);
        }));

        // <--[tag]
        // @attribute <LocationTag.has_town>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether the location is within a town.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "has_town", (attribute, location) -> {
            return new ElementTag(TownyAPI.getInstance().getTown(location) != null);
        });

        // <--[tag]
        // @attribute <LocationTag.town>
        // @returns TownTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the town at the specified location.
        // -->
        LocationTag.tagProcessor.registerTag(TownTag.class, "town", (attribute, location) -> {
            String town = TownyAPI.getInstance().getTownName(location);
            if (town == null) {
                return null;
            }
            return new TownTag(TownyUniverse.getInstance().getTown(town));
        });

        // <--[tag]
        // @attribute <LocationTag.is_nation_zone>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether the location is a nation zone.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "is_nation_zone", (attribute, location) -> {
            return new ElementTag(TownyAPI.getInstance().isNationZone(location));
        });

        // <--[tag]
        // @attribute <LocationTag.is_wilderness>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether the location is wilderness.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "is_wilderness", (attribute, location) -> {
            return new ElementTag(TownyAPI.getInstance().isWilderness(location));
        });

        // <--[tag]
        // @attribute <LocationTag.townblock>
        // @returns TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny TownBlock at this location, if any.
        // -->
        LocationTag.tagProcessor.registerTag(TownBlockTag.class, "townblock", (attribute, location) -> {
            TownBlock tb = TownyAPI.getInstance().getTownBlock(location);
            if (tb == null) {
                return null;
            }
            return new TownBlockTag(tb);
        });

        // <--[tag]
        // @attribute <LocationTag.plotgroup>
        // @returns PlotGroupTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny PlotGroup at this location.
        // -->
        LocationTag.tagProcessor.registerTag(PlotGroupTag.class, "plotgroup", (attribute, location) -> {
            TownBlock block = TownyAPI.getInstance().getTownBlock(location);
            if (block == null || block.getPlotObjectGroup() == null) {
                return null;
            }
            return new PlotGroupTag(block.getPlotObjectGroup());
        });
    }

    public static PlayerTag getResidentAtLocation(LocationTag location) throws NotRegisteredException {
        TownBlock block = TownyAPI.getInstance().getTownBlock(location);
        if (block == null) {
            return null;
        }
        if (!block.hasResident()) {
            return null;
        }
        UUID player = block.getResident().getUUID();
        if (player == null) {
            return null;
        }
        return new PlayerTag(player);
    }
}
