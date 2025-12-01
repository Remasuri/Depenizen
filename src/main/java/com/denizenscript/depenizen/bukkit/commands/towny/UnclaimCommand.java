package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
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
        setSyntax("unclaim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>)");
        setRequiredArguments(2, 4);
        autoCompile();
    }

    // <--[command]
    // @Name Unclaim
    // @Syntax unclaim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>)
    // @Group Depenizen
    // @Plugin Depenizen, Towny
    // @Required 2
    // @Maximum 4
    // @Short Unclaims Towny plots, town blocks, or plot groups for a player.
    //
    // @Description
    // This command integrates with Towny to unclaim land for a specific player.
    //
    // The first argument chooses what to unclaim:
    // - town: unclaims Town blocks from the player's town (similar to /town unclaim).
    // - plot: unclaims individual plots from the player as a resident.
    // - group: unclaims all plots in a Towny plot group, resolved from one of the
    //   selected coordinates.
    //
    // The 'selection' argument is a list of locations that will be converted to Towny
    // coordinates and used to decide what land or group to unclaim.
    //
    // The 'target' argument specifies which player to use as the Towny resident/town
    // member. The target must be online and a valid Towny resident.
    //
    // For town unclaims, the 'admin' argument controls whether the command uses Towny's
    // normal unclaim checks, or forces the unclaim (similar to Towny's admin unclaim
    // options). When admin is false, Towny's own unclaimability checks are run and
    // any failure messages are passed through.
    //
    // For plot and group unclaims, the command intentionally never passes admin:true
    // into Towny's PlotClaim unclaim handler, as that flag is intended for admin
    // claims and not for unclaiming.
    //
    // All Towny operations are run asynchronously via the Towny scheduler.
    //
    // @Tags
    // <PlayerTag.towny.*>
    //
    // @Usage
    // Use to unclaim a region from the player's town.
    // - unclaim town selection:<server.selected_region> target:<player>
    //
    // @Usage
    // Use to unclaim a list of plots from a resident.
    // - unclaim plot selection:<[plot_locations]> target:<player>
    //
    // @Usage
    // Use to unclaim an entire plot group, based on any location in that group.
    // - unclaim group selection:<[some_location_in_group]> target:<player>
    //
    // -->

    public enum Action { TOWN, PLOT, GROUP }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action,
                                   @ArgName("selection") @ArgPrefixed @ArgDefaultNull @ArgSubType(LocationTag.class) List<LocationTag> selection,
                                   @ArgName("admin") @ArgPrefixed @ArgDefaultNull boolean admin,
                                   @ArgName("target") @ArgPrefixed @ArgDefaultNull PlayerTag target) {

        if (selection == null || selection.isEmpty()) {
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a selection!");
        }

        Towny towny = (Towny) Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Towny is not loaded or not enabled.");
        }

        if (target == null) {
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Must specify a target:<player>.");
        }

        Player player = target.getPlayerEntity();
        if (player == null || !player.isOnline()) {
            scriptEntry.setFinished(true);
            throw new InvalidArgumentsRuntimeException("Target player must be online.");
        }

        TownyAPI api = TownyAPI.getInstance();
        Resident resident = api.getResident(player);
        if (resident == null) {
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
                // NOTE: We *never* pass admin=true into PlotClaim for unclaims, because its admin flag
                // is for "admin give" (claims), not for unclaiming.
                runnable = new PlotClaim(towny, player, resident, coords, false, false, false);
            }
            case TOWN -> {
                // Town unclaim – claim=false.
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                // Use Towny's own unclaimability checks when not in admin mode.
                if (!admin) {
                    for (WorldCoord coord : coords) {
                        try {
                            api.testTownUnclaimOrThrow(town, coord);
                        }
                        catch (TownyException ex) {
                            scriptEntry.setFinished(true);
                            throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                        }
                    }
                }

                // forced = admin (so admin can unclaim other towns' blocks if desired)
                runnable = new TownClaim(towny, player, town, coords, false, false, admin);
            }
            case GROUP -> {
                // Group unclaim: resolve the PlotGroup from the first coord and unclaim all its plots
                // by calling residentUnclaim on each block (no groupClaim flag, that's for purchases).
                WorldCoord first = coords.get(0);
                TownBlock tb = first.getTownBlockOrNull();
                if (tb == null || !tb.hasPlotObjectGroup()) {
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("First selected location is not part of a plot group.");
                }

                PlotGroup group = tb.getPlotObjectGroup();
                List<WorldCoord> groupCoords = group.getTownBlocks().stream()
                        .map(TownBlock::getWorldCoord)
                        .collect(Collectors.toList());

                if (groupCoords.isEmpty()) {
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Plot group has no plots to unclaim.");
                }

                // Again, do not pass admin=true to PlotClaim for unclaims.
                runnable = new PlotClaim(towny, player, resident, groupCoords, false, false, false);
            }
            default -> {
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Unknown towny action: " + action);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(towny, runnable);
        scriptEntry.setFinished(true);
    }
}
