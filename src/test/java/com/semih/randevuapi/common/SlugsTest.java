package com.semih.randevuapi.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Spring Boot ile JWT Kimlik Doğrulama | spring-boot-ile-jwt-kimlik-dogrulama
            IŞIK ve İSTANBUL                     | isik-ve-istanbul
            Çağrı Öğütücü Şüphe                  | cagri-ogutucu-suphe
            Java 21: Records & Sealed!           | java-21-records-sealed
            Café résumé                          | cafe-resume
            '  --Boşluklar--  '                  | bosluklar
            """)
    void buildsSlugsFromTurkishAndEnglishTitles(String title, String expected) {
        assertThat(Slugs.from(title)).isEqualTo(expected);
    }

    @Test
    void textWithoutLettersOrDigitsGivesEmptySlug() {
        assertThat(Slugs.from("!!! ???")).isEmpty();
    }

    @Test
    void longTitlesAreCutWithoutATrailingHyphen() {
        String slug = Slugs.from("kelime ".repeat(40));

        assertThat(slug).hasSizeLessThanOrEqualTo(Slugs.MAX_LENGTH);
        assertThat(slug).doesNotEndWith("-");
        assertThat(slug).matches(Slugs.PATTERN);
    }
}
