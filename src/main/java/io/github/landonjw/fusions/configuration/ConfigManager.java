package io.github.landonjw.fusions.configuration;

import io.github.landonjw.fusions.Fusions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.scheduler.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final String[] FILE_NAMES = {"Configuration.conf"};

    private static Path dir, config;
    private static ConfigurationLoader<CommentedConfigurationNode> configLoad;
    private static CommentedConfigurationNode configNode;


    public static void setup(Path folder){
        dir = folder;
        config = dir.resolve(FILE_NAMES[0]);
        load();
    }

    public static void load(){
        try{
            if(!Files.exists(dir)){
                Files.createDirectory(dir);
            }

            Fusions.getContainer().getAsset(FILE_NAMES[0]).get().copyToFile(config, false, true);

            configLoad = HoconConfigurationLoader.builder().setPath(config).build();

            configNode = configLoad.load();
        }
        catch (IOException e){
            Fusions.getLogger().error("Fusions configuration could not load.");
            e.printStackTrace();
        }
    }

    public static void save(){
        Task save = Task.builder().execute(() -> {

            try{
                configLoad.save(configNode);
            }
            catch(IOException e){
                Fusions.getLogger().error("Fusions could not save configuration.");
                e.printStackTrace();
            }

        }).async().submit(Fusions.getInstance());
    }

    public static ConfigurationLoader<CommentedConfigurationNode> getConfigLoad(){
        return configLoad;
    }

    public static CommentedConfigurationNode getConfigNode(Object... node){
        return configNode.getNode(node);
    }
}
