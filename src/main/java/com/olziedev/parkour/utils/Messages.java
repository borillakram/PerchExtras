package com.olziedev.parkour.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses config messages into Adventure components. Supports MiniMessage tags
 * (e.g. &lt;red&gt;, &lt;#ff0000&gt;, &lt;gradient&gt;) as well as the legacy &amp; colour codes and the
 * &amp;#AABBCC hex format, by converting those to MiniMessage before parsing.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Map<Character, String> LEGACY = new HashMap<>();

    static {
        LEGACY.put('0', "<black>");
        LEGACY.put('1', "<dark_blue>");
        LEGACY.put('2', "<dark_green>");
        LEGACY.put('3', "<dark_aqua>");
        LEGACY.put('4', "<dark_red>");
        LEGACY.put('5', "<dark_purple>");
        LEGACY.put('6', "<gold>");
        LEGACY.put('7', "<gray>");
        LEGACY.put('8', "<dark_gray>");
        LEGACY.put('9', "<blue>");
        LEGACY.put('a', "<green>");
        LEGACY.put('b', "<aqua>");
        LEGACY.put('c', "<red>");
        LEGACY.put('d', "<light_purple>");
        LEGACY.put('e', "<yellow>");
        LEGACY.put('f', "<white>");
        LEGACY.put('k', "<obfuscated>");
        LEGACY.put('l', "<bold>");
        LEGACY.put('m', "<strikethrough>");
        LEGACY.put('n', "<underlined>");
        LEGACY.put('o', "<italic>");
        LEGACY.put('r', "<reset>");
    }

    private Messages() {
    }

    /** Parses raw text (MiniMessage + legacy &amp; + &amp;#hex) with %placeholder% substitution. */
    public static Component parse(String text, String... replacements) {
        String raw = text == null ? "" : text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        try {
            return MINI.deserialize(convertLegacy(raw));
        } catch (Exception ex) {
            return Component.text(raw);
        }
    }

    public static Component get(String key, String... replacements) {
        return parse(Configuration.getConfig().getString("settings.messages." + key, ""), replacements);
    }

    /** The message prefixed with settings.messages.prefix. */
    public static Component prefixed(String key, String... replacements) {
        return get("prefix").append(get(key, replacements));
    }

    /** Sends settings.messages.&lt;key&gt; to the sender, skipping blank messages. */
    public static void send(CommandSender sender, String key, String... replacements) {
        String raw = Configuration.getConfig().getString("settings.messages." + key, "");
        if (raw.isEmpty()) return;
        sender.sendMessage(parse(raw, replacements));
    }

    /** Converts legacy &amp; codes and &amp;#AABBCC hex into MiniMessage tags. */
    private static String convertLegacy(String input) {
        // &#RRGGBB -> <#RRGGBB>
        Matcher matcher = HEX.matcher(input);
        StringBuffer hexReplaced = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(hexReplaced, Matcher.quoteReplacement("<#" + matcher.group(1) + ">"));
        }
        matcher.appendTail(hexReplaced);

        // Single-character &x codes -> matching MiniMessage tag.
        String withHex = hexReplaced.toString();
        StringBuilder out = new StringBuilder(withHex.length());
        for (int i = 0; i < withHex.length(); i++) {
            char c = withHex.charAt(i);
            if (c == '&' && i + 1 < withHex.length()) {
                String tag = LEGACY.get(Character.toLowerCase(withHex.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++; // consume the code character
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
