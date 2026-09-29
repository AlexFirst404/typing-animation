package dev.typinganimation.mixin;

import dev.typinganimation.mc.LangFallback;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Bundled-translation fallback for Fabric without Fabric API (see {@link LangFallback}); a no-op whenever the
 * game already loaded our lang files (NeoForge, Forge, Fabric + Fabric API).
 */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {
    @Shadow
    public abstract String getSelected();

    @ModifyArg(method = "onResourceManagerReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/language/I18n;setLanguage(Lnet/minecraft/locale/Language;)V"))
    private Language typinganimation$i18n(Language language) {
        return LangFallback.wrapIfMissing(language, getSelected());
    }

    @ModifyArg(method = "onResourceManagerReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;inject(Lnet/minecraft/locale/Language;)V"))
    private Language typinganimation$inject(Language language) {
        return LangFallback.wrapIfMissing(language, getSelected());
    }
}
