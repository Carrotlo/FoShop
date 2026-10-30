package me.foesio.foShop.worth;

import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketEventsWorthLoreServiceTest {

    @Test
    void worthLineAppendsWithoutReplacingOwnerLore() {
        ItemLore ownerLore = new ItemLore(List.of(Component.text("Owner description")));
        ItemLore displayed = PacketEventsWorthLoreService.appendWorthLine(ownerLore, "§aWorth: $5");

        assertEquals(1, ownerLore.getLines().size());
        assertEquals(2, displayed.getLines().size());
        assertEquals(ownerLore.getLines().getFirst(), displayed.getLines().getFirst());
    }

}
