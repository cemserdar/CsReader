import { t } from './i18n';
import { useLanguage } from './LanguageContext';

/**
 * Hook to use translations in components
 * Usage: const i18n = useI18n();
 *        return <Text>{i18n('app.title')}</Text>
 */
export const useI18n = () => {
    const { language } = useLanguage();

    return (key: string): string => {
        return t(key, language);
    };
};
