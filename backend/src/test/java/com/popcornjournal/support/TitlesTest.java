package com.popcornjournal.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitlesTest {

    @Test
    void keyKeepsOnlyLettersAndDigits() {
        assertThat(Titles.key("F.R.I.E.N.D.S")).isEqualTo("friends");
        assertThat(Titles.key("Salaar - Part 1 : Ceasefire")).isEqualTo("salaarpart1ceasefire");
        assertThat(Titles.key("Amélie")).isEqualTo("amelie");
        assertThat(Titles.key("Zee 5")).isEqualTo(Titles.key("Zee5"));
    }

    @Test
    void cleanCollapsesWhitespaceAndBlanksToNull() {
        assertThat(Titles.clean("  The   Office ")).isEqualTo("The Office");
        assertThat(Titles.clean("   ")).isNull();
        assertThat(Titles.clean(null)).isNull();
    }

    @Test
    void genreCategoryIsTheFirstPart() {
        assertThat(Titles.genreCategory("Thriller - Crime")).isEqualTo("Thriller");
        assertThat(Titles.genreCategory("Crime / Investigation")).isEqualTo("Crime");
        assertThat(Titles.genreCategory("Feel Good")).isEqualTo("Feel Good");
    }
}
