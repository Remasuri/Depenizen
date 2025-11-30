package com.denizenscript.depenizen.bukkit.objects.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.flags.AbstractFlagTracker;
import com.denizenscript.denizencore.flags.FlaggableObject;
import com.denizenscript.denizencore.flags.RedirectionFlagTracker;
import com.denizenscript.denizencore.objects.Adjustable;
import com.denizenscript.denizencore.objects.ArgumentHelper;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.*;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;

public class PlotGroupTag implements ObjectTag, Adjustable, FlaggableObject {

    // <--[ObjectType]
    // @name PlotGroupTag
    // @prefix plotgroup
    // @base ElementTag
    // @implements FlaggableObject
    // @format
    // The identity format for plot groups is <town_uuid>;<group_name>
    // For example, 'plotgroup@123e4567-e89b-12d3-a456-426614174000;Market'.
    //
    // @plugin Depenizen, Towny
    // @description
    // A PlotGroupTag represents a Towny plot group.
    //
    // This object type is flaggable.
    // Flags on this object type will be stored in the server saves file,
    // under "__depenizen_towny_plotgroups.<town_uuid>;<group_name>"
    // -->

    /////////////////////
    //   OBJECT FETCHER
    /////////////////////

    @Fetchable("plotgroup")
    public static PlotGroupTag valueOf(String string, TagContext context) {
        if (string == null) return null;

        string = CoreUtilities.toLowerCase(string);

        if (string.startsWith("plotgroup@")) {
            string = string.substring("plotgroup@".length());
        }

        try {
            UUID id = UUID.fromString(string);
            PlotGroup group = TownyUniverse.getInstance().getGroup(id);
            if (group != null) {
                return new PlotGroupTag(group);
            }
        }
        catch (IllegalArgumentException ignored) { }

        return null;
    }

    public static boolean matches(String arg) {
        if (CoreUtilities.toLowerCase(arg).startsWith("plotgroup@")) {
            return true;
        }
        return valueOf(arg, CoreUtilities.noDebugContext) != null;
    }

    /////////////////////
    //   FIELDS
    /////////////////////

    public PlotGroup plotGroup;

    public PlotGroupTag(PlotGroup plotGroup) {
        this.plotGroup = plotGroup;
    }

    public PlotGroup getPlotGroup() {
        return plotGroup;
    }

    public Town getTown() {
        return plotGroup.getTown();
    }

    /////////////////////
    //   ObjectTag Methods
    /////////////////////

    private String prefix = "PlotGroup";

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public PlotGroupTag setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public String identify() {
        return "plotgroup@" + plotGroup.getUUID().toString();
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    @Override
    public String toString() {
        return identify();
    }

    /////////////////////
    //   Flags
    /////////////////////

    @Override
    public AbstractFlagTracker getFlagTracker() {
        return new RedirectionFlagTracker(DenizenCore.serverFlagMap,
                "__depenizen_towny_plotgroups." + plotGroup.getUUID());
    }

    @Override
    public void reapplyTracker(AbstractFlagTracker tracker) {
        // nothing
    }

    /////////////////////
    //   Tags
    /////////////////////

    public static ObjectTagProcessor<PlotGroupTag> tagProcessor = new ObjectTagProcessor<>();

    public static void register() {
        AbstractFlagTracker.registerFlagHandlers(tagProcessor);

        // <--[tag]
        // @attribute <PlotGroupTag.name>
        // @returns ElementTag
        // @description
        // Returns the name of this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class, "name", (attribute, object) ->
                new ElementTag(object.plotGroup.getName()));

        // <--[tag]
        // @attribute <PlotGroupTag.town>
        // @returns TownTag
        // @description
        // Returns the town that owns this plot group.
        // -->
        tagProcessor.registerTag(TownTag.class, "town", (attribute, object) -> {
            Town town = object.plotGroup.getTown();
            return town != null ? new TownTag(town) : null;
        });

        // <--[tag]
        // @attribute <PlotGroupTag.list_townblocks>
        // @returns ListTag(TownBlockTag)
        // @description
        // Returns a list of TownBlockTags that belong to this plot group.
        // -->
        tagProcessor.registerTag(ListTag.class, "list_townblocks", (attribute, object) -> {
            ListTag list = new ListTag();
            for (TownBlock block : object.plotGroup.getTownBlocks()) {
                if (block != null) {
                    list.addObject(new TownBlockTag(block));
                }
            }
            return list;
        });

        // <--[tag]
        // @attribute <PlotGroupTag.size>
        // @returns ElementTag(Number)
        // @description
        // Returns the number of townblocks in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class, "size", (attribute, object) ->
                new ElementTag(object.plotGroup.getTownBlocks().size()));


        // <--[tag]
        // @attribute <PlotGroupTag.forsale_price>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the sale price of this plot group, if applicable.
        // -->
         tagProcessor.registerTag(ElementTag.class, "forsale_price", (attribute, object) ->
                 new ElementTag(object.plotGroup.getPrice()));

        // <--[tag]
        // @attribute <PlotGroupTag.is_forsale>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this plot group is currently for sale.
        // -->
         tagProcessor.registerTag(ElementTag.class, "is_forsale", (attribute, object) ->
                 new ElementTag(object.plotGroup.getPrice() != (double) -1.0F));


         // <--[tag]
        // @attribute <PlotGroupTag.perm[<group>.<action>]>
        // @returns ElementTag(Boolean)
        // @description
        // Returns a specific permission from this plot group's representative townblock.
        // <group> = resident/ally/outsider
        // <action> = build/destroy/switch/itemuse
        // For example: <plotgroup.perm[resident.build]>
        // -->
        tagProcessor.registerTag(ElementTag.class, "perm", (attribute, object) -> {
            if (!attribute.hasContext(1)) {
                return null;
            }
            String spec = attribute.getContext(1);
            String[] parts = spec.split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }

            String group = CoreUtilities.toLowerCase(parts[0]);
            String action = CoreUtilities.toLowerCase(parts[1]);


            TownyPermission perms = object.plotGroup.getPermissions();
            if (perms == null) {
                return null;
            }

            TownyPermission.ActionType actionType;
            switch (action) {
                case "build":
                    actionType = TownyPermission.ActionType.BUILD;
                    break;
                case "destroy":
                    actionType = TownyPermission.ActionType.DESTROY;
                    break;
                case "switch":
                    actionType = TownyPermission.ActionType.SWITCH;
                    break;
                case "itemuse":
                case "item_use":
                    actionType = TownyPermission.ActionType.ITEM_USE;
                    break;
                default:
                    return null;
            }

            boolean value;
            switch (group) {
                case "resident":
                case "friend":
                    value = perms.getResidentPerm(actionType);
                    break;
                case "ally":
                case "allies":
                    value = perms.getAllyPerm(actionType);
                    break;
                case "outsider":
                case "outsiders":
                    value = perms.getOutsiderPerm(actionType);
                    break;
                default:
                    return null;
            }

            return new ElementTag(value);
        });
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    /////////////////////
    //   Mechanisms
    /////////////////////

    @Override
    public void applyProperty(Mechanism mechanism) {
        mechanism.echoError("Cannot apply properties to a Towny PlotGroup directly!");
    }

    @Override
    public void adjust(Mechanism mechanism) {
        if(mechanism.matches("add_trusted_resident")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if(resident == null){
                mechanism.echoError("Player '"+player.identifySimple() + "' is not a registered Towny resident");
            }
            plotGroup.addTrustedResident(resident);
        }
        if(mechanism.matches("remove_trusted_resident")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if(resident == null){
                mechanism.echoError("Player '"+player.identifySimple() + "' is not a registered Towny resident");
            }
            plotGroup.removeTrustedResident(resident);
        }
    }
}
