package com.ofss.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "BUSINESS_HOLIDAY")
public class BusinessHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HOLIDAY_ID")
    private Long holidayId;

    @Column(
        name = "HOLIDAY_DATE",
        nullable = false,
        unique = true
    )
    private LocalDate holidayDate;

    @Column(
        name = "HOLIDAY_NAME",
        nullable = false,
        length = 200
    )
    private String holidayName;

    @Column(
        name = "COUNTRY_CODE",
        nullable = false,
        length = 10
    )
    private String countryCode = "IN";

    @Column(
        name = "ACTIVE",
        nullable = false
    )
    private boolean active = true;


    public Long getHolidayId() {
        return holidayId;
    }

    public void setHolidayId(
            Long holidayId) {

        this.holidayId =
            holidayId;
    }


    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(
            LocalDate holidayDate) {

        this.holidayDate =
            holidayDate;
    }


    public String getHolidayName() {
        return holidayName;
    }

    public void setHolidayName(
            String holidayName) {

        this.holidayName =
            holidayName;
    }


    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(
            String countryCode) {

        this.countryCode =
            countryCode;
    }


    public boolean isActive() {
        return active;
    }

    public void setActive(
            boolean active) {

        this.active =
            active;
    }
}