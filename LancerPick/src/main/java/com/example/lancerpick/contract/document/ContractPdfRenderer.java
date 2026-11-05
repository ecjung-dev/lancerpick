package com.example.lancerpick.contract.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Text-only rendering: never loads external URLs, HTML, scripts or caller-selected fonts. */
@Component
public class ContractPdfRenderer {
  private final JsonMapper mapper;

  public ContractPdfRenderer(JsonMapper mapper) {
    this.mapper = mapper;
  }

  public byte[] render(long contractId, int version, String canonical) throws IOException {
    if (canonical == null || canonical.length() > 250_000)
      throw new IOException("Contract too large");
    try (var document = new PDDocument();
        var fontStream = new ClassPathResource("fonts/NanumGothic-Regular.ttf").getInputStream();
        var output = new ByteArrayOutputStream()) {
      var font = PDType0Font.load(document, fontStream, true);
      try (var writer = new Writer(document, font)) {
        writer.text("랜서픽 전자계약서", 18);
        writer.text("계약 " + contractId + " / 버전 " + version);
        writer.text("표시할 수 없는 문자는 [U+코드]로 표기합니다. 원문은 계약 조회에서 확인하세요.");
        writer.text("");
        Map<String, Object> values =
            mapper.readValue(canonical, new TypeReference<TreeMap<String, Object>>() {});
        var displayed = new LinkedHashMap<String, Object>();
        for (String key :
            List.of(
                "orderId",
                "templateVersion",
                "buyerId",
                "buyerName",
                "sellerId",
                "sellerName",
                "productTitle",
                "total",
                "deposit",
                "balance",
                "durationDays",
                "description",
                "scope",
                "preparation",
                "originalTerms",
                "requirements",
                "standardBody",
                "specialTerms")) {
          if (values.containsKey(key)) displayed.put(key, values.get(key));
        }
        values.forEach(displayed::putIfAbsent);
        for (var entry : displayed.entrySet()) {
          writer.heading(label(entry.getKey()));
          writer.text(
              "fundingMode".equals(entry.getKey()) && "FULL_HOLD".equals(entry.getValue())
                  ? "전액 보관"
                  : String.valueOf(entry.getValue()));
          writer.text("");
        }
      }
      document.save(output);
      if (output.size() > PrivateContractStorage.MAX_BYTES) throw new IOException("PDF too large");
      return output.toByteArray();
    }
  }

  private static String label(String key) {
    return switch (key) {
      case "standardBody" -> "표준 계약 본문";
      case "specialTerms" -> "특약";
      case "requirements" -> "구매자 요구사항";
      case "scope" -> "작업 범위";
      case "description" -> "서비스 설명";
      case "preparation" -> "준비사항";
      case "productTitle" -> "서비스명";
      case "originalTerms" -> "신청 당시 보관 조건";
      case "buyerName" -> "구매자 표시 이름";
      case "sellerName" -> "판매자 표시 이름";
      case "buyerId" -> "구매자 회원번호";
      case "sellerId" -> "판매자 회원번호";
      case "total" -> "총금액(원)";
      case "policyVersion" -> "거래 정책 버전";
      case "fundingMode" -> "대금 처리 방식";
      case "feeRateBps" -> "정상 수수료(bps, 1000=10%)";
      case "deposit" -> "선금(원)";
      case "balance" -> "잔금(원)";
      case "durationDays" -> "작업 소요일(1일=24시간)";
      case "orderId" -> "주문번호";
      case "templateVersion" -> "표준 양식 버전";
      default -> key;
    };
  }

  private static final class Writer implements AutoCloseable {
    private final PDDocument document;
    private final PDType0Font font;
    private PDPageContentStream stream;
    private float y;
    private int pages;

    Writer(PDDocument d, PDType0Font f) throws IOException {
      document = d;
      font = f;
      page();
    }

    private void page() throws IOException {
      if (++pages > 150) throw new IOException("Too many pages");
      if (stream != null) stream.close();
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      y = PDRectangle.A4.getHeight() - 48;
      stream.beginText();
      stream.setFont(font, 8);
      stream.newLineAtOffset(48, 25);
      stream.showText("랜서픽 / " + pages);
      stream.endText();
    }

    void text(String raw) throws IOException {
      text(raw, 10);
    }

    void heading(String text) throws IOException {
      if (y < 96) page();
      text("[" + text + "]", 12);
    }

    void text(String raw, float fontSize) throws IOException {
      for (String paragraph : raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
        var line = new StringBuilder();
        float width = 0;
        for (int cp : paragraph.codePoints().toArray()) {
          String s = cp == '\t' ? "    " : new String(Character.toChars(cp));
          try {
            if ((cp != '\t' && Character.isISOControl(cp))
                || Character.getType(cp) == Character.FORMAT) throw new IllegalArgumentException();
            font.encode(s);
          } catch (IllegalArgumentException e) {
            s = "[U+" + Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT) + "]";
          }
          float next = font.getStringWidth(s) * fontSize / 1000;
          if (width + next > PDRectangle.A4.getWidth() - 96 && !line.isEmpty()) {
            line(line.toString(), fontSize);
            line.setLength(0);
            width = 0;
          }
          line.append(s);
          width += next;
        }
        line(line.toString(), fontSize);
      }
    }

    private void line(String value, float fontSize) throws IOException {
      if (y < 48) page();
      stream.beginText();
      stream.setFont(font, fontSize);
      stream.newLineAtOffset(48, y);
      stream.showText(value);
      stream.endText();
      y -= Math.max(16, fontSize * 1.5f);
    }

    public void close() throws IOException {
      if (stream != null) stream.close();
    }
  }
}
