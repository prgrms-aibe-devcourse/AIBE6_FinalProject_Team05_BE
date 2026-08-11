package com.pokade.domain.card.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.PokedexKoNameRepository;

import lombok.RequiredArgsConstructor;

/**
 * pokedex_ko_names(1025건, 고정 데이터)를 메모리에 캐싱한다. 요청마다 DB를 조회하지 않도록
 * 서버 기동 시 1회 전체 적재해두고 이후에는 이 캐시만 참조한다.
 * reload()는 이 클래스가 직접 호출 시점을 정하지 않는다 - PokedexKoNameInitializer가
 * CSV→DB 적재를 끝낸 뒤(또는 이미 적재돼 있음을 확인한 뒤) 호출해야 최신 데이터로 채워진다.
 */
@Component
@RequiredArgsConstructor
public class PokedexKoNameCache {

    private final PokedexKoNameRepository pokedexKoNameRepository;

    private volatile Map<Integer, PokedexKoName> cache = Map.of();

    public void reload() {
        List<PokedexKoName> all = pokedexKoNameRepository.findAll();
        Map<Integer, PokedexKoName> loaded = new ConcurrentHashMap<>();
        for (PokedexKoName item : all) {
            loaded.put(item.getPokedexNumber(), item);
        }
        this.cache = loaded;
    }

    public String getNameEn(Integer pokedexNumber) {
        PokedexKoName found = cache.get(pokedexNumber);
        return found != null ? found.getNameEn() : null;
    }

    public String getNameKo(Integer pokedexNumber) {
        PokedexKoName found = cache.get(pokedexNumber);
        return found != null ? found.getNameKo() : null;
    }
}
