package dev.kejona.crossplatforms.inventory;

import dev.kejona.crossplatforms.handler.FormPlayer;

/**
 * Opens player inventories. It is expected that the implementation of {@link InventoryController} and
 * {@link InventoryFactory} are compatible.
 */
public interface InventoryController {

    void openInventory(FormPlayer recipient, InventoryHandle container, ClickHandler clickHandler);
}
