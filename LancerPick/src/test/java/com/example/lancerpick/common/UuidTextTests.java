package com.example.lancerpick.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lancerpick.common.text.UuidText;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidTextTests {

  @Test
  void parsesCanonicalTextAndNormalizesToLowercase() {
    var parsed = UuidText.parse("3F2504E0-4F89-11D3-9A0C-0305E82C3301");

    assertThat(parsed).contains(UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
    assertThat(parsed.orElseThrow().toString()).isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
  }

  @Test
  void rejectsValuesThatUuidFromStringWouldAccept() {
    assertThat(UUID.fromString("1-1-1-1-1")).isNotNull();
    assertThat(UuidText.parse("1-1-1-1-1")).isEmpty();
  }

  @Test
  void rejectsNullBlankAndMalformedValues() {
    assertThat(UuidText.parse(null)).isEmpty();
    assertThat(UuidText.parse("")).isEmpty();
    assertThat(UuidText.parse(" 3f2504e0-4f89-11d3-9a0c-0305e82c3301")).isEmpty();
    assertThat(UuidText.parse("3f2504e04f8911d39a0c0305e82c3301")).isEmpty();
    assertThat(UuidText.parse("3f2504e0-4f89-11d3-9a0c-0305e82c330g")).isEmpty();
    assertThat(UuidText.parse("{3f2504e0-4f89-11d3-9a0c-0305e82c3301}")).isEmpty();
  }
}
