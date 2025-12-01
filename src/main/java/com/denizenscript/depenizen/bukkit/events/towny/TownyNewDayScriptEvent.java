package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.palmergames.bukkit.towny.event.PreNewDayEvent;
import com.palmergames.bukkit.towny.event.PreNewTownEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TownyNewDayScriptEvent extends BukkitScriptEvent implements Listener {
    // <--[event]
    // @Events
    // towny new day
    //
    // @Cancellable true
    //
    // @Triggers when towny executes its daily timer tasks
    //
    // @Plugin Depenizen, Towny
    //
    // @Group Depenizen
    //
    // -->
    public TownyNewDayScriptEvent() {
        registerCouldMatcher("towny new day");
    }
    public PreNewDayEvent event;

    @EventHandler
    public void onTownyNewDay(PreNewDayEvent event) {
        this.event = event;
        fire(event);
    }
}
