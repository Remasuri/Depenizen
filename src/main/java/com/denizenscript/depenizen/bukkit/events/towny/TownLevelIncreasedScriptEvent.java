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

public class TownLevelIncreasedScriptEvent extends BukkitScriptEvent implements Listener {
    public TownLevelIncreasedScriptEvent() {
        registerCouldMatcher("towny town level increased");
    }
    public TownLevelIncreaseEvent event;
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
    public void onTownyTownCreated(TownLevelIncreaseEvent event) {
        this.event = event;
        fire(event);
    }
}
