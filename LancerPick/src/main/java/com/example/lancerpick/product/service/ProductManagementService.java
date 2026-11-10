package com.example.lancerpick.product.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import com.example.lancerpick.order.service.NewOrderPolicy;
import com.example.lancerpick.product.dto.*;
import com.example.lancerpick.product.entity.Category;
import com.example.lancerpick.product.entity.Product;
import com.example.lancerpick.seller.entity.SellerProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.JsonNode;

@Service
@Profile("!demo")
@Transactional(readOnly = true)
public class ProductManagementService {
    private final EntityManager em;
    private final Clock clock;
    private final MemberSanctionQuery sanctions;
    private final NewOrderPolicy newOrderPolicy;
    public ProductManagementService(EntityManager em, Clock clock, MemberSanctionQuery sanctions,
            NewOrderPolicy newOrderPolicy) {
        this.em = em;
        this.clock = clock;
        this.sanctions = sanctions;
        this.newOrderPolicy = newOrderPolicy;
    }

    public OwnedProductResponse getMine(long memberId, String id) { return response(owned(memberId, id, false)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedProductResponse create(long memberId, JsonNode body) {
        var input = ProductWriteInput.parse(body, null);
        // 등록은 회원→상품 순서를 지키며 프로필과 회원 존재를 재확인한다.
        if (em.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        if (sanctions.restriction(memberId).blocksNewActivity())
            throw new BusinessException(HttpStatus.FORBIDDEN, "MEMBER_RESTRICTED", "이용 제한 중에는 새 상품을 등록할 수 없습니다.");
        if (em.find(SellerProfile.class, memberId) == null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELLER_PROFILE_REQUIRED", "판매자 프로필을 먼저 등록해 주세요.");
        category(input.categoryId());
        requireOrderablePrice(input.priceAmount());
        Product product = Product.create(memberId, input, now());
        em.persist(product); em.flush();
        return response(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedProductResponse patch(long memberId, String id, JsonNode body) {
        long expected = ProductWriteInput.version(body);
        Product product = owned(memberId, id, true);
        if (product.getVersion() != expected)
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT", "상품이 변경되었습니다. 최신 내용을 확인해 주세요.");
        var input = ProductWriteInput.parse(body, product);
        category(input.categoryId());
        if ("ON_SALE".equals(product.getSaleStatus())) requireOrderablePrice(input.priceAmount());
        product.revise(input, now()); em.flush();
        return response(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedProductResponse changeSaleStatus(long memberId, String id, JsonNode body) {
        var input = ChangeSaleStatusInput.from(body);
        Product product = owned(memberId, id, true);
        // 같은 상태를 요청해도 조회 버전이 현재 버전과 일치해야 한다.
        if (product.getVersion() != input.expectedVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT",
                    "상품이 변경되었습니다. 최신 내용을 확인해 주세요.");
        }
        if ("ON_SALE".equals(input.saleStatus())) requireOrderablePrice(product.getPrice());
        product.changeSaleStatus(input.saleStatus(), now());
        em.flush();
        return response(product);
    }

    private Product owned(long memberId, String id, boolean lock) {
        long parsed = ProductWriteInput.id(id);
        Product product = lock ? em.find(Product.class, parsed, LockModeType.PESSIMISTIC_WRITE) : em.find(Product.class, parsed);
        if (product == null || product.getSellerId() != memberId)
            throw new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다.");
        return product;
    }
    /**
     * 판매 중인 상품은 현재 신규 주문 정책으로 주문할 수 있는 가격이어야 한다(CONTRACT_V3는 10,000원 이상).
     * 판매 중지 상품은 가격을 고치기 전 상태로 수정할 수 있다. 하한은 생성 스위치를 따라 바뀌므로 DB CHECK가 아닌 여기서 검사한다.
     */
    private void requireOrderablePrice(long price) {
        Long minimum = NewOrderPolicy.minimumAmount(newOrderPolicy.current());
        if (minimum != null && price < minimum) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_PRICE_BELOW_ORDER_MINIMUM",
                    "판매 중인 상품 가격은 %,d원 이상이어야 합니다. 가격을 올리거나 판매를 중지해 주세요.".formatted(minimum));
        }
    }
    private Category category(long id) {
        Category category = em.find(Category.class, id);
        if (category == null) throw ProductWriteInput.invalid();
        return category;
    }
    private LocalDateTime now() { return UtcTimes.nowMicros(clock); }
    private OwnedProductResponse response(Product product) {
        var category = category(product.getCategoryId());
        var member = em.find(Member.class, product.getSellerId());
        return new OwnedProductResponse(product.getId().toString(), product.getTitle(), product.getPrice(), product.getDurationDays(),
                new CategoryResponse(category.getId().toString(), category.getName(), category.getDisplayOrder()),
                new ProductSummaryResponse.Seller(member.getId().toString(), member.getDisplayName()), product.getDescription(),
                product.getScopeDescription(), product.getPreparationDescription(), product.getVersion(), product.getSaleStatus(),
                product.isHidden(), product.getCreatedAt().atOffset(ZoneOffset.UTC), product.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
