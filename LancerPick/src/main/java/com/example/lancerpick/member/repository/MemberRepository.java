package com.example.lancerpick.member.repository;

import com.example.lancerpick.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
    @Query(value = "select count(s) from SellerProfile s where s.memberId=:memberId")
    Long countSellerProfiles(@Param("memberId") Long memberId);

    /** 세션 폐기 확인용. 회원 엔티티 전체가 아니라 auth_version 한 값만 읽는다(잠금 없음). */
    @Query("select m.authVersion from Member m where m.id = :memberId")
    Optional<Long> findAuthVersionById(@Param("memberId") Long memberId);

    /**
     * 비밀번호 변경용 행 잠금(SELECT ... FOR UPDATE). 같은 회원의 비밀번호 변경이 동시에 들어오면
     * 하나씩 처리되어, 늦게 온 요청은 앞 요청이 올린 auth_version을 보고 거절된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
