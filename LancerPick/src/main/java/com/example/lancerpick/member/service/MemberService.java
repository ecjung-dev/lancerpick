package com.example.lancerpick.member.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 회원 기능을 만들지 않는다 (지갑 서비스와 동일 기준)
public class MemberService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final WalletInitializationService walletInitializationService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, Clock clock,
            WalletInitializationService walletInitializationService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.walletInitializationService = walletInitializationService;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = normalizeDisplayName(request.displayName());

        if (memberRepository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        Member member = Member.create(email, passwordEncoder.encode(request.password()), displayName, now);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 DB UNIQUE가 막는다 → 409로 변환
            throw emailAlreadyExists();
        }

        // 지갑 생성 + 초기 적립금 100만원 (황준범 담당 서비스, 같은 트랜잭션).
        // try-catch로 감싸지 않는다: 지급이 실패하면 예외가 전파되어 회원 저장까지 함께 롤백된다.
        walletInitializationService.initializeForNewMember(member.getId());

        return MemberResponse.from(member, false);   // 방금 가입한 회원은 판매자 프로필이 없음
    }

    /** 공백 제거 → 소문자화 → 3~254자(코드 포인트) + 형식 검사 */
    private String normalizeEmail(String raw) {
        String email = TextNormalizer.normalize(raw).toLowerCase(Locale.ROOT);
        int length = email.codePointCount(0, email.length());
        if (length < 3 || length > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw invalidInput();
        }
        return email;
    }

    /** 공백 제거 → 1~30자(코드 포인트) */
    private String normalizeDisplayName(String raw) {
        String name = TextNormalizer.normalize(raw);
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > 30) {
            throw invalidInput();
        }
        return name;
    }

    private BusinessException invalidInput() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }

    private BusinessException emailAlreadyExists() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
    }
}
