package com.pokade.domain.inquiry.repository;

import com.pokade.domain.inquiry.entity.InquiryImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryImageRepository extends JpaRepository<InquiryImage, Long> {

    List<InquiryImage> findByInquiryIdOrderByIdAsc(Long inquiryId);

    List<InquiryImage> findByInquiryIdInOrderByIdAsc(List<Long> inquiryIds);
}
