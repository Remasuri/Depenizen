package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.palmergames.bukkit.towny.event.CancellableTownyEvent;
import com.palmergames.bukkit.towny.event.plot.group.PlotGroupDeletedEvent;
import com.palmergames.bukkit.towny.object.PlotGroup;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * <--[event]
 * @Events
 * towny plotgroup removed
 *
 * @Triggers
 * When a Towny PlotGroup is deleted/removed. Fires before the removal is finalized
 * and can be cancelled.
 *
 * @Context
 * <context.town> Returns the TownTag the plot group belonged to.
 * <context.player> Returns the PlayerTag that removed the plot group, if applicable.
 * <context.plotgroup> Returns the PlotGroupTag that is being removed.
 * <context.cause> Returns an ElementTag of the deletion reason.
 *
 * @Determine
 * "CANCEL_MESSAGE:<ElementTag>" to set the message Towny sends when cancelled.
 *
 * @Cancellable true
 *
 * @Plugin Depenizen, Towny
 *
 * @Group Depenizen
 * -->
 */
public class PlotGroupRemovedScriptEvent extends BukkitScriptEvent implements Listener {

    public PlotGroupRemovedScriptEvent() {
        registerCouldMatcher("towny plotgroup removed");
    }

    public PlotGroupDeletedEvent event;

    @Override
    public ScriptEntryData getScriptEntryData() {
        Player player = event.getPlayer();
        return new BukkitScriptEntryData(player);
    }

    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("town")) {
            PlotGroup group = event.getPlotGroup();
            if (group != null && group.getTown() != null) {
                return new TownTag(group.getTown());
            }
            return null;
        }
        if (name.equals("player")) {
            Player player = event.getPlayer();
            return player != null ? new PlayerTag(player) : null;
        }
        if (name.equals("plotgroup")) {
            return new PlotGroupTag(event.getPlotGroup());
        }
        if (name.equals("cause")) {
            return new ElementTag(event.getDeletionCause().toString());
        }
        return super.getContext(name);
    }

    @Override
    public boolean applyDetermination(ScriptPath path, ObjectTag determinationObj) {
        if (determinationObj instanceof ElementTag && event instanceof CancellableTownyEvent) {
            String determination = determinationObj.toString();
            String lower = CoreUtilities.toLowerCase(determination);
            if (lower.startsWith("cancel_message:")) {
                String message = determination.substring("cancel_message:".length());
                ((CancellableTownyEvent) event).setCancelMessage(message);
                return true;
            }
        }
        return super.applyDetermination(path, determinationObj);
    }

    @EventHandler
    public void onTownyPlotGroupRemoved(PlotGroupDeletedEvent event) {
        this.event = event;
        fire(event);
    }
}
