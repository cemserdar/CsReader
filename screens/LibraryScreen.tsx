import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  FlatList,
  TextInput,
  ActivityIndicator,
  Alert,
  Dimensions,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import { Book, getBooks, saveBook, toggleFavorite, deleteBook, scanBooks } from '../utils/storage';
import { useTheme } from '../utils/themeContext';
import { BookOpen, FileText, Star, Trash2, Plus, Search, Settings as SettingsIcon, ClipboardList } from 'lucide-react-native';
import { useI18n } from '../utils/i18n/useI18n';

interface LibraryScreenProps {
  onSelectBook: (book: Book) => void;
  onNavigate: (screen: 'library' | 'notes' | 'settings') => void;
}

export const LibraryScreen: React.FC<LibraryScreenProps> = ({ onSelectBook, onNavigate }) => {
  const { colors } = useTheme();
  const i18n = useI18n();
  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState<'all' | 'epub' | 'pdf' | 'favorite'>('all');

  useEffect(() => {
    loadBooks();
  }, []);

  const loadBooks = async () => {
    setLoading(true);
    const storedBooks = await getBooks();
    setBooks(storedBooks);
    setLoading(false);
  };

  const handleImportBook = async () => {
    try {
      setImporting(true);
      const result = await DocumentPicker.getDocumentAsync({
        type: ['application/epub+zip', 'application/pdf'],
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets || result.assets.length === 0) {
        setImporting(false);
        return;
      }

      const selectedFile = result.assets[0];
      const docDir = FileSystem.documentDirectory;
      if (!docDir) {
        throw new Error('Document directory not available');
      }

      // Generate a unique filename to prevent overwriting
      const cleanName = selectedFile.name.replace(/[^a-zA-Z0-9.\-_]/g, '_');
      const uniqueFilename = `${Date.now()}_${cleanName}`;
      const targetUri = `${docDir}${uniqueFilename}`;

      // Copy file to permanent storage
      await FileSystem.copyAsync({
        from: selectedFile.uri,
        to: targetUri,
      });

      // Parse temporary title and author from file name
      // Expected format: "Title - Author.epub" or "Title.epub"
      let fileBaseName = selectedFile.name;
      const dotIndex = fileBaseName.lastIndexOf('.');
      if (dotIndex > -1) {
        fileBaseName = fileBaseName.substring(0, dotIndex);
      }

      let title = fileBaseName;
      let author = 'Unknown Author';

      const dashIndex = fileBaseName.indexOf('-');
      if (dashIndex > 0) {
        title = fileBaseName.substring(0, dashIndex).trim();
        author = fileBaseName.substring(dashIndex + 1).trim();
      }

      const isEpub = selectedFile.name.toLowerCase().endsWith('.epub') || selectedFile.mimeType?.includes('epub');
      const bookType: 'epub' | 'pdf' = isEpub ? 'epub' : 'pdf';

      const newBook: Book = {
        id: `book_${Date.now()}`,
        title: title,
        author: author,
        uri: targetUri,
        type: bookType,
        progress: 0,
        addedDate: Date.now(),
        lastRead: Date.now(),
        favorite: false,
      };

      const updatedBooks = await saveBook(newBook);
      setBooks(updatedBooks);

      onSelectBook(newBook);
      Alert.alert(i18n('common.success'), i18n('library.importSuccess'));
    } catch (error: any) {
      console.error(error);
      Alert.alert(i18n('common.error'), `${i18n('library.importError')}: ${error.message}`);
    } finally {
      setImporting(false);
    }
  };

  const handleToggleFavorite = async (id: string) => {
    const updatedBooks = await toggleFavorite(id);
    setBooks(updatedBooks);
  };

  const handleScanLibrary = async () => {
    try {
      setScanning(true);
      const scannedBooks = await scanBooks();
      setBooks(scannedBooks);
      Alert.alert(i18n('library.scanBooks'), `${scannedBooks.length} ${i18n('library.scanBooks').toLowerCase()}`);
    } catch (error: any) {
      console.error('Error scanning library', error);
      Alert.alert(i18n('common.error'), error?.message || i18n('library.importError'));
    } finally {
      setScanning(false);
    }
  };

  const handleDeleteBook = (id: string, title: string) => {
    Alert.alert(
      i18n('library.deleteBook'),
      `"${title}" - ${i18n('common.delete')}?`,
      [
        { text: i18n('common.cancel'), style: 'cancel' },
        {
          text: i18n('common.delete'),
          style: 'destructive',
          onPress: async () => {
            const updatedBooks = await deleteBook(id);
            setBooks(updatedBooks);
            Alert.alert(i18n('common.success'), i18n('library.deletedSuccess'));
          },
        },
      ]
    );
  };

  // Filter books based on search query and selected tab
  const filteredBooks = books.filter(book => {
    const matchesSearch =
      book.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      book.author.toLowerCase().includes(searchQuery.toLowerCase());

    if (!matchesSearch) return false;

    if (activeTab === 'epub') return book.type === 'epub';
    if (activeTab === 'pdf') return book.type === 'pdf';
    if (activeTab === 'favorite') return book.favorite;
    return true;
  });

  const getTabLabel = (tab: string) => {
    switch (tab) {
      case 'all':
        return i18n('library.allBooks');
      case 'epub':
        return 'EPUB';
      case 'pdf':
        return 'PDF';
      case 'favorite':
        return i18n('library.favorites');
      default:
        return tab;
    }
  };

  const renderBookItem = ({ item }: { item: Book }) => {
    const progressPercent = Math.round(item.progress * 100);
    const isEpub = item.type === 'epub';

    return (
      <View style={[styles.bookCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
        {/* Cover / Icon Area */}
        <TouchableOpacity
          style={[styles.coverContainer, { backgroundColor: colors.accent }]}
          onPress={() => onSelectBook(item)}
          activeOpacity={0.8}
        >
          {isEpub ? (
            <BookOpen size={44} color={colors.primary} />
          ) : (
            <FileText size={44} color="#EF4444" />
          )}
          <Text style={[styles.coverTypeText, { color: colors.textMuted }]}>
            {item.type.toUpperCase()}
          </Text>
        </TouchableOpacity>

        {/* Content Info */}
        <View style={styles.bookInfo}>
          <TouchableOpacity onPress={() => onSelectBook(item)} activeOpacity={0.7}>
            <Text numberOfLines={1} style={[styles.bookTitle, { color: colors.text }]}>
              {item.title}
            </Text>
            <Text numberOfLines={1} style={[styles.bookAuthor, { color: colors.textMuted }]}>
              {item.author}
            </Text>
          </TouchableOpacity>

          {/* Progress Bar */}
          <View style={styles.progressContainer}>
            <View style={[styles.progressBarBg, { backgroundColor: colors.border }]}>
              <View
                style={[
                  styles.progressBarFill,
                  {
                    width: `${progressPercent}%`,
                    backgroundColor: isEpub ? colors.primary : '#EF4444',
                  },
                ]}
              />
            </View>
            <Text style={[styles.progressText, { color: colors.textMuted }]}>
              {progressPercent}% {i18n('reader.progress')}
            </Text>
          </View>

          {/* Action Row */}
          <View style={styles.actionRow}>
            <TouchableOpacity
              onPress={() => handleToggleFavorite(item.id)}
              style={styles.actionBtn}
              activeOpacity={0.6}
            >
              <Star
                size={20}
                color={item.favorite ? '#F59E0B' : colors.textMuted}
                fill={item.favorite ? '#F59E0B' : 'transparent'}
              />
            </TouchableOpacity>

            <TouchableOpacity
              onPress={() => handleDeleteBook(item.id, item.title)}
              style={styles.actionBtn}
              activeOpacity={0.6}
            >
              <Trash2 size={20} color="#EF4444" />
            </TouchableOpacity>
          </View>
        </View>
      </View>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      {/* Top Bar */}
      <View style={[styles.header, { borderBottomColor: colors.border }]}>
        <View>
          <Text style={[styles.headerTitle, { color: colors.text }]}>CsReader</Text>
          <Text style={[styles.headerSubtitle, { color: colors.textMuted }]}>{i18n('app.subtitle')}</Text>
        </View>

        <View style={styles.headerButtons}>
          <TouchableOpacity
            style={[styles.headerBtn, { backgroundColor: colors.cardBg, borderColor: colors.border }]}
            onPress={handleScanLibrary}
            disabled={scanning}
          >
            {scanning ? (
              <ActivityIndicator size="small" color={colors.text} />
            ) : (
              <Search size={22} color={colors.text} />
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.headerBtn, { backgroundColor: colors.cardBg, borderColor: colors.border }]}
            onPress={() => onNavigate('notes')}
          >
            <ClipboardList size={22} color={colors.text} />
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.headerBtn, { backgroundColor: colors.cardBg, borderColor: colors.border }]}
            onPress={() => onNavigate('settings')}
          >
            <SettingsIcon size={22} color={colors.text} />
          </TouchableOpacity>
        </View>
      </View>

      {/* Search Bar */}
      <View style={[styles.searchContainer, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
        <Search size={20} color={colors.textMuted} style={styles.searchIcon} />
        <TextInput
          style={[styles.searchInput, { color: colors.text }]}
          placeholder={i18n('library.searchPlaceholder')}
          placeholderTextColor={colors.textMuted}
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
      </View>

      {/* Tabs */}
      <View style={styles.tabsContainer}>
        {(['all', 'epub', 'pdf', 'favorite'] as const).map(tab => (
          <TouchableOpacity
            key={tab}
            onPress={() => setActiveTab(tab)}
            style={[
              styles.tab,
              activeTab === tab && {
                backgroundColor: tab === 'pdf' ? 'rgba(239, 68, 68, 0.1)' : colors.accent,
                borderColor: tab === 'pdf' ? '#EF4444' : colors.primary,
              },
              { borderColor: 'transparent' }
            ]}
          >
            <Text
              style={[
                styles.tabText,
                { color: colors.textMuted },
                activeTab === tab && {
                  color: tab === 'pdf' ? '#EF4444' : colors.primary,
                  fontWeight: 'bold',
                },
              ]}
            >
              {getTabLabel(tab)}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Books List / Grid */}
      {loading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      ) : filteredBooks.length === 0 ? (
        <View style={styles.centerContainer}>
          <BookOpen size={64} color={colors.border} />
          <Text style={[styles.emptyTitle, { color: colors.text }]}>{i18n('library.emptyLibrary')}</Text>
          <Text style={[styles.emptySubtitle, { color: colors.textMuted }]}>
            {searchQuery ? i18n('library.searchPlaceholder') : i18n('library.emptyLibraryMessage')}
          </Text>
          {!searchQuery && (
            <>
              <TouchableOpacity
                style={[styles.emptyBtn, { backgroundColor: colors.primary }]}
                onPress={handleImportBook}
                disabled={importing}
              >
                <Text style={styles.emptyBtnText}>{i18n('library.addBook')}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.emptyBtn, { backgroundColor: colors.border, marginTop: 12 }]}
                onPress={handleScanLibrary}
                disabled={scanning}
              >
                <Text style={[styles.emptyBtnText, { color: colors.text }]}>
                  {scanning ? i18n('library.scanningBooks') : i18n('library.scanBooks')}
                </Text>
              </TouchableOpacity>
            </>
          )}
        </View>
      ) : (
        <FlatList
          data={filteredBooks}
          renderItem={renderBookItem}
          keyExtractor={item => item.id}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}

      {/* Floating Add Button */}
      {books.length > 0 && (
        <TouchableOpacity
          style={[styles.fab, { backgroundColor: colors.primary }]}
          onPress={handleImportBook}
          disabled={importing}
          activeOpacity={0.8}
        >
          {importing ? (
            <ActivityIndicator size="small" color="#FFF" />
          ) : (
            <Plus size={28} color="#FFF" />
          )}
        </TouchableOpacity>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 15,
    borderBottomWidth: 1,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  headerSubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  headerButtons: {
    flexDirection: 'row',
  },
  headerBtn: {
    padding: 10,
    borderRadius: 12,
    borderWidth: 1,
    marginLeft: 8,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 20,
    marginTop: 15,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 14,
    borderWidth: 1,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 15,
    padding: 0,
  },
  tabsContainer: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginTop: 15,
    marginBottom: 10,
  },
  tab: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 1.5,
    marginRight: 8,
  },
  tabText: {
    fontSize: 13,
    fontWeight: '500',
  },
  listContainer: {
    paddingHorizontal: 20,
    paddingBottom: 100,
    paddingTop: 5,
  },
  bookCard: {
    flexDirection: 'row',
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 16,
    overflow: 'hidden',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  coverContainer: {
    width: 100,
    height: 130,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  coverTypeText: {
    fontSize: 10,
    fontWeight: '900',
    position: 'absolute',
    bottom: 8,
  },
  bookInfo: {
    flex: 1,
    padding: 14,
    justifyContent: 'space-between',
  },
  bookTitle: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  bookAuthor: {
    fontSize: 13,
    marginTop: 2,
  },
  progressContainer: {
    marginTop: 8,
  },
  progressBarBg: {
    height: 5,
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 11,
    marginTop: 4,
  },
  actionRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 4,
  },
  actionBtn: {
    padding: 6,
    marginLeft: 12,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginTop: 16,
  },
  emptySubtitle: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 8,
    lineHeight: 20,
  },
  emptyBtn: {
    marginTop: 24,
    paddingHorizontal: 28,
    paddingVertical: 12,
    borderRadius: 24,
  },
  emptyBtnText: {
    color: '#FFF',
    fontSize: 15,
    fontWeight: 'bold',
  },
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
});
