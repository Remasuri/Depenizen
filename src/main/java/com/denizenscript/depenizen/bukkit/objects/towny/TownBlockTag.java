package com.denizenscript.depenizen.bukkit.objects.towny;

import com.denizenscript.denizen.objects.CuboidTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.objects.WorldTag;
import com.denizenscript.denizencore.DenizenCore;
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
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

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
        String lower = CoreUtilities.toLowerCase(string);
        if (lower.startsWith("townblock@")) {
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
        // @plugin Depenizen, Towny
        // @description
        // Returns the world this townblock is in.
        // -->
        tagProcessor.registerTag(WorldTag.class, "world", (attribute, object) -> {
            return new WorldTag(object.getCoord().getBukkitWorld());
        });

        // <--[tag]
        // @attribute <TownBlockTag.x>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the townblock's X coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "x", (attribute, object) ->
                new ElementTag(object.getCoord().getX()));

        // <--[tag]
        // @attribute <TownBlockTag.z>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the townblock's Z coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "z", (attribute, object) ->
                new ElementTag(object.getCoord().getZ()));

        // <--[tag]
        // @attribute <TownBlockTag.town>
        // @returns TownTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the TownTag that owns this townblock, if any.
        // -->
        tagProcessor.registerTag(TownTag.class, "town", (attribute, object) -> {
            Town town = object.townBlock.getTownOrNull();
            return town != null ? new TownTag(town) : null;
        });

        // <--[tag]
        // @attribute <TownBlockTag.plotgroup>
        // @returns PlotGroupTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the plot group this townblock belongs to, if any.
        // -->
        tagProcessor.registerTag(PlotGroupTag.class, "plotgroup", (attribute, object) -> {
            PlotGroup group = object.townBlock.getPlotObjectGroup();
            return group != null ? new PlotGroupTag(group) : null;
        });

        // <--[tag]
        // @attribute <TownBlockTag.x>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the townblock's X coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "x", (attribute, object) ->
                new ElementTag(object.getCoord().getX()));

        // <--[tag]
        // @attribute <TownBlockTag.z>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the townblock's Z coordinate.
        // -->
        tagProcessor.registerTag(ElementTag.class, "z", (attribute, object) ->
                new ElementTag(object.getCoord().getZ()));

        // <--[tag]
        // @attribute <TownBlockTag.is_forsale>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether this townblock is currently for sale.
        // -->
        tagProcessor.registerTag(ElementTag.class,"is_forsale",((attribute, object) -> {
            return new ElementTag(object.townBlock.isForSale());
        }));

        // <--[tag]
        // @attribute <TownBlockTag.world_location>
        // @returns LocationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a LocationTag using the townblock's world and chunk coordinates.
        // The Y-value is a fixed height, and X/Z are based on the block coordinates of the townblock.
        // -->
        tagProcessor.registerTag(LocationTag.class,"world_location",((attribute, object) -> {
            WorldCoord blockLocation = object.townBlock.getWorldCoord();
            return new LocationTag(blockLocation.getLowerMostCornerLocation());
        }));
        // <--[tag]
        // @attribute <TownBlockTag.at_offset[<x>,<z>]>
        // @returns TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the townblock at the given X/Z offset (in townblock coordinates) from this one,
        // or null if no townblock exists there.
        //
        // Offsets are specified in plot units, not blocks.
        // For example:
        // - <[some_townblock].at_offset[1,0]> returns the townblock one plot east.
        // - <[some_townblock].at_offset[-1,0]> returns the townblock one plot west.
        // - <[some_townblock].at_offset[0,1]> returns the townblock one plot south.
        // - <[some_townblock].at_offset[0,-1]> returns the townblock one plot north.
        // -->
        tagProcessor.registerTag(TownBlockTag.class, "at_offset", (attribute, object) -> {
            if (!attribute.hasContext(1)) {
                return null;
            }
            String context = attribute.getContext(1);
            // Expect "x,z"
            String[] parts = context.split("\\s*,\\s*", 2);
            if (parts.length != 2) {
                return null;
            }
            int offX;
            int offZ;
            try {
                offX = Integer.parseInt(parts[0]);
                offZ = Integer.parseInt(parts[1]);
            }
            catch (NumberFormatException ex) {
                return null;
            }

            WorldCoord base = object.getCoord();
            WorldCoord target = base.add(offX, offZ);
            TownBlock targetBlock = TownyAPI.getInstance().getTownBlock(target);
            if (targetBlock == null) {
                return null;
            }
            return new TownBlockTag(targetBlock);
        });
        // <--[tag]
        // @attribute <TownBlockTag.neighbors>
        // @returns ListTag(TownBlockTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of up to four neighboring townblocks (north, south, east, and west)
        // that exist next to this townblock.
        // Townblocks that do not exist are skipped.
        // -->
        tagProcessor.registerTag(ListTag.class, "neighbors", (attribute, object) -> {
            ListTag list = new ListTag();
            WorldCoord base = object.getCoord();
            TownyAPI api = TownyAPI.getInstance();

            int[][] dirs = new int[][] {
                    {1, 0},   // east
                    {-1, 0},  // west
                    {0, 1},   // south
                    {0, -1}   // north
            };

            for (int[] dir : dirs) {
                WorldCoord neighborCoord = base.add(dir[0], dir[1]);
                TownBlock neighbor = api.getTownBlock(neighborCoord);
                if (neighbor != null) {
                    list.addObject(new TownBlockTag(neighbor));
                }
            }

            return list;
        });
        // <--[tag]
        // @attribute <TownBlockTag.corners>
        // @returns ListTag(LocationTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the 4 corner locations of this townblock.
        //
        // The corners are based on the townblock's configured size and use the same
        // Y-value as <TownBlockTag.world_location>.
        //
        // Order of corners in the list:
        // 1) North-West (min X, min Z)
        // 2) North-East (max X, min Z)
        // 3) South-East (max X, max Z)
        // 4) South-West (min X, max Z)
        // -->
        tagProcessor.registerTag(ListTag.class, "corners", (attribute, object) -> {
            ListTag list = new ListTag();

            WorldCoord coord = object.getCoord();
            Location base = coord.getLowerMostCornerLocation();
            World world = base.getWorld();
            double y = base.getY();

            double xMin = base.getX();
            double zMin = base.getZ();

            int size = TownySettings.getTownBlockSize();
            double xMax = xMin + size - 1;
            double zMax = zMin + size - 1;

            // 1) North-West
            list.addObject(new LocationTag(new Location(world, xMin, y, zMin)));
            // 2) North-East
            list.addObject(new LocationTag(new Location(world, xMax, y, zMin)));
            // 3) South-East
            list.addObject(new LocationTag(new Location(world, xMax, y, zMax)));
            // 4) South-West
            list.addObject(new LocationTag(new Location(world, xMin, y, zMax)));

            return list;
        });
        // <--[tag]
        // @attribute <TownBlockTag.center>
        // @returns LocationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the center location of this townblock.
        //
        // The center is computed as the midpoint of the configured plot size
        // based on Towny's lower-most corner location. Y-level matches
        // <TownBlockTag.world_location>.
        // -->
        tagProcessor.registerTag(LocationTag.class, "center", (attribute, object) -> {
            WorldCoord coord = object.getCoord();

            Location base = coord.getLowerMostCornerLocation();
            World world = base.getWorld();
            double y = base.getY();

            double xMin = base.getX();
            double zMin = base.getZ();

            int size = TownySettings.getTownBlockSize();
            // Center of [xMin .. xMin + size - 1] → xMin + (size - 1) / 2.0
            double centerX = xMin + (size - 1) / 2.0;
            double centerZ = zMin + (size - 1) / 2.0;

            return new LocationTag(new Location(world, centerX, y, centerZ));
        });
        // <--[tag]
        // @attribute <TownBlockTag.resident>
        // @returns PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the resident that owns this plot, or null if the plot is town-owned
        // or the resident is not online.
        // -->
        tagProcessor.registerTag(PlayerTag.class, "resident", ((attribute, object) -> {
            Resident resident = object.townBlock.getResidentOrNull();
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
        // @attribute <TownBlockTag.trusted_residents>
        // @returns ListTag(PlayerTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of residents that are trusted on this townblock, as PlayerTags.
        // -->
        tagProcessor.registerTag(ListTag.class, "trusted_residents",((attribute, object) -> {
            ListTag list = new ListTag();
            for (Resident resident : object.townBlock.getTrustedResidents()){
                PlayerTag playerTag = new PlayerTag(resident.getPlayer());
                list.addObject(playerTag);
            }
            return  list;
        }));

        // <--[tag]
        // @attribute <TownBlockTag.towny_type>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the Towny plot type name of this townblock.
        // -->
        tagProcessor.registerTag(ElementTag.class,"towny_type",((attribute, object) -> {
            return new ElementTag(object.townBlock.getType().getName());
        }));

        // <--[tag]
        // @attribute <TownBlockTag.name>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the custom name of this townblock, if any.
        // -->
        tagProcessor.registerTag(ElementTag.class,"name",((attribute, object) -> {
            return new ElementTag(object.townBlock.getName());
        }));

        // <--[tag]
        // @attribute <TownBlockTag.has_pvp>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether PvP is enabled in this townblock.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_pvp",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().pvp);
        });

        // <--[tag]
        // @attribute <TownBlockTag.has_firespread>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether firespread is enabled in this townblock.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_firespread",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().fire);
        });

        // <--[tag]
        // @attribute <TownBlockTag.has_explosions>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether explosions are enabled in this townblock.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_explosions",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().explosion);
        });

        // <--[tag]
        // @attribute <TownBlockTag.has_mobs>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns whether mobs are enabled in this townblock.
        // -->
        tagProcessor.registerTag(ElementTag.class,"has_mobs",(attribute,object) -> {
            return new ElementTag(object.townBlock.getPermissions().mobs);
        });

        // <--[tag]
        // @attribute <TownBlockTag.perm[<group>.<action>]>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns a specific permission value from this townblock.
        // <group> = resident/ally/outsider
        // <action> = build/destroy/switch/itemuse
        // For example: <TownBlockTag.perm[resident.build]>
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
        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();
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
        // @tags
        // <TownBlockTag.perm[<group>.<action>]>
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
            if (perms == null) {
                mechanism.echoError("This TownBlock has no permissions object.");
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
                dataSource.saveTownBlock(townBlock);
            }
            catch (Exception ex) {
                mechanism.echoError("Failed to set TownBlock permission '" + spec + "': " + ex.getMessage());
            }
            return;
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name has_pvp
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether PvP is enabled in this townblock.
        // @tags
        // <TownBlockTag.has_pvp>
        // -->
        if(mechanism.matches("has_pvp")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("pvp",mechanism.getValue().asBoolean());
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name has_firespread
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether firespread is enabled in this townblock.
        // Note that the related tag is <TownBlockTag.has_firespread>.
        // @tags
        // <TownBlockTag.has_firespread>
        // -->
        if(mechanism.matches("has_firespread")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("fire",mechanism.getValue().asBoolean());
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name has_explosions
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether explosions are enabled in this townblock.
        // @tags
        // <TownBlockTag.has_explosions>
        // -->
        if(mechanism.matches("has_explosions")){
            TownyPermission perms = townBlock.getPermissions();
            perms.set("explosion",mechanism.getValue().asBoolean());
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name has_mobs
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether mobs are enabled in this townblock.
        // @tags
        // <TownBlockTag.has_mobs>
        // -->
        if(mechanism.matches("has_mobs")) {
            TownyPermission perms = townBlock.getPermissions();
            perms.set("mobs", mechanism.getValue().asBoolean());
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name towny_type
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the Towny plot type for this townblock.
        // Common values include: residential, shop, arena, embassy, wilds, inn, jail, farm, bank.
        // @tags
        // <TownBlockTag.towny_type>
        // -->
        if(mechanism.matches("towny_type")){
            String typeString = mechanism.getValue().asString();
            String lower = typeString.toLowerCase();
            townBlock.setType(lower);
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name name
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the custom name of this townblock.
        // @tags
        // <TownBlockTag.name>
        // -->
        if(mechanism.matches("name")){
            townBlock.setName(mechanism.getValue().asString());
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name add_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds a trusted resident to this townblock.
        // @tags
        // <TownBlockTag.trusted_residents>
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
            townBlock.addTrustedResident(resident);
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name remove_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Removes a trusted resident from this townblock.
        // @tags
        // <TownBlockTag.trusted_residents>
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
            townBlock.removeTrustedResident(resident);
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the resident that owns this plot.
        // Input may also be the text 'none' to clear the resident and make
        // the plot town-owned instead.
        // @tags
        // <TownBlockTag.resident>
        // -->
        if(mechanism.matches("resident")) {
            if (mechanism.hasValue() && mechanism.getValue().asString().equalsIgnoreCase("none")) {
                townBlock.setResident(null); // town-owned
                dataSource.saveTownBlock(townBlock);
                return;
            }

            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Resident mechanism requires a valid PlayerTag or 'none'.");
                return;
            }
            Resident resident = universe.getResident(player.getUUID());
            townBlock.setResident(resident);
            dataSource.saveTownBlock(townBlock);
        }
        // <--[mechanism]
        // @object TownBlockTag
        // @name forsale_price
        // @input ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Sets the sale price of this townblock.
        // -->
        if(mechanism.matches("forsale_price")){
            townBlock.setPlotPrice(mechanism.getValue().asDouble());
            dataSource.saveTownBlock(townBlock);
        }
    }
}
