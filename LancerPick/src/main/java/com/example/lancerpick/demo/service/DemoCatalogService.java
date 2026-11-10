package com.example.lancerpick.demo.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.demo.dto.DemoCatalogResponse.Category;
import com.example.lancerpick.demo.dto.DemoCatalogResponse.Product;
import com.example.lancerpick.demo.dto.DemoCatalogResponse.Seller;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("(default | demo) & !prod")
public class DemoCatalogService {
    private final List<Category> categories = List.of(
            new Category("1", "디자인", 1), new Category("2", "개발", 2),
            new Category("3", "영상", 3), new Category("4", "마케팅", 4),
            new Category("5", "번역", 5), new Category("6", "문서", 6));
    private final List<Product> products = List.of(
            product("108", "브랜드의 첫인상을 만드는 로고 디자인", 150000, 5, 0, "스튜디오 모노"),
            product("107", "작은 화면까지 편안한 반응형 웹사이트", 350000, 10, 1, "코드앤코"),
            product("106", "핵심을 선명하게 전하는 브랜드 영상", 240000, 7, 2, "프레임웍스"),
            product("105", "브랜드에 어울리는 SNS 콘텐츠 기획", 80000, 3, 3, "페이지랩"),
            product("104", "자연스럽게 읽히는 비즈니스 영문 번역", 50000, 2, 4, "언어의 결"),
            product("103", "내용이 돋보이는 프레젠테이션 디자인", 120000, 4, 5, "슬라이드룸"),
            product("102", "제품의 매력을 담은 상세페이지 디자인", 180000, 5, 0, "오브젝트"),
            product("101", "서비스 소개를 위한 랜딩 페이지 개발", 280000, 7, 1, "웹메이트"));

    public List<Category> categories() { return categories; }

    public PageResponse<Product> products(String keyword, String categoryId, int page, int size) {
        String query = TextNormalizer.normalize(keyword).toLowerCase(Locale.ROOT);
        if (query.codePointCount(0, query.length()) > 100 || !PageResponse.isValidRequest(page, size)
                || (categoryId != null && !categoryId.matches("[1-9][0-9]{0,18}"))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "검색 조건과 페이지 범위를 확인해 주세요.");
        }
        // 예제는 불변 메모리 데이터다. 실제 상품 담당자는 DB 조회·설명 검색·정규화를 구현한다.
        var filtered = products.stream()
                .filter(p -> categoryId == null || p.category().id().equals(categoryId))
                .filter(p -> p.title().toLowerCase(Locale.ROOT).contains(query)).toList();
        long offset = (long) page * size;
        var items = offset >= filtered.size() ? List.<Product>of()
                : filtered.subList((int) offset, (int) Math.min(offset + size, filtered.size()));
        return new PageResponse<>(items, page, size, filtered.size(), (filtered.size() + size - 1) / size);
    }

    private Product product(String id, String title, long price, int days, int categoryIndex, String seller) {
        return new Product(id, title, price, days, categories.get(categoryIndex), new Seller("9" + id, seller));
    }
}
