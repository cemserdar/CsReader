import React from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  Alert,
  ScrollView,
} from 'react-native';
import { useTheme, ThemeName } from '../utils/themeContext';
import { clearAllDatabase } from '../utils/storage';
import { ChevronLeft, Trash2, ShieldAlert, Palette, Type, HelpCircle } from 'lucide-react-native';

interface SettingsScreenProps {
  onBack: () => void;
  onDatabaseCleared: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ onBack, onDatabaseCleared }) => {
  const { colors, themeName, fontSize, pageTransition, setTheme, setFontSize, setPageTransition } = useTheme();

  const handleClearDatabase = () => {
    Alert.alert(
      'Uygulama Verilerini Sıfırla',
      'Tüm e-kitaplar, PDF dosyaları, aldığınız notlar ve vurgulamalar kalıcı olarak silinecektir. Bu işlem geri alınamaz. Sıfırlamak istediğinize emin misiniz?',
      [
        { text: 'İptal', style: 'cancel' },
        {
          text: 'Sıfırla',
          style: 'destructive',
          onPress: async () => {
            await clearAllDatabase();
            Alert.alert('Başarılı', 'Uygulama başarıyla sıfırlandı.', [
              {
                text: 'Tamam',
                onPress: () => {
                  onDatabaseCleared();
                  onBack();
                },
              },
            ]);
          },
        },
      ]
    );
  };

  const handleAdjustFontSize = (delta: number) => {
    const newSize = Math.max(12, Math.min(28, fontSize + delta));
    setFontSize(newSize);
  };

  const handleSetPageTransition = (transition: 'none' | 'slide' | 'fade') => {
    setPageTransition(transition);
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: colors.border }]}>
        <TouchableOpacity onPress={onBack} style={styles.backBtn}>
          <ChevronLeft size={26} color={colors.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: colors.text }]}>Ayarlar</Text>
        <View style={{ width: 26 }} />
      </View>

      <ScrollView contentContainerStyle={styles.contentContainer} showsVerticalScrollIndicator={false}>
        {/* Theme Settings */}
        <View style={[styles.sectionCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
          <View style={styles.sectionHeader}>
            <Palette size={20} color={colors.primary} style={{ marginRight: 8 }} />
            <Text style={[styles.sectionTitle, { color: colors.text }]}>Görünüm Teması</Text>
          </View>
          <Text style={[styles.sectionSubtitle, { color: colors.textMuted }]}>
            Uygulamanın genel renk şemasını değiştirin.
          </Text>

          <View style={styles.themesContainer}>
            {(['light', 'dark', 'sepia', 'forest'] as const).map((t) => (
              <TouchableOpacity
                key={t}
                onPress={() => setTheme(t)}
                style={[
                  styles.themeBox,
                  {
                    backgroundColor:
                      t === 'light' ? '#FFFFFF' :
                        t === 'dark' ? '#1E293B' :
                          t === 'sepia' ? '#FAF6EB' : '#F3F7F2',
                    borderColor: themeName === t ? colors.primary : colors.border,
                  },
                ]}
              >
                <View style={[
                  styles.themeCircle,
                  {
                    backgroundColor:
                      t === 'light' ? '#F3F4F6' :
                        t === 'dark' ? '#0F172A' :
                          t === 'sepia' ? '#F4ECD8' : '#E8EFE9',
                  }
                ]} />
                <Text style={[styles.themeLabel, { color: colors.text, fontWeight: themeName === t ? 'bold' : 'normal' }]}>
                  {t === 'light' && 'Aydınlık'}
                  {t === 'dark' && 'Karanlık'}
                  {t === 'sepia' && 'Sepya'}
                  {t === 'forest' && 'Yeşil'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Font Settings */}
        <View style={[styles.sectionCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
          <View style={styles.sectionHeader}>
            <Type size={20} color={colors.primary} style={{ marginRight: 8 }} />
            <Text style={[styles.sectionTitle, { color: colors.text }]}>Varsayılan Yazı Boyutu</Text>
          </View>
          <Text style={[styles.sectionSubtitle, { color: colors.textMuted }]}>
            EPUB okuyucu için varsayılan metin boyutu.
          </Text>

          <View style={styles.fontAdjuster}>
            <TouchableOpacity
              onPress={() => handleAdjustFontSize(-2)}
              style={[styles.fontBtn, { backgroundColor: colors.bg, borderColor: colors.border }]}
            >
              <Text style={[styles.fontBtnText, { color: colors.text }]}>A-</Text>
            </TouchableOpacity>

            <Text style={[styles.fontSizeText, { color: colors.text }]}>{fontSize} px</Text>

            <TouchableOpacity
              onPress={() => handleAdjustFontSize(2)}
              style={[styles.fontBtn, { backgroundColor: colors.bg, borderColor: colors.border }]}
            >
              <Text style={[styles.fontBtnText, { color: colors.text }]}>A+</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Page Transition Settings */}
        <View style={[styles.sectionCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
          <View style={styles.sectionHeader}>
            <Type size={20} color={colors.primary} style={{ marginRight: 8 }} />
            <Text style={[styles.sectionTitle, { color: colors.text }]}>Sayfa Geçişi</Text>
          </View>
          <Text style={[styles.sectionSubtitle, { color: colors.textMuted }]}>Okuma sırasında tercih ettiğiniz sayfa geçiş efektini seçin.</Text>

          <View style={styles.transitionOptions}>
            {(['none', 'slide', 'fade'] as const).map(option => (
              <TouchableOpacity
                key={option}
                onPress={() => handleSetPageTransition(option)}
                style={[
                  styles.transitionOption,
                  {
                    backgroundColor: pageTransition === option ? colors.primary : colors.bg,
                    borderColor: pageTransition === option ? colors.primary : colors.border,
                  },
                ]}
              >
                <Text style={[styles.transitionOptionText, { color: pageTransition === option ? '#FFF' : colors.text }]}>
                  {option === 'none' ? 'Yok' : option === 'slide' ? 'Kaydır' : 'Solma'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Reset settings */}
        <View style={[styles.sectionCard, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
          <View style={styles.sectionHeader}>
            <ShieldAlert size={20} color="#EF4444" style={{ marginRight: 8 }} />
            <Text style={[styles.sectionTitle, { color: '#EF4444' }]}>Tehlikeli Bölge</Text>
          </View>
          <Text style={[styles.sectionSubtitle, { color: colors.textMuted }]}>
            Tüm uygulama verilerini ve e-kitapları cihazınızdan temizleyin.
          </Text>

          <TouchableOpacity
            onPress={handleClearDatabase}
            style={[styles.dangerBtn, { backgroundColor: '#EF4444' }]}
            activeOpacity={0.8}
          >
            <Trash2 size={18} color="#FFF" style={{ marginRight: 8 }} />
            <Text style={styles.dangerBtnText}>Kütüphaneyi Sıfırla</Text>
          </TouchableOpacity>
        </View>

        {/* About App */}
        <View style={[styles.sectionCard, { backgroundColor: colors.cardBg, borderColor: colors.border, marginBottom: 40 }]}>
          <View style={styles.sectionHeader}>
            <HelpCircle size={20} color={colors.textMuted} style={{ marginRight: 8 }} />
            <Text style={[styles.sectionTitle, { color: colors.text }]}>Uygulama Hakkında</Text>
          </View>
          <Text style={[styles.aboutText, { color: colors.text }]}>
            CsReader v1.0.0{'\n'}
            Expo v56 + React Native{'\n'}
            Farklı renklerde vurgulama, not alma, aydınlık/karanlık/sepya/yeşil temaları ve sayfa geçişleri desteğine sahip e-kitap okuyucu uygulaması.
          </Text>
        </View>
      </ScrollView>
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
  contentContainer: {
    padding: 20,
  },
  sectionCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 20,
    marginBottom: 20,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  sectionSubtitle: {
    fontSize: 12,
    lineHeight: 16,
    marginBottom: 16,
  },
  themesContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  themeBox: {
    width: '48%',
    padding: 12,
    borderRadius: 12,
    borderWidth: 2,
    alignItems: 'center',
    marginBottom: 12,
  },
  themeCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  themeLabel: {
    fontSize: 13,
  },
  fontAdjuster: {
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
  transitionOptions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 14,
  },
  transitionOption: {
    flex: 1,
    paddingVertical: 14,
    marginRight: 10,
    borderWidth: 1,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  transitionOptionText: {
    fontSize: 14,
    fontWeight: '600',
  },
  fontSizeText: {
    fontSize: 18,
    fontWeight: 'bold',
    marginHorizontal: 32,
  },
  dangerBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    marginTop: 8,
  },
  dangerBtnText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: 'bold',
  },
  aboutText: {
    fontSize: 13,
    lineHeight: 20,
    fontStyle: 'normal',
  },
});
