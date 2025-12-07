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
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Location;
import org.bukkit.util.Vector;

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
            boolean distanceHomeBlocksPassed = false;
            boolean distanceTownPlotsPassed = false;
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
        // @attribute <LocationTag.towny_visualizer_lines[radius=<#>;selected=<list_of_townblocks>]>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of visualizer edges for the grid around the location.
        // Each edge is a MapTag: [start=Location; vector=Location(Vector); type=Element(String)]
        // Uses a Vertical-Dominant Butt-Joint algorithm to ensure edges meet perfectly at corners
        // without overlapping or gaps, specifically for Block Display entities.
        // The 'type' returns the Plot Group name, "town" (if no group), "wilderness", or "selection".
        // -->
        LocationTag.tagProcessor.registerTag(ListTag.class, "towny_visualizer_lines", (attribute, location) -> {
            TownyAPI api = TownyAPI.getInstance();
            WorldCoord centerWc = WorldCoord.parseWorldCoord(location);
            TownyWorld townyWorld = centerWc.getTownyWorld();

            if (townyWorld == null) return new ListTag();

            // --- 1. PARSE INPUTS ---
            int radius = 1;
            Set<TownBlock> selectedSet = new HashSet<>();

            if (attribute.hasParam()) {
                ObjectTag paramObj = attribute.getParamObject();

                // Handle MapTag input (Modern Syntax)
                if (paramObj.canBeType(MapTag.class)) {
                    MapTag inputMap = paramObj.asType(MapTag.class, attribute.context);

                    if (inputMap.containsKey("radius")) {
                        radius = inputMap.getObject("radius").asElement().asInt();
                    }

                    if (inputMap.containsKey("selected")) {
                        ListTag selectionList = inputMap.getObject("selected").asType(ListTag.class, attribute.context);
                        for (ObjectTag tag : selectionList.objectForms) {
                            // Support both TownBlockTag and LocationTag inputs
                            if (tag instanceof TownBlockTag) {
                                selectedSet.add(((TownBlockTag) tag).getTownBlock());
                            } else if (tag instanceof LocationTag) {
                                TownBlock tb = TownyAPI.getInstance().getTownBlock((LocationTag) tag);
                                if (tb != null) selectedSet.add(tb);
                            }
                        }
                    }
                }
                // Handle Integer input (Legacy/Simple Syntax)
                else {
                    radius = paramObj.asElement().asInt();
                }
            }

            // --- 2. CONFIGURATION ---
            int size = TownySettings.getTownBlockSize();
            double inset = 0.4; // Distance from the absolute border (half visual wall thickness)
            int centerX = centerWc.getX();
            int centerZ = centerWc.getZ();
            ListTag lines = new ListTag();

            // Define bounds
            int minX = centerX - radius; int maxX = centerX + radius;
            int minZ = centerZ - radius; int maxZ = centerZ + radius;

            // --- 3. VERTICAL SCAN (X Boundaries, Lines run along Z) ---
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    TownBlock left = getSafeTownBlock(townyWorld, x, z);
                    TownBlock right = getSafeTownBlock(townyWorld, x + 1, z);

                    if (!areBlocksMerged(left, right, selectedSet)) {
                        double borderX = (x + 1) * size;
                        double zBase = z * size;

                        // DRAW LEFT BLOCK'S EDGE (Right side of Left block)
                        if (shouldRenderEdge(left, selectedSet)) {
                            boolean topContinues = !areBlocksMerged(
                                    getSafeTownBlock(townyWorld, x, z - 1),
                                    getSafeTownBlock(townyWorld, x + 1, z - 1),
                                    selectedSet
                            );
                            boolean bottomContinues = !areBlocksMerged(
                                    getSafeTownBlock(townyWorld, x, z + 1),
                                    getSafeTownBlock(townyWorld, x + 1, z + 1),
                                    selectedSet
                            );

                            double start = topContinues ? 0 : inset;
                            double end = bottomContinues ? 0 : inset;
                            double len = size - start - end;

                            if (len > 0) {
                                lines.addObject(createEdge(
                                        new Location(location.getWorld(), borderX - inset, 0, zBase + start),
                                        new Vector(0, 0, len), // Points Z+
                                        getEdgeType(left, selectedSet)
                                ));
                            }
                        }

                        // DRAW RIGHT BLOCK'S EDGE (Left side of Right block)
                        if (shouldRenderEdge(right, selectedSet)) {
                            boolean topContinues = !areBlocksMerged(
                                    getSafeTownBlock(townyWorld, x, z - 1),
                                    getSafeTownBlock(townyWorld, x + 1, z - 1),
                                    selectedSet
                            );
                            boolean bottomContinues = !areBlocksMerged(
                                    getSafeTownBlock(townyWorld, x, z + 1),
                                    getSafeTownBlock(townyWorld, x + 1, z + 1),
                                    selectedSet
                            );

                            double start = topContinues ? 0 : inset;
                            double end = bottomContinues ? 0 : inset;
                            double len = size - start - end;

                            if (len > 0) {
                                lines.addObject(createEdge(
                                        new Location(location.getWorld(), borderX + inset, 0, zBase + start),
                                        new Vector(0, 0, len), // Points Z+
                                        getEdgeType(right, selectedSet)
                                ));
                            }
                        }
                    }
                }
            }

            // --- 4. HORIZONTAL SCAN (Z Boundaries, Lines run along X) ---
            for (int z = minZ; z < maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    TownBlock top = getSafeTownBlock(townyWorld, x, z);
                    TownBlock bottom = getSafeTownBlock(townyWorld, x, z + 1);

                    if (!areBlocksMerged(top, bottom, selectedSet)) {
                        double borderZ = (z + 1) * size;
                        double xBase = x * size;

                        // Horizontal lines always shrunk to fit
                        double len = size - (inset * 2);

                        // DRAW TOP BLOCK'S EDGE (Bottom side of Top block)
                        if (shouldRenderEdge(top, selectedSet)) {
                            lines.addObject(createEdge(
                                    new Location(location.getWorld(), xBase + inset, 0, borderZ - inset),
                                    new Vector(len, 0, 0), // Points X+
                                    getEdgeType(top, selectedSet)
                            ));
                        }

                        // DRAW BOTTOM BLOCK'S EDGE (Top side of Bottom block)
                        if (shouldRenderEdge(bottom, selectedSet)) {
                            lines.addObject(createEdge(
                                    new Location(location.getWorld(), xBase + inset, 0, borderZ + inset),
                                    new Vector(len, 0, 0), // Points X+
                                    getEdgeType(bottom, selectedSet)
                            ));
                        }
                    }
                }
            }

            return lines;
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

    // --- HELPER METHODS ---

    private static TownBlock getSafeTownBlock(TownyWorld world, int x, int z) {
        try {
            return world.getTownBlock(x, z);
        } catch (NotRegisteredException e) {
            return null;
        }
    }

    private static String getEdgeType(TownBlock tb, Set<TownBlock> selection) {
        // 1. Selection Override
        if (tb != null && selection.contains(tb)) return "selection";

        // 2. Wilderness
        if (tb == null || !tb.hasTown()) return "wilderness";

        // 3. Plot Group Logic
        if (tb.hasPlotObjectGroup()) {
            try {
                // The Plot Group itself doesn't have a "Type", but its constituent blocks do.
                // We grab the type of the FIRST block in the group to define the group's type.
                PlotGroup group = tb.getPlotObjectGroup();
                Collection<TownBlock> blocks = group.getTownBlocks();
                if (blocks != null && !blocks.isEmpty()) {
                    return blocks.iterator().next().getType().getName().toLowerCase();
                }
            } catch (Exception e) {
                // Fallthrough if error accessing group data
            }
        }

        // 4. Default: It is a town block, but not in a group
        return "town";
    }

    private static boolean shouldRenderEdge(TownBlock tb, Set<TownBlock> selection) {
        if (tb != null && selection.contains(tb)) return true;
        if (tb != null && tb.hasTown()) return true;
        return false;
    }

    private static boolean areBlocksMerged(TownBlock a, TownBlock b, Set<TownBlock> selection) {
        boolean aSel = (a != null && selection.contains(a));
        boolean bSel = (b != null && selection.contains(b));

        // 1. Both Selected -> Merge
        if (aSel && bSel) return true;
        // 2. One Selected, One Not -> Separate
        if (aSel != bSel) return false;

        // Neither selected: Check general Towny status
        boolean aHasTown = (a != null && a.hasTown());
        boolean bHasTown = (b != null && b.hasTown());

        // 3. Both Wilderness -> Merge (We don't draw lines in the wild)
        if (!aHasTown && !bHasTown) return true;
        // 4. One Town, One Wilderness -> Separate (Draw Town Border)
        if (aHasTown != bHasTown) return false;

        // 5. Both are Towns (Check if they are the SAME town)
        try {
            if (!a.getTown().equals(b.getTown())) return false; // Different towns = Border

            // Same Town: Check PlotGroups
            boolean aHasPG = a.hasPlotObjectGroup();
            boolean bHasPG = b.hasPlotObjectGroup();

            if (aHasPG && bHasPG) {
                // Both have groups. Are they the SAME group?
                return a.getPlotObjectGroup().equals(b.getPlotObjectGroup());
            }

            // If one has group and the other doesn't -> Separate
            if (aHasPG != bHasPG) return false;

            // Neither has group -> Merge (They are both generic town plots)
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static MapTag createEdge(Location start, Vector vec, String type) {
        MapTag map = new MapTag();
        map.putObject("start", new LocationTag(start));
        map.putObject("vector", new LocationTag(vec.toLocation(start.getWorld())));
        map.putObject("type", new ElementTag(type));
        return map;
    }
}