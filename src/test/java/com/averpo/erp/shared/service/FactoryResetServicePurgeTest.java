package com.averpo.erp.shared.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Диск иловаларини тозалаш unit тести (DEC-072 / TST-051).
 *
 * <p>Нега алоҳида класс: {@link FactoryResetServiceTest} @Transactional -
 * {@code afterCommit} синхронизацияси у ерда ҲЕЧ ҚАЧОН ишламайди (тест
 * транзакцияси rollback бўлади, commit йўқ), яъни диск тозалаш интеграцион
 * тестда кўринмас эди. Шунга мантиқ {@code purgeDirectory} static методига
 * ажратилган ва бу ерда Spring'сиз, DB'сиз, {@code @TempDir} билан тўғридан
 * тўғри синалади - файллар ҳақиқатан ўчиши гаровланади.
 */
class FactoryResetServicePurgeTest {

    /** Ичма-ич файллар/ост-каталоглар ўчади, илдиз каталог ўзи қолади. */
    @Test
    void purgeDirectory_deletesFilesAndSubdirs_keepsRoot(@TempDir Path root) throws IOException {
        Path sub = Files.createDirectories(root.resolve("2026").resolve("07"));
        Path nested = Files.writeString(sub.resolve("hujjat.pdf"), "x");
        Path top = Files.writeString(root.resolve("rasm.png"), "y");

        FactoryResetService.purgeDirectory(root);

        assertThat(nested).doesNotExist();
        assertThat(top).doesNotExist();
        assertThat(sub).doesNotExist(); // ост-каталоглар ҳам кетади
        assertThat(root).isDirectory(); // илдиз кейинги иловалар учун қолади
    }

    /** Каталог умуман бўлмаса - хатосиз чиқади (ҳали илова юкланмаган ҳолат). */
    @Test
    void purgeDirectory_missingRoot_isNoop(@TempDir Path root) {
        FactoryResetService.purgeDirectory(root.resolve("yoq-katalog"));
        // истисно отилмагани ўзи тасдиқ - best-effort шартнома
        assertThat(root).isDirectory();
    }
}
