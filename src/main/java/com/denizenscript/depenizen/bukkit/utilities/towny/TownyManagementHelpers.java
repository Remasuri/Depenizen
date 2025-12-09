package com.denizenscript.depenizen.bukkit.utilities.towny;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.plot.group.PlotGroupAddEvent;
import com.palmergames.bukkit.towny.event.plot.group.PlotGroupDeletedEvent;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.util.BukkitTools;
import org.bukkit.entity.Player;

public class TownyManagementHelpers {
    public static void addTownBlockToPlotGroup(TownBlock townBlock, PlotGroup plotGroup, Player player) throws RuntimeException {
        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();
        Town town = plotGroup.getTown();

        try {
            // 1) Fire the same event Towny does
            BukkitTools.ifCancelledThenThrow(new PlotGroupAddEvent(plotGroup, townBlock, player));

            // 2) Sync perms from group → block (with null safety)
            TownyPermission groupPerms = plotGroup.getPermissions();
            if (groupPerms != null) {
                townBlock.setPermissions(groupPerms.toString());
            } else if (town != null && town.getPermissions() != null) {
                // fallback to town perms if group has none
                townBlock.setPermissions(town.getPermissions().toString());
            }

            // Mark changed if block perms differ from town perms
            if (town != null && town.getPermissions() != null && townBlock.getPermissions() != null) {
                boolean changed = !townBlock.getPermissions().toString().equals(town.getPermissions().toString());
                townBlock.setChanged(changed);
            }

            // 3) Membership-day limits
            townBlock.setMaxTownMembershipDays(plotGroup.getMaxTownMembershipDays());
            townBlock.setMinTownMembershipDays(plotGroup.getMinTownMembershipDays());

            // 4) Attach block to group
            townBlock.setPlotObjectGroup(plotGroup);

            // 5) Aggregate plot price like Towny
            if (townBlock.getPlotPrice() > 0.0D) {
                plotGroup.addPlotPrice(townBlock.getPlotPrice());
            }

            // 6) Ensure the town knows about the group
            if (town != null) {
                town.addPlotGroup(plotGroup); // safe even if already present
                dataSource.saveTown(town);
            }
            townBlock.setChanged(true);
            // 7) Persist
            plotGroup.save();
            dataSource.saveTownBlock(townBlock);
        } catch (Exception e) {

        }
    }
    public static void removeTownBlockFromPlotGroup(TownBlock townBlock, PlotGroup plotGroup, Player player) {
        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();
        Town town = plotGroup.getTown();

        // Remove from group and block
        plotGroup.removeTownBlock(townBlock);
        townBlock.removePlotObjectGroup();
        townBlock.setChanged(true);
        dataSource.saveTownBlock(townBlock);

        // If group is empty, mirror Towny's delete logic
        if (plotGroup.getTownBlocks().isEmpty()) {
            PlotGroupDeletedEvent event = new PlotGroupDeletedEvent(
                    plotGroup,
                    player,
                    PlotGroupDeletedEvent.Cause.NO_TOWNBLOCKS
            );
            if (!BukkitTools.isEventCancelled(event)) {
                if (town != null) {
                    town.removePlotGroup(plotGroup);
                    dataSource.saveTown(town);
                }
                dataSource.removePlotGroup(plotGroup);
            } else {
                // If deletion is cancelled, just save the group as-is.
                plotGroup.save();
            }
        } else {
            // Group still has blocks, just save it.
            plotGroup.save();
        }
    }
}

