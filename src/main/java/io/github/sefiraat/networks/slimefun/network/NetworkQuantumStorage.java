package io.github.sefiraat.networks.slimefun.network;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.guizhanss.guizhanlib.java.BooleanHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkQuantumStorage extends SlimefunItem implements DistinctiveItem {

    private static final int[] SIZES = new int[]{
        4096,
        32768,
        262144,
        2097152,
        16777216,
        134217728,
        1073741824,
        Integer.MAX_VALUE
    };

    private static final String WIKI_PAGE = "network-storage/quantum-storage";

    public static final String BS_AMOUNT = "stored_amount";
    public static final String BS_VOID = "void_excess";

    public static final int INPUT_SLOT = 1;
    public static final int ITEM_SLOT = 4;
    public static final int ITEM_SET_SLOT = 13;
    public static final int OUTPUT_SLOT = 7;

    private static final ItemStack BACK_INPUT = new CustomItemStack(
        Material.GREEN_STAINED_GLASS_PANE,
        Theme.PASSIVE + "输入"
    );

    private static final ItemStack BACK_ITEM = new CustomItemStack(
        Material.BLUE_STAINED_GLASS_PANE,
        Theme.PASSIVE + "当前存储的物品"
    );

    private static final ItemStack NO_ITEM = new CustomItemStack(
        Material.RED_STAINED_GLASS_PANE,
        Theme.ERROR + "未指定物品",
        Theme.PASSIVE + "拿起物品并点击下方的按钮",
        Theme.PASSIVE + "以设置量子存储保存的物品"
    );

    private static final ItemStack SET_ITEM = new CustomItemStack(
        Material.LIME_STAINED_GLASS_PANE,
        Theme.SUCCESS + "设置",
        Theme.PASSIVE + "拿起物品并点击这里以设置物品",
        Theme.CLICK_INFO + "Shift+左键点击" + Theme.PASSIVE + "切换满载清空输入");

    private static final ItemStack BACK_OUTPUT = new CustomItemStack(
        Material.ORANGE_STAINED_GLASS_PANE,
        Theme.PASSIVE + "输出"
    );

    private static final int[] INPUT_SLOTS = new int[]{0, 2};
    private static final int[] ITEM_SLOTS = new int[]{3, 5};
    private static final int[] OUTPUT_SLOTS = new int[]{6, 8};
    private static final int[] BACKGROUND_SLOTS = new int[]{9, 10, 11, 12, 14, 15, 16, 17};

    private static final Map<Location, QuantumCache> CACHES = new HashMap<>();

    static {
        final ItemMeta itemMeta = NO_ITEM.getItemMeta();
        PersistentDataAPI.setBoolean(itemMeta, Keys.newKey("display"), true);
        NO_ITEM.setItemMeta(itemMeta);
    }

    private final List<Integer> slotsToDrop = new ArrayList<>();
    private final int maxAmount;

    public NetworkQuantumStorage(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, int maxAmount) {
        super(itemGroup, item, recipeType, recipe);
        this.maxAmount = maxAmount;
        slotsToDrop.add(INPUT_SLOT);
        slotsToDrop.add(OUTPUT_SLOT);
    }

    @Override
    public void preRegister() {
        addItemHandler(
            new BlockTicker() {
                @Override
                public boolean isSynchronized() {
                    return false;
                }

                @Override
                public void tick(Block b, SlimefunItem item, SlimefunBlockData data) {
                    onTick(b);
                }
            },
            new BlockBreakHandler(false, false) {
                @Override
                @ParametersAreNonnullByDefault
                public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                    onBreak(event);
                }
            },
            new BlockPlaceHandler(false) {
                @Override
                public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                    onPlace(event);
                }
            }
        );
    }

    private void onTick(Block block) {
        final BlockMenu blockMenu = StorageCacheUtils.getMenu(block.getLocation());

        if (blockMenu == null) {
            CACHES.remove(block.getLocation());
            return;
        }

        final QuantumCache cache = CACHES.get(blockMenu.getLocation());

        if (cache == null) {
            return;
        }

        if (blockMenu.hasViewer()) {
            updateDisplayItem(blockMenu, cache);
        }

        // Move items from the input slot into the card
        final ItemStack input = blockMenu.getItemInSlot(INPUT_SLOT);
        if (input != null && input.getType() != Material.AIR) {
            tryInputItem(blockMenu.getLocation(), new ItemStack[]{input}, cache);
        }

        // Output items
        final ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
        ItemStack fetched = null;
        if (output == null || output.getType() == Material.AIR) {
            // No item in output, try output
            fetched = cache.withdrawItem();
        } else if (StackUtils.itemsMatch(cache, output, true) && output.getAmount() < output.getMaxStackSize()) {
            // There is an item, but it's not filled so lets top it up if we can
            final int requestAmount = output.getMaxStackSize() - output.getAmount();
            fetched = cache.withdrawItem(requestAmount);
        }

        if (fetched != null && fetched.getType() != Material.AIR) {
            blockMenu.pushItem(fetched, OUTPUT_SLOT);
            syncBlock(blockMenu.getLocation(), cache);
        }

        CACHES.put(blockMenu.getLocation().clone(), cache);
    }

    private void toggleVoid(@Nonnull BlockMenu blockMenu) {
        final QuantumCache cache = CACHES.get(blockMenu.getLocation());
        cache.setVoidExcess(!cache.isVoidExcess());
        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    private void setItem(@Nonnull BlockMenu blockMenu, @Nonnull Player player) {
        final ItemStack itemStack = player.getItemOnCursor().clone();

        if (isBlacklisted(itemStack)) {
            return;
        }

        final QuantumCache cache = CACHES.get(blockMenu.getLocation());
        if (cache == null || cache.getAmount() > 0) {
            player.sendMessage(Theme.WARNING + "量子存储必须为空才能更换物品");
            return;
        }
        itemStack.setAmount(1);
        cache.setItemStack(itemStack);
        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    @Override
    public void postRegister() {
        addWikiPage(WIKI_PAGE);
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                for (int i : INPUT_SLOTS) {
                    addItem(i, BACK_INPUT, (p, slot, item, action) -> false);
                }
                for (int i : ITEM_SLOTS) {
                    addItem(i, BACK_ITEM, (p, slot, item, action) -> false);
                }
                for (int i : OUTPUT_SLOTS) {
                    addItem(i, BACK_OUTPUT, (p, slot, item, action) -> false);
                }
                addItem(ITEM_SET_SLOT, SET_ITEM, (p, slot, item, action) -> false);
                addMenuClickHandler(ITEM_SLOT, ChestMenuUtils.getEmptyClickHandler());
                drawBackground(BACKGROUND_SLOTS);
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return new int[]{INPUT_SLOT};
                } else if (flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{OUTPUT_SLOT};
                }
                return new int[0];
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block block) {
                menu.addMenuClickHandler(ITEM_SET_SLOT, (p, slot, item, action) -> {
                    if (action.isShiftClicked()) {
                        toggleVoid(menu);
                    } else {
                        setItem(menu, p);
                    }
                    return false;
                });

                // Cache may exist if placed with items held inside.
                QuantumCache cache = CACHES.get(block.getLocation());
                if (cache == null) {
                    cache = addCache(menu);
                }
                updateDisplayItem(menu, cache);
            }
        };
    }

    private QuantumCache addCache(@Nonnull BlockMenu blockMenu) {
        final Location location = blockMenu.getLocation();
        var blockData = StorageCacheUtils.getBlock(location);
        final String amountString = blockData.getData(BS_AMOUNT);
        final String voidString = blockData.getData(BS_VOID);
        final int amount = amountString == null ? 0 : Integer.parseInt(amountString);
        final boolean voidExcess = voidString == null || Boolean.parseBoolean(voidString);
        final ItemStack itemStack = blockMenu.getItemInSlot(ITEM_SLOT);

        QuantumCache cache = createCache(itemStack, blockMenu, amount, voidExcess);

        CACHES.put(location, cache);
        return cache;
    }

    private QuantumCache createCache(@Nullable ItemStack itemStack, @Nonnull BlockMenu menu, int amount, boolean voidExcess) {
        if (itemStack == null || itemStack.getType() == Material.AIR || isDisplayItem(itemStack)) {
            menu.addItem(ITEM_SLOT, NO_ITEM);
            return new QuantumCache(null, 0, this.maxAmount, true);
        } else {
            final ItemStack clone = itemStack.clone();
            final ItemMeta itemMeta = clone.getItemMeta();
            final List<String> lore = itemMeta.getLore();
            for (int i = 0; i < 3; i++) {
                lore.remove(lore.size() - 1);
            }
            itemMeta.setLore(lore.isEmpty() ? null : lore);
            clone.setItemMeta(itemMeta);

            final QuantumCache cache = new QuantumCache(clone, amount, this.maxAmount, voidExcess);

            updateDisplayItem(menu, cache);
            return cache;
        }
    }

    private boolean isDisplayItem(@Nonnull ItemStack itemStack) {
        return PersistentDataAPI.getBoolean(itemStack.getItemMeta(), Keys.newKey("display"));
    }

    protected void onBreak(@Nonnull BlockBreakEvent event) {
        final Location location = event.getBlock().getLocation();
        final BlockMenu blockMenu = StorageCacheUtils.getMenu(event.getBlock().getLocation());

        if (blockMenu != null) {
            final QuantumCache cache = CACHES.remove(blockMenu.getLocation());

            if (cache != null && cache.getAmount() > 0 && cache.getItemStack() != null) {
                final ItemStack itemToDrop = this.getItem().clone();
                final ItemMeta itemMeta = itemToDrop.getItemMeta();

                DataTypeMethods.setCustom(itemMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, cache);
                cache.addMetaLore(itemMeta);
                itemToDrop.setItemMeta(itemMeta);
                location.getWorld().dropItem(location.clone().add(0.5, 0.5, 0.5), itemToDrop);
                event.setDropItems(false);
            }

            for (int i : this.slotsToDrop) {
                blockMenu.dropItems(location, i);
            }
        }
    }

    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        final ItemStack itemStack = event.getItemInHand();
        final ItemMeta itemMeta = itemStack.getItemMeta();
        final QuantumCache cache = DataTypeMethods.getCustom(itemMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);

        if (cache == null) {
            return;
        }

        syncBlock(event.getBlock().getLocation(), cache);
        CACHES.put(event.getBlock().getLocation(), cache);
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    @ParametersAreNonnullByDefault
    public static void tryInputItem(Location location, ItemStack[] input, QuantumCache cache) {
        if (cache.getItemStack() == null) {
            return;
        }
        for (ItemStack itemStack : input) {
            if (isBlacklisted(itemStack)) {
                continue;
            }
            if (StackUtils.itemsMatch(cache, itemStack, true)) {
                int leftover = cache.increaseAmount(itemStack.getAmount());
                itemStack.setAmount(leftover);
            }
        }
        syncBlock(location, cache);
    }

    private static boolean isBlacklisted(@Nonnull ItemStack itemStack) {
        return itemStack.getType() == Material.AIR
            || itemStack.getType().getMaxDurability() < 0
            || Tag.SHULKER_BOXES.isTagged(itemStack.getType())
            || SlimefunItem.getByItem(itemStack) instanceof NetworkQuantumStorage;
    }

    @ParametersAreNonnullByDefault
    @Nullable
    public static ItemStack getItemStack(@Nonnull QuantumCache cache, @Nonnull BlockMenu blockMenu) {
        if (cache.getItemStack() == null || cache.getAmount() <= 0) {
            return null;
        }
        return getItemStack(cache, blockMenu, cache.getItemStack().getMaxStackSize());
    }

    @ParametersAreNonnullByDefault
    @Nullable
    public static ItemStack getItemStack(@Nonnull QuantumCache cache, @Nonnull BlockMenu blockMenu, int amount) {
        if (cache.getAmount() < amount) {
            // Storage has no content or not enough, mix and match!
            ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
            ItemStack fetched = cache.withdrawItem(amount);

            if (output != null
                && output.getType() != Material.AIR
                && StackUtils.itemsMatch(cache, output, true)
            ) {
                // We have an output item we can use also
                if (fetched == null || fetched.getType() == Material.AIR) {
                    // Storage is totally empty - just use output slot
                    fetched = output.clone();
                    if (fetched.getAmount() > amount) {
                        fetched.setAmount(amount);
                    }
                    output.setAmount(output.getAmount() - fetched.getAmount());
                } else {
                    // Storage has content, lets add on top of it
                    int additional = Math.min(amount - fetched.getAmount(), output.getAmount());
                    output.setAmount(output.getAmount() - additional);
                    fetched.setAmount(fetched.getAmount() + additional);
                }
            }
            syncBlock(blockMenu.getLocation(), cache);
            return fetched;
        } else {
            // Storage has everything we need
            syncBlock(blockMenu.getLocation(), cache);
            return cache.withdrawItem(amount);
        }
    }

    private static void updateDisplayItem(@Nonnull BlockMenu menu, @Nonnull QuantumCache cache) {
        if (cache.getItemStack() == null) {
            menu.replaceExistingItem(ITEM_SLOT, NO_ITEM);
        } else {
            final ItemStack itemStack = cache.getItemStack().clone();
            final ItemMeta itemMeta = itemStack.getItemMeta();
            final List<String> lore = itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(Theme.CLICK_INFO + "满载清空输入: " + Theme.PASSIVE + BooleanHelper.enabledOrDisabled(cache.isVoidExcess()));
            lore.add(Theme.CLICK_INFO + "数量: " + Theme.PASSIVE + cache.getAmount());
            itemMeta.setLore(lore);
            itemStack.setItemMeta(itemMeta);
            itemStack.setAmount(1);
            menu.replaceExistingItem(ITEM_SLOT, itemStack);
        }
    }

    private static void syncBlock(@Nonnull Location location, @Nonnull QuantumCache cache) {
        var blockData = StorageCacheUtils.getBlock(location);
        blockData.setData(BS_AMOUNT, String.valueOf(cache.getAmount()));
        blockData.setData(BS_VOID, String.valueOf(cache.isVoidExcess()));
    }

    public static Map<Location, QuantumCache> getCaches() {
        return CACHES;
    }

    public static int[] getSizes() {
        return SIZES;
    }

    @Override
    public boolean canStack(@Nonnull ItemMeta sfItemMeta, @Nonnull ItemMeta itemMeta) {
        return sfItemMeta.getPersistentDataContainer().equals(itemMeta.getPersistentDataContainer());
    }
}
