package com.pokade.domain.card.init;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.PokedexKoNameJdbcRepository;
import com.pokade.domain.card.repository.PokedexKoNameRepository;
import com.pokade.domain.card.support.KoreanTextUtil;
import com.pokade.domain.card.support.PokedexKoNameCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// PokeAPI 원본 도감번호-한글명 데이터를 서버 기동 시 1회 적재한다. dev/prod 공통으로 필요한 실데이터라 프로파일 제한을 두지 않는다.
@Component
@RequiredArgsConstructor
@Slf4j
public class PokedexKoNameInitializer implements ApplicationRunner {

    private static final String CSV_PATH = "pokedex/pokedex_ko_names.csv";

    private final PokedexKoNameRepository pokedexKoNameRepository;
    private final PokedexKoNameJdbcRepository pokedexKoNameJdbcRepository;
    private final PokedexKoNameCache pokedexKoNameCache;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ParseResult result = readCsv();
        // count() > 0만 보면 이전 실행이 CSV 중간까지만 적재하고 죽었을 때도 "완료"로 오판해
        // 나머지가 영영 채워지지 않는다. CSV의 실제 정상 건수와 비교해야 부분 적재 상태를 구분할 수 있다.
        if (pokedexKoNameRepository.count() < result.pokedexKoNames().size()) {
            pokedexKoNameJdbcRepository.batchUpsert(result.pokedexKoNames());
            log.info("도감 한글명 적재 완료 - 정상 {}건, 스킵 {}건", result.pokedexKoNames().size(), result.skippedCount());
        }

        // 별도 ApplicationRunner의 @Order에 의존하지 않고, DB 적재(또는 스킵) 확인 직후
        // 이 자리에서 직접 캐시를 채워 순서를 보장한다.
        pokedexKoNameCache.reload();
    }

    private ParseResult readCsv() throws IOException {
        List<PokedexKoName> pokedexKoNames = new ArrayList<>();
        int skippedCount = 0;
        ClassPathResource resource = new ClassPathResource(CSV_PATH);

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // 헤더(pokedex_number,name_en,name_ko) 스킵

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", 3);
                if (columns.length < 3) {
                    log.warn("도감 한글명 CSV 파싱 스킵 - 콤마가 없는 잘못된 형식의 줄: \"{}\"", line);
                    skippedCount++;
                    continue;
                }

                Integer pokedexNumber;
                try {
                    pokedexNumber = Integer.valueOf(columns[0].trim());
                } catch (NumberFormatException e) {
                    log.warn("도감 한글명 CSV 파싱 스킵 - 도감번호가 숫자가 아님: \"{}\"", line);
                    skippedCount++;
                    continue;
                }
                String nameEn = columns[1].trim();
                String nameKo = columns[2].trim();

                pokedexKoNames.add(PokedexKoName.builder()
                        .pokedexNumber(pokedexNumber)
                        .nameEn(nameEn)
                        .nameKo(nameKo)
                        .nameKoChosung(KoreanTextUtil.extractChosung(nameKo))
                        .build());
            }
        }

        return new ParseResult(pokedexKoNames, skippedCount);
    }

    private record ParseResult(List<PokedexKoName> pokedexKoNames, int skippedCount) {
    }
}
