package com.ofss.config;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "business-calendar")
public class BusinessCalendarProperties {

    private String timezone = "Asia/Kolkata";

    private LocalTime workingDayStart =
        LocalTime.of(9, 0);

    private LocalTime workingDayEnd =
        LocalTime.of(17, 0);

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(
            String timezone) {

        this.timezone = timezone;
    }

    public LocalTime getWorkingDayStart() {
        return workingDayStart;
    }

    public void setWorkingDayStart(
            LocalTime workingDayStart) {

        this.workingDayStart =
            workingDayStart;
    }

    public LocalTime getWorkingDayEnd() {
        return workingDayEnd;
    }

    public void setWorkingDayEnd(
            LocalTime workingDayEnd) {

        this.workingDayEnd =
            workingDayEnd;
    }
}