package io.github.landonjw.fusions;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import io.github.landonjw.fusions.commands.FusionCommand;
import io.github.landonjw.fusions.configuration.ConfigManager;
import io.github.landonjw.fusions.placeholders.PlaceholderBridge;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Plugin(id = Fusions.PLUGIN_ID, name = Fusions.PLUGIN_NAME, version = Fusions.PLUGIN_VERSION,
        description = "Allows you to fuse pokemon in Pixelmon Reforged.",
        url = "https://www.github.com/landonjw", authors = {"landonjw"},
        dependencies={
                @Dependency(id = Pixelmon.MODID, version = Pixelmon.VERSION),
                @Dependency(id = "teslapowered", optional = true),
                @Dependency(id = "placeholderapi", optional = true)
        })

/* -----------------------------------------------------------------------------------
 *                                Fusions by landonjw
 *
 * Fusions is an expansion on the idea of /dittofusion by XpanD in the EMPC plugin who
 * happily allowed me to continue the feature after it was removed.
 *
 * For feedback, questions, or to see my other work you can find me at these places:
 * Discord: https://discord.gg/9FAanMj
 * Github: https://github.com/landonjw
 * Ore: https://ore.spongepowered.org/landonjw
 *
 * Check out XpanD's other work here: https://github.com/xPXpanD
 *
 * Thanks to:
 *      - XpanD for letting me reimplement his feature.
 *      - FrostEffects for GUI Design.
 * ------------------------------------------------------------------------------------
 */

//TODO: Create custom parsing system to do the following:
    //Add configuration option for different fuse counts per species.
    //Add configuration option for different costs per species.
    //Make species always fusable or sacrificeable
public class Fusions {

    public static final String PLUGIN_ID = "fusions";
    public static final String PLUGIN_NAME = "Fusions";
    public static final String PLUGIN_VERSION = "1.0.2";

    private static Fusions instance;
    private static PluginContainer container;
    private static Logger logger = LoggerFactory.getLogger(PLUGIN_NAME);

    private static EconomyService economyService;

    @Inject
    @ConfigDir(sharedRoot=false)
    private Path dir;

    /** List of species banned from fusing. */
    private static List<EnumSpecies> bannedFusionSpecies = new ArrayList<>();
    /** List of species banned from sacrificing.*/
    private static List<EnumSpecies> bannedSacrificeSpecies = new ArrayList<>();
    /** List of species to use egg group as fuse group during fusion. */
    private static List<EnumSpecies> eggGroupOverride = new ArrayList<>();
    /** List of species to use species as fuse group during fusion. */
    private static List<EnumSpecies> speciesOverride = new ArrayList<>();
    /** List of species to use type as fuse group during fusion. */
    private static List<EnumSpecies> typeOverride = new ArrayList<>();

    /** True if TelsaPowered has been loaded, false if it hasn't. */
    private static boolean teslaRegistered;

    @Listener
    public void preInit(GamePreInitializationEvent event) {
        instance = this;
        container = Sponge.getPluginManager().getPlugin(PLUGIN_ID).get();

        ConfigManager.setup(dir);
    }

    @Listener
    public void init(GameInitializationEvent event){
        loadBanLists();
        loadOverrides();

        CommandSpec fusion = CommandSpec.builder()
                .description(Text.of("Sacrifices a pokemon and fuses it's qualities with a Pokemon."))
                .permission("fusions.commands.fusion")
                .arguments(
                        GenericArguments.optional(GenericArguments.onlyOne(GenericArguments.integer(Text.of("pokemon")))),
                        GenericArguments.optional(GenericArguments.onlyOne(GenericArguments.integer(Text.of("sacrifice"))))
                )
                .executor(new FusionCommand())
                .build();

        Sponge.getCommandManager().register(this, fusion, "fusions", "fusion", "fuse");
    }

    @Listener
    public void postInit(GamePostInitializationEvent event){
        Optional<EconomyService> optionalEconomyService = Sponge.getServiceManager().provide(EconomyService.class);

        if(optionalEconomyService.isPresent()){
            economyService = optionalEconomyService.get();
            logger.info("Economy service was found. Economy features may be used if enabled.");
        }
        else{
            logger.warn("No economy service found. Any economy features enabled will be disabled.");
        }

        if (Sponge.getPluginManager().isLoaded("teslacore")) {
            teslaRegistered = true;
        }

        if (Sponge.getPluginManager().isLoaded("placeholderapi")) {
            PlaceholderBridge.register();
        }
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        logger.info(PLUGIN_NAME + " " + PLUGIN_VERSION + " successfully launched.");
    }

    @Listener
    public void onReload(GameReloadEvent event){
        ConfigManager.load();

        bannedFusionSpecies.clear();
        bannedSacrificeSpecies.clear();
        loadBanLists();

        eggGroupOverride.clear();
        speciesOverride.clear();
        typeOverride.clear();
        loadOverrides();

        logger.info(PLUGIN_NAME + " has been reloaded.");
    }

    public static Logger getLogger(){
        return logger;
    }

    public static Fusions getInstance(){
        return instance;
    }

    public static PluginContainer getContainer(){
        return container;
    }

    /**
     * Tries to get economy service loaded.
     * @return Optional containing Economy Service or null if no economy service is loaded.
     */
    public static Optional<EconomyService> getEconomyService(){
        return Optional.ofNullable(economyService);
    }

    /**
     * Gets list of species banned from fusing.
     * @return List of species banned from fusing.
     */
    public static List<EnumSpecies> getBannedFusionSpecies(){
        return bannedFusionSpecies;
    }

    /**
     * Gets list of species banned from sacrificing.
     * @return List of species banned from sacrificing.
     */
    public static List<EnumSpecies> getBannedSacrificeSpecies(){
        return bannedSacrificeSpecies;
    }

    /**
     * Gets list of species to use egg group as fuse group during fusion.
     * @return List of species to use egg group as fuse group during fusion.
     */
    public static List<EnumSpecies> getEggGroupOverride(){
        return eggGroupOverride;
    }

    /**
     * Gets list of species to use species as fuse group during fusion.
     * @return List of species to use species as fuse group during fusion.
     */
    public static List<EnumSpecies> getSpeciesOverride(){
        return speciesOverride;
    }

    /**
     * Gets list of species to use type as fuse group during fusion.
     * @return List of species to use type as fuse group during fusion.
     */
    public static List<EnumSpecies> getTypeOverride(){
        return typeOverride;
    }

    /**
     * Check that TeslaPowered has successfully been registered.
     * @return True if TelsaPowered has been loaded, false if it hasn't.
     */
    public static boolean isTeslaRegistered(){
        return teslaRegistered;
    }

    /**
     * Sets up species ban list from configuration node values.
     */
    private void loadBanLists(){
        try {
            //Grab list type & banlist from configuration node for fusion bans.
            String fusionListType = ConfigManager.getConfigNode("Fusing-Features", "Species-Bans-Fusion", "List-Type").getString("Black");
            List<String> strFusionSpeciesList = ConfigManager.getConfigNode("Fusing-Features", "Species-Bans-Fusion", "Fusion-Banlist").getList(TypeToken.of(String.class));

            //If config value is whitelist, add all species to banlist & remove any that are parsed. If blacklist, add any that are parsed.
            if(fusionListType.equalsIgnoreCase("White")){
                bannedFusionSpecies.addAll(Arrays.asList(EnumSpecies.values()));
                bannedFusionSpecies.removeAll(parseSpecies(strFusionSpeciesList));
            }
            else{
                bannedFusionSpecies.addAll(parseSpecies(strFusionSpeciesList));
            }


        }
        catch (ObjectMappingException e){
            logger.warn("Fusion species banlist could not be loaded.");
        }

        try{
            //Grab list type & banlist from configuration node for sacrifice bans.
            String sacrificeListType = ConfigManager.getConfigNode("Fusing-Features", "Species-Bans-Sacrifice", "List-Type").getString("Black");
            List<String> strSacrificeSpeciesList = ConfigManager.getConfigNode("Fusing-Features", "Species-Bans-Sacrifice", "Sacrifice-Banlist").getList(TypeToken.of(String.class));

            //If config value is whitelist, add all species to banlist & remove any that are parsed. If blacklist, add any that are parsed.
            if(sacrificeListType.equalsIgnoreCase("White")){
                bannedSacrificeSpecies.addAll(Arrays.asList(EnumSpecies.values()));
                bannedSacrificeSpecies.removeAll(parseSpecies(strSacrificeSpeciesList));
            }
            else{
                bannedSacrificeSpecies.addAll(parseSpecies(strSacrificeSpeciesList));
            }
        }
        catch (ObjectMappingException e){
            logger.warn("Sacrifice species banlist could not be loaded.");
        }
    }

    /**
     * Loads lists of Pokemon that overrides typical fuse group.
     */
    private void loadOverrides(){
        try{
            List<String> strSpeciesList = ConfigManager.getConfigNode("Fusing-Features", "Group-Override-Egg-Group").getList(TypeToken.of(String.class));
            eggGroupOverride.addAll(parseSpecies(strSpeciesList));
        }
        catch(ObjectMappingException e){
            logger.warn("Egg group group override could not be loaded.");
        }

        try{
            List<String> strSpeciesList = ConfigManager.getConfigNode("Fusing-Features", "Group-Override-Species").getList(TypeToken.of(String.class));
            speciesOverride.addAll(parseSpecies(strSpeciesList));
        }
        catch(ObjectMappingException e){
            logger.warn("Species group override could not be loaded.");
        }

        try{
            List<String> strSpeciesList = ConfigManager.getConfigNode("Fusing-Features", "Group-Override-Type").getList(TypeToken.of(String.class));
            typeOverride.addAll(parseSpecies(strSpeciesList));
        }
        catch(ObjectMappingException e){
            logger.warn("Type group override could not be loaded.");
        }
    }

    /**
     * Retrieves a list of EnumSpecies from a list of Strings.
     * @param strSpeciesList List of String of pokemon species names.
     * @return List of EnumSpecies.
     */
    private List<EnumSpecies> parseSpecies(List<String> strSpeciesList){
        List<EnumSpecies> speciesList = new ArrayList<>();
        for(String strSpecies : strSpeciesList){
            if(strSpecies.equalsIgnoreCase("Legendaries")){
                speciesList.addAll(parseSpecies(EnumSpecies.legendaries));
            }
            else if(strSpecies.equalsIgnoreCase("Ultrabeasts")){
                speciesList.addAll(parseSpecies(EnumSpecies.ultrabeasts));
            }
            else{
                EnumSpecies species = EnumSpecies.getFromNameAnyCase(strSpecies);
                if(species != null && !speciesList.contains(species)){
                    speciesList.add(species);
                }
            }
        }
        return speciesList;
    }
}
