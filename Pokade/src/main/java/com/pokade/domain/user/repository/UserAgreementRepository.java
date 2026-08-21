package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.UserAgreement;
import com.pokade.domain.user.entity.type.AgreementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

    Optional<UserAgreement> findFirstByUserIdAndTypeOrderByAgreedAtDescIdDesc(Long userId, AgreementType type);
}
