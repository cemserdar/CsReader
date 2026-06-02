import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  ActivityIndicator,
  SafeAreaView,
  Modal,
  FlatList,
  TextInput,
  ScrollView,
  Share,
  StatusBar,
} from 'react-native';
import { WebView } from 'react-native-webview';
import { Asset } from 'expo-asset';
import * as FileSystem from 'expo-file-system/legacy';
import { Book, Highlight, updateBookProgress, addHighlight, getHighlightsForBook, deleteHighlight, updateHighlightNote } from '../utils/storage';
import { useTheme } from '../utils/themeContext';
import { useI18n } from '../utils/i18n/useI18n';
import { ChevronLeft, Type, BookOpen, Star, Trash2, Edit3, Share2, Palette, X } from 'lucide-react-native';

interface ReaderScreenProps {
  book: Book;
  onBack: () => void;
  onUpdateBookProgress: (id: string, progress: number, lastLocation: { cfi?: string; page?: number }) => void;
}

export const ReaderScreen: React.FC<ReaderScreenProps> = ({ book, onBack, onUpdateBookProgress }) => {
  const { colors, themeName, fontSize, pageTransition, setTheme, setFontSize } = useTheme();
  const i18n = useI18n();
  const webViewRef = useRef<WebView>(null);

  const [loading, setLoading] = useState(true);
  const [showControls, setShowControls] = useState(true);
  const [currentLocation, setCurrentLocation] = useState({ cfi: book.lastCfi || '', page: book.lastPage || 1 });
  const [progress, setProgress] = useState(book.progress);

  // Book Info from WebView
  const [toc, setToc] = useState<{ label: string; href: string }[]>([]);
  const [realTitle, setRealTitle] = useState(book.title);

  // Modals / Drawers States
  const [showStyleModal, setShowStyleModal] = useState(false);
  const [showTocModal, setShowTocModal] = useState(false);
  const [showHighlightModal, setShowHighlightModal] = useState(false);
  const [showNoteModal, setShowNoteModal] = useState(false);

  // Active Highlight Selection State
  const [selectedText, setSelectedText] = useState('');
  const [selectedCfiRange, setSelectedCfiRange] = useState('');
  const [selectedPage, setSelectedPage] = useState<number | undefined>(undefined);
  const [activeHighlight, setActiveHighlight] = useState<Highlight | null>(null);
  const [noteText, setNoteText] = useState('');

  // Book Highlights
  const [bookHighlights, setBookHighlights] = useState<Highlight[]>([]);

  useEffect(() => {
    loadBookHighlights();
  }, [book.id]);

  const loadBookHighlights = async () => {
    const hls = await getHighlightsForBook(book.id);
    setBookHighlights(hls);
  };

  const handleWebViewMessage = async (event: any) => {
    try {
      const message = JSON.parse(event.nativeEvent.data);
      switch (message.type) {
        case 'relocated': // EPUB page changed
          setCurrentLocation({ cfi: message.cfi, page: 1 });
          setProgress(message.progress);
          onUpdateBookProgress(book.id, message.progress, { cfi: message.cfi });
          break;
        case 'pageChange': // PDF page changed
          setCurrentLocation({ cfi: '', page: message.page });
          const pdfProgress = book.lastPage ? (message.page - 1) / (book.lastPage || 1) : 0;
          setProgress(pdfProgress);
          onUpdateBookProgress(book.id, pdfProgress, { page: message.page });
          break;
        case 'progressReady':
          setProgress(message.progress);
          break;
        case 'toc':
          setToc(message.chapters || []);
          break;
        case 'metadata':
          if (message.title) setRealTitle(message.title);
          break;
        case 'click':
          setShowControls(prev => !prev);
          break;
        case 'selected': // Text selection
          setSelectedText(message.text);
          setSelectedCfiRange(message.cfiRange || '');
          setSelectedPage(message.page || undefined);
          setShowHighlightModal(true);
          break;
        case 'highlightClicked': // Highlight tapped in WebView
          const tappedHl = bookHighlights.find(h => h.cfiRange === message.cfiRange);
          if (tappedHl) {
            setActiveHighlight(tappedHl);
            setNoteText(tappedHl.note || '');
            setShowNoteModal(true);
          }
          break;
        case 'error':
          console.error('WebView Error:', message.message);
          break;
      }
    } catch (e) {
      console.error('Failed to parse WebView message', e);
    }
  };

  const triggerWebViewAction = (action: string, payload: any = {}) => {
    if (webViewRef.current) {
      const dataStr = JSON.stringify({ action, ...payload });
      webViewRef.current.postMessage(dataStr);
    }
  };

  const handleLoadEnd = () => {
    // Once WebView is loaded, initialize book rendering
    const htmlAssetFilename = book.type === 'epub' ? 'reader.html' : 'pdf_viewer.html';
    const bookPath = book.uri;

    // Format local file URI for WebView fetch
    // On Android, WebView fetch works with file:// protocol if permission is set.
    const initialLocation = book.type === 'epub' ? book.lastCfi : book.lastPage;

    // Send load message to WebView after 500ms to ensure scripts are initialized
    setTimeout(() => {
      triggerWebViewAction('load', {
        bookPath,
        initialCfi: book.type === 'epub' ? book.lastCfi : undefined,
        initialPage: book.type === 'pdf' ? book.lastPage : undefined,
        theme: themeName,
        fontSize: fontSize,
        pageTransition,
        highlights: bookHighlights,
      });
      setLoading(false);
    }, 600);
  };

  const handleApplyTheme = (theme: 'light' | 'dark' | 'sepia' | 'forest') => {
    setTheme(theme);
    triggerWebViewAction('setTheme', { theme });
  };

  const handleAdjustFontSize = (delta: number) => {
    const newSize = Math.max(12, Math.min(28, fontSize + delta));
    setFontSize(newSize);
    triggerWebViewAction('setFontSize', { fontSize: newSize });
  };

  const handleCreateHighlight = async (color: 'yellow' | 'green' | 'pink' | 'blue' | 'underline') => {
    setShowHighlightModal(false);

    const hl = await addHighlight({
      bookId: book.id,
      cfiRange: book.type === 'epub' ? selectedCfiRange : undefined,
      page: book.type === 'pdf' ? selectedPage : undefined,
      text: selectedText,
      color,
    });

    // Refresh highlights in state
    setBookHighlights(prev => [...prev, hl]);

    // Send action to WebView to render highlight immediately
    triggerWebViewAction('addHighlight', {
      cfiRange: book.type === 'epub' ? selectedCfiRange : undefined,
      page: book.type === 'pdf' ? selectedPage : undefined,
      color,
    });

    // Prompt user to add a note
    setActiveHighlight(hl);
    setNoteText('');
    setShowNoteModal(true);
  };

  const handleSaveNote = async () => {
    if (!activeHighlight) return;
    const updated = await updateHighlightNote(activeHighlight.id, noteText);
    setBookHighlights(updated);
    setShowNoteModal(false);
    setActiveHighlight(null);
  };

  const handleDeleteHighlight = async (hlId: string) => {
    const hlToDelete = bookHighlights.find(h => h.id === hlId);
    if (hlToDelete) {
      // Remove from WebView
      triggerWebViewAction('removeHighlight', { cfiRange: hlToDelete.cfiRange });
      // Remove from Storage
      const updated = await deleteHighlight(hlId);
      setBookHighlights(updated);
    }
    setShowNoteModal(false);
    setActiveHighlight(null);
  };

  const handleShareHighlight = async (text: string) => {
    try {
      await Share.share({
        message: `"${text}"\n\n- CsReader ile "${realTitle}" kitabından paylaşıldı.`,
      });
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const handleNavigateToc = (href: string) => {
    setShowTocModal(false);
    triggerWebViewAction('goToCfi', { cfi: href });
  };

  const handleNavigateHighlight = (hl: Highlight) => {
    setShowTocModal(false);
    if (book.type === 'epub' && hl.cfiRange) {
      triggerWebViewAction('goToCfi', { cfi: hl.cfiRange });
    } else if (book.type === 'pdf' && hl.page) {
      triggerWebViewAction('goToPage', { page: hl.page });
    }
  };

  const getWebViewSource = () => {
    const docDir = FileSystem.documentDirectory;
    const htmlFile = book.type === 'epub' ? 'reader.html' : 'pdf_viewer.html';

    if (docDir) {
      return { uri: `${docDir}${htmlFile}` };
    }

    const assetModule = book.type === 'epub'
      ? require('../assets/reader.html')
      : require('../assets/pdf_viewer.html');

    const asset = Asset.fromModule(assetModule);
    return { uri: asset.uri };
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.bg }]}>
      <StatusBar barStyle={themeName === 'dark' ? 'light-content' : 'dark-content'} />

      {/* WebView Container */}
      <View style={styles.webViewWrapper}>
        <WebView
          ref={webViewRef}
          source={getWebViewSource()}
          originWhitelist={['*']}
          allowFileAccess={true}
          allowUniversalAccessFromFileURLs={true}
          mixedContentMode="always"
          onMessage={handleWebViewMessage}
          onLoadEnd={handleLoadEnd}
          style={{ backgroundColor: colors.bg }}
          javaScriptEnabled={true}
          domStorageEnabled={true}
        />

        {loading && (
          <View style={[styles.loadingOverlay, { backgroundColor: colors.bg }]}>
            <ActivityIndicator size="large" color={colors.primary} />
            <Text style={[styles.loadingText, { color: colors.textMuted }]}>
              Kitap yükleniyor...
            </Text>
          </View>
        )}
      </View>

      {/* Header Bar */}
      {showControls && (
        <View style={[styles.header, { backgroundColor: colors.cardBg, borderBottomColor: colors.border }]}>
          <TouchableOpacity onPress={onBack} style={styles.headerBtn}>
            <ChevronLeft size={26} color={colors.text} />
          </TouchableOpacity>

          <Text numberOfLines={1} style={[styles.bookTitleText, { color: colors.text }]}>
            {realTitle}
          </Text>

          <View style={styles.headerRightBtns}>
            <TouchableOpacity onPress={() => setShowStyleModal(true)} style={styles.headerBtn}>
              <Type size={20} color={colors.text} />
            </TouchableOpacity>

            <TouchableOpacity onPress={() => setShowTocModal(true)} style={styles.headerBtn}>
              <BookOpen size={20} color={colors.text} />
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Footer Bar */}
      {showControls && (
        <View style={[styles.footer, { backgroundColor: colors.cardBg, borderTopColor: colors.border }]}>
          <View style={styles.navButtons}>
            <TouchableOpacity
              onPress={() => triggerWebViewAction('prev')}
              style={[styles.pageBtn, { borderColor: colors.border }]}
            >
              <Text style={{ color: colors.text, fontSize: 13 }}>Geri</Text>
            </TouchableOpacity>

            <Text style={[styles.progressText, { color: colors.textMuted }]}>
              {book.type === 'epub'
                ? `İlerleme: %${Math.round(progress * 100)}`
                : `Sayfa: ${currentLocation.page} / ${book.lastPage || '?'}`}
            </Text>

            <TouchableOpacity
              onPress={() => triggerWebViewAction('next')}
              style={[styles.pageBtn, { borderColor: colors.border }]}
            >
              <Text style={{ color: colors.text, fontSize: 13 }}>İleri</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Style settings modal */}
      <Modal
        visible={showStyleModal}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setShowStyleModal(false)}
      >
        <View style={styles.modalBackdrop}>
          <View style={[styles.styleModalContainer, { backgroundColor: colors.cardBg }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: colors.text }]}>Görünüm Ayarları</Text>
              <TouchableOpacity onPress={() => setShowStyleModal(false)}>
                <X size={24} color={colors.text} />
              </TouchableOpacity>
            </View>

            {/* Themes Selector */}
            <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>TEMA</Text>
            <View style={styles.themesRow}>
              {(['light', 'dark', 'sepia', 'forest'] as const).map(t => (
                <TouchableOpacity
                  key={t}
                  onPress={() => handleApplyTheme(t)}
                  style={[
                    styles.themeOption,
                    {
                      backgroundColor: t === 'light' ? '#FFFFFF' : t === 'dark' ? '#121212' : t === 'sepia' ? '#F4ECD8' : '#E8EFE9',
                      borderColor: themeName === t ? colors.primary : colors.border
                    }
                  ]}
                >
                  <Text style={[
                    styles.themeOptionText,
                    { color: t === 'dark' ? '#FFF' : '#333', fontWeight: themeName === t ? 'bold' : 'normal' }
                  ]}>
                    {t === 'light' && 'Aydınlık'}
                    {t === 'dark' && 'Karanlık'}
                    {t === 'sepia' && 'Sepya'}
                    {t === 'forest' && 'Yeşil'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {/* Font Adjuster */}
            {book.type === 'epub' && (
              <>
                <Text style={[styles.sectionTitle, { color: colors.textMuted, marginTop: 20 }]}>YAZI BOYUTU</Text>
                <View style={styles.fontAdjusterRow}>
                  <TouchableOpacity
                    onPress={() => handleAdjustFontSize(-2)}
                    style={[styles.fontBtn, { backgroundColor: colors.bg, borderColor: colors.border }]}
                  >
                    <Text style={[styles.fontBtnText, { color: colors.text }]}>A-</Text>
                  </TouchableOpacity>

                  <Text style={[styles.fontSizeDisplay, { color: colors.text }]}>
                    {fontSize} px
                  </Text>

                  <TouchableOpacity
                    onPress={() => handleAdjustFontSize(2)}
                    style={[styles.fontBtn, { backgroundColor: colors.bg, borderColor: colors.border }]}
                  >
                    <Text style={[styles.fontBtnText, { color: colors.text }]}>A+</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}
          </View>
        </View>
      </Modal>

      {/* Highlighter picker modal */}
      <Modal
        visible={showHighlightModal}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setShowHighlightModal(false)}
      >
        <View style={styles.selectionBackdrop}>
          <View style={[styles.highlightToolbar, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
            <Text numberOfLines={2} style={[styles.selectedSnippet, { color: colors.textMuted }]}>
              "{selectedText}"
            </Text>

            <View style={styles.colorPickerRow}>
              {(['yellow', 'green', 'pink', 'blue', 'underline'] as const).map(color => (
                <TouchableOpacity
                  key={color}
                  onPress={() => handleCreateHighlight(color)}
                  style={[
                    styles.colorCircle,
                    {
                      backgroundColor:
                        color === 'yellow' ? '#fef08a' :
                          color === 'green' ? '#bbf7d0' :
                            color === 'pink' ? '#fbcfe8' :
                              color === 'blue' ? '#bfdbfe' : 'transparent',
                      borderStyle: color === 'underline' ? 'dashed' : 'solid',
                      borderColor: color === 'underline' ? '#EF4444' : '#E5E7EB',
                      borderWidth: color === 'underline' ? 2 : 1,
                    }
                  ]}
                />
              ))}

              <View style={styles.divider} />

              <TouchableOpacity
                onPress={() => handleShareHighlight(selectedText)}
                style={styles.toolbarIconBtn}
              >
                <Share2 size={20} color={colors.text} />
              </TouchableOpacity>

              <TouchableOpacity
                onPress={() => setShowHighlightModal(false)}
                style={styles.toolbarIconBtn}
              >
                <X size={20} color={colors.text} />
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Add note / edit note modal */}
      <Modal
        visible={showNoteModal}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setShowNoteModal(false)}
      >
        <View style={styles.modalBackdrop}>
          <View style={[styles.styleModalContainer, { backgroundColor: colors.cardBg }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: colors.text }]}>Not Ekle / Düzenle</Text>
              <TouchableOpacity onPress={() => setShowNoteModal(false)}>
                <X size={24} color={colors.text} />
              </TouchableOpacity>
            </View>

            <Text style={[styles.quoteSnippet, { color: colors.text, borderColor: colors.border }]}>
              {activeHighlight?.text}
            </Text>

            <TextInput
              multiline
              style={[
                styles.noteInput,
                {
                  backgroundColor: colors.bg,
                  color: colors.text,
                  borderColor: colors.border
                }
              ]}
              placeholder="Düşüncelerinizi yazın..."
              placeholderTextColor={colors.textMuted}
              value={noteText}
              onChangeText={setNoteText}
            />

            <View style={styles.noteModalButtons}>
              {activeHighlight?.id && (
                <TouchableOpacity
                  onPress={() => handleDeleteHighlight(activeHighlight.id)}
                  style={[styles.noteActionBtn, styles.deleteBtn]}
                >
                  <Trash2 size={18} color="#FFF" />
                  <Text style={styles.noteBtnText}>Sil</Text>
                </TouchableOpacity>
              )}

              <TouchableOpacity
                onPress={handleSaveNote}
                style={[styles.noteActionBtn, { backgroundColor: colors.primary }]}
              >
                <Text style={styles.noteBtnText}>Kaydet</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* TOC & Highlights Drawer */}
      <Modal
        visible={showTocModal}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setShowTocModal(false)}
      >
        <View style={styles.modalBackdrop}>
          <View style={[styles.tocDrawerContainer, { backgroundColor: colors.cardBg }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: colors.text }]}>İçerik & Notlar</Text>
              <TouchableOpacity onPress={() => setShowTocModal(false)}>
                <X size={24} color={colors.text} />
              </TouchableOpacity>
            </View>

            <ScrollView showsVerticalScrollIndicator={false}>
              {/* Chapters list */}
              {toc.length > 0 && (
                <>
                  <Text style={[styles.drawerSectionHeader, { color: colors.textMuted, borderBottomColor: colors.border }]}>
                    BÖLÜMLER
                  </Text>
                  {toc.map((chapter, i) => (
                    <TouchableOpacity
                      key={i}
                      onPress={() => handleNavigateToc(chapter.href)}
                      style={[styles.tocItem, { borderBottomColor: colors.border }]}
                    >
                      <Text style={[styles.tocItemText, { color: colors.text }]}>
                        {chapter.label}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </>
              )}

              {/* Highlights & Notes */}
              <Text style={[
                styles.drawerSectionHeader,
                { color: colors.textMuted, borderBottomColor: colors.border, marginTop: 24 }
              ]}>
                BU KİTAPTAKİ NOTLARIM ({bookHighlights.length})
              </Text>

              {bookHighlights.length === 0 ? (
                <Text style={[styles.emptyNotesText, { color: colors.textMuted }]}>
                  Kitapta henüz vurgulama veya not bulunmuyor. Metinlerin üzerine basılı tutarak not ekleyebilirsiniz.
                </Text>
              ) : (
                bookHighlights.map((hl) => (
                  <View key={hl.id} style={[styles.drawerNoteCard, { backgroundColor: colors.bg, borderColor: colors.border }]}>
                    <TouchableOpacity onPress={() => handleNavigateHighlight(hl)}>
                      <View style={styles.noteCardHeader}>
                        <View style={[
                          styles.colorIndicator,
                          {
                            backgroundColor:
                              hl.color === 'yellow' ? '#fef08a' :
                                hl.color === 'green' ? '#bbf7d0' :
                                  hl.color === 'pink' ? '#fbcfe8' :
                                    hl.color === 'blue' ? '#bfdbfe' : 'transparent',
                            borderWidth: hl.color === 'underline' ? 1 : 0,
                            borderColor: '#EF4444',
                          }
                        ]} />
                        <Text style={[styles.noteDateText, { color: colors.textMuted }]}>
                          {new Date(hl.date).toLocaleDateString('tr-TR')}
                        </Text>
                      </View>

                      <Text numberOfLines={3} style={[styles.noteQuoteText, { color: colors.text }]}>
                        "{hl.text}"
                      </Text>

                      {hl.note ? (
                        <View style={styles.noteContentContainer}>
                          <Edit3 size={12} color={colors.primary} style={{ marginRight: 4 }} />
                          <Text style={[styles.noteCommentText, { color: colors.text }]}>
                            {hl.note}
                          </Text>
                        </View>
                      ) : null}
                    </TouchableOpacity>

                    <View style={styles.noteCardActions}>
                      <TouchableOpacity
                        onPress={() => {
                          setActiveHighlight(hl);
                          setNoteText(hl.note || '');
                          setShowTocModal(false);
                          setShowNoteModal(true);
                        }}
                        style={styles.cardActionBtn}
                      >
                        <Edit3 size={16} color={colors.textMuted} />
                      </TouchableOpacity>

                      <TouchableOpacity
                        onPress={() => {
                          handleShareHighlight(hl.text);
                        }}
                        style={styles.cardActionBtn}
                      >
                        <Share2 size={16} color={colors.textMuted} />
                      </TouchableOpacity>

                      <TouchableOpacity
                        onPress={() => handleDeleteHighlight(hl.id)}
                        style={styles.cardActionBtn}
                      >
                        <Trash2 size={16} color="#EF4444" />
                      </TouchableOpacity>
                    </View>
                  </View>
                ))
              )}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  webViewWrapper: {
    flex: 1,
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
  },
  header: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 90,
    paddingTop: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  headerBtn: {
    padding: 8,
  },
  bookTitleText: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: 'bold',
    marginHorizontal: 12,
  },
  headerRightBtns: {
    flexDirection: 'row',
  },
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 70,
    borderTopWidth: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  navButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  pageBtn: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
    borderWidth: 1,
  },
  progressText: {
    fontSize: 12,
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  selectionBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  styleModalContainer: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    paddingBottom: 40,
    elevation: 10,
  },
  tocDrawerContainer: {
    height: '80%',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    elevation: 10,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    letterSpacing: 1,
    marginBottom: 10,
  },
  themesRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  themeOption: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    marginHorizontal: 4,
  },
  themeOptionText: {
    fontSize: 13,
  },
  fontAdjusterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 10,
  },
  fontBtn: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  fontBtnText: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  fontSizeDisplay: {
    fontSize: 18,
    fontWeight: 'bold',
    marginHorizontal: 30,
  },
  highlightToolbar: {
    width: '100%',
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  selectedSnippet: {
    fontSize: 13,
    fontStyle: 'italic',
    marginBottom: 14,
    lineHeight: 18,
  },
  colorPickerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  colorCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  divider: {
    width: 1,
    height: 24,
    backgroundColor: '#E5E7EB',
    marginHorizontal: 8,
  },
  toolbarIconBtn: {
    padding: 6,
  },
  quoteSnippet: {
    fontSize: 14,
    fontStyle: 'italic',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#3B82F6',
    marginBottom: 16,
    lineHeight: 20,
  },
  noteInput: {
    height: 120,
    borderRadius: 12,
    borderWidth: 1,
    padding: 12,
    fontSize: 14,
    textAlignVertical: 'top',
    marginBottom: 20,
  },
  noteModalButtons: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
  },
  noteActionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 12,
    marginLeft: 12,
  },
  deleteBtn: {
    backgroundColor: '#EF4444',
  },
  noteBtnText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: 'bold',
    marginLeft: 6,
  },
  drawerSectionHeader: {
    fontSize: 11,
    fontWeight: 'bold',
    letterSpacing: 1,
    paddingBottom: 8,
    borderBottomWidth: 1,
    marginBottom: 12,
  },
  tocItem: {
    paddingVertical: 14,
    borderBottomWidth: 1,
  },
  tocItemText: {
    fontSize: 14,
  },
  emptyNotesText: {
    fontSize: 13,
    lineHeight: 20,
    paddingVertical: 20,
    textAlign: 'center',
  },
  drawerNoteCard: {
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 16,
  },
  noteCardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  colorIndicator: {
    width: 24,
    height: 8,
    borderRadius: 4,
  },
  noteDateText: {
    fontSize: 11,
  },
  noteQuoteText: {
    fontSize: 13,
    fontStyle: 'italic',
    lineHeight: 18,
  },
  noteContentContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(0,0,0,0.1)',
  },
  noteCommentText: {
    fontSize: 13,
    fontWeight: '500',
    flex: 1,
  },
  noteCardActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 12,
    paddingTop: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(0,0,0,0.05)',
  },
  cardActionBtn: {
    padding: 6,
    marginLeft: 16,
  },
});
