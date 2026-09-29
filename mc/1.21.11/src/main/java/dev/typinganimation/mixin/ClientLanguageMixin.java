package dev.typinganimation.mixin;

import dev.typinganimation.mc.BuiltinTranslations;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Adds our translations when a loader does not load mod assets as a resource pack (Fabric without Fabric API).
 * The mutable map is intercepted where {@code loadFrom} hands it to {@code DeprecatedTranslationsInfo#applyToMap},
 * just before it is copied into the immutable language (same call on vanilla, NeoForge and Forge).
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void typinganimation$languageStack(ResourceManager resourceManager, List<String> languageStack,
                                                      boolean defaultRightToLeft,
                                                      CallbackInfoReturnable<ClientLanguage> cir) {
        BuiltinTranslations.begin(languageStack);
    }

    @ModifyArg(method = "loadFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/DeprecatedTranslationsInfo;applyToMap(Ljava/util/Map;)V"))
    private static Map<String, String> typinganimation$addTranslations(Map<String, String> translations) {
        BuiltinTranslations.addMissing(translations);
        return translations;
    }
}
