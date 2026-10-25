package com.ofss.service;

import com.ofss.config.BusinessCalendarProperties;
import com.ofss.repository.BusinessHolidayRepository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

@Service
public class BusinessCalendarService {

    private final BusinessHolidayRepository holidayRepository;
    private final BusinessCalendarProperties properties;

    public BusinessCalendarService(
            BusinessHolidayRepository holidayRepository,
            BusinessCalendarProperties properties
    ) {
        this.holidayRepository =
                holidayRepository;

        this.properties =
                properties;
    }


    public ZoneId zoneId() {

        return ZoneId.of(
                properties.getTimezone()
        );
    }


    public boolean isHoliday(
            LocalDate date
    ) {

        return holidayRepository
                .existsByHolidayDateAndActiveTrue(
                        date
                );
    }


    public boolean isWorkingDay(
            LocalDate date
    ) {

        if (date == null) {
            return false;
        }


        DayOfWeek day =
                date.getDayOfWeek();


        if (
                day == DayOfWeek.SATURDAY
                ||
                day == DayOfWeek.SUNDAY
        ) {

            return false;
        }


        return !isHoliday(
                date
        );
    }


    public boolean isWorkingTime(
            OffsetDateTime dateTime
    ) {

        if (dateTime == null) {
            return false;
        }


        ZonedDateTime local =
                dateTime
                        .atZoneSameInstant(
                                zoneId()
                        );


        if (
                !isWorkingDay(
                        local.toLocalDate()
                )
        ) {

            return false;
        }


        LocalTime time =
                local.toLocalTime();


        return
                !time.isBefore(
                        properties
                                .getWorkingDayStart()
                )
                &&
                time.isBefore(
                        properties
                                .getWorkingDayEnd()
                );
    }


    /*
     * =========================================================
     * NORMAL BUSINESS-CALENDAR SLA
     * =========================================================
     *
     * Example:
     *
     * Saturday + 8 working hours
     * -> Monday 17:00
     *
     * Saturday + 24 working hours
     * -> Wednesday 17:00
     */
    public OffsetDateTime calculateDueAt(
            OffsetDateTime assignedAt,
            long slaMinutes
    ) {

        if (assignedAt == null) {

            throw new IllegalArgumentException(
                    "Assigned time is required"
            );
        }


        if (slaMinutes <= 0) {

            throw new IllegalArgumentException(
                    "SLA minutes must be greater than zero"
            );
        }


        ZonedDateTime current =
                normalizeToWorkingTime(
                        assignedAt
                                .atZoneSameInstant(
                                        zoneId()
                                )
                );


        long remainingMinutes =
                slaMinutes;


        while (remainingMinutes > 0) {

            LocalDate currentDate =
                    current.toLocalDate();


            ZonedDateTime workEnd =
                    currentDate
                            .atTime(
                                    properties
                                            .getWorkingDayEnd()
                            )
                            .atZone(
                                    zoneId()
                            );


            long availableMinutes =
                    Duration
                            .between(
                                    current,
                                    workEnd
                            )
                            .toMinutes();


            if (
                    remainingMinutes
                            <= availableMinutes
            ) {

                return current
                        .plusMinutes(
                                remainingMinutes
                        )
                        .toOffsetDateTime();
            }


            remainingMinutes -=
                    availableMinutes;


            current =
                    nextWorkingDayStart(
                            currentDate
                    );
        }


        return current
                .toOffsetDateTime();
    }


    /*
     * =========================================================
     * INVOICE DEADLINE
     * =========================================================
     *
     * Invoice due date is treated as ending
     * at the end of the configured working day.
     *
     * Example:
     *
     * Due date 27/10/2026
     * -> 27/10/2026 17:00 IST
     */
    public OffsetDateTime invoiceDeadline(
            LocalDate invoiceDueDate
    ) {

        if (invoiceDueDate == null) {

            return null;
        }


        return invoiceDueDate
                .atTime(
                        properties
                                .getWorkingDayEnd()
                )
                .atZone(
                        zoneId()
                )
                .toOffsetDateTime();
    }


    /*
     * =========================================================
     * EFFECTIVE SLA DEADLINE
     * =========================================================
     *
     * IMPORTANT:
     *
     * This method DOES NOT change the approval level.
     *
     * L1 remains L1.
     * L2 remains L2.
     * L3 remains L3.
     *
     * It only shortens the available SLA when the
     * invoice due date is earlier than the normal
     * business-calendar SLA.
     */
    public OffsetDateTime calculateEffectiveDueAt(
            OffsetDateTime assignedAt,
            long slaMinutes,
            LocalDate invoiceDueDate
    ) {

        OffsetDateTime normalSlaDeadline =
                calculateDueAt(
                        assignedAt,
                        slaMinutes
                );


        if (invoiceDueDate == null) {

            return normalSlaDeadline;
        }


        OffsetDateTime invoiceDeadline =
                invoiceDeadline(
                        invoiceDueDate
                );


        if (
                invoiceDeadline.isBefore(
                        normalSlaDeadline
                )
        ) {

            return invoiceDeadline;
        }


        return normalSlaDeadline;
    }


    /*
     * =========================================================
     * WORKING MINUTES BETWEEN TWO TIMES
     * =========================================================
     *
     * Counts ONLY:
     *
     * Monday-Friday
     * 09:00-17:00 IST
     *
     * Weekends and configured holidays = zero.
     */
    public long calculateRemainingWorkingMinutes(
            OffsetDateTime from,
            OffsetDateTime deadline
    ) {

        if (
                from == null
                ||
                deadline == null
                ||
                !from.isBefore(
                        deadline
                )
        ) {

            return 0;
        }


        ZoneId zone =
                zoneId();


        ZonedDateTime current =
                from.atZoneSameInstant(
                        zone
                );


        ZonedDateTime end =
                deadline.atZoneSameInstant(
                        zone
                );


        long totalMinutes =
                0;


        while (
                current.isBefore(
                        end
                )
        ) {

            LocalDate date =
                    current.toLocalDate();


            /*
             * Weekend / holiday:
             * consume zero SLA minutes.
             */
            if (
                    !isWorkingDay(
                            date
                    )
            ) {

                current =
                        nextWorkingDayStart(
                                date
                        );

                continue;
            }


            ZonedDateTime workStart =
                    date
                            .atTime(
                                    properties
                                            .getWorkingDayStart()
                            )
                            .atZone(
                                    zone
                            );


            ZonedDateTime workEnd =
                    date
                            .atTime(
                                    properties
                                            .getWorkingDayEnd()
                            )
                            .atZone(
                                    zone
                            );


            if (
                    current.isBefore(
                            workStart
                    )
            ) {

                current =
                        workStart;
            }


            if (
                    !current.isBefore(
                            workEnd
                    )
            ) {

                current =
                        nextWorkingDayStart(
                                date
                        );

                continue;
            }


            ZonedDateTime segmentEnd =
                    end.isBefore(
                            workEnd
                    )
                            ? end
                            : workEnd;


            if (
                    segmentEnd.isAfter(
                            current
                    )
            ) {

                totalMinutes +=
                        Duration
                                .between(
                                        current,
                                        segmentEnd
                                )
                                .toMinutes();
            }


            if (
                    !segmentEnd.isBefore(
                            end
                    )
            ) {

                break;
            }


            current =
                    nextWorkingDayStart(
                            date
                    );
        }


        return Math.max(
                totalMinutes,
                0
        );
    }


    /*
     * Convenience overload for "now".
     */
    public long calculateRemainingWorkingMinutes(
            OffsetDateTime deadline
    ) {

        return calculateRemainingWorkingMinutes(
                OffsetDateTime.now(),
                deadline
        );
    }


    /*
     * =========================================================
     * NORMALIZE TO WORKING TIME
     * =========================================================
     */
    private ZonedDateTime normalizeToWorkingTime(
            ZonedDateTime value
    ) {

        LocalDate date =
                value.toLocalDate();


        if (
                !isWorkingDay(
                        date
                )
        ) {

            return nextWorkingDayStart(
                    date
            );
        }


        LocalTime time =
                value.toLocalTime();


        if (
                time.isBefore(
                        properties
                                .getWorkingDayStart()
                )
        ) {

            return date
                    .atTime(
                            properties
                                    .getWorkingDayStart()
                    )
                    .atZone(
                            zoneId()
                    );
        }


        if (
                !time.isBefore(
                        properties
                                .getWorkingDayEnd()
                )
        ) {

            return nextWorkingDayStart(
                    date
            );
        }


        return value;
    }


    /*
     * =========================================================
     * NEXT WORKING DAY
     * =========================================================
     */
    private ZonedDateTime nextWorkingDayStart(
            LocalDate date
    ) {

        LocalDate next =
                date.plusDays(
                        1
                );


        while (
                !isWorkingDay(
                        next
                )
        ) {

            next =
                    next.plusDays(
                            1
                    );
        }


        return next
                .atTime(
                        properties
                                .getWorkingDayStart()
                )
                .atZone(
                        zoneId()
                );
    }
}