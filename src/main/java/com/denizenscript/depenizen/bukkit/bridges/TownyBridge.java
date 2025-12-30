package com.denizenscript.depenizen.bukkit.bridges;

import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.depenizen.bukkit.commands.towny.ClaimCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.PlotGroupCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.TownCommand;
import com.denizenscript.depenizen.bukkit.events.towny.*;
import com.denizenscript.depenizen.bukkit.objects.towny.*;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyCuboidProperties;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyLocationProperties;
import com.denizenscript.depenizen.bukkit.Bridge;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyPlayerProperties;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyWorldProperties;
import com.denizenscript.depenizen.bukkit.utilities.towny.TownyVisualizerUtils;
import com.denizenscript.depenizen.bukkit.properties.utilities.BukkitPlayerProperties;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.denizenscript.denizen.objects.CuboidTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.tags.TagRunnable;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.properties.PropertyParser;
import com.denizenscript.denizencore.tags.ReplaceableTagEvent;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownySettings.TownLevel;
import com.palmergames.bukkit.towny.utils.ProximityUtil;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TownyBridge extends Bridge {

    @Override
    public void init() {
        ObjectFetcher.registerWithObjectFetcher(TownTag.class, TownTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(NationTag.class, NationTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(TownBlockTag.class,TownBlockTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(PlotGroupTag.class, PlotGroupTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(WorldCoordTag.class, WorldCoordTag.tagProcessor);
        PropertyParser.registerProperty(TownyPlayerProperties.class, PlayerTag.class);
        PropertyParser.registerProperty(BukkitPlayerProperties.class, PlayerTag.class);
        TownyLocationProperties.register();
        PropertyParser.registerProperty(TownyCuboidProperties.class, CuboidTag.class);
        PropertyParser.registerProperty(TownyWorldProperties.class, WorldTag.class);
        ScriptEvent.registerScriptEvent(PlayerClaimsPlotScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerCreatesTownScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerEntersTownScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerExitsTownScriptEvent.class);
        ScriptEvent.registerScriptEvent(TownCreatedScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerJoinsTownScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerLeavesTownScriptEvent.class);
        ScriptEvent.registerScriptEvent(TownLevelDecreasedScriptEvent.class);
        ScriptEvent.registerScriptEvent(TownLevelIncreasedScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlotGroupCreatedScriptEvent.class);
        ScriptEvent.registerScriptEvent(TownyNewDayScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerClaimsTownPlotScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerUnclaimsTownPlotScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlayerUnclaimsPlotScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlotGroupRemovedScriptEvent.class);
        ScriptEvent.registerScriptEvent(PlotGroupUpdatedScriptEvent.class);
        ScriptEvent.registerScriptEvent(TownDeletedScriptEvent.class);
        TagManager.registerTagHandler(ObjectTag.class, "worldcoord", (attribute) -> {
            if(attribute.hasParam()){
                return WorldCoordTag.valueOf(attribute.getParam(), attribute.context);
            }
            return null;
        });
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                townyTagEvent(event);
            }
        }, "towny");
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                townTagEvent(event);
            }
        }, "town");
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                nationTagEvent(event);
            }
        }, "nation");
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                townBlockTagEvent(event);
            }
        }, "townblock");
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                plotGroupTagEvent(event);
            }
        }, "plotgroup");
        DenizenCore.commandRegistry.registerCommand(PlotGroupCommand.class);
        DenizenCore.commandRegistry.registerCommand(ClaimCommand.class);
        DenizenCore.commandRegistry.registerCommand(TownCommand.class);
    }

    public void townyTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes().fulfill(1);

        if(attribute.startsWith("town_start_cost")){
            event.setReplacedObject(new ElementTag(TownySettings.getNewTownPrice()));
            return;
        }
        if (attribute.startsWith("reclaim_cost")){
            event.setReplacedObject(new ElementTag(TownySettings.getEcoPriceReclaimTown()));
            return;
        }
        // <--[tag]
        // @attribute <towny.list_towns[(<world>)]>
        // @returns ListTag(TownTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of all towns. Optionally specify a world name.
        // -->
        if (attribute.startsWith("list_towns")) {
            ListTag towns = new ListTag();
            if (attribute.hasParam()) {
                TownyWorld world = TownyAPI.getInstance().getTownyWorld(attribute.getParam().replace("w@", ""));
                if (world == null) {
                    attribute.echoError("World specified is not a registered towny world!");
                    return;
                }
                for (Town town : world.getTowns().values()) {
                    towns.addObject(new TownTag(town));
                }
            }
            else {
                for (Town town : TownyUniverse.getInstance().getTowns()) {
                    towns.addObject(new TownTag(town));
                }
            }
            event.setReplacedObject(towns.getObjectAttribute(attribute.fulfill(1)));
            return;
        }
        // <--[tag]
        // @attribute <towny.town_block_size>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the size (in blocks) of a single Towny townblock (plot),
        // as configured by Towny's 'town_block_size' setting.
        // This is the grid size used to calculate Towny coordinates.
        // -->
        if (attribute.startsWith("town_block_size")) {
            ElementTag size = new ElementTag(TownySettings.getTownBlockSize());
            event.setReplacedObject(size.getObjectAttribute(attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <towny.nations>
        // @returns ListTag(NationTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of all nations.
        // -->
        if (attribute.startsWith("nations")) {
            ListTag nations = new ListTag();
            for (Nation nation : TownyUniverse.getInstance().getNations()) {
                nations.addObject(new NationTag(nation));
            }
            event.setReplacedObject(nations.getObjectAttribute(attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <towny.town_levels>
        // @returns ListTag(MapTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of Towny town-level definitions from the config.
        //
        // Each entry in the list is a MapTag with keys:
        // - townblock_limit      (ElementTag(Number))
        // - townblock_buy_bonus_limit (ElementTag(Number))
        // - upkeep_modifier      (ElementTag(Decimal))
        // - outpost_limit        (ElementTag(Number))
        // - debt_cap_modifier    (ElementTag(Decimal))
        // - name_prefix          (ElementTag)
        // - name_postfix         (ElementTag)
        // - mayor_prefix         (ElementTag)
        // - mayor_postfix        (ElementTag)
        // -->
        if (attribute.startsWith("town_levels")) {
            ListTag levelsList = new ListTag();

            for (TownLevel level : TownySettings.getConfigTownLevel().values()) {
                MapTag map = new MapTag();

                // core numeric stuff
                map.putObject("townblock_limit", new ElementTag(level.townBlockLimit()));
                map.putObject("townblock_buy_bonus_limit", new ElementTag(level.townBlockBuyBonusLimit()));
                map.putObject("upkeep_modifier", new ElementTag(level.upkeepModifier()));
                map.putObject("outpost_limit", new ElementTag(level.townOutpostLimit()));
                map.putObject("debt_cap_modifier", new ElementTag(level.debtCapModifier()));

                // naming/titles
                map.putObject("name_prefix", new ElementTag(level.namePrefix()));
                map.putObject("name_postfix", new ElementTag(level.namePostfix()));
                map.putObject("mayor_prefix", new ElementTag(level.mayorPrefix()));
                map.putObject("mayor_postfix", new ElementTag(level.mayorPostfix()));

                levelsList.addObject(map);
            }

            event.setReplacedObject(levelsList.getObjectAttribute(attribute.fulfill(1)));
            return;
        }

// <--[tag]
// @attribute <towny.worldcoord_visualizer_lines[<list>]>
// @returns ListTag(MapTag)
// @plugin Depenizen, Towny
// @description
// Given a list of WorldCoordTags, returns a list of visualizer edge maps
// outlining the *selection* of worldcoords.
//
// Coords inside the selection are classified normally (town/plot/homeblock/wilderness).
// Borders against non-selected coords are emitted with type="outside".
// Wilderness edges can be emitted (type="wilderness") when selected wilderness borders town,
// depending on the visualizer utils' selection behavior.
//
// Each edge is a MapTag:
//   start=LocationTag
//   vector=LocationTag
//   type=ElementTag(<edge type>)
//   (optional) plotgroup=ElementTag(<uuid>)
//
// Example:
// - define coords <player.flag[radar_selection_coords]||<list>>
// - define edges <towny.worldcoord_visualizer_lines[<[coords]>]>
// -->
        if (attribute.startsWith("worldcoord_visualizer_lines")) {
            if (!attribute.hasParam()) {
                attribute.echoError("towny.worldcoord_visualizer_lines[...] requires a list of WorldCoordTags.");
                return;
            }

            ListTag list = attribute.getParamObject().asType(ListTag.class, attribute.context);
            if (list == null) {
                attribute.echoError("towny.worldcoord_visualizer_lines[...] parameter must be a list of WorldCoordTags.");
                return;
            }

            List<WorldCoord> selection = new ArrayList<>();

            for (ObjectTag obj : list.objectForms) {
                if (obj == null) {
                    continue;
                }
                WorldCoordTag wct = obj.asType(WorldCoordTag.class, attribute.context);
                if (wct == null || wct.worldCoord == null) {
                    continue;
                }
                selection.add(wct.worldCoord);
            }

            // Fallback: single WorldCoordTag passed instead of a list
            if (selection.isEmpty()) {
                WorldCoordTag single = attribute.getParamObject().asType(WorldCoordTag.class, attribute.context);
                if (single != null && single.worldCoord != null) {
                    selection.add(single.worldCoord);
                }
            }

            if (selection.isEmpty()) {
                event.setReplacedObject(new ListTag().getObjectAttribute(attribute.fulfill(1)));
                return;
            }

            // Resolve world from first coord (single-world behavior)
            World bukkitWorld = null;
            for (WorldCoord wc : selection) {
                if (wc == null) {
                    continue;
                }
                try {
                    bukkitWorld = wc.getBukkitWorld();
                }
                catch (Throwable ex) {
                    // ignore
                }
                if (bukkitWorld != null) {
                    break;
                }
            }

            if (bukkitWorld == null) {
                event.setReplacedObject(new ListTag().getObjectAttribute(attribute.fulfill(1)));
                return;
            }

            // IMPORTANT for "no group-by-world": drop coords that aren't in this world
            String worldName = bukkitWorld.getName();
            selection.removeIf(wc -> wc == null || !worldName.equals(wc.getWorldName()));

            if (selection.isEmpty()) {
                event.setReplacedObject(new ListTag().getObjectAttribute(attribute.fulfill(1)));
                return;
            }

            // Now selection behaves like before (inside/outside only)
            ListTag edges = TownyVisualizerUtils.buildSelectionVisualizerEdges(bukkitWorld, selection);

            event.setReplacedObject(edges.getObjectAttribute(attribute.fulfill(1)));
            return;
        }


    }

    public void townTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes();

        // <--[tag]
        // @attribute <town[<name>]>
        // @returns TownTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the town by the input name.
        // -->
        if (attribute.hasParam()) {
            TownTag town;
            if (TownTag.matches(attribute.getParam())) {
                town = attribute.paramAsType(TownTag.class);
            }
            else {
                attribute.echoError("Could not match '" + attribute.getParam() + "' to a valid town!");
                return;
            }
            if (town != null) {
                event.setReplacedObject(town.getObjectAttribute(attribute.fulfill(1)));
            }
            else {
                attribute.echoError("Unknown town '" + attribute.getParam() + "' for town[] tag.");
            }
        }
    }

    public void townBlockTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes();

        // <--[tag]
        // @attribute <townblock[<id>]>
        // @returns TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the TownBlockTag for the given identifier.
        //
        // The identifier can be:
        // - The standard TownBlock ID: "world;x;z"
        // - A list-style value: "world|x|z" (for example from <location.towny_grid_location>)
        // -->
        if (attribute.hasParam()) {
            TownBlockTag townblock;
            if (TownBlockTag.matches(attribute.getParam())) {
                townblock = attribute.paramAsType(TownBlockTag.class);
            }
            else {
                attribute.echoError("Could not match '" + attribute.getParam() + "' to a valid townblock!");
                return;
            }
            if (townblock != null) {
                event.setReplacedObject(townblock.getObjectAttribute(attribute.fulfill(1)));
            }
            else {
                attribute.echoError("Unknown townblock '" + attribute.getParam() + "' for townblock[] tag.");
            }
        }
    }

    public void plotGroupTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes();

        // <--[tag]
        // @attribute <plotgroup[<id>]>
        // @returns PlotGroupTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the PlotGroupTag by the given identifier.
        // -->
        if (attribute.hasParam()) {
            PlotGroupTag group;
            if (PlotGroupTag.matches(attribute.getParam())) {
                group = attribute.paramAsType(PlotGroupTag.class);
            }
            else {
                attribute.echoError("Could not match '" + attribute.getParam() + "' to a valid plotgroup!");
                return;
            }

            if (group != null) {
                event.setReplacedObject(group.getObjectAttribute(attribute.fulfill(1)));
            }
            else {
                attribute.echoError("Unknown plotgroup '" + attribute.getParam() + "' for plotgroup[] tag.");
            }
        }
    }

    public void nationTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes();

        // <--[tag]
        // @attribute <nation[<name>]>
        // @returns NationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the nation by the input name.
        // -->
        if (attribute.hasParam()) {
            NationTag nation;
            if (NationTag.matches(attribute.getParam())) {
                nation = attribute.paramAsType(NationTag.class);
            }
            else {
                attribute.echoError("Could not match '" + attribute.getParam() + "' to a valid nation!");
                return;
            }
            if (nation != null) {
                event.setReplacedObject(nation.getObjectAttribute(attribute.fulfill(1)));
            }
            else {
                attribute.echoError("Unknown nation '" + attribute.getParam() + "' for nation[] tag.");
            }
        }

    }
}








