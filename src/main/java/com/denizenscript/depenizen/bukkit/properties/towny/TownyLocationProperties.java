package com.denizenscript.depenizen.bukkit.properties.towny;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

import java.util.UUID;

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
            }
            catch (NotRegisteredException ex) {
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
            }
            catch (NotRegisteredException ex) {
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
        // @attribute <LocationTag.towny_grid_location>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny grid coordinates (world;X;Z) of this location.
        // This uses Towny's configured town_block_size, so it will match the Towny plot grid
        // even if the grid size was changed in the Towny config.
        //
        // The format is "world;x;z" (for example: "World;12;-7").
        // -->
        LocationTag.tagProcessor.registerTag(ListTag.class, "towny_grid_location", (attribute, location) -> {
            WorldCoord coord = WorldCoord.parseWorldCoord(location); // respects town_block_size
            ListTag list = new ListTag();
            list.addObject(new ElementTag(coord.getWorldName()));
            list.addObject(new ElementTag(coord.getX()));
            list.addObject(new ElementTag(coord.getZ()));
            return list;
        });
        // <--[tag]
        // @attribute <LocationTag.towny_is_town_allowed>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether a new Towny town *may be founded* at this location according to Towny's rules.
        //
        // This tag performs the same distance and validity checks that Towny uses when executing
        // the `/town new` command. It evaluates:
        //
        // - Whether the location is in a Towny-enabled world.
        // - Whether the location’s TownBlock is currently unclaimed (wilderness).
        // - Whether the location is at least the configured minimum distance from other towns'
        //   homeblocks (<@link tag TownySettings.getMinDistanceFromTownHomeblocks>).
        // - Whether the location is at least the configured minimum distance from other towns'
        //   plot blocks (<@link tag TownySettings.getMinDistanceFromTownPlotblocks>).
        //
        // Returns `true` if and only if all Towny requirements for founding a new town are satisfied.
        //
        // Useful when checking if a player may create a town at a target location before calling
        // the Depenizen `town create` command.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_is_town_allowed", ((attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();
            WorldCoord wc;
            try {
                wc = WorldCoord.parseWorldCoord(location);
            }
            catch (Exception ex) {
                attribute.echoError("Towny: cannot convert location to WorldCoord: " + ex.getMessage());
                return new ElementTag(false);
            }
            TownyWorld townyWorld = wc.getTownyWorld();
            if (townyWorld == null || !townyWorld.isUsingTowny()) {
                // Not a Towny world, so can't found a town here.
                return new ElementTag(false);
            }
            if (api.getTownBlock(wc) != null) {
                return new ElementTag(false);
            }
            int requiredHomeBlocks = TownySettings.getMinDistanceFromTownHomeblocks();
            int requiredTownPlots = TownySettings.getMinDistanceFromTownPlotblocks();
            Coord coord = new Coord(wc.getX(), wc.getZ());
            boolean distanceHomeBlocksPassed = false;
            boolean distanceTownPlotsPassed = false;
            if (requiredHomeBlocks > 0) {
                int distanceHomeBlocks = townyWorld.getMinDistanceFromOtherTownsHomeBlocks(coord);
                distanceHomeBlocksPassed = distanceHomeBlocks >= requiredHomeBlocks;
            } else {
                distanceHomeBlocksPassed = true;
            }
            if(requiredTownPlots > 0) {
                int distanceTownPlots = townyWorld.getMinDistanceFromOtherTownsPlots(coord);
                distanceTownPlotsPassed = distanceTownPlots >= requiredTownPlots;
            }
            else{
                distanceTownPlotsPassed = true;
            }
            return new ElementTag(distanceHomeBlocksPassed && distanceTownPlotsPassed);
        }));
        // <--[tag]
        // @attribute <LocationTag.towny_is_new_homeblock_allowed>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether this location may be assigned as the *new homeblock* of its town,
        // following the same distance and world rules used by Towny's
        // `/town set homeblock` command.
        //
        // This tag checks:
        // - The location is inside a Towny world.
        // - The location is part of a claimed TownBlock (not wilderness).
        // - The TownBlock belongs to a town.
        // - The location is at least the configured minimum distance from:
        //     - Other towns' homeblocks
        //     - Other towns' plot blocks
        //
        // It returns `true` only if Towny would allow setting this location
        // as the town's homeblock.
        //
        // Useful when validating whether a town may move its homeblock
        // prior to calling a Depenizen script or Towny API method.
        // -->
        LocationTag.tagProcessor.registerTag(ElementTag.class, "towny_is_new_homeblock_allowed", ((attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();

            // Convert to WorldCoord
            WorldCoord wc;
            try {
                wc = WorldCoord.parseWorldCoord(location);
            }
            catch (Exception ex) {
                attribute.echoError("Towny: cannot convert location to WorldCoord: " + ex.getMessage());
                return new ElementTag(false);
            }

            TownyWorld tWorld = wc.getTownyWorld();
            if (tWorld == null || !tWorld.isUsingTowny()) {
                return new ElementTag(false);
            }

            // Homeblocks MUST be inside a town, so ensure it is claimed.
            TownBlock block = api.getTownBlock(wc);
            if (block == null) {
                return new ElementTag(false); // must be claimed
            }

            Town town = block.getTownOrNull();
            if (town == null) {
                return new ElementTag(false); // must belong to a town
            }

            // Pull Towny config values
            int requiredHomeBlocks = TownySettings.getMinDistanceFromTownHomeblocks();
            int requiredTownPlots = TownySettings.getMinDistanceFromTownPlotblocks();

            Coord coord = new Coord(wc.getX(), wc.getZ());

            boolean distanceHomeBlocksPassed;
            boolean distanceTownPlotsPassed;

            // Homeblock -> homeblock distance check
            if (requiredHomeBlocks > 0) {
                int distanceHomeBlocks = tWorld.getMinDistanceFromOtherTownsHomeBlocks(coord, town);
                distanceHomeBlocksPassed = distanceHomeBlocks >= requiredHomeBlocks;
            }
            else {
                distanceHomeBlocksPassed = true;
            }

            // Plot -> plot distance check
            if (requiredTownPlots > 0) {
                int distanceTownPlots = tWorld.getMinDistanceFromOtherTownsPlots(coord, town);
                distanceTownPlotsPassed = distanceTownPlots >= requiredTownPlots;
            }
            else {
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
        // This can be used to access additional Towny data such as
        // the plot owner, plot type, permissions, plot group, etc.
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
        // Returns the Towny PlotGroup at this location, if the TownBlock
        // belongs to one. PlotGroups are used in Towny to link multiple plots
        // together for shared settings such as permissions or ownership.
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
