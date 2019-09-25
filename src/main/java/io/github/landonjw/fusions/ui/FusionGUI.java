package io.github.landonjw.fusions.ui;

import com.mcsimonflash.sponge.teslalibs.inventory.Action;
import com.mcsimonflash.sponge.teslalibs.inventory.Element;
import com.mcsimonflash.sponge.teslalibs.inventory.Layout;
import com.mcsimonflash.sponge.teslalibs.inventory.View;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.items.ItemPixelmonSprite;
import io.github.landonjw.fusions.Fusions;
import io.github.landonjw.fusions.api.Fusion;
import io.github.landonjw.fusions.configuration.ConfigManager;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.DyeColors;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.enchantment.Enchantment;
import org.spongepowered.api.item.enchantment.EnchantmentTypes;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI that allows a player to fuse their Pokemon.
 * GUI consists of a players party on the left, and a slot for a fused Pokemon on the right.
 * The player can select two Pokemon by left and right clicking, and then left click resulting Pokemon to fuse.
 * @author landonjw
 * @since 9/25/2019 Version 1.0.0
 */
public class FusionGUI {
    /** Player doing the fusion. */
    private Player player;
    /** Fusion player is creating. */
    private Fusion fusion;
    /** If the GUI should hide resulting IVs for fusion. */
    private boolean hideResultIVs;

    /**
     * Constructor for the GUI. Creates a new Fusion object from player.
     * @param player Player doing the fusion.
     */
    public FusionGUI(Player player){
        this.player = player;
        this.fusion = new Fusion(player);

        this.hideResultIVs = ConfigManager.getConfigNode("GUI-Features", "Hide-Fusion-IVs").getBoolean();
    }

    /**
     * Builds the GUI and displays it to the player.
     */
    public void openGUI(){
        HashMap<Integer, Element> elements = new HashMap<>();

        /* -------------------------------------------------------------------
         * Creates a bunch of items to be used just for empty space in GUI
         * -------------------------------------------------------------------
         */
        ItemStack itemBlueFiller = ItemStack.of(ItemTypes.STAINED_GLASS_PANE, 1);
        itemBlueFiller.offer(Keys.DYE_COLOR, DyeColors.BLUE);
        itemBlueFiller.offer(Keys.DISPLAY_NAME, Text.EMPTY);
        Element blueFiller = Element.of(itemBlueFiller);

        ItemStack itemBlackFiller = ItemStack.of(ItemTypes.STAINED_GLASS_PANE, 1);
        itemBlackFiller.offer(Keys.DYE_COLOR, DyeColors.BLACK);
        itemBlackFiller.offer(Keys.DISPLAY_NAME, Text.EMPTY);
        Element blackFiller = Element.of(itemBlackFiller);

        ItemStack itemWhiteFiller = ItemStack.of(ItemTypes.STAINED_GLASS_PANE, 1);
        itemWhiteFiller.offer(Keys.DYE_COLOR, DyeColors.WHITE);
        itemWhiteFiller.offer(Keys.DISPLAY_NAME, Text.EMPTY);
        Element whiteFiller = Element.of(itemWhiteFiller);

        ItemStack itemGrayFiller = ItemStack.of(ItemTypes.STAINED_GLASS_PANE, 1);
        itemGrayFiller.offer(Keys.DYE_COLOR, DyeColors.GRAY);
        itemGrayFiller.offer(Keys.DISPLAY_NAME, Text.EMPTY);
        Element grayFiller = Element.of(itemGrayFiller);

        /* -------------------------------------------------------------------
         * Creates items for each Pokemon in player's party in an empty box
         * on the left side of fusion GUI.
         * -------------------------------------------------------------------
         */
        Pokemon[] pokemonList = Pixelmon.storageManager.getParty(player.getUniqueId()).getAll();
        //List of positions in Chest UI to place Pokemon.
        int[] positionArray = {10, 12, 14, 28, 30, 32};

        //Iterate through all Pokemon in players party and create items
        for(int i = 0; i < pokemonList.length; i++){
            if(pokemonList[i] != null){
                ItemStack itemPokemon = (ItemStack) (Object) ItemPixelmonSprite.getPhoto(pokemonList[i]);
                itemPokemon.offer(Keys.DISPLAY_NAME, Text.of(TextColors.AQUA, TextStyles.BOLD, pokemonList[i].getSpecies().name));

                //Generates lore for Pokemon displaying it's IVs and valuable attributes.
                ArrayList<Text> lore = new ArrayList<>();
                lore.add(Text.EMPTY);

                int[] ivs = pokemonList[i].getIVs().getArray();
                lore.addAll(getIVLore(ivs));

                lore.add(Text.EMPTY);
                if (pokemonList[i].isShiny()) {
                    lore.add(Text.of(TextColors.AQUA, "Shiny"));
                }
                if (pokemonList[i].getAbilitySlot() == 2) {
                    lore.add(Text.of(TextColors.AQUA, "Hidden Ability"));
                }

                if(fusion.getMaxFuseCount() > 0){
                    int timesFused = pokemonList[i].getPersistentData().getInteger("fuseCount");
                    lore.add(Text.of(TextColors.AQUA, "Fuse Count: ", TextColors.GRAY, timesFused + "/" + fusion.getMaxFuseCount()));
                }

                //Give sprite enchantment effect and lore if it's a selected Pokemon
                if(fusion.getPokemonIndex() == i || fusion.getSacrificeIndex() == i){
                    itemPokemon.offer(Keys.ITEM_ENCHANTMENTS, Arrays.asList(Enchantment.of(EnchantmentTypes.UNBREAKING, 1)));
                    itemPokemon.offer(Keys.HIDE_ENCHANTMENTS, true);

                    lore.add(Text.EMPTY);
                    if(fusion.getPokemonIndex() == i){
                        lore.add(Text.of(TextColors.GOLD, "Selected Pokemon To Fuse"));
                    }

                    if(fusion.getSacrificeIndex() == i){
                        lore.add(Text.of(TextColors.GOLD, "Selected Pokemon To Sacrifice"));
                    }
                }

                itemPokemon.offer(Keys.ITEM_LORE, lore);

                //Left click selects Pokemon to be fused, right click selected Pokemon to use as sacrifice.
                int slotIndex = i;
                Consumer<Action.Click> consSelectPokemon = action -> {
                    if(action.getEvent() instanceof ClickInventoryEvent.Primary){
                        if(fusion.getSacrificeIndex() != slotIndex && fusion.getPokemonIndex() != slotIndex){
                            fusion.setPokemonIndex(slotIndex);
                            openGUI();
                        }
                    }
                    else if(action.getEvent() instanceof ClickInventoryEvent.Secondary){
                        if(fusion.getSacrificeIndex() != slotIndex && fusion.getPokemonIndex() != slotIndex){
                            fusion.setSacrificeIndex(slotIndex);
                            openGUI();
                        }
                    }
                };

                Element pokemon = Element.of(itemPokemon, consSelectPokemon);

                elements.put(positionArray[i], pokemon);
            }

            /* -------------------------------------------------------------------
             * Creates item for resulting fusion. Will display error message in
             * lore if fusion isn't allowed. Shown on right of fusion GUI.
             * -------------------------------------------------------------------
             */
            if(fusion.getPokemon() != null){
                ItemStack itemPokemon = (ItemStack) (Object) ItemPixelmonSprite.getPhoto(fusion.getPokemon());
                itemPokemon.offer(Keys.DISPLAY_NAME, Text.of(TextColors.AQUA, TextStyles.BOLD, fusion.getPokemon().getSpecies().name));

                ArrayList<Text> lore = new ArrayList<>();
                lore.add(Text.EMPTY);

                Text validation = fusion.validateSlots();

                if(validation != null){
                    lore.add(validation);
                }
                else{
                    lore.addAll(getIVLore(fusion.getPokemonIVs(), fusion.getFusedIVs()));

                    lore.add(Text.EMPTY);
                    if (fusion.transfersShiny()) {
                        lore.add(Text.of(TextColors.AQUA, "Becomes Shiny"));
                    }
                    if (fusion.transfersHA()) {
                        lore.add(Text.of(TextColors.AQUA, "Acquires Hidden Ability"));
                    }

                    int newFusionCount = fusion.getPokemon().getPersistentData().getInteger("fuseCount") + 1;
                    if(fusion.transfersFuseCount()){
                        newFusionCount += fusion.getSacrifice().getPersistentData().getInteger("fuseCount");
                    }

                    if(fusion.getMaxFuseCount() > 0){
                        lore.add(Text.of(TextColors.AQUA, "Fuse Count: ", TextColors.GRAY, newFusionCount + "/" + fusion.getMaxFuseCount()));
                    }
                    else{
                        lore.add(Text.of(TextColors.AQUA, "Fuse Count: ", TextColors.GRAY, newFusionCount));
                    }

                    if(fusion.costEnabled()){
                        double cost = fusion.getCost();
                        lore.add(Text.EMPTY);
                        lore.add(Text.of(TextColors.DARK_AQUA, "Cost: ", TextColors.AQUA, cost));
                    }
                }
                itemPokemon.offer(Keys.ITEM_LORE, lore);

                Consumer<Action.Click> consStartFusion = action -> {
                    if(fusion.validateSlots() == null){
                        fusion.startFusion();
                        player.closeInventory();
                    }
                };

                Element fusion = Element.of(itemPokemon, consStartFusion);
                elements.put(25, fusion);
            }
        }

        Layout layout = Layout.builder()
                .row(blueFiller, 0)
                .set(blueFiller, 9, 15, 16, 17)
                .set(blackFiller, 18, 24, 26)
                .set(whiteFiller, 27, 33, 34, 35)
                .row(whiteFiller, 4)
                .row(grayFiller, 5)
                .setAll(elements).build();

        View view = View.builder()
                .archetype(InventoryArchetypes.DOUBLE_CHEST)
                .property(InventoryTitle.of(Text.of(TextColors.DARK_AQUA, TextStyles.BOLD, "Fusions")))
                .build(Fusions.getContainer());

        view.define(layout);
        view.open(player);
    }

    /**
     * Creates a list of Text that displays a Pokemon's IVs.
     * Used for lore on Pokemon slots.
     * @param ivs IVs of Pokemon to generate lore for.
     * @return List of Text that displays a Pokemon's IVs.
     */
    private List<Text> getIVLore(int[] ivs){
        ArrayList<Text> ivLore = new ArrayList<>();

        for(int k = 0; k < ivs.length; k++){
            String type = "";
            switch (k) {
                case 0:
                    type = "HP";
                    break;
                case 1:
                    type = "Attack";
                    break;
                case 2:
                    type = "Defense";
                    break;
                case 3:
                    type = "Special Attack";
                    break;
                case 4:
                    type = "Special Defense";
                    break;
                case 5:
                    type = "Speed";
                    break;
            }

            ivLore.add(Text.of(TextColors.DARK_AQUA, type + ": ", TextColors.GRAY, ivs[k]));
        }
        return ivLore;
    }

    /**
     * Creates a list of Text that displays a Pokemon's IVs and it's resulting Fusions IVs.
     * Used for lore on resulting fusion slot.
     * @param pokemonIVs IVs of a Pokemon before fusion.
     * @param fuseIVs IVs of a Pokemon after fusion.
     * @return list of Text that displays a Pokemon's IVs and it's resulting Fusions IVs.
     */
    private List<Text> getIVLore(int[] pokemonIVs, int[] fuseIVs) {
        ArrayList<Text> ivLore = new ArrayList<>();

        for (int i = 0; i < fuseIVs.length; i++) {
            String type = "";
            switch (i) {
                case 0:
                    type = "HP";
                    break;
                case 1:
                    type = "Attack";
                    break;
                case 2:
                    type = "Defense";
                    break;
                case 3:
                    type = "Special Attack";
                    break;
                case 4:
                    type = "Special Defense";
                    break;
                case 5:
                    type = "Speed";
                    break;
            }

            if(hideResultIVs){
                ivLore.add(Text.of(TextColors.DARK_AQUA, type + ": ", TextColors.GRAY, pokemonIVs[i],
                        TextColors.DARK_AQUA, " > ", TextColors.GRAY, "?"));
            }
            else{
                //Only shows the (+) at the end if the IV changed.
                if(fuseIVs[i] - pokemonIVs[i] > 0) {
                    ivLore.add(Text.of(TextColors.DARK_AQUA, type + ": ", TextColors.GRAY, pokemonIVs[i],
                            TextColors.DARK_AQUA, " > ", TextColors.GRAY, fuseIVs[i], TextColors.AQUA, " (+" + (fuseIVs[i] - pokemonIVs[i]) + ")"));
                }
                else{
                    ivLore.add(Text.of(TextColors.DARK_AQUA, type + ": ", TextColors.GRAY, pokemonIVs[i],
                            TextColors.DARK_AQUA, " > ", TextColors.GRAY, fuseIVs[i], TextColors.AQUA));
                }
            }
        }
        return ivLore;
    }
}
