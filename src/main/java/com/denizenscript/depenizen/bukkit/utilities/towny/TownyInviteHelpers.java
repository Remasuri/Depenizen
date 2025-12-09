package com.denizenscript.depenizen.bukkit.utilities.towny;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.depenizen.bukkit.objects.towny.NationTag;
import com.denizenscript.depenizen.bukkit.objects.towny.TownTag;
import com.palmergames.bukkit.towny.invites.Invite;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class TownyInviteHelpers {
    public static MapTag inviteToMapTag(Invite invite) {
        MapTag map = new MapTag();

        // -----------------------------
        // Resolve sender object
        // -----------------------------
        Object senderObj = invite.getSender();
        ObjectTag resolvedSender = resolveTownyObject(senderObj);

        // -----------------------------
        // Resolve receiver object
        // -----------------------------
        Object receiverObj = invite.getReceiver();
        ObjectTag resolvedReceiver = resolveTownyObject(receiverObj);

        // -----------------------------
        // Infer type string
        // -----------------------------
        String type = buildInviteType(resolvedSender, resolvedReceiver);

        // -----------------------------
        // Fill in map tag
        // -----------------------------
        map.putObject("type", new ElementTag(type));

        if (resolvedSender != null) {
            map.putObject("sender", resolvedSender);
        }
        if (resolvedReceiver != null) {
            map.putObject("receiver", resolvedReceiver);
        }

        // UUID metadata (optional but useful)
        if (senderObj instanceof Resident res && res.getUUID() != null) {
            map.putObject("sender_uuid", new ElementTag(res.getUUID().toString()));
        }
        if (receiverObj instanceof Resident res && res.getUUID() != null) {
            map.putObject("receiver_uuid", new ElementTag(res.getUUID().toString()));
        }

        // direct sender (the player who executed the command)
        if (invite.getDirectSender() instanceof OfflinePlayer op) {
            map.putObject("direct_sender", new PlayerTag(op));
        }

        return map;
    }

    // ---------------------------------------------------
    // Resolve Towny object → Depenizen ObjectTag
    // ---------------------------------------------------
    private static ObjectTag resolveTownyObject(Object obj) {
        if (obj instanceof Town town) {
            return new TownTag(town);
        }
        if (obj instanceof Nation nation) {
            return new NationTag(nation);
        }
        if (obj instanceof Resident res) {
            // Try PlayerTag, fallback to name element
            if (res.getUUID() != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(res.getUUID());
                return new PlayerTag(op);
            }
            return new ElementTag(res.getName());
        }
        return null;
    }

    // ---------------------------------------------------
    // Build type field, e.g. "town_resident"
    // ---------------------------------------------------
    private static String buildInviteType(Object sender, Object receiver) {
        return typeName(sender) + "_" + typeName(receiver);
    }

    private static String typeName(Object obj) {
        if (obj instanceof TownTag) return "town";
        if (obj instanceof NationTag) return "nation";
        if (obj instanceof PlayerTag) return "resident";
        return "unknown";
    }
}
