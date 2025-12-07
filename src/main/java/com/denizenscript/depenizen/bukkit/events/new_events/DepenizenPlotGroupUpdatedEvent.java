package com.denizenscript.depenizen.bukkit.events.new_events;

import com.palmergames.bukkit.towny.object.PlotGroup;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DepenizenPlotGroupUpdatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final PlotGroup plotGroup;

    public DepenizenPlotGroupUpdatedEvent(PlotGroup plotGroup) {
        this.plotGroup = plotGroup;
    }

    public PlotGroup getPlotGroup() {
        return plotGroup;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    // If you don't need cancellation, you can drop Cancellable and all that.
    private boolean cancelled;
    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
