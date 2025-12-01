package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;

import java.util.List;
import java.util.UUID;

//TODO: Expand with capabilities to have different identifiers. Maybe Name can become optional? (Randomly generate name?)
public class PlotGroupCommand extends AbstractCommand {

    public PlotGroupCommand() {
        setName("plotgroup");
        setSyntax("plotgroup [create/delete] (town:<town>) (name:<#>) (plotgroup:<plotgroup>) (townblocks:<list[<townblock>]>)");
        setRequiredArguments(2, 4);
        autoCompile();
    }

    public enum Action { CREATE, DELETE }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action,
                                   @ArgName("town") @ArgPrefixed @ArgDefaultNull TownTag town,
                                   @ArgName("name") @ArgPrefixed @ArgDefaultNull String name,
                                   @ArgName("plotgroup") @ArgPrefixed @ArgDefaultNull PlotGroupTag plotgroupTag,
                                   @ArgName("townblocks") @ArgPrefixed @ArgDefaultNull @ArgSubType(TownBlockTag.class) List<TownBlockTag> townblocks) {

        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();

        switch (action) {

            case CREATE -> {
                if (town == null || name == null || name.isBlank()) {
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Must specify a town and name when creating a plotgroup!");
                }

                if (townblocks != null) {
                    for (TownBlockTag townBlockTag : townblocks) {
                        TownBlock townBlock = townBlockTag.getTownBlock();
                        if (townBlock.getTownOrNull() != town.getTown()) {
                            scriptEntry.setFinished(true);
                            throw new InvalidArgumentsRuntimeException("townblocks must all be from the specified town!");
                        }
                    }
                }

                // This might not be necessary, since Towny might no longer have this restriction
                // in newer versions for internal data due to the fact that plotgroups now use UUIDs
                if (town.getTown().hasPlotGroupName(name)) {
                    Debug.echoError("Name already exists in town!");
                    scriptEntry.setFinished(true);
                    return;
                }

                UUID uuid = UUID.randomUUID();
                PlotGroup plotGroup = new PlotGroup(uuid, name, town.getTown());

                if (townblocks != null) {
                    for (TownBlockTag townBlockTag : townblocks) {
                        TownBlock townBlock = townBlockTag.getTownBlock();
                        plotGroup.addTownBlock(townBlock);
                        townBlock.setPlotObjectGroup(plotGroup);
                        dataSource.saveTownBlock(townBlock);
                    }
                }

                town.getTown().addPlotGroup(plotGroup);
                universe.registerGroup(plotGroup);
                dataSource.saveTown(town.getTown());
                dataSource.savePlotGroup(plotGroup);

                scriptEntry.saveObject("created_plotgroup", new PlotGroupTag(plotGroup));

                scriptEntry.setFinished(true);
            }

            case DELETE -> {
                PlotGroup plotGroup;
                Town owningTown;

                if (plotgroupTag != null) {
                    plotGroup = plotgroupTag.getPlotGroup();
                    if (plotGroup == null) {
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("Invalid plotgroup specified!");
                    }
                    owningTown = plotGroup.getTown();

                    if (town != null && owningTown != town.getTown()) {
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("Specified town does not match the plotgroup's town!");
                    }
                }
                else {
                    if (town == null || name == null || name.isBlank()) {
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("Must specify either plotgroup:<plotgroup> or town:<town> name:<name> to delete a plotgroup!");
                    }

                    owningTown = town.getTown();
                    plotGroup = owningTown.getPlotObjectGroupFromName(name);
                    if (plotGroup == null) {
                        Debug.echoError("Plot group with name " + name + " does not exist in town!");
                        scriptEntry.setFinished(true);
                        return;
                    }
                }

                for (TownBlock townBlock : plotGroup.getTownBlocks()) {
                    townBlock.removePlotObjectGroup();
                    dataSource.saveTownBlock(townBlock);
                }

                owningTown.removePlotGroup(plotGroup);
                universe.unregisterGroup(plotGroup.getUUID());
                dataSource.saveTown(owningTown);
                dataSource.removePlotGroup(plotGroup);

                scriptEntry.setFinished(true);
            }
        }
    }
}
