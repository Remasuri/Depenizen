package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.depenizen.bukkit.objects.towny.WorldCoordTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.scripts.commands.generator.ArgSubType;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.PlotClaim;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClaimCommand extends AbstractCommand {

    public ClaimCommand() {
        setName("claim");
        setSyntax("claim [town/plot/group] (selection:<list[<worldcoord>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>) (unclaim:<true/false>)");
        setRequiredArguments(2, 6);
        autoCompile();
    }

    // <--[command]
    // @Name Claim
    // @Syntax claim [town/plot/group] (selection:<list[<worldcoord>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>) (unclaim:<true/false>)
    // @Group Depenizen
    // @Plugin Depenizen, Towny
    // @Required 2
    // @Maximum 6
    // @Short Claims (or unclaims) Towny plots, town blocks, or plot groups for a player.
    //
    // @Description
    // Mirrors Towny's TownCommand claim/unclaim behavior (pre-events, economy) for town claims/unclaims,
    // but without sending chat output. Plot/group operations are delegated to Towny's PlotClaim task.
    //
    // Use unclaim:true to unclaim using the same command.
    //
    // Saves:
    // - result: "success" or "failure"
    // - selection: list[WorldCoordTag] (the selection that will actually be used)
    // - cost: ElementTag (double)
    // - cause: ElementTag (failure reason key or message)
    // -->

    public enum Action { TOWN, PLOT, GROUP }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action,
                                   @ArgName("selection") @ArgPrefixed @ArgDefaultNull @ArgSubType(WorldCoordTag.class) List<WorldCoordTag> selection,
                                   @ArgName("admin") @ArgPrefixed @ArgDefaultNull boolean admin,
                                   @ArgName("target") @ArgPrefixed @ArgDefaultNull PlayerTag target,
                                   @ArgName("outpost") @ArgPrefixed @ArgDefaultNull boolean outpost,
                                   @ArgName("unclaim") @ArgPrefixed @ArgDefaultNull boolean unclaim) {

        if (selection == null || selection.isEmpty()) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.saveObject("selection", new ListTag());
            scriptEntry.saveObject("cost", new ElementTag(0));
            scriptEntry.saveObject("cause", new ElementTag("Must specify a selection!"));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a selection!");
        }

        Towny towny = (Towny) Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.saveObject("selection", new ListTag());
            scriptEntry.saveObject("cost", new ElementTag(0));
            scriptEntry.saveObject("cause", new ElementTag("Towny is not loaded or not enabled."));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Towny is not loaded or not enabled.");
        }

        if (target == null) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.saveObject("selection", new ListTag());
            scriptEntry.saveObject("cost", new ElementTag(0));
            scriptEntry.saveObject("cause", new ElementTag("Must specify a target:<player>."));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a target:<player>.");
        }

        Player player = target.getPlayerEntity();
        if (player == null || !player.isOnline()) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.saveObject("selection", new ListTag());
            scriptEntry.saveObject("cost", new ElementTag(0));
            scriptEntry.saveObject("cause", new ElementTag("Target player must be online."));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Target player must be online.");
        }

        TownyAPI api = TownyAPI.getInstance();
        Resident resident = api.getResident(player);
        if (resident == null) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.saveObject("selection", new ListTag());
            scriptEntry.saveObject("cost", new ElementTag(0));
            scriptEntry.saveObject("cause", new ElementTag("Target player is not a Towny resident."));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Target player is not a Towny resident.");
        }

        // Convert WorldCoordTag list -> WorldCoord list
        List<WorldCoord> coords = new ArrayList<>(selection.size());
        for (WorldCoordTag wcTag : selection) {
            if (wcTag == null) {
                scriptEntry.saveObject("result", new ElementTag("failure"));
                scriptEntry.saveObject("selection", new ListTag());
                scriptEntry.saveObject("cost", new ElementTag(0));
                scriptEntry.saveObject("cause", new ElementTag("Selection contains a null WorldCoordTag."));
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Selection contains a null WorldCoordTag.");
            }
            coords.add(wcTag.worldCoord);
        }

        Runnable runnable;

        switch (action) {
            case PLOT -> {
                // Plot claim/unclaim
                runnable = new PlotClaim(towny, player, resident, coords, !unclaim, admin, false);

                scriptEntry.saveObject("selection", toWorldCoordTagList(coords));
                scriptEntry.saveObject("cost", new ElementTag(0));
                scriptEntry.saveObject("cause", new ElementTag(""));
            }
            case GROUP -> {
                if (!unclaim) {
                    runnable = new PlotClaim(towny, player, resident, coords, true, admin, true);

                    scriptEntry.saveObject("selection", toWorldCoordTagList(coords));
                    scriptEntry.saveObject("cost", new ElementTag(0));
                    scriptEntry.saveObject("cause", new ElementTag(""));
                }
                else {
                    WorldCoord first = coords.get(0);
                    TownBlock tb = first.getTownBlockOrNull();
                    if (tb == null || !tb.hasPlotObjectGroup()) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.saveObject("selection", toWorldCoordTagList(coords));
                        scriptEntry.saveObject("cost", new ElementTag(0));
                        scriptEntry.saveObject("cause", new ElementTag("First selected worldcoord is not part of a plot group."));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("First selected worldcoord is not part of a plot group.");
                    }
                    PlotGroup group = tb.getPlotObjectGroup();
                    List<WorldCoord> groupCoords = group.getTownBlocks().stream()
                            .map(TownBlock::getWorldCoord)
                            .toList();

                    if (groupCoords.isEmpty()) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.saveObject("selection", new ListTag());
                        scriptEntry.saveObject("cost", new ElementTag(0));
                        scriptEntry.saveObject("cause", new ElementTag("Plot group has no plots to unclaim."));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("Plot group has no plots to unclaim.");
                    }

                    runnable = new PlotClaim(towny, player, resident, groupCoords, false, admin, false);

                    scriptEntry.saveObject("selection", toWorldCoordTagList(groupCoords));
                    scriptEntry.saveObject("cost", new ElementTag(0));
                    scriptEntry.saveObject("cause", new ElementTag(""));
                }
            }
            case TOWN -> {
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.saveObject("selection", toWorldCoordTagList(coords));
                    scriptEntry.saveObject("cost", new ElementTag(0));
                    scriptEntry.saveObject("cause", new ElementTag("Target player does not have a town."));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                if (!unclaim) {
                    VetResult vet = DepenizenTownyCommandHelper.vetTownClaim(town, coords, player, outpost);

                    // Always expose vet outputs
                    scriptEntry.saveObject("selection", toWorldCoordTagList(vet.validWorldCoords));
                    scriptEntry.saveObject("cost", new ElementTag(vet.cost));
                    scriptEntry.saveObject("cause", new ElementTag(vet.error == null ? "" : vet.error));

                    if (!vet.result) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(vet.error);
                    }

                    try {
                        DepenizenTownyCommandHelper.verifyTownClaim(player, town, vet.validWorldCoords, outpost, admin);
                    }
                    catch (TownyException ex) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.saveObject("cause", new ElementTag(ex.getMessage(player)));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                    }

                    runnable = new TownClaim(towny, player, town, vet.validWorldCoords, outpost, true, admin);
                }
                else {
                    VetResult vet = DepenizenTownyCommandHelper.vetTownUnclaim(town, coords);

                    // Always expose vet outputs
                    scriptEntry.saveObject("selection", toWorldCoordTagList(vet.validWorldCoords));
                    scriptEntry.saveObject("cost", new ElementTag(vet.cost));
                    scriptEntry.saveObject("cause", new ElementTag(vet.error == null ? "" : vet.error));

                    if (!vet.result) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(vet.error);
                    }

                    try {
                        DepenizenTownyCommandHelper.verifyTownUnclaim(player, town, resident, vet.validWorldCoords, admin);
                    }
                    catch (TownyException ex) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.saveObject("cause", new ElementTag(ex.getMessage(player)));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                    }

                    runnable = new TownClaim(towny, player, town, vet.validWorldCoords, outpost, false, admin);
                }
            }
            default -> {
                scriptEntry.saveObject("result", new ElementTag("failure"));
                scriptEntry.saveObject("selection", toWorldCoordTagList(coords));
                scriptEntry.saveObject("cost", new ElementTag(0));
                scriptEntry.saveObject("cause", new ElementTag("Unknown towny action: " + action));
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Unknown towny action: " + action);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(towny, runnable);
        scriptEntry.saveObject("result", new ElementTag("success"));
        scriptEntry.setFinished(true);
    }

    private static ListTag toWorldCoordTagList(List<WorldCoord> coords) {
        ListTag list = new ListTag();
        if (coords == null) {
            return list;
        }
        for (WorldCoord wc : coords) {
            list.addObject(new WorldCoordTag(wc));
        }
        return list;
    }
}
