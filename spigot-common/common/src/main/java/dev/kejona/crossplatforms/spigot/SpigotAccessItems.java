package dev.kejona.crossplatforms.spigot;

import dev.kejona.crossplatforms.Logger;
import dev.kejona.crossplatforms.accessitem.AccessItem;
import dev.kejona.crossplatforms.accessitem.AccessItemRegistry;
import dev.kejona.crossplatforms.config.ConfigManager;
import dev.kejona.crossplatforms.handler.BedrockHandler;
import dev.kejona.crossplatforms.handler.FormPlayer;
import dev.kejona.crossplatforms.handler.Placeholders;
import dev.kejona.crossplatforms.inventory.ItemHandle;
import dev.kejona.crossplatforms.permission.Permissions;
import dev.kejona.crossplatforms.resolver.PlayerResolver;
import dev.kejona.crossplatforms.resolver.Resolver;
import dev.kejona.crossplatforms.spigot.adapter.NbtAccessor;
import dev.kejona.crossplatforms.spigot.adapter.SpigotAdapter;
import dev.kejona.crossplatforms.spigot.handler.SpigotPlayer;
import lombok.AllArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public final class SpigotAccessItems extends AccessItemRegistry implements Listener {

    private final Logger logger = Logger.get();

    private final NbtAccessor nbt;
    private final BedrockHandler bedrockHandler;
    private final Placeholders placeholders;

    public SpigotAccessItems(JavaPlugin plugin,
                             SpigotAdapter adapter,
                             ConfigManager configManager,
                             Permissions permissions,
                             BedrockHandler bedrockHandler,
                             Placeholders placeholders) {
        super(configManager, permissions);
        this.nbt = adapter.nbtAccessor(plugin);
        this.bedrockHandler = bedrockHandler;
        this.placeholders = placeholders;
    }

    public void setItemId(@Nonnull ItemStack itemStack, @Nonnull String identifier) {
        nbt.setCustomString(itemStack, AccessItem.STATIC_IDENTIFIER, identifier);
    }

    /**
     * Attempt to retrieve the Access Item ID that an ItemStack points to. The Access Item ID may or may not refer
     * to an actual AccessItem
     * @param itemStack The ItemStack to check
     * @return The AccessItem ID if the ItemStack contained the name, null if not.
     */
    @Nullable
    public String getItemId(@Nonnull ItemStack itemStack) {
        return nbt.getCustomString(itemStack, AccessItem.STATIC_IDENTIFIER);
    }

    public ItemStack createItemStack(AccessItem accessItem, Player player) {
        FormPlayer formPlayer = new SpigotPlayer(player);
        Resolver resolver = new PlayerResolver(formPlayer, placeholders);

        ItemHandle handle = accessItem.convertAndResolve(formPlayer, resolver);
        ItemStack item = handle.castedHandle(ItemStack.class);

        setItemId(item, accessItem.getIdentifier());
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) { // opening menus through access items
        Action action = event.getAction();
        if (action != Action.PHYSICAL) {
            ItemStack item = event.getItem();
            if (item != null) {
                String id = getItemId(item);
                if (id != null) {
                    // Don't allow using the item to break blocks
                    // If it was a right click, using the access item should be the only behaviour
                    event.setCancelled(true);

                    Player player = event.getPlayer();
                    if (isEnabled()) {
                        AccessItem access = getItem(id);
                        if (access == null) {
                            // item no longer exists
                            remove(player, item, RemoveReason.ITEM_REMOVED);
                        } else if (player.hasPermission(access.permission(AccessItem.Limit.POSSESS))) {
                            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                                access.trigger(new SpigotPlayer(player));
                            }
                        } else {
                            // player no longer has permission to have the item
                            remove(player, item, RemoveReason.IMPERMISSIBLE);
                        }
                    } else {
                        // item no longer exists because the registry has been disabled
                        remove(player, item, RemoveReason.ITEMS_DISABLED);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) { // keep the access items in place
        // todo: don't allow duplication for creative players
        ItemStack item = event.getCurrentItem();
        if (item != null) {
            String id = getItemId(item);
            if (id != null) {
                // the item is or was an access item

                HumanEntity human = event.getWhoClicked();
                if (isEnabled()) {
                    AccessItem access = getItem(id);
                    if (access == null) {
                        remove(human, item, RemoveReason.ITEM_REMOVED);
                    } else if (!human.hasPermission(access.permission(AccessItem.Limit.POSSESS))) {
                        // doesn't have permission to have it
                        remove(human, item, RemoveReason.IMPERMISSIBLE);
                    } else if (!human.hasPermission(access.permission(AccessItem.Limit.MOVE))) {
                        // has permission to have it, but not move it
                        event.setCancelled(true);
                    }

                    // they have permission to have it and move it - no action required
                } else {
                    remove(human, item, RemoveReason.ITEMS_DISABLED);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) { // restricting dropping
        ItemStack item = event.getItemDrop().getItemStack();
        String id = getItemId(item);
        if (id != null) {
            AccessItem access = getItem(id);
            Player player = event.getPlayer();
            if (access == null) {
                // access item no longer exists
                player.sendMessage(RemoveReason.ITEM_REMOVED.message);
                event.getItemDrop().remove();
            } else if (player.hasPermission(access.permission(AccessItem.Limit.DROP))) {
                if (!player.hasPermission(access.permission(AccessItem.Limit.PRESERVE))) {
                    // has permission to "drop" it but the item is destroyed
                    event.getItemDrop().remove();
                }
            } else {
                // doesn't have permission to drop it, cancel the event
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) { // restricting dropping
        Player player = event.getEntity();
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            String id = getItemId(iterator.next());
            if (id != null) {
                AccessItem access = getItem(id);
                if (access == null || !player.hasPermission(access.permission(AccessItem.Limit.PRESERVE))) {
                    // the access item no longer exists and should be removed
                    // OR the player no longer has permission for it, so the item should be removed.
                    iterator.remove();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) { // give the access item when the player joins
        regive(event.getPlayer(), AccessItem::isOnJoin);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) { // give the access item when the player respawns
        regive(event.getPlayer(), AccessItem::isOnRespawn);
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        regive(event.getPlayer(), AccessItem::isOnWorldChange);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                String id = getItemId(item);
                if (id != null) {
                    AccessItem access = getItem(id);
                    if (access == null || !access.isPersist()) {
                        // the access item no longer exists and should be removed
                        // OR the access item is not allowed to be persisted

                        // we could check for permission to have it here, but that'll happen when they join again
                        player.getInventory().remove(item);
                    }
                }
            }
        }
    }

    public void handlePlayerPickupItem(Player player, Item item, Cancellable event) {
        String id = getItemId(item.getItemStack());
        if (id != null) {
            AccessItem access = getItem(id);
            if (access == null) {
                // the item no longer exists, don't allow picking it up
                event.setCancelled(true);
            } else if (!player.hasPermission(access.permission(AccessItem.Limit.POSSESS))) {
                // they don't have permission to have it
                event.setCancelled(true);
            }
        }
    }

    private void remove(HumanEntity player, ItemStack item, RemoveReason reason) {
        player.getInventory().remove(item);
        player.sendMessage(reason.message);
    }

    private void regive(Player player, Predicate<AccessItem> give) {
        List<String> contained = new ArrayList<>(); // Access items the player already has and that will not be removed

        // Remove any access items that are now longer allowed
        for (ItemStack item : player.getInventory()) {
            if (item != null) {
                String id = getItemId(item);
                if (id != null) {
                    AccessItem access = getItem(id);
                    if (access == null) {
                        // access item no longer exists
                        player.getInventory().remove(item);
                    } else {
                        if (player.hasPermission(access.permission(AccessItem.Limit.POSSESS))) {
                            contained.add(access.getIdentifier());
                            logger.debug(String.format("%s is keeping access item %s", player.getName(), access.getIdentifier()));
                        } else {
                            player.getInventory().remove(item);
                            logger.debug(String.format("Removed %s from %s because they don't have permission for it", access.getIdentifier(), player.getName()));
                        }
                    }
                }
            }
        }

        // Give any access items that should be given
        boolean changedHand = false; // If we have changed the item the player is holding
        for (AccessItem access : getItems().values()) {
            if (give.test(access) && access.getPlatform().matches(player.getUniqueId(), bedrockHandler) && player.hasPermission(access.permission(AccessItem.Limit.EVENT))) {
                if (!contained.contains(access.getIdentifier())) {
                    if (setHeldSlot() && !changedHand) {
                        giveAccessItem(new SpigotPlayer(player), access, true);
                        changedHand = true;
                        logger.debug("Set held slot to " + access.getSlot());
                    } else {
                        giveAccessItem(new SpigotPlayer(player), access, false);
                    }

                    logger.debug(String.format("Gave access item %s to %s", access.getIdentifier(), player.getName()));
                } else {
                    logger.debug(String.format("%s has permission for access item %s, but they already have it", player.getName(), access.getIdentifier()));
                }
            }
        }
    }

    @Override
    public boolean giveAccessItem(FormPlayer player, AccessItem accessItem, boolean setHeldSlot) {
        return giveAccessItem(player.getHandle(Player.class), accessItem, setHeldSlot);
    }

    /**
     * Gives a player an access item
     * @param player The player to give the access item to
     * @param accessItem The access item to give
     * @param setHeldSlot True if the player's selected item should be forced to the access item
     * @return True if the access item was successfully given. False if the inventory was too full.
     */
    public boolean giveAccessItem(Player player, AccessItem accessItem, boolean setHeldSlot) {
        ItemStack accessItemStack = createItemStack(accessItem, player); // todo update placeholders after the fact. but when?

        int desiredSlot = accessItem.getSlot();
        ItemStack blockingItem = player.getInventory().getItem(desiredSlot);
        boolean success = false;
        if (blockingItem == null || blockingItem.getType() == Material.AIR) {
            // put the item in the desired place
            player.getInventory().setItem(desiredSlot, accessItemStack);
            success = true;
        } else {
            for (int i = 0; i < 9; i++) {
                // Try and find an empty slot

                ItemStack otherItem = player.getInventory().getItem(i);
                if (otherItem == null || otherItem.getType() == Material.AIR) {
                    // slot is empty, move the item that is blocking us to it
                    player.getInventory().setItem(i, blockingItem);
                    // put the access item in the slot that is no longer blocked
                    player.getInventory().setItem(desiredSlot, accessItemStack);
                    success = true;
                    break;
                }
            }
            // If the player doesn't have the space in their hotbar then they don't get it
        }

        if (success) {
            if (setHeldSlot) {
                // Set the held item to the slot of the access item
                player.getInventory().setHeldItemSlot(accessItem.getSlot());
            }
            return true;
        } else {
            // todo: send message about no success
            return false;
        }
    }

    @AllArgsConstructor
    private enum RemoveReason {
        ITEMS_DISABLED("Access Items are currently disabled."),
        ITEM_REMOVED("That Access Item doesn't exist anymore."),
        IMPERMISSIBLE("You don't have permission to have that.");

        @Nonnull
        final String message;
    }
}
