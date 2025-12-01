package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.plot.group.PlotGroupCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlotGroupCreatedScriptEvent extends BukkitScriptEvent implements Listener {
    // <--[event]
    // @Events
    // towny plotgroup created
    //
    // @Triggers after all checks are complete and a Towny PlotGroup is fully created.
    //
    // @Context
    // <context.town> Returns the town that was created.
    // <context.player> Returns the player that created the plot group.
    // <context.plotgroup> Returns the plotgroup that was created.
    //
    // @Plugin Depenizen, Towny
    //
    // @Group Depenizen
    //
    // -->
    public PlotGroupCreatedScriptEvent() {
        registerCouldMatcher("towny plotgroup created");
    }
    public PlotGroupCreatedEvent event;
    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("town")) {
            return new TownTag(event.getTownBlock().getTownOrNull());
        }
        if (name.equals("player")){
            return  new PlayerTag(event.getPlayer());
        }
        if(name.equals("plotgroup")){
            return new PlotGroupTag(event.getPlotGroup());
        }
        return super.getContext(name);
    }
    @EventHandler
    public void onTownyPlotGroupCreated(PlotGroupCreatedEvent event) {
        this.event = event;
        fire(event);
    }

}
