package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.denizenscript.depenizen.bukkit.objects.towny.WorldCoordTag;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimCmdEvent;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * <--[event]
 * @Events
 * towny player unclaims plot
 *
 * @Location true
 *
 * @Cancellable true
 *
 * @Triggers
 * When a player runs a Towny unclaim command for town land
 * (eg. /town unclaim, /town unclaim rect, etc) and before
 * the townblocks are actually unclaimed. This includes
 * multi-block unclaims.
 *
 * @Context
 * <context.town> Returns a TownTag of the town doing the unclaim.
 * <context.townblocks> Returns a ListTag(TownBlockTag) of all townblocks that will be unclaimed.
 * <context.townblock> Returns the first TownBlockTag in the unclaim selection (for backwards-style usage).
 *
 * @Determine
 * "CANCEL_MESSAGE:<ElementTag>" to set the message Towny sends when the unclaim is cancelled.
 *
 * @Plugin Depenizen, Towny
 *
 * @Player Always.
 *
 * @Group Depenizen
 * -->
 */
public class PlayerUnclaimsPlotScriptEvent extends BukkitScriptEvent implements Listener {

    public PlayerUnclaimsPlotScriptEvent() {
        registerCouldMatcher("towny player unclaims plot");
    }

    public TownPreUnclaimCmdEvent event;

    @Override
    public boolean matches(ScriptPath path) {
        Player player = event.getResident() != null ? event.getResident().getPlayer() : null;
        if (player == null) {
            return false;
        }
        if (!runInCheck(path, player.getLocation())) {
            return false;
        }
        return super.matches(path);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        Player player = event.getResident() != null ? event.getResident().getPlayer() : null;
        return new BukkitScriptEntryData(player);
    }

    @Override
    public ObjectTag getContext(String name) {
        switch (name) {
            case "town":
                return new TownTag(event.getTown());

            case "worldcoords": {
                ListTag list = new ListTag();
                for (WorldCoord coord : event.getUnclaimSelection()) {
                    //TownBlock tb = coord.getTownBlockOrNull();
                    //if (tb != null) {
                        list.addObject(new WorldCoordTag(coord));
                    //}
                }
                return list;
            }

            case "worldcoord": {
                return new WorldCoordTag(event.getUnclaimSelection().getFirst());
            }
        }
        return super.getContext(name);
    }

    @Override
    public boolean applyDetermination(ScriptPath path, ObjectTag determinationObj) {
        if (determinationObj instanceof ElementTag) {
            String determination = determinationObj.toString();
            String lower = CoreUtilities.toLowerCase(determination);
            if (lower.startsWith("cancel_message:")) {
                event.setCancelMessage(determination.substring("cancel_message:".length()));
                return true;
            }
        }
        return super.applyDetermination(path, determinationObj);
    }

    @EventHandler
    public void onTownyPlayerUnclaimsPlot(TownPreUnclaimCmdEvent event) {
        this.event = event;
        fire(event);
    }
}
