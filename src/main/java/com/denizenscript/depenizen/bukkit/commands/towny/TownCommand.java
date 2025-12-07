package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.PreDeleteTownEvent;
import com.palmergames.bukkit.towny.event.PreNewTownEvent;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * town [create/delete] (name:<#>) (mayor:<player>) (homeblock:<location>) (town:<town>)
 *
 * - town create name:<name> (mayor:<player>) (homeblock:<location>)
 *   Creates a new Towny town at the given homeblock (or mayor's current location).
 *   Saves:
 *     result: "success" or "failure"
 *     created_town: <TownTag> (on success)
 *
 * - town delete (town:<town> / name:<name>)
 *   Deletes a Towny town, using Towny's own datasource deleteTown().
 *   Saves:
 *     result: "success" or "failure"
 */
public class TownCommand extends AbstractCommand {

    public TownCommand() {
        setName("town");
        setSyntax("town [create/delete] (name:<#>) (mayor:<player>) (homeblock:<location>) (town:<town>)");
        setRequiredArguments(2, 4);
        autoCompile();
    }

    public enum Action { CREATE, DELETE }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action,
                                   @ArgName("name") @ArgPrefixed @ArgDefaultNull String name,
                                   @ArgName("mayor") @ArgPrefixed @ArgDefaultNull PlayerTag mayorTag,
                                   @ArgName("homeblock") @ArgPrefixed @ArgDefaultNull LocationTag homeblock,
                                   @ArgName("town") @ArgPrefixed @ArgDefaultNull TownTag townTag) {

        TownyUniverse universe = TownyUniverse.getInstance();
        TownyAPI api = TownyAPI.getInstance();

        switch (action) {

            case CREATE -> {
                // --- Argument & context resolution ---
                if (name == null || name.isBlank()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Must specify name:<#> when creating a town!");
                }

                PlayerTag mayor = mayorTag != null ? mayorTag : Utilities.getEntryPlayer(scriptEntry);
                if (mayor == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Must specify a mayor:<player> or have a linked player!");
                }

                Player player = mayor.getPlayerEntity();
                if (player == null || !player.isOnline()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Mayor player must be online.");
                }

                // Determine homeblock location: explicit homeblock, or mayor's current location
                LocationTag homeLoc = homeblock != null ? homeblock : new LocationTag(player.getLocation());

                Resident resident = api.getResident(player);
                if (resident == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError("Mayor is not a Towny resident.");
                    scriptEntry.setFinished(true);
                    return;
                }

                if (resident.hasTown()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError("Mayor already belongs to a town and cannot create a new one.");
                    scriptEntry.setFinished(true);
                    return;
                }

                // Ensure town name is unique
                if (api.getTown(name) != null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError("A town with the name '" + name + "' already exists.");
                    scriptEntry.setFinished(true);
                    return;
                }

                // Resolve WorldCoord for homeblock and verify it's valid & unclaimed
                WorldCoord worldCoord;
                try {
                    worldCoord = WorldCoord.parseWorldCoord(homeLoc);
                }
                catch (Exception ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError(ex);
                    Debug.echoError("Unable to parse homeblock location into a Towny WorldCoord.");
                    scriptEntry.setFinished(true);
                    return;
                }

                TownyWorld townyWorld = worldCoord.getTownyWorld();
                if (townyWorld == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError("Homeblock is not in a Towny world.");
                    scriptEntry.setFinished(true);
                    return;
                }

                // Do not allow town creation on an already-claimed townblock.
                if (api.getTownBlock(worldCoord) != null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError("Homeblock location is already claimed by another town.");
                    scriptEntry.setFinished(true);
                    return;
                }

                // --- Fire Towny's PreNewTownEvent and allow other plugins to cancel or adjust price ---
                double price = TownySettings.getNewTownPrice();
                PreNewTownEvent preEvent = new PreNewTownEvent(
                        player,
                        name,
                        homeLoc,
                        price
                );
                Bukkit.getPluginManager().callEvent(preEvent);

                if (preEvent.isCancelled()) {
                    // Creation is vetoed by another plugin / listener
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    return;
                }

                try {
                    // --- Create town via Towny's datasource ---
                    universe.newTown(name);
                    Town town = universe.getTown(name);

                    // Attach resident + mayor
                    resident.setTown(town);
                    town.setMayor(resident);

                    // Create the initial TownBlock as the homeblock
                    TownBlock townBlock = new TownBlock(worldCoord.getX(), worldCoord.getZ(), townyWorld);
                    townBlock.setTown(town);
                    universe.getDataSource().saveTownBlock(townBlock);

                    // Set homeblock & spawn
                    town.setHomeBlock(townBlock);
                    town.setSpawn(homeLoc);

                    // Persist town + resident
                    universe.getDataSource().saveTown(town);
                    universe.getDataSource().saveResident(resident);

                    // Fire Towny's NewTownEvent after successful creation
                    Bukkit.getPluginManager().callEvent(new NewTownEvent(town));

                    // Output for Denizen
                    scriptEntry.saveObject("created_town", new TownTag(town));
                    scriptEntry.saveObject("result", new ElementTag("success"));
                    scriptEntry.setFinished(true);
                }
                catch (TownyException ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError(ex.getMessage());
                    scriptEntry.setFinished(true);
                }
                catch (Exception ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError(ex);
                    scriptEntry.setFinished(true);
                }
            }

            case DELETE -> {
                // Delete either by explicit town:<town> or by name:<name>
                Town town = null;

                if (townTag != null) {
                    town = townTag.getTown();
                }
                else if (name != null && !name.isBlank()) {
                    town = api.getTown(name);
                }

                if (town == null) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    throw new InvalidArgumentsRuntimeException("Must specify a valid town:<town> or name:<name> to delete.");
                }

                // Determine the sender for the events (player if available, otherwise console)
                CommandSender sender = null;
                PlayerTag linkedPlayerTag = Utilities.getEntryPlayer(scriptEntry);
                if (linkedPlayerTag != null) {
                    Player linkedPlayer = linkedPlayerTag.getPlayerEntity();
                    if (linkedPlayer != null && linkedPlayer.isOnline()) {
                        sender = linkedPlayer;
                    }
                }
                if (sender == null) {
                    sender = Bukkit.getConsoleSender();
                }

                // Work out an appropriate cause
                DeleteTownEvent.Cause cause;
                if (sender instanceof Player playerSender) {
                    Resident senderResident = api.getResident(playerSender);
                    // If the sender is the mayor, treat it as a normal command, otherwise as an admin command
                    if (senderResident != null && town.getMayor() != null && town.getMayor().equals(senderResident)) {
                        cause = DeleteTownEvent.Cause.COMMAND;
                    }
                    else {
                        cause = DeleteTownEvent.Cause.ADMIN_COMMAND;
                    }
                }
                else {
                    cause = DeleteTownEvent.Cause.ADMIN_COMMAND;
                }

                // --- Fire Towny's PreDeleteTownEvent and allow cancellation ---
                PreDeleteTownEvent preDeleteEvent = new PreDeleteTownEvent(town, cause, sender);
                Bukkit.getPluginManager().callEvent(preDeleteEvent);

                if (preDeleteEvent.isCancelled()) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    scriptEntry.setFinished(true);
                    return;
                }

                try {
                    // Use Towny's own datasource deleteTown, which handles townblocks etc.
                    universe.getDataSource().deleteTown(town);

                    // Fire Towny's DeleteTownEvent after deletion
                    Resident mayor = town.getMayor(); // may be null, event handles that
                    Bukkit.getPluginManager().callEvent(new DeleteTownEvent(town, mayor, cause, sender));

                    scriptEntry.saveObject("result", new ElementTag("success"));
                    scriptEntry.setFinished(true);
                }
                catch (Exception ex) {
                    scriptEntry.saveObject("result", new ElementTag("failure"));
                    Debug.echoError(ex);
                    scriptEntry.setFinished(true);
                }
            }
        }
    }
}
