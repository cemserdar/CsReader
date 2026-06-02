import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  FlatList,
  TextInput,
  Share,
  Alert,
  ScrollView,
} from 'react-native';
import { Highlight, Book, getHighlights, getBooks, deleteHighlight } from '../utils/storage';
import { useTheme } from '../utils/themeContext';
import { ChevronLeft, Search, Trash2, Share2, MessageSquare, BookOpen } from 'lucide-react-native';

interface NotesScreenProps {
  onBack: () => void;
  onSelectBookAtLocation: (book: Book, location: { cfi?: string; page?: number }) => void;
}

export const NotesScreen: React.FC<NotesScreenProps> = ({ onBack, onSelectBookAtLocation }) => {
  const { colors } = useTheme();
  const [highlights, setHighlights] = useState<Highlight[]>([]);
  const [books, setBooks] = useState<Book[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeColorFilter, setActiveColorFilter] = useState<'all' | 'yellow' | 'green' | 'pink' | 'blue' | 'underline'>('all');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    const allHls = await getHighlights();
    const allBooks = await getBooks();
    setHighlights(allHls);
    setBooks(allBooks);
  };

  const handleDelete = (id: string) => {
    Alert.alert(
      'Notu Sil',
      'Bu vurgulamayı ve notu kalıcı olarak silmek istediğinize emin misiniz?',
      [
        { text: 'İptal', style: 'cancel' },
        {
          text: 'Sil',
          style: 'destructive',
          onPress: async () => {
            const updated = await deleteHighlight(id);
            setHighlights(updated);
          },
        },
      ]
    );
  };

  const handleShare = async (hl: Highlight, bookTitle: string) => {
    try {
      let message = `"${hl.text}"\n`;
      if (hl.note) {
        message += `Notum: ${hl.note}\n`;
      }
      message += `\n- "${bookTitle}" kitabından CsReader ile paylaşıldı.`;
      
      await Share.share({ message });
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const handleNotePress = (hl: Highlight) => {
    const associatedBook = books.find(b => b.id === hl.bookId);
    if (associatedBook) {
      onSelectBookAtLocation(associatedBook, { cfi: hl.cfiRange, page: hl.page });
    } else {
      Alert.alert('Hata', 'Bu nota ait kitap bulunamadı. Silinmiş olabilir.');
    }
  };

  // Filter notes based on query and color
  const filteredNotes = highlights.filter(hl => {
    const book = books.find(b => b.id === hl.bookId);
    const bookTitle = book ? book.title : '';
    const bookAuthor = book ? book.author : '';

    const matchesSearch =
      hl.text.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (hl.note || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
      bookTitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
      bookAuthor.toLowerCase().includes(searchQuery.toLowerCase());

    if (!matchesSearch) return false;
    if (activeColorFilter !== 'all' && hl.color !== activeColorFilter) return false;
    
    return true;
  }).sort((a, b) => b.date - a.date); // Sort by date descending

  const renderNoteItem = ({ item }: { item: Highlight }) => {
    const book = books.find(b => b.id === item.bookId);
    const bookTitle = book ? book.title : 'Bilinmeyen Kitap';
    const bookAuthor = book ? book.author : '';

    return (
      <View style={[styles.noteCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
        {/* Book Header */}
        <TouchableOpacity onPress={() => handleNotePress(item)} style={styles.bookHeader}>
          <BookOpen size={16} color={colors.primary} style={{ marginRight: 6 }} />
          <Text numberOfLines={1} style={[styles.bookTitleText, { color: colors.text }]}>
            {bookTitle} {bookAuthor ? `• ${bookAuthor}` : ''}
          </Text>
        </TouchableOpacity>

        {/* Highlight Metadata Row */}
        <View style={styles.metaRow}>
          <View style={[
            styles.colorIndicator,
            {
              backgroundColor: 
                item.color === 'yellow' ? '#fef08a' :
                item.color === 'green' ? '#bbf7d0' :
                item.color === 'pink' ? '#fbcfe8' :
                item.color === 'blue' ? '#bfdbfe' : 'transparent',
              borderWidth: item.color === 'underline' ? 1 : 0,
              borderColor: '#EF4444',
            }
          ]} />
          <Text style={[styles.dateText, { color: colors.textMuted }]}>
            {new Date(item.date).toLocaleDateString('tr-TR')} {item.page ? `(Sayfa ${item.page})` : ''}
          </Text>
        </View>

        {/* Highlight Text */}
        <TouchableOpacity onPress={() => handleNotePress(item)}>
          <Text numberOfLines={4} style={[styles.quoteText, { color: colors.text }]}>
            "{item.text}"
          </Text>
        </TouchableOpacity>

        {/* Note comment if exists */}
        {item.note ? (
          <View style={[styles.commentContainer, { backgroundColor: colors.bg, borderColor: colors.border }]}>
            <MessageSquare size={14} color={colors.primary} style={styles.commentIcon} />
            <Text style={[styles.commentText, { color: colors.text }]}>
              {item.note}
            </Text>
          </View>
        ) : null}

        {/* Actions Row */}
        <View style={[styles.actionsRow, { borderTopColor: colors.border }]}>
          <TouchableOpacity
            onPress={() => handleNotePress(item)}
            style={styles.actionBtn}
          >
            <Text style={{ color: colors.primary, fontSize: 12, fontWeight: 'bold' }}>Kitapta Git</Text>
          </TouchableOpacity>

          <View style={styles.rightActions}>
            <TouchableOpacity
              onPress={() => handleShare(item, bookTitle)}
              style={styles.iconBtn}
            >
              <Share2 size={18} color={colors.textMuted} />
            </TouchableOpacity>

            <TouchableOpacity
              onPress={() => handleDelete(item.id)}
              style={styles.iconBtn}
            >
              <Trash2 size={18} color="#EF4444" />
            </TouchableOpacity>
          </View>
        </View>
      </View>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: colors.border }]}>
        <TouchableOpacity onPress={onBack} style={styles.backBtn}>
          <ChevronLeft size={26} color={colors.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: colors.text }]}>Tüm Notlarım</Text>
        <View style={{ width: 26 }} />
      </View>

      {/* Search */}
      <View style={[styles.searchContainer, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
        <Search size={20} color={colors.textMuted} style={styles.searchIcon} />
        <TextInput
          style={[styles.searchInput, { color: colors.text }]}
          placeholder="Notlarında ara..."
          placeholderTextColor={colors.textMuted}
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
      </View>

      {/* Color Filter Row */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.colorFiltersContainer}
      >
        <TouchableOpacity
          onPress={() => setActiveColorFilter('all')}
          style={[
            styles.filterTab,
            activeColorFilter === 'all' && { backgroundColor: colors.accent, borderColor: colors.primary }
          ]}
        >
          <Text style={[styles.filterTabText, { color: colors.text }, activeColorFilter === 'all' && { color: colors.primary, fontWeight: 'bold' }]}>
            Hepsi
          </Text>
        </TouchableOpacity>

        {(['yellow', 'green', 'pink', 'blue', 'underline'] as const).map(color => (
          <TouchableOpacity
            key={color}
            onPress={() => setActiveColorFilter(color)}
            style={[
              styles.filterTab,
              activeColorFilter === color && { backgroundColor: colors.accent, borderColor: colors.primary },
              { flexDirection: 'row', alignItems: 'center' }
            ]}
          >
            <View style={[
              styles.colorDot,
              {
                backgroundColor: 
                  color === 'yellow' ? '#fef08a' :
                  color === 'green' ? '#bbf7d0' :
                  color === 'pink' ? '#fbcfe8' :
                  color === 'blue' ? '#bfdbfe' : 'transparent',
                borderWidth: color === 'underline' ? 1 : 0,
                borderColor: '#EF4444',
              }
            ]} />
            <Text style={[
              styles.filterTabText,
              { color: colors.text },
              activeColorFilter === color && { color: colors.primary, fontWeight: 'bold' }
            ]}>
              {color === 'yellow' && 'Sarı'}
              {color === 'green' && 'Yeşil'}
              {color === 'pink' && 'Pembe'}
              {color === 'blue' && 'Mavi'}
              {color === 'underline' && 'Altı Çizili'}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Notes List */}
      {filteredNotes.length === 0 ? (
        <View style={styles.centerContainer}>
          <MessageSquare size={64} color={colors.border} />
          <Text style={[styles.emptyTitle, { color: colors.text }]}>Not Bulunmadı</Text>
          <Text style={[styles.emptySubtitle, { color: colors.textMuted }]}>
            {searchQuery ? 'Aramanıza uyan not veya vurgulama bulunamadı.' : 'Henüz not almadınız. Okuyucu ekranından bir metni seçerek başlayabilirsiniz.'}
          </Text>
        </View>
      ) : (
        <FlatList
          data={filteredNotes}
          renderItem={renderNoteItem}
          keyExtractor={item => item.id}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
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
  backBtn: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
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
  colorFiltersContainer: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    height: 60,
  },
  filterTab: {
    paddingVertical: 6,
    paddingHorizontal: 14,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    marginRight: 8,
    justifyContent: 'center',
  },
  filterTabText: {
    fontSize: 12,
  },
  colorDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginRight: 6,
  },
  listContainer: {
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  noteCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  bookHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  bookTitleText: {
    fontSize: 13,
    fontWeight: 'bold',
    flex: 1,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  colorIndicator: {
    width: 32,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  dateText: {
    fontSize: 11,
  },
  quoteText: {
    fontSize: 14,
    fontStyle: 'italic',
    lineHeight: 20,
  },
  commentContainer: {
    flexDirection: 'row',
    marginTop: 10,
    padding: 10,
    borderRadius: 10,
    borderWidth: 1,
  },
  commentIcon: {
    marginRight: 6,
    marginTop: 2,
  },
  commentText: {
    fontSize: 13,
    lineHeight: 18,
    flex: 1,
  },
  actionsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 12,
    paddingTop: 10,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  actionBtn: {
    paddingVertical: 4,
  },
  rightActions: {
    flexDirection: 'row',
  },
  iconBtn: {
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
    fontSize: 18,
    fontWeight: 'bold',
    marginTop: 16,
  },
  emptySubtitle: {
    fontSize: 13,
    textAlign: 'center',
    marginTop: 8,
    lineHeight: 18,
  },
});
