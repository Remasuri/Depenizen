package com.denizenscript.depenizen.bukkit.objects.towny;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.depenizen.bukkit.commands.towny.DepenizenTownyCommandHelper;
import com.denizenscript.depenizen.bukkit.commands.towny.VetResult;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.event.TownInvitePlayerEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.invites.Invite;
import com.palmergames.bukkit.towny.invites.InviteHandler;
import com.palmergames.bukkit.towny.invites.InviteReceiver;
import com.palmergames.bukkit.towny.invites.InviteSender;
import com.palmergames.bukkit.towny.invites.exceptions.TooManyInvitesException;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.denizenscript.denizen.objects.*;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.flags.AbstractFlagTracker;
import com.denizenscript.denizencore.flags.FlaggableObject;
import com.denizenscript.denizencore.flags.RedirectionFlagTracker;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.TagContext;
import com.palmergames.bukkit.towny.object.inviteobjects.PlayerJoinTownInvite;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import com.palmergames.bukkit.towny.utils.AreaSelectionUtil;
import com.palmergames.bukkit.towny.utils.ProximityUtil;
import com.palmergames.bukkit.util.BukkitTools;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.InvalidObjectException;
import java.util.*;

import com.denizenscript.depenizen.bukkit.properties.towny.TownyVisualizerUtils;
import org.bukkit.entity.Player;

import javax.swing.text.Element;

import static com.denizenscript.depenizen.bukkit.utilities.towny.TownyInviteHelpers.inviteToMapTag;
import static com.palmergames.bukkit.towny.command.TownCommand.isEdgeBlock;

public class TownTag implements ObjectTag, Adjustable, FlaggableObject {

    // <--[ObjectType]
    // @name TownTag
    // @prefix town
    // @base ElementTag
    // @implements FlaggableObject
    // @format
    // The identity format for towns is <town_uuid>
    // For example, 'town@123-abc'.
    //
    // @plugin Depenizen, Towny
    // @description
    // A TownTag represents a Towny town in the world.
    //
    // This object type is flaggable.
    // Flags on this object type will be stored in the server saves file, under special sub-key "__depenizen_towny_towns_uuid"
    //
    // -->

    /////////////////////
    //   OBJECT FETCHER
    /////////////////

    @Fetchable("town")
    public static TownTag valueOf(String string, TagContext context) {
        if (string.startsWith("town@")) {
            string = string.substring("town@".length());
        }
        if (string.length() == 36 && string.indexOf('-') >= 0) {
            try {
                UUID uuid = UUID.fromString(string);
                if (uuid != null) {
                    Town town = TownyUniverse.getInstance().getTown(uuid);
                    if (town != null) {
                        return new TownTag(town);
                    }
                }
            }
            catch (IllegalArgumentException e) {
                // Nothing
            }
        }
        Town town = TownyUniverse.getInstance().getTown(string);
        if (town == null) {
            return null;
        }
        return new TownTag(town);
    }

    public static boolean matches(String arg) {
        if (arg.startsWith("town@")) {
            return true;
        }
        return valueOf(arg, CoreUtilities.noDebugContext) != null;
    }

    /////////////////////
    //   STATIC CONSTRUCTORS
    /////////////////

    public Town town;

    public TownTag(Town town) {
        this.town = town;
    }

    public static TownTag fromWorldCoord(WorldCoord coord) {
        if (coord == null) {
            return null;
        }
        try {
            return new TownTag(coord.getTownBlock().getTown());
        }
        catch (NotRegisteredException e) {
            return null;
        }
    }

    public static ListTag getPlayersFromResidents(Collection<Resident> residentCollection) {
        ListTag list = new ListTag();
        for (Resident resident : residentCollection) {
            if (resident.getUUID() != null) {
                OfflinePlayer pl = Bukkit.getOfflinePlayer(resident.getUUID());
                if (pl.hasPlayedBefore()) {
                    list.addObject(new PlayerTag(pl));
                    continue;
                }
            }
            list.add(resident.getName());
        }
        return list;
    }

    public static CuboidTag getCuboid(World world, int townCoordX, int townCoordZ) {
        int x = townCoordX * Coord.getCellSize();
        int z = townCoordZ * Coord.getCellSize();
        return new CuboidTag(
                new LocationTag(world, x, world.getMinHeight(), z),
                new LocationTag(world, x + Coord.getCellSize() - 1, world.getMaxHeight(), z + Coord.getCellSize() - 1));
    }

    /////////////////////
    //   ObjectTag Methods
    /////////////////
    private String prefix = "Town";

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public TownTag setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public String identify() {
        return "town@" + town.getUUID();
    }

    @Override
    public String identifySimple() {
        // TODO: Properties?
        return identify();
    }

    public Town getTown() {
        return town;
    }

    public boolean equals(TownTag town) {
        return town.getTown().getUUID().equals(this.getTown().getUUID());
    }

    @Override
    public String toString() {
        return identify();
    }
    @Override
    public AbstractFlagTracker getFlagTracker() {
        if (DenizenCore.serverFlagMap.hasFlag("__depenizen_towny_nations." + town.getName())
                && !DenizenCore.serverFlagMap.hasFlag("__depenizen_towny_towns_uuid." + town.getUUID())) {
            ObjectTag legacyValue = DenizenCore.serverFlagMap.getFlagValue("__depenizen_towny_nations." + town.getName());
            DenizenCore.serverFlagMap.setFlag("__depenizen_towny_towns_uuid." + town.getUUID(), legacyValue, null);
            DenizenCore.serverFlagMap.setFlag("__depenizen_towny_nations." + town.getName(), null, null);
        }
        return new RedirectionFlagTracker(DenizenCore.serverFlagMap, "__depenizen_towny_towns_uuid." + town.getUUID());
    }

    @Override
    public void reapplyTracker(AbstractFlagTracker tracker) {
        // Nothing to do.
    }

    public static void register() {

        AbstractFlagTracker.registerFlagHandlers(tagProcessor);

        // <--[tag]
        // @attribute <TownTag.assistants>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @deprecated use 'members_by_rank'
        // @description
        // Returns a list of the town's assistants. Players will be valid PlayerTag instances, non-players will be plaintext of the name.
        // Deprecated in favor of <@link tag TownTag.members_by_rank>.
        // -->
        tagProcessor.registerTag(ListTag.class, "assistants", (attribute, object) -> {
            return getPlayersFromResidents(object.town.getRank("assistant"));
        });
        // <--[tag]
        // @attribute <TownTag.townblocks>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the towns townblocks.
        // -->
        tagProcessor.registerTag(ListTag.class,"townblocks",(Attribute,object) -> {
            ListTag list = new ListTag();
            for (TownBlock townBlock : object.town.getTownBlocks()) {
                if (townBlock != null){
                    list.addObject(new TownBlockTag(townBlock));
                }
            }
            return list;
        });
        // <--[tag]
        // @attribute <TownTag.balance>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.balance
        // @description
        // Returns the current money balance of the object.town.
        // -->
        tagProcessor.registerTag(ElementTag.class, "balance", (attribute, object) -> {
            return new ElementTag(object.town.getAccount().getHoldingBalance());
        });

        // <--[tag]
        // @attribute <TownTag.board>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @mechanism TownTag.board
        // @description
        // Returns the town's current board.
        // -->
        tagProcessor.registerTag(ElementTag.class, "board", (attribute, object) -> {
            return new ElementTag(object.town.getBoard());
        });
        // <--[tag]
        // @attribute <TownTag.members_by_rank[<rank>]>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the town's members with a given rank. Players will be valid PlayerTag instances, non-players will be plaintext of the name.
        // -->
        tagProcessor.registerTag(ListTag.class, ElementTag.class, "members_by_rank", (attribute, object, rankObj) -> {
            return getPlayersFromResidents(object.town.getRank(rankObj.asString()));
        });

        // <--[tag]
        // @attribute <TownTag.is_open>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.is_open
        // @description
        // Returns true if the town is currently open.
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_open", (attribute, object) -> {
            return new ElementTag(object.town.isOpen());
        });

        // <--[tag]
        // @attribute <TownTag.trusted_residents>
        // @returns ListTag(PlayerTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of residents that are trusted in the town as PlayerTags.
        // -->
        tagProcessor.registerTag(ListTag.class, "trusted_residents", ((attribute, object) -> {
            ListTag list = new ListTag();
            for (Resident resident : object.town.getTrustedResidents()){
                PlayerTag playerTag = new PlayerTag(resident.getPlayer());
                list.addObject(playerTag);
            }
            return  list;
        }));

        // <--[tag]
        // @attribute <TownTag.is_forsale>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.is_forsale
        // @description
        // Returns true if the town is currently listed for sale.
        // -->
        tagProcessor.registerTag(ElementTag.class,"is_forsale",((attribute, object) -> {
            return new ElementTag(object.town.isForSale());
        }));

        // <--[tag]
        // @attribute <TownTag.forsale_price>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.forsale_price
        // @description
        // Returns the asking price when the town is for sale.
        // -->
        tagProcessor.registerTag(ElementTag.class,"forsale_price",((attribute, object) -> {
            return new ElementTag(object.town.getForSalePrice());
        }));
        // <--[tag]
        // @attribute <TownTag.is_public>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.is_public
        // @description
        // Returns true if the town is currently public.
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_public", (attribute, object) -> {
            return new ElementTag(object.town.isPublic());
        });

        // <--[tag]
        // @attribute <TownTag.mayor>
        // @returns PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the mayor of the object.town.
        // -->
        tagProcessor.registerTag(PlayerTag.class, "mayor", (attribute, object) -> {
            Resident mayor = object.town.getMayor();
            if (mayor.getUUID() != null) {
                OfflinePlayer pl = Bukkit.getOfflinePlayer(mayor.getUUID());
                if (pl.hasPlayedBefore()) {
                    return new PlayerTag(pl);
                }
            }
            return null;
        });

        // <--[tag]
        // @attribute <TownTag.name>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the town's name.
        // -->
        tagProcessor.registerTag(ElementTag.class, "name", (attribute, object) -> {
            return new ElementTag(object.town.getName());
        });

        // <--[tag]
        // @attribute <TownTag.nation>
        // @returns NationTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the nation that the town belongs to.
        // -->
        tagProcessor.registerTag(NationTag.class, "nation", (attribute, object) -> {
            try {
                return new NationTag(object.town.getNation());
            }
            catch (NotRegisteredException e) {
            }
            return null;
        });

        // <--[tag]
        // @attribute <TownTag.homeblock>
        // @returns TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the towns current homeblock
        // -->
        tagProcessor.registerTag(TownBlockTag.class,"homeblock",((attribute, object) -> {
            return new TownBlockTag(object.town.getHomeBlockOrNull());
        }));


        // <--[tag]
        // @attribute <TownTag.player_count>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the number of players in the object.town.
        // -->
        tagProcessor.registerTag(ElementTag.class, "player_count", (attribute, object) -> {
            return new ElementTag(object.town.getNumResidents());
        });

        // <--[tag]
        // @attribute <TownTag.residents>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the town's residents. Players will be valid PlayerTag instances, non-players will be plaintext of the name.
        // -->
        tagProcessor.registerTag(ListTag.class, "residents", (attribute, object) -> {
            ListTag list = new ListTag();
            for (Resident resident : object.town.getResidents()) {
                if (resident.getUUID() != null) {
                    OfflinePlayer pl = Bukkit.getOfflinePlayer(resident.getUUID());
                    if (pl.hasPlayedBefore()) {
                        list.addObject(new PlayerTag(pl));
                        continue;
                    }
                }
                list.add(resident.getName());
            }
            return list;
        });

// <--[tag]
// @attribute <TownTag.towny_invites>
// @returns ListTag(MapTag)
// @plugin Depenizen, Towny
// @description
// Returns a list of MapTags describing all invites this town has SENT.
// Each map can contain:
// - direction: ElementTag("sent")
// - sender_name, sender_uuid, sender_type
// - sender_player: PlayerTag (if sender is a Resident with UUID)
// - receiver_name, receiver_uuid, receiver_type
// - receiver_player: PlayerTag (if receiver is a Resident with UUID)
// - receiver_town: TownTag (if receiver is a Town)
// - receiver_nation: NationTag (if receiver is a Nation)
// - is_resident_invite, is_town_invite, is_nation_invite: ElementTag(Boolean)
// -->
        tagProcessor.registerTag(ListTag.class, "towny_invites", (attribute, object) -> {
            ListTag list = new ListTag();
            for (Invite invite : object.town.getSentInvites()) {
                list.addObject(inviteToMapTag(invite));
            }
            return list;
        });

        tagProcessor.registerTag(ElementTag.class, "has_unlimited_claims",(attribute, object) -> {
            return new ElementTag(object.town.hasUnlimitedClaims());
        });
        tagProcessor.registerTag(ElementTag.class,"available_townblocks",(attribute, object) -> {
            return new ElementTag(object.town.availableTownBlocks());
        });
        // <--[tag]
        // @attribute <TownTag.size>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the number of blocks the town owns.
        // -->
        tagProcessor.registerTag(ElementTag.class, "size", (attribute, object) -> {
            return new ElementTag(object.town.getPurchasedBlocks());
        });

        // <--[tag]
        // @attribute <TownTag.spawn>
        // @returns LocationTag
        // @plugin Depenizen, Towny
        // @mechanism TownTag.spawn
        // @description
        // Returns the spawn point of the object.town.
        // -->
        tagProcessor.registerTag(LocationTag.class, "spawn", (attribute, object) -> {
            try {
                return new LocationTag(object.town.getSpawn());
            }
            catch (TownyException e) {
            }
            return null;
        });

        // <--[tag]
        // @attribute <TownTag.tag>
        // @returns ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Returns the town's tag.
        // -->
        tagProcessor.registerTag(ElementTag.class, "tag", (attribute, object) -> {
            return new ElementTag(object.town.getTag());
        });

        // <--[tag]
        // @attribute <TownTag.taxes>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the town's current taxes.
        // -->
        tagProcessor.registerTag(ElementTag.class, "taxes", (attribute, object) -> {
            return new ElementTag(object.town.getTaxes());
        });
        // <--[tag]
        // @attribute <TownTag.outlaws>
        // @returns ListTag(PlayerTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the town's outlaws
        // -->
        tagProcessor.registerTag(ListTag.class,"outlaws",(attribute,object) -> {
            ListTag outlaws = new ListTag();
            for (Resident resident : object.town.getOutlaws()) {
                outlaws.addObject(new PlayerTag(resident.getUUID()));
            }
            return outlaws;
        });
        // <--[tag]
        // @attribute <TownTag.outposts>
        // @returns ListTag(LocationTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the town's outpost locations.
        // -->
        tagProcessor.registerTag(ListTag.class, "outposts", (attribute, object) -> {
            ListTag posts = new ListTag();
            for (Location p : object.town.getAllOutpostSpawns()) {
                posts.addObject(new LocationTag(p));
            }
            return posts;
        });

        // <--[tag]
        // @attribute <TownTag.has_explosions>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.has_explosions
        // @description
        // Returns if the town has explosions turned on.
        // -->
        tagProcessor.registerTag(ElementTag.class, "has_explosions", (attribute, object) -> {
            return new ElementTag(object.town.isExplosion());
        });

        // <--[tag]
        // @attribute <TownTag.has_mobs>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.has_mobs
        // @description
        // Returns if the town has mobs turned on.
        // -->
        tagProcessor.registerTag(ElementTag.class, "has_mobs", (attribute, object) -> {
            return new ElementTag(object.town.hasMobs());
        });

        // <--[tag]
        // @attribute <TownTag.has_pvp>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.has_pvp
        // @description
        // Returns if the town has PvP turned on.
        // -->
        tagProcessor.registerTag(ElementTag.class, "has_pvp", (attribute, object) -> {
            return new ElementTag(object.town.isPVP());
        });

        // <--[tag]
        // @attribute <TownTag.has_firespread>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.has_firespread
        // @description
        // Returns if the town has firespread turned on.
        // -->
        tagProcessor.registerTag(ElementTag.class, "has_firespread", (attribute, object) -> {
            return new ElementTag(object.town.isFire());
        });

        // <--[tag]
        // @attribute <TownTag.has_taxpercent>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns if the town has taxes in percentage.
        // -->
        tagProcessor.registerTag(ElementTag.class, "has_taxpercent", (attribute, object) -> {
            return new ElementTag(object.town.isTaxPercentage());
        });

        // <--[tag]
        // @attribute <TownTag.plot_object_group_names>
        // @returns ListTag
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of the names of town plot object groups.
        // -->
        tagProcessor.registerTag(ListTag.class, "plot_object_group_names", (attribute, object) -> {
            ListTag output = new ListTag();
            if (!object.town.hasPlotGroups()) {
                return null;
            }
            for (PlotGroup group : object.town.getPlotGroups()) {
                output.add(group.getName());
            }
            return output;
        });
// <--[tag]
// @attribute <TownTag.towny_visualizer_lines[world=<world>]>
// @returns ListTag
// @plugin Depenizen, Towny
// @description
// Returns a list of static visualizer edges for the entire town (in a single world).
//
// Each edge is a MapTag: [start=Location; vector=Location(Vector); type=Element(String); plotgroup=Element(UUID?)]
//
// Parameters (MapTag):
// - world: optional, a WorldTag or world name. If omitted, the first world that the town has plots in is used.
//
// This list is completely static (no selection) and is intended to be cached,
// for example as a flag on the town and reused by all players.
// -->
        tagProcessor.registerTag(ListTag.class, "towny_visualizer_lines", (attribute, object) -> {
            Town town = object.town;
            if (town == null) {
                return new ListTag();
            }

            World bukkitWorld = null;

            // --- PARAM PARSING ---
            if (attribute.hasParam()) {
                ObjectTag paramObj = attribute.getParamObject();
                if (paramObj.canBeType(MapTag.class)) {
                    MapTag inputMap = paramObj.asType(MapTag.class, attribute.context);

                    // world=<world>
                    if (inputMap.containsKey("world")) {
                        ObjectTag worldObj = inputMap.getObject("world");
                        WorldTag worldTag;
                        if (worldObj instanceof WorldTag) {
                            worldTag = (WorldTag) worldObj;
                        }
                        else {
                            worldTag = worldObj.asType(WorldTag.class, attribute.context);
                        }
                        if (worldTag != null) {
                            bukkitWorld = worldTag.getWorld();
                        }
                    }
                }
                else {
                    // Simple syntax: <[town].towny_visualizer_lines[<world>]>
                    if (paramObj instanceof WorldTag) {
                        bukkitWorld = ((WorldTag) paramObj).getWorld();
                    }
                    else {
                        WorldTag worldTag = paramObj.asType(WorldTag.class, attribute.context);
                        if (worldTag != null) {
                            bukkitWorld = worldTag.getWorld();
                        }
                    }
                }
            }

            // --- COLLECT TOWNBLOCKS IN TARGET WORLD ---
            List<TownBlock> blocksInWorld = new ArrayList<>();
            for (TownBlock tb : town.getTownBlocks()) {
                if (tb == null) {
                    continue;
                }
                WorldCoord wc = tb.getWorldCoord();
                World tbWorld = wc.getBukkitWorld();
                if (tbWorld == null) {
                    continue;
                }
                if (bukkitWorld != null && !tbWorld.equals(bukkitWorld)) {
                    continue;
                }
                if (bukkitWorld == null) {
                    bukkitWorld = tbWorld;
                }
                blocksInWorld.add(tb);
            }

            if (bukkitWorld == null || blocksInWorld.isEmpty()) {
                return new ListTag();
            }

            // Compute Towny coord bounds for this world
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (TownBlock tb : blocksInWorld) {
                int x = tb.getX();
                int z = tb.getZ();
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            if (minX == Integer.MAX_VALUE) {
                return new ListTag();
            }

            // Resolve TownyWorld from any block in that world
            TownyWorld townyWorld;
            try {
                townyWorld = blocksInWorld.get(0).getWorldCoord().getTownyWorld();
            }
            catch (Exception ex) {
                return new ListTag();
            }

            // Expand bounds slightly so borders against wilderness render cleanly
            return TownyVisualizerUtils.buildVisualizerEdges(
                    townyWorld,
                    bukkitWorld,
                    minX - 1, maxX + 1,
                    minZ - 1, maxZ + 1
            );
        });


        // <--[tag]
        // @attribute <TownTag.list_plotgroups>
        // @returns ListTag(PlotGroupTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of PlotGroupTags for all plot groups in the town.
        // -->
        tagProcessor.registerTag(ListTag.class, "list_plotgroups",((attribute, object) -> {
            ListTag output = new ListTag();
            if (!object.town.hasPlotGroups()) {
                //Not sure if return null or empty list is better?
                return output;
            }
            for(PlotGroup group : object.town.getPlotGroups()) {
                PlotGroupTag plotGroupTag = new PlotGroupTag(group);
                output.addObject(plotGroupTag);
            }
            return output;
        }));

        // <--[tag]
        // @attribute <TownTag.plots>
        // @returns ListTag(ChunkTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of chunks the town has claimed.
        // Note that this will not be accurate if the plot size has been changed in your Towny config.
        // Generally, use <@link tag TownTag.cuboids> instead.
        // -->
        tagProcessor.registerTag(ListTag.class, "plots", (attribute, object) -> {
            ListTag output = new ListTag();
            for (TownBlock block : object.town.getTownBlocks()) {
                output.addObject(new ChunkTag(new WorldTag(block.getWorld().getName()), block.getX(), block.getZ()));
            }
            return output;
        });

        // <--[tag]
        // @attribute <TownTag.cuboids>
        // @returns ListTag(CuboidTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of plot cuboids claimed by the town.
        // Note that the cuboids may be in separate worlds if the town has outposts.
        // -->
        tagProcessor.registerTag(ListTag.class, "cuboids", (attribute, object) -> {
            ListTag output = new ListTag();
            for (TownBlock block : object.town.getTownBlocks()) {
                output.addObject(getCuboid(block.getWorldCoord().getBukkitWorld(), block.getX(), block.getZ()));
            }
            return output;
        });

        // <--[tag]
        // @attribute <TownTag.plottax>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the amount of taxes collected from plots.
        // -->
        tagProcessor.registerTag(ElementTag.class, "plottax", (attribute, object) -> {
            return new ElementTag(object.town.getPlotTax());
        });
        // <--[tag]
        // @attribute <TownTag.town_level>
        // @returns ElementTag(Number)
        // @plugin Depenizen, Towny
        // @description
        // Returns the level of the town
        // -->
        tagProcessor.registerTag(ElementTag.class,"town_level",(Attribute,object) -> {
            return new ElementTag(object.town.getLevelNumber());
        });
        // <--[tag]
        // @attribute <TownTag.plotprice>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the price of a plot.
        // -->
        tagProcessor.registerTag(ElementTag.class, "plotprice", (attribute, object) -> {
            return new ElementTag(object.town.getPlotPrice());
        });

        // <--[tag]
        // @attribute <TownTag.perm[<group>.<action>]>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @mechanism TownTag.perm
        // @description
        // Returns whether a permission is enabled for a specific group in this town.
        // Valid groups: 'resident', 'ally', 'outsider' (with some alias names).
        // Valid actions: 'build', 'destroy', 'switch', 'itemuse'.
        // For example: <[town].perm[resident.build]>
        // -->
        tagProcessor.registerTag(ElementTag.class, "perm", (attribute, object) -> {
            if (!attribute.hasParam()) {
                return null;
            }
            String spec = attribute.getParam(); // e.g. "resident.build"
            String[] parts = spec.split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }

            String group = CoreUtilities.toLowerCase(parts[0]);
            String action = CoreUtilities.toLowerCase(parts[1]);

            TownyPermission perms = object.town.getPermissions();

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
                case "friend": // alias
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
        // <--[tag]
        // @attribute <TownTag.claim_cost>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the cost to claim a single townblock for this town.
        //
        // Equivalent to the town's configured "town block cost" in Towny.
        // -->
        //
        // <--[tag]
        // @attribute <TownTag.claim_cost[<amount>]>
        // @returns ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Returns the total cost to claim the given amount of townblocks for this town.
        //
        // The input is a number of claims.
        // For example: <[town].claim_cost[5]> returns the cost for 5 new claims.
        // -->
        tagProcessor.registerTag(ElementTag.class,"claim_cost",(attribute, object) -> {
            Town town = object.town;
            if(town == null)
                return null;
            if (!attribute.hasParam()){
                return new ElementTag(town.getTownBlockCost());
            }
            try {
                var amount = attribute.getIntParam();
                return new ElementTag(amount == 1 ? town.getTownBlockCost() : town.getTownBlockCostN(amount));
            } catch (Exception e){
                return null;
            }
        });
        // <--[tag]
        // @attribute <TownTag.is_ruined>
        // @returns ElementTag(boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns if the town is ruined
        // -->
        tagProcessor.registerTag(ElementTag.class,"is_ruined",(attribute,object) -> {
            return new ElementTag(object.town.isRuined());
        });
        // <--[tag]
// @attribute <TownTag.vet_claim[<map>]>
// @returns MapTag
// @plugin Depenizen, Towny
// @description
// Vets a town claim attempt using DepenizenTownyCommandHelper.vetTownClaim.
//
// Input map keys:
// - selection: ListTag(WorldCoordTag) or a single WorldCoordTag (required)
// - player: PlayerTag (required)
// - outpost: ElementTag(Boolean) (optional, defaults false)
//
// Output map keys:
// - result: ElementTag(Boolean)
// - error: ElementTag(String) (error key or empty)
// - selection: ListTag(WorldCoordTag) (filtered valid selection)
// - cost: ElementTag(Decimal)
// -->
        tagProcessor.registerTag(MapTag.class, "vet_claim", (attribute, object) -> {
            Town town = object.town;
            if (town == null || !attribute.hasParam()) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("no_town_or_no_input"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }

            ObjectTag paramObj = attribute.getParamObject();
            MapTag inputMap = paramObj.asType(MapTag.class, attribute.context);
            if (inputMap == null) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("invalid_input_map"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }


            // selection
// selection (required)
            ObjectTag selObj = inputMap.getObject("selection");
            if (selObj == null) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("no_selection"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }

            ListTag selList = ListTag.getListFor(selObj, attribute.context);
            List<WorldCoordTag> wcTags = selList.filter(WorldCoordTag.class, attribute.context, false);

            if (wcTags.isEmpty()) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("no_valid_selection"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }

            List<WorldCoord> coords = new ArrayList<>(wcTags.size());
            for (WorldCoordTag wcTag : wcTags) {
                coords.add(wcTag.getWorldCoord());
            }

            if (coords.isEmpty()) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("no_valid_selection"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }

            // player (required for your existing filtering)
            Player player = null;
            if (inputMap.containsKey("player")) {
                ObjectTag playerObj = inputMap.getObject("player");
                PlayerTag ptag = playerObj == null ? null : playerObj.asType(PlayerTag.class, attribute.context);
                if (ptag != null) {
                    player = ptag.getPlayerEntity();
                }
            }
            if (player == null) {
                MapTag out = new MapTag();
                out.putObject("result", new ElementTag(false));
                out.putObject("error", new ElementTag("no_player"));
                out.putObject("selection", new ListTag());
                out.putObject("cost", new ElementTag(0.0));
                return out;
            }

            boolean outpost = false;
            if (inputMap.containsKey("outpost")) {
                outpost = inputMap.getElement("outpost").asBoolean();
            }

            VetResult vet = DepenizenTownyCommandHelper.vetTownClaim(town, coords, player, outpost);
            return VetResult.ToMap.apply(vet);
        });
    }

    public static ObjectTagProcessor<TownTag> tagProcessor = new ObjectTagProcessor<>();

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    @Override
    public void applyProperty(Mechanism mechanism) {
        mechanism.echoError("Cannot apply properties to a Towny town!");
    }
    @Override
    public void adjust(Mechanism mechanism) {

        TownyUniverse universe = TownyUniverse.getInstance();
        var dataSource = universe.getDataSource();
        // <--[mechanism]
        // @object TownTag
        // @name balance
        // @input ElementTag(Decimal)|ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the money balance of a town, with a reason for the change.
        // @tags
        // <TownTag.balance>
        // -->
        if (mechanism.matches("balance")) {
            ListTag input = mechanism.valueAsType(ListTag.class);
            if (input.size() != 2 || !ArgumentHelper.matchesDouble(input.get(0))) {
                mechanism.echoError("Invalid balance mech input.");
                return;
            }
            town.getAccount().setBalance(new ElementTag(input.get(0)).asDouble(), input.get(1));
        }
        // <--[mechanism]
        // @object TownTag
        // @name name
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the name of a town.
        // @tags
        // <TownTag.name>
        // -->
        if (mechanism.matches("name")) {
            String newName = mechanism.getValue().asString();
            try {
                TownyAPI.getInstance().getDataSource().renameTown(town, newName);
            } catch (Exception ex) {
                mechanism.echoError("Could not rename town: " + ex.getMessage());
            }
        }
        // <--[mechanism]
        // @object TownTag
        // @name tag
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the tag of the town.
        // @tags
        // <TownTag.tag>
        // -->
        if (mechanism.matches("tag")) {
            town.setTag(mechanism.getValue().asString());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name board
        // @input ElementTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the board (message) of the town.
        // @tags
        // <TownTag.board>
        // -->
        if (mechanism.matches("board")) {
            town.setBoard(mechanism.getValue().asString());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name has_pvp
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether PvP is enabled in the town.
        // @tags
        // <TownTag.has_pvp>
        // -->
        if (mechanism.matches("has_pvp")) {
            boolean val = mechanism.getValue().asBoolean();

            // 1) Town-level toggle:
            town.setPVP(val);

            // 2) Town-level permission object:
            TownyPermission perms = town.getPermissions();
            if (perms != null) {
                perms.set("pvp", val);
            }

            // 3) Propagate to unowned plots, like /town toggle pvp:
            for (TownBlock townBlock : town.getTownBlocks()) {
                try {
                    if (townBlock.hasResident()) {
                        continue; // skip resident-owned plots
                    }
                } catch (Exception ignored) {
                }
                TownyPermission tbPerms = townBlock.getPermissions();
                if (tbPerms != null) {
                    tbPerms.set("pvp", val);
                    townBlock.setChanged(true);
                    townBlock.save();
                }
            }

            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name has_firespread
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether fire spread is enabled in the town.
        // @tags
        // <TownTag.has_firespread>
        // -->
        if (mechanism.matches("has_firespread")) {
            boolean val = mechanism.getValue().asBoolean();

            // Town toggle:
            town.setFire(val);

            TownyPermission perms = town.getPermissions();
            if (perms != null) {
                perms.set("fire", val);
            }

            for (TownBlock townBlock : town.getTownBlocks()) {
                try {
                    if (townBlock.hasResident()) {
                        continue;
                    }
                } catch (Exception ignored) {
                }
                TownyPermission tbPerms = townBlock.getPermissions();
                if (tbPerms != null) {
                    tbPerms.set("fire", val);
                    townBlock.setChanged(true);
                    townBlock.save();
                }
            }

            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name has_explosions
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether explosions are enabled in the town.
        // @tags
        // <TownTag.has_explosions>
        // -->
        if (mechanism.matches("has_explosions")) {
            boolean val = mechanism.getValue().asBoolean();

            town.setExplosion(val);

            TownyPermission perms = town.getPermissions();
            if (perms != null) {
                perms.set("explosion", val);
            }

            for (TownBlock townBlock : town.getTownBlocks()) {
                try {
                    if (townBlock.hasResident()) {
                        continue;
                    }
                } catch (Exception ignored) {
                }
                TownyPermission tbPerms = townBlock.getPermissions();
                if (tbPerms != null) {
                    tbPerms.set("explosion", val);
                    townBlock.setChanged(true);
                    townBlock.save();
                }
            }

            dataSource.saveTown(town);
        }

        // <--[mechanism]
        // @object TownTag
        // @name has_mobs
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether mobs are enabled in the town.
        // @tags
        // <TownTag.has_mobs>
        // -->
        if (mechanism.matches("has_mobs")) {
            boolean val = mechanism.getValue().asBoolean();

            town.setHasMobs(val);

            TownyPermission perms = town.getPermissions();
            if (perms != null) {
                perms.set("mobs", val);
            }

            for (TownBlock townBlock : town.getTownBlocks()) {
                try {
                    if (townBlock.hasResident()) {
                        continue;
                    }
                } catch (Exception ignored) {
                }
                TownyPermission tbPerms = townBlock.getPermissions();
                if (tbPerms != null) {
                    tbPerms.set("mobs", val);
                    townBlock.setChanged(true);
                    townBlock.save();
                }
            }

            dataSource.saveTown(town);
        }

        // <--[mechanism]
        // @object TownTag
        // @name is_public
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether the town is public.
        // @tags
        // <TownTag.is_public>
        // -->
        if (mechanism.matches("is_public")) {
            town.setPublic(mechanism.getValue().asBoolean());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name is_open
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether the town is open.
        // @tags
        // <TownTag.is_open>
        // -->
        if (mechanism.matches("is_open")) {
            town.setOpen(mechanism.getValue().asBoolean());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name is_forsale
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets whether the town is listed for sale.
        // @tags
        // <TownTag.is_forsale>
        // -->
        if (mechanism.matches("is_forsale")) {
            town.setForSale(mechanism.getValue().asBoolean());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name forsale_price
        // @input ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Sets the asking price for the town when it is for sale.
        // @tags
        // <TownTag.forsale_price>
        // -->
        if (mechanism.matches("forsale_price")) {
            town.setForSalePrice(mechanism.getValue().asDouble());
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name plottax
        // @input ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Sets the plot tax that residents pay in this town
        // @tags
        // <TownTag.plottax>
        // -->
        if(mechanism.matches("plottax")){
            town.setPlotTax(mechanism.getValue().asDouble());
            town.save();
        }
        // <--[mechanism]
        // @object TownTag
        // @name taxes
        // @input ElementTag(Decimal)
        // @plugin Depenizen, Towny
        // @description
        // Sets the tax that residents pay in this town
        // @tags
        // <TownTag.taxes>
        // -->
        if(mechanism.matches("taxes")){
            town.setTaxes(mechanism.getValue().asDouble());
            town.save();
        }
        // <--[mechanism]
        // @object TownTag
        // @name has_taxpercent
        // @input ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Sets if the taxes should be treated as percentages
        // @tags
        // <TownTag.has_taxpercent>
        // -->
        if(mechanism.matches("has_taxpercent")){
            town.setTaxPercentage(mechanism.getValue().asBoolean());
            town.save();
        }
        // <--[mechanism]
        // @object TownTag
        // @name add_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds a trusted resident to the town.
        // @tags
        // <TownTag.trusted_residents>
        // -->
        if (mechanism.matches("add_trusted_resident")) {
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident");
                return;
            }
            town.addTrustedResident(resident);
            town.save();
            //dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name remove_trusted_resident
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Removes a trusted resident from the town.
        // @tags
        // <TownTag.trusted_residents>
        // -->
        if (mechanism.matches("remove_trusted_resident")) {
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("Trusted resident mechanisms require a valid PlayerTag.");
                return;
            }
            Resident resident = TownyUniverse.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident");
                return;
            }
            town.removeTrustedResident(resident);
            town.save();
        }
        // <--[mechanism]
        // @object TownTag
        // @name perm
        // @input ElementTag or ListTag
        // @plugin Depenizen, Towny
        // @description
        // Sets a permission value for a specific group and action in this town.
        // Input should be in the form: "<group>.<action>|<boolean>"
        // For example: "resident.build|true".
        // Valid groups: resident, ally, outsider (with some alias names).
        // Valid actions: build, destroy, switch, itemuse.
        // @tags
        // <TownTag.perm[<group>.<action>]>
        // -->
        if (mechanism.matches("perm")) {
            ListTag input = mechanism.valueAsType(ListTag.class);
            if (input.size() != 2) {
                mechanism.echoError("Invalid perm mech input: expected 2 values: '<group>.<action>|<boolean>'.");
                return;
            }

            String spec = input.get(0); // e.g. "resident.build"
            boolean value = new ElementTag(input.get(1)).asBoolean();

            String[] parts = spec.split("\\.", 2);
            if (parts.length != 2) {
                mechanism.echoError("Invalid perm spec '" + spec + "': expected '<group>.<action>'.");
                return;
            }

            String group = CoreUtilities.toLowerCase(parts[0]);  // resident / ally / outsider
            String action = CoreUtilities.toLowerCase(parts[1]); // build / destroy / switch / itemuse

            TownyPermission perms = town.getPermissions();

            String key;
            switch (group) {
                case "resident":
                case "friend":
                    switch (action) {
                        case "build":
                            key = "residentbuild";
                            break;
                        case "destroy":
                            key = "residentdestroy";
                            break;
                        case "switch":
                            key = "residentswitch";
                            break;
                        case "itemuse":
                        case "item_use":
                            key = "residentitemuse";
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
                            key = "allybuild";
                            break;
                        case "destroy":
                            key = "allydestroy";
                            break;
                        case "switch":
                            key = "allyswitch";
                            break;
                        case "itemuse":
                        case "item_use":
                            key = "allyitemuse";
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
                            key = "outsiderbuild";
                            break;
                        case "destroy":
                            key = "outsiderdestroy";
                            break;
                        case "switch":
                            key = "outsiderswitch";
                            break;
                        case "itemuse":
                        case "item_use":
                            key = "outsideritemuse";
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

            try {
                // 1) Apply to town perms
                perms.set(key, value);
                dataSource.saveTown(town);

                // 2) Mimic `/town set perm` behaviour:
                //    propagate to all *unowned* townblocks so they stay in sync.
                for (TownBlock townBlock : town.getTownBlocks()) {
                    try {
                        if (townBlock.hasResident()) {
                            continue;
                        }
                    } catch (Exception ex) {
                        continue;
                    }

                    TownyPermission tbPerms = townBlock.getPermissions();
                    if (tbPerms == null) {
                        continue;
                    }
                    tbPerms.set(key, value);
                    townBlock.setChanged(true);
                    townBlock.save();
                }
            } catch (Exception ex) {
                mechanism.echoError("Failed to set Town perm '" + spec + "': " + ex.getMessage());
            }
            return;
        }
        // <--[mechanism]
        // @object TownTag
        // @name spawn
        // @input LocationTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the spawn point of the town.
        // @tags
        // <TownTag.spawn>
        // -->
        if (mechanism.matches("spawn")) {
            //TODO: Add Homeblock check and output error if this does not contain homeblock
            town.setSpawn(mechanism.valueAsType(LocationTag.class));
            dataSource.saveTown(town);
        }
        // <--[mechanism]
        // @object TownTag
        // @name homeblock
        // @input TownBlockTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the homeblock of the town
        // @tags
        // <TownTag.spawn>
        // -->
        if (mechanism.matches("homeblock")) {
            TownBlockTag townblockTag = mechanism.valueAsType(TownBlockTag.class);
            town.setHomeBlock(townblockTag.townBlock);
            dataSource.saveTown(town);
        }
        // <--[mechanism]
// @object TownTag
// @name add_resident
// @input PlayerTag
// @plugin Depenizen, Towny
// @description
// Adds the specified player as a resident of this town.
// This is a low-level operation: it does NOT perform safety checks
// like "is the player already in a town?" – you should handle that
// in your Denizen script before calling this.
// @tags
// <TownTag.residents>
// <TownTag.player_count>
// -->
        if (mechanism.matches("add_resident")) {
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("add_resident mechanism requires a valid PlayerTag.");
                return;
            }
            Resident resident = TownyAPI.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident.");
                return;
            }
            try {


                if (town.hasOutlaw(resident)) {
                    town.removeOutlaw(resident);
                }

                resident.setTown(town);
                Towny.getPlugin().deleteCache(resident);
                resident.save();
                town.save();
            } catch (AlreadyRegisteredException exception) {
                mechanism.echoError("Resident already in a town.");
            }
        }
        // <--[mechanism]
// @object TownTag
// @name remove_resident
// @input PlayerTag
// @plugin Depenizen, Towny
// @description
// Removes the specified player from this town's residents.
// Like add_resident, does not perform higher-level safety checks.
// @tags
// <TownTag.residents>
// <TownTag.player_count>
// -->
        if (mechanism.matches("remove_resident")) {
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("remove_resident mechanism requires a valid PlayerTag.");
                return;
            }

            Resident resident = TownyAPI.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident.");
                return;
            }
            if (town.hasResident(resident)) {
                resident.removeTown();
            }
            town.checkTownHasEnoughResidentsForNationRequirements();
            resident.save();
            town.save();
        }
        // <--[mechanism]
        // @object TownTag
        // @name add_outlaw
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds specified player as an outlaw to the town
        // @tags
        // <TownTag.mayor>
        // -->
        if(mechanism.matches("add_outlaw")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("add_outlaw mechanism requires a valid PlayerTag.");
                return;
            }
            Resident resident = TownyAPI.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident.");
                return;
            }
            try {
                town.addOutlaw(resident);
                resident.save();
                town.save();
            }
            catch (Exception ex) {
                mechanism.echoError("Could not add outlaw: " + ex.getMessage());
            }
        }
        // <--[mechanism]
        // @object TownTag
        // @name remove_outlaw
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds specified player as an outlaw to the town
        // @tags
        // <TownTag.mayor>
        // -->
        if(mechanism.matches("remove_outlaw")){
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("remove_outlaw mechanism requires a valid PlayerTag.");
                return;
            }
            Resident resident = TownyAPI.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident.");
                return;
            }
            try {
                town.removeOutlaw(resident);
                resident.save();
                town.save();
            }
            catch (Exception ex) {
                mechanism.echoError("Could not add outlaw: " + ex.getMessage());
            }
        }
        if(mechanism.matches("has_unlimited_claims")){
            town.setHasUnlimitedClaims(mechanism.getValue().asBoolean());
        }
        // <--[mechanism]
        // @object TownTag
        // @name mayor
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Sets the mayor of this town to the specified player.
        // The player must be a registered Towny resident and already belong to this town.
        // @tags
        // <TownTag.mayor>
        // -->
        if (mechanism.matches("mayor")) {
            PlayerTag player = mechanism.valueAsType(PlayerTag.class);
            if (player == null) {
                mechanism.echoError("mayor mechanism requires a valid PlayerTag.");
                return;
            }

            Resident resident = TownyAPI.getInstance().getResident(player.getUUID());
            if (resident == null) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a registered Towny resident.");
                return;
            }

            if (!town.hasResident(resident)) {
                mechanism.echoError("Player '" + player.identifySimple() + "' is not a resident of town '" + town.getName() + "'.");
                return;
            }

            try {
                town.setMayor(resident);
                resident.save();
                town.save();
            }
            catch (Exception ex) {
                mechanism.echoError("Could not set mayor: " + ex.getMessage());
            }
        }
    }

}

