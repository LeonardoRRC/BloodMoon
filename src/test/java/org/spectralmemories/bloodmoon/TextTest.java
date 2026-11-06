package org.spectralmemories.bloodmoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest
{
    @Test
    void legacyCodesBecomeTags ()
    {
        assertEquals("<reset><green>Hola <bold>mundo", Text.LegacyToMini("&aHola &lmundo"));
        assertEquals("<reset><#ff0000>rojo", Text.LegacyToMini("&#ff0000rojo"));
        assertEquals("<reset><red>x", Text.LegacyToMini("§cx"));
        assertEquals("<gradient:#000000:#ffffff>sin cambios</gradient>", Text.LegacyToMini("<gradient:#000000:#ffffff>sin cambios</gradient>"));
    }

    @Test
    void legacyAndMiniMessageGiveSameText ()
    {
        assertEquals("There are 3 days", Text.Plain(Text.Parse("&aThere are&f $d &adays", new String[]{"$d"}, new Object[]{"3"})).replace("  ", " "));
        assertEquals("There are 3 days", Text.Plain(Text.Parse("<green>There are <white>$d</white> days", new String[]{"$d"}, new Object[]{"3"})));
    }

    @Test
    void legacyColorKeepsColor ()
    {
        Component c = Text.Parse("&cRojo");
        assertEquals("Rojo", Text.Plain(c));
        assertTrue(containsColor(c, NamedTextColor.RED));
    }

    @Test
    void playerNamesCannotInjectTags ()
    {
        Component c = Text.Parse("<red>$p ha muerto", new String[]{"$p"}, new Object[]{"<bold><click:run_command:'/op x'>malo"});
        assertEquals("<bold><click:run_command:'/op x'>malo ha muerto", Text.Plain(c));
    }

    @Test
    void componentPlaceholdersKeepTheirStyle ()
    {
        Component boss = Text.Parse("<bold>Jefe");
        Component c = Text.Parse("$b ha llegado", new String[]{"$b"}, new Object[]{boss});
        assertEquals("Jefe ha llegado", Text.Plain(c));
    }

    @Test
    void newLineToken ()
    {
        assertEquals("a\nb", Text.Plain(Text.Parse("a$nb")));
    }

    @Test
    void itemTextIsNotItalicByDefault ()
    {
        assertEquals(TextDecoration.State.FALSE, Text.Item("Excalibur").decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, Text.Item("<italic>Excalibur").children().isEmpty()
                ? Text.Item("<italic>Excalibur").decoration(TextDecoration.ITALIC)
                : Text.Item("<italic>Excalibur").children().get(0).decoration(TextDecoration.ITALIC));
    }

    @Test
    void everyDefaultLocaleParses ()
    {
        assertEquals(LocaleReader.LOCALES_IDS.length, LocaleReader.DEFAULT_LOCALES.length);
        for (String raw : LocaleReader.DEFAULT_LOCALES)
        {
            String plain = Text.Plain(Text.Parse(raw));
            assertFalse(plain.contains("<"), "Etiqueta sin interpretar en: " + raw + " -> " + plain);
        }
    }

    private static boolean containsColor (Component c, TextColor color)
    {
        if (color.equals(c.color())) return true;
        for (Component child : c.children()) if (containsColor(child, color)) return true;
        return false;
    }
}
