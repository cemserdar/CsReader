import React, { createContext, useContext, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getDeviceLanguage, LanguageCode, getSupportedLanguages } from './i18n';

interface LanguageContextType {
    language: LanguageCode;
    setLanguage: (language: LanguageCode) => void;
    supportedLanguages: { code: LanguageCode; name: string }[];
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [language, setLanguageState] = useState<LanguageCode>('en');

    // Load language on mount
    useEffect(() => {
        const loadLanguage = async () => {
            try {
                // Try to load saved language
                const savedLanguage = await AsyncStorage.getItem('app_language');
                if (savedLanguage && isSupportedLanguage(savedLanguage)) {
                    setLanguageState(savedLanguage as LanguageCode);
                } else {
                    // Use device language
                    const deviceLanguage = getDeviceLanguage();
                    setLanguageState(deviceLanguage);
                    // Save device language
                    await AsyncStorage.setItem('app_language', deviceLanguage);
                }
            } catch (error) {
                console.error('Error loading language:', error);
                const deviceLanguage = getDeviceLanguage();
                setLanguageState(deviceLanguage);
            }
            // Language loaded, context is ready
        };

        loadLanguage();
    }, []);

    const setLanguage = async (newLanguage: LanguageCode) => {
        try {
            await AsyncStorage.setItem('app_language', newLanguage);
            setLanguageState(newLanguage);
        } catch (error) {
            console.error('Error saving language:', error);
        }
    };

    // Always provide context, even during loading
    return (
        <LanguageContext.Provider
            value={{
                language,
                setLanguage,
                supportedLanguages: getSupportedLanguages(),
            }}
        >
            {children}
        </LanguageContext.Provider>
    );
};

// Helper to check if language is supported
function isSupportedLanguage(lang: any): lang is LanguageCode {
    const supported: LanguageCode[] = ['tr', 'en', 'de', 'fr', 'es', 'pt', 'ru', 'sv', 'no', 'da', 'fi'];
    return supported.includes(lang);
}

// Hook to use language context
export const useLanguage = () => {
    const context = useContext(LanguageContext);
    if (!context) {
        throw new Error('useLanguage must be used within LanguageProvider');
    }
    return context;
};
