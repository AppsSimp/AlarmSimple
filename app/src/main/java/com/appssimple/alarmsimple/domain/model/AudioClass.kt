package com.appssimple.alarmsimple.domain.model

enum class AudioClass {
    SILENCE,
    AMBIENT,
    BREATHING,
    SNORE,        // Храп
    GASP,         // Резкий судорожный вдох (после паузы дыхания)
    FLATULENCE,   // Газы / бурчание
    COUGH,        // Кашель / чихание
    BRUXISM,      // Скрежет зубами
    RUSTLING,     // Шуршание простыней / ворочание
    SPEECH,       // Разговор
    IMPACT,       // Удар / хлопок
    NOISE,        // Прочий громкий звук
    UNKNOWN       // Неопознанный громкий/цикличный шум
}
