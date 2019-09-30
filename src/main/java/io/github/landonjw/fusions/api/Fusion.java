package io.github.landonjw.fusions.api;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonSpec;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.enums.EnumEggGroup;
import com.pixelmonmod.pixelmon.enums.EnumGrowth;
import com.pixelmonmod.pixelmon.enums.EnumType;
import io.github.landonjw.fusions.Fusions;
import io.github.landonjw.fusions.commands.FusionCommand;
import io.github.landonjw.fusions.configuration.ConfigManager;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.math.BigDecimal;
import java.util.*;

/**
 * Lets a player fuse a Pokemon with a sacrifice in order to inherit certain traits or IVs.
 * @author landonjw
 * @since 9/25/2019 Version 1.0.0
 */
public class Fusion {
    /* -----------------------------------------------------------------------------------------
     * All of these variables contain information regarding the Pokemon to fuse & sacrifice.
     * -----------------------------------------------------------------------------------------
     */
    /** Player that is doing the fusion. */
    private Player player;
    /** Index of the Pokemon to do fusion on. */
    private int pokemonIndex;
    /** Pokemon to do fusion on. */
    Pokemon pokemon;
    /** IVs of the Pokemon to do fusion on. */
    private int[] pokemonIVs;
    /** Index of the Pokemon to sacrifice for the fusion. */
    private int sacrificeIndex;
    /** Pokemon to sacrifice for the fusion. */
    Pokemon sacrifice;
    /** IVs of the Pokemon to sacrifice for the fusion. */
    private int[] sacrificeIVs;

    /* -----------------------------------------------------------------------------------------
     * All of these variables contain information regarding configuration values for fusion.
     * -----------------------------------------------------------------------------------------
     */
    /** If fusion should affect IVs. */
    private boolean enableIVs;
    /** If fusion should affect Growth. */
    private boolean enableGrowth;
    /** How many times a Pokemon can be fused. */
    private int fuseCount;
    /** The type of group that is allowed to fuse together. Can be type, species, or egg group. */
    private String fuseGroup;
    /** Makes fused Pokemon unbreedable. */
    private boolean forceUnbreedable;
    /** The number of IVs to be affected from a fuse. IF less than 6, it grabs the highest IVs from sacrifice. */
    private int numAffectedIVs;
    /** The percent of difference in Pokemon and sacrifice's IVs to add to Pokemon during fusion. */
    private double fusePercent;
    /** The maximum IV amount that a Pokemon can get in any single IV. */
    private int maxIncrease;
    /** The minimum IV amount that a Pokemon can get in any single IV. */
    private int minIncrease;
    /** If a fusion should keep the fuse count of a sacrificed Pokemon. */
    private boolean retainFuseCount;
    /** If a fusion should keep an HA of a sacrificed Pokemon. */
    private boolean retainHA;
    /** If a fusion should keep the shininess of a sacrificed Pokemon. */
    private boolean retainShiny;
    /** If fusion should cost any money. */
    private boolean enableCost;
    /** The base cost of fusing two Pokemon. */
    private double baseCost;
    /** Addition cost per fusion on a Pokemon. */
    private double costPerFusion;
    /** If cost per fusion should scale linearly or exponentially. */
    private String costIncreaseType;
    /** The currency to use for fusion command costs.*/
    private String currency;

    /**
     * Basic constructor for Fusion that does not have any slots chosen.
     * Used by the GUI, where the initial GUI has no Pokemon selected.
     * @param player Player that is doing the fusion.
     */
    public Fusion(Player player){
        this(player, 0, 0);
    }

    /**
     * Advances constructor for Fusion that has slots specified and loads information about Pokemon.
     * @param player Player that is doing the fusion.
     * @param pokemonSlot Slot of the Pokemon to do fusion on.
     * @param sacrificeSlot Slot of the Pokemon to sacrifice.
     */
    public Fusion(Player player, int pokemonSlot, int sacrificeSlot){
        this.player = player;
        this.pokemonIndex = pokemonSlot - 1;
        this.sacrificeIndex = sacrificeSlot - 1;
        this.pokemon = (pokemonIndex >= 0 && pokemonIndex <= 5) ? Pixelmon.storageManager.getParty(player.getUniqueId()).get(pokemonIndex) : null;
        this.sacrifice = (sacrificeIndex >= 0 && sacrificeIndex <= 5) ? Pixelmon.storageManager.getParty(player.getUniqueId()).get(sacrificeIndex): null;

        loadAndValidateConfig();

        if(validateSlots(pokemonIndex, pokemon, sacrificeIndex, sacrifice) == null){
            this.pokemonIVs = pokemon.getIVs().getArray();
            this.sacrificeIVs = sacrifice.getIVs().getArray();
        }
    }

    /**
     * Checks slots and Pokemon to see if they pass rules set for Fusion.
     * @param pokemonIndex Index of the Pokemon to do fusion on.
     * @param pokemon Pokemon to do fusion on.
     * @param sacrificeIndex Index of the Pokemon to sacrifice for fusion.
     * @param sacrifice Pokemon to sacrifice for fusion.
     * @return Text consisting the rule broken, or null if everything is valid for fusion.
     */
    public Text validateSlots(int pokemonIndex, Pokemon pokemon, int sacrificeIndex, Pokemon sacrifice){

        //Make sure player isn't in battle.
        if(BattleRegistry.getBattle((EntityPlayerMP) player) != null){
            return Text.of(TextColors.RED, "You can not fuse pokemon while in battle.");
        }

        //Make sure indexes are valid input (between 0 and 6).
        if(pokemonIndex < 0 || pokemonIndex > 5
                || sacrificeIndex < 0 || sacrificeIndex > 5){
            return Text.of(TextColors.RED, "Slots must between 1 and 6.");
        }

        //Make sure indexes aren't the same (can't fuse the same Pokemon).
        if(pokemonIndex == sacrificeIndex){
            return Text.of(TextColors.RED, "You cannot fuse a Pokemon with itself.");
        }

        //Check that neither slot is empty.
        if(pokemon == null || sacrifice == null){
            return Text.of(TextColors.RED, "You must select two slots containing a Pokemon.");
        }

        //Check if a Pokemon is outside of it's pokeball.
        if(pokemon.getPixelmonIfExists() != null || sacrifice.getPixelmonIfExists() != null ){
            return Text.of(TextColors.RED, "A pokemon is outside of it's pokeball.");
        }

        //Check if pokemon is banned from being fused.
        if(Fusions.getBannedFusionSpecies().contains(pokemon.getSpecies())){
            return Text.of(TextColors.RED, "This pokemon is not capable of fusion.");
        }

        //Check if sacrifice is banned from being a sacrifice.
        if(Fusions.getBannedSacrificeSpecies().contains(sacrifice.getSpecies())){
            return Text.of(TextColors.RED, "A pokemon refuses to be sacrificed.");
        }

        if(fuseCount > 0) {
            //Check that Pokemon hasn't already been fused too many times if fuse count is enabled.
            if (pokemon.getPersistentData().getInteger("fuseCount") > (fuseCount - 1)) {
                return Text.of(TextColors.RED, "This Pokemon has been fused too many times.");
            }

            //Check that sacrifice fuse count doesn't take Pokemon's fuse count over limit when carried over if fuse count is enabled.
            if (retainFuseCount) {
                if (pokemon.getPersistentData().getInteger("fuseCount") + sacrifice.getPersistentData().getInteger("fuseCount") > (fuseCount - 1)) {
                    return Text.of(TextColors.RED, "Sacrifice has been fused too many times to be used.");
                }
            }
        }

        //Check if Pokemon is in override group & check if it's valid if it is.
        if(Fusions.getEggGroupOverride().contains(pokemon.getSpecies())){
            if(!hasSharedEggGroup(pokemon, sacrifice)){
                return Text.of(TextColors.RED, "Pokemon do not share a similar egg group.");
            }
        }
        else if(Fusions.getSpeciesOverride().contains(pokemon.getSpecies())){
            if(pokemon.getSpecies() != sacrifice.getSpecies()){
                return Text.of(TextColors.RED, "Pokemon do not share a similar species.");
            }
        }
        else if(Fusions.getTypeOverride().contains(pokemon.getSpecies())){
            if(!hasSharedType(pokemon, sacrifice)){
                return Text.of(TextColors.RED, "Pokemon do not share a similar type.");
            }
        }
        else{
            //Check both pokemon are compatible with selected fuse group from configuration (type, species, or egg group).
            if(((fuseGroup.equalsIgnoreCase("Type")) && !hasSharedType(pokemon, sacrifice))
                    || (fuseGroup.equalsIgnoreCase("Egg Group") && !hasSharedEggGroup(pokemon, sacrifice) )
                    || (fuseGroup.equalsIgnoreCase("Species") && pokemon.getSpecies() != sacrifice.getSpecies())){
                return Text.of(TextColors.RED, "Pokemon do not share a similar " + fuseGroup.toLowerCase() + ".");
            }
        }

        return null;
    }

    /**
     * Checks slots and Pokemon to see if they pass rules set for Fusion. Uses variables set in object.
     * @return Text consisting the rule broken, or null if everything is valid for fusion.
     */
    public Text validateSlots(){
        return validateSlots(pokemonIndex, pokemon, sacrificeIndex, sacrifice);
    }

    /**
     * Grabs values from configuration node and validates them.
     * If one of the values aren't valid, it will adjust them so they are valid.
     */
    public void loadAndValidateConfig(){
        /* --------------------------------------------------------
         * Grab values from configuration node in ConfigManager.
         * --------------------------------------------------------
         */
        enableIVs = ConfigManager.getConfigNode("Fusing-Features", "IVs", "Enable-IVs").getBoolean();
        enableGrowth = ConfigManager.getConfigNode("Fusing-Features", "Growth", "Enable-Growth").getBoolean();
        fuseCount = ConfigManager.getConfigNode("Fusing-Features", "Fuse-Count").getInt();
        fuseGroup = ConfigManager.getConfigNode("Fusing-Features", "Fuse-Group").getString("Species");
        forceUnbreedable = ConfigManager.getConfigNode("Fusing-Features", "Force-Unbreedable").getBoolean();
        numAffectedIVs = ConfigManager.getConfigNode("Fusing-Features", "IVs", "Num-IVs-Affected").getInt();
        fusePercent = ConfigManager.getConfigNode("Fusing-Features", "IVs", "Fuse-Percent").getDouble();
        maxIncrease = ConfigManager.getConfigNode("Fusing-Features", "IVs", "Fuse-Increase-Max").getInt();
        minIncrease = ConfigManager.getConfigNode("Fusing-Features", "IVs", "Fuse-Increase-Min").getInt();
        retainFuseCount = ConfigManager.getConfigNode("Retain-Qualities", "Retain-Fuse-Count").getBoolean();
        retainHA = ConfigManager.getConfigNode("Retain-Qualities", "Retain-HA").getBoolean();
        retainShiny = ConfigManager.getConfigNode("Retain-Qualities", "Retain-Shiny").getBoolean();
        enableCost = ConfigManager.getConfigNode("Fusing-Costs", "Enable-Cost").getBoolean();
        baseCost = ConfigManager.getConfigNode("Fusing-Costs", "Cost-Base").getDouble();
        costPerFusion = ConfigManager.getConfigNode("Fusing-Costs", "Cost-Per-Fusion").getDouble();
        costIncreaseType = ConfigManager.getConfigNode("Fusing-Costs", "Cost-Increase-Type").getString("Linear");
        currency = ConfigManager.getConfigNode("Fusing-Costs", "Currency").getString("");

        /* --------------------------------------------------------
         * Validate configuration values and readjust if necessary.
         * --------------------------------------------------------
         */

        //Check fuse count is above or equal to 0, adjust to 0 if it isn't.
        if(fuseCount < 0){
            fuseCount = 0;
        }

        //Check fuse group is Species, Egg Group (or EggGroup), or Type, adjust to Species if it isn't.
        if(!fuseGroup.equalsIgnoreCase("Species")
                && !fuseGroup.replace(" ", "").equalsIgnoreCase("EggGroup")
                && !fuseGroup.equalsIgnoreCase("Type") ){
            fuseGroup = "Species";
        }

        //Check number of affected IVs are between 0 and 6, adjust to 0 or 6 if it isn't,
        if(numAffectedIVs > 6){
            numAffectedIVs = 6;
        }
        else if(numAffectedIVs < 0){
            numAffectedIVs = 0;
        }

        //Check fuse percent is within 0.0 and 1.0, adjust to 0 or 1 if it isn't.
        if(fusePercent > 1){
            fusePercent = 1;
        }
        else if(fusePercent < 0){
            fusePercent = 0;
        }

        //Check maximum IV increase is above or equal to 0, adjust to 0 if it isn't.
        if(maxIncrease < 0){
            maxIncrease = 0;
        }

        //Check minimum IV increase is above or equal to 0, adjust to 0 if it isn't.
        if(minIncrease < 0){
            minIncrease = 0;
        }

        //Check base cost is above or equal to 0, adjust to 0 if it isn't.
        if(baseCost < 0){
            baseCost = 0;
        }

        //Check cost per fusion is above or equal to 0, adjust to 0 if it isn't.
        if(costPerFusion < 0){
            costPerFusion = 0;
        }

        //Check cost increase type is equal to Linear or Exponential, set to Linear if it isn't.
        if(!costIncreaseType.equalsIgnoreCase("Linear")
                && !costIncreaseType.equalsIgnoreCase("Exponential")){
            costIncreaseType = "Linear";
        }
    }

    /**
     * Starts the fusion process if validation is successful.
     */
    public void startFusion(){

        //Validates fusion and sends player error message if it doesn't succeed.
        Text validation = validateSlots();
        if(validation != null){
            player.sendMessage(validation);
            return;
        }

        //Try to withdraw money from player, stop fusion if they don't have necessary funds.
        if(enableCost) {

            //Get economy service if available.
            Optional<EconomyService> optionalEconomyService = Fusions.getEconomyService();
            if (optionalEconomyService.isPresent()) {
                EconomyService economyService = optionalEconomyService.get();

                //Get user account
                Optional<UniqueAccount> optionalUniqueAccount = economyService.getOrCreateAccount(player.getUniqueId());
                if (optionalUniqueAccount.isPresent()) {
                    UniqueAccount uniqueAccount = optionalUniqueAccount.get();

                    double fusionCost = getCost();

                    //Try to withdraw from player, send message and cancel fusion if they dont have the funds.
                    EventContext eventContext = EventContext.builder().add(EventContextKeys.PLUGIN, Fusions.getContainer()).build();
                    Cause cause = Cause.of(eventContext, Fusions.getContainer());

                    Currency econCurrency = null;
                    for(Currency economyCurrency : economyService.getCurrencies()){
                        if(economyCurrency.getDisplayName().equals(Text.of(currency))){
                            econCurrency = economyCurrency;
                        }
                    }

                    if(econCurrency == null){
                        Fusions.getLogger().warn("Specified currency not found. Using default currency...");
                        econCurrency = economyService.getDefaultCurrency();
                    }

                    TransactionResult transactionResult = uniqueAccount.withdraw(econCurrency, BigDecimal.valueOf(fusionCost), cause);

                    if(transactionResult.getResult() == ResultType.FAILED || transactionResult.getResult() == ResultType.ACCOUNT_NO_FUNDS){
                        player.sendMessage(Text.of(TextColors.RED, "You do not have enough money."));
                        return;
                    }
                }
            }
        }

        //Gets new IVs if IV fusion is enabled.
        int[] fusedIVs = new int[6];
        if(enableIVs) {
            fusedIVs = getFusedIVs();
            pokemon.getIVs().fillFromArray(fusedIVs);
        }

        //Changes size if Growth fusion is enabled.
        String sizeChange = null;
        if(enableGrowth){
            if(sacrifice.getGrowth().scaleValue > pokemon.getGrowth().scaleValue){
                pokemon.setGrowth(EnumGrowth.getNextGrowth(pokemon.getGrowth()));
                sizeChange = "larger";
            }
            else if(sacrifice.getGrowth().scaleValue < pokemon.getGrowth().scaleValue){
                int growthIndex = ((pokemon.getGrowth().index - 1 + EnumGrowth.values().length) % EnumGrowth.values().length);
                pokemon.setGrowth(EnumGrowth.getGrowthFromIndex(growthIndex));
                sizeChange = "smaller";
            }
        }

        //Transfers fuse count if sacrifice has fuse count and setting is enabled.
        boolean fuseCountTransferred = transfersFuseCount();
        if(fuseCountTransferred){
            int sacrificeFuseCount = sacrifice.getPersistentData().getInteger("fuseCount");
            int pokemonFuseCount = pokemon.getPersistentData().getInteger("fuseCount");
            pokemon.getPersistentData().setInteger("fuseCount", pokemonFuseCount + sacrificeFuseCount);
        }

        //Transfers HA if sacrifice has HA and setting is enabled.
        boolean haTransferred = transfersHA();
        if(haTransferred){
            pokemon.setAbilitySlot(2);
        }

        //Transfers shininess if sacrifice is shiny and setting is enabled.
        boolean shinyTransferred = transfersShiny();
        if(shinyTransferred){
            pokemon.setShiny(true);
        }

        //Makes Pokemon unbreedable if setting is enabled.
        boolean madeUnbreedable = makesUnbreedable();
        if(madeUnbreedable){
            new PokemonSpec("unbreedable").apply(pokemon);
        }

        //Adds one to Pokemon's fuse count.
        pokemon.getPersistentData().setInteger("fuseCount", pokemon.getPersistentData().getInteger("fuseCount") + 1);

        //Gets resulting text for fusion.
        Text fusedText = getFusionResultText(fusedIVs, sizeChange, fuseCountTransferred, haTransferred, shinyTransferred, madeUnbreedable);

        //Removes sacrifice from inventory & sends result text.
        Pixelmon.storageManager.getParty(player.getUniqueId()).set(sacrificeIndex, null);
        player.sendMessage(fusedText);

        //Adds player to the fusion command cooldown.
        FusionCommand.addCooldown(player.getUniqueId());
    }

    /**
     * Gets the resulting text from a fusion, displaying IV changes and any qualities retained from sacrifice.
     * fusedIVs and sizeChange parameters will not be displayed if their respective features are disabled.
     * @param fusedIVs Resulting IVs after fusion.
     * @param sizeChange Size change after fusion, or empty if no size change.
     * @param fuseCountTransferred If a fuse count was transferred from the sacrifice.
     * @param haTransferred If a HA was transferred from the sacrifice.
     * @param shinyTransferred If shininess was transferred from the sacrifice.
     * @return
     */
    private Text getFusionResultText(int[] fusedIVs, String sizeChange, boolean fuseCountTransferred,
                                     boolean haTransferred, boolean shinyTransferred, boolean madeUnbreedable){
        Text fusedText = Text.of(TextColors.BLUE, "-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-\n",
                TextColors.AQUA, TextStyles.BOLD, "Your Pokemon have been fused!\n");

        /* --------------------------------------------------------
         * Displays any IV stat changes if feature is enabled.
         * --------------------------------------------------------
         */

        if(enableIVs){
            for(int i = 0; i < 6; i++) {
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

                int difference = fusedIVs[i] - pokemonIVs[i];
                Text differenceText = (difference != 0) ? Text.of(TextColors.AQUA, " (+" + difference + ")") : Text.EMPTY;

                fusedText = fusedText.concat(Text.of(TextColors.DARK_AQUA, type + ": ",
                        TextColors.GRAY, pokemonIVs[i], TextColors.DARK_AQUA, " > ", TextColors.GRAY, fusedIVs[i], differenceText, "\n"));
            }
        }

        /* --------------------------------------------------------
         * Displays any transferred qualities from the sacrifice.
         * --------------------------------------------------------
         */

        //Add empty line to separate IVs from other qualities.
        if(sizeChange != null || fuseCountTransferred || haTransferred || shinyTransferred || madeUnbreedable){
            fusedText = fusedText.concat(Text.of("\n"));
        }

        if(sizeChange != null){
            fusedText = fusedText.concat(Text.of(TextColors.AQUA, pokemon.getSpecies().name + " seems to have gotten " + sizeChange + "!\n"));
        }

        if(fuseCountTransferred){
            fusedText = fusedText.concat(Text.of(TextColors.AQUA, pokemon.getSpecies().name + " absorbed the sacrifice's fusion count!\n"));
        }

        if(haTransferred){
            fusedText = fusedText.concat(Text.of(TextColors.AQUA, pokemon.getSpecies().name + " absorbed the sacrifice's HA!\n"));
        }

        if(shinyTransferred){
            fusedText = fusedText.concat(Text.of(TextColors.AQUA, pokemon.getSpecies().name + " absorbed the sacrifice's shininess!\n"));
        }

        if(madeUnbreedable){
            fusedText = fusedText.concat(Text.of(TextColors.RED, pokemon.getSpecies().name + " can no longer breed!\n"));
        }

        fusedText = fusedText.concat(Text.of(TextColors.BLUE, "-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-"));

        return fusedText;
    }

    /**
     * Gets the player doing the fusion.
     * @return Player doing the fusion.
     */
    public Player getPlayer(){
        return player;
    }

    /**
     * Gets the index of the Pokemon to do fusion on.
     * @return The index of the Pokemon to do fusion on.
     */
    public int getPokemonIndex(){
        return pokemonIndex;
    }

    /**
     * Gets the index of the Pokemon to sacrifice for fusion.
     * @return The index of the Pokemon to sacrifice for fusion.
     */
    public int getSacrificeIndex(){
        return sacrificeIndex;
    }

    /**
     * Gets the Pokemon to do fusion on.
     * @return The Pokemon to do fusion on.
     */
    public Pokemon getPokemon(){
        return pokemon;
    }

    /**
     * Gets the Pokemon to sacrifice for fusion.
     * @return The Pokemon to sacrifice for fusion.
     */
    public Pokemon getSacrifice(){
        return sacrifice;
    }

    /**
     * Gets IVs of the Pokemon to do fusion on.
     * @return Array with the IVs of the Pokemon to do fusion on.
     */
    public int[] getPokemonIVs(){
        return pokemonIVs;
    }

    /**
     * Gets IVs of the Pokemon to sacrifice for fusion.
     * @return Array with the IVs of the Pokemon to sacrifice for fusion.
     */
    public int[] getSacrificeIVs(){
        return sacrificeIVs;
    }

    public int getMaxFuseCount(){
        return fuseCount;
    }

    /**
     * Sets the index of the Pokemon to do fusion on.
     * @param index Index of the Pokemon to do fusion on.
     */
    public void setPokemonIndex(int index){
        if(index <= 5 && index >= 0){
            this.pokemonIndex = index;
            this.pokemon = Pixelmon.storageManager.getParty(player.getUniqueId()).get(pokemonIndex);
            this.pokemonIVs = pokemon.getIVs().getArray();
        }
    }

    /**
     * Sets the slot of the Pokemon to do fusion on.
     * @param slot Slot of the Pokemon to do fusion on.
     */
    public void setPokemonSlot(int slot){
        setPokemonIndex(slot - 1);
    }

    /**
     * Sets the index of the Pokemon to sacrifice for fusion.
     * @param index Index of the Pokemon to sacrifice for fusion.
     */
    public void setSacrificeIndex(int index){
        if(index <= 5 && index >= 0){
            this.sacrificeIndex = index;
            this.sacrifice = Pixelmon.storageManager.getParty(player.getUniqueId()).get(sacrificeIndex);
            this.sacrificeIVs = sacrifice.getIVs().getArray();
        }
    }

    /**
     * Sets the slot of the Pokemon to sacrifice for fusion.
     * @param slot Slot of the Pokemon to sacrifice for fusion.
     */
    public void setSacrificeSlot(int slot){
        setSacrificeIndex(slot - 1);
    }

    /**
     * Gets resulting IVs from fusion.
     * @return Array with the resulting IVs from fusion.
     */
    public int[] getFusedIVs(){

        //Check pokemon are valid
        if(validateSlots(pokemonIndex, pokemon, sacrificeIndex, sacrifice) != null){
            return null;
        }

        //Generate a new IV set and find the highest IVs
        int[] newIVs = pokemonIVs.clone();

        List<Integer> indexesToAlter = getHighestIVIndex(sacrificeIVs, numAffectedIVs);

        //Generate new IVs for fusion
        for(int index : indexesToAlter){
            int ivToChange = newIVs[index];
            int sacrificeIV = sacrificeIVs[index];

            int increase = (int) Math.round((sacrificeIV - ivToChange) * fusePercent);

            if(increase > maxIncrease){
                increase = maxIncrease;
            }
            else if(increase < minIncrease){
                increase = minIncrease;
            }

            newIVs[index] = ((ivToChange + increase) <= 31) ? ivToChange + increase : 31;
        }
        return newIVs;
    }

    /**
     * Gets a certain amount of indexes with the highest IVs from an integer array.
     * @param ivs Integer array consisting of IVs.
     * @param ivsToGet How many IVs to get.
     * @return List of indexes corresponding to the highest IVs from integer array.
     */
    public List<Integer> getHighestIVIndex(int[] ivs, int ivsToGet){
        List<Integer> indexes = new ArrayList<>();

        for(int i = 0; i < ivsToGet; i++){
            int highestIVIndex = 0;
            int highestValue = 0;

            for(int k = 0; k < ivs.length; k++){
                if(ivs[k] >= highestValue && !indexes.contains(k) && pokemonIVs[k] != 31){
                    highestValue = ivs[k];
                    highestIVIndex = k;
                }
            }
            indexes.add(highestIVIndex);
        }

        return indexes;
    }

    /**
     * Checks if two Pokemon share a type.
     * @param pokemon Pokemon to do fusion on.
     * @param sacrifice Pokemon to sacrifice for fusion.
     * @return True if Pokemon share a type, false if they don't.
     */
    private boolean hasSharedType(Pokemon pokemon, Pokemon sacrifice){
        for(EnumType sacrificeType : sacrifice.getBaseStats().getTypeList()){
            if(pokemon.getBaseStats().getTypeList().contains(sacrificeType)){
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if two Pokemon share an egg group.
     * @param pokemon Pokemon to do fusion on.
     * @param sacrifice Pokemon to sacrifice for fusion.
     * @return True if Pokemon share an egg group, false if they don't.
     */
    private boolean hasSharedEggGroup(Pokemon pokemon, Pokemon sacrifice){
        for(EnumEggGroup sacrificeEggGroup : EnumEggGroup.getEggGroups(sacrifice)){
            for(EnumEggGroup pokemonEggGroup : EnumEggGroup.getEggGroups(pokemon)){
                if(sacrificeEggGroup == pokemonEggGroup){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if fuse count will be transferred from sacrifice during fusion.
     * @return True if fuse count will be transferred from sacrifice during fusion, false if it won't.
     */
    public boolean transfersFuseCount(){
        if(retainFuseCount) {
            int sacrificeFuseCount = sacrifice.getPersistentData().getInteger("fuseCount");
            return sacrificeFuseCount > 0;
        }
        return false;
    }

    /**
     * Checks if HA will be transferred from sacrifice during fusion.
     * @return True if HA will be transferred from sacrifice during fusion, false if it won't.
     */
    public boolean transfersHA(){
        if(retainHA) {
            if(sacrifice.getAbilitySlot() == 2) {
                return pokemon.getBaseStats().abilities[2] != null && pokemon.getAbilitySlot() != 2;
            }
        }
        return false;
    }

    /**
     * Checks if shininess will be transferred from sacrifice during fusion.
     * @return True if shininess will be transferred from sacrifice during fusion, false if it won't.
     */
    public boolean transfersShiny(){
        if(retainShiny) {
            return sacrifice.isShiny() && !pokemon.isShiny();
        }
        return false;
    }

    /**
     * Checks if fusion will make a Pokemon unbreedable.
     * @return True if fusion will make a Pokemon unbreedable, false if it won't.
     */
    public boolean makesUnbreedable(){
        if(forceUnbreedable) {
            return !new PokemonSpec("unbreedable").matches(pokemon);
        }
        return false;
    }

    /**
     * Checks if IV altering features are enabled for the plugin.
     * @return True if IV altering features are enabled for the plugin, false if they aren't.
     */
    public boolean ivsEnabled(){
        return enableIVs;
    }

    /**
     * Checks if there is a cost for doing a fusion.
     * @return True if there is a cost for doing a fusion, false if there isn't a cost.
     */
    public boolean costEnabled(){
        return enableCost;
    }

    /**
     * Checks if fusion makes fused Pokemon unbreedable.
     * @return True if fused Pokemon are unbreedable, false if they are breedable.
     */
    public boolean forceUnbreedable(){
        return forceUnbreedable;
    }

    /**
     * Gets the cost of doing a fusion.
     * @return Cost of doing a fusion. If cost isn't enabled, returns 0.
     */
    public double getCost(){
        if(costEnabled()) {

            //Get total fuse count after fusion from Pokemon & sacrifice
            int fuseCount = 0;
            if(transfersFuseCount()){
                fuseCount = pokemon.getPersistentData().getInteger("fuseCount") + sacrifice.getPersistentData().getInteger("fuseCount") + 1;
            }
            else{
                fuseCount = pokemon.getPersistentData().getInteger("fuseCount") + 1;
            }

            if (costIncreaseType.equalsIgnoreCase("Exponential")) {
                return baseCost + Math.pow(costPerFusion, fuseCount);
            }
            else  if(costIncreaseType.equalsIgnoreCase("Linear")) {
                return baseCost + costPerFusion * (fuseCount);
            }
            else{
                return baseCost;
            }
        }
        return 0;
    }
}
