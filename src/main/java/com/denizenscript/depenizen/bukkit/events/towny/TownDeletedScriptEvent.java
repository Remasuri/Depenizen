package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.PreDeleteTownEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TownDeletedScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // towny town deleted
    //
    // @Triggers before a towny town would get deleted
    //
    // @Context
    // <context.town> Returns the town that will be deleted.
    //
    // @Plugin Depenizen, Towny
    //
    // @Group Depenizen
    //
    // -->

    public TownDeletedScriptEvent() {
        registerCouldMatcher("towny town deleted");
    }

    public PreDeleteTownEvent event;

    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("town")) {
            return new TownTag(event.getTown());
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onTownyTownDeleted(PreDeleteTownEvent event) {
        this.event = event;
        fire(event);
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
}
