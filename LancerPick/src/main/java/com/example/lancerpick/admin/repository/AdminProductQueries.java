package com.example.lancerpick.admin.repository;
import com.example.lancerpick.admin.dto.*;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Category;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Seller;
import com.example.lancerpick.admin.entity.*;
import com.example.lancerpick.product.entity.*;
import com.example.lancerpick.member.entity.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
@Repository @Profile("!demo")
public class AdminProductQueries {
    private final EntityManager em;private final JPAQueryFactory query;
    public AdminProductQueries(EntityManager em){this.em=em;query=new JPAQueryFactory(em);}
    public record ProductFilter(String keyword,Boolean hidden,String saleStatus){}
    public record ProductVisibility(boolean hidden,long version){}
    private static final QProduct p=QProduct.product;
    private static final QCategory c=QCategory.category;
    private static final QMember m=QMember.member;
    private BooleanBuilder where(ProductFilter f){var b=new BooleanBuilder();if(f.keyword()!=null){var pattern="%"+escapeLike(f.keyword())+"%";b.and(p.title.like(pattern,'!').or(p.description.like(pattern,'!')));}if(f.hidden()!=null)b.and(p.hidden.eq(f.hidden()));if(f.saleStatus()!=null)b.and(p.saleStatus.eq(f.saleStatus()));return b;}
    public long count(ProductFilter f){return query.select(p.id.count()).from(p).join(c).on(c.id.eq(p.categoryId)).join(m).on(m.id.eq(p.sellerId)).where(where(f)).fetchOne();}
    public List<AdminProductSummaryResponse> find(ProductFilter f,int size,long offset){if(offset>=count(f))return List.of();return query.select(p,c,m).from(p).join(c).on(c.id.eq(p.categoryId)).join(m).on(m.id.eq(p.sellerId)).where(where(f)).orderBy(p.createdAt.desc(),p.id.desc()).limit(size).offset(offset).fetch().stream().map(t->{var v=t.get(p);return new AdminProductSummaryResponse(v.getId().toString(),v.getTitle(),v.getPrice(),v.getDurationDays(),category(t),seller(t),v.getSaleStatus(),v.isHidden(),v.getVersion(),at(v.getCreatedAt()),at(v.getUpdatedAt()));}).toList();}
    static String escapeLike(String keyword){return keyword.replace("!","!!").replace("%","!%").replace("_","!_");}
    public Optional<AdminProductDetailResponse> findDetail(long id){var t=query.select(p,c,m).from(p).join(c).on(c.id.eq(p.categoryId)).join(m).on(m.id.eq(p.sellerId)).where(p.id.eq(id)).fetchOne();return Optional.ofNullable(t).map(row->{var v=row.get(p);return new AdminProductDetailResponse(v.getId().toString(),v.getTitle(),v.getPrice(),v.getDurationDays(),category(row),seller(row),v.getDescription(),v.getScopeDescription(),v.getPreparationDescription(),v.getVersion(),v.getSaleStatus(),v.isHidden(),at(v.getCreatedAt()),at(v.getUpdatedAt()));});}
    public boolean exists(long id){return query.select(p.id).from(p).where(p.id.eq(id)).fetchFirst()!=null;}
    public Optional<ProductVisibility> lockVisibility(long id){return Optional.ofNullable(query.selectFrom(p).where(p.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne()).map(v->new ProductVisibility(v.isHidden(),v.getVersion()));}
    public int updateVisibility(long id,boolean hidden,long version,LocalDateTime at){var product=em.find(Product.class,id,LockModeType.PESSIMISTIC_WRITE);if(product==null || !product.moderate(hidden,version,at))return 0;em.flush();return 1;}
    public void insertModerationLog(long product,long admin,String action,String reason,LocalDateTime at){em.persist(ProductModerationLog.create(product,admin,action,reason,at));em.flush();}
    public long countLogs(long id){var l=QProductModerationLog.productModerationLog;return query.select(l.id.count()).from(l).where(l.productId.eq(id)).fetchOne();}
    public List<ModerationLogResponse> findLogs(long id,int size,long offset){if(offset>=countLogs(id))return List.of();var l=QProductModerationLog.productModerationLog;return query.selectFrom(l).where(l.productId.eq(id)).orderBy(l.createdAt.desc(),l.id.desc()).limit(size).offset(offset).fetch().stream().map(v->new ModerationLogResponse(v.getId().toString(),v.getAdminId().toString(),v.getAction(),v.getReason(),at(v.getCreatedAt()))).toList();}
    public Optional<String> findMemberRole(long id){return Optional.ofNullable(query.select(m.role).from(m).where(m.id.eq(id)).fetchOne()).map(Enum::name);}
    private static Category category(Tuple row){var v=row.get(c);return new Category(v.getId().toString(),v.getName());}
    private static Seller seller(Tuple row){var v=row.get(m);return new Seller(v.getId().toString(),v.getDisplayName());}
    private static OffsetDateTime at(LocalDateTime time){return time==null?null:time.atOffset(ZoneOffset.UTC);}
}
