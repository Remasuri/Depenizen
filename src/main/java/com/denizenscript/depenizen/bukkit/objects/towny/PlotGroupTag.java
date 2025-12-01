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
import org.bukkit.entity.Player;

import java.util.*;

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
        // @plugin Depenizen, Towny
        // @description
        // Returns the name of this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class, "name", (attribute, object) ->
                new ElementTag(object.plotGroup.getName()));

        // <--[tag]
        // @attribute <PlotGroupTag.town>
        // @returns TownTag
        // @plugin Depenizen, Towny
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
        // @plugin Depenizen, Towny
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
        // @plugin Depenizen, Towny
        // @description
        // Returns the number of townblocks in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class, "size", (attribute, object) ->
                new ElementTag(object.plotGroup.getTownBlocks().size()));


        // <--[tag]
        // @attribute <PlotGroupTag.forsale_price>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the sale price of this plot group, if applicable.
        // -->
        tagProcessor.registerTag(ElementTag.class, "forsale_price", (attribute, object) ->
                new ElementTag(object.plotGroup.getPrice()));

        // <--[tag]
        // @attribute <PlotGroupTag.is_forsale>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether this plot group is currently for sale.
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_forsale", (attribute, object) ->
                new ElementTag(object.plotGroup.getPrice() != (double) -1.0F));
        // <--[tag]
        // @attribute <PlotGroupTag.resident>
        // @returns PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the resident that owns this plot group, or null if the plot group is town-owned
        // or the resident is not online.
        // -->
        tagProcessor.registerTag(PlayerTag.class, "resident", ((attribute, object) -> {
            Resident resident = object.plotGroup.getResident();
            if (resident == null) {
                return null;
            }
            UUID uuid = resident.getUUID();
            if (uuid == null) {
                return null;
            }
            return new PlayerTag(Bukkit.getOfflinePlayer(uuid));
        }));
        // <--[tag]
        // @attribute <PlotGroupTag.has_pvp>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether PvP is enabled in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_pvp",(attribute,object) -> {
            return new ElementTag(object.plotGroup.getPermissions().pvp);
        });

        // <--[tag]
        // @attribute <PlotGroupTag.has_fire>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether firespread is enabled in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_fire",(attribute,object) -> {
            return new ElementTag(object.plotGroup.getPermissions().fire);
        });

        // <--[tag]
        // @attribute <PlotGroupTag.has_explosions>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether explosions are enabled in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_explosions",(attribute,object) -> {
            return new ElementTag(object.plotGroup.getPermissions().explosion);
        });

        // <--[tag]
        // @attribute <PlotGroupTag.has_mobs>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether mobs are enabled in this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_mobs",(attribute,object) -> {
            return new ElementTag(object.plotGroup.getPermissions().mobs);
        });

        // <--[tag]
        // @attribute <PlotGroupTag.plot_type>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny plot type name for this plot group.
        // -->
        tagProcessor.registerTag(ElementTag.class,"plot_type",((attribute, object) -> {
            return new ElementTag(object.plotGroup.getTownBlockType().getName());
        }));

        // <--[tag]
        // @attribute <PlotGroupTag.perm[<group>.<action>]>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns a specific permission from this plot group's permissions.
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
        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();
        // <--[mechanism]
        // @object PlotGroupTag
        // @name perm
        // @input ListTag
        // @plugin Depenizen, Towny
        // @description
        // Sets a specific permission on this plot group's permissions object.
        // Input is a list of: <group.action>|<boolean>
        // Where <group> = resident/ally/outsider
        // and <action> = build/destroy/switch/itemuse
        // @tags
        // <PlotGroupTag.perm[<group>.<action>]>
        // -->
        if (mechanism.matches("perm")) {
            ListTag input = mechanism.valueAsType(ListTag.class);
            if (input.size() != 2) {
                mechanism.echoError("Invalid perm mech input: expected 2 values: '<group>.<action>|<boolean>'.");
                return;
            }

            String spec = input.get(0);
            boolean value = new ElementTag(input.get(1)).asBoolean();

            String[] parts = spec.split("\\.", 2);
            if (parts.length != 2) {
                mechanism.echoError("Invalid perm spec '" + spec + "': expected '<group>.<action>'.");
                return;
            }

            String group = CoreUtilities.toLowerCase(parts[0]);  // resident / ally / outsider
            String action = CoreUtilities.toLowerCase(parts[1]); // build / destroy / switch / itemuse

            TownyPermission perms = plotGroup.getPermissions();
            if (perms == null) {
                mechanism.echoError("This PlotGroup has no permissions object.");
                return;
            }
            try {
                switch (group) {
                    case "resident":
                    case "friend": // optional alias if you want
                        switch (action) {
                            case "build":
                                perms.set("residentbuild",value);
                                break;
                            case "destroy":
                                perms.set("residentdestroy",value);
                                break;
                            case "switch":
                                perms.set("residentswitch",value);
                                break;
                            case "itemuse":
                            case "item_use":
                                perms.set("residentitemuse",value);
                                break;
                            default:
                                mechanism.echoError("Unknown action '" + action + "' for group 'resident'.");
                                return;
                        }
                        break;

                    case "ally":
                    case "allies":
                        switch (action) {
                            case "build":
                                perms.set("allybuild",value);
                                break;
                            case "destroy":
                                perms.set("allydestroy",value);
                                break;
                            case "switch":
                                perms.set("allyswitch",value);
                                break;
                            case "itemuse":
                            case "item_use":
                                perms.set("allyitemuse",value);
                                break;
                            default:
                                mechanism.echoError("Unknown action '" + action + "' for group 'ally'.");
                                return;
                        }
                        break;

                    case "outsider":
                    case "outsiders":
                        switch (action) {
                            case "build":
                                perms.set("outsiderbuild",value);
                                break;
                            case "destroy":
                                perms.set("outsiderdestroy",value);
                                break;
                            case "switch":
                                perms.set("outsiderswitch",value);
                                break;
                            case "itemuse":
                            case "item_use":
                                perms.set("outsideritemuse",value);
                                break;
                            default:
                                mechanism.echoError("Unknown action '" + action + "' for group 'outsider'.");
                                return;
                        }
                        break;

                    default:
                        mechanism.echoError("Unknown perm group '" + group + "'. Expected resident/ally/outsider.");
                        return;
                }
                dataSource.savePlotGroup(plotGroup);
            }
            catch (Exception ex) {
                mechanism.echoError("Failed to set TownBlock permission '" + spec + "': " + ex.getMessage());
            }
            return;
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name has_pvp
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether PvP is enabled in this plot group.
        // @tags
        // <PlotGroupTag.has_pvp>
        // -->
        if(mechanism.matches("has_pvp")){
            TownyPermission perms = plotGroup.getPermissions();
            perms.set("pvp",mechanism.getValue().asBoolean());
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name has_firespread
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether firespread is enabled in this plot group.
        // Note that the related tag is <PlotGroupTag.has_fire>.
        // @tags
        // <PlotGroupTag.has_fire>
        // -->
        if(mechanism.matches("has_firespread")){
            TownyPermission perms = plotGroup.getPermissions();
            perms.set("fire",mechanism.getValue().asBoolean());
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name has_explosions
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether explosions are enabled in this plot group.
        // @tags
        // <PlotGroupTag.has_explosions>
        // -->
        if(mechanism.matches("has_explosions")){
            TownyPermission perms = plotGroup.getPermissions();
            perms.set("explosion",mechanism.getValue().asBoolean());
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name has_mobs
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether mobs are enabled in this plot group.
        // @tags
        // <PlotGroupTag.has_mobs>
        // -->
        if(mechanism.matches("has_mobs")) {
            TownyPermission perms = plotGroup.getPermissions();
            perms.set("mobs", mechanism.getValue().asBoolean());
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name plot_type
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the Towny plot type for all townblocks in this plot group.
        // Common values include: residential, shop, arena, embassy, wilds, inn, jail, farm, bank.
        // @tags
        // <PlotGroupTag.plot_type>
        // -->
        if(mechanism.matches("plot_type")){
            String typeString = mechanism.getValue().asString();
            String lower = typeString.toLowerCase();
            TownBlockType type = switch (typeString) {
                case "residential", "default" -> TownBlockType.RESIDENTIAL;
                case "shop", "commercial" -> TownBlockType.COMMERCIAL;
                case "arena" -> TownBlockType.ARENA;
                case "embassy" -> TownBlockType.EMBASSY;
                case "wilds" -> TownBlockType.WILDS;
                case "inn" -> TownBlockType.INN;
                case "jail" -> TownBlockType.JAIL;
                case "farm" -> TownBlockType.FARM;
                case "bank" -> TownBlockType.BANK;
                default -> new TownBlockType(typeString);
            };
            for(TownBlock townBlock : plotGroup.getTownBlocks()){
                townBlock.setType(type);
                dataSource.saveTownBlock(townBlock);
            }
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name add_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds a trusted resident to this plot group.
        // -->
        if(mechanism.matches("add_trusted_resident")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if(resident == null){
                mechanism.echoError("Player '"+player.identifySimple() + "' is not a registered Towny resident");
                return;
            }
            plotGroup.addTrustedResident(resident);
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name remove_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Removes a trusted resident from this plot group.
        // -->
        if(mechanism.matches("remove_trusted_resident")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if(resident == null){
                mechanism.echoError("Player '"+player.identifySimple() + "' is not a registered Towny resident");
                return;
            }
            plotGroup.removeTrustedResident(resident);
            dataSource.savePlotGroup(plotGroup);
        }
        // Unsure if this is how plotgroups determine their isForsale value. This needs to be tested
        // It might work by setting the forsale price to -1?
        // <--[mechanism]
        // @object PlotGroupTag
        // @name is_forsale
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether this plot group is considered for sale, updating all townblocks in the group.
        // @tags
        // <PlotGroupTag.is_forsale>
        // -->
        if (mechanism.matches("is_forsale")) {
            Town town = plotGroup.getTown();
            if (town == null) {
                mechanism.echoError("Plot group lacks an owning town.");
                return;
            }
            boolean forSale = mechanism.getValue().asBoolean();
            TownBlockTypeCache cache = town.getTownBlockTypeCache();
            for (TownBlock block : plotGroup.getTownBlocks()) {
                if (forSale) {
                    cache.addTownBlockOfTypeForSale(block);
                }
                else {
                    cache.removeTownBlockOfTypeForSale(block);
                }
                dataSource.saveTownBlock(block);
            }
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name forsale_price
        // @input ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Sets the sale price for this plot group.
        // @tags
        // <PlotGroupTag.forsale_price>
        // -->
        if (mechanism.matches("forsale_price")) {
            plotGroup.setPrice(mechanism.getValue().asDouble());
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the resident that owns this plot group.
        // Input may also be the text 'none' to clear the resident and make
        // the plot group town-owned instead.
        // @tags
        // <PlotGroupTag.resident>
        // -->
        if(mechanism.matches("resident")) {
            if (mechanism.hasValue() && mechanism.getValue().asString().equalsIgnoreCase("none")) {
                plotGroup.setResident(null); // town-owned
                dataSource.savePlotGroup(plotGroup);
                return;
            }

            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Resident mechanism requires a valid PlayerTag or 'none'.");
                return;
            }
            Resident resident = universe.getResident(player.getUUID());
            plotGroup.setResident(resident);
            dataSource.savePlotGroup(plotGroup);
        }
        // <--[mechanism]
        // @object PlotGroupTag
        // @name townblocks
        // @input ListTag(TownBlockTag)
        // @plugin Depenizen, Towny
        // @description
        // Sets which TownBlocks belong to this plot group.
        // Existing blocks not in the list will be removed from the group,
        // and new blocks in the list will be added to the group.
        // -->
        if(mechanism.matches("townblocks")) {
            ListTag input = mechanism.valueAsType(ListTag.class);
            List<TownBlockTag> townBlockTags = input.filter(TownBlockTag.class, mechanism.context);
            if (townBlockTags.isEmpty()) {
                mechanism.echoError("townblocks mechanism requires a list of TownBlockTags.");
                return;
            }
            Set<TownBlock> newBlocks = new HashSet<>();
            for (TownBlockTag tbt : townBlockTags) {
                TownBlock block = tbt.getTownBlock();
                if (block == null) {
                    mechanism.echoError("Found null townblock in townblocks list: " + tbt.identifySimple());
                    continue;
                }
                newBlocks.add(block);
            }
            Set<TownBlock> oldBlocks = new HashSet<>(plotGroup.getTownBlocks());
            for (TownBlock oldBlock : oldBlocks) {
                if (!newBlocks.contains(oldBlock)) {
                    // Remove this group from the block
                    try {
                        oldBlock.removePlotObjectGroup();
                        dataSource.saveTownBlock(oldBlock);
                    }
                    catch (Exception ex) {
                        mechanism.echoError("Error removing plotgroup from townblock "
                                + oldBlock.getWorldCoord() + ": " + ex.getMessage());
                    }
                }
            }
            for (TownBlock newBlock : newBlocks) {
                if (!oldBlocks.contains(newBlock)) {
                    try {
                        newBlock.setPlotObjectGroup(plotGroup);
                        dataSource.saveTownBlock(newBlock);
                    }
                    catch (Exception ex) {
                        mechanism.echoError("Error adding plotgroup to townblock "
                                + newBlock.getWorldCoord() + ": " + ex.getMessage());
                    }
                }
            }
            plotGroup.setTownblocks(new ArrayList<>(newBlocks));
            dataSource.savePlotGroup(plotGroup);
        }
    }
}
