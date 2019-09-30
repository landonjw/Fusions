package io.github.landonjw.fusions.placeholders;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import io.github.landonjw.fusions.Fusions;
import io.github.landonjw.fusions.configuration.ConfigManager;
import me.rojo8399.placeholderapi.*;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;

/**
 * Allows functionality with placeholders when PlaceholderAPI is present.
 * @author landonjw
 * @since 9/29/2019 Version 1.0.2
 */
public class PlaceholderBridge {
    public static void register() {
        Sponge.getServiceManager().provideUnchecked(PlaceholderService.class).loadAll(new PlaceholderBridge(), Fusions.getInstance()).stream()
                .map(builder -> builder.tokens("max_fuse_count", "fuse_count_<slot>").author("landonjw").plugin(Fusions.getInstance()).version(Fusions.PLUGIN_VERSION))
                .forEach(builder -> {
                    try {
                        builder.buildAndRegister();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    @Placeholder(id = "fusions")
    public Object fusions(@Source Player player, @Token String token) throws NoValueException {
        String[] values = token.split("_");
        if (values.length == 3) {
            if(token.equalsIgnoreCase("max_fuse_count")){
                //Returns config setting for max fuse counts if placeholder is %fusions_max_fuse_count%
                int fuseCount = ConfigManager.getConfigNode("Fusing-Features", "Fuse-Count").getInt();

                if(fuseCount < 0){
                    fuseCount = 0;
                }

                return fuseCount;
            }
            else if(values[0].equalsIgnoreCase("fuse") && values[1].equalsIgnoreCase("count")){
                //Returns current fuse count of slot if placeholder is %fusions_fuse_count_<slot>%
                int slot = Integer.parseInt(values[2]);

                //Only allow slots between 1&6
                if(slot < 1 || slot > 6){
                    return 0;
                }

                Pokemon pokemon = Pixelmon.storageManager.getParty((EntityPlayerMP) player).get(slot - 1);

                if(pokemon == null){
                    return 0;
                }
                else{
                    return pokemon.getPersistentData().getInteger("fuseCount");
                }
            }
            else{
                throw new NoValueException("Invalid arguments. Placeholders: %fusions_max_fuse_count%, %fusions_fuse_count_<slot>%");
            }
        }
        else {
            throw new NoValueException("Not enough arguments. Placeholders: %fusions_max_fuse_count%, %fusions_fuse_count_<slot>%");
        }
    }
}