package com.howlite.cobblemoncards.screen;

import com.howlite.cobblemoncards.CobblemonCardsConfig;
import com.howlite.cobblemoncards.menu.CardRestorerMenu;
import com.howlite.cobblemoncards.network.ChangeRestorerTargetGradePayload;
import com.howlite.cobblemoncards.network.PerformRestorerPayload;
import com.howlite.cobblemoncards.util.PlatformHelper;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CardRestorerScreen extends AbstractContainerScreen<CardRestorerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon-cards", "textures/gui/card_restorer.png"
    );

    private static final int GUI_WIDTH = 232;
    private static final int GUI_HEIGHT = 202;
    private static final int TEX_SIZE = 512;

    // =========================================================================
    // Barre de grade (10 segments) — piste dans le GUI
    // =========================================================================
    // Position dans le GUI : x=27, y=108 (ajusté +15px) ; largeur totale = 178 px, hauteur = 6 px
    private static final int GRADE_BAR_X       = 27;
    private static final int GRADE_BAR_Y       = 108;
    private static final int GRADE_BAR_TOTAL_W = 178;
    private static final int GRADE_BAR_H       = 6;

    // UV sprites de grade dans la texture 512×512
    // Bleu  (grade cible)  : U=240, V=0,  W=178, H=6
    // Vert  (grade actuel) : U=240, V=6,  W=178, H=6
    // Gris  (case vide)    : aucun sprite → fillRect 0xFF3A3A3A
    private static final int UV_GRADE_BLUE_U  = 240;
    private static final int UV_GRADE_BLUE_V  = 0;
    private static final int UV_GRADE_GREEN_U = 240;
    private static final int UV_GRADE_GREEN_V = 6;

    // =========================================================================
    // Boutons [-] [box] [+]
    // =========================================================================
    // Positions dans le GUI (zone noire gauche de la machine, décalés +9px X)
    private static final int BTN_MINUS_X  = 29;
    private static final int BTN_MINUS_Y  = 86;
    private static final int BTN_MIDDLE_X = 41;
    private static final int BTN_MIDDLE_Y = 86;
    private static final int BTN_PLUS_X   = 62;
    private static final int BTN_PLUS_Y   = 86;

    // UV sprites boutons dans la texture 512×512
    // [-]  : U=272, V=16, W=10, H=10
    // [box]: U=283, V=16, W=19, H=10
    // [+]  : U=303, V=16, W=10, H=10
    private static final int UV_BTN_MINUS_U  = 272;
    private static final int UV_BTN_MINUS_V  = 16;
    private static final int UV_BTN_MIDDLE_U = 283;
    private static final int UV_BTN_MIDDLE_V = 16;
    private static final int UV_BTN_PLUS_U   = 303;
    private static final int UV_BTN_PLUS_V   = 16;

    // =========================================================================
    // Réservoir vertical de Dust (barre violette hachurée)
    // =========================================================================
    // Texture analysis: tube réservoir X=161, Y=24 (étendu +17px vers le haut), W=17, H=71
    private static final int RESERVOIR_X = 161;
    private static final int RESERVOIR_Y = 24;
    private static final int RESERVOIR_W = 17;
    private static final int RESERVOIR_H = 71;

    private static final int UV_RESERVOIR_FILL_U    = 240;
    private static final int UV_RESERVOIR_FILL_V    = 16;
    private static final int RESERVOIR_FILL_TILE_H  = 70; // hauteur totale de la tuile source

    // =========================================================================
    // NOUVEAU BOUTON RESTORE GRAND (sous le cadre de la carte)
    // =========================================================================
    // Position dans le GUI : x=81, y=82 (abaissé de 18px), W=70, H=14
    private static final int RESTORE_BTN_X = 81;
    private static final int RESTORE_BTN_Y = 82;
    private static final int RESTORE_BTN_W = 70;
    private static final int RESTORE_BTN_H = 14;

    // UV sprites du bouton Restore (70x14) dans la texture 512x512
    // 1. Variant Base (Gris/Inactif)    : U=272, V=32
    // 2. Variant Selected (Vert/Prêt)   : U=272, V=46
    // 3. Variant Blocked (Haché/Timer)  : U=272, V=60
    private static final int UV_RESTORE_BTN_BASE_U     = 272;
    private static final int UV_RESTORE_BTN_BASE_V     = 32;
    private static final int UV_RESTORE_BTN_SELECTED_U = 272;
    private static final int UV_RESTORE_BTN_SELECTED_V = 46;
    private static final int UV_RESTORE_BTN_BLOCKED_U  = 272;
    private static final int UV_RESTORE_BTN_BLOCKED_V  = 60;

    // =========================================================================
    // Zone centrale du cadre de carte (X=96..135, Y=22..63, W=40, H=41)
    // =========================================================================
    // Texture analysis: Cadre gris X=96, Y=22, W=40, H=41 (Centre X=116, Y=42)
    private static final int CARD_FRAME_X = 96;
    private static final int CARD_FRAME_Y = 22;
    private static final int CARD_FRAME_W = 40;
    private static final int CARD_FRAME_H = 41;

    // Timer de restauration côté client (60 ticks = 3 secondes)
    private int restoreTimerTicks = 0;
    private static final int TOTAL_RESTORE_TICKS = 60;

    public CardRestorerScreen(CardRestorerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth  = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 3;
        // On cache le label "Inventory" en le plaçant hors du GUI
        this.inventoryLabelY = this.imageHeight + 10;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Uniquement le titre du Card Restorer, sans l'étiquette "Inventory"
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.restoreTimerTicks > 0) {
            this.restoreTimerTicks--;
            if (this.restoreTimerTicks == 0) {
                if (menu.canRestore()) {
                    PlatformHelper.INSTANCE.sendToServer(new PerformRestorerPayload());
                }
            }
        }
    }

    // Supprime le rendu par défaut du slot 0 (carte) pour éviter le doublon
    // avec le rendu 3D custom agrandi de renderCenterEnlargedCard()
    @Override
    protected void renderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot) {
        if (slot == this.menu.getSlot(0)) {
            return; // Le slot carte est rendu via renderCenterEnlargedCard
        }
        super.renderSlot(graphics, slot);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);

        // Rendu de la carte en GRAND au centre du cadre (comme dans les Binders)
        renderCenterEnlargedCard(graphics);

        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 1. Fond principal de l'interface (232x202)
        graphics.blit(TEXTURE, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEX_SIZE, TEX_SIZE);

        // 2. Rendu du réservoir de Dust (Fond + Jauge de remplissage)
        renderDustReservoir(graphics, x, y);

        // 3. Rendu de la barre de grade (10 segments)
        renderGradeBar(graphics, x, y);

        // 4. Rendu des boutons [-] [box] [+]
        renderButtons(graphics, x, y);

        // 5. Rendu du grand bouton Restore (3 variantes)
        renderRestoreButton(graphics, x, y);

        // 6. Textes d'information (coût en dust et statut)
        renderDustCostText(graphics, x, y);
    }

    /**
     * Affiche la jauge violette hachurée se remplissant du bas vers le haut.
     * La tuile source (U=240, V=16, W=16, H=70) est découpée depuis le bas
     * pour simuler un remplissage progressif.
     */
    private void renderDustReservoir(GuiGraphics graphics, int x, int y) {
        int stored = menu.getStoredDust();
        int max    = menu.getMaxStoredDust();

        if (stored > 0 && max > 0) {
            float ratio = Math.min(1.0f, (float) stored / (float) max);
            int   fillH = (int) (ratio * RESERVOIR_H);
            if (fillH > 0) {
                // On dessine depuis le BAS du cadre
                int drawY   = y + RESERVOIR_Y + (RESERVOIR_H - fillH);
                int drawX   = x + RESERVOIR_X + 1; // Intérieur de la jauge 15px dans le cadre de 17px
                // Offset dans la tuile source (partie basse de la tuile de 70 px)
                int spriteV = UV_RESERVOIR_FILL_V + (RESERVOIR_FILL_TILE_H - fillH);
                graphics.blit(TEXTURE,
                        drawX, drawY,
                        UV_RESERVOIR_FILL_U, spriteV,
                        16, fillH,
                        TEX_SIZE, TEX_SIZE);
            }
        }
    }

    /**
     * Rendu de la carte insérée en GRAND au centre du cadre de la machine (X=96..135, Y=22..63)
     */
    private void renderCenterEnlargedCard(GuiGraphics graphics) {
        Slot cardSlot = this.menu.getSlot(0);
        if (cardSlot != null && cardSlot.hasItem()) {
            ItemStack stack = cardSlot.getItem();
            if (!stack.isEmpty()) {
                graphics.pose().pushPose();

                // Centrer la carte dans le cadre central et l'abaisser dans le slot (X=116, Y=48)
                int centerX = this.leftPos + CARD_FRAME_X + (CARD_FRAME_W / 2);
                int centerY = this.topPos  + CARD_FRAME_Y + (CARD_FRAME_H / 2) + 6;

                graphics.pose().translate(centerX, centerY, 150);
                float scale = 48.0f; // Échelle agrandie (remis à la grande taille demandée)
                graphics.pose().scale(scale, -scale, scale);

                Lighting.setupForFlatItems();
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        ItemDisplayContext.GUI,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        graphics.pose(),
                        graphics.bufferSource(),
                        Minecraft.getInstance().level,
                        0
                );
                graphics.flush();
                graphics.pose().popPose();
            }
        }
    }

    /**
     * Rendu du grand bouton Restore sous la carte en 3 variantes :
     * 1. Variant Bloqué (U=272, V=60) : Pendant l'animation du timer de restauration
     * 2. Variant Sélectionné (U=272, V=46) : Quand la restauration est possible (vert brillant)
     * 3. Variant De base (U=272, V=32) : Inactif (gris neutre)
     */
    private void renderRestoreButton(GuiGraphics graphics, int x, int y) {
        int btnX = x + RESTORE_BTN_X;
        int btnY = y + RESTORE_BTN_Y;

        if (restoreTimerTicks > 0) {
            // Variante 3 : Bloqué avec timer (hachures sombres)
            graphics.blit(TEXTURE, btnX, btnY, UV_RESTORE_BTN_BLOCKED_U, UV_RESTORE_BTN_BLOCKED_V, RESTORE_BTN_W, RESTORE_BTN_H, TEX_SIZE, TEX_SIZE);

            float secondsLeft = restoreTimerTicks / 20.0f;
            String text = String.format("%.1fs", secondsLeft);
            int textW = font.width(text);
            graphics.drawString(font, text, btnX + (RESTORE_BTN_W - textW) / 2, btnY + 3, 0xFFFF55, true);
        } else if (menu.canRestore()) {
            // Variante 2 : Sélectionné / Prêt (Vert brillant)
            graphics.blit(TEXTURE, btnX, btnY, UV_RESTORE_BTN_SELECTED_U, UV_RESTORE_BTN_SELECTED_V, RESTORE_BTN_W, RESTORE_BTN_H, TEX_SIZE, TEX_SIZE);

            String text = "RESTORE";
            int textW = font.width(text);
            graphics.drawString(font, text, btnX + (RESTORE_BTN_W - textW) / 2, btnY + 3, 0xFFFFFF, true);
        } else {
            // Variante 1 : De base / Inactif (Gris neutre)
            graphics.blit(TEXTURE, btnX, btnY, UV_RESTORE_BTN_BASE_U, UV_RESTORE_BTN_BASE_V, RESTORE_BTN_W, RESTORE_BTN_H, TEX_SIZE, TEX_SIZE);

            String text = "RESTORE";
            int textW = font.width(text);
            graphics.drawString(font, text, btnX + (RESTORE_BTN_W - textW) / 2, btnY + 3, 0x888888, false);
        }
    }

    /**
     * Rendu de la barre de grade à 10 cases :
     *   Vert  = grade actuel de la carte
     *   Bleu  = grade cible
     *   Gris  = case vide (fillRect)
     */
    private void renderGradeBar(GuiGraphics graphics, int x, int y) {
        int currentGrade = menu.getCurrentCardGrade();
        int targetGrade  = menu.getTargetGrade();

        for (int i = 1; i <= 10; i++) {
            int segX  = (int) ((i - 1) * 17.8f);
            int nextX = (int) (i       * 17.8f);
            int segW  = nextX - segX;

            int cellX = x + GRADE_BAR_X + segX;
            int cellY = y + GRADE_BAR_Y;

            if (i <= currentGrade) {
                int spriteU = UV_GRADE_GREEN_U + segX;
                graphics.blit(TEXTURE, cellX, cellY,
                        spriteU, UV_GRADE_GREEN_V,
                        segW, GRADE_BAR_H,
                        TEX_SIZE, TEX_SIZE);
            } else if (i <= targetGrade) {
                int spriteU = UV_GRADE_BLUE_U + segX;
                graphics.blit(TEXTURE, cellX, cellY,
                        spriteU, UV_GRADE_BLUE_V,
                        segW, GRADE_BAR_H,
                        TEX_SIZE, TEX_SIZE);
            } else if (i > CobblemonCardsConfig.restorerMaxGrade) {
                // Hors de portée du Restorer (uniquement obtenable par grading)
                graphics.fill(cellX, cellY, cellX + segW, cellY + GRADE_BAR_H, 0xFF1E1E1E);
            } else {
                graphics.fill(cellX, cellY, cellX + segW, cellY + GRADE_BAR_H, 0xFF3A3A3A);
            }
        }
    }

    private void renderButtons(GuiGraphics graphics, int x, int y) {
        // Bouton [-]
        graphics.blit(TEXTURE,
                x + BTN_MINUS_X, y + BTN_MINUS_Y,
                UV_BTN_MINUS_U, UV_BTN_MINUS_V,
                10, 10, TEX_SIZE, TEX_SIZE);

        // Boîte centrale [box]
        graphics.blit(TEXTURE,
                x + BTN_MIDDLE_X, y + BTN_MIDDLE_Y,
                UV_BTN_MIDDLE_U, UV_BTN_MIDDLE_V,
                19, 10, TEX_SIZE, TEX_SIZE);

        // Bouton [+]
        graphics.blit(TEXTURE,
                x + BTN_PLUS_X, y + BTN_PLUS_Y,
                UV_BTN_PLUS_U, UV_BTN_PLUS_V,
                10, 10, TEX_SIZE, TEX_SIZE);

        // Chiffre du grade cible à l'intérieur de la boîte centrale
        int    targetGrade = menu.getTargetGrade();
        String targetText  = targetGrade > 0 ? String.valueOf(targetGrade) : "-";
        int    textW       = font.width(targetText);
        graphics.drawString(font, targetText,
                x + BTN_MIDDLE_X + (19 - textW) / 2 + 1,
                y + BTN_MIDDLE_Y + 1,
                0xFFFFFF, false);
    }

    private void renderDustCostText(GuiGraphics graphics, int x, int y) {
        int dustCost     = menu.getDustCost();
        int currentGrade = menu.getCurrentCardGrade();
        int targetGrade  = menu.getTargetGrade();
        int maxGrade     = CobblemonCardsConfig.restorerMaxGrade;

        // Zone de texte : abaissée à Y=36 au-dessus de la flèche
        int textX = x + 12;
        int textY = y + 36;

        if (currentGrade <= 0) {
            graphics.drawString(font, "Insert Card",  textX, textY,      0xAAAAAA, false);
            graphics.drawString(font, "Grade 1-" + maxGrade, textX, textY + 11, 0x777777, false);
        } else if (currentGrade >= maxGrade) {
            graphics.drawString(font, "Grade " + currentGrade, textX, textY,      0x55FF55, false);
            graphics.drawString(font, "Max reached",           textX, textY + 11, 0xFF5555, false);
        } else if (targetGrade <= currentGrade) {
            graphics.drawString(font, "Grade " + currentGrade, textX, textY,      0x55FF55, false);
            graphics.drawString(font, "Use + button",          textX, textY + 11, 0xAAAAAA, false);
        } else {
            graphics.drawString(font, "Cost: " + dustCost + " Dust",
                    textX, textY,      0xFFFF55, false);
            graphics.drawString(font, "G" + currentGrade + " \u2192 G" + targetGrade,
                    textX, textY + 11, 0x55FFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        // Clic sur bouton [-]
        if (isInBounds(mouseX, mouseY, x + BTN_MINUS_X, y + BTN_MINUS_Y, 10, 10)) {
            int currentGrade = menu.getCurrentCardGrade();
            int targetGrade = menu.getTargetGrade();
            int newTarget = Math.max(currentGrade + 1, targetGrade - 1);
            if (newTarget <= currentGrade) {
                newTarget = 0;
            }
            PlatformHelper.INSTANCE.sendToServer(new ChangeRestorerTargetGradePayload(newTarget));
            return true;
        }

        // Clic sur bouton [+]
        if (isInBounds(mouseX, mouseY, x + BTN_PLUS_X, y + BTN_PLUS_Y, 10, 10)) {
            int currentGrade = menu.getCurrentCardGrade();
            int targetGrade = menu.getTargetGrade();
            int maxGrade = CobblemonCardsConfig.restorerMaxGrade;
            if (currentGrade >= maxGrade) {
                return true;
            }
            int newTarget;
            if (targetGrade <= currentGrade) {
                newTarget = Math.min(maxGrade, currentGrade + 1);
            } else {
                newTarget = Math.min(maxGrade, targetGrade + 1);
            }
            PlatformHelper.INSTANCE.sendToServer(new ChangeRestorerTargetGradePayload(newTarget));
            return true;
        }

        // Clic sur le grand bouton Restore (X=81, Y=82, W=70, H=14)
        if (isInBounds(mouseX, mouseY, x + RESTORE_BTN_X, y + RESTORE_BTN_Y, RESTORE_BTN_W, RESTORE_BTN_H)) {
            if (this.restoreTimerTicks <= 0 && menu.canRestore()) {
                this.restoreTimerTicks = TOTAL_RESTORE_TICKS;
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                        )
                );
            }
            return true;
        }

        // Clic n'importe où dans la zone de la carte (cadre central X=96..135, Y=22..63) -> Interagir avec le slot 0
        if (isInBounds(mouseX, mouseY, x + CARD_FRAME_X, y + CARD_FRAME_Y, CARD_FRAME_W, CARD_FRAME_H)) {
            Slot cardSlot = this.menu.getSlot(0);
            if (cardSlot != null) {
                this.slotClicked(cardSlot, 0, button, net.minecraft.world.inventory.ClickType.PICKUP);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInBounds(double mouseX, double mouseY, int bx, int by, int bw, int bh) {
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        int x = this.leftPos;
        int y = this.topPos;

        // Tooltip — Réservoir de Dust
        if (isInBounds(mouseX, mouseY, x + RESERVOIR_X, y + RESERVOIR_Y, RESERVOIR_W, RESERVOIR_H)) {
            graphics.renderTooltip(font,
                    Component.literal("Stored Dust: "
                            + String.format("%,d", menu.getStoredDust())
                            + " / "
                            + String.format("%,d", menu.getMaxStoredDust())),
                    mouseX, mouseY);
        }

        // Tooltip sur le grand bouton Restore
        if (isInBounds(mouseX, mouseY, x + RESTORE_BTN_X, y + RESTORE_BTN_Y, RESTORE_BTN_W, RESTORE_BTN_H)) {
            if (this.restoreTimerTicks > 0) {
                float secondsLeft = restoreTimerTicks / 20.0f;
                graphics.renderTooltip(font, Component.literal("Restoring... " + String.format("%.1fs", secondsLeft)), mouseX, mouseY);
            } else if (menu.getCurrentCardGrade() >= CobblemonCardsConfig.restorerMaxGrade) {
                graphics.renderTooltip(font, Component.translatable("gui.cobblemon-cards.card_restorer.max_grade"), mouseX, mouseY);
            } else if (menu.canRestore()) {
                graphics.renderTooltip(font, Component.translatable("gui.cobblemon-cards.card_restorer.restore"), mouseX, mouseY);
            } else if (menu.getCurrentCardGrade() > 0 && menu.getTargetGrade() > menu.getCurrentCardGrade()) {
                graphics.renderTooltip(font, Component.translatable("gui.cobblemon-cards.card_restorer.not_enough_dust"), mouseX, mouseY);
            }
        }

        // Tooltip Barre de grade
        if (isInBounds(mouseX, mouseY, x + GRADE_BAR_X, y + GRADE_BAR_Y, GRADE_BAR_TOTAL_W, GRADE_BAR_H)) {
            double relX = mouseX - (x + GRADE_BAR_X);
            int hoverGrade = Math.min(10, Math.max(1, (int) (relX / 17.8f) + 1));
            graphics.renderTooltip(font, Component.literal("Grade " + hoverGrade), mouseX, mouseY);
        }
    }
}
