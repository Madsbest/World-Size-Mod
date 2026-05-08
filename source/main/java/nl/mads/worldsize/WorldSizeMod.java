package nl.mads.worldsize;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static net.minecraft.commands.Commands.literal;

public class WorldSizeMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("assets/worldsize");

    @Override
    public void onInitialize() {
        LOGGER.info("WorldSize mod Loaded!");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("assets/worldsize")
                    .executes(this::executeWorldSize)
            );
        });
    }

    private int executeWorldSize(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath();

        // Stuur een header bericht
        context.getSource().sendSuccess(
            () -> Component.literal("§6§l=== World Size ==="), false
        );
        context.getSource().sendSuccess(
            () -> Component.literal("§7Path: §f" + worldDir), false
        );

        // Bereken de grootte per map (26.1 heeft nieuwe mappenstructuur)
        Map<String, Long> sectionSizes = new LinkedHashMap<>();

        // Dimensies (nieuw in 26.1: dimensions/minecraft/*)
        sectionSizes.put("Overworld (regions)",   getFolderSize(worldDir.resolve("dimensions/minecraft/overworld/region")));
        sectionSizes.put("Nether (regions)",       getFolderSize(worldDir.resolve("dimensions/minecraft/the_nether/region")));
        sectionSizes.put("The End (regions)",      getFolderSize(worldDir.resolve("dimensions/minecraft/the_end/region")));

        // Entities (ook per dimensie in 26.1)
        sectionSizes.put("Overworld (entities)",   getFolderSize(worldDir.resolve("dimensions/minecraft/overworld/entities")));
        sectionSizes.put("Nether (entities)",       getFolderSize(worldDir.resolve("dimensions/minecraft/the_nether/entities")));
        sectionSizes.put("The End (entities)",      getFolderSize(worldDir.resolve("dimensions/minecraft/the_end/entities")));

        // Spelers
        sectionSizes.put("Player Data",             getFolderSize(worldDir.resolve("players")));

        // Gedeelde data
        sectionSizes.put("Data (Shared)",         getFolderSize(worldDir.resolve("data")));

        // Totaal
        long totalBytes = getFolderSize(worldDir);

        // Stuur elke sectie als bericht
        for (Map.Entry<String, Long> entry : sectionSizes.entrySet()) {
            if (entry.getValue() > 0) {
                String name = entry.getKey();
                String size = formatBytes(entry.getValue());
                context.getSource().sendSuccess(
                    () -> Component.literal("§e" + name + ": §f" + size), false
                );
            }
        }

        // Totaallijn
        String totalStr = formatBytes(totalBytes);
        context.getSource().sendSuccess(
            () -> Component.literal("§6Total: §f§l" + totalStr), false
        );

        return 1;
    }

    /**
     * Berekent de totale grootte van een map (inclusief submappen).
     * Geeft 0 terug als de map niet bestaat.
     */
    private long getFolderSize(Path folder) {
        if (!Files.exists(folder)) return 0L;

        AtomicLong size = new AtomicLong(0L);
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOGGER.warn("Couldn't read file: {}", file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.error("Error with calculating {}: {}", folder, e.getMessage());
        }
        return size.get();
    }

    /**
     * Formatteert bytes naar een leesbare string (B, KB, MB, GB).
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024)          return bytes + " B";
        if (bytes < 1024 * 1024)   return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
