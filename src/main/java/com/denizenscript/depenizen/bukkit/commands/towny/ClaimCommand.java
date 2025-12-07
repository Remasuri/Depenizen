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
        setSyntax("claim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>)");
        setRequiredArguments(2, 5);
        autoCompile();
    }

    // <--[command]
    // @Name Claim
    // @Syntax claim [town/plot/group] (selection:<list[<location>]>) (admin:<true/false>) (target:<player>) (outpost:<true/false>)
    // @Group Depenizen
    // @Plugin Depenizen, Towny
    // @Required 2
    // @Maximum 4
    // @Short Claims Towny plots, town blocks, or plot groups for a player.
    //
    // @Description
    // Mirrors Towny's TownCommand claim behavior (pre-events, economy) for town claims,
    // but without sending chat output. Plot/group claims are delegated to PlotClaim.
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
                // Plot claims: let PlotClaim handle its own checks and events.
                runnable = new PlotClaim(towny, player, resident, coords, true, admin, false);
            }
            case TOWN -> {
                Town town = api.getTown(player);
                if (town == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Target player does not have a town.");
                }

                try {
                    // Town-like checks, events, and economy for non-admin claims.
                    DepenizenTownyCommandHelper.verifyTownClaim(player, town, coords, outpost, admin);
                }
                catch (TownyException ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException(ex.getMessage(player));
                }

                // (plugin, player, town, selection, outpost, claim=true, forced=admin)
                runnable = new TownClaim(towny, player, town, coords, outpost, true, admin);
            }
            case GROUP -> {
                // Group claims: same as plot, but groupClaim=true.
                runnable = new PlotClaim(towny, player, resident, coords, true, admin, true);
            }
            default -> {
                scriptEntry.saveObject("result", new ElementTag("failure"));
                scriptEntry.setFinished(true);
                throw new InvalidArgumentsRuntimeException("Unknown towny target: " + action);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(towny, runnable);
        scriptEntry.saveObject("result", new ElementTag("success"));
        scriptEntry.setFinished(true);
    }
}
