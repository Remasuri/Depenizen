package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
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

import java.util.List;
import java.util.stream.Collectors;

public class UnclaimCommand extends AbstractCommand {

    public UnclaimCommand() {
        setName("unclaim");
        setSyntax("unclaim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>)");
        setRequiredArguments(2, 5);
        autoCompile();
    }

    // <--[command]
    // @Name Unclaim
    // @Syntax unclaim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>)
    // @Group Depenizen
    // @Plugin Depenizen, Towny
    // @Required 2
    // @Maximum 4
    // @Short Unclaims Towny plots, town blocks, or plot groups for a player.
    //
    // @Description
    // Mirrors Towny's TownCommand unclaim behavior (pre-events, economy) for town unclaims,
    // but without sending chat output. Plot/group unclaims are delegated to PlotClaim.
    //
    // Saves:
    // - result: "success" or "failure"
    //
    // -->

    public enum Action { TOWN, PLOT, GROUP }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action,
                                   @ArgName("selection") @ArgPrefixed @ArgDefaultNull @ArgSubType(LocationTag.class) List<LocationTag> selection,
                                   @ArgName("admin") @ArgPrefixed @ArgDefaultNull boolean admin,
                                   @ArgName("target") @ArgPrefixed @ArgDefaultNull PlayerTag target,
                                   @ArgName("outpost") @ArgPrefixed @ArgDefaultNull boolean outpost) {

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

        List<WorldCoord> coords = selection.stream()
                .map(WorldCoord::parseWorldCoord)
                .collect(Collectors.toList());

        Runnable runnable;

        switch (action) {
            case PLOT -> {
                // Plot unclaim – claim=false, groupClaim=false
                // (admin flag ignored for unclaims as in your original design)
                runnable = new PlotClaim(towny, player, resident, coords, false, false, false);
            }
            case TOWN -> {
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                try {
                    // Town-like unclaim checks, pre-event, and unclaim economy (for non-admin).
                    DepenizenTownyCommandHelper.verifyTownUnclaim(player, town, resident, coords, admin);
                }
                catch (TownyException ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                }

                // Town unclaim – claim=false, forced=admin
                runnable = new TownClaim(towny, player, town, coords, outpost, false, admin);
            }
            case GROUP -> {
                // Group unclaim: resolve the PlotGroup from the first coord and unclaim all its plots.
                WorldCoord first = coords.get(0);
                TownBlock tb = first.getTownBlockOrNull();
                if (tb == null || !tb.hasPlotObjectGroup()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("First selected location is not part of a plot group.");
                }

                PlotGroup group = tb.getPlotObjectGroup();
                List<WorldCoord> groupCoords = group.getTownBlocks().stream()
                        .map(TownBlock::getWorldCoord)
                        .collect(Collectors.toList());

                if (groupCoords.isEmpty()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Plot group has no plots to unclaim.");
                }

                runnable = new PlotClaim(towny, player, resident, groupCoords, false, false, false);
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
