package com.pokade.domain.inquiry.repository;

import com.pokade.domain.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Inquiry> findAllByOrderByCreatedAtDesc();
}
