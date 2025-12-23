package com.denizenscript.depenizen.bukkit.utilities.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.*;

public class TownyVisualizerUtils {

    // Tunable constants
    public static final double INSET = 0.4;
    public static final double GROW = 0.5;

    /**
     * Unified info for each WorldCoord cell, used by the edge pass.
     *
     * edgeType used in output rendering:
     * - "wilderness"  (no townblock)
     * - "town"        (regular town block)
     * - "<plotType>"  (plot type name, lowercased)
     * - "homeblock"   (town homeblock, has its own region)
     * - "selection"   (selection-universe inside)
     * - "outside"     (selection-universe outside, never rendered)
     */
    public static final class BlockInfo {
        public final WorldCoord coord;      // always non-null
        public final TownBlock townBlock;   // may be null
        public final String regionKey;      // used to decide merging (same region => no border)
        public final String edgeType;       // output type
        public final String plotGroupId;    // PlotGroup UUID string, or null

        public BlockInfo(WorldCoord coord, TownBlock townBlock, String regionKey, String edgeType, String plotGroupId) {
            this.coord = coord;
            this.townBlock = townBlock;
            this.regionKey = regionKey;
            this.edgeType = edgeType;
            this.plotGroupId = plotGroupId;
        }
    }

    // --------------------------
    // Basic helpers
    // --------------------------

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
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
            return false;
        }
        return Objects.equals(a.regionKey, b.regionKey);
    }

    private static boolean isOutside(BlockInfo info) {
        return info != null && "outside".equals(info.edgeType);
    }

    private static boolean shouldRenderEdge(BlockInfo info) {
        if (info == null) {
            return false;
        }
        // In normal (non-selection) mode, we don't render wilderness edges.
        return !"wilderness".equals(info.edgeType);
    }

    private static boolean shouldRenderSelectionEdge(BlockInfo info) {
        // Selection-universe mode: only render inside edges (type="selection")
        return info != null && "selection".equals(info.edgeType);
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

    // --------------------------
    // Towny classification (WorldCoord -> BlockInfo)
    // --------------------------

    private static TownBlock getSafeTownBlock(WorldCoord wc) {
        try {
            return wc.getTownBlockOrNull();
        }
        catch (Throwable ex) {
            return null;
        }
    }

    private static boolean isTownHomeBlock(TownBlock tb, Town town) {
        if (tb == null || town == null) {
            return false;
        }
        try {
            TownBlock home = town.getHomeBlock();
            return home != null && home.equals(tb);
        }
        catch (Throwable ex) {
            return false;
        }
    }

    /**
     * Classify a WorldCoord into wilderness/town/plot/homeblock.
     */
    private static BlockInfo classify(WorldCoord wc) {
        TownBlock tb = getSafeTownBlock(wc);

        String regionKey;
        String edgeType;
        String plotGroupId = null;

        if (tb == null || !tb.hasTown()) {
            regionKey = "wilderness";
            edgeType = "wilderness";
        }
        else {
            try {
                Town town = tb.getTown();
                String townName = town.getName();

                if (isTownHomeBlock(tb, town)) {
                    // Homeblock is its own region so it gets edges against adjacent town blocks.
                    regionKey = "home:" + townName;
                    edgeType = "homeblock";
                }
                else if (tb.hasPlotObjectGroup()) {
                    PlotGroup group = tb.getPlotObjectGroup();
                    plotGroupId = group.getUUID().toString();

                    String typeName = safeLower(tb.getType().getName());
                    regionKey = "pg:" + plotGroupId;
                    edgeType = typeName.isEmpty() ? "town" : typeName;
                }
                else {
                    regionKey = "town:" + townName;
                    edgeType = "town";
                }
            }
            catch (Throwable ex) {
                regionKey = "town";
                edgeType = "town";
            }
        }

        return new BlockInfo(wc, tb, regionKey, edgeType, plotGroupId);
    }

    // --------------------------
    // Grid builders
    // --------------------------

    /**
     * Full rectangular grid, includes wilderness.
     */
    private static Map<Long, BlockInfo> buildBlockGrid(
            World bukkitWorld,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        Map<Long, BlockInfo> grid = new HashMap<>();
        if (bukkitWorld == null) {
            return grid;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                WorldCoord wc = new WorldCoord(bukkitWorld, x, z);
                grid.put(key(x, z), classify(wc));
            }
        }

        return grid;
    }

    /**
     * Selection-universe grid:
     * - selected coords => regionKey="selection", edgeType="selection"
     * - everything else in scan rectangle => regionKey="outside", edgeType="outside"
     *
     * This replicates the OLD selection behavior (inside/outside only).
     */
    private static Map<Long, BlockInfo> buildSelectionUniverseGrid(
            World bukkitWorld,
            Collection<WorldCoord> selectedCoords,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        Map<Long, BlockInfo> grid = new HashMap<>();
        if (bukkitWorld == null || selectedCoords == null || selectedCoords.isEmpty()) {
            return grid;
        }

        Set<Long> selectedSet = new HashSet<>();
        for (WorldCoord wc : selectedCoords) {
            if (wc == null) {
                continue;
            }
            // single-world call: ignore mismatched
            if (!Objects.equals(wc.getWorldName(), bukkitWorld.getName())) {
                continue;
            }
            selectedSet.add(key(wc.getX(), wc.getZ()));
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long k = key(x, z);
                WorldCoord wc = new WorldCoord(bukkitWorld, x, z);

                if (selectedSet.contains(k)) {
                    grid.put(k, new BlockInfo(wc, null, "selection", "selection", null));
                }
                else {
                    grid.put(k, new BlockInfo(wc, null, "outside", "outside", null));
                }
            }
        }

        return grid;
    }

    // --------------------------
    // Edge passes
    // --------------------------

    /**
     * Edge builder for normal towny classification mode.
     * (wilderness doesn't render; homeblock/plot/town do)
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

                // LEFT side
                if (shouldRenderEdge(left)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directTop = getBlock(grid, x, z - 1);
                    BlockInfo diagTop = getBlock(grid, x + 1, z - 1);
                    if (sameRegion(left, directTop)) {
                        start -= grow;
                        if (sameRegion(left, diagTop)) {
                            start -= grow;
                        }
                    }

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

                // RIGHT side
                if (shouldRenderEdge(right)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directTop = getBlock(grid, x + 1, z - 1);
                    BlockInfo diagTop = getBlock(grid, x, z - 1);
                    if (sameRegion(right, directTop)) {
                        start -= grow;
                        if (sameRegion(right, diagTop)) {
                            start -= grow;
                        }
                    }

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

                // TOP side
                if (shouldRenderEdge(top)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directLeft = getBlock(grid, x - 1, z);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z + 1);
                    if (sameRegion(top, directLeft)) {
                        start -= grow;
                        if (sameRegion(top, diagLeft)) {
                            start -= grow;
                        }
                    }

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

                // BOTTOM side
                if (shouldRenderEdge(bottom)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directLeft = getBlock(grid, x - 1, z + 1);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z);
                    if (sameRegion(bottom, directLeft)) {
                        start -= grow;
                        if (sameRegion(bottom, diagLeft)) {
                            start -= grow;
                        }
                    }

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
     * Edge builder for selection-universe mode (inside/outside only).
     * Only the "selection" side emits edges, matching old behavior.
     */
    private static ListTag buildSelectionUniverseEdgesFromGrid(
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

        // --- VERTICAL SCAN ---
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockInfo left = getBlock(grid, x, z);
                BlockInfo right = getBlock(grid, x + 1, z);

                if (areBlocksMerged(left, right)) {
                    continue;
                }

                double borderX = (x + 1) * size;
                double zBase = z * size;

                if (shouldRenderSelectionEdge(left)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directTop = getBlock(grid, x, z - 1);
                    BlockInfo diagTop = getBlock(grid, x + 1, z - 1);
                    if (sameRegion(left, directTop)) {
                        start -= grow;
                        if (sameRegion(left, diagTop)) {
                            start -= grow;
                        }
                    }

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

                if (shouldRenderSelectionEdge(right)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directTop = getBlock(grid, x + 1, z - 1);
                    BlockInfo diagTop = getBlock(grid, x, z - 1);
                    if (sameRegion(right, directTop)) {
                        start -= grow;
                        if (sameRegion(right, diagTop)) {
                            start -= grow;
                        }
                    }

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

        // --- HORIZONTAL SCAN ---
        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                BlockInfo top = getBlock(grid, x, z);
                BlockInfo bottom = getBlock(grid, x, z + 1);

                if (areBlocksMerged(top, bottom)) {
                    continue;
                }

                double borderZ = (z + 1) * size;
                double xBase = x * size;

                if (shouldRenderSelectionEdge(top)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directLeft = getBlock(grid, x - 1, z);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z + 1);
                    if (sameRegion(top, directLeft)) {
                        start -= grow;
                        if (sameRegion(top, diagLeft)) {
                            start -= grow;
                        }
                    }

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

                if (shouldRenderSelectionEdge(bottom)) {
                    double start = inset;
                    double end = size - inset;

                    BlockInfo directLeft = getBlock(grid, x - 1, z + 1);
                    BlockInfo diagLeft = getBlock(grid, x - 1, z);
                    if (sameRegion(bottom, directLeft)) {
                        start -= grow;
                        if (sameRegion(bottom, diagLeft)) {
                            start -= grow;
                        }
                    }

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

    // --------------------------
    // Public API
    // --------------------------

    /**
     * Towny-aware rectangular scan (wilderness included).
     * Wilderness doesn't render edges; town/plot/homeblock do.
     */
    public static ListTag buildVisualizerEdges(
            World bukkitWorld,
            int minX, int maxX,
            int minZ, int maxZ
    ) {
        if (bukkitWorld == null) {
            return new ListTag();
        }

        Map<Long, BlockInfo> grid = buildBlockGrid(
                bukkitWorld,
                minX - 1, maxX + 1,
                minZ - 1, maxZ + 1
        );

        return buildEdgesFromGrid(grid, bukkitWorld, minX, maxX, minZ, maxZ);
    }

    /**
     * Selection-universe visualizer (OLD behavior):
     * - inside cells => type="selection"
     * - outside cells => type="outside" (never rendered)
     * - edges only emitted from inside (selection) side
     * - all returned edges have type="selection"
     */
    public static ListTag buildSelectionVisualizerEdges(
            World bukkitWorld,
            Collection<WorldCoord> selectedCoords
    ) {
        if (bukkitWorld == null || selectedCoords == null || selectedCoords.isEmpty()) {
            return new ListTag();
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (WorldCoord wc : selectedCoords) {
            if (wc == null) {
                continue;
            }
            if (!Objects.equals(wc.getWorldName(), bukkitWorld.getName())) {
                continue;
            }
            int x = wc.getX();
            int z = wc.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        if (minX == Integer.MAX_VALUE) {
            return new ListTag();
        }

        // Pad bounds like before
        int scanMinX = minX - 1;
        int scanMaxX = maxX + 1;
        int scanMinZ = minZ - 1;
        int scanMaxZ = maxZ + 1;

        Map<Long, BlockInfo> grid = buildSelectionUniverseGrid(
                bukkitWorld,
                selectedCoords,
                scanMinX - 1, scanMaxX + 1,
                scanMinZ - 1, scanMaxZ + 1
        );

        // IMPORTANT: pass the padded scan bounds into the edge builder
        return buildSelectionUniverseEdgesFromGrid(grid, bukkitWorld, scanMinX, scanMaxX, scanMinZ, scanMaxZ);
    }
}
