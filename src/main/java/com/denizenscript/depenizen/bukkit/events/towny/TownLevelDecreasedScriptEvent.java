package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.town.TownLevelDecreaseEvent;
import com.palmergames.bukkit.towny.event.town.TownLevelIncreaseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;


//TODO: Figure out if increase & decrease can be combined to single "changed" event. Learn more java syntax for this
public class TownLevelDecreasedScriptEvent extends BukkitScriptEvent implements Listener {
    // <--[event]
    // @Events
    // towny town level decreased
    //
    // @Triggers after all checks are complete and a Towny town levels down.
    //
    // @Context
    // <context.town> Returns the town that changed.
    // <context.town_level> Returns the new town level.
    //
    // @Plugin Depenizen, Towny
    //
    // @Group Depenizen
    //
    // -->
    public TownLevelDecreasedScriptEvent() {
        registerCouldMatcher("towny town level decreased");
    }
    public TownLevelDecreaseEvent event;
    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("town")) {
            return new TownTag(event.getTown());
        }
        if (name.equals("town_level")) {
            return new ElementTag(event.getTown().getLevelNumber());
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onTownyTownCreated(TownLevelDecreaseEvent event) {
        this.event = event;
        fire(event);
    }
}
