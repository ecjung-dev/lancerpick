package com.example.lancerpick.member.repository;

import com.example.lancerpick.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 회원가입 시 이메일 중복 확인 */
    boolean existsByEmail(String email);

    /** 로그인 시 이메일로 회원 찾기 */
    Optional<Member> findByEmail(String email);

    /**
     * Member 응답의 hasSellerProfile 계산용. seller_profiles는 판매자 담당(박소현) 테이블이라
     * 엔티티를 중복 정의하지 않고 개수만 읽는다. 판매자 Repository가 생기면 그쪽 조회로 바꿀 수 있다.
     */
    @Query(value = "SELECT COUNT(*) FROM seller_profiles WHERE member_id = :memberId", nativeQuery = true)
    Long countSellerProfiles(@Param("memberId") Long memberId);
}
