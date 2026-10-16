package me.foesio.foKits.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilPresentationTest {
    @Test
    void ownerControlsUnitOrderWordsAndPluralForms() throws Exception {
        YamlConfiguration messages = new YamlConfiguration();
        messages.loadFromString("""
                duration:
                  zero: 'nul seconden'
                  separator: ' / '
                  day: {one: '{count} dag', many: '{count} dagen'}
                  hour: {one: '{count} uur', many: '{count} uren'}
                  minute: {one: '{count} minuut', many: '{count} minuten'}
                  second: {one: '{count} seconde', many: '{count} seconden'}
                """);

        assertEquals("1 dag / 2 uren / 1 minuut / 3 seconden",
                TimeUtil.formatDuration((86400L + 7200L + 60L + 3L) * 1000L, messages));
        assertEquals("nul seconden", TimeUtil.formatDuration(0, messages));
        messages.set("duration.second.many", null);
        assertEquals("", TimeUtil.formatDuration(3000L, messages));
    }
}
