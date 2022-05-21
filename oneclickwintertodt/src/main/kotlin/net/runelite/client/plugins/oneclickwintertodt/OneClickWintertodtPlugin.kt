package net.runelite.client

import com.google.inject.Provides
import net.runelite.api.*
import net.runelite.api.events.GameTick
import net.runelite.api.events.HitsplatApplied
import net.runelite.api.events.MenuOptionClicked
import net.runelite.api.widgets.WidgetInfo.LEVEL_UP_CONTINUE
import net.runelite.api.widgets.WidgetInfo.BANK_ITEM_CONTAINER as bank
import net.runelite.api.widgets.WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER as bankInventory
import net.runelite.api.widgets.WidgetInfo.INVENTORY as inventory
import net.runelite.client.config.ConfigManager
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.events.ConfigChanged
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.oneclickwintertodt.States
import net.runelite.client.plugins.oneclickwintertodt.magic.*
import net.runelite.client.plugins.oneclickwintertodt.api.entry.Entries
import net.runelite.client.plugins.oneclickwintertodt.util.Log
import net.runelite.client.plugins.oneclickwintertodt.OneClickWintertodtConfig
import net.runelite.client.plugins.oneclickwintertodt.api.inventory.Inventory
import net.runelite.client.plugins.oneclickwintertodt.client.*
import org.pf4j.Extension
import javax.inject.Inject
import kotlin.properties.Delegates

@Extension
@PluginDescriptor(
    name = "One Click Wintertodt",
    description = ":Prayje:",
    tags = ["rebecca, oneclick, one click, wintertodt"]
)
class OneClickWintertodtPlugin : Plugin() {

    @Inject
    private lateinit var config: OneClickWintertodtConfig

    @Inject
    lateinit var client: Client

    @Inject
    lateinit var events: Entries

    @Inject
    lateinit var inventories: Inventory

    companion object : Log()

    @Provides
    fun provideConfig(configManager: ConfigManager): OneClickWintertodtConfig {
        return configManager.getConfig(OneClickWintertodtConfig::class.java)
    }

    var performAction = true
    private var se = true
    private var healPyro = false
    private lateinit var food: List<Int>
    private var health = 25
    private var debug = false
    private var gameStarted = true
    private var bankChest: GameObject? = null
    private var door: GameObject? = null
    private var hammerCrate: GameObject? = null
    private var tinderboxCrate: GameObject? = null
    private var knifeCrate: GameObject? = null
    private var vialCrate: GameObject? = null
    private var unlitBrazier: GameObject? = null
    private var litBrazier: GameObject? = null
    private var brokenBrazier: GameObject? = null
    private var roots: GameObject? = null
    private var herbPatch: GameObject? = null

    override fun startUp() {
        log.info("Starting One Click Wintertodt")
        reset()
    }

    override fun shutDown() {
        log.info("Stopping One Click Wintertodt")
    }

    private fun reset() {
        performAction = true
        gameStarted = false
        food = config.food().id.toList()
        health = config.health()
        healPyro = config.healPyro()
        debug = config.debugger()
        state = States.IDLE
    }

    private var itemContainer: Array<Item> by Delegates.observable(arrayOf()) { property, previous, current ->
        if(state == States.EAT) {
            performAction = true
        }
    }

    private var state by Delegates.observable(States.IDLE) { property, previous, current ->
        if (previous != current) {
            performAction = true
        }
    }

    @Subscribe
    private fun onConfigChanged(event: ConfigChanged) {
        food = config.food().id.toList()
        health = config.health()
        healPyro = config.healPyro()
        debug = config.debugger()
    }

    @Subscribe
    private fun onHitsplatApplied(event: HitsplatApplied) {
        if(event.actor == client.localPlayer) {
            performAction = true
            return
        }
    }

    @Subscribe
    private fun onGameTick(event: GameTick) {
        bankChest = client.findGameObject(BANK_CHEST)
        door = client.findGameObject(DOOR)
        hammerCrate = client.findGameObject(HAMMER_CRATE)
        tinderboxCrate = client.findGameObject(TINDERBOX_CRATE)
        knifeCrate = client.findGameObject(KNIFE_CRATE)
        vialCrate = client.findGameObject(VIAL_CRATE)
        unlitBrazier = client.findGameObject(UNLIT_BRAZIER)?.takeIf { if(se) it.worldLocation == SE_POS else it.worldLocation == SW_POS }
        litBrazier = client.findGameObject(LIT_BRAZIER)?.takeIf { if(se) it.worldLocation == SE_POS else it.worldLocation == SW_POS }
        brokenBrazier = client.findGameObject(BROKEN_BRAZIER)?.takeIf { if(se) it.worldLocation == SE_POS else it.worldLocation == SW_POS }
        roots = client.findGameObject(ROOT)?.takeIf { if(se) it.worldLocation == SE_ROOT_POS else it.worldLocation == SW_ROOT_POS }
        herbPatch = client.findGameObject(HERB_PATCH)?.takeIf { it.worldLocation == SE_HERB_POS }
    }

    @Subscribe
    fun onMenuEntryClicked(event: MenuOptionClicked) {
        with(inventories) {
            with(events) {
                client.getItemContainer(InventoryID.INVENTORY.id)?.let { itemContainer = it.items }
                handleLogic()
                if(debug) {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "State = $state Processing = $performAction", "")
                }
                if (!performAction) {
                    event.consume()
                }
                performAction = false
                when (state) {
                    States.HEAL_PYROMANCER -> {
                        if (inventory.contains(HEALING_POTIONS)) {
                            event.healPyromancer()
                        }
                        return
                    }
                    States.WALK_TO_POS -> {
                        if(se) {
                            event.walkTo(SOUTHEAST)
                        } else {
                            event.walkTo(SOUTHWEST)
                        }
                        return
                    }
                    States.PICK_HERB -> {
                        herbPatch?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.MIX_VIAL -> {
                        inventory.getItem(ItemID.BRUMA_HERB)?.let {
                            event.useOn(it, inventory.getItem(ItemID.REJUVENATION_POTION_UNF))
                            return
                        }
                    }

                    States.WITHDRAW_FOOD -> {
                        bank.getItem(food.last())?.let {
                            event.clickItem(it, 3, bank)
                            return
                        }
                    }
                    States.DEPOSIT_ITEMS -> {
                        bankInventory.getItem(ItemID.SUPPLY_CRATE)?.let {
                            event.clickItem(it, 2, bankInventory)
                            return
                        }
                    }
                    States.EAT -> {
                        food.forEach {
                            try {
                                inventory.getItem(it)?.let { eat ->
                                    state = States.IDLE
                                    event.clickItem(eat, 2, inventory)
                                    return
                                }
                            } catch (e: Exception) {
                                println(e.stackTrace)
                            }
                        }
                    }
                    States.RETURN_INSIDE -> {
                        door?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.LEAVE_DOOR -> {
                        door?.let {
                            event.use(it)
                            state = States.CONFIRM_EXIT
                            return
                        }
                    }
                    States.CONFIRM_EXIT -> {
                        event.talk(1, 14352385)
                        return
                    }
                    States.NEED_VIAL -> {
                        event.use(vialCrate!!)
                        return
                    }
                    States.NEED_HAMMER -> {
                        event.use(hammerCrate!!)
                        return
                    }
                    States.NEED_KNIFE -> {
                        event.use(knifeCrate!!)
                        return
                    }
                    States.NEED_TINDERBOX -> {
                        event.use(tinderboxCrate!!)
                        return
                    }
                    States.LIGHT_BRAZIER, States.GO_TO_BRAZIER -> {
                        unlitBrazier?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.FIREMAKING -> {
                        litBrazier?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.WOODCUTTING -> {
                        roots?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.FLETCHING -> {
                        inventory.getItem(ItemID.BRUMA_ROOT)?.let {
                            event.useOn(it, inventory.getItem(ItemID.KNIFE))
                            return
                        }
                    }
                    States.BANK -> {
                        bankChest?.let {
                            event.use(it)
                            return
                        }
                    }
                    States.IDLE -> {}
                    else -> {}
                }
            }
        }
    }

    private fun handleLogic() {
        with(inventories) {
            if(client.getWidget(LEVEL_UP_CONTINUE) != null) {
                performAction = true
            }
            when (client.localPlayer!!.worldLocation.regionID) {
                BANK_REGION -> {
                    if (client.banking()) {
                        if (bankInventory.quantity(food) <= 4) {
                            state = States.WITHDRAW_FOOD
                            return
                        }
                        if (bankInventory.contains(ItemID.SUPPLY_CRATE)) {
                            state = States.DEPOSIT_ITEMS
                            return
                        }
                    }
                    bankChest?.let {
                        if (inventory.quantity(food) <= 4 ||  inventory.contains(ItemID.SUPPLY_CRATE)) {
                            if (!client.localPlayer.isMoving) {
                                performAction = true
                            }
                            state = States.BANK
                            return
                        }
                    }
                    state = States.RETURN_INSIDE
                    return
                }
                LOBBY_REGION -> {
                    if (state == States.CONFIRM_EXIT) {
                        if (client.getWidget(14352385) != null) {
                            performAction = true
                        }
                        return
                    }
                    if (state == States.LEAVE_DOOR) {
                        return
                    }
                    if (client.localPlayer.worldLocation.isInArea(LOBBY_AREA) && !client.localPlayer.isMoving) {
                        performAction = true
                    }

                    if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= health) {
                        if (!inventory.contains(food)) {
                            state = States.LEAVE_DOOR
                            return
                        }
                        state = States.EAT
                        return
                    }
                    if (healPyro && !inventory.contains(ItemID.REJUVENATION_POTION_UNF) && !inventory.contains(HEALING_POTIONS)) {
                        state = States.NEED_VIAL
                        return
                    }
                    if (!inventory.contains(ItemID.HAMMER)) {
                        state = States.NEED_HAMMER
                        return
                    }
                    if (!inventory.contains(ItemID.KNIFE)) {
                        state = States.NEED_KNIFE
                        return
                    }
                    if (!inventory.contains(ItemID.TINDERBOX)) {
                        state = States.NEED_TINDERBOX
                        return
                    }
                    if ((!gameStarted && inventory.quantity(food) <= 4) || inventory.contains(ItemID.SUPPLY_CRATE)) {
                        state = States.LEAVE_DOOR
                        return
                    }
                    if(!healPyro) {
                        if(se) {
                            if(client.findNpc(NpcID.INCAPACITATED_PYROMANCER)?.worldLocation == SE_PYROMANCER_POS) {
                                se = false
                            }
                        } else {
                            if(client.findNpc(NpcID.INCAPACITATED_PYROMANCER)?.worldLocation == SW_PYROMANCER_POS) {
                                se = true
                            }
                        }
                    }
                    if (roots == null || unlitBrazier == null && litBrazier == null && brokenBrazier == null) {
                        state = States.WALK_TO_POS
                        performAction = true
                        return
                    }
                    if (gameStarted && state == States.GO_TO_BRAZIER && client.getWidget(INTERFACE_TEXT)?.text?.isEmpty()!!) {
                        state = States.LIGHT_BRAZIER
                        return
                    }
                    if (client.getWidget(INTERFACE_TEXT)?.text?.contains("0:00", true) == true) {
                        gameStarted = true
                    }
                    if (client.getWidget(INTERFACE_TEXT)?.text?.contains("returns in", true) == true) {
                        state = States.GO_TO_BRAZIER
                        gameStarted = false
                        return
                    }
                    if (litBrazier != null && !gameStarted) {
                        gameStarted = true
                    }
                    if (healPyro && client.findNpc(NpcID.INCAPACITATED_PYROMANCER)?.worldLocation == SE_PYROMANCER_POS && inventory.contains(HEALING_POTIONS)) {
                        state = States.HEAL_PYROMANCER
                        return
                    }
                    if (healPyro && inventory.contains(ItemID.BRUMA_HERB) && inventory.contains(ItemID.REJUVENATION_POTION_UNF)) {
                        state = States.MIX_VIAL
                        return
                    }
                    if (healPyro && client.findNpc(NpcID.INCAPACITATED_PYROMANCER)?.worldLocation == SE_PYROMANCER_POS && inventory.contains(ItemID.REJUVENATION_POTION_UNF) && !inventory.contains(ItemID.BRUMA_HERB)) {
                        state = States.PICK_HERB
                        return
                    }
                    if (state == States.LIGHT_BRAZIER && unlitBrazier != null) {
                        return
                    }
                    if (state == States.FIREMAKING && unlitBrazier != null) {
                        state = States.LIGHT_BRAZIER
                        return
                    }
                    if (inventory.contains(ItemID.BRUMA_ROOT) && (inventory.freeSpace() <= 0 || (inventory.quantity(ItemID.BRUMA_ROOT) + inventory.quantity(ItemID.BRUMA_KINDLING)) >= 10)) {
                        state = States.FLETCHING
                        return
                    }
                    if (!inventory.contains(ItemID.BRUMA_KINDLING)) {
                        if (client.localPlayer!!.animation == -1) {
                            performAction = true
                        }
                        state = States.WOODCUTTING
                        return
                    }
                    if (inventory.contains(ItemID.BRUMA_KINDLING) && !inventory.contains(ItemID.BRUMA_ROOT) && litBrazier != null) {
                        state = States.FIREMAKING
                        return
                    }
                    if (brokenBrazier != null) {
                        state = States.REPAIR
                        return
                    }
                    if (unlitBrazier != null) {
                        state = States.LIGHT_BRAZIER
                        return
                    }
                    state = States.IDLE
                    performAction = true
                    return
                }
                else -> {}
            }
        }
    }
}