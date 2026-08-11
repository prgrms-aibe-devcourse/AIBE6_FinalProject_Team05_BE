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
import com.pokade.domain.card.repository.PokedexKoNameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// PokeAPI 원본 도감번호-한글명 데이터를 서버 기동 시 1회 적재한다. dev/prod 공통으로 필요한 실데이터라 프로파일 제한을 두지 않는다.
@Component
@RequiredArgsConstructor
@Slf4j
public class PokedexKoNameInitializer implements ApplicationRunner {

    private static final String CSV_PATH = "pokedex/pokedex_ko_names.csv";

    private final PokedexKoNameRepository pokedexKoNameRepository;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (pokedexKoNameRepository.count() > 0) {
            return;
        }

        List<PokedexKoName> pokedexKoNames = readCsv();
        pokedexKoNameRepository.saveAll(pokedexKoNames);
        log.info("도감 한글명 {}건 적재 완료", pokedexKoNames.size());
    }

    private List<PokedexKoName> readCsv() throws IOException {
        List<PokedexKoName> pokedexKoNames = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(CSV_PATH);

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // 헤더(pokedex_number,name_ko) 스킵

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", 2);
                Integer pokedexNumber = Integer.valueOf(columns[0].trim());
                String nameKo = columns[1].trim();

                pokedexKoNames.add(PokedexKoName.builder()
                        .pokedexNumber(pokedexNumber)
                        .nameKo(nameKo)
                        .build());
            }
        }

        return pokedexKoNames;
    }
}
