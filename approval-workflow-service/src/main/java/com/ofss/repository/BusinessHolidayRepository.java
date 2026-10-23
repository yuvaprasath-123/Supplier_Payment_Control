package com.ofss.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.entity.BusinessHoliday;

import java.time.LocalDate;
import java.util.Optional;

public interface BusinessHolidayRepository
        extends JpaRepository<BusinessHoliday, Long> {

    boolean existsByHolidayDateAndActiveTrue(
        LocalDate holidayDate
    );

    Optional<BusinessHoliday>
        findByHolidayDateAndActiveTrue(
            LocalDate holidayDate
        );
}