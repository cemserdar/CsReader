import React, { createContext, useState, useEffect, useContext } from 'react';
import { getSettings, saveSettings, PageTransition } from './storage';

export type ThemeName = 'light' | 'dark' | 'sepia' | 'forest';

export interface ThemeColors {
  bg: string;
  cardBg: string;
  text: string;
  textMuted: string;
  border: string;
  primary: string;
  accent: string;
}

export const THEMES: Record<ThemeName, ThemeColors> = {
  light: {
    bg: '#F3F4F6', // Slate-100
    cardBg: '#FFFFFF',
    text: '#1F2937', // Slate-800
    textMuted: '#6B7280',
    border: '#E5E7EB',
    primary: '#3B82F6', // Blue-500
    accent: '#EFF6FF',
  },
  dark: {
    bg: '#0F172A', // Slate-900 (Rich Dark Blue-Grey)
    cardBg: '#1E293B', // Slate-800
    text: '#F8FAFC', // Slate-50
    textMuted: '#94A3B8', // Slate-400
    border: '#334155', // Slate-700
    primary: '#60A5FA', // Blue-400
    accent: '#1E293B',
  },
  sepia: {
    bg: '#F4ECD8', // Traditional Warm Sepia
    cardBg: '#FAF6EB',
    text: '#5C4033', // Deep Sepia Brown
    textMuted: '#8C7768',
    border: '#E3D7C1',
    primary: '#B45309', // Amber-700 (Warm Sienna)
    accent: '#FAF4E3',
  },
  forest: {
    bg: '#E8EFE9', // Relaxing Sage Green
    cardBg: '#F3F7F2',
    text: '#223821', // Dark Forest Green
    textMuted: '#5D735C',
    border: '#D2DEC5',
    primary: '#15803D', // Green-700
    accent: '#EEF4EC',
  },
};

export type ThemePageTransition = PageTransition;

interface ThemeContextType {
  themeName: ThemeName;
  colors: ThemeColors;
  fontSize: number;
  pageTransition: ThemePageTransition;
  setTheme: (theme: ThemeName) => Promise<void>;
  setFontSize: (size: number) => Promise<void>;
  setPageTransition: (transition: ThemePageTransition) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [themeName, setThemeNameState] = useState<ThemeName>('light');
  const [fontSize, setFontSizeState] = useState<number>(16);
  const [pageTransition, setPageTransitionState] = useState<ThemePageTransition>('slide');

  useEffect(() => {
    async function loadThemeSettings() {
      const settings = await getSettings();
      setThemeNameState(settings.theme);
      setFontSizeState(settings.fontSize);
      setPageTransitionState(settings.pageTransition);
    }
    loadThemeSettings();
  }, []);

  const setTheme = async (name: ThemeName) => {
    setThemeNameState(name);
    await saveSettings({ theme: name });
  };

  const setFontSize = async (size: number) => {
    setFontSizeState(size);
    await saveSettings({ fontSize: size });
  };

  const setPageTransition = async (transition: PageTransition) => {
    setPageTransitionState(transition);
    await saveSettings({ pageTransition: transition });
  };

  const colors = THEMES[themeName];

  return (
    <ThemeContext.Provider value={{ themeName, colors, fontSize, pageTransition, setTheme, setFontSize, setPageTransition }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
