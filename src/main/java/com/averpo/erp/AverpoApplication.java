package com.averpo.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AVERPO кириш нуқтаси. Scheduling - ЦБ курс импорти учун (4-босқич).
 *
 * @author Zafar
 */
@SpringBootApplication
@EnableScheduling
public class AverpoApplication {

    /** Иловани ишга туширади. */
    public static void main(String[] args) {
        SpringApplication.run(AverpoApplication.class, args);
    }
}
