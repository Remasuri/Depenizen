package com.denizenscript.depenizen.bukkit.properties.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.*;

public class TownyVisualizerUtils {

    // Tunable constants, shared by both LocationTag and TownTag visualizers
    public static final double INSET = 0.4;
    public static final double GROW = 0.5;

    // Unified info for each townblock coord, used by the edge pass.
    public static final class BlockInfo {
        public final TownBlock block;
        public final String regionKey;   // used to decide merging (same region => no border)
        public final String edgeType;    // renderer color (wilderness, town, or plot type)
        public final String plotGroupId; // PlotGroup UUID string, or null

        public BlockInfo(TownBlock block, String regionKey, String edgeType, String plotGroupId) {
            this.block = block;
            this.regionKey = regionKey;
            this.edgeType = edgeType;
            this.plotGroupId = plotGroupId;
        }
    }

    private static TownBlock getSafeTownBlock(TownyWorld world, int x, int z) {
        try {
            return world.getTownBlock(x, z);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Builds a grid of BlockInfo for the given TownyWorld in the given coord bounds.
     * Includes wilderness cells (block == null) so that town-vs-wilderness borders render correctly.
     */
    private static Map<Long, BlockInfo> buildBlockGrid(
            TownyWorld world,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        Map<Long, BlockInfo> grid = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                TownBlock tb = getSafeTownBlock(world, x, z);

                String regionKey;
                String edgeType;
                String plotGroupId = null;

                if (tb == null || !tb.hasTown()) {
                    // Wilderness
                    regionKey = "wilderness";
                    edgeType = "wilderness";
                }
                else {
                    try {
                        Town town = tb.getTown();
                        String townName = town.getName();

                        if (tb.hasPlotObjectGroup()) {
                            PlotGroup group = tb.getPlotObjectGroup();
                            String typeName = safeLower(tb.getType().getName()); // plot type name
                            plotGroupId = group.getUUID().toString();

                            regionKey = "pg:" + plotGroupId;
                            edgeType = typeName.isEmpty() ? "town" : typeName;
                        }
                        else {
                            // Generic town block (no group) – merge by town name
                            regionKey = "town:" + townName;
                            edgeType = "town";
                        }
                    }
                    catch (Exception ex) {
                        // Fallback, shouldn't usually happen
                        regionKey = "error";
                        edgeType = "town";
                    }
                }

                grid.put(key(x, z), new BlockInfo(tb, regionKey, edgeType, plotGroupId));
            }
        }
        return grid;
    }

    /**
     * Builds a grid for a *selection* of townblocks only.
     *
     * All selected blocks share the same regionKey ("selection") and edgeType ("selection").
     * Everything else in the scan area is wilderness.
     *
     * That means:
     * - No borders between selected blocks (same regionKey).
     * - Borders only between selection and non-selection.
     * - Edges are only drawn from the selection side (wilderness is filtered out).
     */
    private static Map<Long, BlockInfo> buildSelectionGrid(
            Collection<TownBlock> selectedBlocks,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        Map<Long, BlockInfo> grid = new HashMap<>();

        if (selectedBlocks == null || selectedBlocks.isEmpty()) {
            return grid;
        }

        Map<Long, TownBlock> selectedMap = new HashMap<>();
        for (TownBlock tb : selectedBlocks) {
            if (tb == null) {
                continue;
            }
            selectedMap.put(key(tb.getX(), tb.getZ()), tb);
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long k = key(x, z);
                TownBlock tb = selectedMap.get(k);

                if (tb == null) {
                    // Everything not in the selection is wilderness
                    grid.put(k, new BlockInfo(null, "wilderness", "wilderness", null));
                    continue;
                }

                // All selected blocks are one region with a single edge type.
                String regionKey = "selection";
                String edgeType = "selection";
                String plotGroupId = null;

                grid.put(k, new BlockInfo(tb, regionKey, edgeType, plotGroupId));
            }
        }

        return grid;
    }

    private static BlockInfo getBlock(Map<Long, BlockInfo> grid, int x, int z) {
        return grid.get(key(x, z));
    }

    private static boolean sameRegion(BlockInfo a, BlockInfo b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.regionKey, b.regionKey);
    }

    private static boolean areBlocksMerged(BlockInfo a, BlockInfo b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            // one wilderness, one not -> not merged (border)
            return false;
        }
        return Objects.equals(a.regionKey, b.regionKey);
    }

    private static boolean shouldRenderEdge(BlockInfo info) {
        if (info == null) {
            return false;
        }
        // We never draw wilderness edges – edges are drawn from the non-wilderness side.
        if ("wilderness".equals(info.edgeType)) {
            return false;
        }
        return true; // town, plot-type, or "selection"
    }

    private static MapTag createEdge(Location start, Vector vec, BlockInfo info) {
        MapTag map = new MapTag();
        map.putObject("start", new LocationTag(start));
        map.putObject("vector", new LocationTag(vec.toLocation(start.getWorld())));
        map.putObject("type", new ElementTag(info.edgeType));
        if (info.plotGroupId != null) {
            map.putObject("plotgroup", new ElementTag(info.plotGroupId));
        }
        return map;
    }

    /**
     * Shared edge builder for both the normal town visualizer and the selection visualizer.
     *
     * @param grid       pre-built BlockInfo grid (includes padding)
     * @param bukkitWorld Bukkit world to place Locations in.
     * @param minX       min Towny X (inclusive) of the scan area.
     * @param maxX       max Towny X (inclusive) of the scan area.
     * @param minZ       min Towny Z (inclusive) of the scan area.
     * @param maxZ       max Towny Z (inclusive) of the scan area.
     */
    private static ListTag buildEdgesFromGrid(
            Map<Long, BlockInfo> grid,
            World bukkitWorld,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        ListTag lines = new ListTag();
        if (grid == null || bukkitWorld == null) {
            return lines;
        }

        int size = TownySettings.getTownBlockSize();
        double inset = INSET;
        double grow = GROW;

        // --- VERTICAL SCAN (X boundaries, edges run along +Z) ---
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockInfo left = getBlock(grid, x, z);
                BlockInfo right = getBlock(grid, x + 1, z);

                if (areBlocksMerged(left, right)) {
                    continue;
                }

                double borderX = (x + 1) * size;
                double zBase = z * size;

                // --- LEFT BLOCK'S EDGE (right side of left block) ---
                if (shouldRenderEdge(left)) {
                    double start = inset;          // top side (lower Z)
                    double end = size - inset;     // bottom side (higher Z)

                    // Top side neighbors (Z-)
                    BlockInfo directTop = getBlock(grid, x, z - 1);
                    BlockInfo diagTop = getBlock(grid, x + 1, z - 1); // across border at corner
                    if (sameRegion(left, directTop)) {
                        start -= grow;
                        if (sameRegion(left, diagTop)) {
                            start -= grow;
                        }
                    }

                    // Bottom side neighbors (Z+)
                    BlockInfo directBottom = getBlock(grid, x, z + 1);
                    BlockInfo diagBottom = getBlock(grid, x + 1, z + 1);
                    if (sameRegion(left, directBottom)) {
                        end += grow;
                        if (sameRegion(left, diagBottom)) {
                            end += grow;
                        }
                    }

                    double len = end - start;
                    if (len > 0) {
                        lines.addObject(createEdge(
                                new Location(bukkitWorld, borderX - inset, 0, zBase + start),
                                new Vector(0, 0, len),
                                left
                        ));
                    }
                }

                // --- RIGHT BLOCK'S EDGE (left side of right block) ---
                if (shouldRenderEdge(right)) {
                    double start = inset;          // top side (lower Z)
                    double end = size - inset;     // bottom side (higher Z)

                    // Top side neighbors (Z-)
                    BlockInfo directTop = getBlock(grid, x + 1, z - 1);
                    BlockInfo diagTop = getBlock(grid, x, z - 1);
                    if (sameRegion(right, directTop)) {
                        start -= grow;
                        if (sameRegion(right, diagTop)) {
                            start -= grow;
                        }
                    }

                    // Bottom side neighbors (Z+)
                    BlockInfo directBottom = getBlock(grid, x + 1, z + 1);
                    BlockInfo diagBottom = getBlock(grid, x, z + 1);
                    if (sameRegion(right, directBottom)) {
                        end += grow;
                        if (sameRegion(right, diagBottom)) {
                            end += grow;
                        }
                    }

                    double len = end - start;
                    if (len > 0) {
                        lines.addObject(createEdge(
                                new Location(bukkitWorld, borderX + inset, 0, zBase + start),
                                new Vector(0, 0, len),
                                right
                        ));
                    }
                }
            }
        }

        // --- HORIZONTAL SCAN (Z boundaries, edges run along +X) ---
        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                BlockInfo top = getBlock(grid, x, z);
                BlockInfo bottom = getBlock(grid, x, z + 1);

                if (areBlocksMerged(top, bottom)) {
                    continue;
                }

                double borderZ = (z + 1) * size;
                double xBase = x * size;

                // --- TOP BLOCK'S EDGE (bottom side of top block) ---
                if (shouldRenderEdge(top)) {
                    double start = inset;          // left side (lower X)
                    double end = size - inset;     // right side (higher X)

                    // Left side neighbors (X-)
                    BlockInfo directLeft = getBlock(grid, x - 1, z);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z + 1);
                    if (sameRegion(top, directLeft)) {
                        start -= grow;
                        if (sameRegion(top, diagLeft)) {
                            start -= grow;
                        }
                    }

                    // Right side neighbors (X+)
                    BlockInfo directRight = getBlock(grid, x + 1, z);
                    BlockInfo diagRight = getBlock(grid, x + 1, z + 1);
                    if (sameRegion(top, directRight)) {
                        end += grow;
                        if (sameRegion(top, diagRight)) {
                            end += grow;
                        }
                    }

                    double len = end - start;
                    if (len > 0) {
                        lines.addObject(createEdge(
                                new Location(bukkitWorld, xBase + start, 0, borderZ - inset),
                                new Vector(len, 0, 0),
                                top
                        ));
                    }
                }

                // --- BOTTOM BLOCK'S EDGE (top side of bottom block) ---
                if (shouldRenderEdge(bottom)) {
                    double start = inset;          // left side (lower X)
                    double end = size - inset;     // right side (higher X)

                    // Left side neighbors (X-)
                    BlockInfo directLeft = getBlock(grid, x - 1, z + 1);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z);
                    if (sameRegion(bottom, directLeft)) {
                        start -= grow;
                        if (sameRegion(bottom, diagLeft)) {
                            start -= grow;
                        }
                    }

                    // Right side neighbors (X+)
                    BlockInfo directRight = getBlock(grid, x + 1, z + 1);
                    BlockInfo diagRight = getBlock(grid, x + 1, z);
                    if (sameRegion(bottom, directRight)) {
                        end += grow;
                        if (sameRegion(bottom, diagRight)) {
                            end += grow;
                        }
                    }

                    double len = end - start;
                    if (len > 0) {
                        lines.addObject(createEdge(
                                new Location(bukkitWorld, xBase + start, 0, borderZ + inset),
                                new Vector(len, 0, 0),
                                bottom
                        ));
                    }
                }
            }
        }

        return lines;
    }

    /**
     * Existing town-wide edge builder – unchanged behaviour, now using the shared edge pass.
     */
    public static ListTag buildVisualizerEdges(
            TownyWorld townyWorld,
            World bukkitWorld,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        ListTag lines = new ListTag();
        if (townyWorld == null || bukkitWorld == null) {
            return lines;
        }

        // Build cached grid with 1-block padding to allow neighbor checks outside the scan range
        Map<Long, BlockInfo> grid = buildBlockGrid(
                townyWorld,
                minX - 1, maxX + 1,
                minZ - 1, maxZ + 1
        );

        return buildEdgesFromGrid(grid, bukkitWorld, minX, maxX, minZ, maxZ);
    }

    /**
     * New: builds an edge-map for a *selection* of TownBlocks.
     *
     * The whole selection is treated as a single region ("selection") and edges are generated
     * only where a selected block borders a non-selected block. All edges use type "selection".
     */
    public static ListTag buildSelectionVisualizerEdges(
            TownyWorld townyWorld,
            World bukkitWorld,
            Collection<TownBlock> selectedBlocks
    ) {
        ListTag lines = new ListTag();
        if (townyWorld == null || bukkitWorld == null || selectedBlocks == null || selectedBlocks.isEmpty()) {
            return lines;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (TownBlock tb : selectedBlocks) {
            if (tb == null) {
                continue;
            }
            int x = tb.getX();
            int z = tb.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        if (minX == Integer.MAX_VALUE) {
            return lines;
        }

        // Pad the bounds just like TownTag.towny_visualizer_lines does
        int scanMinX = minX - 1;
        int scanMaxX = maxX + 1;
        int scanMinZ = minZ - 1;
        int scanMaxZ = maxZ + 1;

        // Grid includes extra padding around scan area for neighbor checks
        Map<Long, BlockInfo> grid = buildSelectionGrid(
                selectedBlocks,
                scanMinX - 1, scanMaxX + 1,
                scanMinZ - 1, scanMaxZ + 1
        );

        // IMPORTANT: pass the *padded* scan bounds into the edge builder
        return buildEdgesFromGrid(grid, bukkitWorld, scanMinX, scanMaxX, scanMinZ, scanMaxZ);
    }

}
