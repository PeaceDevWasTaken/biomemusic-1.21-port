package com.biomemusic.mixin;

import com.biomemusic.AdditionalMusic;
import com.biomemusic.BiomeMusic;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.biomemusic.AdditionalMusic.WATER_ADDITIONAL;

@Mixin(Minecraft.class)
public class ClientMusicChoiceMixin
{
    @Shadow
    @Nullable
    public Screen screen;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Final
    public Gui gui;

    @Unique
    private int caveTicks = 0;

    @Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
    private void biomemusic$musicChoice(final CallbackInfoReturnable<Music> cir)
    {
        if (screen instanceof WinScreen)
        {
            return;
        }

        Music music = Optionull.map(this.screen, Screen::getBackgroundMusic);
        if (music != null)
        {
            cir.setReturnValue(music);
            return;
        }

        List<Music> possibleTracks = new ArrayList<>();

        if (this.player != null)
        {
            if (player.getY() < player.level().getSeaLevel() && !player.level().canSeeSky(player.blockPosition()))
            {
                if (player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()) < 6)
                {
                    caveTicks++;
                }
            }
            else
            {
                caveTicks = 0;
            }

            if (this.player.level().dimension() == Level.END)
            {
                if (gui.getBossOverlay().shouldPlayMusic())
                {
                    possibleTracks.add(Musics.END_BOSS);
                }
                else
                {
                    possibleTracks.add(Musics.END);
                }

                if (!BiomeMusic.config.getCommonConfig().disableDefaultMusicInDimensions)
                {
                    possibleTracks.add(Musics.GAME);
                }

                possibleTracks.add(AdditionalMusic.END_ADDITIONAL);
                possibleTracks.add(AdditionalMusic.END_ADDITIONAL);
            }
            else if (player.level().dimension() == Level.NETHER)
            {
                if (!BiomeMusic.config.getCommonConfig().disableDefaultMusicInDimensions)
                {
                    possibleTracks.add(Musics.GAME);
                }
                possibleTracks.add(AdditionalMusic.NETHER_ALL);
                possibleTracks.add(AdditionalMusic.NETHER_ALL);
            }
            else
            {
                if ((player.level().getDayTime() % 24000) > 12600)
                {
                    possibleTracks.add(AdditionalMusic.NIGHT_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.NIGHT_ADDITIONAL);

                    if (BiomeMusic.config.getCommonConfig().playonlycustomnightmusic)
                    {
                        cir.setReturnValue(AdditionalMusic.NIGHT_ADDITIONAL);
                        return;
                    }
                }

                if (caveTicks > 300)
                {
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                    possibleTracks.add(AdditionalMusic.CAVE_ADDITIONAL);
                }

                if (player.isCreative())
                {
                    possibleTracks.add(Musics.CREATIVE);
                }

                possibleTracks.add(Musics.GAME);
                possibleTracks.add(AdditionalMusic.GAME_ADDITIONAL);
                possibleTracks.add(AdditionalMusic.GAME_ADDITIONAL);

                if (this.player.isUnderWater() && this.player.level().getBiome(this.player.blockPosition()).is(BiomeTags.PLAYS_UNDERWATER_MUSIC))
                {
                    possibleTracks.clear();
                    possibleTracks.add(Musics.UNDER_WATER);
                    possibleTracks.add(Musics.UNDER_WATER);
                    possibleTracks.add(Musics.UNDER_WATER);
                    possibleTracks.add(WATER_ADDITIONAL);
                    possibleTracks.add(WATER_ADDITIONAL);
                    caveTicks = 0;
                }
            }

            // Add biome music
            Holder<Biome> holder = this.player.level().getBiome(this.player.blockPosition());
            final Music biomeMusic = holder.value().getBackgroundMusic().orElse(null);
            if (biomeMusic != null)
            {
                if (!BiomeMusic.config.getCommonConfig().musicVariance)
                {
                    possibleTracks.clear();
                }

                for (int i = 0; i < 5; i++)
                {
                    possibleTracks.add(biomeMusic);
                }
            }

            if (BiomeMusic.config.getCommonConfig().musicVariance)
            {
                for (final Map.Entry<TagKey<Biome>, List<Music>> entry : AdditionalMusic.taggedMusic.entrySet())
                {
                    if (holder.is(entry.getKey()))
                    {
                        possibleTracks.addAll(entry.getValue());
                    }
                }

                for (final Map.Entry<String, List<Music>> entry : AdditionalMusic.namedMusic.entrySet())
                {
                    if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().location().getPath().contains(entry.getKey()))
                    {
                        possibleTracks.addAll(entry.getValue());
                    }
                }
            }
        }
        else
        {
            possibleTracks.add(Musics.MENU);
        }

        if (possibleTracks.isEmpty())
        {
            return;
        }

        for (Iterator<Music> iterator = possibleTracks.iterator(); iterator.hasNext(); )
        {
            final Music track = iterator.next();
            if (AdditionalMusic.DISABLED.contains(track))
            {
                iterator.remove();
            }
        }

        cir.setReturnValue(possibleTracks.get(BiomeMusic.rand.nextInt(possibleTracks.size())));
    }
}
