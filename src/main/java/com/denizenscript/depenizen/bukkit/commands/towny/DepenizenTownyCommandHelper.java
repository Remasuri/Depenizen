package com.denizenscript.depenizen.bukkit.commands.towny;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimCmdEvent;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockOwner;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.utils.ProximityUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helper logic to mimic Towny's TownCommand behaviour for
 * claim / unclaim checks, events, and economy.
 */
public final class DepenizenTownyCommandHelper {

    private DepenizenTownyCommandHelper() {
    }

    /**
     * Full verification path for *town* claims, roughly matching:
     *   parseTownClaimCommand -> vetTownAllowedTheseClaims ->
     *   fireTownPreClaimEventOrThrow -> vetTheTownCanPayIfRequired
     *
     * @param admin if true: skip normal checks & economy (admin claims are free).
     */
    public static void verifyTownClaim(Player player,
                                       Town town,
                                       List<WorldCoord> selection,
                                       boolean outpost,
                                       boolean admin) throws TownyException {

        // Normal player claims go through Towny's checks.
        if (!admin) {
            vetTownAllowedTheseClaims(town, outpost, selection);
            // testTownClaimOrThrow performs world / claimability / adjacency checks.
            TownyAPI api = TownyAPI.getInstance();
            for (WorldCoord coord : selection) {
                api.testTownClaimOrThrow(town, coord, outpost, false);
            }
            // Economy pre-check + withdrawal.
            vetTheTownCanPayIfRequired(player, town, outpost, selection);
        }

        // Always fire TownPreClaimEvent so listeners see the claim,
        // even if you're using "admin:true".
        fireTownPreClaimEventOrThrow(player, town, outpost, selection);
    }

    /**
     * Verification path for *town* unclaims, roughly matching:
     *   parseTownUnclaimCommand -> testTownUnclaimOrThrow ->
     *   homeblock checks -> ProximityUtil.testAdjacentUnclaimsRulesOrThrow ->
     *   TownPreUnclaimCmdEvent + unclaim economy.
     *
     * @param admin if true: skip normal tests & economy, but still fire pre-event.
     */
    public static void verifyTownUnclaim(Player player,
                                         Town town,
                                         Resident resident,
                                         List<WorldCoord> selection,
                                         boolean admin) throws TownyException {

        if (selection == null || selection.isEmpty()) {
            return;
        }

        TownyAPI api = TownyAPI.getInstance();

        if (!admin) {
            // Towny's own unclaimability checks.
            for (WorldCoord coord : selection) {
                api.testTownUnclaimOrThrow(town, coord);
            }

            // Disallow unclaiming any homeblock in the selection.
            for (WorldCoord coord : selection) {
                TownBlock tb = coord.getTownBlockOrNull();
                if (tb != null && tb.isHomeBlock()) {
                    // Message here can be refined to use Translatable if needed.
                    throw new TownyException("You cannot unclaim your town's homeblock.");
                }
            }

            // Adjacency rules to avoid disjoint towns.
            ProximityUtil.testAdjacentUnclaimsRulesOrThrow(selection.get(0), town);

            // Economy: Towny uses claimRefundPrice to decide costs/refunds.
            handleUnclaimEconomy(player, town, selection);
        }

        // Fire PreUnclaimCmd event like TownCommand does.
        TownyWorld world = selection.get(0).getTownyWorld();
        if (world == null) {
            throw new TownyException("Selection is not in a Towny world.");
        }

        TownPreUnclaimCmdEvent preEvent = new TownPreUnclaimCmdEvent(town, resident, world, selection);
        Bukkit.getPluginManager().callEvent(preEvent);

        if (preEvent.isCancelled()) {
            String msg = preEvent.getCancelMessage();
            if (msg == null || msg.isEmpty()) {
                msg = "Town unclaim was cancelled.";
            }
            throw new TownyException(msg);
        }
    }

    // -------------------- Internal mirrors of TownCommand logic --------------------

    /**
     * Mirror of TownCommand.vetTownAllowedTheseClaims:
     * - not enough available town blocks
     * - edge/adjacency rules
     */
    private static void vetTownAllowedTheseClaims(Town town,
                                                  boolean outpost,
                                                  List<WorldCoord> selection) throws TownyException {
        if (!town.hasUnlimitedClaims() && selection.size() > town.availableTownBlocks()) {
            // In Towny this uses a Translatable "msg_err_not_enough_blocks".
            throw new TownyException("Town does not have enough available town blocks to claim this selection.");
        }

        // Apply adjacency rules (respects outpost)
        ProximityUtil.testAdjacentClaimsRulesOrThrow(selection.get(0), town, outpost);

        // Non-outpost claims must extend from the existing edge of the town.
        if (!outpost && !isEdgeBlock(town, selection) && !town.getTownBlocks().isEmpty()) {
            // In Towny this is "msg_err_not_attached_edge".
            throw new TownyException("Selection is not attached to the town's existing edge.");
        }
    }

    /**
     * Public helper from TownCommand that checks if any of the coords are edge blocks.
     */
    private static boolean isEdgeBlock(TownBlockOwner owner, List<WorldCoord> worldCoords) {
        List<WorldCoord> visited = new ArrayList<>();

        for (WorldCoord worldCoord : worldCoords) {
            if (isEdgeBlock(owner, worldCoord, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Internal recursive helper for edge detection (copied from TownCommand).
     */
    private static boolean isEdgeBlock(TownBlockOwner owner, WorldCoord worldCoord, List<WorldCoord> visited) {
        for (WorldCoord wc : worldCoord.getCardinallyAdjacentWorldCoords(new boolean[0])) {
            if (!visited.contains(wc)) {
                if (wc.isWilderness()) {
                    visited.add(wc);
                } else {
                    if (wc.getTownBlockOrNull().isOwner(owner)) {
                        return true;
                    }
                    visited.add(wc);
                }
            }
        }
        return false;
    }

    /**
     * Mirror of TownCommand.fireTownPreClaimEventOrThrow.
     */
    private static void fireTownPreClaimEventOrThrow(Player player,
                                                     Town town,
                                                     boolean outpost,
                                                     List<WorldCoord> selection) throws TownyException {
        int blockedClaims = 0;
        String cancelMessage = "";
        boolean isHomeblock = town.getTownBlocks().size() == 0;

        for (WorldCoord coord : selection) {
            TownBlock townBlock = new TownBlock(coord);
            TownPreClaimEvent preClaimEvent = new TownPreClaimEvent(
                    town,
                    townBlock,
                    player,
                    outpost,
                    isHomeblock,
                    false // isOverClaim
            );

            Bukkit.getPluginManager().callEvent(preClaimEvent);

            if (preClaimEvent.isCancelled()) {
                blockedClaims++;
                cancelMessage = preClaimEvent.getCancelMessage();
            }
        }

        if (blockedClaims > 0) {
            // Towny's code formats the message with #blocked and total selection size.
            if (cancelMessage == null || cancelMessage.isEmpty()) {
                cancelMessage = "%d of %d town blocks could not be claimed.";
            }
            throw new TownyException(String.format(cancelMessage, blockedClaims, selection.size()));
        }
    }

    /**
     * Mirror of TownCommand.vetTheTownCanPayIfRequired:
     * ensures the town can afford the selection, and withdraws the money.
     */
    private static void vetTheTownCanPayIfRequired(Player player,
                                                   Town town,
                                                   boolean outpost,
                                                   List<WorldCoord> selection) throws TownyException {
        if (!TownyEconomyHandler.isActive()) {
            return;
        }

        Account townAccount = town.getAccount();
        if (townAccount == null) {
            throw new TownyException("The server economy plugin " + TownyEconomyHandler.getVersion()
                    + " could not return the Town account!");
        }

        int selectionSize = selection.size();
        double blockCost = getSelectionCost(town, outpost, selectionSize);

        if (!townAccount.canPayFromHoldings(blockCost)) {
            double missingAmount = blockCost - townAccount.getHoldingBalance();
            // This mirrors Towny's "msg_no_money_purchased"-style messaging, but simplified.
            String formattedMissing = new DecimalFormat("#").format(missingAmount);
            throw new TownyException("Town cannot afford this claim. Missing: " + formattedMissing);
        }

        // Withdraw asynchronously using Towny's economy executor.
        TownyEconomyHandler.economyExecutor().execute(() ->
                townAccount.withdraw(blockCost,
                        String.format("Town Claim (%d) by %s", selectionSize, player.getName())));
    }

    /**
     * Mirror of TownCommand.getSelectionCost.
     */
    private static double getSelectionCost(Town town,
                                           boolean outpost,
                                           int selectionSize) throws TownyException {
        if (outpost) {
            return TownySettings.getOutpostCost();
        }
        // For non-outpost claims, Towny uses per-block or batched N cost.
        return selectionSize == 1 ? town.getTownBlockCost() : town.getTownBlockCostN(selectionSize);
    }

    /**
     * Economy logic for unclaims, based on TownCommand's use of TownySettings.getClaimRefundPrice():
     *
     * - If refund price < 0: unclaim has a *cost* (town must pay).
     * - If refund price > 0: unclaim *refunds* the town.
     * - If refund price == 0: no money moves.
     */
    private static void handleUnclaimEconomy(Player player,
                                             Town town,
                                             List<WorldCoord> selection) throws TownyException {
        if (!TownyEconomyHandler.isActive()) {
            return;
        }

        double refundPrice = TownySettings.getClaimRefundPrice();
        if (refundPrice == 0.0D) {
            return;
        }

        Account townAccount = town.getAccount();
        if (townAccount == null) {
            throw new TownyException("The server economy plugin " + TownyEconomyHandler.getVersion()
                    + " could not return the Town account!");
        }

        int size = selection.size();

        if (refundPrice < 0.0D) {
            // Negative refund price = actual cost per unclaim.
            double cost = Math.abs(refundPrice * size);
            if (!townAccount.canPayFromHoldings(cost)) {
                String formattedCost = new DecimalFormat("#").format(cost);
                throw new TownyException("Town cannot afford to unclaim this land. Cost: " + formattedCost);
            }

            TownyEconomyHandler.economyExecutor().execute(() ->
                    townAccount.withdraw(cost,
                            String.format("Town Unclaim Cost (%d) by %s", size, player.getName())));
        }
        else {
            // Positive refund price = town earns money per unclaim.
            double refund = refundPrice * size;
            TownyEconomyHandler.economyExecutor().execute(() ->
                    townAccount.deposit(refund,
                            String.format("Town Unclaim Refund (%d) by %s", size, player.getName())));
        }
    }
    static void fireTownPreClaimOrThrow(Player player, Town town, boolean outpost, List<WorldCoord> selection) throws TownyException {
        try {
            Class<?> townCommandClass = Class.forName("com.palmergames.bukkit.towny.command.TownCommand");
            Method method = townCommandClass.getDeclaredMethod(
                    "fireTownPreClaimEventOrThrow",
                    Player.class,
                    Town.class,
                    boolean.class,
                    List.class
            );
            method.setAccessible(true);
            method.invoke(null, player, town, outpost, selection);
        }
        catch (ClassNotFoundException | NoSuchMethodException e) {
            // Helper not available on this Towny version: skip pre-claim events.
        }
        catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof TownyException townyEx) {
                throw townyEx;
            }
            throw new TownyException("An error occurred while running Towny pre-claim checks.");
        }
        catch (IllegalAccessException e) {
            // Should not happen after setAccessible(), but if it does, just skip.
        }
    }

    static void fireTownPreUnclaimOrThrow(Player player, Town town, List<WorldCoord> selection) throws TownyException {
        try {
            Class<?> townCommandClass = Class.forName("com.palmergames.bukkit.towny.command.TownCommand");
            Method method = townCommandClass.getDeclaredMethod(
                    "fireTownPreUnclaimEventOrThrow",
                    Player.class,
                    Town.class,
                    List.class
            );
            method.setAccessible(true);
            method.invoke(null, player, town, selection);
        }
        catch (ClassNotFoundException | NoSuchMethodException e) {
            // Helper not available: skip pre-unclaim events.
        }
        catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof TownyException townyEx) {
                throw townyEx;
            }
            throw new TownyException("An error occurred while running Towny pre-unclaim checks.");
        }
        catch (IllegalAccessException e) {
            // Ignore and skip.
        }
    }
}
