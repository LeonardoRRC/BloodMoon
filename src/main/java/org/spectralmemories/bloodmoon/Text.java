package org.spectralmemories.bloodmoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Todo el texto del plugin pasa por aqui y se interpreta con MiniMessage.
 *
 * Para no romper los locales.yml y configs antiguos, antes de parsear se convierten los codigos
 * legacy (&a, &l, §c, &#RRGGBB) a sus etiquetas MiniMessage. Se pueden mezclar ambos formatos.
 *
 * Los placeholders del plugin ($p, $d, $k...) se insertan como etiquetas propias: los valores de texto
 * van sin parsear (un jugador llamado "<red>x" no puede inyectar formato) y los Component se insertan tal cual.
 */
public final class Text
{
    private Text() {}

    public static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final Pattern LEGACY_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TAGS = new HashMap<>();

    static
    {
        LEGACY_TAGS.put('0', "black");
        LEGACY_TAGS.put('1', "dark_blue");
        LEGACY_TAGS.put('2', "dark_green");
        LEGACY_TAGS.put('3', "dark_aqua");
        LEGACY_TAGS.put('4', "dark_red");
        LEGACY_TAGS.put('5', "dark_purple");
        LEGACY_TAGS.put('6', "gold");
        LEGACY_TAGS.put('7', "gray");
        LEGACY_TAGS.put('8', "dark_gray");
        LEGACY_TAGS.put('9', "blue");
        LEGACY_TAGS.put('a', "green");
        LEGACY_TAGS.put('b', "aqua");
        LEGACY_TAGS.put('c', "red");
        LEGACY_TAGS.put('d', "light_purple");
        LEGACY_TAGS.put('e', "yellow");
        LEGACY_TAGS.put('f', "white");
        LEGACY_TAGS.put('k', "obfuscated");
        LEGACY_TAGS.put('l', "bold");
        LEGACY_TAGS.put('m', "strikethrough");
        LEGACY_TAGS.put('n', "underlined");
        LEGACY_TAGS.put('o', "italic");
        LEGACY_TAGS.put('r', "reset");
    }

    /**
     * Convierte codigos legacy a etiquetas MiniMessage. Como en el formato legacy un color anula
     * negrita, cursiva, etc., cada color va precedido de &lt;reset&gt; para que se vea igual que antes
     */
    public static String LegacyToMini (String text)
    {
        if (text == null || (text.indexOf('&') < 0 && text.indexOf('§') < 0)) return text == null ? "" : text;

        Matcher hex = LEGACY_HEX.matcher(text);
        StringBuilder withHex = new StringBuilder();
        while (hex.find()) hex.appendReplacement(withHex, Matcher.quoteReplacement("<reset><#" + hex.group(1) + ">"));
        hex.appendTail(withHex);

        Matcher code = LEGACY_CODE.matcher(withHex.toString());
        StringBuilder result = new StringBuilder();
        while (code.find())
        {
            char c = Character.toLowerCase(code.group(1).charAt(0));
            String tag = LEGACY_TAGS.get(c);
            boolean isColor = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            String replacement = isColor ? "<reset><" + tag + ">" : "<" + tag + ">";
            code.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        code.appendTail(result);
        return result.toString();
    }

    /**
     * Parsea un texto (MiniMessage, legacy o mezcla). $n es salto de linea
     *
     * @param keys   placeholders a reemplazar, por ejemplo {"$p", "$k"}. Puede ser null
     * @param values valores en el mismo orden: Component se inserta con su formato, cualquier otra cosa como texto plano
     */
    public static Component Parse (String raw, String[] keys, Object[] values)
    {
        String text = LegacyToMini(raw).replace("$n", "<newline>");

        TagResolver.Builder resolvers = TagResolver.builder();
        if (keys != null && values != null)
        {
            int count = Math.min(keys.length, values.length);
            for (int i = 0; i < count; i++)
            {
                if (keys[i] == null || !text.contains(keys[i])) continue;
                String tag = "bm_arg" + i;
                text = text.replace(keys[i], "<" + tag + ">");
                Object value = values[i];
                resolvers.resolver(value instanceof Component
                        ? Placeholder.component(tag, (Component) value)
                        : Placeholder.unparsed(tag, String.valueOf(value)));
            }
        }

        try
        {
            return MINI.deserialize(text, resolvers.build());
        }
        catch (Exception e)
        {
            Compat.warn("Texto con formato MiniMessage invalido: '" + raw + "' (" + e.getMessage() + ")");
            return Component.text(MINI.stripTags(text));
        }
    }

    public static Component Parse (String raw)
    {
        return Parse(raw, null, null);
    }

    /**
     * Para nombres y lore de items: sin cursiva salvo que el texto la pida, como se espera en un item custom
     */
    public static Component Item (String raw)
    {
        return Parse(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String Plain (Component component)
    {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
