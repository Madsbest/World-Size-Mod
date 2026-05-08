package nl.mads.worldsize.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow @Final
    private LevelSummary summary;

    @Shadow @Final
    private Minecraft minecraft;

    @Unique
    private StringWidget worldsize_sizeWidget;

    @Unique
    private String worldsize_cachedSize = null;

    /**
     * In 26.1 gebruiken we extractContent. Dit is de vervanger van de render-methode.
     */
    @Inject(
            method = "extractContent",
            at = @At("TAIL")
    )
    private void worldsize_onExtractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
        if (worldsize_cachedSize == null) {
            Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
            Path worldDir = savesDir.resolve(summary.getLevelId());
            worldsize_cachedSize = worldsize_formatBytes(worldsize_getFolderSize(worldDir));

            // We maken de tekst iets lichter grijs voor een "clean" uiterlijk
            Component sizeComponent = Component.literal("💾 " + worldsize_cachedSize).withColor(0x888888);
            this.worldsize_sizeWidget = new StringWidget(sizeComponent, this.minecraft.font);
        }

        // We pakken de breedte van de rij (standaard 270 in jouw broncode)
        WorldSelectionList.WorldListEntry entry = (WorldSelectionList.WorldListEntry) (Object) this;
        int rowWidth = 270;
        int textWidth = this.minecraft.font.width(this.worldsize_sizeWidget.getMessage());

        // Positie: Helemaal rechts, bovenaan de entry
        // We trekken de tekstbreedte af van de totale rijbreedte
        int x = entry.getContentX() + rowWidth - textWidth - 5;
        int y = entry.getContentY() + 2; // 2 pixels van de bovenkant

        // Teken een klein donker balkje achter de tekst voor betere leesbaarheid
        // De kleur 0x66000000 is iets transparanter dan voorheen
        graphics.fill(x - 3, y - 1, x + textWidth + 3, y + 9, 0x66000000);

        // Zet de widget op de nieuwe plek en render
        this.worldsize_sizeWidget.setPosition(x, y);
        this.worldsize_sizeWidget.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Unique
    private long worldsize_getFolderSize(Path folder) {
        if (!Files.exists(folder)) return 0L;
        final AtomicLong size = new AtomicLong(0L);
        try {
            // Gebruik de 'diamond' operator <> om de Type Argument error te fixen
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                @NotNull
                public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                @NotNull
                public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return size.get();
    }

    @Unique
    private String worldsize_formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
