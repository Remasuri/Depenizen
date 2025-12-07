package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.depenizen.bukkit.events.new_events.DepenizenPlotGroupUpdatedEvent;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Depenizen wrapper for DepenizenPlotGroupUpdatedEvent.
 */
public class PlotGroupUpdatedScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // towny plotgroup updated
    //
    // @Cancellable true
    //
    // @Triggers when a Towny plot group has its townblocks changed via Depenizen
    // (for example, through the <plotgroup.townblocks> mechanism).
    //
    // @Context
    // <context.plotgroup> Returns the PlotGroupTag that was updated.
    // <context.town> Returns the TownTag that owns this plotgroup.
    //
    // @Plugin Depenizen, Towny
    //
    // @Group Depenizen
    //
    // -->

    public PlotGroupUpdatedScriptEvent() {
        registerCouldMatcher("towny plotgroup updated");
    }

    public DepenizenPlotGroupUpdatedEvent event;

    @Override
    public boolean matches(ScriptPath path) {
        // No extra filters for now, just basic matcher.
        return super.matches(path);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        // No player or NPC context for this event.
        return new BukkitScriptEntryData(null, null);
    }

    @Override
    public ObjectTag getContext(String name) {
        switch (name) {
            case "plotgroup": {
                PlotGroup group = event.getPlotGroup();
                return group != null ? new PlotGroupTag(group) : null;
            }
            case "town": {
                PlotGroup group = event.getPlotGroup();
                if (group == null) {
                    return null;
                }
                Town town = group.getTown();
                return town != null ? new TownTag(town) : null;
            }
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onDepenizenPlotGroupUpdated(DepenizenPlotGroupUpdatedEvent event) {
        this.event = event;
        fire(event);
    }
}
