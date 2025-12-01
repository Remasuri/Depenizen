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
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.PlotClaim;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ClaimCommand extends AbstractCommand {

    public ClaimCommand() {
        setName("claim");
        setSyntax("claim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>)");
        setRequiredArguments(2, 4);
        autoCompile();
    }

    // <--[command]
    // @Name Claim
    // @Syntax claim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>)
    // @Group Depenizen
    // @Plugin Depenizen, Towny
    // @Required 2
    // @Maximum 4
    // @Short Claims Towny plots, town blocks, or plot groups for a player.
    //
    // @Description
    // This command integrates with Towny to claim land for a specific player.
    //
    // The first argument chooses what to claim:
    // - town: claims Town blocks for the player's town (similar to /town claim).
    // - plot: claims individual plots for the player as a resident.
    // - group: claims all plots in a Towny's plot group.
    //
    // The 'selection' argument is a list of locations that will be converted to Towny
    // coordinates and claimed. This is usually produced by a cuboid or other region
    // selection in Denizen.
    //
    // The 'target' argument specifies which player to use as the Towny resident/town
    // member. The target must be online and a valid Towny resident.
    //
    // The 'admin' argument, when true, lets Town claims bypass normal Towny checks
    // where supported, similar to using Towny's admin claim commands. When false,
    // the command runs Towny's own claimability checks and will fail with the same
    // messages a player would see from Towny.
    //
    // All Towny operations are run asynchronously via the Towny scheduler.
    //
    // @Tags
    // <PlayerTag.towny.*>
    //
    // @Usage
    // Use to claim a selected region as the player's town blocks.
    // - claim town selection:<server.selected_region> target:<player>
    //
    // @Usage
    // Use to claim a list of plots for a resident in admin mode.
    // - claim plot selection:<[plot_locations]> target:<player> admin:true
    //
    // @Usage
    // Use to claim all plots in a plot group, based on a location in that group.
    // - claim group selection:<[some_location_in_group]> target:<player>
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
                // No dedicated TownyAPI "test" helper for plots – let PlotClaim handle its own checks.
                runnable = new PlotClaim(towny, player, resident, coords, true, admin, false);
            }
            case TOWN -> {
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                // Use Towny's own claimability checks when not in admin mode.
                if (!admin) {
                    for (WorldCoord coord : coords) {
                        try {
                            // outpost = false, newTown = false
                            api.testTownClaimOrThrow(town, coord, false, false);
                        }
                        catch (TownyException ex) {
                            scriptEntry.setFinished(true);
                            // reuse Towny's message so behavior matches /town claim
                            throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                        }
                    }
                }

                runnable = new TownClaim(towny, player, town, coords, false, true, admin);
            }
            case GROUP -> {
                // Same as plot, but groupClaim=true.
                // No TownyAPI helper for plot-group claims either, so no extra test here.
                runnable = new PlotClaim(towny, player, resident, coords, true, admin, true);
            }
            default -> {
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Unknown towny target: " + action);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(towny, runnable);
        scriptEntry.setFinished(true);
    }
}
