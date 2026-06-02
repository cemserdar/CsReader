import * as Localization from 'expo-localization';

export type LanguageCode = 'tr' | 'en' | 'de' | 'fr' | 'es' | 'pt' | 'ru' | 'sv' | 'no' | 'da' | 'fi';

// Language mapping from device locale to supported languages
const localeMap: { [key: string]: LanguageCode } = {
    tr: 'tr',
    'tr-TR': 'tr',
    en: 'en',
    'en-US': 'en',
    'en-GB': 'en',
    de: 'de',
    'de-DE': 'de',
    fr: 'fr',
    'fr-FR': 'fr',
    es: 'es',
    'es-ES': 'es',
    pt: 'pt',
    'pt-PT': 'pt',
    'pt-BR': 'pt',
    ru: 'ru',
    'ru-RU': 'ru',
    sv: 'sv',
    'sv-SE': 'sv',
    no: 'no',
    'nb-NO': 'no',
    'nn-NO': 'no',
    da: 'da',
    'da-DK': 'da',
    fi: 'fi',
    'fi-FI': 'fi',
};

// Load translations dynamically
const translations: { [key in LanguageCode]: any } = {
    tr: require('./languages/tr.json'),
    en: require('./languages/en.json'),
    de: require('./languages/de.json'),
    fr: require('./languages/fr.json'),
    es: require('./languages/es.json'),
    pt: require('./languages/pt.json'),
    ru: require('./languages/ru.json'),
    sv: require('./languages/sv.json'),
    no: require('./languages/no.json'),
    da: require('./languages/da.json'),
    fi: require('./languages/fi.json'),
};

// Get device language
export const getDeviceLanguage = (): LanguageCode => {
    const locales = Localization.getLocales();

    if (locales && locales.length > 0) {
        for (const locale of locales) {
            // Try exact match first
            if (localeMap[locale.languageCode]) {
                return localeMap[locale.languageCode];
            }

            // Try with region code
            const fullLocale = `${locale.languageCode}-${locale.regionCode}`;
            if (localeMap[fullLocale]) {
                return localeMap[fullLocale];
            }
        }
    }

    // Fallback to English if device language not supported
    return 'en';
};

// Get translation
export const t = (key: string, language: LanguageCode = 'en'): string => {
    const translation = translations[language];

    // Navigate through nested keys (e.g., "app.title")
    const keys = key.split('.');
    let current: any = translation;

    for (const k of keys) {
        if (current && typeof current === 'object' && k in current) {
            current = current[k];
        } else {
            // Return key itself if translation not found
            return key;
        }
    }

    return typeof current === 'string' ? current : key;
};

// Get all supported languages
export const getSupportedLanguages = (): { code: LanguageCode; name: string }[] => {
    return [
        { code: 'tr', name: 'Türkçe' },
        { code: 'en', name: 'English' },
        { code: 'de', name: 'Deutsch' },
        { code: 'fr', name: 'Français' },
        { code: 'es', name: 'Español' },
        { code: 'pt', name: 'Português' },
        { code: 'ru', name: 'Русский' },
        { code: 'sv', name: 'Svenska' },
        { code: 'no', name: 'Norsk' },
        { code: 'da', name: 'Dansk' },
        { code: 'fi', name: 'Suomi' },
    ];
};
