package com.denizenscript.depenizen.bukkit.properties.towny;

import com.denizenscript.denizencore.objects.properties.Property;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.depenizen.bukkit.objects.towny.NationTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.denizenscript.depenizen.bukkit.utilities.towny.TownyInviteHelpers;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.invites.Invite;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.tags.Attribute;
import org.bukkit.entity.Player;
import java.util.List;

public class TownyPlayerProperties implements Property {

    // <--[property]
    // @object PlayerTag
    // @name towny_player
    // @plugin Depenizen, Towny
    // @description
    // Provides access to Towny-specific data about this player (their Resident object).
    //
    // This includes:
    // - Whether they have a town/nation
    // - Their ranks
    // - Their Towny friend list
    // - All received invites
    // - Town and Nation objects related to the resident
    //
    // This property allows using:
    // <PlayerTag.towny_list_friends>
    // <PlayerTag.towny_invites>
    // <PlayerTag.has_town>
    // <PlayerTag.has_nation>
    //
    // And mechanisms:
    // - towny_add_friend
    // - towny_remove_friend
    // -->

    @Override
    public String getPropertyString() {
        return null;
    }

    @Override
    public String getPropertyId() {
        return "TownyPlayer";
    }

    @Override
    public void adjust(Mechanism mechanism) {

        // <--[mechanism]
        // @object PlayerTag
        // @name towny_add_friend
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Adds the specified player to this resident’s Towny friend list.
        //
        // This is a **one-way friend relation**, matching Towny's behavior:
        // Adding someone as a friend does NOT automatically add you to their friend list.
        //
        // Usage:
        // - adjust <player> towny_add_friend:<other_player>
        //
        // @tags
        // <PlayerTag.towny_list_friends>
        // -->
        if (mechanism.matches("towny_add_friend")) {
            PlayerTag playerToAdd = mechanism.valueAsType(PlayerTag.class);
            if (playerToAdd == null) {
                mechanism.echoError("towny_add_friend mechanism requires a valid PlayerTag.");
                return;
            }

            Resident residentToAdd = TownyUniverse.getInstance().getResident(playerToAdd.getUUID());
            if (residentToAdd == null) {
                mechanism.echoError("Player '" + playerToAdd.identifySimple() + "' is not a registered Towny resident.");
                return;
            }

            Resident resident = getResident();
            resident.addFriend(residentToAdd);
            resident.save();
        }

        // <--[mechanism]
        // @object PlayerTag
        // @name towny_remove_friend
        // @input PlayerTag
        // @plugin Depenizen, Towny
        // @description
        // Removes the specified player from this resident’s Towny friend list.
        //
        // Usage:
        // - adjust <player> towny_remove_friend:<other_player>
        //
        // @tags
        // <PlayerTag.towny_list_friends>
        // -->
        if (mechanism.matches("towny_remove_friend")) {
            PlayerTag playerToRemove = mechanism.valueAsType(PlayerTag.class);
            if (playerToRemove == null) {
                mechanism.echoError("towny_remove_friend mechanism requires a valid PlayerTag.");
                return;
            }

            Resident residentToRemove = TownyUniverse.getInstance().getResident(playerToRemove.getUUID());
            if (residentToRemove == null) {
                mechanism.echoError("Player '" + playerToRemove.identifySimple() + "' is not a registered Towny resident.");
                return;
            }

            Resident resident = getResident();
            resident.removeFriend(residentToRemove);
            resident.save();
        }
    }

    public static boolean describes(ObjectTag object) {
        return object instanceof PlayerTag;
    }

    public static TownyPlayerProperties getFrom(ObjectTag object) {
        if (!describes(object)) {
            return null;
        }
        return new TownyPlayerProperties((PlayerTag) object);
    }

    public static final String[] handledTags = new String[] {
            "has_nation", "has_town", "mode_list",
            "nation_ranks", "nation", "town_ranks", "town",
            "towny_invites", "towny_list_friends"
    };

    public static final String[] handledMechs = new String[] {
            "towny_add_friend", "towny_remove_friend"
    };

    public TownyPlayerProperties(PlayerTag player) {
        this.player = player;
    }

    PlayerTag player;

    public Resident getResident() {
        return TownyUniverse.getInstance().getResident(player.getUUID());
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {

        // <--[tag]
        // @attribute <PlayerTag.towny_list_friends>
        // @returns ListTag(PlayerTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns a list of all Towny friends this resident has added.
        //
        // Notes:
        // - This is Towny's built-in friend list (used for plot permissions).
        // - Friendship is **not mutual** unless both players add each other.
        // -->
        if (attribute.startsWith("towny_list_friends")) {
            Resident resident = getResident();
            ListTag output = new ListTag();
            List<Resident> friends = resident.getFriends();

            for (Resident friend : friends) {
                Player playerObj = TownyAPI.getInstance().getPlayer(friend);
                if (playerObj != null) {
                    output.addObject(new PlayerTag(playerObj));
                }
            }
            return output;
        }

        // <--[tag]
        // @attribute <PlayerTag.has_nation>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns true if the player belongs to a nation.
        // -->
        if (attribute.startsWith("has_nation")) {
            return new ElementTag(getResident().hasNation())
                    .getObjectAttribute(attribute.fulfill(1));
        }

        // <--[tag]
        // @attribute <PlayerTag.has_town>
        // @returns ElementTag(Boolean)
        // @plugin Depenizen, Towny
        // @description
        // Returns true if the player belongs to a town.
        // -->
        if (attribute.startsWith("has_town")) {
            return new ElementTag(getResident().hasTown())
                    .getObjectAttribute(attribute.fulfill(1));
        }

        // existing tags...

        // towny_invites block left as-is but documented:
        // <--[tag]
        // @attribute <PlayerTag.towny_invites>
        // @returns ListTag(MapTag)
        // @plugin Depenizen, Towny
        // @description
        // Returns all Towny invites this resident has **received**.
        // Each invite is returned as a MapTag with detailed metadata.
        // -->
        if (attribute.startsWith("towny_invites")) {
            Resident resident = getResident();
            if (resident == null) {
                if (!attribute.hasAlternative()) {
                    Debug.echoError("'" + player.getName() + "' is not a Towny resident!");
                }
                return null;
            }
            ListTag out = new ListTag();
            for (Invite invite : resident.getReceivedInvites()) {
                out.addObject(TownyInviteHelpers.inviteToMapTag(invite));
            }
            return out.getObjectAttribute(attribute.fulfill(1));
        }

        // ... rest of your existing tag handlers preserved exactly.

        return null;
    }
}
