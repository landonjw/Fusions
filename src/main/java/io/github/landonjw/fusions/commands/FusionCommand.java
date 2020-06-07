package io.github.landonjw.fusions.commands;

import io.github.landonjw.fusions.Fusions;
import io.github.landonjw.fusions.api.Fusion;
import io.github.landonjw.fusions.configuration.ConfigManager;
import io.github.landonjw.fusions.ui.FusionGUI;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Command for fusions. Will either create fusion from command arguments, or open GUI.
 * It will only open GUI if TeslaPowered is registered on the server, and configuration setting is enabled.
 *
 * @author landonjw
 * @since 1.0.0
 */
public class FusionCommand implements CommandExecutor {

    /** Stores cooldowns for individual players if cooldown configuration setting is not 0. */
    private static HashMap<UUID, Instant> cooldowns = new HashMap<>();
    /** How long the cooldown should be for the fusion command, or 0 to disable. */
    private int cooldown;
    /** If GUI is enabled. */
    private boolean enableGUI;

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if(src instanceof Player){

            Player player = (Player) src;

            cooldown = ConfigManager.getConfigNode("Fusing-Features", "Cooldown").getInt();
            enableGUI = (Fusions.isTeslaRegistered() && ConfigManager.getConfigNode("GUI-Features", "Enable-GUI").getBoolean());

            //Check that player is not still on cooldown if cooldown feature is enabled.
            if(cooldown > 0){
                clearFinishedCooldowns();
                if(cooldowns.containsKey(player.getUniqueId())){
                    Instant instant = cooldowns.get(player.getUniqueId());

                    if(Duration.between(instant, Instant.now()).compareTo(Duration.ofSeconds(cooldown)) <= 0){
                        long seconds = cooldown - Duration.between(instant, Instant.now()).getSeconds();
                        player.sendMessage(Text.of(TextColors.RED, "Command is still on cooldown for " + seconds + " seconds."));
                        return CommandResult.success();
                    }
                }
            }

            //Check if command should use GUI or not.
            if(enableGUI){
                FusionGUI gui = new FusionGUI(player);
                gui.openGUI();


            }
            else{
                //Stop command if both slot arguments are not present.
                if(!args.<Integer>getOne("pokemon").isPresent() || !args.<Integer>getOne("sacrifice").isPresent()){
                    player.sendMessage(Text.of(TextColors.DARK_RED, "Not enough arguments. Usage: /fusion <pokemon> <sacrifice>"));
                    return CommandResult.success();
                }

                int pokemonSlot = args.<Integer>getOne("pokemon").get();
                int sacrificeSlot = args.<Integer>getOne("sacrifice").get();

                Fusion fusion = new Fusion(player, pokemonSlot, sacrificeSlot);
                fusion.startFusion();
            }
        }
        return CommandResult.success();
    }

    /**
     * Clears UUIDs from the cooldown map if the cooldown has already passed.
     */
    private void clearFinishedCooldowns(){
        List<UUID> uuidsToRemove = new ArrayList<>();
        cooldowns.forEach((uuid, instant) -> {
            if(Duration.between(instant, Instant.now()).compareTo(Duration.ofSeconds(cooldown)) > 0){
                uuidsToRemove.add(uuid);
            }
        });
        for(UUID uuid : uuidsToRemove){
            cooldowns.remove(uuid);
        }
    }

    /**
     * Adds a player to the cooldown list.
     * Used for GUI so that player does not go on cooldown without successfully fusing.
     *
     * @param uuid UUID of the player to add cooldown to.
     */
    public static void addCooldown(UUID uuid){
        cooldowns.put(uuid, Instant.now());
    }
}
