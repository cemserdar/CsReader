import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  View,
  ActivityIndicator,
  Text,
  Animated,
  StatusBar,
} from 'react-native';
import { ThemeProvider, useTheme } from './utils/themeContext';
import { prepareReaderAssets } from './utils/assets';
import { LibraryScreen } from './screens/LibraryScreen';
import { ReaderScreen } from './screens/ReaderScreen';
import { NotesScreen } from './screens/NotesScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { Book, updateBookProgress } from './utils/storage';

function AppContent() {
  const { colors, themeName } = useTheme();
  
  // App initialization state
  const [initializing, setInitializing] = useState(true);
  
  // Navigation states
  const [activeScreen, setActiveScreen] = useState<'library' | 'reader' | 'notes' | 'settings'>('library');
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);

  // Transition Animation
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    async function initApp() {
      try {
        // Copy HTML/JS files to DocumentDirectory on first boot
        // Set force=true in development to make sure updates to reader.html are copied
        await prepareReaderAssets(__DEV__);
      } catch (err) {
        console.error('Failed to prepare reader assets:', err);
      } finally {
        setInitializing(false);
      }
    }
    initApp();
  }, []);

  // Animate screen transitions
  useEffect(() => {
    if (initializing) return;
    
    fadeAnim.setValue(0);
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 350,
      useNativeDriver: true,
    }).start();
  }, [activeScreen, initializing]);

  const handleSelectBook = (book: Book) => {
    setSelectedBook(book);
    setActiveScreen('reader');
  };

  const handleSelectBookAtLocation = (book: Book, location: { cfi?: string; page?: number }) => {
    // If the book is already selected, update its last location in storage
    const updatedBook = {
      ...book,
      lastCfi: location.cfi || book.lastCfi,
      lastPage: location.page || book.lastPage,
    };
    setSelectedBook(updatedBook);
    setActiveScreen('reader');
  };

  const handleUpdateBookProgress = async (id: string, progress: number, lastLocation: { cfi?: string; page?: number }) => {
    // Save to storage
    const updatedBooks = await updateBookProgress(id, progress, lastLocation);
    
    // Update local book state if it is currently open
    if (selectedBook && selectedBook.id === id) {
      setSelectedBook(prev => prev ? {
        ...prev,
        progress,
        lastCfi: lastLocation.cfi || prev.lastCfi,
        lastPage: lastLocation.page || prev.lastPage,
      } : null);
    }
  };

  const handleDatabaseCleared = () => {
    setSelectedBook(null);
    setActiveScreen('library');
  };

  if (initializing) {
    return (
      <View style={[styles.loadingContainer, { backgroundColor: '#F3F4F6' }]}>
        <ActivityIndicator size="large" color="#3B82F6" />
        <Text style={styles.loadingText}>CsReader Hazırlanıyor...</Text>
      </View>
    );
  }

  const renderScreen = () => {
    switch (activeScreen) {
      case 'library':
        return (
          <LibraryScreen
            onSelectBook={handleSelectBook}
            onNavigate={(screen) => setActiveScreen(screen)}
          />
        );
      case 'reader':
        if (!selectedBook) return null;
        return (
          <ReaderScreen
            book={selectedBook}
            onBack={() => setActiveScreen('library')}
            onUpdateBookProgress={handleUpdateBookProgress}
          />
        );
      case 'notes':
        return (
          <NotesScreen
            onBack={() => setActiveScreen('library')}
            onSelectBookAtLocation={handleSelectBookAtLocation}
          />
        );
      case 'settings':
        return (
          <SettingsScreen
            onBack={() => setActiveScreen('library')}
            onDatabaseCleared={handleDatabaseCleared}
          />
        );
      default:
        return null;
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      <StatusBar barStyle={themeName === 'dark' ? 'light-content' : 'dark-content'} />
      <Animated.View style={[styles.animWrapper, { opacity: fadeAnim }]}>
        {renderScreen()}
      </Animated.View>
    </View>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  animWrapper: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 14,
    fontSize: 15,
    fontWeight: 'bold',
    color: '#4B5563',
  },
});
