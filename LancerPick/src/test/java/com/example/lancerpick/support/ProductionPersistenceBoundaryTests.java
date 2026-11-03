package com.example.lancerpick.support;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 테스트 fixture SQL·Flyway는 허용하고, 운영 코드의 직접 JDBC/네이티브 SQL 재도입을 막는다. */
class ProductionPersistenceBoundaryTests {
    @Test
    void productionPersistenceUsesJpaRatherThanDirectJdbcOrNativeQueries() throws Exception {
        var forbidden=Pattern.compile("import\\s+org\\.springframework\\.jdbc\\.|nativeQuery\\s*=\\s*true|createNativeQuery\\s*\\(|DriverManager\\.getConnection\\s*\\(");
        var violations=new ArrayList<String>();
        try(var files=Files.walk(Path.of("src/main/java"))){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                if(forbidden.matcher(Files.readString(file)).find())violations.add(file.toString());
            }
        }
        assertThat(violations).isEmpty();
    }
}
