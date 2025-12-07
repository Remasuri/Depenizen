package com.denizenscript.depenizen.bukkit.bridges;

import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.depenizen.bukkit.commands.noteblockapi.NBSCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.ClaimCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.PlotGroupCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.TownCommand;
import com.denizenscript.depenizen.bukkit.commands.towny.UnclaimCommand;
import com.denizenscript.depenizen.bukkit.events.towny.*;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyCuboidProperties;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyLocationProperties;
import com.denizenscript.depenizen.bukkit.objects.towny.NationTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.denizenscript.depenizen.bukkit.Bridge;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyPlayerProperties;
import com.denizenscript.depenizen.bukkit.properties.towny.TownyWorldProperties;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
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
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownySettings.TownLevel;
public class TownyBridge extends Bridge {

    @Override
    public void init() {
        ObjectFetcher.registerWithObjectFetcher(TownTag.class, TownTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(NationTag.class, NationTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(TownBlockTag.class,TownBlockTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(PlotGroupTag.class, PlotGroupTag.tagProcessor);
        PropertyParser.registerProperty(TownyPlayerProperties.class, PlayerTag.class);
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
        DenizenCore.commandRegistry.registerCommand(UnclaimCommand.class);
        DenizenCore.commandRegistry.registerCommand(TownCommand.class);
    }

    public void townyTagEvent(ReplaceableTagEvent event) {
        Attribute attribute = event.getAttributes().fulfill(1);

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
        //
        // Example usage:
        // - Get the minimum residents required for the second town level:
        //   <towny.town_levels.get[1].get[num_residents]>
        // - Get the townblock limit of the level that needs 10 residents:
        //   <towny.town_levels.parse_tag[
        //       <[levels].filter[num_residents.equals[10]].first.get[townblock_limit]>
        //   ]>
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

                // If you ever want block-type limits exposed, you could also serialize:
                // level.getTownBlockTypeLimits() -> another MapTag or ListTag.

                levelsList.addObject(map);
            }

            event.setReplacedObject(levelsList.getObjectAttribute(attribute.fulfill(1)));
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
        //
        // Examples:
        // - <townblock[world;12;34]>
        // - <townblock[<player.location.towny_grid_location>]>
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
        //
        // The identifier is usually the plotgroup UUID, for example:
        // - <plotgroup[123e4567-e89b-12d3-a456-426614174000]>
        //
        // It uses PlotGroupTag.matches/valueOf internally, so any format those
        // accept will work here.
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
