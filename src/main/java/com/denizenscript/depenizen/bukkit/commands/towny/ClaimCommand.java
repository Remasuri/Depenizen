package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.depenizen.bukkit.objects.towny.WorldCoordTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
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
    //
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
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a selection!");
        }

        Towny towny = (Towny) Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Towny is not loaded or not enabled.");
        }

        if (target == null) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a target:<player>.");
        }

        Player player = target.getPlayerEntity();
        if (player == null || !player.isOnline()) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Target player must be online.");
        }

        TownyAPI api = TownyAPI.getInstance();
        Resident resident = api.getResident(player);
        if (resident == null) {
            scriptEntry.saveObject("result", new ElementTag("failure"));
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Target player is not a Towny resident.");
        }

        // Convert WorldCoordTag list -> WorldCoord list
        List<WorldCoord> coords = new ArrayList<>(selection.size());
        for (WorldCoordTag wcTag : selection) {
            if (wcTag == null) {
                scriptEntry.saveObject("result", new ElementTag("failure"));
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Selection contains a null WorldCoordTag.");
            }
            coords.add(wcTag.worldCoord);
        }

        Runnable runnable;

        switch (action) {
            case PLOT -> {
                // Plot claim/unclaim
                // PlotClaim signature: (plugin, player, resident, selection, claim, forced, groupClaim)
                runnable = new PlotClaim(towny, player, resident, coords, !unclaim, admin, false);
            }
            case GROUP -> {
                if (!unclaim) {
                    // Group claim
                    runnable = new PlotClaim(towny, player, resident, coords, true, admin, true);
                }
                else {
                    // Group unclaim: resolve group from first coord and unclaim entire group.
                    WorldCoord first = coords.get(0);
                    TownBlock tb = first.getTownBlockOrNull();
                    if (tb == null || !tb.hasPlotObjectGroup()) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("First selected worldcoord is not part of a plot group.");
                    }
                    PlotGroup group = tb.getPlotObjectGroup();
                    List<WorldCoord> groupCoords = group.getTownBlocks().stream()
                            .map(TownBlock::getWorldCoord)
                            .toList();

                    if (groupCoords.isEmpty()) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException("Plot group has no plots to unclaim.");
                    }

                    runnable = new PlotClaim(towny, player, resident, groupCoords, false, admin, false);
                }
            }
            case TOWN -> {
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                if (!unclaim) {
                    try {
                        DepenizenTownyCommandHelper.verifyTownClaim(player, town, coords, outpost, admin);
                    }
                    catch (TownyException ex) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                    }
                    // TownClaim signature: (plugin, player, town, selection, outpost, claim=true, forced=admin)
                    runnable = new TownClaim(towny, player, town, coords, outpost, true, admin);
                }
                else {
                    try {
                        DepenizenTownyCommandHelper.verifyTownUnclaim(player, town, resident, coords, admin);
                    }
                    catch (TownyException ex) {
                        scriptEntry.saveObject("result", new ElementTag("failure"));
                        scriptEntry.setFinished(true);
                        throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                    }
                    // Town unclaim
                    runnable = new TownClaim(towny, player, town, coords, outpost, false, admin);
                }
            }
            default -> {
                scriptEntry.saveObject("result", new ElementTag("failure"));
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Unknown towny action: " + action);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(towny, runnable);
        scriptEntry.saveObject("result", new ElementTag("success"));
        scriptEntry.setFinished(true);
    }
}
