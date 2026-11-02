package com.example.lancerpick.member.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 회원 기능을 만들지 않는다 (지갑 서비스와 동일 기준)
public class MemberService {
    /**
     * 이메일 형식 (API 10절). 소문자화 후 검사한다.
     * - 로컬 파트: 영문·숫자·허용 특수문자, 점은 맨 앞·맨 뒤·연속 불가, 최대 64자
     * - 도메인: 점으로 구분된 라벨 2개 이상, 라벨은 영문·숫자·하이픈(앞뒤 하이픈 불가) 1~63자, 마지막은 영문 2자 이상
     * 예) review@domain..com, .a@b.com, a@-b.com, a@b → 거절
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=[^@]{1,64}@)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    /** V1__init.sql의 members 이메일 UNIQUE 제약 이름 */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_members_email";

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

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        Member member = Member.create(email, passwordEncoder.encode(request.password()), displayName, now);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 DB UNIQUE(uk_members_email)가 막는다 → 409로 변환.
            // 그 밖의 제약 위반은 이메일 중복이 아니므로 원래 예외를 그대로 전파한다(공통 처리에서 500).
            // 실패한 트랜잭션 안에서 재조회하지 않는다.
            if (isEmailUniqueViolation(e)) {
                throw emailAlreadyExists();
            }
            throw e;
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

    /** 예외 원인 사슬에서 이메일 UNIQUE 제약 위반인지 확인한다. 이름이 확인되지 않으면 false. */
    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && containsEmailConstraint(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLIntegrityConstraintViolationException sql
                    && containsEmailConstraint(sql.getMessage())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static boolean containsEmailConstraint(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT);
    }

    private BusinessException invalidInput() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }

    private BusinessException emailAlreadyExists() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
    }
}
