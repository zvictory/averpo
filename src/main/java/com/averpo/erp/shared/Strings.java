package com.averpo.erp.shared;

/**
 * Матн ёрдамчилари - энг кўп такрорланган майда амаллар битта жойда
 * (Beruniy-backlog5: blankToNull 11 та файлда айнан бир хил нусхада
 * турарди, ҳар бирини алоҳида кузатиш керак эди).
 */
public final class Strings {

    /** Utility класс - instance ясалмайди. */
    private Strings() { }

    /**
     * Бўш/пробел-фақат сатрни null'га айлантиради, акс ҳолда strip
     * қилиб қайтаради - форма майдонлари ихтиёрий бўлганда «бўш сатр»
     * билан «йўқ» бир хил маънога келади (базада null сақланади).
     */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
