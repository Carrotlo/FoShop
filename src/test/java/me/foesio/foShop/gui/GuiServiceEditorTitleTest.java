package me.foesio.foShop.gui;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiServiceEditorTitleTest {

    @Test
    void codeOwnedEditorTitlesAreDarkAndSmallCaps() {
        assertEquals("§8ꜰᴏꜱʜᴏᴘ ᴇᴅɪᴛᴏʀ", GuiService.editorTitle("FoShop Editor"));
        assertEquals("§8ʀᴏᴛᴀᴛɪɴɢ: ʙʟᴏᴄᴋꜱ", GuiService.editorTitle("Rotating: blocks"));
        assertTrue(GuiService.editorTitle("Rotating Items: blocks")
                .startsWith(GuiService.editorTitle("Rotating Items: ")));
    }

    @Test
    void receiptSeparatorKeepsConfiguredSpace() {
        assertEquals(" ", PlainTextComponentSerializer.plainText().serialize(GuiService.receiptSeparator(" ")));
    }
}
