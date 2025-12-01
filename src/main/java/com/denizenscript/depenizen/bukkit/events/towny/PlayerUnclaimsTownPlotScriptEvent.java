package com.denizenscript.depenizen.bukkit.events.towny;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.depenizen.bukkit.objects.towny.PlotGroupTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownBlockTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotPreClaimEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotPreUnclaimEvent;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerUnclaimsTownPlotScriptEvent extends BukkitScriptEvent implements Listener {
    // <--[event]
    // @Events
    // towny player unclaims town plot
    //
    // @Location true
    //
    // @Cancellable true
    //
    // @Triggers when a player tries to claim a new Towny town plot or plot group.
    //
    // @Context
    // <context.town> Returns a TownTag of the town.
    // <context.townblock> Returns the townblock that will be unclaimed by the player.
    // <context.plotgroup> Returns the plotgroup that will be unclaimed by the player.
    //
    // @Determine
    // "CANCEL_MESSAGE:<ElementTag>" to set the message Towny sends when cancelled.
    //
    // @Plugin Depenizen, Towny
    //
    // @Player Always.
    //
    // @Group Depenizen
    //
    // -->
    public PlayerUnclaimsTownPlotScriptEvent() {
        registerCouldMatcher("towny player unclaims town plot");
    }
    public PlotPreUnclaimEvent event;
    @Override
    public boolean matches(ScriptPath path) {
        Resident resident = event.getOldResident();
        if(resident == null)
            return false;
        Player player = resident.getPlayer();
        if(player == null)
            return false;
        if (!runInCheck(path, player.getLocation())) {
            return false;
        }
        return super.matches(path);
    }
    @Override
    public ScriptEntryData getScriptEntryData() {
        Resident resident = event.getOldResident();
        Player player = (resident != null) ? resident.getPlayer() : null;
        return new BukkitScriptEntryData(player);
    }
    @Override
    public ObjectTag getContext(String name) {
        switch (name) {
            case "town":
                return new TownTag(event.getTownBlock().getTownOrNull());
            case "townblock":
                return new TownBlockTag(event.getTownBlock());
            case "plotgroup":
                return new PlotGroupTag(event.getTownBlock().getPlotObjectGroup());
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
    public void onTownyPlayerUnclaimsTownPlot(PlotPreUnclaimEvent event) {
        this.event = event;
        fire(event);
    }
}
