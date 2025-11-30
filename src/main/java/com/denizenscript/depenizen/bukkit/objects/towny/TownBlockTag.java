package com.denizenscript.depenizen.bukkit.objects.towny;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.flags.AbstractFlagTracker;
import com.denizenscript.denizencore.flags.FlaggableObject;
import com.denizenscript.denizencore.flags.RedirectionFlagTracker;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class TownBlockTag implements ObjectTag, Adjustable, FlaggableObject {

    // <--[ObjectType]
    // @name TownBlockTag
    // @prefix townblock
    // @base ElementTag
    // @implements FlaggableObject
    // @format
    // The identity format for townblocks is <world>;<x>;<z>
    // For example, 'townblock@world;12;34'.
    //
    // @plugin Depenizen, Towny
    // @description
    // A TownBlockTag represents a single Towny townblock (plot).
    //
    // This object type is flaggable.
    // Flags on this object type will be stored in the server saves file,
    // under "__depenizen_towny_townblocks.<world>;<x>;<z>"
    // -->

    ////////////////
    //  OBJECT FETCHER
    ////////////////

    @Fetchable("townblock")
    public static TownBlockTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        string = CoreUtilities.toLowerCase(string);
        if (string.startsWith("townblock@")) {
            string = string.substring("townblock@".length());
        }
        // format: world;x;z
        String[] parts = string.split(";", 3);
        if (parts.length != 3) {
            return null;
        }
        String worldName = parts[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        int x, z;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        }
        catch (NumberFormatException ex) {
            return null;
        }

        WorldCoord coord = new WorldCoord(worldName, x, z);
        TownBlock townBlock = TownyAPI.getInstance().getTownBlock(coord);
        if (townBlock == null) {
            return null;
        }
        return new TownBlockTag(townBlock);
    }

    public static boolean matches(String arg) {
        if (CoreUtilities.toLowerCase(arg).startsWith("townblock@")) {
            return true;
        }
        return valueOf(arg, CoreUtilities.noDebugContext) != null;
    }

    ////////////////
    //  FIELDS
    ////////////////

    public TownBlock townBlock;

    public TownBlockTag(TownBlock townBlock) {
        this.townBlock = townBlock;
    }

    public TownBlock getTownBlock() {
        return townBlock;
    }

    public WorldCoord getCoord() {
        return townBlock.getWorldCoord();
    }

    ////////////////
    //  ObjectTag impl
    ////////////////

    private String prefix = "TownBlock";

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public TownBlockTag setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public String identify() {
        WorldCoord coord = getCoord();
        return "townblock@" + coord.getWorldName() + ";" + coord.getX() + ";" + coord.getZ();
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    @Override
    public String toString() {
        return identify();
    }

    ////////////////
    //  Flags
    ////////////////

    @Override
    public AbstractFlagTracker getFlagTracker() {
        WorldCoord coord = getCoord();
        String key = coord.getWorldName() + ";" + coord.getX() + ";" + coord.getZ();
        return new RedirectionFlagTracker(DenizenCore.serverFlagMap,
                "__depenizen_towny_townblocks." + key);
    }

    @Override
    public void reapplyTracker(AbstractFlagTracker tracker) {
        // nothing
    }

    ////////////////
    //  Tags
    ////////////////

    public static ObjectTagProcessor<TownBlockTag> tagProcessor = new ObjectTagProcessor<>();

    public static void register() {
        AbstractFlagTracker.registerFlagHandlers(tagProcessor);

        // Basic info tags

        // <--[tag]
        // @attribute <TownBlockTag.world>
        // @returns WorldTag
        // @description
        // Returns the world this townblock is in.
        // -->
        tagProcessor.registerTag(WorldTag.class, "world", (attribute, object) -> {
            return new WorldTag(object.getCoord().getBukkitWorld());
        });

        // <--[tag]
        // @attribute <TownBlockTag.x>
        // @returns ElementTag(Number)
        // @description
        // Returns the townblock's X coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "x", (attribute, object) ->
                new ElementTag(object.getCoord().getX()));

        // <--[tag]
        // @attribute <TownBlockTag.z>
        // @returns ElementTag(Number)
        // @description
        // Returns the townblock's Z coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "z", (attribute, object) ->
                new ElementTag(object.getCoord().getZ()));

        // <--[tag]
        // @attribute <TownBlockTag.town>
        // @returns TownTag
        // @description
        // Returns the TownTag that owns this townblock.
        // -->
        tagProcessor.registerTag(TownTag.class, "town", (attribute, object) -> {
            return new TownTag(object.townBlock.getTownOrNull());
        });
        tagProcessor.registerTag(ElementTag.class,"is_forsale",((attribute, object) -> {
            return new ElementTag(object.townBlock.isForSale());
        }));

        tagProcessor.registerTag(LocationTag.class,"world_location",((attribute, object) -> {
            WorldCoord blockLocation = object.townBlock.getWorldCoord();
           return new LocationTag(blockLocation.getBukkitWorld(),blockLocation.getX(), 600f,blockLocation.getZ());
        }));
        tagProcessor.registerTag(ListTag.class, "trusted_residents",((attribute, object) -> {
            ListTag list = new ListTag();
            for (Resident resident : object.townBlock.getTrustedResidents()){
                PlayerTag playerTag = new PlayerTag(resident.getPlayer());
                list.addObject(playerTag);
            }
            return  list;
        }));

        tagProcessor.registerTag(ElementTag.class,"has_pvp",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().pvp);
        });
        tagProcessor.registerTag(ElementTag.class,"has_fire",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().fire);
        });
        tagProcessor.registerTag(ElementTag.class,"has_explosions",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().explosion);
        });
        tagProcessor.registerTag(ElementTag.class,"has_mobs",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().mobs);
        });
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

            TownyPermission perms = object.townBlock.getPermissions();

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

    ////////////////
    //  Mechanisms
    ////////////////


    @Override
    public void applyProperty(Mechanism mechanism) {
        mechanism.echoError("Cannot apply properties to a Towny TownBlock directly!");
    }

    @Override
    public void adjust(Mechanism mechanism) {

        // <--[mechanism]
        // @object TownBlockTag
        // @name perm
        // @input ListTag
        // @plugin Depenizen, Towny
        // @description
        // Sets a specific permission on this TownBlock.
        // Input is a list of: <group.action>|<boolean>
        // Where <group> = resident/ally/outsider
        // and <action> = build/destroy/switch/itemuse
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

            TownyPermission perms = townBlock.getPermissions();

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
            }
            catch (Exception ex) {
                mechanism.echoError("Failed to set TownBlock permission '" + spec + "': " + ex.getMessage());
            }
            return;
        }
        if(mechanism.matches("has_pvp")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("pvp",mechanism.getValue().asBoolean());
        }
        if(mechanism.matches("has_firespread")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("fire",mechanism.getValue().asBoolean());
        }
        if(mechanism.matches("has_explosions")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("explosion",mechanism.getValue().asBoolean());
        }
        if(mechanism.matches("has_mobs")) {
            TownyPermission perms = townBlock.getPermissions();
            perms.set("mobs", mechanism.getValue().asBoolean());
        }
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
            townBlock.addTrustedResident(resident);
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
            townBlock.removeTrustedResident(resident);
        }
        if(mechanism.matches("is_forsale")){
            Town town = townBlock.getTownOrNull();
            if (town == null){
                mechanism.echoError("No town registered for this townblock");
                return;
            }
            boolean for_sale = mechanism.getValue().asBoolean();
            if(for_sale)
                town.getTownBlockTypeCache().addTownBlockOfTypeForSale(townBlock);
            else
                town.getTownBlockTypeCache().removeTownBlockOfTypeForSale(townBlock);
        }
        if(mechanism.matches("forsale_price")){
            townBlock.setPlotPrice(mechanism.getValue().asDouble());
        }
    }
}
