package com.ofss.service;

import com.ofss.config.BusinessCalendarProperties;
import com.ofss.repository.BusinessHolidayRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class BusinessCalendarService {

    private final BusinessHolidayRepository
        businessHolidayRepository;

    private final BusinessCalendarProperties
        properties;

    public BusinessCalendarService(
            BusinessHolidayRepository businessHolidayRepository,
            BusinessCalendarProperties properties) {

        this.businessHolidayRepository =
            businessHolidayRepository;

        this.properties =
            properties;
    }

    private ZoneId zoneId() {

        return ZoneId.of(
            properties.getTimezone()
        );
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(
            LocalDate date) {

        return businessHolidayRepository
            .existsByHolidayDateAndActiveTrue(
                date
            );
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(
            LocalDate date) {

        DayOfWeek day =
            date.getDayOfWeek();

        if (
            day == DayOfWeek.SATURDAY ||
            day == DayOfWeek.SUNDAY
        ) {
            return false;
        }

        return !isHoliday(
            date
        );
    }

    @Transactional(readOnly = true)
    public boolean isWorkingTime(
            OffsetDateTime dateTime) {

        ZonedDateTime indiaTime =
            dateTime.atZoneSameInstant(
                zoneId()
            );

        LocalDate date =
            indiaTime.toLocalDate();

        LocalTime time =
            indiaTime.toLocalTime();

        if (!isWorkingDay(date)) {
            return false;
        }

        LocalTime start =
            properties.getWorkingDayStart();

        LocalTime end =
            properties.getWorkingDayEnd();

        return
            !time.isBefore(start) &&
            time.isBefore(end);
    }

    /**
     * Calculates the approval SLA deadline using:
     *
     * Monday-Friday
     * 09:00-17:00 IST
     *
     * Weekends and configured holidays
     * consume zero SLA time.
     */
    @Transactional(readOnly = true)
    public OffsetDateTime calculateDueAt(
            OffsetDateTime assignedAt,
            long slaMinutes) {

        if (assignedAt == null) {
            throw new IllegalArgumentException(
                "assignedAt is required"
            );
        }

        if (slaMinutes <= 0) {
            throw new IllegalArgumentException(
                "slaMinutes must be greater than zero"
            );
        }

        ZonedDateTime cursor =
            assignedAt.atZoneSameInstant(
                zoneId()
            );

        cursor =
            normalizeToWorkingTime(
                cursor
            );

        long remainingMinutes =
            slaMinutes;

        while (remainingMinutes > 0) {

            cursor =
                normalizeToWorkingTime(
                    cursor
                );

            LocalDate currentDate =
                cursor.toLocalDate();

            ZonedDateTime endOfWorkingDay =
                ZonedDateTime.of(
                    currentDate,
                    properties.getWorkingDayEnd(),
                    zoneId()
                );

            long availableMinutesToday =
                Duration.between(
                    cursor,
                    endOfWorkingDay
                ).toMinutes();

            /*
             * Deadline falls within the current
             * working day.
             */
            if (
                remainingMinutes <=
                availableMinutesToday
            ) {

                cursor =
                    cursor.plusMinutes(
                        remainingMinutes
                    );

                remainingMinutes = 0;

                break;
            }

            /*
             * Consume today's remaining SLA time.
             */
            remainingMinutes -=
                availableMinutesToday;

            /*
             * Move to the next working day
             * at 09:00 IST.
             */
            cursor =
                nextWorkingDayStart(
                    currentDate.plusDays(1)
                );
        }

        return cursor.toOffsetDateTime();
    }

    @Transactional(readOnly = true)
    public ZonedDateTime normalizeToWorkingTime(
            ZonedDateTime value) {

        ZonedDateTime indiaTime =
            value.withZoneSameInstant(
                zoneId()
            );

        LocalDate date =
            indiaTime.toLocalDate();

        LocalTime time =
            indiaTime.toLocalTime();

        /*
         * Weekend or configured holiday.
         */
        if (!isWorkingDay(date)) {

            return nextWorkingDayStart(
                date.plusDays(1)
            );
        }

        LocalTime start =
            properties.getWorkingDayStart();

        LocalTime end =
            properties.getWorkingDayEnd();

        /*
         * Before 09:00.
         */
        if (time.isBefore(start)) {

            return ZonedDateTime.of(
                date,
                start,
                zoneId()
            );
        }

        /*
         * 17:00 or later.
         */
        if (!time.isBefore(end)) {

            return nextWorkingDayStart(
                date.plusDays(1)
            );
        }

        return indiaTime;
    }

    @Transactional(readOnly = true)
    public ZonedDateTime nextWorkingDayStart(
            LocalDate startingDate) {

        LocalDate candidate =
            startingDate;

        int safetyCounter =
            0;

        while (
            !isWorkingDay(
                candidate
            )
        ) {

            candidate =
                candidate.plusDays(1);

            safetyCounter++;

            if (
                safetyCounter > 366
            ) {

                throw new IllegalStateException(
                    "Unable to find next working day."
                );
            }
        }

        return ZonedDateTime.of(
            candidate,
            properties.getWorkingDayStart(),
            zoneId()
        );
    }
}