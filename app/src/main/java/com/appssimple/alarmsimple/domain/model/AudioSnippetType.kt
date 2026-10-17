package com.appssimple.alarmsimple.domain.model

enum class AudioSnippetType {
    SNORE,           // Храп
    SLEEP_TALK,      // Разговоры / бормотание во сне
    COUGH_SNEEZE,    // Кашель / чихание
    BRUXISM,         // Скрежет зубами
    FLATULENCE,      // Метеоризм / звуки газов
    APNEA_GASP,      // Пауза в дыхании / резкий судорожный вдох
    PET_NOISE,       // Питомцы (мяуканье, лай)
    IMPACT_NOISE,    // Удары, падение предметов, хлопок двери
    RUSTLING,        // Ворочание / шуршание простыней
    UNKNOWN          // Другие необычные звуки
}
