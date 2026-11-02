package com.example.lancerpick.member.repository;

import com.example.lancerpick.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 회원가입 시 이메일 중복 확인 */
    boolean existsByEmail(String email);

    /** 로그인 시 이메일로 회원 찾기 */
    Optional<Member> findByEmail(String email);
}
